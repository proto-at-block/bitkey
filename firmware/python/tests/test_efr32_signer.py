import os
import shutil
import tempfile
import unittest
from pathlib import Path

import semver
from bitkey.firmware_signer import Efr32ElfSigner, EFR32_PROPERTIES_MAGIC
from bitkey.key_manager import LocalKeyManager, SigningKeys
from bitkey.signer_utils import AssetInfo, ElfSymbol, semver_to_int
from elftools.elf.elffile import ELFFile

# Test data directories
TESTS_DIR = Path(__file__).parent
TEST_BIN_DIR = TESTS_DIR / "data" / "bin"
KEYS_DIR = TESTS_DIR.parent.parent / "config" / "keys"
PARTITIONS_DIR = TESTS_DIR.parent.parent / "config" / "partitions"

# Test ELF binary
TEST_ELF = TEST_BIN_DIR / "w3a-core-evt-app-a-dev.elf"
TEST_BL_ELF = TEST_BIN_DIR / "w3a-core-evt-loader-dev.elf"
PARTITIONS_CONFIG = PARTITIONS_DIR / "w3a-core" / "partitions.yml"


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


class TestEfr32ElfSigning(unittest.TestCase):
    def setUp(self):
        if not TEST_ELF.exists():
            self.skipTest(f"Test ELF not found: {TEST_ELF}")
        if not PARTITIONS_CONFIG.exists():
            self.skipTest(f"Partitions config not found: {PARTITIONS_CONFIG}")
        self.signing_keys = SigningKeys(KEYS_DIR, "w3a-core", "dev", "app")
        self.key_manager = LocalKeyManager(self.signing_keys)
        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_efr32_signing_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_ELF, self.temp_elf)

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
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product="w3a-core-evt", image_type="app")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)
        # verify_signature() will reopen the file if closed
        self.assertTrue(signer.verify_signature(self.key_manager, asset_info))

    def test_corrupted_firmware_fails_verification(self):
        """Verify that corrupted firmware data fails verification.

        This ensures the signature covers the firmware content (not just the signature itself).
        We corrupt the certificate (part of the signed data) and verify that verification fails.
        """
        # Sign the ELF
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product="w3a-core-evt", image_type="app")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
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

    def test_sl_app_properties_structure(self):
        """Test that sl_app_properties has the correct structure after signing."""

        test_version = "1.0.101"
        asset_info = AssetInfo(app_version=test_version, slot="a", product="w3a-core-evt", image_type="app")

        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Read sl_app_properties from signed ELF
        props = read_symbol_data(Path(signer.get_elf_path()), "sl_app_properties")

        # These are from some hardcoded fields in the _set_version function
        # The app properties struct is not currently defined in the signer, so just reusing the constants.

        # Verify magic bytes (APPLICATION_PROPERTIES_MAGIC)
        self.assertEqual(props[:16], bytes(EFR32_PROPERTIES_MAGIC), "Magic bytes should match EFR32_PROPERTIES_MAGIC")

        # Verify struct version (offset 16, 4 bytes) - should be (1 << 8) + 1 = 0x0101
        struct_version = int.from_bytes(props[16:20], byteorder="little")
        self.assertEqual(struct_version, (1 << 8) + 1, "Struct version should be 1.1")

        # Verify app version (offset 32, 4 bytes)
        app_version_bytes = props[32:36]
        app_version_int = int.from_bytes(app_version_bytes, byteorder="little")
        expected_version_int = semver_to_int(semver.VersionInfo.parse(test_version))
        self.assertEqual(
            app_version_int, expected_version_int, f"App version should be {expected_version_int} (from {test_version})"
        )

    def test_certificate_injection(self):
        """Test that the certificate is correctly injected into app_certificate."""
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product="w3a-core-evt", image_type="app")

        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        cert_in_elf = read_symbol_data(Path(signer.get_elf_path()), "app_certificate")

        # Compare with expected certificate
        with open(self.signing_keys.cert_path, "rb") as f:
            expected_cert = f.read()
        self.assertEqual(cert_in_elf, expected_cert, "Certificate in ELF should match signing certificate")

    def test_signature_in_elf(self):
        """Test that signature is present in the ELF and not the placeholder."""
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product="w3a-core-evt", image_type="app")

        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Read signature from ELF
        signed_elf = Path(signer.get_elf_path())
        sig_in_elf = read_symbol_data(signed_elf, "app_codesigning_signature")

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

    def test_sysinfo_version_string(self):
        """Test that _sysinfo_version_string is set correctly for Memfault."""
        test_version = "1.0.101"
        asset_info = AssetInfo(app_version=test_version, slot="a", product="w3a-core-evt", image_type="app")

        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        version_string = read_symbol_data(Path(signer.get_elf_path()), "_sysinfo_version_string")

        # Verify version string (should be null-padded to 12 bytes)
        expected_version = test_version.encode("ascii")
        expected_padded = expected_version + bytes(12 - len(expected_version))
        self.assertEqual(
            version_string, expected_padded, f"Sysinfo version should be '{test_version}' null-padded to 12 bytes"
        )


