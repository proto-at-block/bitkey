"""Unit tests for delta firmware update verification (verify_patch and verify_delta commands)."""

import tempfile
import unittest
from pathlib import Path

import click.testing
from bitkey.firmware_signer import (
    FwupDeltaPatchGenerator,
    apply_patch,
    cli,
    verify_delta_update,
    verify_firmware_signature_with_padding,
    verify_patch_signature,
)
from bitkey.fwa.bitkey_fwa.constants import PRODUCT_W1A, PRODUCT_W3A_CORE, PRODUCT_W3A_UXC
from bitkey.key_manager import LocalKeyManager, PatchSigningKeys, SigningKeys
from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.Signature import DSS

KEYS_DIR = Path(__file__).parent.parent.parent / "config" / "keys"


class TestVerifyPatchSignature(unittest.TestCase):
    """Test cases for verify_patch_signature function."""

    def setUp(self):
        self.patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")
        self.key_manager = LocalKeyManager(self.patch_keys)

        # Create test patch data and sign it
        self.test_patch_content = b"This is test patch content for delta update verification"

    def _create_signed_patch(self, content: bytes, use_correct_key: bool = True) -> Path:
        """Helper to create a signed patch file."""
        digest = SHA256.new(content)

        if use_correct_key:
            signature = self.key_manager.generate_signature(digest)
        else:
            # Create signature with wrong key (generate random key)
            wrong_key = ECC.generate(curve="P-256")
            signature = DSS.new(wrong_key, "deterministic-rfc6979").sign(digest)

        # Write to temp file
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        tmp.write(content)
        tmp.write(signature)
        tmp.close()
        return Path(tmp.name)

    def test_verify_valid_patch_signature(self):
        """Test verification of a correctly signed patch."""
        patch_path = self._create_signed_patch(self.test_patch_content)
        try:
            success, error = verify_patch_signature(patch_path, self.key_manager)
            self.assertTrue(success)
            self.assertIsNone(error)
        finally:
            patch_path.unlink(missing_ok=True)

    def test_verify_invalid_patch_signature_wrong_key(self):
        """Test verification fails with wrong signing key."""
        patch_path = self._create_signed_patch(self.test_patch_content, use_correct_key=False)
        try:
            success, error = verify_patch_signature(patch_path, self.key_manager)
            self.assertFalse(success)
            self.assertIsNotNone(error)
        finally:
            patch_path.unlink(missing_ok=True)

    def test_verify_corrupted_signature(self):
        """Test verification fails with corrupted (zero-filled) signature."""
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        tmp.write(self.test_patch_content)
        tmp.write(bytes(64))  # Zero-filled invalid signature
        tmp.close()
        patch_path = Path(tmp.name)

        try:
            success, error = verify_patch_signature(patch_path, self.key_manager)
            self.assertFalse(success)
            self.assertIsNotNone(error)
        finally:
            patch_path.unlink(missing_ok=True)

    def test_verify_tampered_content(self):
        """Test verification fails when patch content is tampered after signing."""
        patch_path = self._create_signed_patch(self.test_patch_content)

        try:
            # Tamper the content (modify one byte, preserving signature)
            with open(patch_path, "rb") as f:
                data = bytearray(f.read())
            data[0] ^= 0xFF  # Flip bits of first byte

            with open(patch_path, "wb") as f:
                f.write(data)

            success, error = verify_patch_signature(patch_path, self.key_manager)
            self.assertFalse(success)
            self.assertIsNotNone(error)
        finally:
            patch_path.unlink(missing_ok=True)

    def test_verify_patch_too_small(self):
        """Test verification raises ValueError when patch file is too small."""
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        tmp.write(b"small")  # Less than 64 bytes
        tmp.close()
        patch_path = Path(tmp.name)

        try:
            with self.assertRaises(ValueError) as ctx:
                verify_patch_signature(patch_path, self.key_manager)
            self.assertIn("too small", str(ctx.exception))
        finally:
            patch_path.unlink(missing_ok=True)


