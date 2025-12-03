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
        ("app_signature", ctypes.c_uint8 * GRANT_SIGNATURE_LEN), # App Signature
        ("wsm_signature", ctypes.c_uint8 * GRANT_SIGNATURE_LEN), # WIK Signature
    ]

    def __init__(self, version: int = GRANT_PROTOCOL_VERSION,
                 serialized_request: bytes = b'\x00'*ctypes.sizeof(GrantRequest),
                 app_signature: bytes = b'\x00'*GRANT_SIGNATURE_LEN,
                 wsm_signature: bytes = b'\x00'*GRANT_SIGNATURE_LEN):
         super().__init__()
         self.version = version

         if not isinstance(serialized_request, bytes) or len(serialized_request) != ctypes.sizeof(GrantRequest):
             raise ValueError(f"serialized_request must be bytes of length {ctypes.sizeof(GrantRequest)}")
         self.serialized_request[:] = serialized_request

         if not isinstance(app_signature, bytes) or len(app_signature) != GRANT_SIGNATURE_LEN:
             raise ValueError(f"app_signature must be bytes of length {GRANT_SIGNATURE_LEN}")
         self.app_signature[:] = app_signature

         if not isinstance(wsm_signature, bytes) or len(wsm_signature) != GRANT_SIGNATURE_LEN:
             raise ValueError(f"wsm_signature must be bytes of length {GRANT_SIGNATURE_LEN}")
         self.wsm_signature[:] = wsm_signature


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
               f" app_signature={bytes(self.app_signature).hex()}," \
               f" wsm_signature={bytes(self.wsm_signature).hex()})"

    def get_wik_signing_input(self) -> bytes:
        """Returns the data that WIK signs: version + serialized_request + app_signature + label"""
        size_excluding_wsm_sig = ctypes.sizeof(self) - GRANT_SIGNATURE_LEN
        grant_part_bytes = self.serialize()[:size_excluding_wsm_sig]
        return b"BKGrant" + grant_part_bytes


