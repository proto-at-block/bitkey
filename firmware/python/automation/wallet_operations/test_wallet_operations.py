"""On-device integration test suite for verifying Bitcoin transaction signing."""

from __future__ import annotations

import logging
import sys
import time

import allure
import pytest
import wallet_pb2 as wallet_pb
from bitkey.secure_channel import SecureChannel
from bitkey.wallet import Wallet

from ..conftest import PlatformConfig
from ..inv_commands import Inv

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class TestWalletOperations:

    @pytest.fixture(scope="class", autouse=True)
    def setup(self, request: pytest.FixtureRequest, platform_config: PlatformConfig) -> None:
        """Pre-test setup. Performed once.

        :param request: PyTest fixture request object for command-line arguments.
        :param platform_config: target device platform configuration.
        :returns: ``None``
        """
        logger.info("Setup fixture")

        inv_task = Inv(request, platform_config)
        inv_task.clean()
        inv_task.build()
        inv_task.flash()

    @allure.step("Sign Txn request")
    def test_derive_and_sign_sync(self, wallet: Wallet, secure_channel: SecureChannel) -> None:
        """Synchronous derive-and-sign testing.

        Derives a key at a BIP-32 path and signs a 32-bit digest.

        :param wallet: the ``Wallet`` instance for the device under test.
        :param secure_channel: authenticated secure channel.
        :returns: ``None``.
        """
        rsp = wallet.derive_and_sign(
            digest=b"12345678123456781234567812345678",
            path=[1, 2],
            async_sign=False,
        )
        logger.info(str(rsp))

        if wallet.product.lower().startswith("w1"):
            assert rsp.status == wallet_pb.status.SUCCESS
        else:
            assert rsp.status == wallet_pb.status.FEATURE_NOT_SUPPORTED

    @allure.step("Async txn signing")
    def test_derive_and_sign_async(self, wallet: Wallet, secure_channel: SecureChannel) -> None:
        """Asynchronous derive-and-sign request.

        Derives a key at a BIP-32 path and signs a 32-bit digest. Expects the
        Wallet to be able to perform this operation and later retrieve the
        result.

        :param wallet: the ``Wallet`` instance for the device under test.
        :param secure_channel: authenticated secure channel.
        :returns: ``None``.
        """
        prev_status: None | int = None

        for _ in range(100):
            rsp = wallet.derive_and_sign(
                digest=b"12345678123456781234567812345678",
                path=[1, 2, 3],
                async_sign=True,
            )

            if rsp.status == wallet_pb.status.SUCCESS:
                assert prev_status == wallet_pb.status.IN_PROGRESS, f"Expected previous status to be 'IN_PROGRESS', but was {prev_status=}"
                break

            prev_status = rsp.status
            time.sleep(0.001)
        else:
            if wallet.product.lower().startswith("w1"):
                assert prev_status == wallet_pb.status.SUCCESS, f"Transaction signing failed, {prev_status=}."
            else:
                assert prev_status == wallet_pb.status.FEATURE_NOT_SUPPORTED, f"Transaction signing should not be supported, {prev_status=}."


if __name__ == "__main__":
    sys.exit(pytest.main(sys.argv[1:]))
