"""Tests for invoke FWUP tasks."""

from __future__ import annotations

import pathlib
import unittest
import unittest.mock

from invoke import Exit

from tasks import fwup as task_fwup


class TestFwupTasks(unittest.TestCase):
    """Tests for firmware update invoke tasks."""

    def test_fwup_publishable_delta_update_rejects_versions_below_release_floor(self) -> None:
        """Ensures delta releases are not generated below the publish floor."""
        self.assertFalse(
            task_fwup._fwup_publishable_delta_update("w3a", "1.1.99", "1.2.1")
        )

    def test_fwup_publishable_delta_update_rejects_w1_versions_below_release_floor(self) -> None:
        """Ensures the publish floor also applies to W1 releases."""
        self.assertFalse(
            task_fwup._fwup_publishable_delta_update("w1a", "1.0.45", "1.2.0")
        )

    def test_fwup_publishable_delta_update_allows_release_floor_version(self) -> None:
        """Ensures the publish floor is inclusive."""
        self.assertTrue(
            task_fwup._fwup_publishable_delta_update("w3a", "1.2.0", "1.2.1")
        )

    def test_fwup_valid_delta_update_requires_forward_upgrade(self) -> None:
        """Ensures equal versions are still rejected."""
        self.assertFalse(
            task_fwup._fwup_valid_delta_update("w3a", "1.2.0", "1.2.0")
        )

    def test_fwup_valid_delta_update_allows_local_w1_version_below_release_floor(self) -> None:
        """Ensures local tooling can still generate supported legacy W1 patches."""
        self.assertTrue(
            task_fwup._fwup_valid_delta_update("w1a", "1.0.45", "1.2.0")
        )

    def test_bundle_delta_allows_valid_delta_bundle(self) -> None:
        """Ensures direct bundle generation accepts patches within size limits."""
        mock_delta_bundle = unittest.mock.Mock(valid=True, max_size=123)

        with unittest.mock.patch.object(
            task_fwup, "FwupBundler"
        ) as mock_bundler, unittest.mock.patch.object(
            task_fwup, "load_patch_signing_key", return_value="key"
        ), unittest.mock.patch.object(
            task_fwup.click, "echo"
        ) as mock_echo:
            mock_bundler.return_value.generate_delta.return_value = mock_delta_bundle

            task_fwup.bundle_delta.body(
                None,
                product="w3a",
                hardware_revision="pdvt",
                image_type="prod",
                from_version="1.2.0",
                to_version="1.2.1",
                from_dir="/tmp/from-version",
                to_dir="/tmp/to-version",
                bundle_dir="/tmp/bundle",
            )

        mock_bundler.assert_called_once_with("w3a", "pdvt", "prod")
        mock_bundler.return_value.generate_delta.assert_called_once()
        self.assertIn(
            unittest.mock.call("Patch max size: 123 bytes"),
            mock_echo.mock_calls,
        )

    def test_bundle_delta_exits_when_delta_bundle_is_invalid(self) -> None:
        """Ensures direct bundle generation fails on over-sized patches."""
        mock_delta_bundle = unittest.mock.Mock(
            valid=False,
            max_size=122881,
            invalid_details=["core: a2b.patch=122881 (limit 122880)"],
        )

        with unittest.mock.patch.object(
            task_fwup, "FwupBundler"
        ) as mock_bundler, unittest.mock.patch.object(
            task_fwup, "load_patch_signing_key", return_value="key"
        ), unittest.mock.patch.object(
            task_fwup.click, "echo"
        ) as mock_echo, unittest.mock.patch.object(
            task_fwup.click, "style", side_effect=lambda text, **_: text
        ):
            mock_bundler.return_value.generate_delta.return_value = mock_delta_bundle

            with self.assertRaises(Exit) as exc:
                task_fwup.bundle_delta.body(
                    None,
                    product="w3a",
                    hardware_revision="pdvt",
                    image_type="prod",
                    from_version="1.2.0",
                    to_version="1.2.1",
                    from_dir="/tmp/from-version",
                    to_dir="/tmp/to-version",
                    bundle_dir="/tmp/bundle",
                )

        self.assertEqual(1, exc.exception.code)
        self.assertIn(
            unittest.mock.call(
                "✗ Patch invalid (core: a2b.patch=122881 (limit 122880))"
            ),
            mock_echo.mock_calls,
        )

    def test_delta_release_local_generates_explicit_versions_below_release_floor(self) -> None:
        """Ensures explicit local patch generation still works below the publish floor."""
        mock_delta_bundle = unittest.mock.Mock(valid=True, max_size=123)

        with unittest.mock.patch.object(
            task_fwup, "FwupBundler"
        ) as mock_bundler, unittest.mock.patch.object(
            task_fwup, "load_patch_signing_key", return_value="key"
        ), unittest.mock.patch.object(
            task_fwup.click, "echo"
        ) as mock_echo:
            mock_bundler.return_value.generate_delta.return_value = mock_delta_bundle

            task_fwup.delta_release_local.body(
                None,
                to_version="1.2.1",
                to_dir="/tmp/to-version",
                hw_revision="evt",
                revision="deadbeef",
                bearer_token="token",
                product="w3a",
                dont_upload=True,
                from_version="1.1.99",
                from_dir="/tmp/from-version",
            )

        mock_bundler.assert_called_once_with("w3a", "evt", "dev")
        mock_bundler.return_value.generate_delta.assert_called_once()
        self.assertIn(
            unittest.mock.call("Skipping upload"),
            mock_echo.mock_calls,
        )

    def test_delta_release_local_skips_memfault_versions_below_release_floor(self) -> None:
        """Ensures publish-style local flows still respect the release floor."""
        with unittest.mock.patch.object(
            task_fwup, "FwupBundler"
        ) as mock_bundler, unittest.mock.patch.object(
            task_fwup, "released_versions", return_value=["1.1.99"]
        ), unittest.mock.patch.object(
            task_fwup.click, "echo"
        ):
            task_fwup.delta_release_local.body(
                None,
                to_version="1.2.1",
                to_dir="/tmp/to-version",
                hw_revision="evt",
                revision="deadbeef",
                bearer_token="token",
                product="w3a",
            )

        mock_bundler.assert_not_called()

    def test_delta_release_local_skips_upload_for_explicit_versions_below_release_floor(self) -> None:
        """Ensures explicit local generation below the floor does not upload a release."""
        mock_delta_bundle = unittest.mock.Mock(valid=True, max_size=123)

        with unittest.mock.patch.object(
            task_fwup, "FwupBundler"
        ) as mock_bundler, unittest.mock.patch.object(
            task_fwup, "load_patch_signing_key", return_value="key"
        ), unittest.mock.patch.object(
            task_fwup.sh, "memfault"
        ) as mock_memfault, unittest.mock.patch.object(
            task_fwup.click, "echo"
        ) as mock_echo:
            mock_bundler.return_value.generate_delta.return_value = mock_delta_bundle

            task_fwup.delta_release_local.body(
                None,
                to_version="1.2.1",
                to_dir="/tmp/to-version",
                hw_revision="evt",
                revision="deadbeef",
                bearer_token="token",
                product="w3a",
                from_version="1.1.99",
                from_dir="/tmp/from-version",
            )

        mock_memfault.assert_not_called()
        self.assertIn(
            unittest.mock.call(
                "Skipping upload for 1.1.99 - delta releases require from_version >= 1.2.0 and < 1.2.1"
            ),
            mock_echo.mock_calls,
        )

    def test_delta_release_local_strict_exits_on_invalid_delta(self) -> None:
        """Ensures strict local delta generation fails on invalid patches."""
        mock_delta_bundle = unittest.mock.Mock(
            valid=False,
            max_size=122881,
            invalid_details=["core: a2b.patch=122881 (limit 122880)"],
        )

        with unittest.mock.patch.object(
            task_fwup, "FwupBundler"
        ) as mock_bundler, unittest.mock.patch.object(
            task_fwup, "load_patch_signing_key", return_value="key"
        ), unittest.mock.patch.object(
            task_fwup.click, "echo"
        ) as mock_echo:
            mock_bundler.return_value.generate_delta.return_value = mock_delta_bundle

            with self.assertRaises(Exit) as exc:
                task_fwup.delta_release_local.body(
                    None,
                    to_version="1.2.1",
                    to_dir="/tmp/to-version",
                    hw_revision="pdvt",
                    revision="deadbeef",
                    bearer_token="token",
                    image_type="prod",
                    dont_upload=True,
                    from_version="1.2.0",
                    from_dir="/tmp/from-version",
                    product="w3a",
                    strict=True,
                )

        self.assertEqual(1, exc.exception.code)
        self.assertIn(
            unittest.mock.call(
                "Can't release 1.2.0 -- patch invalid (core: a2b.patch=122881 (limit 122880))"
            ),
            mock_echo.mock_calls,
        )

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
        mock_secho.assert_called_once_with(
            "Bootloader upgrade failed.", fg="red")

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
