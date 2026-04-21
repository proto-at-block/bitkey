"""Test suite for HKDF-based key derivation."""

from __future__ import annotations

import logging
import sys

import pytest
from bitkey_proto import wallet_pb2 as wallet_pb
from bitkey.secure_channel import SecureChannel
from bitkey.wallet import Wallet

from python.automation.commander import CommanderHelper
from python.automation.inv_commands import Inv

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class TestClassHKDF:
    commander = CommanderHelper()
    Inv_task = Inv()

    @pytest.fixture(scope="class", autouse=True)
    def setup(self, request: pytest.FixtureRequest) -> None:
        """Pre-test setup. Performed once.

        :param request: PyTest fixture request object for command-line arguments.
        :returns: ``None``
        """
        logger.info("Setup fixture")
        logger.info("Clean, build, and flash")
        self.Inv_task.clean(request=request)
        self.Inv_task.build(request=request)
        self.Inv_task.flash_with_filesystem_recovery(request=request)
        if not request.config.option.skip_flash:
            self.commander.reset()

    def different_key_for_different_label(self, wallet: Wallet, curve: str) -> None:
        """Helper function for performing the key derivation test for HKDF.

        Derives two public keys on device using the same key but different
        labels. Validates that the derivation succeeds, but the public keys
        are differing.

        :param wallet: the Wallet instance under test.
        :param curve: curve to use.
        :returns: ``None``
        """
        logger.debug(f"different_key_for_different_label ({curve})")

        label1: str = 'foobar'
        label2: str = 'barfoo'

        rsp1 = wallet.derive_public_key(curve=curve, label=label1)
        assert rsp1.status == wallet_pb.SUCCESS
        pubkey1 = rsp1.derive_public_key_rsp.pubkey

        rsp2 = wallet.derive_public_key(curve=curve, label=label2)
        assert rsp2.status == wallet_pb.SUCCESS
        pubkey2 = rsp2.derive_public_key_rsp.pubkey

        assert rsp1 != rsp2
        assert pubkey1 != pubkey2

    @pytest.mark.xfail(reason="Public key derivation has been removed.")
    def test_different_key_for_different_label_p256(
        self,
        wallet: Wallet,
        secure_channel: SecureChannel,
    ) -> None:
        """HKDF key derivation test for NIST P-256 curve.

        :param wallet: Wallet instance under test.
        :param secure_channel: authenticated secure channel.
        :returns: ``None``
        """
        self.different_key_for_different_label(wallet, curve='CURVE_P256')

    @pytest.mark.xfail(reason="Public key derivation has been removed.")
    def test_different_key_for_different_label_ed25519(
        self,
        wallet: Wallet,
        secure_channel: SecureChannel,
    ) -> None:
        """HKDF key derivation test for ED25519.

        :param wallet: Wallet instance under test.
        :param secure_channel: authenticated secure channel.
        :returns: ``None``
        """
        self.different_key_for_different_label(wallet, curve='CURVE_ED25519')


if __name__ == "__main__":
    sys.exit(pytest.main(sys.argv[1:]))
