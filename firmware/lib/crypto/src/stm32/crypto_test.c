#include "crypto_test.h"

#include "assert.h"
#include "attributes.h"
#include "crypto_test_drbg.h"
#include "crypto_test_ecdsa.h"
#include "crypto_test_ecdsa_p256_keygen.h"
#include "crypto_test_ecdsa_p256_signing.h"
#include "dudero.h"
#include "ecc.h"
#include "hash.h"
#include "hkdf.h"
#include "hmac_drbg_impl.h"
#include "key_management.h"
#include "log.h"
#include "mcu.h"
#include "mcu_hash.h"
#include "mcu_pka.h"
#include "secure_rng.h"
#include "secutils.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

typedef enum {
  /**
   * @brief No failure.
   */
  CRYPTO_TEST_FAILURE_NONE,

  /**
   * @brief Key `(x,y)` are different lengths.
   */
  CRYPTO_TEST_FAILURE_INVALID_KEY,

  /**
   * @brief Signature size is not a multiple of 2.
   */
  CRYPTO_TEST_FAILURE_INVALID_SIGNATURE,

  /**
   * @brief Test result mismatch between expected and actual.
   */
  CRYPTO_TEST_FAILURE_MISMATCH,
} crypto_test_failure_t;

static NO_OPTIMIZE bool crypto_test_ecdsa_verify(void) {
  static const uint8_t Qx[32] = {0xB7, 0xE0, 0x8A, 0xFD, 0xFE, 0x94, 0xBA, 0xD3, 0xF1, 0xDC, 0x8C,
                                 0x73, 0x47, 0x98, 0xBA, 0x1C, 0x62, 0xB3, 0xA0, 0xAD, 0x1E, 0x9E,
                                 0xA2, 0xA3, 0x82, 0x01, 0xCD, 0x08, 0x89, 0xBC, 0x7A, 0x19};

  static const uint8_t Qy[32] = {0x36, 0x03, 0xF7, 0x47, 0x95, 0x9D, 0xBF, 0x7A, 0x4B, 0xB2, 0x26,
                                 0xE4, 0x19, 0x28, 0x72, 0x90, 0x63, 0xAD, 0xC7, 0xAE, 0x43, 0x52,
                                 0x9E, 0x61, 0xB5, 0x63, 0xBB, 0xC6, 0x06, 0xCC, 0x5E, 0x09};

  // E = SHA-256(message)
  static const uint8_t E_hash[32] = {
    0xA4, 0x1A, 0x41, 0xA1, 0x2A, 0x79, 0x95, 0x48, 0x21, 0x1C, 0x41, 0x0C, 0x65, 0xD8, 0x13, 0x3A,
    0xFD, 0xE3, 0x4D, 0x28, 0xBD, 0xD5, 0x42, 0xE4, 0xB6, 0x80, 0xCF, 0x28, 0x99, 0xC8, 0xA8, 0xC4};

  // Signature (r, s)
  static const uint8_t sig_r[32] = {
    0x2B, 0x42, 0xF5, 0x76, 0xD0, 0x7F, 0x41, 0x65, 0xFF, 0x65, 0xD1, 0xF3, 0xB1, 0x50, 0x0F, 0x81,
    0xE4, 0x4C, 0x31, 0x6F, 0x1F, 0x0B, 0x3E, 0xF5, 0x73, 0x25, 0xB6, 0x9A, 0xCA, 0x46, 0x10, 0x4F};

  static const uint8_t sig_s[32] = {
    0xDC, 0x42, 0xC2, 0x12, 0x2D, 0x63, 0x92, 0xCD, 0x3E, 0x3A, 0x99, 0x3A, 0x89, 0x50, 0x2A, 0x81,
    0x98, 0xC1, 0x88, 0x6F, 0xE6, 0x9D, 0x26, 0x2C, 0x4B, 0x32, 0x9B, 0xDB, 0x6B, 0x63, 0xFA, 0xF1};

  mcu_pka_curve_params_t curve;
  mcu_err_t err;

  err = mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve);
  ASSERT(err == MCU_ERROR_OK);

  mcu_pka_public_key_t key = {
    .x = Qx,
    .y = Qy,
    .size = sizeof(Qx),
  };
  mcu_pka_signature_t sig = {
    .r = sig_r,
    .s = sig_s,
    .size = sizeof(sig_r),
  };
  err = mcu_pka_ecdsa_verify(&curve, &key, E_hash, sizeof(E_hash), &sig);
  if (err == MCU_ERROR_OK) {
    LOGI("Crypto ECDSA Verify Test Passed");
  } else {
    LOGE("Crypto ECDSA Verify Test Failed");
  }

  return (err == MCU_ERROR_OK);
}

