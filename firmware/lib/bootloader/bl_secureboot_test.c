#include "bl_secureboot.h"
#include "bl_secureboot_impl.h"
#include "criterion_test_utils.h"
#include "ecc.h"
#include "fff.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(secure_bool_t, addr_in_flash, uintptr_t);
FAKE_VOID_FUNC(secure_glitch_random_delay);

static ApplicationCertificate_t app_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0},
  .key = {0},
  .version = 0,
  .signature = {0},
};

static ApplicationCertificate_t bl_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0},
  .key = {0},
  .version = 0,
  .signature = {0},
};

ApplicationProperties_t app_a_properties = {
  .magic = APPLICATION_PROPERTIES_MAGIC,
  .structVersion = APPLICATION_PROPERTIES_VERSION,
  .signatureType = APPLICATION_SIGNATURE_ECDSA_P256,
  .signatureLocation = 0,
  .app =
    {
      .type = APPLICATION_TYPE_MCU,
      .version = 0,
      .capabilities = 0,
      .productId = {0},
    },
  .cert = (ApplicationCertificate_t*)&app_certificate,
};

ApplicationProperties_t app_b_properties = {
  .magic = APPLICATION_PROPERTIES_MAGIC,
  .structVersion = APPLICATION_PROPERTIES_VERSION,
  .signatureType = APPLICATION_SIGNATURE_ECDSA_P256,
  .signatureLocation = 0,
  .app =
    {
      .type = APPLICATION_TYPE_MCU,
      .version = 0,
      .capabilities = 0,
      .productId = {0},
    },
  .cert = (ApplicationCertificate_t*)&app_certificate,
};

static uint8_t app_a_codesigning_signature[ECC_SIG_SIZE] = {0};
static uint8_t app_b_codesigning_signature[ECC_SIG_SIZE] = {0};

static uint8_t fw_app_a[1024] = {0};
static uint8_t fw_app_b[1024] = {0};

static boot_slot_t slot_a = {
  .props = &app_a_properties, .boot_addr = 1234, .signature_verified = SECURE_TRUE};
static boot_slot_t slot_b = {
  .props = &app_b_properties, .boot_addr = 5678, .signature_verified = SECURE_TRUE};
static boot_slot_t* selected_slot = NULL;

typedef enum {
  SIG_VALID = 42,
  SIG_INVALID = 43,
} sig_status_t;

#define MOCK_SIGN(sig)        (sig[0] = SIG_VALID)
#define MOCK_CORRUPT_SIG(sig) (sig[0] = SIG_INVALID)

// Mock verification function.
// We could use fff's return sequences, but it's a bit easier to reason about the
// unit tests this way.
secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, const uint8_t* hash, uint32_t hash_size,
                                     const uint8_t signature[ECC_SIG_SIZE]) {
  return (signature[0] == SIG_VALID) ? SECURE_TRUE : SECURE_FALSE;
}

static void mock_sign_certs(void) {
  MOCK_SIGN(bl_certificate.signature);
  MOCK_SIGN(app_certificate.signature);
}

static void mock_corrupt_cert_signatures(void) {
  MOCK_CORRUPT_SIG(bl_certificate.signature);
  MOCK_CORRUPT_SIG(app_certificate.signature);
}

static void mock_sign_firmware(void) {
  MOCK_SIGN(app_a_codesigning_signature);
  MOCK_SIGN(app_b_codesigning_signature);
}

static void mock_corrupt_firmware_signatures(void) {
  MOCK_CORRUPT_SIG(app_a_codesigning_signature);
  MOCK_CORRUPT_SIG(app_b_codesigning_signature);
}

static void setup_valid_signatures(void) {
  mock_sign_certs();
  mock_sign_firmware();
}

Test(bootloader, verify_app_cert, .init = setup_valid_signatures) {
  cr_assert(bl_verify_app_certificate(&app_certificate, &bl_certificate) == SECURE_TRUE);
}

Test(bootloader, fail_verify_app_cert, .init = mock_corrupt_cert_signatures) {
  cr_assert(bl_verify_app_certificate(&app_certificate, &bl_certificate) == SECURE_FALSE);
}

Test(bootloader, verify_app, .init = setup_valid_signatures) {
  cr_assert(bl_verify_application(&app_certificate, fw_app_a, sizeof(fw_app_a),
                                  app_a_codesigning_signature) == SECURE_TRUE);

  cr_assert(bl_verify_application(&app_certificate, fw_app_b, sizeof(fw_app_b),
                                  app_b_codesigning_signature) == SECURE_TRUE);
}

Test(bootloader, fail_verify_app, .init = mock_corrupt_firmware_signatures) {
  cr_assert(bl_verify_application(&app_certificate, fw_app_a, sizeof(fw_app_a),
                                  app_a_codesigning_signature) == SECURE_FALSE);

  cr_assert(bl_verify_application(&app_certificate, fw_app_b, sizeof(fw_app_b),
                                  app_b_codesigning_signature) == SECURE_FALSE);
}

Test(bootloader, verify_app_slots, .init = setup_valid_signatures) {
  secure_bool_t retvals[4] = {SECURE_TRUE, SECURE_TRUE, SECURE_TRUE, SECURE_TRUE};
  SET_RETURN_SEQ(addr_in_flash, retvals, sizeof(retvals));

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_TRUE);

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_b_properties, fw_app_b, sizeof(fw_app_b),
                               app_b_codesigning_signature) == SECURE_TRUE);
}

Test(bootloader, invalid_flash_addr, .init = setup_valid_signatures) {
  secure_bool_t retvals[4] = {SECURE_TRUE, SECURE_FALSE, SECURE_FALSE, SECURE_TRUE};
  SET_RETURN_SEQ(addr_in_flash, retvals, sizeof(retvals));

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_FALSE);

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_b_properties, fw_app_b, sizeof(fw_app_b),
                               app_b_codesigning_signature) == SECURE_FALSE);
}

Test(bootloader, select_slot_a) {
  app_a_properties.app.version = 1;
  app_b_properties.app.version = 0;

  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_TRUE);
  cr_assert(selected_slot == &slot_a);
}

Test(bootloader, select_slot_b) {
  app_a_properties.app.version = 0;
  app_b_properties.app.version = 1;

  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_TRUE);
  cr_assert(selected_slot == &slot_b);
}

Test(bootloader, select_slot_a_when_versions_match) {
  app_a_properties.app.version = 1;
  app_b_properties.app.version = 1;

  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_TRUE);
  cr_assert(selected_slot == &slot_a);
}

Test(bootloader, select_slot_a_when_b_invalid) {
  slot_b.signature_verified = SECURE_FALSE;
  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_TRUE);
  cr_assert(selected_slot == &slot_a);
}

Test(bootloader, select_no_slot_when_both_invalid) {
  slot_a.signature_verified = SECURE_FALSE;
  slot_b.signature_verified = SECURE_FALSE;
  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_FALSE);
}
