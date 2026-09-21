"""Pin Paper's JNA native code into the image, never a writable executable mount.

Paperclip's patch-only mode materializes the exact libraries described by its
SHA-256 manifest without running Minecraft or accepting the EULA. This build
helper verifies those libraries before installing the native loader and a tiny
startup probe. Runtime /tmp stays noexec and JNA unpacking stays disabled.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import subprocess
import zipfile


PROBE_LIBRARIES = (
    "net.java.dev.jna:jna",
    "net.java.dev.jna:jna-platform",
    "com.github.oshi:oshi-core",
    "org.slf4j:slf4j-api",
)
ARCHITECTURES = {
    "x86_64": ("linux-x86-64", 62),
    "amd64": ("linux-x86-64", 62),
    "aarch64": ("linux-aarch64", 183),
    "arm64": ("linux-aarch64", 183),
}
NATIVE_ROOT = Path("/opt/poppy/native")


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def verified_libraries(paper, libraries):
    """Resolve only known coordinates; reject mismatches before writing anything."""
    with zipfile.ZipFile(paper) as archive:
        manifest = archive.read("META-INF/libraries.list").decode("utf-8")
    selected = {}
    library_root = libraries.resolve()
    for line in manifest.splitlines():
        checksum, coordinate, relative = line.split("\t")
        key = ":".join(coordinate.split(":")[:2])
        if key not in PROBE_LIBRARIES:
            continue
        if key in selected:
            raise ValueError("Duplicate Paper library: " + key)
        path = PurePosixPath(relative)
        if (path.is_absolute() or ".." in path.parts or "\\" in relative
                or ":" in relative or not relative.endswith(".jar")):
            raise ValueError("Unsafe Paper library path: " + relative)
        target = (library_root / relative).resolve()
        if not target.is_relative_to(library_root):
            raise ValueError("Paper library escapes its directory: " + relative)
        content = target.read_bytes()
        if not re.fullmatch(r"[0-9a-f]{64}", checksum) or sha256(content) != checksum:
            raise ValueError("Paper library checksum mismatch: " + coordinate)
        selected[key] = (coordinate, relative, checksum, content)
    missing = set(PROBE_LIBRARIES) - selected.keys()
    if missing:
        raise ValueError("Missing Paper libraries: " + ", ".join(sorted(missing)))
    return selected


def install(paper, libraries, output, architecture=None):
    architecture = (architecture or platform.machine()).lower()
    if architecture not in ARCHITECTURES:
        raise ValueError("Unsupported native architecture: " + architecture)
    resource_prefix, machine = ARCHITECTURES[architecture]
    selected = verified_libraries(paper, libraries)
    native_entry = "com/sun/jna/" + resource_prefix + "/libjnidispatch.so"
    with zipfile.ZipFile(io.BytesIO(selected[PROBE_LIBRARIES[0]][3])) as archive:
        native = archive.read(native_entry)
    # ELF64, little endian, Linux architecture corresponding to the image.
    if (len(native) < 20 or native[:6] != b"\x7fELF\x02\x01"
            or int.from_bytes(native[18:20], "little") != machine):
        raise ValueError("Invalid JNA ELF for " + architecture)
    output.mkdir(parents=True, exist_ok=False)
    probe_libraries = output / "probe-libs"
    probe_libraries.mkdir()
    (output / "probe").mkdir()
    native_path = output / "libjnidispatch.so"
    native_path.write_bytes(native)
    native_path.chmod(0o555)
    provenance = {
        "paper_sha256": sha256(paper.read_bytes()),
        "native_resource": native_entry,
        "native_sha256": sha256(native),
        "libraries": {},
    }
    for key, (coordinate, relative, checksum, content) in selected.items():
        destination = probe_libraries / PurePosixPath(relative).name
        destination.write_bytes(content)
        destination.chmod(0o444)
        provenance[coordinate] = checksum
        provenance["libraries"][key] = coordinate
    manifest = output / "provenance.json"
    manifest.write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    manifest.chmod(0o444)


def probe_command(root=NATIVE_ROOT):
    return [
        "java",
        "-Djna.boot.library.path=" + str(root),
        "-Djna.nounpack=true", "-Djna.nosys=true",
        "-cp", str(root / "probe") + os.pathsep + str(root / "probe-libs" / "*"),
        "NativeStartupProbe", str(root),
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("install")
    build.add_argument("--paper", type=Path, required=True)
    build.add_argument("--libraries", type=Path, required=True)
    build.add_argument("--output", type=Path, required=True)
    commands.add_parser("probe")
    args = parser.parse_args()
    if args.command == "install":
        install(args.paper, args.libraries, args.output)
    else:
        subprocess.run(probe_command(), check=True, timeout=45)


if __name__ == "__main__":
    main()
