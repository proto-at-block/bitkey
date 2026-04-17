"""Tests for invoke FWUP tasks."""

from __future__ import annotations

import pathlib
import unittest
import unittest.mock

from invoke import Exit

from tasks import fwup as task_fwup


class TestFwupTasks(unittest.TestCase):
    """Tests for firmware update invoke tasks."""

    def test_bl_upgrade_task_uses_w1a_fwup_params(self) -> None:
        """Ensures W1 bootloader upgrades resolve FWUP params from w1a."""
        binary = pathlib.Path("loader.signed.bin")
        signature = pathlib.Path("loader.detached_signature")
        metadata = pathlib.Path("loader.detached_metadata")

        with unittest.mock.patch.object(
            task_fwup, "check_exists", side_effect=[binary, signature, metadata]
        ), unittest.mock.patch.object(
            task_fwup.Wallet, "chip_name_to_role", return_value=123
        ), unittest.mock.patch.object(
            task_fwup.wallet_pb2.mcu_role, "Name", return_value="MCU_ROLE_CORE"
        ), unittest.mock.patch.object(
            task_fwup.FwupParams, "from_product", return_value=unittest.mock.sentinel.params
        ) as mock_from_product, unittest.mock.patch.object(
            task_fwup, "NFCTransaction"
        ), unittest.mock.patch.object(
            task_fwup, "WalletComms"
        ), unittest.mock.patch.object(
            task_fwup, "FirmwareUpdater"
        ) as mock_updater_cls, unittest.mock.patch.object(
            task_fwup.click, "secho"
        ):
            mock_updater_cls.return_value.bl_upgrade.return_value = True

            task_fwup.bl_upgrade.body(
                None,
                binary=str(binary),
                signature=str(signature),
                metadata=str(metadata),
                product="W1",
                mcu="EFR32",
            )

        mock_from_product.assert_called_once_with("w1a")

    def test_bl_upgrade_task_exits_when_fwup_params_are_missing(self) -> None:
        """Ensures missing FWUP params fail cleanly before attempting an update."""
        binary = pathlib.Path("loader.signed.bin")
        signature = pathlib.Path("loader.detached_signature")
        metadata = pathlib.Path("loader.detached_metadata")

        with unittest.mock.patch.object(
            task_fwup, "check_exists", side_effect=[binary, signature, metadata]
        ), unittest.mock.patch.object(
            task_fwup.Wallet, "chip_name_to_role", return_value=123
        ), unittest.mock.patch.object(
            task_fwup.wallet_pb2.mcu_role, "Name", return_value="MCU_ROLE_CORE"
        ), unittest.mock.patch.object(
            task_fwup.FwupParams, "from_product", return_value=None
        ), unittest.mock.patch.object(
            task_fwup.click, "secho"
        ) as mock_secho, unittest.mock.patch.object(
            task_fwup, "FirmwareUpdater"
        ) as mock_updater_cls:
            with self.assertRaises(Exit) as exc:
                task_fwup.bl_upgrade.body(
                    None,
                    binary=str(binary),
                    signature=str(signature),
                    metadata=str(metadata),
                    product="w1",
                    mcu="efr32",
                )

        self.assertEqual(1, exc.exception.code)
        mock_secho.assert_called_once_with(
            "Failed to determine FWUP params for w1a.",
            fg="red",
        )
        mock_updater_cls.assert_not_called()

    def test_bl_upgrade_task_exits_nonzero_on_failure(self) -> None:
        """Ensures bootloader task failures propagate to callers."""
        binary = pathlib.Path("loader.signed.bin")
        signature = pathlib.Path("loader.detached_signature")
        metadata = pathlib.Path("loader.detached_metadata")

        with unittest.mock.patch.object(
            task_fwup, "check_exists", side_effect=[binary, signature, metadata]
        ), unittest.mock.patch.object(
            task_fwup.Wallet, "chip_name_to_role", return_value=123
        ), unittest.mock.patch.object(
            task_fwup.wallet_pb2.mcu_role, "Name", return_value="MCU_ROLE_CORE"
        ), unittest.mock.patch.object(
            task_fwup.FwupParams, "from_product", return_value=unittest.mock.sentinel.params
        ), unittest.mock.patch.object(
            task_fwup, "NFCTransaction"
        ), unittest.mock.patch.object(
            task_fwup, "WalletComms"
        ), unittest.mock.patch.object(
            task_fwup, "FirmwareUpdater"
        ) as mock_updater_cls, unittest.mock.patch.object(
            task_fwup.click, "secho"
        ) as mock_secho:
            mock_updater_cls.return_value.bl_upgrade.return_value = False

            with self.assertRaises(Exit) as exc:
                task_fwup.bl_upgrade.body(
                    None,
                    binary=str(binary),
                    signature=str(signature),
                    metadata=str(metadata),
                    product="w3",
                    mcu="efr32",
                )

        self.assertEqual(1, exc.exception.code)
        mock_secho.assert_called_once_with("Bootloader upgrade failed.", fg="red")

    def test_bl_upgrade_task_reports_success(self) -> None:
        """Ensures bootloader task still reports success on completion."""
        binary = pathlib.Path("loader.signed.bin")
        signature = pathlib.Path("loader.detached_signature")
        metadata = pathlib.Path("loader.detached_metadata")

        with unittest.mock.patch.object(
            task_fwup, "check_exists", side_effect=[binary, signature, metadata]
        ), unittest.mock.patch.object(
            task_fwup.Wallet, "chip_name_to_role", return_value=123
        ), unittest.mock.patch.object(
            task_fwup.wallet_pb2.mcu_role, "Name", return_value="MCU_ROLE_CORE"
        ), unittest.mock.patch.object(
            task_fwup.FwupParams, "from_product", return_value=unittest.mock.sentinel.params
        ), unittest.mock.patch.object(
            task_fwup, "NFCTransaction"
        ), unittest.mock.patch.object(
            task_fwup, "WalletComms"
        ), unittest.mock.patch.object(
            task_fwup, "FirmwareUpdater"
        ) as mock_updater_cls, unittest.mock.patch.object(
            task_fwup.click, "secho"
        ) as mock_secho:
            mock_updater_cls.return_value.bl_upgrade.return_value = True

            task_fwup.bl_upgrade.body(
                None,
                binary=str(binary),
                signature=str(signature),
                metadata=str(metadata),
                product="w3",
                mcu="efr32",
            )

        mock_secho.assert_called_once_with(
            "Bootloader upgrade finished successfully.", fg="green"
        )


if __name__ == "__main__":
    unittest.main()
