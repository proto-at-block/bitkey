"""Test cases for the utility module."""

from __future__ import annotations

import unittest
import unittest.mock

from bitkey import util


class TestUtil(unittest.TestCase):
    """Test cases for the utility functions."""

    def test_usb_dev_from_port_darwin(self: TestUtil) -> None:
        """Test cases for the USB bus/dev number retrieval from port on OSX."""
        with unittest.mock.patch("bitkey.util.platform.system") as mock_system:
            mock_system.return_value = "darwin"
            self.assertIsNone(util.usb_dev_from_port("1-6.4.4.2"))

    def test_usb_dev_from_port_linux(self: TestUtil) -> None:
        """Test cases for the USB bus/dev number retrieval from port on Linux."""
        with unittest.mock.patch(
            "bitkey.util.platform.system"
        ) as mock_system, unittest.mock.patch(
            "bitkey.util.open"
        ) as mock_open, unittest.mock.patch(
            "bitkey.util.os"
        ) as mock_os:
            mock_system.return_value = "linux"

            port_spec = "1-6.4.4.2"
            mock_os.path.exists.return_value = False
            self.assertIsNone(util.usb_dev_from_port(port_spec))
            mock_os.path.exists.assert_called_with("/sys/bus/usb/devices/1-6.4.4.2")

            port_spec = "3-6.4.4.4.2"
            f = unittest.mock.Mock()
            f.read.side_effect = [IOError]
            mock_os.path.exists.return_value = True
            mock_open.return_value.__enter__.side_effect = [f]
            self.assertIsNone(util.usb_dev_from_port(port_spec))

            port_spec = "9-6.5.4.4.7"
            f = unittest.mock.Mock()
            f.read.side_effect = ["500", "199"]
            mock_os.path.exists.return_value = True
            mock_open.return_value.__enter__.side_effect = [f, f]
            self.assertEqual((500, 199), util.usb_dev_from_port(port_spec))

    def test_size_to_bytes(self):
        """Test cases for converting size strings to integers."""
        self.assertEqual(util.size_to_bytes("1M"), 1024 * 1024)
        self.assertEqual(util.size_to_bytes("1K"), 1024)
        self.assertEqual(util.size_to_bytes(1024), 1024)
        self.assertEqual(util.size_to_bytes(1), 1)
        with self.assertRaises(NotImplementedError):
            util.size_to_bytes("1G")
        with self.assertRaises(NotImplementedError):
            util.size_to_bytes("1X")
        with self.assertRaises(ValueError):
            util.size_to_bytes("A2")
        with self.assertRaises(ValueError):
            util.size_to_bytes("")


if __name__ == "__main__":
    unittest.main()
