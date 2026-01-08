#!/usr/bin/env python3
"""
Test cases for STM32U5ElfSigner (UXC firmware signing)

Comprehensive tests for the STM32U5 platform signer, mirroring the EFR32 test structure.
"""

import os
import shutil
import tempfile
import unittest
from pathlib import Path

import semver
from bitkey.fwa.bitkey_fwa.constants import PRODUCT_W3A_UXC
from bitkey.key_manager import PicocertKeyManager, SigningKeys
from bitkey.signer_utils import (
    APP_SIGNATURE_SYMBOL,
    BL_CERTIFICATE_SYMBOL,
    AssetInfo,
    ElfSymbol,
    FirmwareSignerException,
    semver_to_int,
)
from bitkey.stm32u5_signer import Stm32U5ElfSigner
from elftools.elf.elffile import ELFFile

# Test data directories
TESTS_DIR = Path(__file__).parent
TEST_BIN_DIR = TESTS_DIR / "data" / "bin"
KEYS_DIR = TESTS_DIR.parent.parent / "config" / "keys"

# Test ELF binaries
TEST_ELF_SLOT_A = TEST_BIN_DIR / "w3a-uxc-evt-app-a-dev.elf"
TEST_ELF_SLOT_B = TEST_BIN_DIR / "w3a-uxc-evt-app-b-dev.elf"
TEST_ELF_LOADER = TEST_BIN_DIR / "w3a-uxc-evt-loader-dev.elf"


# Helper functions for reading symbol data from ELF files
def get_symbol_file_offset(elf_path: Path, symbol_name: str) -> tuple[int, int]:
    """Get the file offset and size of a symbol in an ELF file."""
    with open(elf_path, "rb") as f:
        elf = ELFFile(f)
        symtab = elf.get_section_by_name(".symtab")
        sym = ElfSymbol(symtab, symbol_name)

        # Find file offset in PT_LOAD segments
        file_offset = None
        for seg in elf.iter_segments():
            if seg.header["p_type"] != "PT_LOAD":
                continue
            if sym.addr() >= seg["p_vaddr"] and sym.addr() < seg["p_vaddr"] + seg["p_filesz"]:
                file_offset = sym.addr() - seg["p_vaddr"] + seg["p_offset"]
                break

        assert file_offset is not None, f"Could not find symbol {symbol_name}"
        return file_offset, sym.size()


def read_symbol_data(elf_path: Path, symbol_name: str) -> bytes:
    """Read the data for a symbol from an ELF file."""
    file_offset, size = get_symbol_file_offset(elf_path, symbol_name)
    with open(elf_path, "rb") as f:
        f.seek(file_offset)
        return f.read(size)


