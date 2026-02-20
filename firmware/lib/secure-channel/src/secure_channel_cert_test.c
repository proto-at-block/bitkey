#include "filesystem.h"
#include "hash.h"
#include "hex.h"
#include "log.h"
#include "printf.h"
#include "secure_channel_cert.h"
#include "secure_channel_cert_impl.h"
#include "secure_channel_test.h"

#include <string.h>

#define TEST_CERT_ID          "w3_core_id_test"
#define TEST_CERT_ID_TAMPERED "w3_core_id_test_tampered"

// External reference to product cert descriptors
extern const secure_channel_cert_desc_t* const secure_channel_product_certs[];

static void cleanup_test_certs(void) {
  // Clean up key and cert for local identity
  const secure_channel_cert_desc_t* const* desc_ptr = secure_channel_product_certs;
  while (*desc_ptr != NULL) {
    const secure_channel_cert_desc_t* desc = *desc_ptr;
    secure_channel_cert_clear_cert_and_key_files(desc->id);
    desc_ptr++;
  }
  // Clear test cert for pinning/etc
  char test_cert_id[SC_CERT_ID_MAX_SIZE] = {0};
  strncpy(test_cert_id, TEST_CERT_ID, sizeof(test_cert_id) - 1);
  test_cert_id[sizeof(test_cert_id) - 1] = '\0';
  secure_channel_cert_clear_cert_and_key_files(test_cert_id);

  strncpy(test_cert_id, TEST_CERT_ID_TAMPERED, sizeof(test_cert_id) - 1);
  test_cert_id[sizeof(test_cert_id) - 1] = '\0';
  secure_channel_cert_clear_cert_and_key_files(test_cert_id);
}

static void cleanup_test_dir(void) {
  if (fs_get_filetype(SC_CERT_DIRECTORY) == FS_FILE_TYPE_DIR) {
    fs_remove(SC_CERT_DIRECTORY);
  }
}

static bool test_initialize_certificates(void) {
  printf("\n[TEST] Initialize certificates\n");
  cleanup_test_certs();
  cleanup_test_dir();
  secure_channel_cert_init();
  return true;
}