static NO_OPTIMIZE bool crypto_test_ecdsa_sign(void) {
  // Test vectors for ECDSA signing on secp256r1
  // Private key d
  static const uint8_t private_key[32] = {
    0xC9, 0xAF, 0xA9, 0xD8, 0x45, 0xBA, 0x75, 0x16, 0x6B, 0x5C, 0x21, 0x57, 0x67, 0xB1, 0xD6, 0x93,
    0x4E, 0x50, 0xC3, 0xDB, 0x36, 0xE8, 0x9B, 0x12, 0x7B, 0x8A, 0x62, 0x2B, 0x12, 0x0F, 0x67, 0x21};

  // Ephemeral key k (deterministic for testing)
  static const uint8_t k[32] = {0xA6, 0xE3, 0xC5, 0x7D, 0xD0, 0x1A, 0xBE, 0x90, 0x08, 0x64, 0x38,
                                0x51, 0x57, 0x63, 0xFC, 0x2F, 0x30, 0xF7, 0x20, 0x7E, 0xE7, 0x24,
                                0x1D, 0xD5, 0xC5, 0xC7, 0xD7, 0x8F, 0x76, 0x8A, 0xEC, 0x5D};

  // Message hash z
  static const uint8_t hash[32] = {0x54, 0x68, 0x69, 0x73, 0x20, 0x69, 0x73, 0x20, 0x61, 0x20, 0x74,
                                   0x65, 0x73, 0x74, 0x20, 0x6D, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65,
                                   0x20, 0x68, 0x61, 0x73, 0x68, 0x20, 0x76, 0x61, 0x6C, 0x75};

  // Expected signature r
  static const uint8_t expected_r[32] = {
    0x2A, 0x42, 0x1B, 0xDD, 0x10, 0x3F, 0x9A, 0x14, 0x2F, 0x35, 0xF8, 0x91, 0xB3, 0x11, 0xFF, 0x61,
    0x94, 0x20, 0xF4, 0xD8, 0x59, 0x4A, 0x29, 0xF5, 0x1E, 0x12, 0x1B, 0x02, 0xFF, 0xC5, 0xB2, 0x47};

  // Expected signature s
  static const uint8_t expected_s[32] = {
    0x5A, 0x24, 0xC3, 0x59, 0xA7, 0x9D, 0xB4, 0x1B, 0xD7, 0x16, 0x6B, 0x2A, 0x4B, 0x13, 0x28, 0x43,
    0x9B, 0xDA, 0xC5, 0xDB, 0xB6, 0x0C, 0xAE, 0xE6, 0x3B, 0xC3, 0xA0, 0x3F, 0x95, 0x0A, 0x5E, 0xB9};

  mcu_pka_curve_params_t curve;
  mcu_err_t err;

  // Get secp256r1 curve parameters
  err = mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve);
  ASSERT(err == MCU_ERROR_OK);

  // Buffers for signature output
  uint8_t signature_r[32];
  uint8_t signature_s[32];

  // Sign the hash
  err = mcu_pka_ecdsa_sign(&curve, private_key, hash, sizeof(hash), k, signature_r, signature_s);
  if (err != MCU_ERROR_OK) {
    LOGE("Crypto ECDSA Sign Test Failed: err=%u", err);
    return false;
  }

  // Verify signature r matches expected value
  for (size_t i = 0; i < sizeof(signature_r); i++) {
    if (signature_r[i] != expected_r[i]) {
      LOGE("Crypto ECDSA Sign Test Failed: r mismatch at index %zu", i);
      return false;
    }
  }

  // Verify signature s matches expected value
  for (size_t i = 0; i < sizeof(signature_s); i++) {
    if (signature_s[i] != expected_s[i]) {
      LOGE("Crypto ECDSA Sign Test Failed: s mismatch at index %zu", i);
      return false;
    }
  }

  LOGI("Crypto ECDSA Sign Test Passed");
  return true;
}

