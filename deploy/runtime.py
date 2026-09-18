"""Container PID 1, local console and readiness; never accepts EULA automatically."""
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import threading

CONTROL = Path("/run/poppy/control.sock")
READY = Path("/run/poppy/ready")
KIND = os.environ.get("SERVER_KIND", "")
PORT = int(os.environ.get("SERVER_PORT", "0"))


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def initialize(data=Path("/data"), defaults=Path("/opt/poppy/defaults")):
    import yaml
    for source in defaults.rglob("*"):
        if not source.is_file():
            continue
        target = data / source.relative_to(defaults)
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            if KIND == "pvp" and source.name == "server.properties":
                text = source.read_text()
                for key, value in {"server-ip": "127.0.0.1", "server-port": "25566",
                                   "online-mode": "false"}.items():
                    text = re.sub(r"(?m)^" + key + r"=.*$", key + "=" + value, text)
                target.write_text(text)
            else:
                shutil.copyfile(source, target)
    if KIND == "proxy":
        try:
            import tomllib
            config = tomllib.loads((data / "velocity.toml").read_text())
        except ImportError:
            import toml
            config = toml.load(data / "velocity.toml")
        assert config.get("online-mode") is True, "Proxy online authentication must remain enabled"
        assert config.get("bind") == "0.0.0.0:25565"
        assert config.get("player-info-forwarding-mode") == "LEGACY"
        assert config.get("servers", {}).get("pvp") == "127.0.0.1:25566"
        assert config.get("servers", {}).get("lobby") == "127.0.0.1:25567"
    else:
        assert properties(data / "eula.txt").get("eula", "").strip() == "true", "Operator EULA acceptance required"
        config = properties(data / "server.properties")
        for key, value in {"server-ip": "127.0.0.1", "server-port": str(PORT),
                           "online-mode": "false", "enable-rcon": "false"}.items():
            assert config.get(key) == value, "Unsafe backend setting: " + key
        assert yaml.safe_load((data / "spigot.yml").read_text()).get("settings", {}).get("bungeecord") is True
        plugins = data / "plugins"
        plugins.mkdir(exist_ok=True)
        managed = list(Path("/opt/poppy/plugins").glob("*.jar"))
        names = {path.name for path in managed}
        for existing in plugins.glob("*.jar"):
            assert existing.name in names, "Unmanaged/duplicate plugin requires review: " + existing.name
        for source in managed:
            shutil.copyfile(source, plugins / source.name)


def varint(value):
    result = bytearray()
    while True:
        byte = value & 127
        value >>= 7
        result.append(byte | (128 if value else 0))
        if not value:
            return bytes(result)


def read_varint(sock):
    value = 0
    for shift in range(0, 35, 7):
        raw = sock.recv(1)
        if not raw:
            raise RuntimeError("Unexpected EOF")
        value |= (raw[0] & 127) << shift
        if not raw[0] & 128:
            return value
    raise RuntimeError("Invalid VarInt")


def status_ping(port):
    host = b"localhost"
    payload = b"\x00" + varint(47) + varint(len(host)) + host + struct.pack(">H", port) + b"\x01"
    with socket.create_connection(("127.0.0.1", port), timeout=3) as sock:
        sock.sendall(varint(len(payload)) + payload + b"\x01\x00")
        size = read_varint(sock)
        assert 0 < size < 1048576
        assert read_varint(sock) == 0
        length = read_varint(sock)
        assert 0 < length < 1048576
        data = b""
        while len(data) < length:
            block = sock.recv(length - len(data))
            if not block:
                raise RuntimeError("Truncated status")
            data += block
        response = json.loads(data)
        assert "version" in response and "players" in response


def command(text):
    assert text in ("stop", "end")
    with socket.socket(socket.AF_UNIX) as sock:
        sock.settimeout(5)
        sock.connect(str(CONTROL))
        sock.sendall(text.encode())
        assert sock.recv(16) == b"OK"


def run():
    READY.unlink(missing_ok=True)
    CONTROL.unlink(missing_ok=True)
    initialize()
    memory = os.environ["JAVA_MEMORY"]
    assert re.fullmatch(r"[1-9][0-9]*[MG]", memory)
    args = ["java", "-Xms256M", "-Xmx" + memory, "-XX:+UseG1GC", "-jar", "/opt/poppy/server.jar"]
    if KIND != "proxy":
        args.append("nogui")
    child = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, text=True, bufsize=1)
    guard = threading.Lock()

    def stop(*_):
        READY.unlink(missing_ok=True)
        with guard:
            if child.poll() is None:
                child.stdin.write("end\n" if KIND == "proxy" else "stop\n")
                child.stdin.flush()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    server = socket.socket(socket.AF_UNIX)
    server.bind(str(CONTROL))
    os.chmod(CONTROL, 0o600)
    server.listen(2)

    def listen():
        while child.poll() is None:
            connection, _ = server.accept()
            with connection:
                connection.settimeout(5)
                message = connection.recv(16).decode()
                if message == ("end" if KIND == "proxy" else "stop"):
                    stop()
                    connection.sendall(b"OK")
    threading.Thread(target=listen, daemon=True).start()
    required = {"pvp": ["PoppyPractice 0.1.0 enabled.", "PvP bridge mode:"],
                "lobby": ["Standalone lobby enabled."], "proxy": []}[KIND]
    seen = set()
    failed = False
    for line in child.stdout:
        print(line, end="", flush=True)
        if "ERROR" in line or "SEVERE" in line or "Exception" in line:
            failed = True
            READY.unlink(missing_ok=True)
        for message in required:
            if message in line:
                seen.add(message)
        if "Done (" in line and not failed and len(seen) == len(required):
            READY.touch()
    result = child.wait()
    READY.unlink(missing_ok=True)
    return result


if __name__ == "__main__":
    if sys.argv[1:] == ["run"]:
        sys.exit(run())
    elif sys.argv[1:] == ["health"]:
        assert READY.exists(), "Startup/plugins not ready"
        status_ping(PORT)
    elif sys.argv[1:] == ["stop"]:
        command("end" if KIND == "proxy" else "stop")
    else:
        raise SystemExit("Use run, health or stop")
