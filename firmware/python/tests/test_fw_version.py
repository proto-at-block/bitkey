import unittest
from unittest import mock

from bitkey import fw_version, git as git_module, semver_stub


class TestGetSemver(unittest.TestCase):
    def test_accepts_maximum_representable_configured_version(self):
        config = mock.mock_open(read_data='{"fw_version": "99.99.255"}')

        with mock.patch("builtins.open", config):
            version = fw_version._get_semver("fw_version")

        self.assertEqual(str(version), "99.99.255")

    def test_rejects_unrepresentable_configured_version_components(self):
        versions = {
            "major": "100.0.0",
            "minor": "1.100.0",
            "patch": "1.0.256",
        }

        for component, configured_version in versions.items():
            with self.subTest(component=component):
                config = mock.mock_open(
                    read_data=f'{{"fw_version": "{configured_version}"}}'
                )

                with mock.patch("builtins.open", config):
                    with self.assertRaisesRegex(
                        ValueError, rf"{component} must not exceed"
                    ):
                        fw_version._get_semver("fw_version")

    def test_rejects_unrepresentable_git_derived_version(self):
        git = mock.Mock(semver_tag=fw_version.semver.VersionInfo.parse("1.100.0"))

        with mock.patch(
            "builtins.open", side_effect=FileNotFoundError
        ), mock.patch.object(fw_version, "Git", return_value=git):
            with self.assertRaisesRegex(ValueError, r"minor must not exceed 99"):
                fw_version._get_semver("fw_version")

    def test_rejects_prerelease_and_build_identifiers(self):
        for configured_version in ("1.2.3-rc.1", "1.2.3+build.1"):
            with self.subTest(configured_version=configured_version):
                config = mock.mock_open(
                    read_data=f'{{"fw_version": "{configured_version}"}}'
                )

                with mock.patch("builtins.open", config):
                    with self.assertRaisesRegex(ValueError, r"not supported"):
                        fw_version._get_semver("fw_version")

    def test_stub_rejects_unsupported_identifiers(self):
        for configured_version in ("1.2.3-rc.1", "1.2.3+build.1"):
            with self.subTest(configured_version=configured_version):
                with self.assertRaisesRegex(ValueError, r"not supported"):
                    semver_stub.VersionInfo.parse(configured_version)

    def test_rejects_unsupported_git_tag_with_stub(self):
        def run_git_command(args):
            if args[:3] == ["describe", "--tags", "--exact-match"]:
                return "fw-1.2.3-rc.1"
            return ""

        with mock.patch("builtins.open", side_effect=FileNotFoundError):
            with mock.patch.object(
                git_module, "VersionInfo", semver_stub.VersionInfo
            ), mock.patch.object(
                git_module.Git, "_run_git_cmd", side_effect=run_git_command
            ):
                with self.assertRaisesRegex(ValueError, r"not supported"):
                    fw_version._get_semver("fw_version")

    def test_stub_skips_unsupported_historical_git_tags(self):
        def run_git_command(args):
            if args[:2] == ["tag", "--list"]:
                return "fw-2.0.0\nfw-1.2.3-rc.1"
            return ""

        with mock.patch("builtins.open", side_effect=FileNotFoundError):
            with mock.patch.object(
                git_module, "VersionInfo", semver_stub.VersionInfo
            ), mock.patch.object(
                git_module.Git, "_run_git_cmd", side_effect=run_git_command
            ):
                self.assertEqual(str(fw_version._get_semver("fw_version")), "2.0.0")


class TestMutateSemver(unittest.TestCase):
    def test_set_rejects_unrepresentable_version_before_opening_config(self):
        config = mock.mock_open(read_data='{"fw_version": "1.2.3"}')

        with mock.patch("builtins.open", config):
            with self.assertRaisesRegex(ValueError, r"patch must not exceed 255"):
                fw_version._set("fw_version", "1.2.256")

        config.assert_not_called()

    def test_bump_rejects_unrepresentable_version_before_rewriting_config(self):
        config = mock.mock_open(read_data='{"fw_version": "99.99.255"}')

        with mock.patch("builtins.open", config):
            with self.assertRaisesRegex(ValueError, r"patch must not exceed 255"):
                fw_version._bump("fw_version")

        handle = config()
        handle.seek.assert_not_called()
        handle.truncate.assert_not_called()
        handle.write.assert_not_called()


if __name__ == "__main__":
    unittest.main()
