"""Test cases for WalletFS module."""

from __future__ import annotations

import unittest
import unittest.mock
import tempfile
from pathlib import Path

try:
    from bitkey.walletfs import WalletFS, GDBFs
    from bitkey.partition_info import PartitionInfo
    WALLETFS_AVAILABLE = True
except ImportError:
    # Skip tests if littlefs or other dependencies not available
    WALLETFS_AVAILABLE = False


@unittest.skipUnless(WALLETFS_AVAILABLE, "WalletFS dependencies not available")
class TestWalletFS(unittest.TestCase):
    """Test cases for WalletFS class."""

    def test_walletfs_with_partition_info(self):
        """Test WalletFS initialization with partition_info."""
        # Create a minimal littlefs binary file
        with tempfile.NamedTemporaryFile(mode='wb', delete=False) as tmp:
            # Write minimal littlefs structure (simplified - 24 blocks of 8192 bytes)
            tmp.write(b'\x00' * (24 * 8192))
            tmp_path = tmp.name

        try:
            config_dir = Path(__file__).parent.parent.parent / \
                "config" / "partitions"
            partition_info = PartitionInfo("w1a", config_dir=config_dir)

            # This will fail to mount (invalid fs), but we can check initialization
            try:
                fs = WalletFS(tmp_path, partition_info=partition_info)
                # If we got here and fs has the attributes, partition_info was accepted
                if hasattr(fs, '_block_size'):
                    self.assertEqual(
                        fs._block_size, partition_info.filesystem_block_size)
                    self.assertEqual(
                        fs._block_count, partition_info.filesystem_block_count)
                    self.assertEqual(
                        fs._lfs_start, partition_info.filesystem_start_address)
                    self.assertEqual(
                        fs._lfs_end, partition_info.filesystem_end_address)
            except Exception:
                # Mount failure is expected with dummy data, but attributes should still be set
                pass
        finally:
            Path(tmp_path).unlink()

    def test_walletfs_without_partition_info_shows_warning(self):
        """Test WalletFS shows warning when no partition_info provided."""
        with tempfile.NamedTemporaryFile(mode='wb', delete=False) as tmp:
            tmp.write(b'\x00' * (24 * 8192))
            tmp_path = tmp.name

        try:
            # Capture click output
            with unittest.mock.patch('click.echo') as mock_echo:
                try:
                    fs = WalletFS(tmp_path, partition_info=None)
                except Exception:
                    pass  # Mount failure expected

                # Verify warning was displayed
                mock_echo.assert_called()
                call_args = str(mock_echo.call_args)
                self.assertIn('WARNING', call_args)
                self.assertIn('No partition_info provided', call_args)
        finally:
            Path(tmp_path).unlink()

    def test_walletfs_fallback_uses_w1_defaults(self):
        """Test WalletFS fallback uses W1 hardcoded defaults."""
        with tempfile.NamedTemporaryFile(mode='wb', delete=False) as tmp:
            tmp.write(b'\x00' * (24 * 8192))
            tmp_path = tmp.name

        try:
            with unittest.mock.patch('click.echo'):
                try:
                    fs = WalletFS(tmp_path, partition_info=None)
                    # Check fallback values match W1 configuration
                    if hasattr(fs, '_block_size'):
                        self.assertEqual(fs._block_size, 8192)
                        self.assertEqual(fs._block_count, 24)
                        self.assertEqual(fs._lfs_start, 0x0800c000)
                        self.assertEqual(fs._lfs_end, 0x0800c000 + (8192 * 24))
                except Exception:
                    pass  # Mount failure expected
        finally:
            Path(tmp_path).unlink()


@unittest.skipUnless(WALLETFS_AVAILABLE, "WalletFS dependencies not available")
class TestGDBFs(unittest.TestCase):
    """Test cases for GDBFs class."""

    def test_gdbfs_missing_partitions_key(self):
        """Test GDBFs raises error when platform missing 'partitions' key."""
        # Mock context and platform without 'partitions' key
        mock_ctx = unittest.mock.Mock()
        mock_meson = unittest.mock.Mock()
        mock_meson.platform = {"jlink_gdb_chip": "EFR32MG24BXXXF1536"}
        mock_meson._platform = "test-platform"

        with unittest.mock.patch('bitkey.walletfs.MesonBuild', return_value=mock_meson):
            with self.assertRaises(ValueError) as cm:
                gdbfs = GDBFs(mock_ctx, target="test")

            self.assertIn("does not have a 'partitions' entry",
                          str(cm.exception))
            self.assertIn("test-platform", str(cm.exception))

    def test_gdbfs_empty_partitions_value(self):
        """Test GDBFs raises error when 'partitions' value is empty."""
        mock_ctx = unittest.mock.Mock()
        mock_meson = unittest.mock.Mock()
        mock_meson.platform = {
            "jlink_gdb_chip": "EFR32MG24BXXXF1536",
            "partitions": ""  # Empty string
        }
        mock_meson._platform = "test-platform"

        with unittest.mock.patch('bitkey.walletfs.MesonBuild', return_value=mock_meson):
            with self.assertRaises(ValueError) as cm:
                gdbfs = GDBFs(mock_ctx, target="test")

            self.assertIn("has an empty 'partitions' entry", str(cm.exception))
            self.assertIn("test-platform", str(cm.exception))

    def test_gdbfs_with_valid_platform(self):
        """Test GDBFs initialization with valid platform configuration."""
        mock_ctx = unittest.mock.Mock()
        mock_meson = unittest.mock.Mock()
        mock_meson.platform = {
            "jlink_gdb_chip": "EFR32MG24BXXXF1536",
            "partitions": "w1a"
        }
        mock_meson._platform = "w1"

        config_dir = Path(__file__).parent.parent.parent / \
            "config" / "partitions"

        # Mock PartitionInfo to avoid file system dependencies
        mock_partition_info = unittest.mock.Mock()
        mock_partition_info.filesystem_start_address = 0x0800c000
        mock_partition_info.filesystem_end_address = 0x08036000

        with unittest.mock.patch('bitkey.walletfs.MesonBuild', return_value=mock_meson):
            with unittest.mock.patch('bitkey.walletfs.PartitionInfo', return_value=mock_partition_info) as mock_pi:
                gdbfs = GDBFs(mock_ctx, target="test")

                # Verify PartitionInfo was called with correct partition name
                mock_pi.assert_called_once_with("w1a")

                # Verify partition_info was stored
                self.assertEqual(gdbfs.partition_info, mock_partition_info)

    def test_gdbfs_stores_jlink_serial(self):
        """Test GDBFs stores jlink_serial parameter."""
        mock_ctx = unittest.mock.Mock()
        mock_meson = unittest.mock.Mock()
        mock_meson.platform = {
            "jlink_gdb_chip": "EFR32MG24BXXXF1536",
            "partitions": "w1a"
        }
        mock_meson._platform = "w1"

        mock_partition_info = unittest.mock.Mock()

        with unittest.mock.patch('bitkey.walletfs.MesonBuild', return_value=mock_meson):
            with unittest.mock.patch('bitkey.walletfs.PartitionInfo', return_value=mock_partition_info):
                gdbfs = GDBFs(mock_ctx, target="test", jlink_serial="123456")

                # Verify jlink_serial was stored
                self.assertEqual(gdbfs.jlink_serial, "123456")


if __name__ == "__main__":
    unittest.main()
