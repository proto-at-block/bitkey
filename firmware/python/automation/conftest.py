"""
conftest.py
This file is used to configure the automation tests with variables.
It also includes globally accessible pytest fixtures.
"""

import logging
import pytest

from tasks.lib.paths import BUILD_FW_APPS_DIR, CONFIG_FILE

from bitkey.gdb import GdbCapture, JLinkGdbServer
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

DEVICE_CHIP = "EFR32MG24BXXXF1536"
PRODUCT = "w1a"
HARDWARE_REVISION = "dvt"
IMAGE_TYPE = "dev"


@pytest.fixture
def gdb_capture(request):
    breakpoints = request.node.get_closest_marker("breakpoints")
    with JLinkGdbServer(DEVICE_CHIP) as gdb:
        gdb_capture = GdbCapture(breakpoints)
        yield
        gdb_capture.get_backtrace()


@pytest.fixture
def wallet():
    return Wallet(WalletComms(NFCTransaction()))

@pytest.fixture
def auth_with_pin(wallet):
    logger.info("Authenticating with PIN")
    logger.info(wallet.provision_unlock_secret("foobar"))
    logger.info(wallet.unlock_secret("foobar"))