class TestApplyPatch(unittest.TestCase):
    """Test cases for apply_patch function."""

    def setUp(self):
        self.patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")

        # Create simple test binaries
        self.from_content = b"Original firmware content version 1"
        self.to_content = b"Updated firmware content version 2"

    def _create_signed_patch_from_binaries(self, from_content: bytes, to_content: bytes) -> Path:
        """Create a signed patch between two firmware versions."""
        # Read private key
        with open(self.patch_keys.private_key_path, "r") as f:
            key_pem = f.read()

        # Create temp files for from and to binaries
        from_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
        from_tmp.write(from_content)
        from_tmp.close()

        to_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
        to_tmp.write(to_content)
        to_tmp.close()

        patch_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        patch_tmp.close()

        try:
            FwupDeltaPatchGenerator.create_and_sign(key_pem, from_tmp.name, to_tmp.name, patch_tmp.name)
            return Path(patch_tmp.name)
        finally:
            Path(from_tmp.name).unlink(missing_ok=True)
            Path(to_tmp.name).unlink(missing_ok=True)

    def test_apply_patch_success(self):
        """Test successful patch application."""
        # Create from binary
        from_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
        from_tmp.write(self.from_content)
        from_tmp.close()
        from_path = Path(from_tmp.name)

        # Create signed patch
        patch_path = self._create_signed_patch_from_binaries(self.from_content, self.to_content)

        output_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
        output_tmp.close()
        output_path = Path(output_tmp.name)

        try:
            success, error = apply_patch(from_path, patch_path, output_path)
            self.assertTrue(success, f"Patch application failed: {error}")
            self.assertIsNone(error)

            # Verify output matches expected "to" content
            with open(output_path, "rb") as f:
                output_content = f.read()
            self.assertEqual(output_content, self.to_content)
        finally:
            from_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            output_path.unlink(missing_ok=True)

    def test_apply_patch_file_too_small(self):
        """Test patch application raises ValueError with too-small patch file."""
        from_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
        from_tmp.write(self.from_content)
        from_tmp.close()
        from_path = Path(from_tmp.name)

        # Create tiny invalid patch
        patch_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        patch_tmp.write(b"tiny")
        patch_tmp.close()
        patch_path = Path(patch_tmp.name)

        output_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
        output_tmp.close()
        output_path = Path(output_tmp.name)

        try:
            with self.assertRaises(ValueError) as ctx:
                apply_patch(from_path, patch_path, output_path)
            self.assertIn("too small", str(ctx.exception))
        finally:
            from_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            output_path.unlink(missing_ok=True)