static bool test_get_local_certificate(void) {
  printf("\n[TEST] Get local certificate\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  const secure_channel_cert_desc_t* local_desc = secure_channel_product_certs[0];
  if (local_desc == NULL) {
    printf("FAILED: No certificate descriptors available\n");
    return false;
  }

  secure_channel_cert_data_t cert_data = {0};
  if (!secure_channel_read_cert(local_desc->id, &cert_data)) {
    printf("FAILED: Could not get certificate\n");
    return false;
  }

  return true;
}

static bool test_sign_and_verify(void) {
  printf("\n[TEST] Sign and verify data with local certificate\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  const secure_channel_cert_desc_t* local_desc = secure_channel_product_certs[0];
  if (local_desc == NULL) {
    printf("FAILED: No certificate descriptors available\n");
    return false;
  }

  secure_channel_cert_data_t cert_data = {0};
  if (!secure_channel_read_cert(local_desc->id, &cert_data)) {
    printf("FAILED: Could not get certificate\n");
    return false;
  }

  uint8_t test_data[] = "Hello, secure channel!";
  uint8_t data_hash[SHA256_DIGEST_SIZE] = {0};
  bool hash_ok =
    crypto_hash(test_data, sizeof(test_data), data_hash, sizeof(data_hash), ALG_SHA256);
  if (!hash_ok) {
    printf("FAILED: Could not hash data\n");
    return false;
  }

  uint8_t signature[ECC_SIG_SIZE] = {0};
  if (!secure_channel_sign_digest(local_desc, data_hash, sizeof(data_hash), signature,
                                  sizeof(signature))) {
    printf("FAILED: Could not sign data\n");
    return false;
  }

  if (!secure_channel_verify_digest(&cert_data, data_hash, sizeof(data_hash), signature,
                                    sizeof(signature))) {
    printf("FAILED: Could not verify signature\n");
    return false;
  }

  return true;
}

static bool test_tofu_pinning(void) {
  printf("\n[TEST] TOFU pinning with peer certificate\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  // Generate a temporary peer certificate with a different subject
  secure_channel_cert_desc_t peer_desc = {
    .key_type = ALG_ECC_P256,
    .key_storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .cert_type = CERT_TYPE_PICOCERT,
  };
  memset(peer_desc.id, 0, sizeof(peer_desc.id));
  strncpy(peer_desc.id, TEST_CERT_ID, sizeof(peer_desc.id) - 1);
  peer_desc.id[sizeof(peer_desc.id) - 1] = '\0';

  if (!secure_channel_cert_generate_certificate(&peer_desc)) {
    printf("FAILED: Could not generate peer certificate\n");
    return false;
  }

  secure_channel_cert_data_t peer_cert = {0};
  if (!secure_channel_read_cert(peer_desc.id, &peer_cert)) {
    printf("FAILED: Could not read peer certificate\n");
    return false;
  }

  // Clear existing key material (just generated)
  // and then pin the peer certificate (TOFU)
  // this is similar to just receiving a peer certificate from a secure channel handshake
  secure_channel_cert_clear_cert_and_key_files(peer_desc.id);
  secure_channel_cert_err_t result = secure_channel_pin_cert(&peer_cert);

  if (result != SECURE_CHANNEL_CERT_OK) {
    printf("FAILED: Certificate pin failed with result: %d\n", result);
    return false;
  }

  result = secure_channel_matches_pinned_cert(&peer_cert);

  if (result != SECURE_CHANNEL_CERT_OK) {
    printf("FAILED: Certificate pin verification failed with result: %d\n", result);
    return false;
  }

  // tamper with cert subject
  memset(peer_cert.data.picocert.subject, 0, sizeof(peer_cert.data.picocert.subject));
  strncpy(peer_cert.data.picocert.subject, TEST_CERT_ID_TAMPERED,
          sizeof(peer_cert.data.picocert.subject) - 1);
  peer_cert.data.picocert.subject[sizeof(peer_cert.data.picocert.subject) - 1] = '\0';

  result = secure_channel_matches_pinned_cert(&peer_cert);
  if (result != SECURE_CHANNEL_CERT_PINNED_NOT_FOUND) {
    printf("FAILED: Tampered certificate subject not detected! Result: %d\n", result);
    return false;
  }

  // restore cert subject
  memset(peer_cert.data.picocert.subject, 0, sizeof(peer_cert.data.picocert.subject));
  strncpy(peer_cert.data.picocert.subject, TEST_CERT_ID,
          sizeof(peer_cert.data.picocert.subject) - 1);
  peer_cert.data.picocert.subject[sizeof(peer_cert.data.picocert.subject) - 1] = '\0';

  // tamper with cert data
  peer_cert.data.picocert.public_key[5] ^= 0xFF;  // Flip bits
  result = secure_channel_matches_pinned_cert(&peer_cert);

  if (result != SECURE_CHANNEL_CERT_PINNED_MISMATCH) {
    printf("FAILED: Tampered certificate not detected! Result: %d\n", result);
    return false;
  }

  return true;
}

static bool test_pin_null_cert(void) {
  printf("\n[TEST] Verify null certificate\n");
  cleanup_test_certs();
  secure_channel_cert_data_t cert_data = {0};
  secure_channel_cert_err_t result = secure_channel_matches_pinned_cert(&cert_data);
  if (result != SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE) {
    printf("FAILED: Unsupported certificate type not detected! Result: %d\n", result);
    return false;
  }
  return true;
}

static bool test_pin_unsupported_type(void) {
  printf("\n[TEST] Verify unsupported certificate type\n");
  cleanup_test_certs();
  secure_channel_cert_data_t cert_data = {0};
  cert_data.type = 99;
  secure_channel_cert_err_t result = secure_channel_matches_pinned_cert(&cert_data);
  if (result != SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE) {
    printf("FAILED: Unsupported certificate type not detected! Result: %d\n", result);
    return false;
  }
  return true;
}

static bool test_pin_already_exists(void) {
  printf("\n[TEST] Pin certificate but it already exists\n");
  cleanup_test_certs();
  secure_channel_cert_init();
  secure_channel_cert_data_t cert_data = {0};
  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }
  secure_channel_cert_err_t result = secure_channel_pin_cert(&cert_data);
  if (result != SECURE_CHANNEL_CERT_PINNED_ALREADY_EXISTS) {
    printf("FAILED: Certificate already pinned! Result: %d\n", result);
    return false;
  }
  return true;
}

static bool test_verify_pin_exists(void) {
  printf("\n[TEST] Verify pinned certificate exists\n");
  cleanup_test_certs();
  secure_channel_cert_init();
  secure_channel_cert_data_t cert_data = {0};
  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }
  // Returns true since we generated it ourselves
  secure_channel_cert_err_t result = secure_channel_matches_pinned_cert(&cert_data);
  if (result != SECURE_CHANNEL_CERT_OK) {
    printf("FAILED: Certificate not pinned! Result: %d\n", result);
    return false;
  }
  return true;
}

static bool test_verify_pin_not_exists(void) {
  printf("\n[TEST] Verify pinned certificate does not exist\n");
  cleanup_test_certs();
  secure_channel_cert_init();
  secure_channel_cert_data_t cert_data = {0};
  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }
  secure_channel_cert_clear_cert_and_key_files(desc->id);
  secure_channel_cert_err_t result = secure_channel_matches_pinned_cert(&cert_data);
  if (result != SECURE_CHANNEL_CERT_PINNED_NOT_FOUND) {
    printf("FAILED: Certificate not found! Result: %d\n", result);
    return false;
  }
  return true;
}

