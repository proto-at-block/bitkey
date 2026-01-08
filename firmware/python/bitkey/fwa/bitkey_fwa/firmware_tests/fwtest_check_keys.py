import bitkey_fwa

from bitkey_fwa.fwut import FirmwareUnderTest
from bitkey_fwa.keys import get_patch_signing_key_bytes

# format is product, security, version glob
app_signing_key_fmt = '{}-app-signing-key-{}{}.*.pub.pem'
loader_signing_key_fmt = '{}-bl-signing-key-{}.*.pub.pem'
patch_signing_key_fmt = '{}-patch-signing-key-{}.*.pub.pem'
root_fw_signing_ca_key_fmt = '{}-root-firmware-signing-ca-key-{}.*.pub.pem'

ECC_SIGNATURE_SIZE = 64


class KeyChecks(bitkey_fwa.TestCase):
    """Check that various keys are present and establish a root of trust"""

    @bitkey_fwa.asset("app")
    @bitkey_fwa.product("w1a")
    def fwtest_verify_delta_patch_pubkey(self):
        """"Verify the delta patch pubkey"""

        delta_key_hex = get_patch_signing_key_bytes(patch_signing_key_fmt)
        self.assertInFirmware(delta_key_hex,
                              "Delta patch pubkey not found in firmware")

    @bitkey_fwa.asset("app")
    @bitkey_fwa.suffix("elf")
    def fwtest_verify_app_slot(self):
        """Verify that the application is signed with the application key"""

        if not FirmwareUnderTest.signed:
            raise Warning("Warning: Firmware unsigned")
            return

        slot = FirmwareUnderTest.slot

        signature = self.get_elf_section_by_name(
            f'.app_{slot}_codesigning_signature_section')
        signature_data = self.get_elf_section_data(signature)
        signature_addr = self.get_elf_section_addr(signature)

        self.assertEqual(self.get_elf_section_size(signature), ECC_SIGNATURE_SIZE,
                         f"Incorrect signature section size: {self.get_elf_section_size(signature)}")

        app_start_addr = self.get_elf_symbol_value_from_name(
            f'app_{slot}_slot_page')

        signing_data = self.get_elf_data_from_addr_range(
            app_start_addr, signature_addr)

        # assert that signing data is 632k minus sig size
        self.assertEqual(len(signing_data), 632 * 1024 - ECC_SIGNATURE_SIZE,
                         f"Signing data has incorrect size: {len(signing_data)}")

        self.verify_signature(app_signing_key_fmt,
                              signing_data, signature_data)

    @bitkey_fwa.asset("app")
    @bitkey_fwa.suffix("elf")
    def fwtest_verify_app_certificate(self):
        """Verify that the application certificate has been signed with the bootloader key"""

        if not FirmwareUnderTest.signed:
            raise Warning("Warning: Firmware unsigned")
            return
        # get the  app cert
        app_certificate = self.get_elf_symbol_data(
            "app_certificate", ".rodata")

        signing_data = app_certificate[:-ECC_SIGNATURE_SIZE]
        signature_data = app_certificate[-ECC_SIGNATURE_SIZE:]

        self.verify_signature(loader_signing_key_fmt,
                              signing_data, signature_data)

    @bitkey_fwa.asset("loader")
    @bitkey_fwa.suffix("elf")
    def fwtest_verify_loader_slot(self):
        """Verify that the bootloader is signed with the bootloader key"""

        if not FirmwareUnderTest.signed:
            raise Warning("Warning: Firmware unsigned")
            return

        # locate application and verify signature

        bl_slot_size = self.get_elf_symbol_value_from_name("bl_slot_size")
        bl_base_addr = self.get_elf_symbol_value_from_name("bl_base_addr")
        bl_signature_size = self.get_elf_symbol_value_from_name("bl_signature_size")

        self.assertEqual(bl_signature_size, ECC_SIGNATURE_SIZE,
                         f"Incorrect signature section size: {bl_signature_size}")

        bl_slot_data = self.get_elf_data_from_addr_range(
            bl_base_addr, bl_base_addr + bl_slot_size)

        signing_size = bl_slot_size - bl_signature_size

        signing_data = bl_slot_data[:signing_size]
        signature_data = bl_slot_data[signing_size:]

        # verify that signature data at signature addr matches what we just pulled
        codesigning_signature_section = self.get_elf_section_by_name(
            ".bl_codesigning_signature_section")
        signature_data_copy = self.get_elf_section_data(
            codesigning_signature_section)

        self.assertEqual(signature_data, signature_data_copy,
                         "Signature mismatch! Symbol or section address is incorrect")

        self.verify_signature(loader_signing_key_fmt,
                              signing_data, signature_data)

    @bitkey_fwa.asset("loader")
    @bitkey_fwa.suffix("elf")
    def fwtest_verify_bl_certificate(self):
        """Verify that the bootloader certificate has been signed with the root fw key"""

        if not FirmwareUnderTest.signed:
            raise Warning("Warning: Firmware unsigned")
            return

        # get the loader cert
        bl_certificate = self.get_elf_symbol_data(
            "bl_certificate", ".rodata")

        signing_data = bl_certificate[:-ECC_SIGNATURE_SIZE]
        signature_data = bl_certificate[-ECC_SIGNATURE_SIZE:]

        self.verify_signature(root_fw_signing_ca_key_fmt,
                              signing_data, signature_data)
