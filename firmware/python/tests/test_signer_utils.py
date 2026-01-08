import tempfile
import unittest
from pathlib import Path

import semver
from bitkey.signer_utils import AssetInfo, SignatureInfo, get_latest_cert, semver_to_int


class TestSemverToInt(unittest.TestCase):
    """Test cases for semver_to_int function."""

    def test_basic_version(self):
        """Test basic semantic version conversion."""
        ver = semver.VersionInfo.parse("1.0.0")
        result = semver_to_int(ver)
        # Format: MM mm ppp -> "0100000" -> 100000
        self.assertEqual(result, 100000)

    def test_version_with_minor(self):
        """Test version with minor number."""
        ver = semver.VersionInfo.parse("1.2.0")
        result = semver_to_int(ver)
        # Format: 01 02 000 -> "0102000" -> 102000
        self.assertEqual(result, 102000)

    def test_version_with_patch(self):
        """Test version with patch number."""
        ver = semver.VersionInfo.parse("1.0.101")
        result = semver_to_int(ver)
        # Format: 01 00 101 -> "0100101" -> 100101
        self.assertEqual(result, 100101)

    def test_full_version(self):
        """Test full version with major, minor, and patch."""
        ver = semver.VersionInfo.parse("12.34.567")
        result = semver_to_int(ver)
        # Format: 12 34 567 -> "1234567" -> 1234567
        self.assertEqual(result, 1234567)

    def test_max_two_digit_major(self):
        """Test that major version uses 2 digits."""
        ver = semver.VersionInfo.parse("99.0.0")
        result = semver_to_int(ver)
        # Format: 99 00 000 -> "9900000" -> 9900000
        self.assertEqual(result, 9900000)

    def test_max_two_digit_minor(self):
        """Test that minor version uses 2 digits."""
        ver = semver.VersionInfo.parse("0.99.0")
        result = semver_to_int(ver)
        # Format: 00 99 000 -> "0099000" -> 99000
        self.assertEqual(result, 99000)

    def test_max_three_digit_patch(self):
        """Test that patch version uses 3 digits."""
        ver = semver.VersionInfo.parse("0.0.999")
        result = semver_to_int(ver)
        # Format: 00 00 999 -> "0000999" -> 999
        self.assertEqual(result, 999)

    def test_leading_zeros(self):
        """Test that single digit versions get leading zeros."""
        ver = semver.VersionInfo.parse("1.2.3")
        result = semver_to_int(ver)
        # Format: 01 02 003 -> "0102003" -> 102003
        self.assertEqual(result, 102003)

    def test_production_version(self):
        """Test a realistic production version."""
        ver = semver.VersionInfo.parse("2.15.42")
        result = semver_to_int(ver)
        # Format: 02 15 042 -> "0215042" -> 215042
        self.assertEqual(result, 215042)

    def test_zero_version(self):
        """Test zero version."""
        ver = semver.VersionInfo.parse("0.0.0")
        result = semver_to_int(ver)
        self.assertEqual(result, 0)


class TestAssetInfo(unittest.TestCase):
    """Test cases for AssetInfo class."""

    def test_initialization(self):
        """Test AssetInfo initialization."""
        asset_info = AssetInfo(app_version="1.0.101", slot="a", product="w3a-core", image_type="app")

        self.assertEqual(asset_info.app_version, "1.0.101")
        self.assertEqual(asset_info.slot, "a")
        self.assertEqual(asset_info.product, "w3a-core")
        self.assertEqual(asset_info.image_type, "app")

    def test_getters(self):
        """Test AssetInfo getter methods."""
        asset_info = AssetInfo(app_version="2.3.4", slot="b", product="w3a-uxc", image_type="bl")

        self.assertEqual(asset_info.get_app_version(), "2.3.4")
        self.assertEqual(asset_info.get_slot(), "b")
        self.assertEqual(asset_info.get_product(), "w3a-uxc")
        self.assertEqual(asset_info.get_image_type(), "bl")

    def test_none_version(self):
        """Test AssetInfo with None version."""
        asset_info = AssetInfo(app_version=None, slot="a", product="w1a", image_type="app")

        self.assertIsNone(asset_info.get_app_version())


