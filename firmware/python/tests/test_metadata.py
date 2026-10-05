import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import semver

from bitkey import fw_version
from bitkey.metadata import Metadata


class TestFirmwareVersionMetadata(unittest.TestCase):
    def test_includes_head_timestamp_and_preserves_git_fields(self):
        git = mock.Mock(
            identity="fw-1.2.5-0-g81251adc34",
            branch="reproducible-firmware-builds",
            head_timestamp=1787760230,
        )

        with mock.patch.object(fw_version, "Git", return_value=git), mock.patch.object(
            fw_version,
            "_get_semver",
            return_value=semver.VersionInfo.parse("1.2.5"),
        ):
            data = fw_version.metadata("app")

        self.assertEqual(data["git_id"], "fw-1.2.5-0-g81251adc34")
        self.assertEqual(data["git_branch"], "reproducible-firmware-builds")
        self.assertEqual(data["timestamp"], 1787760230)


class TestMetadataGeneration(unittest.TestCase):
    PROVENANCE = {
        "git_id": "fw-1.2.5-0-g81251adc34",
        "git_branch": "reproducible-firmware-builds",
        "timestamp": 1787760230,
        "ver_major": 1,
        "ver_minor": 2,
        "ver_patch": 5,
    }

    def test_generation_does_not_depend_on_wall_clock(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir = Path(temp_dir)
            firmware = temp_dir / "firmware.bin"
            first = temp_dir / "first.metadata.bin"
            second = temp_dir / "second.metadata.bin"
            firmware.write_bytes((b"\x00" * Metadata._APP_OFFSET) + b"firmware")

            metadata = Metadata(firmware)
            with mock.patch.object(
                fw_version, "metadata", return_value=self.PROVENANCE
            ), mock.patch.object(time, "time", side_effect=[1, 2]):
                metadata.generate(first, image_type="app")
                metadata.generate(second, image_type="app")

            self.assertEqual(first.read_bytes(), second.read_bytes())
            decoded = Metadata.read_from_bytes(first.read_bytes())
            self.assertEqual(decoded["timestamp"], self.PROVENANCE["timestamp"])

    def test_maximum_patch_version_round_trips(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir = Path(temp_dir)
            firmware = temp_dir / "firmware.bin"
            config = temp_dir / "invoke.json"
            output = temp_dir / "metadata.bin"
            firmware.write_bytes((b"\x00" * Metadata._APP_OFFSET) + b"firmware")
            config.write_text('{"fw_version": "99.99.255"}')
            git = mock.Mock(identity="git-id", branch="branch", head_timestamp=1)

            with mock.patch.object(
                fw_version, "CONFIG_FILE", config
            ), mock.patch.object(fw_version, "Git", return_value=git):
                Metadata(firmware).generate(output, image_type="app")

            decoded = Metadata.read_from_bytes(output.read_bytes())
            self.assertEqual(
                (decoded["ver_major"], decoded["ver_minor"], decoded["ver_patch"]),
                (99, 99, 255),
            )

    def test_unrepresentable_patch_version_is_not_generated(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir = Path(temp_dir)
            firmware = temp_dir / "firmware.bin"
            config = temp_dir / "invoke.json"
            output = temp_dir / "metadata.bin"
            firmware.write_bytes((b"\x00" * Metadata._APP_OFFSET) + b"firmware")
            config.write_text('{"fw_version": "1.0.256"}')
            git = mock.Mock(identity="git-id", branch="branch", head_timestamp=1)

            with mock.patch.object(
                fw_version, "CONFIG_FILE", config
            ), mock.patch.object(fw_version, "Git", return_value=git):
                with self.assertRaisesRegex(ValueError, r"patch must not exceed 255"):
                    Metadata(firmware).generate(output, image_type="app")

            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
