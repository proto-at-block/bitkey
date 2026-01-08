"""Test cases for the CLI module."""

from __future__ import annotations

import unittest
import unittest.mock

import click.testing
from bitkey_proto import mfgtest_pb2, wallet_pb2

from bitkey import cli


class TestCli(unittest.TestCase):
    """Test cases for the BitKey command-line interface."""

    def setUp(self: TestCli) -> None:
        """Creates the test runner."""
        self.runner = click.testing.CliRunner()

        self.nfc_patcher = unittest.mock.patch(
            "bitkey.comms.nfc", autospec=True)
        self.mock_nfc = self.nfc_patcher.start()
        self.reader = self.mock_nfc.ContactlessFrontend.return_value
        self.tag = self.reader.connect.return_value
        self.addCleanup(self.nfc_patcher.stop)

    def test_help(self: TestCli) -> None:
        """Validates that help does not attempt to connect to a reader instance."""
        result = self.runner.invoke(cli.cli, ["--help"])
        self.assertEqual(0, result.exit_code)
        self.assertIn("--nfc-port", result.output)
        self.assertIn("--serial-port", result.output)
        self.mock_nfc.ContactlessFrontend.assert_not_called()

    def test_wallet_create(self: TestCli) -> None:
        """Tests for the creation of the Wallet instance."""
        with unittest.mock.patch(
            "bitkey.comms.NFCTransaction.port_spec_to_usb_device"
        ) as mock_spec:
            mock_spec.return_value = "usb:0:1"

            result = self.runner.invoke(
                cli.cli, ["--nfc-port", "/dev/hidraw0", "reset"]
            )
            self.assertEqual(0, result.exit_code)

            mock_spec.assert_called_with("/dev/hidraw0")
            self.mock_nfc.ContactlessFrontend.assert_called_with("usb:0:1")

    def test_wallet_mfgtest_spi_loopback(self: TestCli) -> None:
        """Tests for the SPI loopback command."""
        with unittest.mock.patch("bitkey.cli.WalletComms") as mock_comms:
            comms = mock_comms.return_value
            data = [0xAA, 0x55]

            # Match between the SPI data in the command and response.
            rsp = wallet_pb2.wallet_rsp()
            msg = mfgtest_pb2.mfgtest_spi_loopback_rsp()
            msg.rsp_status = msg.mfgtest_spi_loopback_rsp_status.SUCCESS
            msg.data = bytes(data)
            rsp.mfgtest_spi_loopback_rsp.CopyFrom(msg)
            comms.transceive.return_value = rsp

            result = self.runner.invoke(
                cli.cli, ["mfgtest-spi-loopback",
                          "FINGERPRINT"] + list(map(str, data))
            )
            self.assertEqual(0, result.exit_code)
            self.assertIn("SUCCESS", result.output)

            # Workaround applied.
            rsp = wallet_pb2.wallet_rsp()
            msg = mfgtest_pb2.mfgtest_spi_loopback_rsp()
            msg.rsp_status = msg.mfgtest_spi_loopback_rsp_status.FAIL
            msg.data = bytes([0x55, 0x2A])
            rsp.mfgtest_spi_loopback_rsp.CopyFrom(msg)
            comms.transceive.return_value = rsp

            result = self.runner.invoke(
                cli.cli, ["mfgtest-spi-loopback", "FINGERPRINT"] +
                list(f"0x{b:02X}" for b in data)
            )
            self.assertEqual(0, result.exit_code)
            self.assertIn("SUCCESS", result.output)

            # Failing test case (workaround applied but data is invalid).
            rsp = wallet_pb2.wallet_rsp()
            msg = mfgtest_pb2.mfgtest_spi_loopback_rsp()
            msg.rsp_status = msg.mfgtest_spi_loopback_rsp_status.FAIL
            msg.data = bytes([0x55, 0x80])
            rsp.mfgtest_spi_loopback_rsp.CopyFrom(msg)
            comms.transceive.return_value = rsp

            result = self.runner.invoke(
                cli.cli, ["mfgtest-spi-loopback", "FINGERPRINT"] +
                list(f"0x{b:02X}" for b in data)
            )
            self.assertEqual(0, result.exit_code)
            self.assertIn("FAIL", result.output)


if __name__ == "__main__":
    unittest.main()
