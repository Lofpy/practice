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
import tempfile
import threading

CONTROL = Path("/run/poppy/control.sock")
READY = Path("/run/poppy/ready")
STARTUP_ERROR = Path("/run/poppy/startup-error")
KIND = os.environ.get("SERVER_KIND", "")
PORT = int(os.environ.get("SERVER_PORT", "0"))
PLUGIN_SOURCE = Path("/opt/poppy/plugins")
SURVIVAL_PROTOCOL = 777


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def load_toml(text):
    try:
        import tomllib
        return tomllib.loads(text)
    except ImportError:
        import toml
        return toml.loads(text)


def replace_file_text(path, transform):
    """Replace only changed display defaults, atomically and without reformatting."""
    if not path.is_file():
        return
    with path.open(encoding="utf-8", newline="") as source:
        original = source.read()
    updated = transform(original)
    if updated == original:
        return
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="",
                                         prefix=".branding-", dir=path.parent,
                                         delete=False) as output:
            temporary = Path(output.name)
            output.write(updated)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, path.stat().st_mode & 0o777)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def migrate_branding(data, kind):
    """Upgrade known Poppy display defaults; preserve custom/operational settings.

    Deployment calls initialization only after its state/DB backup, so the
    existing transaction also restores these files if startup fails.
    """
    if kind in ("pvp", "lobby"):
        old = "PoppyPractice 1.8.9 NoDebuff 1v1" if kind == "pvp" else "Poppy Network Lobby"
        new = r"\u00a7cAscendingMC \u00a77" + ("Practice" if kind == "pvp" else "Lobby")
        replace_file_text(data / "server.properties", lambda text: re.sub(
            r"(?m)^(motd=)" + re.escape(old) + r"(\r?)$",
            lambda match: match.group(1) + new + match.group(2), text))

    if kind == "lobby":
        import yaml
        replacements = {
            "scoreboard-title": ("&d&lPoppy Network", "&c&lAscendingMC"),
            "welcome-message": (
                "&dPoppy Network &7へようこそ！ &fコンパスから PvP に参加できます。",
                "&cAscendingMC &7へようこそ！ &fコンパスから Practice に参加できます。"),
        }

        def lobby_config(text):
            document = yaml.compose(text, Loader=yaml.SafeLoader)
            if not isinstance(document, yaml.MappingNode):
                return text
            changes = []
            for key, value in document.value:
                if key.value not in replacements or not isinstance(value, yaml.ScalarNode):
                    continue
                old, new = replacements[key.value]
                start, end = value.start_mark.index, value.end_mark.index
                # Aliases/anchors are operator customizations, not legacy defaults.
                if (value.value == old and key.end_mark.index < start
                        and not text[start:end].startswith(("&", "*"))):
                    changes.append((start, end, json.dumps(new, ensure_ascii=False)))
            for start, end, replacement in sorted(changes, reverse=True):
                text = text[:start] + replacement + text[end:]
            return text

        replace_file_text(data / "plugins/PoppyLobby/config.yml", lobby_config)

    if kind == "proxy":
        replacements = {
            ("", "motd"): (
                "<light_purple>Poppy Network</light_purple> <gray>| Lobby & Practice</gray>",
                "<red>AscendingMC</red> <gray>| Lobby & Practice</gray>"),
            ("query", "map"): ("Poppy Network", "AscendingMC"),
        }

        def proxy_config(text):
            config = load_toml(text)
            section = ""
            result = []
            for line in text.splitlines(keepends=True):
                header = re.match(r"^\s*\[([^\[\]]+)\]\s*(?:#.*)?$", line)
                if header:
                    section = header.group(1).strip()
                for (target_section, key), (old, new) in replacements.items():
                    values = config if not section else config.get(section, {})
                    if section != target_section or values.get(key) != old:
                        continue
                    pattern = (r"^([ \t]*" + re.escape(key) + r"[ \t]*=[ \t]*)"
                               r"(\"(?:\\.|[^\"\\])*\"|'[^']*')"
                               r"([ \t]*(?:#[^\r\n]*)?(?:\r?\n)?)$")
                    line = re.sub(pattern, lambda match: match.group(1)
                                  + json.dumps(new, ensure_ascii=False) + match.group(3), line)
                result.append(line)
            return "".join(result)

        replace_file_text(data / "velocity.toml", proxy_config)


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
    migrate_branding(data, KIND)
    if KIND == "proxy":
        # Added only after the deployment's complete state backup. Existing custom
        # addresses must fail validation, never be silently overwritten.
        def register_survival(text):
            config = load_toml(text)
            if "survival" in config.get("servers", {}):
                return text
            return re.sub(r"(?m)^(\[servers\][ \t]*(?:#[^\r\n]*)?)(\r?\n)",
                          lambda match: match.group(1) + match.group(2)
                          + 'survival = "127.0.0.1:25568"' + match.group(2), text, count=1)
        replace_file_text(data / "velocity.toml", register_survival)
        config = load_toml((data / "velocity.toml").read_text())
        assert config.get("online-mode") is True, "Proxy online authentication must remain enabled"
        assert config.get("bind") == "0.0.0.0:25565"
        assert config.get("player-info-forwarding-mode") == "LEGACY"
        assert config.get("servers", {}).get("pvp") == "127.0.0.1:25566"
        assert config.get("servers", {}).get("lobby") == "127.0.0.1:25567"
        assert config.get("servers", {}).get("survival") == "127.0.0.1:25568"
    else:
        assert properties(data / "eula.txt").get("eula", "").strip() == "true", "Operator EULA acceptance required"
        config = properties(data / "server.properties")
        for key, value in {"server-ip": "127.0.0.1", "server-port": str(PORT),
                           "online-mode": "false", "enable-rcon": "false"}.items():
            assert config.get(key) == value, "Unsafe backend setting: " + key
        assert yaml.safe_load((data / "spigot.yml").read_text()).get("settings", {}).get("bungeecord") is True
        if KIND == "survival":
            assert config.get("enforce-secure-profile") == "false", "Legacy forwarding requires proxy authentication"
            paper = yaml.safe_load((data / "config/paper-global.yml").read_text())
            assert paper.get("proxies", {}).get("bungee-cord", {}).get("online-mode") is True
            assert paper.get("proxies", {}).get("velocity", {}).get("enabled") is False
    plugins = data / "plugins"
    plugins.mkdir(exist_ok=True)
    managed = list(PLUGIN_SOURCE.glob("*.jar"))
    names = {path.name for path in managed}
    if KIND == "survival":
        assert names == {"AscendingSurvival.jar"}, "Survival must not include protocol translation plugins"
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


