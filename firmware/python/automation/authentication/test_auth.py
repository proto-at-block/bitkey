from bitkey.comms import NFCTransaction, WalletComms
import pytest
import allure
import logging
from python.automation.inv_commands import Inv
from python.automation.commander import CommanderHelper
from bitkey.wallet import Wallet
import wallet_pb2

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

class TestClassAuthentication:
    commander = CommanderHelper()
    Inv_task = Inv()
    wallet = Wallet(WalletComms(NFCTransaction()))

    @pytest.fixture(scope="class", autouse=True)
    def setup(self):
        logger.info("Authentication tests")
        self.Inv_task.clean()
        self.Inv_task.build()
        self.Inv_task.flash_with_filesystem_recovery()
        self.commander.reset()

    def test_authenticate(self):
        """This test verifies that PIN authentication is working"""
        logger.info("test_authenticate")
        logger.info(self.wallet.provision_unlock_secret("foobar"))
        resp = self.wallet.unlock_secret("foobar")
        logger.info(resp)
        assert resp.status == wallet_pb2.status.SUCCESS  

    def test_failed_authenticate(self):
        """This test verifies that incorrect PIN authentication fails"""
        logger.info("test_failed_authenticate")
        logger.info(self.wallet.provision_unlock_secret("foobar"))
        resp = self.wallet.unlock_secret("123456")
        logger.info(resp)
        assert resp.status == wallet_pb2.status.WRONG_SECRET