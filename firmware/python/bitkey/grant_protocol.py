import ctypes
from enum import IntEnum
from typing import Union
from dataclasses import dataclass
import ecdsa
import hashlib

GRANT_PROTOCOL_VERSION = 1
GRANT_DEVICE_ID_LEN = 8
GRANT_CHALLENGE_LEN = 16
GRANT_SIGNATURE_LEN = 64
SHA256_DIGEST_SIZE = 32

class GrantAction(IntEnum):
    ACTION_INVALID = 0
    ACTION_FINGERPRINT_RESET = 1


class GrantRequest(ctypes.Structure):
    _pack_ = 1 # Ensure packed alignment
    _fields_ = [
        ("version", ctypes.c_uint8),
        ("device_id", ctypes.c_uint8 * GRANT_DEVICE_ID_LEN),
        ("challenge", ctypes.c_uint8 * GRANT_CHALLENGE_LEN),
        ("action", ctypes.c_uint8), # Uses smallest type due to packing
        ("signature", ctypes.c_uint8 * GRANT_SIGNATURE_LEN), # HW Auth Signature
    ]

    def __init__(self, version: int = GRANT_PROTOCOL_VERSION,
                 device_id: bytes = b'\x00'*GRANT_DEVICE_ID_LEN,
                 challenge: bytes = b'\x00'*GRANT_CHALLENGE_LEN,
                 action: Union[GrantAction, int] = GrantAction.ACTION_FINGERPRINT_RESET,
                 signature: bytes = b'\x00'*GRANT_SIGNATURE_LEN):
        super().__init__()
        self.version = version

        if not isinstance(device_id, bytes) or len(device_id) != GRANT_DEVICE_ID_LEN:
            raise ValueError(f"device_id must be bytes of length {GRANT_DEVICE_ID_LEN}")
        self.device_id[:] = device_id

        if not isinstance(challenge, bytes) or len(challenge) != GRANT_CHALLENGE_LEN:
            raise ValueError(f"challenge must be bytes of length {GRANT_CHALLENGE_LEN}")
        self.challenge[:] = challenge

        # Allow passing either the enum or its value
        if isinstance(action, GrantAction):
            self.action = action.value
        elif isinstance(action, int):
             # Basic check if value is defined in enum
             if action not in GrantAction.__members__.values():
                 raise ValueError(f"Invalid integer value for GrantAction: {action}")
             self.action = action
        else:
            raise TypeError("action must be GrantAction enum or integer")

        if not isinstance(signature, bytes) or len(signature) != GRANT_SIGNATURE_LEN:
            raise ValueError(f"signature must be bytes of length {GRANT_SIGNATURE_LEN}")
        self.signature[:] = signature


    def serialize(self) -> bytes:
        """Converts the ctypes Structure instance to bytes."""
        return bytes(self)

    @classmethod
    def deserialize(cls, data: bytes) -> 'GrantRequest':
        """Creates a GrantRequest instance from bytes."""
        if len(data) != ctypes.sizeof(cls):
            raise ValueError(f"Incorrect data size for GrantRequest: got {len(data)}, expected {ctypes.sizeof(cls)}")
        return cls.from_buffer_copy(data)

    def get_signing_input(self) -> bytes:
        """Returns the bytes of the request excluding the signature field."""
        size_excluding_sig = ctypes.sizeof(self) - GRANT_SIGNATURE_LEN
        return b"BKGrantReq" + self.serialize()[:size_excluding_sig]

    def __repr__(self):
        return f"GrantRequest(version={self.version}," \
               f" device_id={bytes(self.device_id).hex()}," \
               f" challenge={bytes(self.challenge).hex()}," \
               f" action={GrantAction(self.action)}," \
               f" signature={bytes(self.signature).hex()})"