static NO_OPTIMIZE bool crypto_test_ecdsa_p256_verify(void) {
  mcu_pka_curve_params_t curve;
  crypto_test_failure_t failure = CRYPTO_TEST_FAILURE_NONE;
  mcu_err_t err;
  size_t i;

  err = mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve);
  ASSERT(err == MCU_ERROR_OK);

  for (i = 0; i < TEST_ECDSA_VERIFY_NUM_TESTS; i++) {
    const struct wycheproof_ecdsa_verify_test* test = &wycheproof_ecdsa_verify_tests[i];

    // Sanity check: X and Y must be the same length.
    if (test->wx_len != test->wy_len) {
      // Skip check if expected to fail; ECDSA verify() does not accept mismatched
      // key lengths.
      if (test->expected != false) {
        failure = CRYPTO_TEST_FAILURE_INVALID_KEY;
        break;
      }
      continue;
    }

    const mcu_pka_public_key_t key = {
      .x = (const uint8_t*)test->wx, .y = (const uint8_t*)test->wy, .size = test->wx_len};

    // Sanity check: Signature length must be divisible by 2.
    if ((test->sig_len % 2) != 0) {
      failure = CRYPTO_TEST_FAILURE_INVALID_SIGNATURE;
      break;
    }

    // Signature contains R and S portions in the same buffer, so first half
    // of the buffer is R and second half is S.
    const mcu_pka_signature_t sig = {
      .r = (const uint8_t*)&test->sig[0],
      .s = (const uint8_t*)&test->sig[test->sig_len / 2],
      .size = test->sig_len / 2,
    };

    // Empty messages are expected to fail.
    if (test->msg_len == 0) {
      if (test->expected != false) {
        failure = CRYPTO_TEST_FAILURE_MISMATCH;
        break;
      }
      continue;
    }

    // Hash the message.
    uint8_t hash[MCU_HASH_SHA256_DIGEST_LENGTH];
    err =
      mcu_hash(MCU_HASH_ALG_SHA256, (const uint8_t*)test->msg, test->msg_len, hash, sizeof(hash));
    ASSERT(err == MCU_ERROR_OK);

    err = mcu_pka_ecdsa_verify(&curve, &key, hash, sizeof(hash), &sig);

    // Validate that the test failed or pass as expected.
    if ((test->expected == TEST_ECDSA_VERIFY_SUCCESS) ^ (err == MCU_ERROR_OK)) {
      // Ensure the operation did not time out.
      ASSERT(err != MCU_ERROR_TIMEOUT);
      failure = CRYPTO_TEST_FAILURE_MISMATCH;
      break;
    }
  }

  if (failure != CRYPTO_TEST_FAILURE_NONE) {
    LOGE("Crypto ECDSA P256 Verification Test Failed: test_index=%u, failure=%u", i, failure);
  } else {
    LOGI("Crypto ECDSA P256 Verification Tests Passed");
  }

  return (failure == CRYPTO_TEST_FAILURE_NONE);
}

