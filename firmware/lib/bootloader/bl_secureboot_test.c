#include "bl_secureboot.h"
#include "bl_secureboot_impl.h"
#include "criterion_test_utils.h"
#include "ecc.h"
#include "fff.h"
#include "mcu_devinfo.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(secure_bool_t, addrs_in_same_slot, uintptr_t, uintptr_t);
FAKE_VOID_FUNC(secure_glitch_random_delay);
FAKE_VALUE_FUNC(bool, rtos_in_isr);

static ApplicationCertificate_t bl_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0},
  .key = {0},
  .version = 0,
  .signature = {0},
};

// Mimics real flash layout: properties followed by certificate.
// This guarantees cert address > properties address, as required by bl_verify_app_slot.
static struct {
  ApplicationProperties_t props;
  ApplicationCertificate_t cert;
} app_a_slot = {
  .props =
    {
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
      .cert = NULL,  // Set in setup
    },
  .cert =
    {
      .structVersion = APPLICATION_CERTIFICATE_VERSION,
      .flags = {0},
      .key = {0},
      .version = 0,
      .signature = {0},
    },
};

static struct {
  ApplicationProperties_t props;
  ApplicationCertificate_t cert;
} app_b_slot = {
  .props =
    {
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
      .cert = NULL,  // Set in setup
    },
  .cert =
    {
      .structVersion = APPLICATION_CERTIFICATE_VERSION,
      .flags = {0},
      .key = {0},
      .version = 0,
      .signature = {0},
    },
};

// Convenience aliases
#define app_a_properties app_a_slot.props
#define app_b_properties app_b_slot.props

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

static void setup_cert_pointers(void) {
  app_a_slot.props.cert = &app_a_slot.cert;
  app_b_slot.props.cert = &app_b_slot.cert;
}

static void mock_sign_certs(void) {
  setup_cert_pointers();
  MOCK_SIGN(bl_certificate.signature);
  MOCK_SIGN(app_a_slot.cert.signature);
  MOCK_SIGN(app_b_slot.cert.signature);
}

static void mock_corrupt_cert_signatures(void) {
  setup_cert_pointers();
  MOCK_CORRUPT_SIG(bl_certificate.signature);
  MOCK_CORRUPT_SIG(app_a_slot.cert.signature);
  MOCK_CORRUPT_SIG(app_b_slot.cert.signature);
}

static void mock_sign_firmware(void) {
  setup_cert_pointers();
  MOCK_SIGN(app_a_codesigning_signature);
  MOCK_SIGN(app_b_codesigning_signature);
}

static void mock_corrupt_firmware_signatures(void) {
  setup_cert_pointers();
  MOCK_CORRUPT_SIG(app_a_codesigning_signature);
  MOCK_CORRUPT_SIG(app_b_codesigning_signature);
}

static void setup_valid_signatures(void) {
  memset(app_a_properties.app.productId, 0, sizeof(app_a_properties.app.productId));
  memset(app_b_properties.app.productId, 0, sizeof(app_b_properties.app.productId));
  mock_sign_certs();
  mock_sign_firmware();
}

Test(bootloader, verify_app_cert, .init = setup_valid_signatures) {
  cr_assert(bl_verify_app_certificate(&app_a_slot.cert, &bl_certificate) == SECURE_TRUE);
}

Test(bootloader, fail_verify_app_cert, .init = mock_corrupt_cert_signatures) {
  cr_assert(bl_verify_app_certificate(&app_a_slot.cert, &bl_certificate) == SECURE_FALSE);
}

Test(bootloader, verify_app, .init = setup_valid_signatures) {
  cr_assert(bl_verify_application(&app_a_slot.cert, fw_app_a, sizeof(fw_app_a),
                                  app_a_codesigning_signature) == SECURE_TRUE);

  cr_assert(bl_verify_application(&app_b_slot.cert, fw_app_b, sizeof(fw_app_b),
                                  app_b_codesigning_signature) == SECURE_TRUE);
}

Test(bootloader, fail_verify_app, .init = mock_corrupt_firmware_signatures) {
  cr_assert(bl_verify_application(&app_a_slot.cert, fw_app_a, sizeof(fw_app_a),
                                  app_a_codesigning_signature) == SECURE_FALSE);

  cr_assert(bl_verify_application(&app_b_slot.cert, fw_app_b, sizeof(fw_app_b),
                                  app_b_codesigning_signature) == SECURE_FALSE);
}

