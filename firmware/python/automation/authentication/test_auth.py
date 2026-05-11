"""Tests for exercising PIN-based authentication over NFC against a real device."""

import logging
import pytest
import sys

import wallet_pb2
from bitkey.wallet import Wallet

from ..conftest import PlatformConfig
from ..inv_commands import Inv

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class TestClassAuthentication:
    """Test suite for wallet authentication tests."""

    @pytest.fixture(scope="class", autouse=True)
    def setup(self, request: pytest.FixtureRequest, platform_config: PlatformConfig) -> None:
        """Pre-test setup. Performed once.

        :param request: PyTest fixture request object for command-line arguments.
        :param platform_config: target device platform configuration.
        :returns: ``None``
        """
        logger.info("Authentication tests")

        inv_task = Inv(request, platform_config)
        inv_task.clean()
        inv_task.build()
        inv_task.flash()

    def test_authenticate(self, wallet: Wallet, platform_config: PlatformConfig) -> None:
        """This test verifies that PIN authentication is working.

        :param wallet: Wallet device under test.
        :param platform_config: test platform configuration.
        :returns: ``None``
        """
        # Use the development test command to by-pass fingerprint authentication.
        success: bool = wallet.unlock_device()
        assert success, "Failed to unlock device."

        logger.info("test_authenticate")
        logger.info(wallet.provision_unlock_secret("foobar"))
        resp = wallet.unlock_secret("foobar")
        logger.info(resp)
        assert resp.status == wallet_pb2.status.SUCCESS

    def test_failed_authenticate(self, wallet: Wallet, platform_config: PlatformConfig) -> None:
        """This test verifies that incorrect PIN authentication fails.

        :param wallet: Wallet device under test.
        :param platform_config: test platform configuration.
        :returns: ``None``
        """
        # Use the development test command to by-pass fingerprint authentication.
        success: bool = wallet.unlock_device()
        assert success, "Failed to unlock device."

        logger.info("test_failed_authenticate")
        logger.info(wallet.provision_unlock_secret("foobar"))
        resp = wallet.unlock_secret("123456")
        logger.info(resp)

        assert resp.status == wallet_pb2.status.WRONG_SECRET


if __name__ == "__main__":
    sys.exit(pytest.main(sys.argv[1:]))
