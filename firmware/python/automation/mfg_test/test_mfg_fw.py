"""Manufacturing firmware test suite."""

from __future__ import annotations

import logging
import sys
import time

import allure
import bitkey.bitlog as bitlog
import pytest
from bitkey_proto import mfgtest_pb2 as mfgtest_pb
from bitkey_proto import wallet_pb2 as wallet_pb
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet

from ..conftest import PlatformConfig
from ..inv_commands import Inv
from .. import util

# Device capacitive touch port identifier.
_CAP_TOUCH_PORT: str = "PORT_B"

# Device capacitive touch PIN number.
_CAP_TOUCH_PIN: int = 1

# Takes 11 seconds for the BIST to execute.
_BIST_FINGERPRINT_SELFTEST_TIME: int = 11

# Time (seconds) for the wallet to reset.
_WALLET_RESET_TIME: int = 10

# Bitlog event for fuel gauge initialization.
_FUEL_GAUGE_INIT_EVENT: int = 4

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class TestClassMfgFW:

    @pytest.fixture(scope="class", autouse=True)
    def setup(self, platform_config: PlatformConfig, request: pytest.FixtureRequest) -> None:
        """Pre-test setup. Programs a fresh build of the manufacturing firmware
        onto the device.

        :param platform_config: platform configuration for the device under test.
        :param request: PyTest fixture request object for command-line arguments.
        :returns: ``None``
        """
        manufacturing_images: list[tuple[str, str]] = []
        for chip_config in platform_config.chips.values():
            target: str = util.convert_target_app_name(
                chip_config.target,
                platform_config.type,
                platform_config.revision,
                "mfgtest"
            )
            manufacturing_images.append((chip_config.name, target))

        logger.info("Setup fixture")

        self.inv_task = Inv(request, platform_config)
        self.inv_task.clean()
        self.inv_task.build()
        self.inv_task.flash(targets=manufacturing_images)

    def _drain_logs(self, wallet: Wallet) -> list[bitlog.BitlogEvent]:
        """Drains bitlogs from a Wallet device.

        :param wallet: Wallet instance to retrieve logs from.
        :returns: list of bitlog events.
        """
        events: bytes = b""
        while True:
            rsp = wallet.events()
            logger.info(rsp)

            event_rsp = rsp.events_get_rsp
            status: int = event_rsp.rsp_status
            assert status == event_rsp.events_get_rsp_status.SUCCESS, "Failed to get events."

            event = event_rsp.fragment
            events += event.data
            if event.remaining_size == 0:
                break

        return bitlog.parse_events(events)

    def test_mfgtest_fingerprint_selftest(self, wallet: Wallet) -> None:
        """Verifies the fingerprint sensor self-test (BIST).

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        start_rsp = wallet.mfgtest_fingerprint_selftest_start()
        logger.info(start_rsp)
        assert start_rsp.mfgtest_fingerprint_rsp.rsp_status == wallet_pb.status.SUCCESS
        time.sleep(_BIST_FINGERPRINT_SELFTEST_TIME)

        result_rsp = wallet.mfgtest_fingerprint_selftest_get_result()
        logger.info(result_rsp)
        assert result_rsp.mfgtest_fingerprint_rsp.rsp_status == wallet_pb.status.SUCCESS

    def test_mfgtest_fingerprint_calibrate(self, wallet: Wallet) -> None:
        """Verifies the fingerprint sensor calibration.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        calibrate_rsp = wallet.mfgtest_fingerprint_calibrate()
        logger.info(calibrate_rsp)
        assert calibrate_rsp.mfgtest_fingerprint_rsp.rsp_status == wallet_pb.status.SUCCESS

    def test_mfgtest_security_enable(self, wallet: Wallet) -> None:
        """Verifies enabling fingerprint security mode.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        # **WARNING**: DO NOT CHANGE `real` to `True` or you will brick your device!
        security_rsp = wallet.mfgtest_fingerprint_security_enable(real=False)
        logger.info(security_rsp)
        assert security_rsp.mfgtest_fingerprint_rsp.rsp_status == wallet_pb.status.SUCCESS

    def test_mfgtest_security_mode(self, wallet: Wallet) -> None:
        """Verifies querying fingerprint security mode.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        security_rsp = wallet.mfgtest_fingerprint_security_mode()
        logger.info(security_rsp)
        assert security_rsp.mfgtest_fingerprint_rsp.rsp_status == wallet_pb.status.SUCCESS

    def test_device_id(self, wallet: Wallet) -> None:
        """Verifies querying the device ID.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        assy_serial_len: int = 16
        device_id_rsp = wallet.device_id().device_id_rsp
        logger.info(device_id_rsp)

        # Units may not have a programmed MLB or ASSY serial number, so only
        # validate the length if the response indicates they are present.
        assert not device_id_rsp.mlb_serial_valid or len(device_id_rsp.mlb_serial) == assy_serial_len
        assert not device_id_rsp.assy_serial_valid or len(device_id_rsp.assy_serial) == assy_serial_len

    def test_fuel(self, wallet: Wallet) -> None:
        """Verifies querying the fuel gauge.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        fuel_rsp = wallet.fuel().fuel_rsp
        logger.info(fuel_rsp)

        assert fuel_rsp.valid, "Expected to get a valid result from the fuel gauge."

    def test_mfgtest_gpio(self, wallet: Wallet) -> None:
        """Verifies GPIO read operation on the capacitive touch sensor.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        action_value: int = mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_action.Value(
            'READ')
        port_value: int = mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_port.Value(
            _CAP_TOUCH_PORT)

        rsp = wallet.mfgtest_gpio(action_value, port_value, _CAP_TOUCH_PIN)
        logger.info(rsp)

        # PIN is asserted when touch is not present.
        gpio_rsp = rsp.mfgtest_gpio_rsp
        assert gpio_rsp.output == 1, "Expected PIN to be inactive."

    def test_reset(self, wallet: Wallet) -> None:
        """Verifies device reset via NFC.

        :param wallet: Wallet instance for the device under test.
        :returns: ``None``
        """
        _ = self._drain_logs(wallet)

        try:
            _ = wallet.reset()
        except Exception as _:
            pass
        finally:
            # Wait for the device to reset.
            time.sleep(_WALLET_RESET_TIME)

        # Device will be locked after reset, so unlock in order to drain the
        # logs.
        success: bool = wallet.unlock_device(manufacturing=True)
        assert success, "Failed to unlock device."

        # Drain logs to confirm device reset.
        parsed_events: list[bitlog.BitlogEvent] = self._drain_logs(wallet)
        assert any(parsed_event.event == _FUEL_GAUGE_INIT_EVENT for parsed_event in parsed_events), "Expected fuel_gauge_init log"

    @pytest.mark.skip(reason="ESW-20955: FWUP tests are currently disabled.")
    def test_mfgtest_fwup_upgrade(self, platform_config: PlatformConfig, wallet: Wallet) -> None:
        """Verifies firmware upgrade from MFG Test image to SLOT B.

        :param platform_config: platform configuration for the device under test.
        :param wallet: Wallet device under test.
        :returns: ``None``
        """
        # TODO(ESW-20955): Update this with new FWUP fixture.
        initial_version, active_slot = self.get_active_version(wallet=wallet)
        logger.info(
            "Starting with version: %s, with activeSlot %s" % (
                initial_version, active_slot)
        )
        assert active_slot == 1, "Expected to start active on SLOT_A"

        new_version, active_slot = self._fwup_new(platform_config, wallet)
        assert active_slot == 2, "Expected to be active on SLOT_B"

    @allure.step("Bump version, build apps, and fwup")
    def _fwup_new(self, platform_config: PlatformConfig, wallet: Wallet) -> tuple[int, int]:
        """Bumps firmware version, builds, and performs FWUP.

        :param platform_config: platform configuration for the device under test.
        :param wallet: Wallet device under test.
        :returns: tuple of (new_version, active_slot).
        """
        # TODO(ESW-20955): Update this with new FWUP fixtures.
        logger.info("Bump, and build A & B slot")
        self.inv_task.bump()
        self.inv_task.clean()
        self.inv_task.build_platforms()

        logger.info("Bundle and FWUP")
        self.inv_task.fwup_bundle()
        self.inv_task.fwup_fwup()
        time.sleep(_WALLET_RESET_TIME)

        new_version, active_slot = self.get_active_version(wallet=wallet)
        logger.info(
            "Current version: %s, with activeSlot %s" % (
                new_version, active_slot)
        )
        return new_version, active_slot

    @allure.step("Build apps, and fwup")
    def _fwup_current_version(self, platform_config: PlatformConfig) -> None:
        """Builds and performs FWUP without version bump.

        :param platform_config: platform configuration for the device under test.
        :returns: ``None``
        """
        self.inv_task.build_platforms()

        logger.info("Bundle and FWUP")
        self.inv_task.fwup_bundle()
        self.inv_task.fwup_fwup()

    @allure.step("Get version from Metadata response")
    def get_active_version(self, wallet: Wallet) -> tuple[int, int]:
        """Queries the device for the active firmware version and slot.

        :returns: tuple of (version_patch, active_slot).
        """
        metadata = self.get_metadata(wallet=wallet).meta_rsp
        active_slot = metadata.active_slot
        if active_slot == 1:
            return metadata.meta_slot_a.version.patch, active_slot
        elif active_slot == 2:
            return metadata.meta_slot_b.version.patch, active_slot
        else:
            raise NotImplementedError(f"Unexpected active slot: {active_slot}")

    @allure.step("Get current metadata via NFC")
    def get_metadata(self, wallet: Wallet) -> wallet_pb.wallet_rsp:
        """Queries the device metadata over NFC.

        :param wallet: connected Wallet instance.
        :returns: metadata response from the device.
        """
        rsp = wallet.metadata()
        logger.info(rsp)
        return rsp


if __name__ == "__main__":
    sys.exit(pytest.main(sys.argv[1:]))
