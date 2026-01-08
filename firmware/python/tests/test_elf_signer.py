import os
import shutil
import tempfile
import unittest
from pathlib import Path

from bitkey.elf_signer import ElfSigner
from bitkey.signer_utils import ElfSymbol

# Test data directories
TESTS_DIR = Path(__file__).parent
TEST_BIN_DIR = TESTS_DIR / "data" / "bin"
KEYS_DIR = TESTS_DIR.parent.parent / "config" / "keys"
PARTITIONS_CONFIG_DIR = TESTS_DIR.parent.parent / "config" / "partitions" / "w3a-core"

# Test ELF binary and partitions config
TEST_ELF = TEST_BIN_DIR / "w3a-core-evt-app-a-dev.elf"
TEST_BL_ELF = TEST_BIN_DIR / "w3a-core-evt-loader-dev.elf"
PARTITIONS_CONFIG = PARTITIONS_CONFIG_DIR / "partitions.yml"


class MockElfSigner(ElfSigner):
    """Concrete implementation of ElfSigner for testing base class functionality.

    Only implements the required abstract methods. Uses default implementations
    for verify_signature and stitch_and_finalize from the base class.
    """

    def _set_version(self, image_type: str, app_version: str):
        """Minimal implementation for testing."""
        pass

    def _set_build_id(self, slot: str):
        """Minimal implementation for testing."""
        pass