static bool test_verify_pin_mismatched(void) {
  printf("\n[TEST] Verify pinned certificate mismatched\n");
  cleanup_test_certs();
  secure_channel_cert_init();
  secure_channel_cert_data_t cert_data = {0};
  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }
  // tamper with cert data
  cert_data.data.picocert.public_key[5] ^= 0xFF;  // Flip bits to create a mismatch
  secure_channel_cert_err_t result = secure_channel_matches_pinned_cert(&cert_data);
  if (result != SECURE_CHANNEL_CERT_PINNED_MISMATCH) {
    printf("FAILED: Certificate mismatch not detected! Result: %d\n", result);
    return false;
  }
  return true;
}

static bool test_peer_signature_verification(void) {
  printf("\n[TEST] Peer signature verification\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  // Set up peer descriptor and load cert
  secure_channel_cert_desc_t peer_desc = {
    .key_type = ALG_ECC_P256,
    .key_storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .cert_type = CERT_TYPE_PICOCERT,
  };
  strncpy(peer_desc.id, TEST_CERT_ID, sizeof(peer_desc.id) - 1);
  peer_desc.id[sizeof(peer_desc.id) - 1] = '\0';

  if (!secure_channel_cert_generate_certificate(&peer_desc)) {
    printf("FAILED: Could not generate peer certificate\n");
    return false;
  }
  secure_channel_cert_data_t peer_cert = {0};
  if (!secure_channel_read_cert(peer_desc.id, &peer_cert)) {
    printf("FAILED: Could not read peer certificate\n");
    return false;
  }

  // Generate peer signature, in prod this is done by the peer during the secure channel handshake.
  uint8_t peer_data[] = "Peer message";
  uint8_t peer_hash[SHA256_DIGEST_SIZE] = {0};
  bool peer_hash_ok =
    crypto_hash(peer_data, sizeof(peer_data), peer_hash, sizeof(peer_hash), ALG_SHA256);
  if (!peer_hash_ok) {
    printf("FAILED: Could not hash peer message\n");
    return false;
  }

  uint8_t peer_signature[ECC_SIG_SIZE] = {0};
  if (!secure_channel_sign_digest(&peer_desc, peer_hash, sizeof(peer_hash), peer_signature,
                                  sizeof(peer_signature))) {
    printf("FAILED: Could not sign peer message\n");
    return false;
  }

  // Verify the peer cert matches the pinned cert
  secure_channel_cert_err_t result = secure_channel_matches_pinned_cert(&peer_cert);
  if (result != SECURE_CHANNEL_CERT_OK) {
    printf("FAILED: Pin verification failed with result: %d\n", result);
    return false;
  }

  // Verify the signature from the peer, using the public key.
  if (!secure_channel_verify_digest(&peer_cert, peer_hash, sizeof(peer_hash), peer_signature,
                                    sizeof(peer_signature))) {
    printf("FAILED: Signature verification failed\n");
    return false;
  }

  return true;
}

static bool test_verify_valid_self_signed_cert(void) {
  printf("\n[TEST] Verify valid self-signed certificate\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (desc == NULL) {
    printf("FAILED: No certificate descriptors available\n");
    return false;
  }

  secure_channel_cert_data_t cert_data = {0};
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }

  if (!secure_channel_cert_verify_self_signed_picocert_signature(&cert_data.data.picocert)) {
    printf("FAILED: Valid self-signed certificate verification failed\n");
    return false;
  }

  return true;
}

