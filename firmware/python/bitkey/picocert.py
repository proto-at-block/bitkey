import time
from typing import List

from construct import Bytes, Int8ul, Int32ul, Int64ul, PaddedString, Struct
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS

# Use deterministic ECDSA signing mode (RFC 6979)
DSS_MODE = "deterministic-rfc6979"

PICOCERT_CURRENT_VERSION = 1
PICOCERT_P256 = 0
PICOCERT_SHA256 = 0
PICOCERT_MAX_NAME_LEN = 32
PICOCERT_MAX_CHAIN_LEN = 10
PICOCERT_PUBKEY_SIZE = 65
PICOCERT_SIGNATURE_SIZE = 64
ECDSA_P256_COORDINATE_SIZE = 32
ECDSA_CURVE = "NIST P-256"

PicocertV1Struct = Struct(
    "version" / Int8ul,
    "issuer" / PaddedString(PICOCERT_MAX_NAME_LEN, "utf8"),
    "subject" / PaddedString(PICOCERT_MAX_NAME_LEN, "utf8"),
    "valid_from" / Int64ul,
    "valid_to" / Int64ul,
    "curve" / Int8ul,
    "hash" / Int8ul,
    "reserved" / Int32ul,
    "public_key" / Bytes(PICOCERT_PUBKEY_SIZE),
    "signature" / Bytes(PICOCERT_SIGNATURE_SIZE),
)