class TestEfr32BootloaderSigning(unittest.TestCase):
    """Test cases for EFR32 bootloader signing."""

    def setUp(self):
        if not TEST_BL_ELF.exists():
            self.skipTest(f"Test bootloader ELF not found: {TEST_BL_ELF}")
        if not PARTITIONS_CONFIG.exists():
            self.skipTest(f"Partitions config not found: {PARTITIONS_CONFIG}")

        self.signing_keys = SigningKeys(KEYS_DIR, "w3a-core", "dev", "bl")
        self.key_manager = LocalKeyManager(self.signing_keys)
        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_efr32_bl_signing_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_BL_ELF, self.temp_elf)

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_sign_bootloader_and_verify_signature(self):
        """Sign bootloader ELF and verify signature is cryptographically valid."""
        # Sign the bootloader ELF (no slot for bootloader)
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)
        
        # Verify signature
        self.assertTrue(signer.verify_signature(self.key_manager, asset_info))

    def test_bootloader_certificate_injection(self):
        """Test that bootloader certificate is correctly injected."""
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)
        
        signed_elf = Path(signer.get_elf_path())
        cert_in_elf = read_symbol_data(signed_elf, "bl_certificate")

        # Compare with expected certificate
        with open(self.signing_keys.cert_path, "rb") as f:
            expected_cert = f.read()
        self.assertEqual(cert_in_elf, expected_cert, "Bootloader certificate in ELF should match signing certificate")

    def test_corrupted_bootloader_fails_verification(self):
        """Verify that corrupted bootloader firmware fails verification."""
        # Sign the bootloader
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Verify the signature is valid before corrupting it
        self.assertTrue(signer.verify_signature(self.key_manager, asset_info))

        signed_elf = Path(signer.get_elf_path())
        # Corrupt the certificate by flipping the first byte
        file_offset, _ = get_symbol_file_offset(signed_elf, "bl_certificate")
        with open(signed_elf, "r+b") as f:
            f.seek(file_offset)
            original_byte = f.read(1)
            f.seek(file_offset)
            f.write(bytes([original_byte[0] ^ 0xFF]))

        self.assertFalse(signer.verify_signature(self.key_manager, asset_info))

    def test_verify_signed_elf(self):
        """Test verifying a properly signed ELF (simulating verify command)."""
        # First sign the bootloader
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Now verify it (simulating the verify command)
        verify_signer = Efr32ElfSigner(Path(signer.get_elf_path()), str(PARTITIONS_CONFIG))
        verify_asset_info = AssetInfo(app_version=None, slot=None, product="w3a-core-evt", image_type="bl")
        
        is_valid = verify_signer.verify_signature(self.key_manager, verify_asset_info)
        self.assertTrue(is_valid, "Signature verification should pass for properly signed bootloader")

    def test_verify_reads_correct_signature(self):
        """Test that verify reads the correct signature from the ELF."""
        # Sign the bootloader
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Read signature using the same method as verify command
        verify_signer = Efr32ElfSigner(Path(signer.get_elf_path()), str(PARTITIONS_CONFIG))
        signature_symbol = verify_signer._image_to_sig_sym_name("bl")
        signature = verify_signer._read_symbol_data(signature_symbol)

        # Verify it's 64 bytes and not placeholder
        self.assertEqual(len(signature), 64, "Signature should be 64 bytes")
        self.assertNotEqual(signature, b"\xca\xfe" * 32, "Signature should not be placeholder")

    def test_bootloader_metadata_section_created(self):
        """Test that bootloader metadata section is properly handled during signing."""
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Check that detached metadata file was created
        detached_meta = Path(signer.get_elf_path()).with_suffix("").with_suffix(".detached_metadata")
        self.assertTrue(detached_meta.exists(), "Detached metadata file should be created for bootloader")

        # Check that metadata file is padded to multiple of 4 bytes
        meta_size = os.path.getsize(detached_meta)
        self.assertEqual(meta_size % 4, 0, "Metadata size should be multiple of 4 bytes")

    def test_bootloader_detached_signature_created(self):
        """Test that bootloader detached signature file is created."""
        asset_info = AssetInfo(app_version="1.0.101", slot=None, product="w3a-core-evt", image_type="bl")
        signer = Efr32ElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))
        signer.codesign(self.key_manager, asset_info)

        # Check that detached signature file was created
        sig_path = Path(signer.get_elf_path()).with_suffix("").with_suffix(".detached_signature")
        self.assertTrue(sig_path.exists(), "Detached signature file should be created")

        # Check that signature is 64 bytes (ECDSA P-256)
        sig_size = os.path.getsize(sig_path)
        self.assertEqual(sig_size, 64, "Detached signature should be 64 bytes")


if __name__ == "__main__":
    unittest.main()