Test(bootloader, verify_app_slots, .init = setup_valid_signatures) {
  // bl_verify_app_slot() checks slot membership twice. Each SECURE_IF_FAILIN()
  // may evaluate the condition multiple times, so repeated trailing TRUE values
  // are intentional.
  secure_bool_t retvals[4] = {SECURE_TRUE, SECURE_TRUE, SECURE_TRUE, SECURE_TRUE};
  SET_RETURN_SEQ(addrs_in_same_slot, retvals, sizeof(retvals) / sizeof(retvals[0]));

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_TRUE);

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_b_properties, fw_app_b, sizeof(fw_app_b),
                               app_b_codesigning_signature) == SECURE_TRUE);
}

Test(bootloader, invalid_slot_addr, .init = setup_valid_signatures) {
  // First verify_app_slot():
  // - props/app must evaluate TRUE across the repeated SECURE_IF_FAILIN checks
  // - cert/props fails on the first evaluation and short-circuits
  // Second verify_app_slot():
  // - props/app fails on the first evaluation and short-circuits
  secure_bool_t retvals[5] = {SECURE_TRUE, SECURE_TRUE, SECURE_TRUE, SECURE_FALSE, SECURE_FALSE};
  SET_RETURN_SEQ(addrs_in_same_slot, retvals, sizeof(retvals) / sizeof(retvals[0]));

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_FALSE);

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_b_properties, fw_app_b, sizeof(fw_app_b),
                               app_b_codesigning_signature) == SECURE_FALSE);
}

Test(bootloader, verify_app_slot_per_device_chipid_match, .init = setup_valid_signatures) {
  secure_bool_t retvals[2] = {SECURE_TRUE, SECURE_TRUE};
  SET_RETURN_SEQ(addrs_in_same_slot, retvals, sizeof(retvals) / sizeof(retvals[0]));

  static const uint8_t posix_chipid[] = {0x50, 0x4f, 0x53, 0x49, 0x58, 0x2d, 0x49, 0x44};
  memset(app_a_properties.app.productId, 0, sizeof(app_a_properties.app.productId));
  memcpy(app_a_properties.app.productId, posix_chipid, sizeof(posix_chipid));

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_TRUE);
}

Test(bootloader, verify_app_slot_per_device_chipid_mismatch, .init = setup_valid_signatures) {
  secure_bool_t retvals[2] = {SECURE_TRUE, SECURE_TRUE};
  SET_RETURN_SEQ(addrs_in_same_slot, retvals, sizeof(retvals) / sizeof(retvals[0]));

  memset(app_a_properties.app.productId, 0, sizeof(app_a_properties.app.productId));
  app_a_properties.app.productId[0] = 0x01;

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_FALSE);
}

Test(bootloader, verify_app_slot_per_device_chipid_nonzero_tail, .init = setup_valid_signatures) {
  secure_bool_t retvals[2] = {SECURE_TRUE, SECURE_TRUE};
  SET_RETURN_SEQ(addrs_in_same_slot, retvals, sizeof(retvals) / sizeof(retvals[0]));

  static const uint8_t posix_chipid[] = {0x50, 0x4f, 0x53, 0x49, 0x58, 0x2d, 0x49, 0x44};
  memset(app_a_properties.app.productId, 0, sizeof(app_a_properties.app.productId));
  memcpy(app_a_properties.app.productId, posix_chipid, sizeof(posix_chipid));
  app_a_properties.app.productId[CHIPID_LENGTH] = 0x01;

  cr_assert(bl_verify_app_slot(&bl_certificate, &app_a_properties, fw_app_a, sizeof(fw_app_a),
                               app_a_codesigning_signature) == SECURE_FALSE);
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
  app_a_properties.app.version = 1;
  app_b_properties.app.version = 2;
  slot_a.signature_verified = SECURE_TRUE;
  slot_b.signature_verified = SECURE_FALSE;
  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_TRUE);
  cr_assert(selected_slot == &slot_a);
}

Test(bootloader, select_slot_b_when_a_invalid) {
  app_a_properties.app.version = 2;
  app_b_properties.app.version = 1;
  slot_a.signature_verified = SECURE_FALSE;
  slot_b.signature_verified = SECURE_TRUE;
  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_TRUE);
  cr_assert(selected_slot == &slot_b);
}

Test(bootloader, select_no_slot_when_both_invalid) {
  slot_a.signature_verified = SECURE_FALSE;
  slot_b.signature_verified = SECURE_FALSE;
  cr_assert(bl_select_slot(&slot_a, &slot_b, &selected_slot) == SECURE_FALSE);
}