class PicocertV1:
    VERSION = 1
    STRUCT = PicocertV1Struct
    SIZE = PicocertV1Struct.sizeof()  # Total certificate size in bytes
    SIGNABLE_SIZE = SIZE - PICOCERT_SIGNATURE_SIZE  # Size of data that gets signed (everything except signature)

    def __init__(
        self,
        issuer: str,
        subject: str,
        valid_from: int,
        valid_to: int,
        curve: int,
        hash: int,
        public_key: bytes,
        signature: bytes,
        reserved: int = 0,
    ):
        """
        Create a new Picocert V1 certificate.

        Reference: https://github.com/block/picocert

        Args:
            issuer: Issuer name (max 32 bytes)
            subject: Subject name (max 32 bytes)
            valid_from: Start of validity period (Unix timestamp)
            valid_to: End of validity period (Unix timestamp)
            curve: Curve ID (0 = P-256)
            hash: Hash algorithm ID (0 = SHA-256)
            public_key: Uncompressed ECC public key (65 bytes)
            signature: ECDSA signature in r||s format (64 bytes)
            reserved: Reserved field (must be 0)
        """
        self.version = self.VERSION
        self.issuer = issuer
        self.subject = subject
        self.valid_from = valid_from
        self.valid_to = valid_to
        self.curve = curve
        self.hash = hash
        self.reserved = reserved
        self.public_key = public_key
        self.signature = signature

    @classmethod
    def from_bytes(cls, data: bytes) -> "PicocertV1":
        """
        Parse a certificate from binary data.
        """
        try:
            parsed = cls.STRUCT.parse(data)
            return cls(
                issuer=parsed.issuer,
                subject=parsed.subject,
                valid_from=parsed.valid_from,
                valid_to=parsed.valid_to,
                curve=parsed.curve,
                hash=parsed.hash,
                public_key=parsed.public_key,
                signature=parsed.signature,
                reserved=parsed.reserved,
            )
        except Exception as e:
            raise ValueError(f"Failed to parse certificate: {e}")

    def to_bytes(self) -> bytes:
        """
        Serialize the certificate to binary format.

        Returns:
            Binary certificate data (217 bytes)
        """
        return self.STRUCT.build(
            {
                "version": self.version,
                "issuer": self.issuer,
                "subject": self.subject,
                "valid_from": self.valid_from,
                "valid_to": self.valid_to,
                "curve": self.curve,
                "hash": self.hash,
                "reserved": self.reserved,
                "public_key": self.public_key,
                "signature": self.signature,
            }
        )

    def get_public_key(self) -> ECC.EccKey:
        """Get the public key for verification."""
        if self.public_key[0] != 0x04:
            raise ValueError("Public key must be in uncompressed format (start with 0x04)")

        x = int.from_bytes(
            self.public_key[1 : ECDSA_P256_COORDINATE_SIZE + 1],
            byteorder="big",
            signed=False,
        )
        y = int.from_bytes(
            self.public_key[ECDSA_P256_COORDINATE_SIZE + 1 :],
            byteorder="big",
            signed=False,
        )
        return ECC.construct(curve=ECDSA_CURVE, point_x=x, point_y=y)

    def verify_cert_signature(self, issuer: "PicocertV1") -> bool:
        """
        Verify this certificate's signature using the issuer's public key.
        """
        try:
            # Extract signable portion (everything except signature)
            cert_bytes = self.to_bytes()
            signable_data = cert_bytes[: self.SIGNABLE_SIZE]
            hash_obj = SHA256.new(signable_data)

            # Verify the signature using the issuer's public key
            issuer_public_key = issuer.get_public_key()
            verifier = DSS.new(issuer_public_key, DSS_MODE)
            verifier.verify(hash_obj, self.signature)
            return True

        except Exception:
            return False

    def verify_signature(self, hash_obj: SHA256.SHA256Hash, signature: bytes) -> bool:
        """
        Verify a signature using this certificate's public key and a pre-computed SHA256 hash.

        Args:
            hash_obj: A pre-computed SHA256 hash object (not raw data).
            signature: The signature bytes to verify.

        Returns:
            True if the signature is valid, False otherwise.
        """
        try:
            verifier = DSS.new(self.get_public_key(), DSS_MODE)
            verifier.verify(hash_obj, signature)
            return True

        except Exception:
            return False

    def validate(self, issuer: "PicocertV1") -> bool:
        """
        Validate this certificate against its issuer.

        Python equivalent of picocert_validate_cert() from the C implementation.
        Performs comprehensive validation including version, curve, hash, reserved fields,
        validity periods, signature verification, and name chain validation.
        """
        if issuer is None:
            raise ValueError("Invalid issuer certificate (None)")

        # Version check
        if issuer.version != PICOCERT_CURRENT_VERSION:
            raise ValueError(f"Issuer version {issuer.version} != {PICOCERT_CURRENT_VERSION}")
        if self.version != PICOCERT_CURRENT_VERSION:
            raise ValueError(f"Subject version {self.version} != {PICOCERT_CURRENT_VERSION}")

        # Validate curve - only P256 is currently supported
        if issuer.curve != PICOCERT_P256:
            raise ValueError(f"Issuer curve {issuer.curve} != {PICOCERT_P256} (P-256)")
        if self.curve != PICOCERT_P256:
            raise ValueError(f"Subject curve {self.curve} != {PICOCERT_P256} (P-256)")

        # Validate hash algorithm - only SHA256 is currently supported
        if issuer.hash != PICOCERT_SHA256:
            raise ValueError(f"Issuer hash {issuer.hash} != {PICOCERT_SHA256} (SHA-256)")
        if self.hash != PICOCERT_SHA256:
            raise ValueError(f"Subject hash {self.hash} != {PICOCERT_SHA256} (SHA-256)")

        # Reserved fields must be zero
        if issuer.reserved != 0:
            raise ValueError(f"Issuer reserved field is {issuer.reserved}, expected 0")
        if self.reserved != 0:
            raise ValueError(f"Subject reserved field is {self.reserved}, expected 0")

        # Ensure validity periods are logical (start <= end)
        if self.valid_from > self.valid_to:
            raise ValueError(f"Subject validity period invalid: {self.valid_from} > {self.valid_to}")
        if issuer.valid_from > issuer.valid_to:
            raise ValueError(f"Issuer validity period invalid: {issuer.valid_from} > {issuer.valid_to}")

        # Ensure the issuer signed this certificate
        if not self.verify_cert_signature(issuer):
            raise ValueError("Certificate signature verification failed")

        # Ensure the names match (issuer.subject == self.issuer)
        if issuer.subject != self.issuer:
            raise ValueError(f"Issuer name mismatch: issuer.subject='{issuer.subject}' != subject.issuer='{self.issuer}'")

        # Ensure certificates are not expired
        # All times are Unix timestamps (seconds since epoch)
        current_time = int(time.time())

        if current_time < self.valid_from or current_time > self.valid_to:
            raise ValueError(f"Subject certificate expired (now={current_time}, valid={self.valid_from}-{self.valid_to})")
        if current_time < issuer.valid_from or current_time > issuer.valid_to:
            raise ValueError(f"Issuer certificate expired (now={current_time}, valid={issuer.valid_from}-{issuer.valid_to})")

        return True

    def is_self_signed(self) -> bool:
        """Check if this certificate is self-signed (issuer == subject)."""
        return self.issuer == self.subject

    def __str__(self) -> str:
        """Human-readable representation."""
        return (
            f"PicocertV1:\n"
            f"  Issuer:  {self.issuer}\n"
            f"  Subject: {self.subject}\n"
            f"  Valid:   {self.valid_from} - {self.valid_to}\n"
            f"  Curve:   P-256 ({self.curve})\n"
            f"  Hash:    SHA-256 ({self.hash})"
        )


def validate_cert_chain(cert_chain: List[PicocertV1]) -> None:
    """
    Validate a certificate chain from leaf to root.

    Python equivalent of picocert_validate_cert_chain() from the C implementation.
    The chain is ordered from leaf (index 0) to root (index -1).
    """
    if not cert_chain:
        raise ValueError("Certificate chain is empty")

    # Verify each certificate in the chain
    for i in range(len(cert_chain) - 1):
        subject = cert_chain[i]
        issuer = cert_chain[i + 1]
        subject.validate(issuer)

    # Ensure the root certificate is self-signed
    root = cert_chain[-1]
    if not root.is_self_signed():
        raise ValueError("Root certificate is not self-signed")

    # Validate root against itself
    root.validate(root)


def parse_certificate(data: bytes) -> PicocertV1:
    """
    Parse a picocert certificate from binary data.

    Reads the version byte and constructs the appropriate certificate class.
    """
    if len(data) < 1:
        raise ValueError("Invalid certificate data: empty")

    version = data[0]

    if version == 1:
        return PicocertV1.from_bytes(data)
    else:
        raise ValueError(f"Unsupported certificate version: {version}")