class Grant(ctypes.Structure):
    _pack_ = 1 # Ensure packed alignment
    _fields_ = [
        ("version", ctypes.c_uint8),
        ("serialized_request", ctypes.c_uint8 * ctypes.sizeof(GrantRequest)),
        ("signature", ctypes.c_uint8 * GRANT_SIGNATURE_LEN), # WIK Signature
    ]

    def __init__(self, version: int = GRANT_PROTOCOL_VERSION, serialized_request: bytes = b'\x00'*ctypes.sizeof(GrantRequest),
                 signature: bytes = b'\x00'*GRANT_SIGNATURE_LEN):
         super().__init__()
         self.version = version

         if not isinstance(serialized_request, bytes) or len(serialized_request) != ctypes.sizeof(GrantRequest):
             raise ValueError(f"serialized_request must be bytes of length {ctypes.sizeof(GrantRequest)}")
         self.serialized_request[:] = serialized_request

         if not isinstance(signature, bytes) or len(signature) != GRANT_SIGNATURE_LEN:
             raise ValueError(f"signature must be bytes of length {GRANT_SIGNATURE_LEN}")
         self.signature[:] = signature


    def serialize(self) -> bytes:
        """Converts the ctypes Structure instance to bytes."""
        return bytes(self)

    @classmethod
    def deserialize(cls, data: bytes) -> 'Grant':
        """Creates a Grant instance from bytes."""
        if len(data) != ctypes.sizeof(cls):
            raise ValueError(f"Incorrect data size for Grant: got {len(data)}, expected {ctypes.sizeof(cls)}")
        return cls.from_buffer_copy(data)

    def __repr__(self):
        return f"Grant(version={self.version}," \
               f" serialized_request={bytes(self.serialized_request).hex()}," \
               f" signature={bytes(self.signature).hex()})"

    def get_signing_input(self) -> bytes:
        size_excluding_sig = ctypes.sizeof(self) - GRANT_SIGNATURE_LEN
        grant_part_bytes = self.serialize()[:size_excluding_sig]
        return b"BKGrant" + grant_part_bytes


@dataclass
class MockServer:
    # This comes from wallet/server/src/wsm/keys/test_integrity_key.b64
    WIK_DEV_PRIVATE_KEY = "ebdfc29b426cea1028c575775d3196e3293bb08e47942cea59930a1562bd86bc"
    WIK_DEV_PUBLIC_KEY = "03078451e0c1e12743d2fdd93ae7d03d5cf7813d2f612de10904e1c6a0b87f7071"

    def _normalize_signature(self, signature: bytes) -> bytes:
        """Normalizes the signature to a low-s value."""
        curve_order = ecdsa.SECP256k1.order
        r = int.from_bytes(signature[:32], 'big')
        s = int.from_bytes(signature[32:], 'big')

        if s * 2 > curve_order:
            s = curve_order - s

        return r.to_bytes(32, 'big') + s.to_bytes(32, 'big')

    def sign_grant_for_request(self, request: GrantRequest, verify_request_signature: bool = True, hw_auth_pubkey: bytes = None) -> Grant:
        if verify_request_signature:
            assert hw_auth_pubkey is not None, "hw_auth_pubkey is required to verify the request signature"
            verifying_key = ecdsa.VerifyingKey.from_string(
                hw_auth_pubkey,
                curve=ecdsa.SECP256k1
            )
            print("Verifying request signature...")
            verifying_key.verify(request.signature, request.get_signing_input(), hashfunc=hashlib.sha256)
            print("Request signature verified")

        grant = Grant(
            version=GRANT_PROTOCOL_VERSION,
            serialized_request=request.serialize(),
            signature=b'\x00'*GRANT_SIGNATURE_LEN
        )

        signing_input = grant.get_signing_input()

        # RFC6979 + normalize to low-s value.
        signature = ecdsa.SigningKey.from_string(
            bytes.fromhex(self.WIK_DEV_PRIVATE_KEY),
            curve=ecdsa.SECP256k1
        ).sign_deterministic(signing_input, hashfunc=hashlib.sha256)
        signature = self._normalize_signature(signature)

        # Verify the signature too
        verifying_key = ecdsa.VerifyingKey.from_string(
            bytes.fromhex(self.WIK_DEV_PUBLIC_KEY),
            curve=ecdsa.SECP256k1
        )
        verifying_key.verify(signature, signing_input, hashfunc=hashlib.sha256)

        grant.signature[:] = signature

        return grant


if __name__ == "__main__":
    server = MockServer()
    request = GrantRequest(action=GrantAction.ACTION_FINGERPRINT_RESET)
    grant = server.sign_grant_for_request(request)
    print(grant)
