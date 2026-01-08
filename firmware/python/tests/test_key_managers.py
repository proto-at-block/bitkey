import unittest
from pathlib import Path

from bitkey.fwa.bitkey_fwa.constants import PRODUCT_W1A, PRODUCT_W3A_CORE, PRODUCT_W3A_UXC
from bitkey.key_manager import LocalKeyManager, PicocertKeyManager, SigningKeys
from Crypto.Hash import SHA256

KEYS_DIR = Path(__file__).parent.parent.parent / "config" / "keys"


class TestLocalKeyManager(unittest.TestCase):
    def setUp(self):
        self.signing_keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev", "app")
        self.key_manager = LocalKeyManager(self.signing_keys)
        self.test_data = b"This is test firmware data for signing"
        self.test_digest = SHA256.new(self.test_data)

    def test_verify_valid_signature(self):
        signature = self.key_manager.generate_signature(self.test_digest)
        try:
            self.key_manager.verify_signature(self.test_digest, signature)
        except ValueError:
            self.fail("Valid signature verification failed")

    def test_verify_invalid_signature(self):
        invalid_signature = bytes(64)
        with self.assertRaises(ValueError):
            self.key_manager.verify_signature(self.test_digest, invalid_signature)

    def test_deterministic_signatures(self):
        signature1 = self.key_manager.generate_signature(self.test_digest)
        signature2 = self.key_manager.generate_signature(self.test_digest)
        self.assertEqual(signature1, signature2)

    def test_get_signing_cert_path(self):
        cert_path = self.key_manager.get_signing_cert_path()
        self.assertIsInstance(cert_path, str)
        self.assertTrue(Path(cert_path).exists())
        self.assertTrue(cert_path.endswith(".bin"))


class TestPicocertKeyManager(unittest.TestCase):
    def setUp(self):
        self.signing_keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev", "app")
        self.key_manager = PicocertKeyManager(self.signing_keys)
        self.test_data = b"This is test firmware data for STM32U5 signing"
        self.test_digest = SHA256.new(self.test_data)

    def test_verify_valid_signature(self):
        signature = self.key_manager.generate_signature(self.test_digest)
        try:
            self.key_manager.verify_signature(self.test_digest, signature)
        except ValueError:
            self.fail("Valid signature verification failed")

    def test_verify_invalid_signature(self):
        invalid_signature = bytes(64)
        with self.assertRaises(ValueError):
            self.key_manager.verify_signature(self.test_digest, invalid_signature)

    def test_verify_wrong_digest(self):
        signature = self.key_manager.generate_signature(self.test_digest)
        wrong_data = b"Different STM32U5 firmware data"
        wrong_digest = SHA256.new(wrong_data)
        with self.assertRaises(ValueError):
            self.key_manager.verify_signature(wrong_digest, signature)

    def test_deterministic_signatures(self):
        signature1 = self.key_manager.generate_signature(self.test_digest)
        signature2 = self.key_manager.generate_signature(self.test_digest)
        self.assertEqual(signature1, signature2)

    def test_get_signing_cert_path(self):
        cert_path = self.key_manager.get_signing_cert_path()
        self.assertIsInstance(cert_path, str)
        self.assertTrue(Path(cert_path).exists())
        self.assertTrue(cert_path.endswith(".pct"))

    def test_picocert_contains_valid_public_key(self):
        signature = self.key_manager.generate_signature(self.test_digest)
        self.key_manager.verify_signature(self.test_digest, signature)


class TestSigningKeys(unittest.TestCase):
    def test_efr32_key_paths(self):
        keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_CORE, "dev", "app")
        self.assertTrue(keys.cert_path.endswith(".bin"))
        self.assertTrue(keys.public_key_path.endswith(".pub.pem"))
        self.assertTrue(keys.private_key_path.endswith(".priv.pem"))

        self.assertTrue(Path(keys.cert_path).exists())
        self.assertTrue(Path(keys.public_key_path).exists())
        self.assertTrue(Path(keys.private_key_path).exists())

    def test_stm32u5_key_paths(self):
        keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "dev", "app")
        self.assertTrue(keys.cert_path.endswith(".pct"))
        self.assertTrue(keys.public_key_path.endswith(".pub.pem"))
        self.assertTrue(keys.private_key_path.endswith(".priv.pem"))
        self.assertTrue(Path(keys.cert_path).exists())
        self.assertTrue(Path(keys.public_key_path).exists())
        self.assertTrue(Path(keys.private_key_path).exists())

    def test_invalid_key_directory(self):
        with self.assertRaises(AssertionError):
            SigningKeys("/nonexistent/path", PRODUCT_W3A_UXC, "dev", "app")

    def test_efr32_prod_keys_have_production_suffix(self):
        """Test that EFR32 platforms (W1A, W3A-CORE) use -production suffix for prod keys."""
        for product in [PRODUCT_W1A, PRODUCT_W3A_CORE]:
            with self.subTest(product=product):
                keys = SigningKeys(KEYS_DIR, product, "prod", "app")
                self.assertIn("-production", keys.public_key_path)
                self.assertIn("-production", keys.cert_path)

    def test_stm32u5_prod_keys_no_production_suffix(self):
        """Test that STM32U5 platform (W3A-UXC) does NOT use -production suffix for prod keys."""
        keys = SigningKeys(KEYS_DIR, PRODUCT_W3A_UXC, "prod", "app")
        self.assertNotIn("-production", keys.public_key_path)
        self.assertNotIn("-production", keys.cert_path)


if __name__ == "__main__":
    unittest.main()
