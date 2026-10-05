#include "ecc.h"
#include "wsm_integrity_key.h"

#include <criterion/criterion.h>

#ifdef CONFIG_PROD
static const uint8_t EXPECTED_WSM_INTEGRITY_PUBKEY[WSM_INTEGRITY_PUBKEY_SIZE] = {
  0x02, 0x95, 0x21, 0x6a, 0x2e, 0x0b, 0x54, 0xb3, 0x82, 0xcc, 0x39,
  0x38, 0xe2, 0x07, 0x29, 0x8d, 0x21, 0xcb, 0x8c, 0x5f, 0x68, 0x6f,
  0x78, 0xb0, 0x5d, 0x9f, 0x14, 0xb4, 0xe4, 0x66, 0x9e, 0x56, 0x0f,
};
#else
static const uint8_t EXPECTED_WSM_INTEGRITY_PUBKEY[WSM_INTEGRITY_PUBKEY_SIZE] = {
  0x03, 0x07, 0x84, 0x51, 0xe0, 0xc1, 0xe1, 0x27, 0x43, 0xd2, 0xfd,
  0xd9, 0x3a, 0xe7, 0xd0, 0x3d, 0x5c, 0xf7, 0x81, 0x3d, 0x2f, 0x61,
  0x2d, 0xe1, 0x09, 0x04, 0xe1, 0xc6, 0xa0, 0xb8, 0x7f, 0x70, 0x71,
};
#endif

static bool verify_signature_called;

bool crypto_ecc_secp256k1_verify_signature(const uint8_t* pubkey, const uint8_t* message,
                                           uint32_t message_size,
                                           const uint8_t signature[ECC_SIG_SIZE]) {
  cr_assert_arr_eq(pubkey, EXPECTED_WSM_INTEGRITY_PUBKEY, WSM_INTEGRITY_PUBKEY_SIZE);
  cr_assert_not_null(message);
  cr_assert_gt(message_size, 0);
  cr_assert_not_null(signature);
  verify_signature_called = true;
  return true;
}

Test(wsm_integrity_key_tests, image_contains_selected_key) {
  cr_assert_arr_eq(WSM_INTEGRITY_PUBKEY, EXPECTED_WSM_INTEGRITY_PUBKEY, WSM_INTEGRITY_PUBKEY_SIZE);
}

Test(wsm_integrity_key_tests, verification_uses_selected_key) {
  const uint8_t message[] = {0x01};
  const uint8_t signature[ECC_SIG_SIZE] = {0};
  verify_signature_called = false;

  cr_assert(wsm_verify_signature(message, sizeof(message), signature));
  cr_assert(verify_signature_called);
}
