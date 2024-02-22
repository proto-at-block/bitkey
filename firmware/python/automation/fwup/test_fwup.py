import sys
import allure
from python.automation.commander import CommanderHelper
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet
from python.automation.inv_commands import Inv
import logging
import pytest
from tasks.lib.paths import ROOT_DIR
from python.automation.conftest import PRODUCT, HARDWARE_REVISION, IMAGE_TYPE

APP_B_FIRMWARE = ROOT_DIR.joinpath(
    f"build/fwup-bundle/{PRODUCT}-{HARDWARE_REVISION}-app-b-{IMAGE_TYPE}.signed.bin")
APP_B_SIG = ROOT_DIR.joinpath(
    f"build/fwup-bundle/{PRODUCT}-{HARDWARE_REVISION}-app-b-{IMAGE_TYPE}.detached_signature")

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

Inv_task = Inv()
Commander = CommanderHelper()


@pytest.fixture
def setup():
    """Each test starts with a fresh build flashed into App A Slot"""
    Commander.reset()
    metadata = get_metadata()
    Inv_task.set_version("%s.%s.%s" % (metadata.meta_rsp.meta_slot_a.version.major,
                                       metadata.meta_rsp.meta_slot_a.version.minor,
                                       metadata.meta_rsp.meta_slot_a.version.patch))
    logger.info("Setup fixture")
    logger.info("Clean & build")
    Inv_task.clean()
    Inv_task.build()
    logger.info("Flashing A slot with FS recovery")
    Inv_task.flash_with_filesystem_recovery()
    Commander.reset()

def test_corrupted_firmware(setup):
    """This test verifies that FWUP is rejected for corrupted firmware"""
    initial_version, active_slot = get_active_version()
    Inv_task.bump()
    Inv_task.clean()
    Inv_task.build_platforms()
    Inv_task.fwup_bundle()

    corrupt_file(APP_B_FIRMWARE)
    auth_with_pin()
    output = Inv_task.fwup_fwup()
    new_version, new_slot = get_active_version()
    assert initial_version == new_version, "Version updated when it shouldn't have"
    assert active_slot == new_slot, "Slot changed when it shouldn't have"
    assert "rsp_status: SIGNATURE_INVALID" in output, "Expected SIGNATURE_INVALID to be in response output"

def test_corrupted_signature():
    """This test verifies that FWUP is rejected for corrupted signatures"""
    Commander.reset()
    initial_version, active_slot = get_active_version()
    Inv_task.bump()
    Inv_task.clean()
    Inv_task.build_platforms()
    Inv_task.fwup_bundle()

    corrupt_file(APP_B_SIG)
    auth_with_pin()
    output = Inv_task.fwup_fwup()
    new_version, new_slot = get_active_version()
    assert initial_version == new_version, "Version updated when it shouldn't have"
    assert active_slot == new_slot, "Slot changed when it shouldn't have"
    assert "rsp_status: SIGNATURE_INVALID" in output, "Expected SIGNATURE_INVALID to be in response output"

def test_fwup_invalid_version(setup):
    """This test verifies that FWUP is rejected for versions <= current version"""
    initial_version, active_slot = get_active_version()
    auth_with_pin()
    fwup_current_version()
    new_version, new_slot = get_active_version()

    assert initial_version == new_version, "Version updated when it shouldn't have"
    assert active_slot == new_slot, "Slot changed when it shouldn't have"

    Inv_task.set_version("0.0.1")
    auth_with_pin()
    fwup_current_version()
    new_version, new_slot = get_active_version()

    assert initial_version == new_version, "Version updated when it shouldn't have"
    assert active_slot == new_slot, "Slot changed when it shouldn't have"


#@pytest.mark.breakpoints("lfs.c:3167")
@pytest.mark.timeout(900)  # 15 minute timeout
def test_fwup_upgrade(setup):
    """This test verifies that we can FWUP twice from SLOT_A -> SLOT_B -> SLOT_A"""
    initial_version, active_slot = get_active_version()
    logger.info("Starting with version: %s, with activeSlot %s" %
                (initial_version, active_slot))
    assert active_slot == 1, "Expected to start active on SLOT_A"

    new_version, active_slot = fwup_new()

    # assert new_version == initial_version + 1, "Version didn't bump as expected"
    assert active_slot == 2, "Expected to be active on SLOT_B"

    new_version, active_slot = fwup_new()

    # assert new_version == initial_version + 2, "Version didn't bump as expected"
    assert active_slot == 1, "Expected to be active on SLOT_A"


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

@allure.step("Authenticating with PIN")
def auth_with_pin():
    wallet = Wallet(WalletComms(NFCTransaction()))
    logger.info("Authenticating with PIN")
    logger.info(wallet.provision_unlock_secret("foobar"))
    logger.info(wallet.unlock_secret("foobar"))

@allure.step("Bump version, build apps, and fwup")
def fwup_new():
    logger.info("Bump, and build A & B slot")
    Inv_task.bump()
    Inv_task.clean()
    Inv_task.build_platforms()

    logger.info("Bundle and FWUP")
    Inv_task.fwup_bundle()
    auth_with_pin()
    Inv_task.fwup_fwup()

    logger.info("Resetting Wallet")
    Commander.reset()

    new_version, active_slot = get_active_version()
    logger.info("Current version: %s, with activeSlot %s" %
                (new_version, active_slot))
    return new_version, active_slot


@allure.step("Build apps, and fwup")
def fwup_current_version():
    Inv_task.build_platforms()
    logger.info("Bundle and FWUP")
    Inv_task.fwup_bundle()
    auth_with_pin()
    Inv_task.fwup_fwup()

    logger.info("Resetting Wallet")
    Commander.reset()


@allure.step("Set local firmware version to: {version}")
def set_local_version(version):
    Inv_task.set_version(version)


@allure.step("Corrupt file ({file})")
def corrupt_file(file):
    with open(file, 'r+b') as f:
        b = bytearray(f.read())
        b[0] ^= 1
        f.seek(0)
        f.write(b)
