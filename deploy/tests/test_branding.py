"""Migrate only old display defaults; never reset backend, proxy or player data."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("branding_runtime", Path(__file__).parents[1] / "runtime.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class BrandingMigrationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.data = Path(temporary.name)

    def write(self, name, text):
        path = self.data / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode("utf-8"))
        return path

    def read(self, path):
        return path.read_bytes().decode("utf-8")

    def test_backend_changes_only_exact_legacy_motd_and_preserves_crlf(self):
        for kind, old, label in (("pvp", "PoppyPractice 1.8.9 NoDebuff 1v1", "Practice"),
                                 ("lobby", "Poppy Network Lobby", "Lobby")):
            with self.subTest(kind=kind):
                before = "# operator comment\r\nserver-port=25566\r\nmotd=" + old + "\r\nonline-mode=false\r\n"
                path = self.write("server.properties", before)
                runtime.migrate_branding(self.data, kind)
                self.assertEqual(self.read(path), before.replace(old, r"\u00a7cAscendingMC \u00a77" + label))

    def test_custom_motd_and_similar_legacy_text_are_untouched(self):
        for value in ("My Practice", "PoppyPractice 1.8.9 NoDebuff 1v1 Custom", "Poppy Network Lobby Custom"):
            before = "motd=" + value + "\n# motd=PoppyPractice 1.8.9 NoDebuff 1v1\n"
            path = self.write("server.properties", before)
            runtime.migrate_branding(self.data, "pvp")
            runtime.migrate_branding(self.data, "lobby")
            self.assertEqual(self.read(path), before)

    def test_lobby_changes_two_display_scalars_without_reformatting(self):
        before = ("# retain this comment\r\nworld-name: my_lobby\r\npractice-server: pvp\r\n"
                  "scoreboard-title: '&d&lPoppy Network' # title comment\r\n"
                  "welcome-message: '&dPoppy Network &7へようこそ！ &fコンパスから PvP に参加できます。'\r\n"
                  "custom: [1, 2, 3]\r\n")
        path = self.write("plugins/PoppyLobby/config.yml", before)
        runtime.migrate_branding(self.data, "lobby")
        expected = before.replace("'&d&lPoppy Network'", '"&c&lAscendingMC"').replace(
            "'&dPoppy Network &7へようこそ！ &fコンパスから PvP に参加できます。'",
            '"&cAscendingMC &7へようこそ！ &fコンパスから Practice に参加できます。"')
        self.assertEqual(self.read(path), expected)

    def test_lobby_custom_values_and_yaml_aliases_are_preserved(self):
        before = ("shared: &custom '&d&lPoppy Network'\nscoreboard-title: *custom\n"
                  "welcome-message: 'Welcome to a custom network'\n")
        path = self.write("plugins/PoppyLobby/config.yml", before)
        runtime.migrate_branding(self.data, "lobby")
        self.assertEqual(self.read(path), before)

    def test_proxy_preserves_sections_comments_and_operational_values(self):
        before = ("# public identity\r\n"
                  "motd = '<light_purple>Poppy Network</light_purple> <gray>| Lobby & Practice</gray>' # comment\r\n"
                  "online-mode = true\r\nplayer-info-forwarding-mode = 'LEGACY'\r\n"
                  "[servers]\r\npvp = '127.0.0.1:25566'\r\nlobby = '127.0.0.1:25567'\r\n"
                  "[query]\r\nmap = 'Poppy Network' # query comment\r\nenabled = false\r\n"
                  "[custom]\r\nmap = 'Poppy Network'\r\n")
        path = self.write("velocity.toml", before)
        runtime.migrate_branding(self.data, "proxy")
        expected = before.replace(
            "'<light_purple>Poppy Network</light_purple> <gray>| Lobby & Practice</gray>'",
            '"<red>AscendingMC</red> <gray>| Lobby & Practice</gray>"').replace(
            "map = 'Poppy Network' # query comment", 'map = "AscendingMC" # query comment')
        self.assertEqual(self.read(path), expected)

    def test_proxy_custom_branding_is_preserved(self):
        before = "motd = 'Custom Poppy Network'\n[query]\nmap = 'Custom map'\n"
        path = self.write("velocity.toml", before)
        runtime.migrate_branding(self.data, "proxy")
        self.assertEqual(self.read(path), before)

    def test_missing_files_are_not_created_and_player_data_is_untouched(self):
        before = "players:\n  sample:\n    rating: 1678.125\n"
        player_data = self.write("plugins/PoppyPractice/ratings.yml", before)
        for kind in ("pvp", "lobby", "proxy"):
            runtime.migrate_branding(self.data, kind)
        self.assertEqual(self.read(player_data), before)
        self.assertFalse((self.data / "server.properties").exists())
        self.assertFalse((self.data / "velocity.toml").exists())

    def test_second_run_is_idempotent_and_does_not_rewrite_file(self):
        path = self.write("server.properties", "motd=PoppyPractice 1.8.9 NoDebuff 1v1\n")
        runtime.migrate_branding(self.data, "pvp")
        expected = path.read_bytes()
        with patch.object(runtime.os, "replace", side_effect=AssertionError("Must not rewrite")):
            runtime.migrate_branding(self.data, "pvp")
        self.assertEqual(path.read_bytes(), expected)

    def test_failed_atomic_replace_preserves_original_and_removes_temporary(self):
        before = "motd=PoppyPractice 1.8.9 NoDebuff 1v1\n"
        path = self.write("server.properties", before)
        with patch.object(runtime.os, "replace", side_effect=OSError("simulated failure")):
            with self.assertRaises(OSError):
                runtime.migrate_branding(self.data, "pvp")
        self.assertEqual(self.read(path), before)
        self.assertEqual(list(self.data.glob(".branding-*")), [])

    def test_initialize_migrates_existing_proxy_before_validating(self):
        path = self.write("velocity.toml", (
            'motd = "<light_purple>Poppy Network</light_purple> <gray>| Lobby & Practice</gray>"\n'
            'online-mode = true\nbind = "0.0.0.0:25565"\nplayer-info-forwarding-mode = "LEGACY"\n'
            '[servers]\npvp = "127.0.0.1:25566"\nlobby = "127.0.0.1:25567"\n'))
        with patch.object(runtime, "KIND", "proxy"):
            runtime.initialize(self.data, self.data / "missing-defaults")
        self.assertIn("<red>AscendingMC</red>", self.read(path))


if __name__ == "__main__":
    unittest.main()