static NO_OPTIMIZE bool crypto_test_ecdsa_p256_sign_verify(void) {
  // Test message to sign
  const char* test_message = "TEST_MESSAGE";
  uint8_t message_hash[SHA256_DIGEST_SIZE];

  // Hash the test message
  if (!crypto_hash((const uint8_t*)test_message, strlen(test_message), message_hash,
                   sizeof(message_hash), ALG_SHA256)) {
    LOGE("Failed to hash test message");
    return false;
  }

  // Generate a P-256 key pair
  uint8_t privkey_buffer[ECC_PRIVKEY_SIZE];
  key_handle_t privkey = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key = {.bytes = privkey_buffer, .size = sizeof(privkey_buffer)},
  };

  if (!generate_key(&privkey)) {
    LOGE("Failed to generate private key");
    return false;
  }

  // Derive public key from private key
  uint8_t pubkey_buffer[ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED];
  key_handle_t pubkey = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key = {.bytes = pubkey_buffer, .size = sizeof(pubkey_buffer)},
  };

  if (!export_pubkey(&privkey, &pubkey)) {
    LOGE("Failed to export public key");
    zeroize_key(&privkey);
    return false;
  }

  // Sign the message hash
  uint8_t signature[ECC_SIG_SIZE];
  secure_bool_t sign_result =
    crypto_ecc_sign_hash(&privkey, message_hash, sizeof(message_hash), signature);

  if (sign_result != SECURE_TRUE) {
    LOGE("Failed to sign message");
    zeroize_key(&privkey);
    return false;
  }

  // Verify the signature
  secure_bool_t verify_result =
    crypto_ecc_verify_hash(&pubkey, message_hash, sizeof(message_hash), signature);

  if (verify_result != SECURE_TRUE) {
    LOGE("ECC Sign/Verify Test FAILED - signature verification failed");
    zeroize_key(&privkey);
    return false;
  }

  // Test with corrupted signature
  signature[0] ^= 0xFF;  // Flip bits in first byte
  verify_result = crypto_ecc_verify_hash(&pubkey, message_hash, sizeof(message_hash), signature);

  if (verify_result != SECURE_FALSE) {
    LOGE("Corrupted signature was incorrectly accepted!");
    zeroize_key(&privkey);
    return false;
  }
  // Clean up sensitive data
  zeroize_key(&privkey);
  LOGI("Crypto ECDSA P256 Simple Sign/Verify Test Passed");
  return true;
}

