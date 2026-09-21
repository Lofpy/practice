"""Verify pinned native extraction without Docker or platform native execution."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("native_libraries", ROOT / "deploy/native-libraries.py")
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class NativeLibraryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.paper = self.root / "paper.jar"
        self.libraries = self.root / "libraries"
        self.output = self.root / "native"
        self.records = []
        self.elf = b"\x7fELF\x02\x01" + b"\x00" * 12 + (62).to_bytes(2, "little")
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, "w") as jar:
            jar.writestr("com/sun/jna/linux-x86-64/libjnidispatch.so", self.elf)
            arm_elf = self.elf[:18] + (183).to_bytes(2, "little")
            jar.writestr("com/sun/jna/linux-aarch64/libjnidispatch.so", arm_elf)
        for index, key in enumerate(native.PROBE_LIBRARIES):
            content = archive.getvalue() if index == 0 else ("fixture-" + key).encode()
            relative = key.replace(":", "/") + "/1.0/" + key.split(":")[1] + "-1.0.jar"
            path = self.libraries / relative
            path.parent.mkdir(parents=True)
            path.write_bytes(content)
            self.records.append([hashlib.sha256(content).hexdigest(), key + ":1.0", relative])
        self.manifest()

    def manifest(self):
        with zipfile.ZipFile(self.paper, "w") as jar:
            jar.writestr("META-INF/libraries.list", "\n".join("\t".join(row) for row in self.records))

    def install(self, architecture="x86_64"):
        native.install(self.paper, self.libraries, self.output, architecture)

    def test_installs_exact_native_and_all_pinned_probe_jars(self):
        self.install()
        self.assertEqual((self.output / "libjnidispatch.so").read_bytes(), self.elf)
        self.assertEqual(len(list((self.output / "probe-libs").glob("*.jar"))), 4)
        provenance = json.loads((self.output / "provenance.json").read_text())
        self.assertEqual(provenance["paper_sha256"], native.sha256(self.paper.read_bytes()))
        self.assertEqual(provenance["native_sha256"], native.sha256(self.elf))
        if os.name != "nt":
            for path in self.output.rglob("*"):
                self.assertFalse(path.stat().st_mode & 0o022, str(path))
            self.assertEqual((self.output / "libjnidispatch.so").stat().st_mode & 0o777, 0o555)

    def test_arm64_selects_matching_resource_and_machine(self):
        self.install("arm64")
        self.assertEqual((self.output / "libjnidispatch.so").read_bytes()[18:20], (183).to_bytes(2, "little"))

    def test_checksum_mismatch_fails_before_creating_output(self):
        (self.libraries / self.records[0][2]).write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            self.install()
        self.assertFalse(self.output.exists())

    def test_probe_dependency_mismatch_also_fails_closed(self):
        (self.libraries / self.records[-1][2]).write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            self.install()
        self.assertFalse(self.output.exists())

    def test_duplicate_selected_coordinate_is_rejected(self):
        self.records.append(self.records[0])
        self.manifest()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.install()

    def test_missing_selected_coordinate_is_rejected(self):
        self.records.pop()
        self.manifest()
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.install()

    def test_manifest_traversal_is_rejected_before_reads(self):
        for invalid in ("../escape.jar", "/absolute.jar", "C:/absolute.jar", "folder\\escape.jar"):
            with self.subTest(invalid=invalid):
                self.records[0][2] = invalid
                self.manifest()
                with self.assertRaisesRegex(ValueError, "Unsafe"):
                    self.install()

    def test_unsupported_architecture_is_not_silently_accepted(self):
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            self.install("unexpected")
        self.assertFalse(self.output.exists())

    def test_existing_output_is_not_overwritten(self):
        self.output.mkdir()
        marker = self.output / "operator-data"
        marker.write_bytes(b"preserve")
        with self.assertRaises(FileExistsError):
            self.install()
        self.assertEqual(marker.read_bytes(), b"preserve")

    def test_wrong_machine_in_resource_is_rejected(self):
        jar_path = self.libraries / self.records[0][2]
        with zipfile.ZipFile(jar_path, "w") as jar:
            jar.writestr("com/sun/jna/linux-x86-64/libjnidispatch.so", b"invalid")
        self.records[0][0] = native.sha256(jar_path.read_bytes())
        self.manifest()
        with self.assertRaisesRegex(ValueError, "Invalid JNA ELF"):
            self.install()
        self.assertFalse(self.output.exists())

    def test_probe_disables_unpacked_or_system_dispatch_fallback(self):
        command = native.probe_command()
        self.assertIn("-Djna.boot.library.path=" + str(native.NATIVE_ROOT), command)
        self.assertIn("-Djna.nounpack=true", command)
        self.assertIn("-Djna.nosys=true", command)
        self.assertIn("NativeStartupProbe", command)
        self.assertNotIn("-jar", command)


if __name__ == "__main__":
    unittest.main()