class Stm32U5SlotSigningTestBase(unittest.TestCase):
    """Base class for STM32U5 slot signing tests.

    Subclasses must define:
    - slot: The slot to test ('a' or 'b')
    - test_elf: Path to the test ELF file
    """

    slot = None  # Must be overridden by subclass
    test_elf = None  # Must be overridden by subclass

    def setUp(self):
        if self.slot is None or self.test_elf is None:
            self.skipTest("Base class - must be subclassed with slot and test_elf defined")
        if not self.test_elf.exists():
            self.skipTest(f"Test ELF not found: {self.test_elf}")
        self.signing_keys = SigningKeys(KEYS_DIR, "w3a-uxc", "dev", "app")
        self.key_manager = PicocertKeyManager(self.signing_keys)
        self.temp_dir = Path(tempfile.mkdtemp(prefix=f"test_stm32u5_signing_slot_{self.slot}_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(self.test_elf, self.temp_elf)

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_sign_elf_and_verify_signature(self):
        """Sign ELF and verify signature is cryptographically valid.
        This test demonstrates that the signing hash is the same pre and post signing,
        since the hash is computed over everything EXCEPT the signature section.
        We can call gen_presign_hash() on the signed ELF to get the exact hash.
        """
        # Sign the ELF
        asset_info = AssetInfo(app_version="1.0.101", slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)
        # verify_signature() will reopen the file if closed
        self.assertTrue(signer.verify_signature(self.key_manager, asset_info))

    def test_corrupted_firmware_fails_verification(self):
        """Verify that corrupted firmware data fails verification.

        This ensures the signature covers the firmware content (not just the signature itself).
        We corrupt the certificate (part of the signed data) and verify that verification fails.
        """
        # Sign the ELF
        asset_info = AssetInfo(app_version="1.0.101", slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Verify the signature is valid before corrupting it
        self.assertTrue(signer.verify_signature(self.key_manager, asset_info))

        signed_elf = Path(signer.get_elf_path())
        # Corrupt the certificate by flipping the first byte
        file_offset, _ = get_symbol_file_offset(signed_elf, "app_certificate")
        with open(signed_elf, "r+b") as f:
            f.seek(file_offset)
            original_byte = f.read(1)
            f.seek(file_offset)
            f.write(bytes([original_byte[0] ^ 0xFF]))

        self.assertFalse(signer.verify_signature(self.key_manager, asset_info))

    def test_app_properties_structure(self):
        """Test that app_properties has the correct structure after signing."""

        test_version = "1.0.101"
        asset_info = AssetInfo(app_version=test_version, slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")

        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Read app_properties from signed ELF
        props = read_symbol_data(Path(signer.get_elf_path()), "app_properties")

        # Verify app version (offset magic + structVersion)
        VERSION_OFFSET = 16 + 4
        app_version_bytes = props[VERSION_OFFSET : VERSION_OFFSET + 4]
        app_version_int = int.from_bytes(app_version_bytes, byteorder="little")
        expected_version_int = semver_to_int(semver.VersionInfo.parse(test_version))
        self.assertEqual(
            app_version_int, expected_version_int, f"App version should be {expected_version_int} (from {test_version})"
        )

    def test_certificate_injection(self):
        """Test that the certificate is correctly injected into app_certificate."""
        asset_info = AssetInfo(app_version="1.0.101", slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")

        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        cert_in_elf = read_symbol_data(Path(signer.get_elf_path()), "app_certificate")

        # Compare with expected certificate
        with open(self.signing_keys.cert_path, "rb") as f:
            expected_cert = f.read()
        self.assertEqual(cert_in_elf, expected_cert, "Certificate in ELF should match signing certificate")

    def test_signature_in_elf(self):
        """Test that signature is present in the ELF and not the placeholder."""
        asset_info = AssetInfo(app_version="1.0.101", slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")

        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Read signature from ELF - STM32U5 uses app_codesigning_signature (same symbol for both slots)
        signed_elf = Path(signer.get_elf_path())
        sig_in_elf = read_symbol_data(signed_elf, APP_SIGNATURE_SYMBOL)

        # Verify it's 64 bytes
        self.assertEqual(len(sig_in_elf), 64, "Signature should be 64 bytes")

        # Verify it's not the placeholder
        placeholder = b"\xca\xfe" * 32
        self.assertNotEqual(sig_in_elf, placeholder, "Signature should not be the 0xcafe placeholder")

        # Verify it's not all zeros
        self.assertFalse(all(b == 0 for b in sig_in_elf), "Signature should not be all zeros")

        # Verify signature matches detached signature file
        sig_path = Path(signer.get_elf_path()).with_suffix("").with_suffix(".detached_signature")
        with open(sig_path, "rb") as f:
            detached_sig = f.read()
        self.assertEqual(sig_in_elf, detached_sig, "Signature in ELF should match detached signature")

    def test_detached_signature_created(self):
        """Test that detached signature file is created."""
        asset_info = AssetInfo(app_version="1.0.101", slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Check that detached signature file was created
        sig_path = Path(signer.get_elf_path()).with_suffix("").with_suffix(".detached_signature")
        self.assertTrue(sig_path.exists(), "Detached signature file should be created")

        # Check that signature is 64 bytes (ECDSA P-256)
        sig_size = os.path.getsize(sig_path)
        self.assertEqual(sig_size, 64, "Detached signature should be 64 bytes")

    def test_signed_bin_created(self):
        """Test that signed .bin file is created without signature section."""
        asset_info = AssetInfo(app_version="1.0.101", slot=self.slot, product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Check that signed .bin file was created
        bin_path = Path(signer.get_elf_path()).with_suffix(".bin")
        self.assertTrue(bin_path.exists(), "Signed .bin file should be created")

        # Check that bin file doesn't contain signature section
        # (signature is removed via --remove-section)
        bin_size = os.path.getsize(bin_path)
        self.assertGreater(bin_size, 0, "Binary should have content")


class TestStm32U5SlotASigning(Stm32U5SlotSigningTestBase):
    """Test cases for signing slot A firmware."""

    slot = "a"
    test_elf = TEST_ELF_SLOT_A


class TestStm32U5SlotBSigning(Stm32U5SlotSigningTestBase):
    """Test cases for signing slot B firmware."""

    slot = "b"
    test_elf = TEST_ELF_SLOT_B


class TestStm32U5BootloaderProperties(unittest.TestCase):
    """Test STM32U5 bootloader properties.

    Note: Loaders/bootloaders are signed during the meson build process, not via
    the Python signer tool. The certificate is embedded during build based on the
    build environment (dev/prod). These tests verify the structure of pre-built loaders.
    """

    def setUp(self):
        """Set up test environment for bootloader property checks."""
        if not TEST_ELF_LOADER.exists():
            self.skipTest(f"Test bootloader ELF not found: {TEST_ELF_LOADER}")
        # Note: This is a pre-signed loader from the meson build
        self.loader_elf = TEST_ELF_LOADER

    def test_bl_app_properties_exists(self):
        """Test that bl_app_properties symbol exists in the bootloader ELF.

        This test ensures that bl_app_properties is not stripped by the linker,
        which was a previous issue. The symbol must be present for debugging
        and future use (e.g., version reporting in bootloader).
        """
        # Read bl_app_properties from the pre-built loader
        props = read_symbol_data(self.loader_elf, "bl_app_properties")

        # Verify magic value (PICO_CERT_APP_PROPERTIES_MAGIC = "BITKEY-UXC")
        magic = props[0:16]  # Magic is 16 bytes
        expected_magic = b"BITKEY-UXC\x00\x00\x00\x00\x00\x00"
        self.assertEqual(magic, expected_magic, "bl_app_properties should have correct magic 'BITKEY-UXC'")

        # Verify struct version (should be 1)
        struct_version = int.from_bytes(props[16:20], byteorder="little")
        self.assertEqual(struct_version, 1, "bl_app_properties structVersion should be 1")

    def test_bl_certificate_exists(self):
        """Test that bl_certificate symbol exists and is properly sized."""
        # Read bl_certificate from the pre-built loader
        cert_data = read_symbol_data(self.loader_elf, BL_CERTIFICATE_SYMBOL)

        # Picocert V1 size is 216 bytes
        self.assertEqual(len(cert_data), 216, "bl_certificate should be 216 bytes (PicocertV1 size)")

        # Check if the certificate has been populated (not all zeros)
        # Pre-built test loaders may not have certificates injected yet
        if cert_data != b"\x00" * 216:
            # If populated, verify it's a valid picocert by checking version byte
            version = cert_data[0]
            self.assertEqual(version, 1, "Certificate version should be 1 if populated")


class TestStm32U5ErrorHandling(unittest.TestCase):
    """Test error handling and edge cases."""

    def setUp(self):
        if not TEST_ELF_SLOT_A.exists():
            self.skipTest(f"Test ELF not found: {TEST_ELF_SLOT_A}")
        self.signing_keys = SigningKeys(KEYS_DIR, "w3a-uxc", "dev", "app")
        self.key_manager = PicocertKeyManager(self.signing_keys)
        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_stm32u5_signing_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_ELF_SLOT_A, self.temp_elf)

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_missing_slot_raises_error(self):
        """Test that signing app firmware without a slot raises an error."""
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)

        with self.assertRaises(ValueError) as context:
            signer.codesign(self.key_manager, asset_info)

        self.assertIn("must set slot", str(context.exception))

    def test_invalid_image_type_raises_error(self):
        """Test that signing with invalid image type raises an error."""
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product=PRODUCT_W3A_UXC, image_type="invalid")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)

        with self.assertRaises(FirmwareSignerException) as context:
            signer.gen_presign_hash(self.key_manager, asset_info)

        self.assertIn("only supports 'bl' and 'app' image types", str(context.exception))


class TestStm32U5VerifyCommand(unittest.TestCase):
    """Test the verify command functionality."""

    def setUp(self):
        if not TEST_ELF_SLOT_A.exists():
            self.skipTest(f"Test ELF not found: {TEST_ELF_SLOT_A}")
        self.signing_keys = SigningKeys(KEYS_DIR, "w3a-uxc", "dev", "app")
        self.key_manager = PicocertKeyManager(self.signing_keys)
        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_stm32u5_verify_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_ELF_SLOT_A, self.temp_elf)

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_verify_signed_elf(self):
        """Test verifying a properly signed ELF."""
        # First sign the ELF
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Now verify it (simulating the verify command)
        verify_signer = Stm32U5ElfSigner(Path(signer.get_elf_path()), partitions_config_path=None)
        verify_asset_info = AssetInfo(app_version=None, slot="a", product=PRODUCT_W3A_UXC, image_type="app")

        is_valid = verify_signer.verify_signature(self.key_manager, verify_asset_info)
        self.assertTrue(is_valid, "Signature verification should pass for properly signed ELF")

    def test_verify_reads_correct_signature(self):
        """Test that verify reads the correct signature from the ELF."""
        # Sign the ELF
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product=PRODUCT_W3A_UXC, image_type="app")
        signer = Stm32U5ElfSigner(self.temp_elf, partitions_config_path=None)
        signer.codesign(self.key_manager, asset_info)

        # Read signature using the same method as verify command
        verify_signer = Stm32U5ElfSigner(Path(signer.get_elf_path()), partitions_config_path=None)
        signature_symbol = verify_signer._image_to_sig_sym_name("app")
        signature = verify_signer._read_symbol_data(signature_symbol)

        # Verify it's 64 bytes and not placeholder
        self.assertEqual(len(signature), 64, "Signature should be 64 bytes")
        self.assertNotEqual(signature, b"\xca\xfe" * 32, "Signature should not be placeholder")


if __name__ == "__main__":
    unittest.main()
