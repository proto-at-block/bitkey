import ecdsa
import pytest
import logging
import hashlib
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


    def verify(self, curve, pubkey, signature, message):
        _curve = None
        _hashfunc = None
        _verification_input = None
        if curve == 'CURVE_P256':
            _curve = ecdsa.NIST256p
            _hashfunc = hashlib.sha256
            _verification_input = message
        elif curve == 'CURVE_ED25519':
            _curve = ecdsa.Ed25519
            _verification_input = hashlib.sha256(message).digest()
        else:
            assert False

        verifying_key = ecdsa.VerifyingKey.from_string(
            pubkey, curve=_curve)

        assert verifying_key.verify(
            signature, _verification_input, hashfunc=_hashfunc)


    def sign_verify(self, wallet: Wallet, curve):
        logger.debug(f"sign_verify ({curve})")

        i = 0
        while i < 32:
            label = str(b64encode(token_bytes(16)))
            message = token_bytes(32)
            hash = hashlib.sha256(message).digest()
            rsp = wallet.derive_public_key_and_sign(
                curve=curve, label=label, digest=hash)

            assert rsp.status == wallet_pb.SUCCESS

            pubkey = rsp.derive_public_key_and_sign_rsp.pubkey
            signature = rsp.derive_public_key_and_sign_rsp.signature

            self.verify(curve, pubkey, signature, message)

            logger.debug(i)
            i += 1


    def same_key_for_same_label(self, wallet: Wallet, curve):
        logger.debug(f"same_key_for_same_label ({curve}))")

        label = str(b64encode(token_bytes(16)))

        last_pubkey = None

        i = 0
        while i < 8:
            message = token_bytes(32)
            hash = hashlib.sha256(message).digest()
            rsp = wallet.derive_public_key_and_sign(
                curve=curve, label=label, digest=hash)

            assert rsp.status == wallet_pb.SUCCESS

            pubkey = rsp.derive_public_key_and_sign_rsp.pubkey
            signature = rsp.derive_public_key_and_sign_rsp.signature

            self.verify(curve, pubkey, signature, message)

            if last_pubkey is not None:
                assert pubkey == last_pubkey
                last_pubkey = pubkey

            logger.debug(i)
            i += 1


    def different_key_for_different_label(self, wallet: Wallet, curve):
        logger.debug(f"different_key_for_different_label ({curve}))")

        label1 = 'foobar'
        label2 = 'barfoo'

        rsp1 = wallet.derive_public_key(curve=curve, label=label1)
        assert rsp1.status == wallet_pb.SUCCESS
        pubkey1 = rsp1.derive_public_key_rsp.pubkey

        rsp2 = wallet.derive_public_key(curve=curve, label=label2)
        assert rsp1.status == wallet_pb.SUCCESS
        pubkey2 = rsp2.derive_public_key_rsp.pubkey

        assert rsp1 != rsp2
        assert pubkey1 != pubkey2


    def test_sign_verify_p256(self, auth_with_pin, wallet):
        self.sign_verify(wallet, curve='CURVE_P256')


    def test_sign_verify_ed25519(self, auth_with_pin, wallet):
        self.sign_verify(wallet, curve='CURVE_ED25519')


    def test_same_key_for_same_label_p256(self, auth_with_pin, wallet):
        self.same_key_for_same_label(wallet, curve='CURVE_P256')


    def test_same_key_for_same_label_ed25519(self, auth_with_pin, wallet):
        self.same_key_for_same_label(wallet, curve='CURVE_ED25519')


    def test_different_key_for_different_label_p256(self, auth_with_pin, wallet):
        self.different_key_for_different_label(wallet, curve='CURVE_P256')


    def test_different_key_for_different_label_ed25519(self, auth_with_pin, wallet):
        self.different_key_for_different_label(wallet, curve='CURVE_ED25519')
