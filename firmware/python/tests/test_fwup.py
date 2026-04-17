"""Test cases for the FWUP module."""

from __future__ import annotations

import tempfile
import unittest
import unittest.mock
from pathlib import Path

from bitkey import fwup
from bitkey_proto import wallet_pb2 as wallet_pb


class TestFwup(unittest.TestCase):
    """Test cases for the FWUP module."""

    def test_fwup_params_from_product(self):
        """Validates that the expected FWUP parameters are returned per product.

        Parameters are based on the `partitions.yml` for the target.
        """
        params = fwup.FwupParams.from_product("w1a")
        self.assertEqual(params.version, "")
        self.assertEqual(params.signature_offset, ((632 * 1024) - 64))
        self.assertEqual(params.app_props_offset, 1024)
        self.assertEqual(params.signature_size, 64)
        self.assertEqual(params.chunk_size, 452)

        params = fwup.FwupParams.from_product("w3a-core")
        self.assertEqual(params.version, "")
        self.assertEqual(params.signature_offset, ((632 * 1024) - 64))
        self.assertEqual(params.app_props_offset, 1024)
        self.assertEqual(params.signature_size, 64)
        self.assertEqual(params.chunk_size, 452)

        params = fwup.FwupParams.from_product("w3a-uxc")
        self.assertEqual(params.version, "")
        self.assertEqual(params.signature_offset, ((896 * 1024) - 64))
        self.assertEqual(params.app_props_offset, 1024)
        self.assertEqual(params.signature_size, 64)
        self.assertEqual(params.chunk_size, 448)

        params = fwup.FwupParams.from_product("w4")
        self.assertIsNone(params)

    def test_bootloader_upgrade_uses_partition_metadata_layout(self):
        """Validates bootloader upgrade derives metadata placement from the partition config."""
        with tempfile.TemporaryDirectory() as tmpdir:
            image = Path(tmpdir) / "loader.signed.bin"
            signature = Path(tmpdir) / "loader.detached_signature"
            metadata = Path(tmpdir) / "loader.detached_metadata"

            image.write_bytes(b"bootloader")
            signature.write_bytes(b"s" * 64)
            metadata.write_bytes(b"metadata")

            params = fwup.FwupParams(
                version="1.2.3",
                chunk_size=452,
                signature_offset=0,
                app_props_offset=1024,
                signature_size=64,
            )

            wallet = unittest.mock.Mock()
            wallet.product = "w3"
            wallet.comms = unittest.mock.sentinel.comms
            wallet.chip_name_to_role.return_value = 123
            wallet.partition_config_name.return_value = "w3a-core"

            mocked_update = unittest.mock.Mock()
            mocked_update.params = params
            mocked_update.start.return_value = fwup.FwupStartSuccess()
            mocked_update.transfer_bytes.return_value = True
            mocked_update.finish.return_value = unittest.mock.Mock(
                fwup_finish_rsp=unittest.mock.Mock(
                    rsp_status=1,
                    SUCCESS=1,
                )
            )

            with unittest.mock.patch("bitkey.fwup.Fwup", return_value=mocked_update) as mocked_fwup, \
                    unittest.mock.patch(
                        "bitkey.fwup.get_bootloader_metadata_offset_and_size",
                        return_value=(1234, 2048),
            ) as mocked_metadata_layout:
                updater = fwup.FirmwareUpdater(wallet)
                result = updater.bl_upgrade(
                    mcu="EFR32",
                    image=image,
                    signature=signature,
                    metadata=metadata,
                    params=params,
                )

            self.assertTrue(result)
            mocked_fwup.assert_called_once_with(
                bundle_dir=None,
                binary=image,
                signature=signature,
                start_sequence_id=0,
                comms=wallet.comms,
                mcu_role=123,
                fwup_params=params,
            )
            self.assertEqual(params.app_props_offset, 0)
            self.assertEqual(params.signature_offset, (48 * 1024) - 64)
            mocked_metadata_layout.assert_called_once_with("w3a-core")
            mocked_update.transfer_bytes.assert_has_calls(
                [
                    unittest.mock.call(b"bootloader", 0, 0),
                    unittest.mock.call(b"s" * 64, 0, (48 * 1024) - 64),
                    unittest.mock.call(
                        b"metadata", 0, (48 * 1024) - 64 - 2048),
                ]
            )
            mocked_update.finish.assert_called_once_with(True)

    def test_update_info_reports_active_and_target_versions(self):
        """Ensures slot selection reports active and destination versions separately."""
        updater = fwup.Fwup(
            bundle_dir=None,
            fwup_params=fwup.FwupParams(
                version="1.1.26",
                chunk_size=452,
                signature_offset=0,
                app_props_offset=0,
                signature_size=64,
            ),
            comms=unittest.mock.Mock(),
        )

        rsp = wallet_pb.wallet_rsp()
        rsp.meta_rsp.active_slot = wallet_pb.firmware_slot.SLOT_B
        rsp.meta_rsp.meta_slot_a.version.major = 1
        rsp.meta_rsp.meta_slot_a.version.minor = 1
        rsp.meta_rsp.meta_slot_a.version.patch = 24
        rsp.meta_rsp.meta_slot_b.version.major = 1
        rsp.meta_rsp.meta_slot_b.version.minor = 1
        rsp.meta_rsp.meta_slot_b.version.patch = 16
        updater.comms.transceive.return_value = rsp

        update_info = updater._update_info()

        self.assertEqual(wallet_pb.firmware_slot.SLOT_B,
                         update_info.active_slot)
        self.assertEqual("1.1.16", str(update_info.active_version))
        self.assertEqual(wallet_pb.firmware_slot.SLOT_A,
                         update_info.target_slot)
        self.assertEqual("1.1.24", str(update_info.target_version))

    def test_prepare_skips_when_active_version_matches_bundle(self):
        """Ensures equal-version requests are treated as a successful skip."""
        updater = fwup.Fwup(
            bundle_dir=None,
            fwup_params=fwup.FwupParams(
                version="1.1.26",
                chunk_size=452,
                signature_offset=0,
                app_props_offset=0,
                signature_size=64,
            ),
            comms=unittest.mock.Mock(),
        )
        updater.bundle_dir = Path("/tmp/fwup-bundle")
        updater.manifest_dict = {
            "assets": {
                "application_a": {
                    "image": {"name": "app-a.bin"},
                    "signature": {"name": "app-a.sig"},
                },
                "application_b": {
                    "image": {"name": "app-b.bin"},
                    "signature": {"name": "app-b.sig"},
                },
            }
        }

        rsp = wallet_pb.wallet_rsp()
        rsp.meta_rsp.active_slot = wallet_pb.firmware_slot.SLOT_B
        rsp.meta_rsp.meta_slot_a.version.major = 1
        rsp.meta_rsp.meta_slot_a.version.minor = 1
        rsp.meta_rsp.meta_slot_a.version.patch = 24
        rsp.meta_rsp.meta_slot_b.version.major = 1
        rsp.meta_rsp.meta_slot_b.version.minor = 1
        rsp.meta_rsp.meta_slot_b.version.patch = 26
        updater.comms.transceive.return_value = rsp

        result = updater._prepare()

        self.assertIsInstance(result, fwup.FwupStartSkipped)
        self.assertIsNone(updater.binary)
        self.assertIsNone(updater.signature)

    def test_prepare_rejects_when_active_version_is_newer_than_bundle(self):
        """Ensures downgrade requests are rejected rather than reported as skipped."""
        updater = fwup.Fwup(
            bundle_dir=None,
            fwup_params=fwup.FwupParams(
                version="1.1.26",
                chunk_size=452,
                signature_offset=0,
                app_props_offset=0,
                signature_size=64,
            ),
            comms=unittest.mock.Mock(),
        )

        rsp = wallet_pb.wallet_rsp()
        rsp.meta_rsp.active_slot = wallet_pb.firmware_slot.SLOT_B
        rsp.meta_rsp.meta_slot_a.version.major = 1
        rsp.meta_rsp.meta_slot_a.version.minor = 1
        rsp.meta_rsp.meta_slot_a.version.patch = 24
        rsp.meta_rsp.meta_slot_b.version.major = 1
        rsp.meta_rsp.meta_slot_b.version.minor = 1
        rsp.meta_rsp.meta_slot_b.version.patch = 27
        updater.comms.transceive.return_value = rsp

        result = updater._prepare()

        self.assertIsInstance(result, fwup.FwupStartFailure)
        self.assertIsNone(updater.binary)
        self.assertIsNone(updater.signature)

    def test_start_returns_skipped_when_prepare_reports_skip(self):
        """Ensures the no-op path is not reported as a start failure."""
        updater = fwup.Fwup(
            bundle_dir=None,
            fwup_params=fwup.FwupParams(
                version="1.1.26",
                chunk_size=452,
                signature_offset=0,
                app_props_offset=0,
                signature_size=64,
            ),
            comms=unittest.mock.Mock(),
        )

        with unittest.mock.patch.object(updater, "_prepare", return_value=fwup.FwupStartSkipped()):
            result = updater.start()

        self.assertIsInstance(result, fwup.FwupStartSkipped)
        updater.comms.transceive.assert_not_called()

    def test_fwup_local_returns_true_when_update_is_not_needed(self):
        """Ensures bundle FWUP treats the no-op path as a successful skip."""
        updater = fwup.FirmwareUpdater(wallet=unittest.mock.Mock())
        mocked_update = unittest.mock.Mock()
        mocked_update.start.return_value = fwup.FwupStartSkipped()

        with tempfile.TemporaryDirectory() as tmpdir, \
                unittest.mock.patch.object(updater, "_build_updater", return_value=mocked_update):
            result = updater.fwup_local(bundle=Path(tmpdir))

        self.assertTrue(result)
        mocked_update.transfer.assert_not_called()
        mocked_update.finish.assert_not_called()

    def test_fwup_local_prints_fwup_start_rsp_on_start_failure(self):
        """Validates fwup-local surfaces the device's fwup_start_rsp."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir)
            (bundle / "fwup-manifest.yml").write_text("fwup_bundle: {}\n")

            wallet = unittest.mock.Mock()
            updater = fwup.FirmwareUpdater(wallet)

            rsp = wallet_pb.wallet_rsp()
            rsp.fwup_start_rsp.rsp_status = wallet_pb.fwup_start_rsp.ERROR

            mocked_update = unittest.mock.Mock()
            mocked_update.start.return_value = fwup.FwupStartFailure(response=rsp)

            with unittest.mock.patch.object(
                updater,
                "_build_updater",
                return_value=mocked_update,
            ) as mocked_build_updater, unittest.mock.patch(
                "builtins.print"
            ) as mocked_print:
                result = updater.fwup_local(bundle)

            self.assertFalse(result)
            mocked_build_updater.assert_called_once()
            self.assertEqual(mocked_print.call_args_list[0], unittest.mock.call("Failed to start"))
            self.assertEqual(str(mocked_print.call_args_list[1].args[0]).strip(), "rsp_status: ERROR")

    def test_fwup_local_prints_status_only_start_failure_response(self):
        """Validates fwup-local falls back to the full response for status-only failures."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir)
            (bundle / "fwup-manifest.yml").write_text("fwup_bundle: {}\n")

            wallet = unittest.mock.Mock()
            updater = fwup.FirmwareUpdater(wallet)

            rsp = wallet_pb.wallet_rsp()
            rsp.status = wallet_pb.ERROR

            mocked_update = unittest.mock.Mock()
            mocked_update.start.return_value = fwup.FwupStartFailure(response=rsp)

            with unittest.mock.patch.object(
                updater,
                "_build_updater",
                return_value=mocked_update,
            ), unittest.mock.patch("builtins.print") as mocked_print:
                result = updater.fwup_local(bundle)

            self.assertFalse(result)
            self.assertEqual(mocked_print.call_args_list[0], unittest.mock.call("Failed to start"))
            self.assertEqual(str(mocked_print.call_args_list[1].args[0]).strip(), "status: ERROR")


if __name__ == "__main__":
    unittest.main()