static NO_OPTIMIZE bool crypto_test_scalar_validation(void) {
  // Get P-256 curve parameters
  mcu_pka_curve_params_t curve_params;
  mcu_err_t err = mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params);
  if (err != MCU_ERROR_OK) {
    LOGE("Failed to get curve parameters");
    return false;
  }

  unsigned int passed = 0;
  unsigned int total = 0;

  // Test 1: Zero should be invalid
  total++;
  uint8_t zero[MCU_PKA_SECP256R1_SIZE] = {0};
  if (mcu_pka_validate_scalar(&curve_params, zero, sizeof(zero)) != MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 1 - Zero incorrectly accepted");
  }

  // Test 2: One should be valid (minimum valid value)
  total++;
  uint8_t one[MCU_PKA_SECP256R1_SIZE] = {0};
  one[MCU_PKA_SECP256R1_SIZE - 1] = 1;  // Big-endian: 0x00...01
  if (mcu_pka_validate_scalar(&curve_params, one, sizeof(one)) == MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 2 - One incorrectly rejected");
  }

  // Test 3: n-1 should be valid (maximum valid value)
  total++;
  uint8_t n_minus_1[MCU_PKA_SECP256R1_SIZE];
  memcpy(n_minus_1, curve_params.order, MCU_PKA_SECP256R1_SIZE);
  // Subtract 1 from n (big-endian)
  for (int i = MCU_PKA_SECP256R1_SIZE - 1; i >= 0; i--) {
    if (n_minus_1[i] > 0) {
      n_minus_1[i]--;
      break;
    }
    n_minus_1[i] = 0xFF;  // Borrow from next byte
  }
  if (mcu_pka_validate_scalar(&curve_params, n_minus_1, sizeof(n_minus_1)) == MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 3 - n-1 incorrectly rejected");
  }

  // Test 4: n (curve order) should be invalid
  total++;
  uint8_t n[MCU_PKA_SECP256R1_SIZE];
  memcpy(n, curve_params.order, MCU_PKA_SECP256R1_SIZE);
  if (mcu_pka_validate_scalar(&curve_params, n, sizeof(n)) != MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 4 - n incorrectly accepted");
  }

  // Test 5: n+1 should be invalid
  total++;
  uint8_t n_plus_1[MCU_PKA_SECP256R1_SIZE];
  memcpy(n_plus_1, curve_params.order, MCU_PKA_SECP256R1_SIZE);
  // Add 1 to n (big-endian)
  for (int i = MCU_PKA_SECP256R1_SIZE - 1; i >= 0; i--) {
    n_plus_1[i]++;
    if (n_plus_1[i] != 0) {
      break;  // No carry
    }
    // Carry to next byte
  }
  if (mcu_pka_validate_scalar(&curve_params, n_plus_1, sizeof(n_plus_1)) != MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 5 - n+1 incorrectly accepted");
  }

  // Test 6: All 0xFF bytes (maximum 256-bit value) should be invalid
  total++;
  uint8_t max_value[MCU_PKA_SECP256R1_SIZE];
  memset(max_value, 0xFF, sizeof(max_value));
  if (mcu_pka_validate_scalar(&curve_params, max_value, sizeof(max_value)) != MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 6 - Max 256-bit value incorrectly accepted");
  }

  // Test 7: Random valid value in middle of range should be valid
  total++;
  uint8_t mid_value[MCU_PKA_SECP256R1_SIZE] = {
    0x7F, 0xFF, 0xFF, 0xFF, 0x80, 0x00, 0x00, 0x00, 0x7F, 0xFF, 0xFF, 0xFF, 0x7F, 0xFF, 0xFF, 0xFF,
    0x5E, 0x73, 0x7D, 0x56, 0xD3, 0x8B, 0xCF, 0x42, 0x79, 0xDC, 0xE5, 0x61, 0x7E, 0x31, 0x92, 0xA8};
  if (mcu_pka_validate_scalar(&curve_params, mid_value, sizeof(mid_value)) == MCU_ERROR_OK) {
    passed++;
  } else {
    LOGE("FAIL: Test 7 - Mid-range value incorrectly rejected");
  }

  LOGI("Crypto Scalar Validation Test: %u / %u Passed", passed, total);
  return (passed == total);
}

static NO_OPTIMIZE bool crypto_test_ecdsa_p256_keygen_vectors(void) {
  unsigned int passed = 0;
  unsigned int total = P256_NUM_KEYGEN_VECTORS;

  for (unsigned int i = 0; i < P256_NUM_KEYGEN_VECTORS; i++) {
    const p256_keygen_vector_t* test = &p256_keygen_vectors[i];

    // Set up private key
    uint8_t privkey_buffer[ECC_PRIVKEY_SIZE];
    memcpy(privkey_buffer, test->d, ECC_PRIVKEY_SIZE);

    key_handle_t privkey = {
      .alg = ALG_ECC_P256,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key = {.bytes = privkey_buffer, .size = sizeof(privkey_buffer)},
    };

    // Derive public key
    uint8_t pubkey_buffer[ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED];
    key_handle_t pubkey = {
      .alg = ALG_ECC_P256,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key = {.bytes = pubkey_buffer, .size = sizeof(pubkey_buffer)},
    };

    if (!export_pubkey(&privkey, &pubkey)) {
      LOGE("Test %u: Failed to derive public key", i);
      continue;
    }

    // Verify public key matches expected values
    if (memcmp(&pubkey.key.bytes[0], test->Qx, P256_COORDINATE_SIZE) == 0 &&
        memcmp(&pubkey.key.bytes[P256_COORDINATE_SIZE], test->Qy, P256_COORDINATE_SIZE) == 0) {
      passed++;
    } else {
      LOGE("Test %u: Public key mismatch", i);
    }
  }

  LOGI("P-256 Key Generation Test: %u / %u Passed", passed, total);

  return (passed == total);
}