class TestSignatureInfo(unittest.TestCase):
    """Test cases for SignatureInfo dataclass."""

    def test_initialization(self):
        """Test SignatureInfo initialization."""
        sig_info = SignatureInfo(
            slot="a", address=0x080DFFC0, signature=b"\x00" * 64, is_valid=True, verification_message="Signature valid"
        )

        self.assertEqual(sig_info.slot, "a")
        self.assertEqual(sig_info.address, 0x080DFFC0)
        self.assertEqual(sig_info.signature, b"\x00" * 64)
        self.assertTrue(sig_info.is_valid)
        self.assertEqual(sig_info.verification_message, "Signature valid")

    def test_invalid_signature(self):
        """Test SignatureInfo with invalid signature."""
        sig_info = SignatureInfo(
            slot="b",
            address=0x080FFFC0,
            signature=b"\xff" * 64,
            is_valid=False,
            verification_message="Signature cryptographically invalid",
        )

        self.assertFalse(sig_info.is_valid)
        self.assertIn("invalid", sig_info.verification_message.lower())


class TestGetLatestCert(unittest.TestCase):
    """Test cases for get_latest_cert function."""

    def setUp(self):
        """Create a temporary directory with mock certificate files."""
        self.temp_dir = Path(tempfile.mkdtemp(prefix="test_certs_"))

        # Create mock certificate files with different versions
        self.mock_certs = [
            "w3a-core-app-signing-cert-dev.1.bin",
            "w3a-core-app-signing-cert-dev.2.bin",
            "w3a-core-app-signing-cert-dev.3.bin",
            "w3a-uxc-app-signing-cert-dev.1.bin",
            "w1a-bl-signing-cert-prod.1.bin",
            "w1a-bl-signing-cert-prod.2.bin",
        ]

        for cert_name in self.mock_certs:
            (self.temp_dir / cert_name).touch()

    def tearDown(self):
        """Clean up temporary directory."""
        import shutil

        if self.temp_dir.exists():
            shutil.rmtree(self.temp_dir)

    def test_get_latest_cert_basic(self):
        """Test getting the latest certificate."""
        latest = get_latest_cert(str(self.temp_dir), "w3a-core", "app", "dev")

        self.assertEqual(latest.name, "w3a-core-app-signing-cert-dev.3.bin")

    def test_get_latest_cert_version_2(self):
        """Test getting latest cert when highest version is 2."""
        latest = get_latest_cert(str(self.temp_dir), "w1a", "bl", "prod")

        self.assertEqual(latest.name, "w1a-bl-signing-cert-prod.2.bin")

    def test_get_latest_cert_single_version(self):
        """Test getting cert when only one version exists."""
        latest = get_latest_cert(str(self.temp_dir), "w3a-uxc", "app", "dev")

        self.assertEqual(latest.name, "w3a-uxc-app-signing-cert-dev.1.bin")

    def test_get_latest_cert_with_env_type(self):
        """Test getting cert with env_type parameter."""
        # Create certs with env_type
        (self.temp_dir / "w3a-core-app-signing-cert-dev-production.1.bin").touch()
        (self.temp_dir / "w3a-core-app-signing-cert-dev-production.2.bin").touch()

        latest = get_latest_cert(str(self.temp_dir), "w3a-core", "app", "dev", env_type="production")

        self.assertEqual(latest.name, "w3a-core-app-signing-cert-dev-production.2.bin")

    def test_get_latest_cert_not_found(self):
        """Test that get_latest_cert raises ValueError when no certs found."""
        with self.assertRaises(ValueError):
            get_latest_cert(str(self.temp_dir), "nonexistent", "app", "dev")


if __name__ == "__main__":
    unittest.main()
