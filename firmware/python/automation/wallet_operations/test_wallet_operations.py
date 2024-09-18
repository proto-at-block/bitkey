import allure
import wallet_pb2 as wallet_pb
from python.automation.commander import CommanderHelper
from python.automation.inv_commands import Inv
import logging
import pytest
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet
from tasks.lib.paths import ROOT_DIR
from time import sleep

from dataclasses import dataclass

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

Inv_task = Inv()
Commander = CommanderHelper()


@pytest.fixture
def setup():
    """Each test starts with a fresh build flashed into App A Slot"""
    logger.info("Setup fixture")
    logger.info("Clean, build, and flash A slot")
    Inv_task.clean()
    Inv_task.build()
    Inv_task.flash_with_filesystem_recovery()
    Commander.reset()


@allure.step("Sign Txn request")
def sign_transaction():
    wallet = Wallet(comms=WalletComms(NFCTransaction()))
    rsp = wallet.sign_txn("12345678123456781234567812345678", 1, 2)
    logger.info(rsp)
    return rsp


@allure.step("Async txn signing")
def sign_transaction_async():
    wallet = Wallet(comms=WalletComms(NFCTransaction()))

    prev_status = None
    while True:
        rsp = wallet.derive_and_sign(
            digest = b"12345678123456781234567812345678",
            path = [1, 2, 3],
            async_sign=True
        )
        if rsp.status == wallet_pb.status.SUCCESS:
            assert prev_status == wallet_pb.status.IN_PROGRESS
        prev_status = rsp.status

        sleep(0.001)
    return rsp

if __name__ == "__main__":
    sign_transaction_async()