static NO_OPTIMIZE bool crypto_test_ecdsa_p256_signing_vectors(void) {
  unsigned int passed = 0;
  unsigned int total = P256_NUM_SIGNING_VECTORS;

  // Get P-256 curve parameters
  mcu_pka_curve_params_t curve_params;
  if (mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params) != MCU_ERROR_OK) {
    LOGE("Failed to get curve parameters");
    return false;
  }

  for (unsigned int i = 0; i < P256_NUM_SIGNING_VECTORS; i++) {
    const p256_signing_vector_t* test = &p256_signing_vectors[i];

    // Hash the message using SHA-256
    uint8_t message_hash[SHA256_DIGEST_SIZE];
    if (!crypto_hash(test->msg, test->msg_len, message_hash, sizeof(message_hash), ALG_SHA256)) {
      LOGE("Test %u: Failed to hash message", i);
      continue;
    }

    // Generate signature using provided private key (d) and nonce (k)
    uint8_t sig_r[P256_SIGNATURE_SIZE];
    uint8_t sig_s[P256_SIGNATURE_SIZE];

    mcu_err_t result = mcu_pka_ecdsa_sign(&curve_params, test->d, message_hash,
                                          sizeof(message_hash), test->k, sig_r, sig_s);

    if (result != MCU_ERROR_OK) {
      LOGE("Test %u: Signature generation failed", i);
      continue;
    }

    // Compare generated signature with expected signature
    if (memcmp(sig_r, test->R, P256_SIGNATURE_SIZE) == 0 &&
        memcmp(sig_s, test->S, P256_SIGNATURE_SIZE) == 0) {
      passed++;
    } else {
      LOGE("Test %u: Signature mismatch", i);
    }
  }

  if (passed == total) {
    LOGI("P-256 Signature Generation Test Passed: %u / %u", passed, total);
  } else {
    LOGE("P-256 Signature Generation Test Failed: %u / %u", passed, total);
  }

  return (passed == total);
}

void crypto_test_ecdsa(void) {
  unsigned int success = 0;

  success += crypto_test_ecdsa_verify() ? 1 : 0;
  success += crypto_test_ecdsa_sign() ? 1 : 0;
  success += crypto_test_ecdsa_p256_verify() ? 1 : 0;
  success += crypto_test_ecdsa_p256_sign_verify() ? 1 : 0;
  success += crypto_test_scalar_validation() ? 1 : 0;
  success += crypto_test_ecdsa_p256_keygen_vectors() ? 1 : 0;
  success += crypto_test_ecdsa_p256_signing_vectors() ? 1 : 0;

  LOGI("Crypto ECDSA Test Suite: %u / 7 Passed", success);
}

void crypto_test_random(void) {
  dudero_ctx_t ctx;
  dudero_stream_init(&ctx);
  for (size_t i = 0; i < 256; i++) {
    uint8_t r = 0;
    if (!crypto_random(&r, 1)) {
      LOGE("Failed crypto_random");
      return;
    }
    dudero_stream_add(&ctx, r);
  }
  if (dudero_stream_finish(&ctx) != DUDERO_RET_OK) {
    LOGE("randomness_test FAIL");
  } else {
    LOGI("randomness_test PASS");
  }
  return;
}