class TestVerifyDeltaUpdate(unittest.TestCase):
    """Test cases for verify_delta_update function (end-to-end with signature verification)."""

    def setUp(self):
        self.patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")
        self.patch_key_manager = LocalKeyManager(self.patch_keys)

        self.firmware_keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev", "app")
        self.firmware_key_manager = LocalKeyManager(self.firmware_keys)

        # Create test firmware data
        self.from_firmware_data = b"Original firmware data v1"
        self.to_firmware_data = b"Updated firmware data v2"

        # Use a small flash slot size for testing (binary size + padding + signature area)
        self.flash_slot_size = len(self.to_firmware_data) + 1024  # 1KB padding for test

    def _create_detached_signature(self, firmware_data: bytes) -> bytes:
        """Create a valid detached signature for firmware by padding and signing."""
        padded_size_for_hash = self.flash_slot_size - 64  # minus signature area
        padding_needed = padded_size_for_hash - len(firmware_data)
        padded_data = firmware_data + (b"\xff" * padding_needed)
        digest = SHA256.new(padded_data)
        return self.firmware_key_manager.generate_signature(digest)

    def _create_delta_test_files(self):
        """Create test files for delta update testing.

        Returns:
            from_path: Path to source firmware (.signed.bin)
            to_path: Path to target firmware (.signed.bin)
            patch_path: Path to signed patch
            sig_path: Path to detached signature for target firmware
        """
        # Create from binary
        from_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin")
        from_tmp.write(self.from_firmware_data)
        from_tmp.close()
        from_path = Path(from_tmp.name)

        # Create to binary
        to_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin")
        to_tmp.write(self.to_firmware_data)
        to_tmp.close()
        to_path = Path(to_tmp.name)

        # Create signed patch
        with open(self.patch_keys.private_key_path, "r") as f:
            key_pem = f.read()

        patch_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.patch")
        patch_tmp.close()
        FwupDeltaPatchGenerator.create_and_sign(key_pem, from_tmp.name, to_tmp.name, patch_tmp.name)
        patch_path = Path(patch_tmp.name)

        # Create detached signature for target firmware
        signature = self._create_detached_signature(self.to_firmware_data)
        sig_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".detached_signature")
        sig_tmp.write(signature)
        sig_tmp.close()
        sig_path = Path(sig_tmp.name)

        return from_path, to_path, patch_path, sig_path

    def test_verify_delta_update_success(self):
        """Test successful delta update verification with signature verification."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        try:
            success, error = verify_delta_update(
                patch_path,
                from_path,
                sig_path,
                self.patch_key_manager,
                self.firmware_key_manager,
                self.flash_slot_size,
                to_path,
            )
            self.assertTrue(success, f"Delta update verification failed: {error}")
            self.assertIsNone(error)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)

    def test_verify_delta_update_success_without_to_signed_bin(self):
        """Test successful delta update verification without optional .signed.bin comparison."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        try:
            # Don't pass to_signed_bin - signature verification alone should be sufficient
            success, error = verify_delta_update(
                patch_path, from_path, sig_path, self.patch_key_manager, self.firmware_key_manager, self.flash_slot_size
            )
            self.assertTrue(success, f"Delta update verification failed: {error}")
            self.assertIsNone(error)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)

    def test_verify_delta_update_invalid_patch_signature(self):
        """Test delta update fails early with invalid patch signature."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        # Create patch with invalid signature
        patch_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.patch")
        patch_tmp.write(b"fake patch content")
        patch_tmp.write(bytes(64))  # Invalid signature
        patch_tmp.close()
        bad_patch_path = Path(patch_tmp.name)

        try:
            success, error = verify_delta_update(
                bad_patch_path, from_path, sig_path, self.patch_key_manager, self.firmware_key_manager, self.flash_slot_size
            )
            self.assertFalse(success)
            self.assertIn("Patch signature verification failed", error)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)
            bad_patch_path.unlink(missing_ok=True)

    def test_verify_delta_update_invalid_firmware_signature(self):
        """Test delta update fails with invalid firmware signature."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        # Create invalid (wrong) signature
        bad_sig_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".detached_signature")
        bad_sig_tmp.write(bytes(64))  # Zero-filled invalid signature
        bad_sig_tmp.close()
        bad_sig_path = Path(bad_sig_tmp.name)

        try:
            success, error = verify_delta_update(
                patch_path, from_path, bad_sig_path, self.patch_key_manager, self.firmware_key_manager, self.flash_slot_size
            )
            self.assertFalse(success)
            self.assertIn("Firmware signature verification failed", error)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)
            bad_sig_path.unlink(missing_ok=True)

    def test_verify_delta_update_wrong_to_signed_bin(self):
        """Test delta update fails when patched result doesn't match expected .signed.bin."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        # Create a wrong "to" .signed.bin
        wrong_to_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin")
        wrong_to_tmp.write(b"Wrong firmware content")
        wrong_to_tmp.close()
        wrong_to_path = Path(wrong_to_tmp.name)

        try:
            success, error = verify_delta_update(
                patch_path,
                from_path,
                sig_path,
                self.patch_key_manager,
                self.firmware_key_manager,
                self.flash_slot_size,
                wrong_to_path,
            )
            self.assertFalse(success)
            self.assertIn("does not match expected .signed.bin", error)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)
            wrong_to_path.unlink(missing_ok=True)


class TestVerifyFirmwareSignatureWithPadding(unittest.TestCase):
    """Test cases for verify_firmware_signature_with_padding function."""

    def setUp(self):
        self.firmware_keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev", "app")
        self.key_manager = LocalKeyManager(self.firmware_keys)
        self.test_firmware = b"Test firmware binary content"
        self.flash_slot_size = 1024  # Small test size

    def _create_valid_signature(self, firmware_data: bytes, flash_slot_size: int) -> bytes:
        """Create a valid signature by padding firmware to flash slot size."""
        padded_size_for_hash = flash_slot_size - 64
        padding_needed = padded_size_for_hash - len(firmware_data)
        padded_data = firmware_data + (b"\xff" * padding_needed)
        digest = SHA256.new(padded_data)
        return self.key_manager.generate_signature(digest)

    def test_verify_valid_signature_with_padding(self):
        """Test successful signature verification with proper padding."""
        signature = self._create_valid_signature(self.test_firmware, self.flash_slot_size)

        success, error = verify_firmware_signature_with_padding(
            self.test_firmware, signature, self.flash_slot_size, self.key_manager
        )
        self.assertTrue(success, f"Signature verification failed: {error}")
        self.assertIsNone(error)

    def test_verify_wrong_signature_size(self):
        """Test verification fails with wrong signature size."""
        success, error = verify_firmware_signature_with_padding(
            self.test_firmware, b"short", self.flash_slot_size, self.key_manager
        )
        self.assertFalse(success)
        self.assertIn("Invalid signature size", error)

    def test_verify_binary_too_large(self):
        """Test verification fails when binary is too large for flash slot."""
        large_firmware = b"x" * (self.flash_slot_size + 100)  # Larger than slot
        signature = bytes(64)

        success, error = verify_firmware_signature_with_padding(
            large_firmware, signature, self.flash_slot_size, self.key_manager
        )
        self.assertFalse(success)
        self.assertIn("Binary too large", error)

    def test_verify_tampered_firmware(self):
        """Test verification fails when firmware is tampered."""
        signature = self._create_valid_signature(self.test_firmware, self.flash_slot_size)

        # Tamper with firmware
        tampered = b"Tampered firmware content!!"

        success, error = verify_firmware_signature_with_padding(tampered, signature, self.flash_slot_size, self.key_manager)
        self.assertFalse(success)


class TestVerifyPatchCLI(unittest.TestCase):
    """Test cases for verify-patch CLI command."""

    def setUp(self):
        self.runner = click.testing.CliRunner()
        self.patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")
        self.key_manager = LocalKeyManager(self.patch_keys)

    def _create_signed_patch(self, content: bytes) -> Path:
        """Helper to create a signed patch file."""
        digest = SHA256.new(content)
        signature = self.key_manager.generate_signature(digest)

        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        tmp.write(content)
        tmp.write(signature)
        tmp.close()
        return Path(tmp.name)

    def test_verify_patch_cli_success(self):
        """Test verify-patch CLI with valid patch."""
        patch_path = self._create_signed_patch(b"test patch content for CLI")

        try:
            result = self.runner.invoke(
                cli,
                [
                    "verify-patch",
                    "--patch",
                    str(patch_path),
                    "--product",
                    PRODUCT_W3A_UXC,
                    "--key-type",
                    "dev",
                    "--keys-dir",
                    str(KEYS_DIR),
                ],
            )
            self.assertEqual(0, result.exit_code, f"Command failed: {result.output}")
            self.assertIn("PASSED", result.output)
        finally:
            patch_path.unlink(missing_ok=True)

    def test_verify_patch_cli_invalid_signature(self):
        """Test verify-patch CLI with invalid signature."""
        # Create patch with invalid signature
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        tmp.write(b"test patch content")
        tmp.write(bytes(64))  # Invalid signature
        tmp.close()
        patch_path = Path(tmp.name)

        try:
            result = self.runner.invoke(
                cli,
                [
                    "verify-patch",
                    "--patch",
                    str(patch_path),
                    "--product",
                    PRODUCT_W3A_UXC,
                    "--key-type",
                    "dev",
                    "--keys-dir",
                    str(KEYS_DIR),
                ],
            )
            self.assertNotEqual(0, result.exit_code)
            self.assertIn("FAILED", result.output)
        finally:
            patch_path.unlink(missing_ok=True)


class TestVerifyDeltaCLI(unittest.TestCase):
    """Test cases for verify-delta CLI command (with mandatory signature verification)."""

    # Use the real flash slot size for CLI tests (CLI gets it from partition config)
    FLASH_SLOT_SIZE = 896 * 1024  # 896KB for w3a-uxc

    def setUp(self):
        self.runner = click.testing.CliRunner()

        self.patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")
        self.firmware_keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev", "app")
        self.firmware_key_manager = LocalKeyManager(self.firmware_keys)

        self.from_firmware_data = b"From firmware CLI test"
        self.to_firmware_data = b"To firmware CLI test"

    def _create_detached_signature(self, firmware_data: bytes) -> bytes:
        """Create a valid detached signature for firmware."""
        padded_size_for_hash = self.FLASH_SLOT_SIZE - 64
        padding_needed = padded_size_for_hash - len(firmware_data)
        padded_data = firmware_data + (b"\xff" * padding_needed)
        digest = SHA256.new(padded_data)
        return self.firmware_key_manager.generate_signature(digest)

    def _create_delta_test_files(self):
        """Create test files for delta CLI testing."""
        # Create from binary
        from_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin")
        from_tmp.write(self.from_firmware_data)
        from_tmp.close()
        from_path = Path(from_tmp.name)

        # Create to binary
        to_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.bin")
        to_tmp.write(self.to_firmware_data)
        to_tmp.close()
        to_path = Path(to_tmp.name)

        with open(self.patch_keys.private_key_path, "r") as f:
            key_pem = f.read()

        patch_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.patch")
        patch_tmp.close()
        FwupDeltaPatchGenerator.create_and_sign(key_pem, from_tmp.name, to_tmp.name, patch_tmp.name)
        patch_path = Path(patch_tmp.name)

        # Create detached signature for target firmware
        signature = self._create_detached_signature(self.to_firmware_data)
        sig_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".detached_signature")
        sig_tmp.write(signature)
        sig_tmp.close()
        sig_path = Path(sig_tmp.name)

        return from_path, to_path, patch_path, sig_path

    def test_verify_delta_cli_success(self):
        """Test verify-delta CLI with valid inputs."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        try:
            result = self.runner.invoke(
                cli,
                [
                    "verify-delta",
                    "--patch",
                    str(patch_path),
                    "--from-signed-bin",
                    str(from_path),
                    "--to-detached-signature",
                    str(sig_path),
                    "--to-signed-bin",
                    str(to_path),
                    "--product",
                    PRODUCT_W3A_UXC,
                    "--key-type",
                    "dev",
                    "--keys-dir",
                    str(KEYS_DIR),
                ],
            )
            self.assertEqual(0, result.exit_code, f"Command failed: {result.output}")
            self.assertIn("PASSED", result.output)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)

    def test_verify_delta_cli_success_without_to_signed_bin(self):
        """Test verify-delta CLI succeeds without optional --to-signed-bin."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        try:
            result = self.runner.invoke(
                cli,
                [
                    "verify-delta",
                    "--patch",
                    str(patch_path),
                    "--from-signed-bin",
                    str(from_path),
                    "--to-detached-signature",
                    str(sig_path),
                    "--product",
                    PRODUCT_W3A_UXC,
                    "--key-type",
                    "dev",
                    "--keys-dir",
                    str(KEYS_DIR),
                ],
            )
            self.assertEqual(0, result.exit_code, f"Command failed: {result.output}")
            self.assertIn("PASSED", result.output)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)

    def test_verify_delta_cli_invalid_patch_signature(self):
        """Test verify-delta CLI fails with invalid patch signature."""
        from_path, to_path, patch_path, sig_path = self._create_delta_test_files()

        # Create invalid patch
        patch_tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".signed.patch")
        patch_tmp.write(b"invalid patch content")
        patch_tmp.write(bytes(64))
        patch_tmp.close()
        bad_patch_path = Path(patch_tmp.name)

        try:
            result = self.runner.invoke(
                cli,
                [
                    "verify-delta",
                    "--patch",
                    str(bad_patch_path),
                    "--from-signed-bin",
                    str(from_path),
                    "--to-detached-signature",
                    str(sig_path),
                    "--product",
                    PRODUCT_W3A_UXC,
                    "--key-type",
                    "dev",
                    "--keys-dir",
                    str(KEYS_DIR),
                ],
            )
            self.assertNotEqual(0, result.exit_code)
            self.assertIn("FAILED", result.output)
        finally:
            from_path.unlink(missing_ok=True)
            to_path.unlink(missing_ok=True)
            patch_path.unlink(missing_ok=True)
            sig_path.unlink(missing_ok=True)
            bad_patch_path.unlink(missing_ok=True)


class TestPatchSigningKeys(unittest.TestCase):
    """Test cases for PatchSigningKeys class."""

    def test_uxc_patch_keys_path(self):
        """Test patch signing keys path for w3a-uxc (uses w3a-core keys)."""
        keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")
        self.assertIn("w3a-core", keys.public_key_path)
        self.assertIn("patch-signing-key", keys.public_key_path)
        self.assertTrue(Path(keys.public_key_path).exists())
        self.assertIsNone(keys.cert_path)

    def test_uxc_patch_keys_prod_path(self):
        """Test patch signing keys path for w3a-uxc prod."""
        keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "prod")
        self.assertIn("w3a-core-prod", keys.public_key_path)
        self.assertIn("patch-signing-key-prod", keys.public_key_path)
        self.assertTrue(Path(keys.public_key_path).exists())

    def test_w3a_core_patch_keys_path(self):
        """Test patch signing keys path for w3a-core."""
        keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev")
        self.assertIn("w3a-core", keys.public_key_path)
        self.assertIn("patch-signing-key", keys.public_key_path)
        self.assertTrue(Path(keys.public_key_path).exists())
        self.assertIsNone(keys.cert_path)

    def test_w1a_patch_keys_path(self):
        """Test patch signing keys path for w1a (uses w1a-specific keys)."""
        keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W1A, "dev")
        self.assertIn("w1a", keys.public_key_path)
        self.assertIn("patch-signing-key", keys.public_key_path)
        self.assertTrue(Path(keys.public_key_path).exists())
        self.assertIsNone(keys.cert_path)

    def test_invalid_key_directory(self):
        """Test PatchSigningKeys fails with invalid directory."""
        with self.assertRaises(AssertionError):
            PatchSigningKeys("/nonexistent/path", PRODUCT_W3A_UXC, "dev")


class TestMultiProductPatchVerification(unittest.TestCase):
    """Test patch verification across different products."""

    def _create_signed_patch(self, content: bytes, product: str) -> Path:
        """Helper to create a signed patch for a specific product."""
        patch_keys = PatchSigningKeys(KEYS_DIR, product, "dev")
        key_manager = LocalKeyManager(patch_keys)

        digest = SHA256.new(content)
        signature = key_manager.generate_signature(digest)

        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".patch")
        tmp.write(content)
        tmp.write(signature)
        tmp.close()
        return Path(tmp.name)

    def test_verify_patch_w3a_core(self):
        """Test patch verification with w3a-core keys."""
        patch_content = b"Test patch content for w3a-core"
        patch_path = self._create_signed_patch(patch_content, PRODUCT_W3A_CORE)

        patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev")
        key_manager = LocalKeyManager(patch_keys)

        try:
            success, error = verify_patch_signature(patch_path, key_manager)
            self.assertTrue(success, f"Patch verification failed for w3a-core: {error}")
        finally:
            patch_path.unlink(missing_ok=True)

    def test_verify_patch_w1a(self):
        """Test patch verification with w1a keys."""
        patch_content = b"Test patch content for w1a"
        patch_path = self._create_signed_patch(patch_content, PRODUCT_W1A)

        patch_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W1A, "dev")
        key_manager = LocalKeyManager(patch_keys)

        try:
            success, error = verify_patch_signature(patch_path, key_manager)
            self.assertTrue(success, f"Patch verification failed for w1a: {error}")
        finally:
            patch_path.unlink(missing_ok=True)

    def test_w3a_uxc_and_core_share_patch_keys(self):
        """Test that w3a-uxc and w3a-core use the same patch signing keys."""
        uxc_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev")
        core_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev")

        # Both should point to w3a-core keys
        self.assertEqual(uxc_keys.public_key_path, core_keys.public_key_path)
        self.assertEqual(uxc_keys.private_key_path, core_keys.private_key_path)

    def test_w1a_has_separate_patch_keys(self):
        """Test that w1a uses its own patch signing keys (not w3a-core)."""
        w1a_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W1A, "dev")
        core_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev")

        # W1A should have different keys than w3a-core
        self.assertNotEqual(w1a_keys.public_key_path, core_keys.public_key_path)
        self.assertIn("w1a", w1a_keys.public_key_path)

    def test_cross_product_patch_verification_fails(self):
        """Test that patch signed with one product's key fails verification with another."""
        patch_content = b"Test patch for cross-product verification"

        # Sign with w1a key
        patch_path = self._create_signed_patch(patch_content, PRODUCT_W1A)

        # Try to verify with w3a-core key - should fail
        core_keys = PatchSigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev")
        core_key_manager = LocalKeyManager(core_keys)

        try:
            success, error = verify_patch_signature(patch_path, core_key_manager)
            self.assertFalse(success, "Cross-product verification should fail")
        finally:
            patch_path.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
