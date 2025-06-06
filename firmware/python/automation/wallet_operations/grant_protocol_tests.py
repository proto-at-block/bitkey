from bitkey.grant_protocol import MockServer, GrantRequest, GrantAction, SHA256_DIGEST_SIZE
from bitkey.comms import NFCTransaction, WalletComms
from bitkey.wallet import Wallet

from automation.commander import reset_device

from bitkey_proto import wallet_pb2 as wallet_pb

from time import sleep


def transaction_verification_flow():
    pass
    # server = MockServer()
    # wallet = Wallet(comms=WalletComms(NFCTransaction()))

    # # Ask for a grant request.
    # proto_rsp = wallet.transaction_verification_request()
    # assert(proto_rsp.status == wallet_pb.SUCCESS)

    # grant_request = GrantRequest.deserialize(proto_rsp.transaction_verification_request_rsp.grant_request)
    # print(grant_request)

    # # Forward request to server.
    # sighash = b'\x42'*SHA256_DIGEST_SIZE
    # server.transaction_verification_flow(sighash)
    # grant = server.sign_grant_for_request(grant_request)
    # print(grant)

    # # Send grant to wallet.
    # proto_rsp = wallet.transaction_verification_finalize(grant)
    # assert(proto_rsp.status == wallet_pb.SUCCESS)

    # # Now we can sign the transaction with the sighash specified above.
    # proto_rsp = wallet.derive_and_sign(sighash, [1, 2, 3], False)
    # assert(proto_rsp.status == wallet_pb.SUCCESS, f"Failed to sign transaction: {proto_rsp.status}")
    # print(proto_rsp.derive_and_sign_rsp.signature)

def fingerprint_reset_flow():
    server = MockServer()
    wallet = Wallet(comms=WalletComms(NFCTransaction()))

    # Ask for a grant request.
    proto_rsp = wallet.fingerprint_reset_request()
    print(proto_rsp)
    assert(proto_rsp.status == wallet_pb.SUCCESS)

    grant_request = GrantRequest.deserialize(proto_rsp.fingerprint_reset_request_rsp.grant_request)
    print(grant_request)

    # Forward request to server.
    grant = server.sign_grant_for_request(grant_request)
    print(grant)

    # Reset the hardware to simulate a waiting period.
    # reset_device()

    # Sleep a bit.
    # sleep(2)

    # Send grant to wallet.
    proto_rsp = wallet.fingerprint_reset_finalize(grant)
    assert(proto_rsp.status == wallet_pb.SUCCESS)

    print(proto_rsp)

if __name__ == "__main__":
    # transaction_verification_flow()
    fingerprint_reset_flow()