static bool test_verify_invalid_self_signed_cert_signature(void) {
  printf("\n[TEST] Detect invalid self-signed certificate signature\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (desc == NULL) {
    printf("FAILED: No certificate descriptors available\n");
    return false;
  }

  secure_channel_cert_data_t cert_data = {0};
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }

  // Tamper with the signature
  cert_data.data.picocert.signature[0] ^= 0xFF;

  if (secure_channel_cert_verify_self_signed_picocert_signature(&cert_data.data.picocert)) {
    printf("FAILED: Tampered signature not detected\n");
    return false;
  }

  return true;
}

static bool test_verify_invalid_self_signed_cert_public_key(void) {
  printf("\n[TEST] Detect invalid self-signed certificate public key\n");

  cleanup_test_certs();
  secure_channel_cert_init();

  const secure_channel_cert_desc_t* desc = secure_channel_product_certs[0];
  if (desc == NULL) {
    printf("FAILED: No certificate descriptors available\n");
    return false;
  }

  secure_channel_cert_data_t cert_data = {0};
  if (!secure_channel_read_cert(desc->id, &cert_data)) {
    printf("FAILED: Could not read certificate\n");
    return false;
  }

  // Tamper with the public key (skip the 0x04 prefix)
  cert_data.data.picocert.public_key[10] ^= 0xFF;

  if (secure_channel_cert_verify_self_signed_picocert_signature(&cert_data.data.picocert)) {
    printf("FAILED: Tampered public key not detected\n");
    return false;
  }

  return true;
}

void secure_channel_cert_test(void) {
  printf("\n=== Secure Channel Certificate Test ===\n");

  bool result = true;
  // Run all tests in sequence
  result &= test_initialize_certificates();
  result &= test_get_local_certificate();
  result &= test_sign_and_verify();
  result &= test_tofu_pinning();
  result &= test_peer_signature_verification();
  result &= test_pin_null_cert();
  result &= test_pin_unsupported_type();
  result &= test_pin_already_exists();
  result &= test_verify_pin_exists();
  result &= test_verify_pin_not_exists();
  result &= test_verify_pin_mismatched();
  result &= test_verify_valid_self_signed_cert();
  result &= test_verify_invalid_self_signed_cert_signature();
  result &= test_verify_invalid_self_signed_cert_public_key();
  if (!result) {
    printf("FAILED: One or more tests failed\n");
  } else {
    printf("PASSED\n");
  }
}
