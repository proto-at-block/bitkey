import unittest
from unittest import mock

import semver

from bitkey import fw_version


class TestGetSemver(unittest.TestCase):
    def test_accepts_maximum_representable_configured_version(self):
        config = mock.mock_open(read_data='{"fw_version": "99.99.999"}')

        with mock.patch("builtins.open", config):
            version = fw_version._get_semver("fw_version")

        self.assertEqual(version, semver.VersionInfo.parse("99.99.999"))

    def test_rejects_unrepresentable_configured_version_components(self):
        versions = {
            "major": "100.0.0",
            "minor": "1.100.0",
            "patch": "1.0.1000",
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
        git = mock.Mock(semver_tag=semver.VersionInfo.parse("1.100.0"))

        with mock.patch(
            "builtins.open", side_effect=FileNotFoundError
        ), mock.patch.object(fw_version, "Git", return_value=git):
            with self.assertRaisesRegex(ValueError, r"minor must not exceed 99"):
                fw_version._get_semver("fw_version")


if __name__ == "__main__":
    unittest.main()