class TestElfSignerBase(unittest.TestCase):
    """Test cases for ElfSigner base class functionality."""

    def setUp(self):
        """Set up test environment."""
        if not TEST_ELF.exists():
            self.skipTest(f"Test ELF not found: {TEST_ELF}")
        if not PARTITIONS_CONFIG.exists():
            self.skipTest(f"Partitions config not found: {PARTITIONS_CONFIG}")

        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_elf_signer_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_ELF, self.temp_elf)

        self.signer = MockElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "signer") and hasattr(self.signer, "elf_file"):
            if not self.signer.elf_file.closed:
                self.signer.elf_file.close()

        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_initialization(self):
        """Test ElfSigner initialization."""
        self.assertIsNotNone(self.signer.elf)
        self.assertIsNotNone(self.signer.elf_file)
        self.assertIsNotNone(self.signer.symtab)
        self.assertFalse(self.signer.elf_file.closed)
        self.assertEqual(self.signer.partitions_config_path, str(PARTITIONS_CONFIG))

    def test_get_elf_path(self):
        """Test get_elf_path returns the correct signed ELF path."""
        elf_path = self.signer.get_elf_path()
        self.assertTrue(elf_path.endswith(".signed.elf"))
        self.assertTrue(Path(elf_path).exists())

    def test_resolve_symbol(self):
        """Test _resolve_symbol finds correct file offset for a symbol."""
        # Get a known symbol from the ELF
        sym = ElfSymbol(self.signer.symtab, "app_certificate")

        # Resolve it to a file offset
        file_offset = self.signer._resolve_symbol(sym)

        # File offset should be a valid integer
        self.assertIsInstance(file_offset, int)
        self.assertGreater(file_offset, 0)

        # Verify we can seek to this offset
        self.signer.elf_file.seek(file_offset)
        data = self.signer.elf_file.read(sym.size())
        self.assertEqual(len(data), sym.size())

    def test_read_symbol_data(self):
        """Test _read_symbol_data reads correct data from a symbol."""
        # Read a known symbol
        data = self.signer._read_symbol_data("app_certificate")

        # Should return bytes
        self.assertIsInstance(data, bytes)
        self.assertGreater(len(data), 0)

    def test_write_and_read_symbol_data(self):
        """Test _write_symbol_data and _read_symbol_data work together."""
        # Read original data
        original_data = self.signer._read_symbol_data("app_certificate")

        # Write new data (create test data of same size with FEEDFACE pattern)
        pattern = b"\xfe\xed\xfa\xce"
        test_data = pattern * (len(original_data) // len(pattern))
        self.signer._write_symbol_data("app_certificate", test_data)

        # Read it back
        read_data = self.signer._read_symbol_data("app_certificate")

        # Verify it matches what we wrote
        self.assertEqual(read_data, test_data)

        # Restore original data
        self.signer._write_symbol_data("app_certificate", original_data)
        restored_data = self.signer._read_symbol_data("app_certificate")
        self.assertEqual(restored_data, original_data)

    def test_image_to_sig_sym_name(self):
        """Test _image_to_sig_sym_name generates correct symbol names."""
        # Test for application image
        app_sym = self.signer._image_to_sig_sym_name("app")
        self.assertEqual(app_sym, "app_codesigning_signature")

        # Test for bootloader image
        bl_sym = self.signer._image_to_sig_sym_name("bl")
        self.assertEqual(bl_sym, "bl_codesigning_signature")

    def test_image_to_sig_section(self):
        """Test _image_to_sig_section generates correct section names."""
        # Test for bootloader (no slot required)
        bl_section = self.signer._image_to_sig_section("bl")
        self.assertEqual(bl_section, ".bl_codesigning_signature_section")

        # Test for application with slot A
        app_a_section = self.signer._image_to_sig_section("app", "a")
        self.assertEqual(app_a_section, ".app_a_codesigning_signature_section")

        # Test for application with slot B
        app_b_section = self.signer._image_to_sig_section("app", "b")
        self.assertEqual(app_b_section, ".app_b_codesigning_signature_section")

    def test_image_to_sig_section_requires_slot_for_app(self):
        """Test _image_to_sig_section requires slot for app images."""
        with self.assertRaises(ValueError):
            self.signer._image_to_sig_section("app")

    def test_inject_cert(self):
        """Test _inject_cert writes and verifies certificate injection."""
        # Read original certificate size to create matching test data
        original_cert = self.signer._read_symbol_data("app_certificate")
        test_cert = b"\xde\xad\xbe\xef" * (len(original_cert) // 4)

        # Inject the test certificate
        self.signer._inject_cert(test_cert, "app")

        # Verify it was written correctly
        read_cert = self.signer._read_symbol_data("app_certificate")
        self.assertEqual(read_cert, test_cert)

    def test_inject_signature(self):
        """Test _inject_signature writes and verifies signature injection."""
        # Create a 64-byte test signature with DEADBEEF pattern
        test_signature = b"\xde\xad\xbe\xef" * 16  # 4 bytes * 16 = 64 bytes

        # Inject the signature
        self.signer._inject_signature(test_signature, "app")

        # Verify it was written correctly
        read_sig = self.signer._read_symbol_data("app_codesigning_signature")
        self.assertEqual(read_sig, test_signature)

    def test_get_application_size(self):
        """Test _get_application_size reads from partitions config."""
        app_size = self.signer._get_application_size()

        # Should return a positive integer
        self.assertIsInstance(app_size, int)
        self.assertGreater(app_size, 0)

        # Should be a multiple of 1024 (since config uses KB)
        self.assertEqual(app_size % 1024, 0)

    def test_constants(self):
        """Test that ElfSigner constants are defined correctly."""
        self.assertEqual(ElfSigner.ECC_P256_SIG_SIZE, 64)
        self.assertEqual(ElfSigner.FLASH_ERASED_VALUE, 0xFF)

    def test_multiple_symbol_operations(self):
        """Test performing multiple symbol read/write operations."""
        # Read multiple symbols
        cert_data = self.signer._read_symbol_data("app_certificate")
        sig_data = self.signer._read_symbol_data("app_codesigning_signature")

        # Both should return valid data
        self.assertIsInstance(cert_data, bytes)
        self.assertIsInstance(sig_data, bytes)
        self.assertGreater(len(cert_data), 0)
        self.assertGreater(len(sig_data), 0)

        # Write to one shouldn't affect the other (using BAADC0DE pattern)
        pattern = b"\xba\xad\xc0\xde"
        new_cert = pattern * (len(cert_data) // len(pattern))
        self.signer._write_symbol_data("app_certificate", new_cert)

        # Signature should remain unchanged
        sig_data_after = self.signer._read_symbol_data("app_codesigning_signature")
        self.assertEqual(sig_data, sig_data_after)

        # Certificate should be updated
        cert_data_after = self.signer._read_symbol_data("app_certificate")
        self.assertEqual(cert_data_after, new_cert)

    def test_symbol_boundary_handling(self):
        """Test that symbol operations respect symbol boundaries."""
        # Get symbol info
        sym = ElfSymbol(self.signer.symtab, "app_certificate")
        sym_size = sym.size()

        # Read the symbol
        data = self.signer._read_symbol_data("app_certificate")

        # Data length should match symbol size exactly
        self.assertEqual(len(data), sym_size)

    def test_file_handle_state_after_operations(self):
        """Test that file handle is in correct state after operations."""
        # Perform a read operation
        self.signer._read_symbol_data("app_certificate")

        # File should still be open and seekable
        self.assertFalse(self.signer.elf_file.closed)

        # Should be able to perform another operation
        data = self.signer._read_symbol_data("app_codesigning_signature")
        self.assertIsInstance(data, bytes)

    def test_metadata_padding_calculation(self):
        """Test the _gen_padding method for bootloader metadata alignment.

        This tests the _gen_padding method that pads bootloader metadata
        to the nearest multiple of 4 bytes.
        """
        # Test cases: (input_size, expected_padding_length)
        test_cases = [
            (0, 0),  # 0 bytes -> 0 padding
            (1, 3),  # 1 byte -> 3 padding
            (2, 2),  # 2 bytes -> 2 padding
            (3, 1),  # 3 bytes -> 1 padding
            (4, 0),  # 4 bytes -> 0 padding
            (5, 3),  # 5 bytes -> 3 padding
            (7, 1),  # 7 bytes -> 1 padding
            (8, 0),  # 8 bytes -> 0 padding
            (100, 0),  # 100 bytes -> 0 padding (already multiple of 4)
            (101, 3),  # 101 bytes -> 3 padding
            (102, 2),  # 102 bytes -> 2 padding
            (103, 1),  # 103 bytes -> 1 padding
        ]

        for meta_size, expected_padding_len in test_cases:
            # Generate padding using the signer's method
            padding = self.signer._gen_padding(meta_size)

            # Verify padding length is correct
            self.assertEqual(
                len(padding),
                expected_padding_len,
                f"Size {meta_size} should need {expected_padding_len} bytes padding, got {len(padding)}",
            )

            # Verify padding bytes are all 0xFF (flash erased value)
            if expected_padding_len > 0:
                self.assertEqual(padding, b"\xff" * expected_padding_len, "Padding should be all 0xFF bytes")

            # Verify final size would be multiple of 4
            final_size = meta_size + len(padding)
            self.assertEqual(
                final_size % 4, 0, f"Size {meta_size} + padding {len(padding)} = {final_size} should be multiple of 4"
            )


class TestElfSignerBootloader(unittest.TestCase):
    """Test cases for ElfSigner base class with bootloader binaries."""

    def setUp(self):
        """Set up test environment with bootloader ELF."""
        if not TEST_BL_ELF.exists():
            self.skipTest(f"Test bootloader ELF not found: {TEST_BL_ELF}")
        if not PARTITIONS_CONFIG.exists():
            self.skipTest(f"Partitions config not found: {PARTITIONS_CONFIG}")

        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_elf_signer_bl_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_BL_ELF, self.temp_elf)

        self.signer = MockElfSigner(self.temp_elf, str(PARTITIONS_CONFIG))

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "signer") and hasattr(self.signer, "elf_file"):
            if not self.signer.elf_file.closed:
                self.signer.elf_file.close()

        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_bootloader_has_expected_symbols(self):
        """Test that bootloader ELF contains expected bootloader symbols."""
        # Bootloader should have these symbols
        expected_symbols = [
            "bl_certificate",
            "bl_codesigning_signature",
            "bl_metadata",
        ]

        for sym_name in expected_symbols:
            try:
                sym = ElfSymbol(self.signer.symtab, sym_name)
                self.assertIsNotNone(sym)
                self.assertGreater(sym.size(), 0)
            except AssertionError:
                self.fail(f"Bootloader missing expected symbol: {sym_name}")

    def test_bootloader_image_to_sig_sym_name(self):
        """Test _image_to_sig_sym_name for bootloader image type."""
        sym_name = self.signer._image_to_sig_sym_name("bl")
        self.assertEqual(sym_name, "bl_codesigning_signature")

    def test_bootloader_image_to_sig_section(self):
        """Test _image_to_sig_section for bootloader image type."""
        section_name = self.signer._image_to_sig_section("bl")
        self.assertEqual(section_name, ".bl_codesigning_signature_section")

    def test_bootloader_read_certificate(self):
        """Test reading bootloader certificate."""
        cert_data = self.signer._read_symbol_data("bl_certificate")

        self.assertIsInstance(cert_data, bytes)
        self.assertGreater(len(cert_data), 0)

    def test_bootloader_read_signature(self):
        """Test reading bootloader signature."""
        sig_data = self.signer._read_symbol_data("bl_codesigning_signature")

        self.assertIsInstance(sig_data, bytes)
        self.assertEqual(len(sig_data), 64)  # ECDSA P-256 signature

    def test_bootloader_inject_cert(self):
        """Test injecting certificate into bootloader."""
        # Read original certificate
        original_cert = self.signer._read_symbol_data("bl_certificate")

        # Create test certificate with DEADBEEF pattern
        test_cert = b"\xde\xad\xbe\xef" * (len(original_cert) // 4)

        # Inject the test certificate
        self.signer._inject_cert(test_cert, "bl")

        # Verify it was written correctly
        read_cert = self.signer._read_symbol_data("bl_certificate")
        self.assertEqual(read_cert, test_cert)

    def test_bootloader_inject_signature(self):
        """Test injecting signature into bootloader."""
        # Create a 64-byte test signature
        test_signature = b"\xde\xad\xbe\xef" * 16

        # Inject the signature
        self.signer._inject_signature(test_signature, "bl")

        # Verify it was written correctly
        read_sig = self.signer._read_symbol_data("bl_codesigning_signature")
        self.assertEqual(read_sig, test_signature)

    def test_bootloader_has_metadata_section(self):
        """Test that bootloader has metadata section."""
        # Check if .bl_metadata_section exists
        metadata_section = self.signer.elf.get_section_by_name(".bl_metadata_section")
        self.assertIsNotNone(metadata_section, "Bootloader should have .bl_metadata_section")
        self.assertGreater(metadata_section.data_size, 0)


class TestElfSignerWithoutPartitionsConfig(unittest.TestCase):
    """Test ElfSigner when partitions config is optional."""

    def setUp(self):
        """Set up test environment."""
        if not TEST_ELF.exists():
            self.skipTest(f"Test ELF not found: {TEST_ELF}")

        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_elf_signer_no_config_"))
        self.temp_elf = self.temp_dir / "test.elf"
        shutil.copy2(TEST_ELF, self.temp_elf)

        # Initialize without partitions config
        self.signer = MockElfSigner(self.temp_elf)

    def tearDown(self):
        """Clean up temp files."""
        if hasattr(self, "signer") and hasattr(self.signer, "elf_file"):
            if not self.signer.elf_file.closed:
                self.signer.elf_file.close()

        if hasattr(self, "temp_dir") and os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    def test_initialization_without_partitions_config(self):
        """Test ElfSigner can be initialized without partitions config."""
        self.assertIsNotNone(self.signer.elf)
        self.assertIsNotNone(self.signer.symtab)
        self.assertIsNone(self.signer.partitions_config_path)

    def test_symbol_operations_without_partitions_config(self):
        """Test symbol operations work without partitions config."""
        # Symbol operations should still work
        data = self.signer._read_symbol_data("app_certificate")
        self.assertIsInstance(data, bytes)
        self.assertGreater(len(data), 0)


if __name__ == "__main__":
    unittest.main()
