"""Test cases for the comms module."""

from __future__ import annotations

import unittest
import unittest.mock

from bitkey import comms


class TestComms(unittest.TestCase):
    """Test cases for the `comms` module."""

    def test_port_spec_to_usb_device(self: TestComms) -> None:
        """Test case for the USB spec conversion function."""
        transaction = comms.NFCTransaction
        self.assertEqual("usb", transaction.port_spec_to_usb_device("usb"))
        self.assertEqual(
            "usb:003:009", transaction.port_spec_to_usb_device("usb:003:009")
        )
        self.assertEqual(
            "usb:054c:02e1", transaction.port_spec_to_usb_device("usb:054c:02e1")
        )
        self.assertEqual(
            "usb:/dev/hidraw0", transaction.port_spec_to_usb_device("/dev/hidraw0")
        )

        with unittest.mock.patch("bitkey.comms.util.usb_dev_from_port") as mock_dev_from_port:
            mock_dev_from_port.return_value = None

            with self.assertRaises(RuntimeError):
                transaction.port_spec_to_usb_device("1-6.4.4.2")

            mock_dev_from_port.return_value = [500, 199]
            self.assertEqual(
                "usb:500:199", transaction.port_spec_to_usb_device("3-6.4.4.4.2")
            )


if __name__ == "__main__":
    unittest.main()
