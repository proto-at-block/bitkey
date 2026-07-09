import os
import unittest

import semver
from bitkey_fwa.constants import (
    ASSET_APP,
    ENV_MFGTEST,
    PRODUCT_W3A_CORE,
    PRODUCT_W3A_UXC,
    SECURITY_PROD,
    SUFFIX_ELF,
)
from bitkey_fwa.firmware_tests.fwtest_check_versions import VersionChecks
from bitkey_fwa.fwut import FirmwareUnderTest


class StubVersionChecks(VersionChecks):
    def __init__(self, version: str = "1.2.5", per_device_signed: bool = False):
        super().__init__("fwtest_mfgtest_version_check")
        self.version = semver.VersionInfo.parse(version)
        self.per_device_signed = per_device_signed

    def _get_and_validate_version(self, firmware_type: str) -> semver.VersionInfo:
        return self.version

    def _is_per_device_signed_application(self) -> bool:
        return self.per_device_signed


class StubProductIdVersionChecks(VersionChecks):
    def __init__(self, product_id: bytes):
        super().__init__("fwtest_mfgtest_version_check")
        self.product_id = product_id

    def _get_app_properties_product_id(self):
        return self.product_id


class TestMfgtestVersionCheck(unittest.TestCase):
    def setUp(self):
        FirmwareUnderTest.reset()
        FirmwareUnderTest.product = PRODUCT_W3A_CORE
        FirmwareUnderTest.asset = ASSET_APP
        FirmwareUnderTest.environment = ENV_MFGTEST
        FirmwareUnderTest.security = SECURITY_PROD
        FirmwareUnderTest.suffix = SUFFIX_ELF
        FirmwareUnderTest.signed = True
        self.old_mfgtest_mode = os.environ.pop(
            "FWA_MFGTEST_VERSION_CHECK",
            None,
        )

    def tearDown(self):
        FirmwareUnderTest.reset()
        if self.old_mfgtest_mode is not None:
            os.environ["FWA_MFGTEST_VERSION_CHECK"] = self.old_mfgtest_mode

    def test_regular_mfgtest_uses_mfgtest_max_version(self):
        check = StubVersionChecks(version="1.2.5", per_device_signed=False)

        with self.assertRaises(AssertionError):
            check.fwtest_mfgtest_version_check()

    def test_per_device_signed_mfgtest_uses_app_max_version(self):
        FirmwareUnderTest.product = PRODUCT_W3A_UXC
        check = StubVersionChecks(version="1.2.5", per_device_signed=True)

        check.fwtest_mfgtest_version_check()

    def test_per_device_signed_mfgtest_still_enforces_app_max_version(self):
        FirmwareUnderTest.product = PRODUCT_W3A_UXC
        check = StubVersionChecks(version="1.3.1", per_device_signed=True)

        with self.assertRaises(AssertionError):
            check.fwtest_mfgtest_version_check()


class TestPerDeviceSignedDetection(unittest.TestCase):
    def setUp(self):
        FirmwareUnderTest.reset()
        FirmwareUnderTest.product = PRODUCT_W3A_CORE
        FirmwareUnderTest.asset = ASSET_APP
        FirmwareUnderTest.signed = True

    def tearDown(self):
        FirmwareUnderTest.reset()

    def test_zero_product_id_is_not_per_device_signed(self):
        FirmwareUnderTest.product = PRODUCT_W3A_UXC
        check = StubProductIdVersionChecks(
            b"\x00" * VersionChecks.PRODUCT_ID_SIZE
        )

        self.assertFalse(check._is_per_device_signed_application())

    def test_efr32_chip_id_product_id_is_not_enabled_for_per_device_signed(self):
        chip_id = bytes.fromhex("0011223344556677")
        product_id = chip_id + (b"\x00" * 8)
        check = StubProductIdVersionChecks(product_id)

        self.assertFalse(check._is_per_device_signed_application())

    def test_stm32u5_chip_id_product_id_is_per_device_signed(self):
        FirmwareUnderTest.product = PRODUCT_W3A_UXC
        chip_id = bytes.fromhex("00112233445566778899aabb")
        product_id = chip_id + (b"\x00" * 4)
        check = StubProductIdVersionChecks(product_id)

        self.assertTrue(check._is_per_device_signed_application())

    def test_nonzero_product_id_padding_fails(self):
        FirmwareUnderTest.product = PRODUCT_W3A_UXC
        chip_id = bytes.fromhex("00112233445566778899aabb")
        product_id = chip_id + b"\x00" * 3 + b"\x01"
        check = StubProductIdVersionChecks(product_id)

        with self.assertRaises(AssertionError):
            check._is_per_device_signed_application()

    def test_unsigned_product_id_is_not_per_device_signed(self):
        FirmwareUnderTest.product = PRODUCT_W3A_UXC
        FirmwareUnderTest.signed = False
        chip_id = bytes.fromhex("00112233445566778899aabb")
        product_id = chip_id + (b"\x00" * 4)
        check = StubProductIdVersionChecks(product_id)

        self.assertFalse(check._is_per_device_signed_application())
