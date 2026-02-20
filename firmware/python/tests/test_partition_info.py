"""Test cases for the PartitionInfo module."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import yaml
from bitkey.partition_info import PartitionInfo, get_application_partition_size, get_application_partition_size_from_config


class TestPartitionInfo(unittest.TestCase):
    """Test cases for PartitionInfo configuration parsing."""

    @classmethod
    def setUpClass(cls):
        """Set up test fixtures once for all tests."""
        cls.config_dir = Path(__file__).parent.parent.parent / "config" / "partitions"

    def test_w1a_partition_parsing(self):
        """Test parsing of w1a partition configuration."""
        partition_info = PartitionInfo("w1a", config_dir=self.config_dir)

        # W1A: bootloader=48K, filesystem starts at 0x08000000 + 48K
        self.assertEqual(partition_info.filesystem_start_address, 0x0800C000)
        self.assertEqual(partition_info.filesystem_size, 192 * 1024)
        self.assertEqual(partition_info.filesystem_block_size, 8192)
        self.assertEqual(partition_info.filesystem_block_count, 24)
        self.assertEqual(partition_info.filesystem_end_address, 0x0800C000 + (192 * 1024))

    def test_w3a_core_partition_parsing(self):
        """Test parsing of w3a-core partition configuration."""
        partition_info = PartitionInfo("w3a-core", config_dir=self.config_dir)

        # W3A-core has same layout as W1A
        self.assertEqual(partition_info.filesystem_start_address, 0x0800C000)
        self.assertEqual(partition_info.filesystem_size, 192 * 1024)
        self.assertEqual(partition_info.filesystem_block_count, 24)

    def test_w3a_uxc_partition_parsing(self):
        """Test parsing of w3a-uxc partition configuration."""
        partition_info = PartitionInfo("w3a-uxc", config_dir=self.config_dir)

        # W3A-uxc: bootloader=128K, app_a=896K, app_b=896K, filesystem=128K
        # filesystem starts at 0x08000000 + 128K + 896K + 896K = 0x08000000 + 1920K
        expected_start = 0x08000000 + (128 * 1024) + (896 * 1024) + (896 * 1024)
        self.assertEqual(partition_info.filesystem_start_address, expected_start)
        self.assertEqual(partition_info.filesystem_size, 128 * 1024)
        self.assertEqual(partition_info.filesystem_block_count, 16)

    def test_missing_config_file(self):
        """Test error handling for missing partition config file."""
        with self.assertRaises(FileNotFoundError) as cm:
            PartitionInfo("nonexistent", config_dir=self.config_dir)

        self.assertIn("Partition config file not found", str(cm.exception))
        self.assertIn("nonexistent", str(cm.exception))

    def test_invalid_top_level_structure(self):
        """Test error handling for non-dict top-level YAML structure."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                # Write a list instead of a dict
                f.write("- item1\n- item2\n")

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("top-level YAML structure must be a mapping", str(cm.exception))

    def test_missing_flash_section(self):
        """Test error handling for YAML missing 'flash' section."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump({"ram": {"size": "256K"}}, f)

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("'flash' section is missing", str(cm.exception))

    def test_flash_not_a_dict(self):
        """Test error handling for 'flash' not being a dict."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump({"flash": "not-a-dict"}, f)

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("'flash' section is missing or not a mapping", str(cm.exception))

    def test_partitions_not_a_list(self):
        """Test error handling for 'partitions' not being a list."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump({"flash": {"origin": 0x08000000, "size": "1536K", "partitions": "not-a-list"}}, f)

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("'flash.partitions' must be a list", str(cm.exception))

    def test_partition_missing_size(self):
        """Test error handling for partition missing 'size' field."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump(
                    {
                        "flash": {
                            "origin": 0x08000000,
                            "partitions": [{"name": "bootloader"}, {"name": "filesystem", "size": "192K"}],  # Missing size!
                        }
                    },
                    f,
                )

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("missing required 'size' field", str(cm.exception))
            self.assertIn("bootloader", str(cm.exception))

    def test_partition_zero_size(self):
        """Test error handling for partition with zero size."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump(
                    {
                        "flash": {
                            "origin": 0x08000000,
                            "partitions": [
                                {"name": "bootloader", "size": 0},  # Zero size!
                                {"name": "filesystem", "size": "192K"},
                            ],
                        }
                    },
                    f,
                )

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("non-positive size", str(cm.exception))
            self.assertIn("bootloader", str(cm.exception))

    def test_partition_negative_size(self):
        """Test error handling for partition with negative size."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump(
                    {
                        "flash": {
                            "origin": 0x08000000,
                            "partitions": [
                                {"name": "bootloader", "size": -100},  # Negative size!
                                {"name": "filesystem", "size": "192K"},
                            ],
                        }
                    },
                    f,
                )

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("non-positive size", str(cm.exception))
            self.assertIn("bootloader", str(cm.exception))

    def test_no_filesystem_partition(self):
        """Test error handling when no 'filesystem' partition exists."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            with open(config_file, "w") as f:
                yaml.dump(
                    {
                        "flash": {
                            "origin": 0x08000000,
                            "partitions": [{"name": "bootloader", "size": "48K"}, {"name": "application", "size": "192K"}],
                        }
                    },
                    f,
                )

            with self.assertRaises(ValueError) as cm:
                PartitionInfo("test", config_dir=config_dir)

            self.assertIn("No 'filesystem' partition found", str(cm.exception))

    def test_filesystem_size_not_multiple_of_block_size(self):
        """Test error handling for filesystem size not being multiple of block size."""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_dir = Path(tmpdir)
            partition_dir = config_dir / "test"
            partition_dir.mkdir()

            config_file = partition_dir / "partitions.yml"
            # 100K is not a multiple of 8192 bytes
            with open(config_file, "w") as f:
                yaml.dump(
                    {
                        "flash": {
                            "origin": 0x08000000,
                            "partitions": [{"name": "bootloader", "size": "48K"}, {"name": "filesystem", "size": "100K"}],
                        }
                    },
                    f,
                )

            partition_info = PartitionInfo("test", config_dir=config_dir)

            # Error should occur when accessing block_count property
            with self.assertRaises(ValueError) as cm:
                _ = partition_info.filesystem_block_count

            self.assertIn("not a multiple of block size", str(cm.exception))

    def test_repr(self):
        """Test __repr__ output format."""
        partition_info = PartitionInfo("w1a", config_dir=self.config_dir)
        repr_str = repr(partition_info)

        self.assertIn("PartitionInfo", repr_str)
        self.assertIn("w1a", repr_str)
        self.assertIn("0x0800c000", repr_str)

    def test_filesystem_end_address_calculation(self):
        """Test that filesystem_end_address is correctly calculated."""
        partition_info = PartitionInfo("w1a", config_dir=self.config_dir)

        expected_end = partition_info.filesystem_start_address + partition_info.filesystem_size
        self.assertEqual(partition_info.filesystem_end_address, expected_end)


class TestGetApplicationPartitionSize(unittest.TestCase):
    """Test cases for get_application_partition_size functions."""

    @classmethod
    def setUpClass(cls):
        """Set up test fixtures once for all tests."""
        cls.config_dir = Path(__file__).parent.parent.parent / "config" / "partitions"

    def test_get_size_from_config_w1a(self):
        """Test getting application partition size for w1a."""
        config_path = self.config_dir / "w1a" / "partitions.yml"
        size = get_application_partition_size_from_config(config_path)

        # w1a has 632KB application partitions
        self.assertEqual(size, 632 * 1024)

    def test_get_size_from_config_w3a_core(self):
        """Test getting application partition size for w3a-core."""
        config_path = self.config_dir / "w3a-core" / "partitions.yml"
        size = get_application_partition_size_from_config(config_path)

        # w3a-core has 632KB application partitions
        self.assertEqual(size, 632 * 1024)

    def test_get_size_from_config_w3a_uxc(self):
        """Test getting application partition size for w3a-uxc."""
        config_path = self.config_dir / "w3a-uxc" / "partitions.yml"
        size = get_application_partition_size_from_config(config_path)

        # w3a-uxc has 896KB application partitions
        self.assertEqual(size, 896 * 1024)

    def test_get_size_by_product_name(self):
        """Test getting application partition size by product name."""
        # Test all products
        self.assertEqual(get_application_partition_size("w1a", self.config_dir), 632 * 1024)
        self.assertEqual(get_application_partition_size("w3a-core", self.config_dir), 632 * 1024)
        self.assertEqual(get_application_partition_size("w3a-uxc", self.config_dir), 896 * 1024)

    def test_file_not_found(self):
        """Test that FileNotFoundError is raised for missing config."""
        with self.assertRaises(FileNotFoundError):
            get_application_partition_size_from_config("/nonexistent/path/partitions.yml")

    def test_invalid_product(self):
        """Test that FileNotFoundError is raised for invalid product."""
        with self.assertRaises(FileNotFoundError):
            get_application_partition_size("invalid-product", self.config_dir)

    def test_missing_application_partition(self):
        """Test that ValueError is raised when no application partition exists."""
        # Create a temp config file without application partition
        with tempfile.NamedTemporaryFile(mode="w", suffix=".yml", delete=False) as f:
            yaml.dump(
                {
                    "flash": {
                        "partitions": [
                            {"name": "bootloader", "size": "48K"},
                            {"name": "filesystem", "size": "192K"},
                        ]
                    }
                },
                f,
            )
            config_path = Path(f.name)

        try:
            with self.assertRaises(ValueError) as ctx:
                get_application_partition_size_from_config(config_path)
            self.assertIn("No application partition found", str(ctx.exception))
        finally:
            config_path.unlink()


if __name__ == "__main__":
    unittest.main()
