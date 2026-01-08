import tempfile
import unittest
from pathlib import Path

from bitkey.picocert import (
    PICOCERT_CURRENT_VERSION,
    PICOCERT_P256,
    PICOCERT_PUBKEY_SIZE,
    PICOCERT_SHA256,
    PICOCERT_SIGNATURE_SIZE,
    PicocertV1,
    parse_certificate,
    validate_cert_chain,
)

KEYS_DIR = Path(__file__).parent.parent.parent / "config" / "keys" / "w3a-uxc-dev"


class TestPicocert(unittest.TestCase):
    """Test cases for picocert validation."""

    def setUp(self):
        """Load test certificates."""
        with open(KEYS_DIR / "w3a-uxc-bl-signing-cert-dev.1.pct", "rb") as f:
            self.bl_cert = parse_certificate(f.read())

        with open(KEYS_DIR / "w3a-uxc-app-signing-cert-dev.1.pct", "rb") as f:
            self.app_cert = parse_certificate(f.read())

        # Fixed timestamp within validity period
        self.test_time = (self.bl_cert.valid_from + self.bl_cert.valid_to) // 2

    def test_parse_bootloader_cert(self):
        """Test parsing bootloader picocert certificate."""
        self.assertEqual(self.bl_cert.version, PICOCERT_CURRENT_VERSION)
        self.assertEqual(self.bl_cert.curve, PICOCERT_P256)
        self.assertEqual(self.bl_cert.hash, PICOCERT_SHA256)
        self.assertEqual(self.bl_cert.reserved, 0)
        self.assertEqual(self.bl_cert.subject, "w3-uxc-bootloader")
        self.assertEqual(len(self.bl_cert.public_key), PICOCERT_PUBKEY_SIZE)
        self.assertEqual(len(self.bl_cert.signature), PICOCERT_SIGNATURE_SIZE)

    def test_parse_application_cert(self):
        """Test parsing application picocert certificate."""
        self.assertEqual(self.app_cert.version, PICOCERT_CURRENT_VERSION)
        self.assertEqual(self.app_cert.curve, PICOCERT_P256)
        self.assertEqual(self.app_cert.hash, PICOCERT_SHA256)
        self.assertEqual(self.app_cert.reserved, 0)
        self.assertEqual(self.app_cert.subject, "w3-uxc-application")
        self.assertEqual(self.app_cert.issuer, "w3-uxc-bootloader")
        self.assertEqual(len(self.app_cert.public_key), PICOCERT_PUBKEY_SIZE)
        self.assertEqual(len(self.app_cert.signature), PICOCERT_SIGNATURE_SIZE)

    def test_certificate_chain_structure(self):
        """Test that certificate chain structure is correct."""
        # Bootloader signs application
        self.assertEqual(self.bl_cert.subject, self.app_cert.issuer)

    def test_verify_cert_signature(self):
        """Test that verify_cert_signature returns True for valid signature."""
        # Application cert should be signed by bootloader cert
        self.assertTrue(self.app_cert.verify_cert_signature(self.bl_cert))

    def test_validate_success(self):
        """Test that validate() succeeds when validating a cert against its issuer."""
        # Should return True - application cert is signed by bootloader cert
        result = self.app_cert.validate(self.bl_cert)
        self.assertTrue(result)

    def test_serialization_roundtrip(self):
        """Test that to_bytes() and from_bytes() round-trip correctly."""
        with open(KEYS_DIR / "w3a-uxc-bl-signing-cert-dev.1.pct", "rb") as f:
            original_bytes = f.read()

        serialized_bytes = self.bl_cert.to_bytes()

        self.assertEqual(original_bytes, serialized_bytes)
        self.assertEqual(len(serialized_bytes), PicocertV1.SIZE)

    def test_validate_serialize_parse_revalidate(self):
        """Test that validation works after serialization and parsing."""
        # First validation - should return True
        result1 = self.app_cert.validate(self.bl_cert)
        self.assertTrue(result1)

        # Serialize both certificates
        app_cert_bytes = self.app_cert.to_bytes()
        bl_cert_bytes = self.bl_cert.to_bytes()

        # Parse back from bytes
        app_cert_reparsed = parse_certificate(app_cert_bytes)
        bl_cert_reparsed = parse_certificate(bl_cert_bytes)

        # Validate again - should still work and return True
        result2 = app_cert_reparsed.validate(bl_cert_reparsed)
        self.assertTrue(result2)

        # Verify signature also works
        self.assertTrue(app_cert_reparsed.verify_cert_signature(bl_cert_reparsed))

    def test_serialize_to_disk(self):
        """Test that serializing a certificate to disk produces correct file size."""
        # Get original certificate file size
        original_file = KEYS_DIR / "w3a-uxc-app-signing-cert-dev.1.pct"
        original_size = original_file.stat().st_size

        # Serialize the parsed certificate
        serialized_bytes = self.app_cert.to_bytes()

        # Write to temporary file
        with tempfile.NamedTemporaryFile(delete=False, suffix=".pct") as tmp:
            tmp.write(serialized_bytes)
            tmp_path = Path(tmp.name)

        try:
            # Verify file size matches original
            self.assertEqual(tmp_path.stat().st_size, original_size)
            self.assertEqual(tmp_path.stat().st_size, PicocertV1.SIZE)

            # Read back and verify it's valid
            with open(tmp_path, "rb") as f:
                reparsed_cert = parse_certificate(f.read())

            # Verify the reparsed cert validates against bootloader
            self.assertTrue(reparsed_cert.validate(self.bl_cert))
        finally:
            # Clean up temp file
            tmp_path.unlink()

    def test_validate_invalid_version(self):
        """Test that validate() raises ValueError for invalid version."""
        # Create a copy and modify the version
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.version = 99

        with self.assertRaises(ValueError):
            test_cert.validate(self.bl_cert)

    def test_validate_invalid_curve(self):
        """Test that validate() raises ValueError for invalid curve."""
        # Create a copy and modify the curve
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.curve = 1

        with self.assertRaises(ValueError):
            test_cert.validate(self.bl_cert)

    def test_validate_invalid_hash(self):
        """Test that validate() raises ValueError for invalid hash algorithm."""
        # Create a copy and modify the hash
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.hash = 1

        with self.assertRaises(ValueError):
            test_cert.validate(self.bl_cert)

    def test_validate_reserved_field(self):
        """Test that validate() raises ValueError for non-zero reserved field."""
        # Create a copy and modify the reserved field
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.reserved = 1

        with self.assertRaises(ValueError):
            test_cert.validate(self.bl_cert)

    def test_validate_issuer_mismatch(self):
        """Test that validate() raises ValueError for issuer name mismatch."""
        # Create a copy and modify the issuer name
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.issuer = "wrong-issuer"

        with self.assertRaises(ValueError):
            test_cert.validate(self.bl_cert)

    def test_validate_invalid_signature(self):
        """Test that validate() raises ValueError for invalid signature."""
        # Create a copy and modify the signature
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.signature = b"\x00" * PICOCERT_SIGNATURE_SIZE

        with self.assertRaises(ValueError):
            test_cert.validate(self.bl_cert)

    def test_verify_cert_signature_invalid(self):
        """Test that verify_cert_signature returns False for invalid signature."""
        # Create a copy and corrupt the signature
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.signature = b"\x00" * PICOCERT_SIGNATURE_SIZE

        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_validate_cert_chain_success(self):
        """Test that validate_cert_chain succeeds for valid chain."""
        # Create a chain: app cert -> bootloader cert (self-signed root)
        chain = [self.app_cert, self.bl_cert]

        # Should not raise any exception
        validate_cert_chain(chain)

    def test_validate_cert_chain_empty(self):
        """Test that validate_cert_chain raises ValueError for empty chain."""
        with self.assertRaises(ValueError):
            validate_cert_chain([])

    def test_validate_cert_chain_not_self_signed_root(self):
        """Test that validate_cert_chain raises ValueError when root is not self-signed."""
        # Create a chain where the root is not self-signed
        # We'll just use app_cert alone, which has issuer="w3-uxc-bootloader"
        with self.assertRaises(ValueError):
            validate_cert_chain([self.app_cert])

    def test_parse_certificate_unsupported_version(self):
        """Test that parse_certificate raises ValueError for unsupported version."""
        # Create invalid cert data with version 99
        invalid_data = bytes([99]) + b"\x00" * (PicocertV1.SIZE - 1)

        with self.assertRaises(ValueError):
            parse_certificate(invalid_data)

    def test_parse_certificate_empty(self):
        """Test that parse_certificate raises ValueError for empty data."""
        with self.assertRaises(ValueError):
            parse_certificate(b"")

    def test_str_representation(self):
        """Test the string representation of a certificate."""
        cert_str = str(self.bl_cert)

        self.assertIn("PicocertV1", cert_str)
        self.assertIn("w3-uxc-bootloader", cert_str)
        self.assertIn("P-256", cert_str)
        self.assertIn("SHA-256", cert_str)

    def test_signature_invalid_after_modifying_public_key(self):
        """Test that signature verification fails if public key is modified."""
        # Create a copy and modify the public key
        test_cert = parse_certificate(self.app_cert.to_bytes())
        modified_pubkey = bytearray(test_cert.public_key)
        modified_pubkey[0] ^= 0xFF  # Flip bits in first byte
        test_cert.public_key = bytes(modified_pubkey)

        # Signature should no longer verify
        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_after_modifying_subject(self):
        """Test that signature verification fails if subject name is modified."""
        # Create a copy and modify the subject name
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.subject = "tampered-subject"

        # Signature should no longer verify
        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_after_modifying_issuer(self):
        """Test that signature verification fails if issuer name is modified."""
        # Create a copy and modify the issuer name
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.issuer = "tampered-issuer"

        # Signature should no longer verify
        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_after_modifying_validity_dates(self):
        """Test that signature verification fails if validity dates are modified."""
        # Create a copy and modify valid_from
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.valid_from = 12345
        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

        # Create a fresh copy and modify valid_to
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.valid_to = 99999999999
        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_after_modifying_curve(self):
        """Test that signature verification fails if curve field is modified."""
        # Create a copy and modify the curve
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.curve = 99  # Invalid curve

        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_after_modifying_hash(self):
        """Test that signature verification fails if hash field is modified."""
        # Create a copy and modify the hash
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.hash = 99  # Invalid hash

        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_after_modifying_reserved(self):
        """Test that signature verification fails if reserved field is modified."""
        # Create a copy and modify the reserved field
        test_cert = parse_certificate(self.app_cert.to_bytes())
        test_cert.reserved = 42  # Non-zero reserved

        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_with_partial_corruption(self):
        """Test that even small signature corruption causes verification failure."""
        # Create a copy and flip a single bit in the signature
        test_cert = parse_certificate(self.app_cert.to_bytes())
        modified_sig = bytearray(test_cert.signature)
        modified_sig[32] ^= 0x01  # Flip one bit in the middle
        test_cert.signature = bytes(modified_sig)

        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))

    def test_signature_invalid_with_truncated_signature(self):
        """Test that truncated signature is detected."""
        # Create a copy and use a truncated signature
        test_cert = parse_certificate(self.app_cert.to_bytes())
        short_signature = test_cert.signature[:32]  # Only 32 bytes instead of 64

        # This should fail during DER conversion or verification
        test_cert.signature = short_signature + b"\x00" * 32
        self.assertFalse(test_cert.verify_cert_signature(self.bl_cert))


if __name__ == "__main__":
    unittest.main()
