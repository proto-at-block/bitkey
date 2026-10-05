import unittest

from bitkey_fwa.constants import (
    ASSET_APP,
    ENV_MFGTEST,
    ENV_NON_MFGTEST,
    PRODUCT_W1A,
    SECURITY_DEV,
    SECURITY_PROD,
    SUFFIX_ELF,
)
from bitkey_fwa.firmware_tests.fwtest_check_keys import (
    WSM_INTEGRITY_PUBKEYS,
    KeyChecks,
)
from bitkey_fwa.fwut import FirmwareUnderTest


class StubKeyChecks(KeyChecks):
    def __init__(self, key: bytes):
        super().__init__("fwtest_verify_wsm_integrity_pubkey")
        self.key = key

    def get_elf_symbol_data(self, symbol_name: str, section_name: str) -> bytes:
        self.assertEqual(symbol_name, "WSM_INTEGRITY_PUBKEY")
        self.assertEqual(section_name, ".rodata")
        return self.key


class TestWsmIntegrityKeyCheck(unittest.TestCase):
    def setUp(self):
        FirmwareUnderTest.reset()
        FirmwareUnderTest.product = PRODUCT_W1A
        FirmwareUnderTest.asset = ASSET_APP
        FirmwareUnderTest.environment = ENV_NON_MFGTEST
        FirmwareUnderTest.suffix = SUFFIX_ELF

    def tearDown(self):
        FirmwareUnderTest.reset()

    def test_accepts_key_matching_firmware_security(self):
        for security, key in WSM_INTEGRITY_PUBKEYS.items():
            with self.subTest(security=security):
                FirmwareUnderTest.security = security
                StubKeyChecks(key).fwtest_verify_wsm_integrity_pubkey()

    def test_rejects_key_for_opposite_firmware_security(self):
        test_cases = (
            (SECURITY_DEV, WSM_INTEGRITY_PUBKEYS[SECURITY_PROD]),
            (SECURITY_PROD, WSM_INTEGRITY_PUBKEYS[SECURITY_DEV]),
        )

        for security, key in test_cases:
            with self.subTest(security=security):
                FirmwareUnderTest.security = security
                with self.assertRaisesRegex(
                    AssertionError,
                    f"Incorrect WSM integrity key for {security} firmware",
                ):
                    StubKeyChecks(key).fwtest_verify_wsm_integrity_pubkey()

    def test_skips_mfgtest_firmware(self):
        FirmwareUnderTest.environment = ENV_MFGTEST
        FirmwareUnderTest.security = SECURITY_PROD

        with self.assertRaises(unittest.SkipTest):
            StubKeyChecks(
                WSM_INTEGRITY_PUBKEYS[SECURITY_DEV]
            ).fwtest_verify_wsm_integrity_pubkey()