def status_ping(port, expected_protocol=None):
    host = b"localhost"
    protocol = expected_protocol if expected_protocol is not None else 47
    payload = b"\x00" + varint(protocol) + varint(len(host)) + host + struct.pack(">H", port) + b"\x01"
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
        if expected_protocol is not None:
            assert response["version"].get("protocol") == expected_protocol, "Unexpected Survival protocol"


def command(text):
    assert text in ("stop", "end")
    with socket.socket(socket.AF_UNIX) as sock:
        sock.settimeout(5)
        sock.connect(str(CONTROL))
        sock.sendall(text.encode())
        assert sock.recv(16) == b"OK"


def java_args(kind, memory):
    assert re.fullmatch(r"[1-9][0-9]*[MG]", memory)
    args = ["java", "-Xms256M", "-Xmx" + memory, "-XX:+UseG1GC"]
    if kind == "survival":
        # JNA's default extraction into /tmp cannot load executable pages on a
        # noexec mount. Use the exact bundled library, installed at image build
        # time under the read-only root, with no system/unpack fallback.
        args.extend(["-Djna.boot.library.path=/opt/poppy/native",
                     "-Djna.nounpack=true", "-Djna.nosys=true"])
    args.extend(["-jar", "/opt/poppy/server.jar"])
    if kind != "proxy":
        args.append("nogui")
    return args


class StartupReadiness:
    def __init__(self, required):
        self.required = set(required)
        self.seen = set()
        self.failed = False

    def observe(self, line):
        if "ERROR" in line or "SEVERE" in line or "Exception" in line:
            if not self.failed:
                # Preserve the first cause rather than only reporting a missing
                # readiness marker after the host's startup deadline expires.
                STARTUP_ERROR.write_text(line.strip()[:2000] + "\n", encoding="utf-8")
            self.failed = True
            READY.unlink(missing_ok=True)
        for message in self.required:
            if message in line:
                self.seen.add(message)
        if "Done (" in line and not self.failed and self.seen == self.required:
            READY.touch()


def health():
    if STARTUP_ERROR.exists():
        raise RuntimeError("Startup/plugins not ready; first startup error: "
                           + STARTUP_ERROR.read_text(encoding="utf-8").strip())
    assert READY.exists(), "Startup/plugins not ready"
    status_ping(PORT, SURVIVAL_PROTOCOL if KIND == "survival" else None)


def run():
    READY.unlink(missing_ok=True)
    STARTUP_ERROR.unlink(missing_ok=True)
    CONTROL.unlink(missing_ok=True)
    initialize()
    args = java_args(KIND, os.environ["JAVA_MEMORY"])
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
                "lobby": ["Standalone lobby enabled."],
                "survival": ["AscendingSurvival 0.1.0 enabled."],
                "proxy": ["AscendingNetwork enabled: Survival requires Minecraft 26.3 (protocol 777)"]}[KIND]
    readiness = StartupReadiness(required)
    for line in child.stdout:
        print(line, end="", flush=True)
        readiness.observe(line)
    result = child.wait()
    READY.unlink(missing_ok=True)
    return result


if __name__ == "__main__":
    if sys.argv[1:] == ["run"]:
        sys.exit(run())
    elif sys.argv[1:] == ["health"]:
        health()
    elif sys.argv[1:] == ["stop"]:
        command("end" if KIND == "proxy" else "stop")
    else:
        raise SystemExit("Use run, health or stop")