void crypto_test_drbg(void) {
  LOGI("DRBG Test:");
  hmac_drbg_state_t state = {0};

  const int num_tests = sizeof(drbg_tests) / sizeof(drbg_tests[0]);
  uint8_t output[sizeof(drbg_tests[0].output)] = {0};

  for (int i = 0; i < num_tests; i++) {
    crypto_hmac_drbg_init(drbg_tests[i].init_entropy, sizeof(drbg_tests[i].init_entropy), &state);
    if (memcmp(state.k, drbg_tests[i].k_start, sizeof(state.k)) != 0 ||
        memcmp(state.v, drbg_tests[i].v_start, sizeof(state.v)) != 0) {
      LOGE("FAIL: init - case %d", i);
      continue;
    }

    crypto_hmac_drbg_reseed(drbg_tests[i].reseed, sizeof(drbg_tests[i].reseed), &state);
    if (memcmp(state.k, drbg_tests[i].k_after_reseed, sizeof(state.k)) != 0 ||
        memcmp(state.v, drbg_tests[i].v_after_reseed, sizeof(state.v)) != 0) {
      LOGE("FAIL: reseed - case %zd", i);
      continue;
    }

    crypto_hmac_drbg_generate(&state, output, sizeof(output));
    if (memcmp(state.k, drbg_tests[i].k_after_gen1, sizeof(state.k)) != 0 ||
        memcmp(state.v, drbg_tests[i].v_after_gen1, sizeof(state.v)) != 0) {
      LOGE("FAIL: generate 0 - case %d", i);
      continue;
    }

    crypto_hmac_drbg_generate(&state, output, sizeof(output));
    if (memcmp(state.k, drbg_tests[i].k_after_gen2, sizeof(state.k)) != 0 ||
        memcmp(state.v, drbg_tests[i].v_after_gen2, sizeof(state.v)) != 0 ||
        memcmp(output, drbg_tests[i].output, sizeof(output)) != 0) {
      LOGE("FAIL: generate 1 - case %d", i);
      continue;
    }
    LOGI("\tTest %d PASS", i);
  }
  // Test getting different output sizes to check edge cases.
  for (size_t i = 0; i < sizeof(output); i++) {
    crypto_hmac_drbg_init(drbg_tests[0].init_entropy, sizeof(drbg_tests[0].init_entropy), &state);
    crypto_hmac_drbg_reseed(drbg_tests[0].reseed, sizeof(drbg_tests[0].reseed), &state);
    crypto_hmac_drbg_generate(&state, output, sizeof(output));
    crypto_hmac_drbg_generate(&state, output, i);
    if (memcmp(output, drbg_tests[0].output, i) != 0) {
      LOGE("FAIL: output size %zu", i);
      return;
    }
  }
  LOGI("\tOutput Size Test PASS");
}

void crypto_test_curve25519(void) {
  uint8_t their_pubkey_buf[EC_PUBKEY_SIZE_X25519] = {
    0xff, 0x0b, 0x5e, 0x28, 0x72, 0x06, 0xe5, 0xa6, 0x1f, 0xc3, 0x4d, 0xae, 0x81, 0x54, 0x8d, 0x4f,
    0xdb, 0x5c, 0x88, 0x72, 0x2c, 0x3c, 0x05, 0xab, 0xe0, 0x68, 0x28, 0xfb, 0x5a, 0x8d, 0x07, 0x59,
  };

  uint8_t our_privkey_buf[ECC_PRIVKEY_SIZE] = {
    0xe2, 0x08, 0x54, 0xfb, 0x7c, 0x69, 0x2b, 0x69, 0xaf, 0x73, 0x5d, 0x63, 0xe8, 0xa8, 0x0c, 0x98,
    0x9f, 0x3c, 0x37, 0x65, 0x1a, 0x1f, 0x32, 0xff, 0x84, 0x6c, 0x6f, 0x4c, 0x05, 0x70, 0x99, 0x3f,
  };

  uint8_t expected_secret[EC_SECRET_SIZE_X25519] = {
    0x4e, 0xce, 0xdb, 0x30, 0x17, 0x5b, 0xb7, 0xbc, 0xed, 0xb8, 0x46, 0x41, 0x1c, 0xe8, 0xc3, 0xf2,
    0xff, 0xcb, 0xcd, 0x72, 0xac, 0xb4, 0x08, 0x71, 0xc4, 0x91, 0xec, 0x87, 0x63, 0xc0, 0x7e, 0x6f,
  };

  key_handle_t our_privkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = our_privkey_buf,
    .key.size = sizeof(our_privkey_buf),
  };

  key_handle_t their_pubkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = their_pubkey_buf,
    .key.size = sizeof(their_pubkey_buf),
  };

  uint8_t secret_buf[EC_SECRET_SIZE_X25519] = {0};
  key_handle_t secret = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = secret_buf,
    .key.size = sizeof(secret_buf),
  };

  if (!crypto_ecc_compute_shared_secret(&our_privkey, &their_pubkey, &secret)) {
    LOGE("Failed to compute shared secret");
    return;
  }

  if (memcmp(secret.key.bytes, expected_secret, sizeof(expected_secret)) != 0) {
    LOGE("Secret does not match expected.");
    return;
  }

  LOGI("25519_ecdh_test PASS");
}

