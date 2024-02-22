from .certs import read_se_serial

from .attestation import hardware_attestation

from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat, PrivateFormat
from cryptography.hazmat.primitives import hashes, hmac
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from secrets import token_bytes

from Crypto.PublicKey import ECC
from Crypto.Signature import DSS
from Crypto.Hash import SHA256

from bitkey_proto import wallet_pb2 as wallet_pb
from bitkey_proto import secure_channel_pb2 as secure_channel_pb


class SecureChannel:
    """A secure channel for [en|de]crypting protobuf fields."""

    PROTOCOL_VERSION = 1

    def __init__(self, wallet):
        self.wallet = wallet
        self.comms = self.wallet.comms

    def __enter__(self):
        # NOTE: This step doesn't have to happen every time. The host can cache
        # a trusted pubkey. But this simple Python implementation for development
        # and testing does not do that.
        self.device_identity_der = hardware_attestation(self.wallet)

        self._generate_ephemeral_x25519_keypair()
        rsp = self._send_establish_cmd()

        # Verify the exchange signature using the device identity cert.
        self._verify_exchange_signature(rsp.secure_channel_establish_rsp.pk_device,
                                        self.device_identity_der,
                                        rsp.secure_channel_establish_rsp.exchange_sig)

        # Derive shared keys
        self._derive_session_keys(rsp.secure_channel_establish_rsp.pk_device)
        self._key_confirmation(
            rsp.secure_channel_establish_rsp.key_confirmation_tag)

        return self

    def __exit__(self, type, value, tb):
        pass

    def _generate_ephemeral_x25519_keypair(self):
        private_key = X25519PrivateKey.generate()
        self.privkey = private_key
        self.pubkey = private_key.public_key()

    def _derive_session_keys(self, their_pubkey: bytes):
        shared_curve_point = self.privkey.exchange(
            X25519PublicKey.from_public_bytes(their_pubkey))

        send_key = HKDF(
            algorithm=hashes.SHA256(),
            length=32,
            salt=None,
            info=self._prepare_label(prefix=b"HOST2BK"),
        ).derive(shared_curve_point)

        self.session_send_key = AESGCM(send_key)

        recv_key = HKDF(
            algorithm=hashes.SHA256(),
            length=32,
            salt=None,
            info=self._prepare_label(prefix=b"BK2HOST"),
        ).derive(shared_curve_point)

        self.session_recv_key = AESGCM(recv_key)

        self.session_conf_key = HKDF(
            algorithm=hashes.SHA256(),
            length=32,
            salt=None,
            info=self._prepare_label(prefix=b"CONFIRM"),
        ).derive(shared_curve_point)

    def _key_confirmation(self, key_confirmation_tag: bytes):
        # HMAC with the conf key, and compare the truncated result to the supplied tag.

        h = hmac.HMAC(self.session_conf_key, hashes.SHA256())
        h.update(b"KEYCONFIRM-V1")
        tag = h.finalize()

        KEY_CONFIRMATION_TAG_LEN = 16
        tag = tag[:KEY_CONFIRMATION_TAG_LEN]

        if tag != key_confirmation_tag:
            raise AssertionError("Key confirmation failed")

    def _prepare_label(self, prefix):
        device_serial = read_se_serial(self.device_identity_der)
        return prefix + device_serial

    def _verify_exchange_signature(self, their_pubkey: bytes, device_identity_der: str, signature: bytes):
        # The exchange signature is over: KEYEXCHANGE-V1 || their_pubkey || our_pubkey
        signing_input = b'KEYEXCHANGE-V1' + \
            their_pubkey + self.pubkey.public_bytes_raw()

        digest = SHA256.new(signing_input)
        verification_key = ECC.import_key(device_identity_der)
        try:
            DSS.new(
                verification_key, 'fips-186-3').verify(digest, signature)
        except ValueError as e:
            raise e

    def _send_establish_cmd(self):
        cmd = wallet_pb.wallet_cmd()
        msg = secure_channel_pb.secure_channel_establish_cmd()
        msg.pk_host = self.pubkey.public_bytes(
            encoding=Encoding.Raw, format=PublicFormat.Raw)
        msg.protocol_version = self.PROTOCOL_VERSION
        cmd.secure_channel_establish_cmd.CopyFrom(msg)
        return self.comms.transceive(cmd)

    def encrypt(self, plaintext):
        msg = secure_channel_pb.secure_channel_message()

        nonce = token_bytes(12)
        result = self.session_send_key.encrypt(
            nonce=nonce, data=plaintext, associated_data=None)

        # 16-byte tag is appended to the end
        ciphertext, tag = result[:-16], result[-16:]

        msg.ciphertext = ciphertext
        msg.mac = tag
        msg.nonce = nonce

        return msg
