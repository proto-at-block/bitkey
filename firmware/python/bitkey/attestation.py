from bitkey.comms import NFCTransaction, WalletComms
from bitkey_proto import wallet_pb2 as wallet_pb

import bitkey.certs
import secrets

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
from cryptography import x509

from Crypto.PublicKey import ECC
from Crypto.Signature import DSS
from Crypto.Hash import SHA256

SILABS_FACTORY_CERT = b"""-----BEGIN CERTIFICATE-----
MIICEjCCAbmgAwIBAgIIJNx7QAwynAowCgYIKoZIzj0EAwIwQjEXMBUGA1UEAwwO
RGV2aWNlIFJvb3QgQ0ExGjAYBgNVBAoMEVNpbGljb24gTGFicyBJbmMuMQswCQYD
VQQGEwJVUzAgFw0xODEwMTAxNzMzMDBaGA8yMTE4MDkxNjE3MzIwMFowOzEQMA4G
A1UEAwwHRmFjdG9yeTEaMBgGA1UECgwRU2lsaWNvbiBMYWJzIEluYy4xCzAJBgNV
BAYTAlVTMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEatHnJa9nUyTyJtuY6xgE
msybdzjhCbmKo3qMzAt/GQ4/TKIXkCwhw1Ni6kmQzh4qrINPYWP8vnG6tPJUyzUp
VKOBnTCBmjASBgNVHRMBAf8ECDAGAQH/AgEBMB8GA1UdIwQYMBaAFBCLCj7NdHWU
9EyEIs2OIqSrMaVCMDQGA1UdHwQtMCswKaAnoCWGI2h0dHA6Ly9jYS5zaWxhYnMu
Y29tL2RldmljZXJvb3QuY3JsMB0GA1UdDgQWBBRDYoRJaG86aXx20B/lHSr513PR
FjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwIDRwAwRAIgY34nvceLA1h3xYgt
mdzguHn7yNYlJQXDp7F8iNLRTBkCIAwkPej1R90Hw2o48eNvOmJG+QeLAUdVlIGY
07PRgSaC
-----END CERTIFICATE-----
"""

SILABS_DEVICE_ROOT = b"""
-----BEGIN CERTIFICATE-----
MIICGTCCAcCgAwIBAgIIEuaipZyqJ/kwCgYIKoZIzj0EAwIwQjEXMBUGA1UEAwwO
RGV2aWNlIFJvb3QgQ0ExGjAYBgNVBAoMEVNpbGljb24gTGFicyBJbmMuMQswCQYD
VQQGEwJVUzAgFw0xODEwMTAxNzMyMDBaGA8yMTE4MDkxNjE3MzIwMFowQjEXMBUG
A1UEAwwORGV2aWNlIFJvb3QgQ0ExGjAYBgNVBAoMEVNpbGljb24gTGFicyBJbmMu
MQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABNAp5f+cr+v9
zxfMQMJjxLxaqdBWe4nTrCwHihHtxYZDYsSBgdzZ3VFUu0xTlP07dWsuCL99abzl
Qyqak+tdTS2jgZ0wgZowEgYDVR0TAQH/BAgwBgEB/wIBAjAfBgNVHSMEGDAWgBQQ
iwo+zXR1lPRMhCLNjiKkqzGlQjA0BgNVHR8ELTArMCmgJ6AlhiNodHRwOi8vY2Eu
c2lsYWJzLmNvbS9kZXZpY2Vyb290LmNybDAdBgNVHQ4EFgQUEIsKPs10dZT0TIQi
zY4ipKsxpUIwDgYDVR0PAQH/BAQDAgGGMAoGCCqGSM49BAMCA0cAMEQCIGlwr4G7
IkG/9XHHk1WPthnY/yNNIzP9pThZkg2zU88ZAiBkAhsPaMKE7NOwWQIBgxy9nevX
c7VKkqNr4UAU5zPbxg==
-----END CERTIFICATE-----
"""

ATTESTATION_LABEL = b"ATV1"


def challenge_response(wallet, device_identity_der: bytes):
    nonce = secrets.token_bytes(16)

    signature = wallet.hardware_attestation(
        nonce).hardware_attestation_rsp.signature

    digest = SHA256.new(ATTESTATION_LABEL + nonce)
    verification_key = ECC.import_key(device_identity_der)

    # Note: device_identity.public_key().verify() *should* work here, but
    # I can't get it to. No clue why.
    # (I did test that corrupting the signature here works)
    try:
        DSS.new(
            verification_key, 'fips-186-3').verify(digest, signature)
    except ValueError as e:
        raise e


def hardware_attestation(wallet) -> str:
    # Step 1) Get certs and check the identity
    batch_cert = bitkey.certs.load_der(
        wallet.cert_get(
            wallet_pb.cert_get_cmd.cert_type.BATCH_CERT).cert_get_rsp.cert
    )

    device_identity_der = wallet.cert_get(
        wallet_pb.cert_get_cmd.cert_type.DEVICE_HOST_CERT).cert_get_rsp.cert

    device_identity = bitkey.certs.load_der(device_identity_der)

    assert bitkey.certs.check_device_cert_is_for_block(
        device_identity), "Wrong identity"

    # Step 2) Validate the cert chain
    factory_cert = bitkey.certs.load_pem(SILABS_FACTORY_CERT)
    device_root = bitkey.certs.load_pem(SILABS_DEVICE_ROOT)

    bitkey.certs.verify_cert_chain([
        device_identity,
        batch_cert,
        factory_cert,
        device_root,
    ])

    # Step 3) Prove the connected hardware actually possesses the private key
    # in the identity cert
    challenge_response(wallet, device_identity_der)

    return device_identity_der


if __name__ == "__main__":
    from bitkey.wallet import Wallet
    wallet = Wallet(WalletComms(NFCTransaction()))
    hardware_attestation(wallet)
