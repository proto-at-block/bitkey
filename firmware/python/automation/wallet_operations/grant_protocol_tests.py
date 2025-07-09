from bitkey.grant_protocol import MockServer, GrantRequest, GrantAction, SHA256_DIGEST_SIZE
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet

from automation.commander import reset_device

from bitkey_proto import wallet_pb2 as wallet_pb
from bitkey_proto import ops_keybundle_pb2 as ops_keybundle
from binascii import hexlify

from time import sleep

CHECK_GRANT_REQUEST_SIGNATURE = True

def fingerprint_reset_flow():
    server = MockServer()
    wallet = Wallet(comms=WalletComms(NFCTransaction()))

    # Ask for a grant request.
    proto_rsp = wallet.fingerprint_reset_request()
    print(proto_rsp)
    assert(proto_rsp.status == wallet_pb.SUCCESS)

    grant_request = GrantRequest.deserialize(proto_rsp.fingerprint_reset_request_rsp.grant_request)
    print("Grant request:")
    print(grant_request)


    if CHECK_GRANT_REQUEST_SIGNATURE:
        BIP32_HARDENED_BIT = 0x80000000

        # Derive the auth key.
        proto_rsp = wallet.derive(0, [87497287 | BIP32_HARDENED_BIT, 0 | BIP32_HARDENED_BIT])

        # last 33 bytes are the the actual key
        hw_auth_key = proto_rsp.derive_rsp.descriptor.bare_bip32_key[-33:]
        print("Hw auth key:")
        print(hexlify(hw_auth_key).decode('ascii'))
        assert len(hw_auth_key) == 33, "Hw auth key should be 33 bytes"

    if CHECK_GRANT_REQUEST_SIGNATURE:
        grant = server.sign_grant_for_request(grant_request, verify_request_signature=True, hw_auth_pubkey=hw_auth_key)
    else:
        grant = server.sign_grant_for_request(grant_request, verify_request_signature=False)

    print("Grant:")
    print(grant)

    # Reset the hardware to simulate a waiting period.
    # reset_device()

    # Sleep a bit.
    print("Sleeping for 1 seconds...")
    sleep(1)

    # Send grant to wallet.
    proto_rsp = wallet.fingerprint_reset_finalize(grant)
    assert(proto_rsp.status == wallet_pb.SUCCESS)

    print(proto_rsp)

if __name__ == "__main__":
    # transaction_verification_flow()
    fingerprint_reset_flow()
