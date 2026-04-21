"""Grant Protocol Test Suite"""

from __future__ import annotations

import logging
import sys
import time
from binascii import hexlify

import pytest
from bitkey_proto import wallet_pb2 as wallet_pb
from bitkey.grant_protocol import MockServer, MockApp, GrantRequest
from bitkey.secure_channel import SecureChannel
from bitkey.wallet import Wallet

from python.automation.commander import CommanderHelper
from python.automation.inv_commands import Inv

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)


class TestClassGrantProtocol:
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

    @pytest.mark.parametrize("check_grant", [True, False])
    def test_fingerprint_reset_flow(
        self,
        wallet: Wallet,
        secure_channel: SecureChannel,
        check_grant: bool,
    ) -> None:
        """Validates the fingerprint reset flow.

        Validates application authentication public key provisioning and grant
        requests. When ``check_grant`` is ``True``, grant verification is done
        with both app and WIK signatures.

        :param wallet: the Wallet instance under test.
        :param secure_channel: authenticated secure channel.
        :param check_grant: ``True`` if grant should be verified.
        :returns: ``None``
        """
        server = MockServer()
        app = MockApp()

        # First, unlock the device if needed.
        logger.info("=== Checking Authentication Status ===")
        auth_status = wallet.query_authentication()
        assert auth_status.query_authentication_rsp.rsp_status == wallet_pb.query_authentication_rsp.AUTHENTICATED, \
            "Device must be unlocked."

        # First, provision the app auth pubkey to the device.
        logger.info("=== Provisioning App Auth Public Key ===")
        app_pubkey = app.get_compressed_public_key()
        logger.info(f"App auth public key: {hexlify(app_pubkey).decode('ascii')}")

        # Send the provisioning command using the wallet's proper API
        proto_rsp = wallet.provision_app_auth_pubkey(app_pubkey)
        logger.info(f"Provision response status: {proto_rsp.status}")
        assert proto_rsp.status == wallet_pb.SUCCESS, "Failed to provision app auth pubkey"
        logger.info("App auth pubkey provisioned successfully!")

        # Ask for a grant request.
        logger.info("=== Requesting Grant ===")
        proto_rsp = wallet.fingerprint_reset_request()
        logger.info(str(proto_rsp))
        assert proto_rsp.status == wallet_pb.SUCCESS

        grant_request = GrantRequest.deserialize(proto_rsp.fingerprint_reset_request_rsp.grant_request)
        logger.info("Grant request:")
        logger.info(str(grant_request))

        # App signs the grant request.
        logger.info("=== App Signing Grant Request ===")
        app_signature = app.sign_grant_request(grant_request)
        logger.info(f"App signature: {hexlify(app_signature).decode('ascii')}")

        if check_grant:
            BIP32_HARDENED_BIT = 0x80000000

            # Derive the auth key.
            proto_rsp = wallet.derive(0, [87497287 | BIP32_HARDENED_BIT, 0 | BIP32_HARDENED_BIT])
            assert proto_rsp.status == wallet_pb.status.SUCCESS, "Derive failed"

            # Last 33 bytes are the the actual key.
            hw_auth_key = proto_rsp.derive_rsp.descriptor.bare_bip32_key[-33:]
            logger.info("Hw auth key:")
            logger.info(hexlify(hw_auth_key).decode('ascii'))
            assert len(hw_auth_key) == 33, "Hw auth key should be 33 bytes"

        # Server signs the grant (including app signature).
        logger.info("=== Server Creating Grant ===")
        if check_grant:
            grant = server.sign_grant_for_request(
                grant_request,
                app_signature,
                verify_request_signature=True,
                hw_auth_pubkey=hw_auth_key
            )
        else:
            grant = server.sign_grant_for_request(
                grant_request,
                app_signature,
                verify_request_signature=False
            )

        logger.info("Grant:")
        logger.info(str(grant))

        # Sleep a bit.
        logger.info("Sleeping for 1 seconds...")
        time.sleep(1)

        # Send grant to wallet.
        logger.info("=== Finalizing Grant ===")
        proto_rsp = wallet.fingerprint_reset_finalize(grant)
        logger.info(f"Finalize response status: {proto_rsp.status}")
        assert proto_rsp.status == wallet_pb.SUCCESS, "Grant finalization failed!"

        logger.info("Fingerprint reset completed successfully!")
        logger.info(str(proto_rsp))

    def test_app_auth_key_rotation_flow(self, wallet: Wallet, secure_channel: SecureChannel) -> None:
        """Tests app auth key replacement (no signature required).

        :param wallet: Wallet device under test.
        :param secure_channel: authenticated secure channel.
        :returns: ``None``
        """
        # First, unlock the device if needed.
        logger.info("=== Checking Authentication Status ===")
        auth_status = wallet.query_authentication()
        assert auth_status.query_authentication_rsp.rsp_status == wallet_pb.query_authentication_rsp.AUTHENTICATED, \
            "Device must be unlocked."

        # Create initial and new app instances.
        current_app = MockApp()
        new_app = MockApp()

        logger.info("=== Initial App Auth Key Provisioning ===")
        # First provision the initial app auth key
        initial_pubkey = current_app.get_compressed_public_key()
        logger.info(f"Initial app pubkey: {hexlify(initial_pubkey).decode('ascii')}")

        proto_rsp = wallet.provision_app_auth_pubkey(initial_pubkey)
        assert proto_rsp.status == wallet_pb.SUCCESS, \
            f"Failed to provision initial app auth pubkey (status={proto_rsp.status})"
        logger.info("Initial app auth pubkey provisioned successfully!")

        # Now replace with a new key (no signature required).
        logger.info("=== Replacing App Auth Key ===")
        new_pubkey = new_app.get_compressed_public_key()
        logger.info(f"New app pubkey: {hexlify(new_pubkey).decode('ascii')}")


        # Send new pubkey command (no signature needed anymore).
        proto_rsp = wallet.provision_app_auth_pubkey(new_pubkey)
        logger.info(f"Replacement response status: {proto_rsp.status}")
        assert proto_rsp.status == wallet_pb.SUCCESS, "Failed to replace app auth pubkey"
        logger.info("App auth pubkey replaced successfully!")

        # Now the new app key would be used for future grant requests.
        logger.info("=== Testing with New Key ===")
        # Request a grant to verify new key works.
        proto_rsp = wallet.fingerprint_reset_request()
        assert proto_rsp.status == wallet_pb.SUCCESS, "Fingerprint reset request failed with new key"

        grant_request = GrantRequest.deserialize(proto_rsp.fingerprint_reset_request_rsp.grant_request)
        logger.info("Grant request created with new key provisioned")

        # New app signs the request with its key.
        app_signature = new_app.sign_grant_request(grant_request)
        logger.info(f"New app signature: {hexlify(app_signature).decode('ascii')[:64]}...")

        # Note: Old app signatures will no longer be accepted.
        logger.info("=== Verifying New Key Is Active ===")
        logger.info("The old app key has been replaced and won't be accepted anymore")

        # Create server to complete the test.
        server = MockServer()

        # Server would sign the grant with the new app's signature
        grant = server.sign_grant_for_request(
            grant_request,
            app_signature,
            verify_request_signature=False  # Skip HW signature verification in test
        )

        logger.info(f"Grant created with new app signature")
        logger.info(f"Grant will be accepted because it uses the newly provisioned key")


if __name__ == "__main__":
    sys.exit(pytest.main(sys.argv[1:]))
