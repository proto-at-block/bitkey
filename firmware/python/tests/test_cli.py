"""Test cases for the CLI module."""

from __future__ import annotations

import base64
import pathlib
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

    def test_device_info_includes_chip_id_hex(self: TestCli) -> None:
        """Ensures device_info prints chipId in hex, not base64."""
        with unittest.mock.patch("bitkey.cli.WalletComms") as mock_comms:
            comms = mock_comms.return_value

            rsp = wallet_pb2.wallet_rsp()
            core_chip_id = bytes.fromhex("7c31fafffeaa0f2e")
            uxc_chip_id = bytes.fromhex("1a003e000e50424557383420")
            core_mcu = rsp.device_info_rsp.device_info_mcus.add()
            core_mcu.chip_id = core_chip_id
            uxc_mcu = rsp.device_info_rsp.device_info_mcus.add()
            uxc_mcu.chip_id = uxc_chip_id
            comms.transceive.return_value = rsp

            result = self.runner.invoke(cli.cli, ["device-info"])
            self.assertEqual(0, result.exit_code)

            core_hex = core_chip_id.hex()
            uxc_hex = uxc_chip_id.hex()
            core_b64 = base64.b64encode(core_chip_id).decode("ascii")
            uxc_b64 = base64.b64encode(uxc_chip_id).decode("ascii")
            self.assertIn(f"\"chipId\": \"{core_hex}\"", result.output)
            self.assertIn(f"\"chipId\": \"{uxc_hex}\"", result.output)
            self.assertNotIn(f"\"chipId\": \"{core_b64}\"", result.output)
            self.assertNotIn(f"\"chipId\": \"{uxc_b64}\"", result.output)

    def test_stress_respects_count_and_delay(self: TestCli) -> None:
        """Ensures the stress command runs a bounded number of metadata calls."""
        with unittest.mock.patch("bitkey.cli.Wallet") as mock_wallet_cls, \
                unittest.mock.patch("bitkey.cli.time.sleep") as mock_sleep:
            wallet = mock_wallet_cls.return_value
            wallet.__enter__.return_value = wallet

            result = self.runner.invoke(
                cli.cli, ["stress", "--count", "3", "--delay", "0.25"]
            )

            self.assertEqual(0, result.exit_code)
            self.assertEqual(3, wallet.metadata.call_count)
            mock_sleep.assert_has_calls([
                unittest.mock.call(0.25),
                unittest.mock.call(0.25),
            ])
            self.assertEqual("1\n2\n3\n", result.output)

    def test_bl_upgrade_command(self: TestCli) -> None:
        """Ensures the CLI forwards bootloader-upgrade artifacts to the updater."""
        with self.runner.isolated_filesystem():
            binary = pathlib.Path("loader.signed.bin")
            signature = pathlib.Path("loader.detached_signature")
            metadata = pathlib.Path("loader.detached_metadata")

            binary.write_bytes(b"bootloader")
            signature.write_bytes(b"signature")
            metadata.write_bytes(b"metadata")

            with unittest.mock.patch.object(cli.Wallet, "fwup_params", return_value=unittest.mock.sentinel.params), \
                    unittest.mock.patch("bitkey.cli.FirmwareUpdater") as mock_updater_cls:
                updater = mock_updater_cls.return_value
                updater.bl_upgrade.return_value = True

                result = self.runner.invoke(
                    cli.cli,
                    [
                        "-p", "w3",
                        "bl-upgrade",
                        "--mcu", "EFR32",
                        "--binary", str(binary),
                        "--signature", str(signature),
                        "--metadata", str(metadata),
                    ],
                )

            self.assertEqual(0, result.exit_code)
            updater.bl_upgrade.assert_called_once_with(
                mcu="EFR32",
                image=binary,
                signature=signature,
                metadata=metadata,
                params=unittest.mock.sentinel.params,
                bl_size=48 * 1024,
            )

    def test_bl_upgrade_rejects_non_efr32(self: TestCli) -> None:
        """Ensures the bootloader-upgrade CLI only accepts EFR32."""
        with self.runner.isolated_filesystem():
            binary = pathlib.Path("loader.signed.bin")
            signature = pathlib.Path("loader.detached_signature")
            metadata = pathlib.Path("loader.detached_metadata")

            binary.write_bytes(b"bootloader")
            signature.write_bytes(b"signature")
            metadata.write_bytes(b"metadata")

            with unittest.mock.patch("bitkey.cli.FirmwareUpdater") as mock_updater_cls:
                result = self.runner.invoke(
                    cli.cli,
                    [
                        "-p", "w3",
                        "bl-upgrade",
                        "--mcu", "STM32U5",
                        "--binary", str(binary),
                        "--signature", str(signature),
                        "--metadata", str(metadata),
                    ],
                )

            self.assertNotEqual(0, result.exit_code)
            self.assertIn("'STM32U5' is not 'EFR32'", result.output)
            mock_updater_cls.assert_not_called()


if __name__ == "__main__":
    unittest.main()
