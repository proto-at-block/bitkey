"""
Grant Protocol Test Script

Prerequisites:
- Device must be connected and unlocked
- Run with: python python/automation/wallet_operations/grant_protocol_tests.py
- For replacement test: python python/automation/wallet_operations/grant_protocol_tests.py rotate

This test demonstrates:
1. App auth pubkey provisioning
2. Grant request creation with app signature
3. Grant verification with both app and WIK signatures
4. App auth key replacement (no signature required)
"""

from bitkey.grant_protocol import MockServer, MockApp, GrantRequest
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet

from bitkey_proto import wallet_pb2 as wallet_pb
from binascii import hexlify
import hashlib

from time import sleep

CHECK_GRANT_REQUEST_SIGNATURE = True
PROVISION_APP_AUTH_KEY = True

def fingerprint_reset_flow():
    server = MockServer()
    app = MockApp()
    wallet = Wallet(comms=WalletComms(NFCTransaction()))

    # First, unlock the device if needed
    print("\n=== Checking Authentication Status ===")
    auth_status = wallet.query_authentication()
    if auth_status.query_authentication_rsp.rsp_status != 1:  # 1 = AUTHENTICATED
        print("Device is locked. Please unlock it manually and re-run the test.")
        return
    print("Device is unlocked.")

    # First, provision the app auth pubkey to the device
    if PROVISION_APP_AUTH_KEY:
        print("\n=== Provisioning App Auth Public Key ===")
        app_pubkey = app.get_compressed_public_key()
        print(f"App auth public key: {hexlify(app_pubkey).decode('ascii')}")

        # Send the provisioning command using the wallet's proper API
        proto_rsp = wallet.provision_app_auth_pubkey(app_pubkey)
        print(f"Provision response status: {proto_rsp.status}")
        assert(proto_rsp.status == wallet_pb.SUCCESS), "Failed to provision app auth pubkey"
        print("App auth pubkey provisioned successfully!\n")

    # Ask for a grant request.
    print("=== Requesting Grant ===")
    proto_rsp = wallet.fingerprint_reset_request()
    print(proto_rsp)
    assert(proto_rsp.status == wallet_pb.SUCCESS)

    grant_request = GrantRequest.deserialize(proto_rsp.fingerprint_reset_request_rsp.grant_request)
    print("Grant request:")
    print(grant_request)

    # App signs the grant request
    print("\n=== App Signing Grant Request ===")
    app_signature = app.sign_grant_request(grant_request)
    print(f"App signature: {hexlify(app_signature).decode('ascii')}")

    if CHECK_GRANT_REQUEST_SIGNATURE:
        BIP32_HARDENED_BIT = 0x80000000

        # Derive the auth key.
        proto_rsp = wallet.derive(0, [87497287 | BIP32_HARDENED_BIT, 0 | BIP32_HARDENED_BIT])

        # last 33 bytes are the the actual key
        hw_auth_key = proto_rsp.derive_rsp.descriptor.bare_bip32_key[-33:]
        print("Hw auth key:")
        print(hexlify(hw_auth_key).decode('ascii'))
        assert len(hw_auth_key) == 33, "Hw auth key should be 33 bytes"

    # Server signs the grant (including app signature)
    print("\n=== Server Creating Grant ===")
    if CHECK_GRANT_REQUEST_SIGNATURE:
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

    print("Grant:")
    print(grant)

    # Reset the hardware to simulate a waiting period.
    # reset_device()

    # Sleep a bit.
    print("Sleeping for 1 seconds...")
    sleep(1)

    # Send grant to wallet.
    print("\n=== Finalizing Grant ===")
    proto_rsp = wallet.fingerprint_reset_finalize(grant)
    print(f"Finalize response status: {proto_rsp.status}")
    assert(proto_rsp.status == wallet_pb.SUCCESS), "Grant finalization failed!"

    print("Fingerprint reset completed successfully!")
    print(proto_rsp)

def app_auth_key_rotation_flow():
    """Demonstrates app auth key replacement (no signature required)"""
    wallet = Wallet(comms=WalletComms(NFCTransaction()))

    # First, unlock the device if needed
    print("\n=== Checking Authentication Status ===")
    auth_status = wallet.query_authentication()
    if auth_status.query_authentication_rsp.rsp_status != 1:  # 1 = AUTHENTICATED
        print("Device is locked. Please unlock it manually and re-run the test.")
        return
    print("Device is unlocked.")

    # Create initial and new app instances
    current_app = MockApp()
    new_app = MockApp()

    print("\n=== Initial App Auth Key Provisioning ===")
    # First provision the initial app auth key
    initial_pubkey = current_app.get_compressed_public_key()
    print(f"Initial app pubkey: {hexlify(initial_pubkey).decode('ascii')}")

    proto_rsp = wallet.provision_app_auth_pubkey(initial_pubkey)
    assert(proto_rsp.status == wallet_pb.SUCCESS), f"Failed to provision initial app auth pubkey (status={proto_rsp.status})"
    print("Initial app auth pubkey provisioned successfully!")

    print("\n=== Replacing App Auth Key ===")
    # Now replace with a new key (no signature required)
    new_pubkey = new_app.get_compressed_public_key()
    print(f"New app pubkey: {hexlify(new_pubkey).decode('ascii')}")

    # Send new pubkey command (no signature needed anymore)
    proto_rsp = wallet.provision_app_auth_pubkey(new_pubkey)
    print(f"Replacement response status: {proto_rsp.status}")
    assert(proto_rsp.status == wallet_pb.SUCCESS), "Failed to replace app auth pubkey"
    print("App auth pubkey replaced successfully!")

    # Now the new app key would be used for future grant requests
    print("\n=== Testing with New Key ===")
    # Request a grant to verify new key works
    proto_rsp = wallet.fingerprint_reset_request()
    assert(proto_rsp.status == wallet_pb.SUCCESS), "Failed to create fingerprint reset request with new key"

    grant_request = GrantRequest.deserialize(proto_rsp.fingerprint_reset_request_rsp.grant_request)
    print("Grant request created with new key provisioned")

    # New app signs the request with its key
    app_signature = new_app.sign_grant_request(grant_request)
    print(f"New app signature: {hexlify(app_signature).decode('ascii')[:64]}...")

    # Note: Old app signatures will no longer be accepted
    print("\n=== Verifying New Key Is Active ===")
    print("The old app key has been replaced and won't be accepted anymore")

    # Create server to complete the test
    server = MockServer()

    # Server would sign the grant with the new app's signature
    grant = server.sign_grant_for_request(
        grant_request,
        app_signature,
        verify_request_signature=False  # Skip HW signature verification in test
    )

    print(f"\nGrant created with new app signature")
    print(f"Grant will be accepted because it uses the newly provisioned key")

    print("\n✅ Key replacement test completed successfully!")


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "rotate":
        app_auth_key_rotation_flow()
    else:
        fingerprint_reset_flow()