void crypto_test_hkdf(void) {
  LOGI("HKDF Test:");
  // Test Case 1: Basic test case with SHA-256
  // IKM (Input Keying Material)
  static const uint8_t ikm[] = {0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                                0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b};

  // Salt
  static const uint8_t salt[] = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                                 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c};

  // Info
  static const uint8_t info[] = {0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9};

  // Expected output (42 bytes)
  static const uint8_t expected_output[] = {
    0x3c, 0xb2, 0x5f, 0x25, 0xfa, 0xac, 0xd5, 0x7a, 0x90, 0x43, 0x4f, 0x64, 0xd0, 0x36,
    0x2f, 0x2a, 0x2d, 0x2d, 0x0a, 0x90, 0xcf, 0x1a, 0x5a, 0x4c, 0x5d, 0xb0, 0x2d, 0x56,
    0xec, 0xc4, 0xc5, 0xbf, 0x34, 0x00, 0x72, 0x08, 0xd5, 0xb8, 0x87, 0x18, 0x58, 0x65};

  key_handle_t ikm_handle = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)ikm,
    .key.size = sizeof(ikm),
  };

  uint8_t output_buf[42] = {0};
  key_handle_t output_handle = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = output_buf,
    .key.size = sizeof(output_buf),
  };

  // Test Case 1: Basic HKDF with salt and info
  if (!crypto_hkdf(&ikm_handle, ALG_SHA256, salt, sizeof(salt), info, sizeof(info),
                   &output_handle)) {
    LOGE("FAIL: Test 1 - crypto_hkdf returned false");
    return;
  }

  if (memcmp(output_buf, expected_output, sizeof(expected_output)) != 0) {
    LOGE("FAIL: Test 1 - output mismatch");
    return;
  }

  LOGI("\tTest 1 Passed (RFC 5869 Test Case 1)");

  // Test Case 2 (RFC 5869 Test Case 3): Test with SHA-256 and zero-length salt/info
  // Note: We are skipping test case 2 from the RFC since we don't support long info labels.
  static const uint8_t ikm2[] = {0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                                 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b};

  static const uint8_t expected_okm2[] = {
    0x8d, 0xa4, 0xe7, 0x75, 0xa5, 0x63, 0xc1, 0x8f, 0x71, 0x5f, 0x80, 0x2a, 0x06, 0x3c,
    0x5a, 0x31, 0xb8, 0xa1, 0x1f, 0x5c, 0x5e, 0xe1, 0x87, 0x9e, 0xc3, 0x45, 0x4e, 0x5f,
    0x3c, 0x73, 0x8d, 0x2d, 0x9d, 0x20, 0x13, 0x95, 0xfa, 0xa4, 0xb6, 0x1a, 0x96, 0xc8};

  key_handle_t ikm2_handle = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)ikm2,
    .key.size = sizeof(ikm2),
  };

  uint8_t output2_buf[42] = {0};
  key_handle_t output2_handle = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = output2_buf,
    .key.size = sizeof(output2_buf),
  };

  // Test Case 2: HKDF with NULL salt (should use zero salt)
  if (!crypto_hkdf(&ikm2_handle, ALG_SHA256, NULL, 0, NULL, 0, &output2_handle)) {
    LOGE("\tFAIL: Test 2 - crypto_hkdf returned false");
    return;
  }

  if (memcmp(output2_buf, expected_okm2, sizeof(expected_okm2)) != 0) {
    LOGE("\tFAIL: Test 2 - output mismatch");
    return;
  }

  LOGI("\tTest 2 PASS (RFC 5869 Test Case 3)");
}