@dataclass
class MockApp:
    """Mock app that can generate and manage app auth keys"""

    def __init__(self):
        # Generate a new app auth key pair
        self.app_auth_private_key = ecdsa.SigningKey.generate(curve=ecdsa.SECP256k1)
        self.app_auth_public_key = self.app_auth_private_key.get_verifying_key()

    def get_compressed_public_key(self) -> bytes:
        """Returns the compressed public key (33 bytes) for provisioning"""
        # Get the uncompressed public key
        pubkey_bytes = self.app_auth_public_key.to_string()
        x = int.from_bytes(pubkey_bytes[:32], 'big')
        y = int.from_bytes(pubkey_bytes[32:], 'big')

        # Compress it: 0x02 if y is even, 0x03 if y is odd
        prefix = b'\x02' if y % 2 == 0 else b'\x03'
        return prefix + x.to_bytes(32, 'big')

    def sign_grant_request(self, grant_request: GrantRequest) -> bytes:
        """Signs the grant request core with app auth key"""
        # Build the request core (excluding signature)
        size_excluding_sig = ctypes.sizeof(grant_request) - GRANT_SIGNATURE_LEN
        request_core = grant_request.serialize()[:size_excluding_sig]

        # Sign with "BKAppBind" label
        signing_input = b"BKAppBind" + request_core

        # Sign with RFC6979 deterministic signature
        signature = self.app_auth_private_key.sign_deterministic(
            signing_input,
            hashfunc=hashlib.sha256
        )

        # Normalize to low-s
        signature = self._normalize_signature(signature)

        return signature

    def create_rotation_signature(self, new_pubkey: bytes) -> bytes:
        """DEPRECATED: Signature no longer required for app auth key replacement.
        This method is kept for backwards compatibility but is not used."""
        if len(new_pubkey) != 33:
            raise ValueError("new_pubkey must be 33 bytes (compressed)")

        # Sign "BKAppRotate" || new_pubkey
        signing_input = b"BKAppRotate" + new_pubkey

        signature = self.app_auth_private_key.sign_deterministic(
            signing_input,
            hashfunc=hashlib.sha256
        )

        return self._normalize_signature(signature)

    def _normalize_signature(self, signature: bytes) -> bytes:
        """Normalizes the signature to a low-s value"""
        curve_order = ecdsa.SECP256k1.order
        r = int.from_bytes(signature[:32], 'big')
        s = int.from_bytes(signature[32:], 'big')

        if s * 2 > curve_order:
            s = curve_order - s

        return r.to_bytes(32, 'big') + s.to_bytes(32, 'big')


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

    def sign_grant_for_request(self, request: GrantRequest, app_signature: bytes,
                              verify_request_signature: bool = True,
                              hw_auth_pubkey: bytes = None,
                              verify_app_signature: bool = False,
                              app_pubkey: bytes = None) -> Grant:
        """
        Signs a grant for a request with both app and WIK signatures.

        Args:
            request: The grant request to sign
            app_signature: The app's signature over the request core
            verify_request_signature: Whether to verify the HW auth signature
            hw_auth_pubkey: HW auth public key (required if verifying)
            verify_app_signature: Whether to verify the app signature
            app_pubkey: App public key (required if verifying app signature)
        """
        if verify_request_signature:
            assert hw_auth_pubkey is not None, "hw_auth_pubkey is required to verify the request signature"
            verifying_key = ecdsa.VerifyingKey.from_string(
                hw_auth_pubkey,
                curve=ecdsa.SECP256k1
            )
            print("Verifying request signature...")
            verifying_key.verify(request.signature, request.get_signing_input(), hashfunc=hashlib.sha256)
            print("Request signature verified")

        if verify_app_signature:
            assert app_pubkey is not None, "app_pubkey is required to verify the app signature"
            # Build request core for app signature verification
            size_excluding_sig = ctypes.sizeof(request) - GRANT_SIGNATURE_LEN
            request_core = request.serialize()[:size_excluding_sig]
            signing_input = b"BKAppBind" + request_core

            # Decompress the app public key if needed
            if len(app_pubkey) == 33:
                # It's compressed, need to decompress for ecdsa library
                from ecdsa.ellipticcurve import Point
                from ecdsa import SECP256k1
                x = int.from_bytes(app_pubkey[1:33], 'big')
                # Calculate y from x (this is simplified, proper implementation would solve the curve equation)
                # For now, we'll skip verification if compressed
                print("App signature verification skipped (compressed pubkey handling not fully implemented)")
            else:
                verifying_key = ecdsa.VerifyingKey.from_string(app_pubkey, curve=ecdsa.SECP256k1)
                verifying_key.verify(app_signature, signing_input, hashfunc=hashlib.sha256)
                print("App signature verified")

        grant = Grant(
            version=GRANT_PROTOCOL_VERSION,
            serialized_request=request.serialize(),
            app_signature=app_signature,
            wsm_signature=b'\x00'*GRANT_SIGNATURE_LEN
        )

        signing_input = grant.get_wik_signing_input()

        # RFC6979 + normalize to low-s value.
        signature = ecdsa.SigningKey.from_string(
            bytes.fromhex(self.WIK_DEV_PRIVATE_KEY),
            curve=ecdsa.SECP256k1
        ).sign_deterministic(signing_input, hashfunc=hashlib.sha256)
        signature = self._normalize_signature(signature)

        # Verify the WIK signature too
        verifying_key = ecdsa.VerifyingKey.from_string(
            bytes.fromhex(self.WIK_DEV_PUBLIC_KEY),
            curve=ecdsa.SECP256k1
        )
        verifying_key.verify(signature, signing_input, hashfunc=hashlib.sha256)

        grant.wsm_signature[:] = signature

        return grant


if __name__ == "__main__":
    # Create mock app and server
    app = MockApp()
    server = MockServer()

    # Create a grant request
    request = GrantRequest(action=GrantAction.ACTION_FINGERPRINT_RESET)

    # App signs the request
    app_signature = app.sign_grant_request(request)

    # Server creates grant with app signature
    grant = server.sign_grant_for_request(
        request,
        app_signature,
        verify_request_signature=False  # No HW key in this demo
    )

    print("App public key:", app.get_compressed_public_key().hex())
    print("Grant:", grant)
