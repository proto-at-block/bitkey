import pytest
import logging
from python.automation.inv_commands import Inv
from python.automation.commander import CommanderHelper
from bitkey.wallet import Wallet
from base64 import b64encode
from secrets import token_bytes

from bitkey_proto import wallet_pb2 as wallet_pb

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

class TestClassHKDF:
    commander = CommanderHelper()
    Inv_task = Inv()

    @pytest.fixture(scope="class", autouse=True)
    def setup(self):
        logger.info("HKDF tests")
        self.Inv_task.clean()
        self.Inv_task.build()
        self.Inv_task.flash_with_filesystem_recovery()
        self.commander.reset()


    def different_key_for_different_label(self, wallet: Wallet, curve):
        logger.debug(f"different_key_for_different_label ({curve}))")

        label1 = 'foobar'
        label2 = 'barfoo'

        rsp1 = wallet.derive_public_key(curve=curve, label=label1)
        assert rsp1.status == wallet_pb.SUCCESS
        pubkey1 = rsp1.derive_public_key_rsp.pubkey

        rsp2 = wallet.derive_public_key(curve=curve, label=label2)
        assert rsp2.status == wallet_pb.SUCCESS
        pubkey2 = rsp2.derive_public_key_rsp.pubkey

        assert rsp1 != rsp2
        assert pubkey1 != pubkey2


    def test_different_key_for_different_label_p256(self, auth_with_pin, wallet):
        self.different_key_for_different_label(wallet, curve='CURVE_P256')


    def test_different_key_for_different_label_ed25519(self, auth_with_pin, wallet):
        self.different_key_for_different_label(wallet, curve='CURVE_ED25519')
