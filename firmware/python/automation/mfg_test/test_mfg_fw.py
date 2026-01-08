import allure
from bitkey.walletfs import WalletFS
from python.automation.commander import CommanderHelper
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.gdb import JLinkGdbServer
from bitkey.wallet import Wallet
from bitkey_proto import mfgtest_pb2 as mfgtest_pb
from python.automation.inv_commands import Inv
from google.protobuf.json_format import MessageToJson
import logging
import pathlib
import pytest
import time
from tasks.lib.paths import ROOT_DIR
from ..conftest import PlatformConfig

CAP_TOUCH_PORT = "PORT_B"
CAP_TOUCH_PIN = 1

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

Inv_task = Inv()
Commander = CommanderHelper()


class TestClassMfgFW:
    @pytest.fixture(scope="class")
    def test_target(self, platform_config: PlatformConfig) -> pathlib.Path:
        yield pathlib.Path(f'{platform_config.product}-{platform_config.revision}-app-a-mfgtest-{platform_config.type}')

    @pytest.fixture(scope="class", autouse=True)
    def mfg_test_fw_fixture(self, test_target: pathlib.Path) -> None:
        """Each test starts with a fresh build of the mfgtest fw flashed into App A Slot"""
        metadata = get_metadata()
        Inv_task.set_version("%s.%s.%s" % (metadata.meta_rsp.meta_slot_a.version.major,
                                           metadata.meta_rsp.meta_slot_a.version.minor,
                                           metadata.meta_rsp.meta_slot_a.version.patch))
        logger.info("Setup fixture")
        logger.info("Clean, build, and flash A slot")
        Inv_task.clean()
        Inv_task.build_platforms()
        Inv_task.flash_with_filesystem_recovery(target=test_target)
        Commander.reset()
        metadata = get_metadata()

        initial_version, active_slot = get_active_version()
        logger.info("Starting with version: %s, with activeSlot %s" %
                    (initial_version, active_slot))
        assert active_slot == 1, "Expected to start active on SLOT_A"

    def test_mfgtest_fingerprint_selftest(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.mfgtest_fingerprint_selftest_start())
            time.sleep(11)  # takes 11 seconds for the BIST to execute
            logger.info(wallet.mfgtest_fingerprint_selftest_get_result())

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_mfgtest_fingerprint_calibrate(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.mfgtest_fingerprint_calibrate())

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_mfgtest_security_enable(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.mfgtest_fingerprint_security_enable(real=False))

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_mfgtest_security_mode(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.mfgtest_fingerprint_security_mode())

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_device_id(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.device_id())

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_fuel(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.fuel())

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_mfgtest_gpio(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            action_value = mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_action.Value(
                'READ')
            port_value = mfgtest_pb.mfgtest_gpio_cmd.mfgtest_gpio_port.Value(
                CAP_TOUCH_PORT)
            logger.info(wallet.mfgtest_gpio(
                action_value, port_value, CAP_TOUCH_PIN))

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_reset(self):

        error_msg = None

        try:
            wallet = Wallet(WalletComms(NFCTransaction()))
            logger.info(wallet.reset())

        except Exception as e:
            error_msg = e

        assert not error_msg, error_msg

    def test_mfgtest_fwup_upgrade(self):
        """This test verifies that we can FWUP from the MFG Test image to SLOT B"""

        initial_version, active_slot = get_active_version()
        logger.info("Starting with version: %s, with activeSlot %s" %
                    (initial_version, active_slot))
        assert active_slot == 1, "Expected to start active on SLOT_A"

        new_version, active_slot = fwup_new()

        # assert new_version == initial_version + 1, "Version didn't bump as expected"
        assert active_slot == 2, "Expected to be active on SLOT_B"


@allure.step("Get version from Metadata response")
def get_active_version():
    metadata = get_metadata().meta_rsp
    active_slot = metadata.active_slot
    if active_slot == 1:
        return metadata.meta_slot_a.version.patch, active_slot
    elif active_slot == 2:
        return metadata.meta_slot_b.version.patch, active_slot


@allure.step("Get current metadata via NFC")
def get_metadata():
    wallet = Wallet(WalletComms(NFCTransaction()))
    logger.info(wallet.metadata())
    return wallet.metadata()


@allure.step("Bump version, build apps, and fwup")
def fwup_new(platform_config: PlatformConfig) -> None:
    logger.info("Bump, and build A & B slot")
    Inv_task.bump()
    Inv_task.clean()
    Inv_task.build_platforms()

    logger.info("Bundle and FWUP")
    Inv_task.fwup_bundle(platform_config)
    Inv_task.fwup_fwup()

    logger.info("Resetting Wallet")
    Commander.reset()

    new_version, active_slot = get_active_version()
    logger.info("Current version: %s, with activeSlot %s" %
                (new_version, active_slot))
    return new_version, active_slot


@allure.step("Build apps, and fwup")
def fwup_current_version(platform_config: PlatformConfig) -> None:
    Inv_task.build_platforms()
    logger.info("Bundle and FWUP")
    Inv_task.fwup_bundle(platform_config)
    Inv_task.fwup_fwup()

    logger.info("Resetting Wallet")
    Commander.reset()


@allure.step("Set local firmware version to: {version}")
def set_local_version(version):
    Inv_task.set_version(version)
