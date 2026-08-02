/**
 * @file test_sim_provisioning.c
 * @brief Unit tests for sim_provisioning signature and certificate format validation
 *
 * These tests verify that:
 * 1. Signatures are exactly 64 bytes (raw R||S format, NOT DER-encoded)
 * 2. Certificates have correct extensions (BC, KU, EKU - all critical)
 * 3. Certificate CN matches format: "Block Inc EUI:<16-hex> S:SE ID:MCU"
 * 4. Certificate chain verifies correctly
 */

#include "sim_crypto_utils.h"
#include "sim_provisioning.h"

#include <openssl/evp.h>
#include <openssl/x509.h>
#include <openssl/x509v3.h>

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Compute SHA-256 digest using modern EVP API
static void compute_sha256(uint8_t out[32], const void* data1, size_t len1, const void* data2,
                           size_t len2) {
  EVP_MD_CTX* ctx = EVP_MD_CTX_new();
  EVP_DigestInit_ex(ctx, EVP_sha256(), NULL);
  EVP_DigestUpdate(ctx, data1, len1);
  if (data2 && len2 > 0) {
    EVP_DigestUpdate(ctx, data2, len2);
  }
  EVP_DigestFinal_ex(ctx, out, NULL);
  EVP_MD_CTX_free(ctx);
}

static void test_signature_format(void) {
  printf("Testing signature format...\n");

  assert(sim_is_provisioned());

  // Test challenge
  uint8_t challenge[16] = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                           0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};

  // Compute digest: SHA256("ATV1" || challenge)
  uint8_t digest[32];
  compute_sha256(digest, "ATV1", 4, challenge, 16);

  // Sign
  uint8_t signature[SIM_P256_RAW_SIG_SIZE];
  size_t sig_len = sim_sign_with_device_key(digest, 32, signature, sizeof(signature));

  // Signature MUST be exactly 64 bytes
  assert(sig_len == SIM_P256_RAW_SIG_SIZE);
  printf("  [PASS] Signature is exactly 64 bytes\n");

  // Signature must NOT be DER-encoded (DER starts with 0x30)
  assert(signature[0] != 0x30);
  printf("  [PASS] Signature is NOT DER-encoded (doesn't start with 0x30)\n");

  // Verify signature with our pubkey
  const uint8_t* pubkey = sim_get_device_pubkey();
  assert(pubkey != NULL);
  assert(sim_verify_raw_signature(pubkey, digest, 32, signature));
  printf("  [PASS] Signature verifies with device public key\n");

  printf("  Signature format test PASSED\n");
}

static void test_certificate_format(void) {
  printf("Testing certificate format...\n");

  assert(sim_is_provisioned());

  size_t cert_len = 0;
  const uint8_t* cert_der = sim_get_device_cert(&cert_len);
  assert(cert_der != NULL);
  assert(cert_len > 0 && cert_len <= SIM_CERT_MAX_SIZE);
  printf("  [PASS] Certificate DER retrieved (%zu bytes)\n", cert_len);

  // Parse certificate
  const uint8_t* p = cert_der;
  X509* cert = d2i_X509(NULL, &p, (long)cert_len);
  assert(cert != NULL);
  printf("  [PASS] Certificate is valid X.509 DER\n");

  // Check subject Organization
  X509_NAME* subject = X509_get_subject_name(cert);
  char org[256] = {0};
  X509_NAME_get_text_by_NID(subject, NID_organizationName, org, sizeof(org));
  assert(strcmp(org, "Block Inc") == 0);
  printf("  [PASS] Organization is 'Block Inc'\n");

  // Check CN contains required strings
  char cn[256] = {0};
  X509_NAME_get_text_by_NID(subject, NID_commonName, cn, sizeof(cn));
  assert(strstr(cn, "Block Inc") != NULL);
  assert(strstr(cn, "EUI:") != NULL);
  assert(strstr(cn, "ID:MCU") != NULL);
  printf("  [PASS] CN contains 'Block Inc', 'EUI:', and 'ID:MCU'\n");

  // Check EUI is 16 hex characters
  char* eui_start = strstr(cn, "EUI:") + 4;
  for (int i = 0; i < 16; i++) {
    char c = eui_start[i];
    assert((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'));
  }
  printf("  [PASS] EUI is 16 uppercase hex characters\n");

  // Check extensions are present and critical
  int ext_nids[] = {NID_basic_constraints, NID_key_usage, NID_ext_key_usage};
  const char* ext_names[] = {"Basic Constraints", "Key Usage", "Extended Key Usage"};
  for (int i = 0; i < 3; i++) {
    int idx = X509_get_ext_by_NID(cert, ext_nids[i], -1);
    assert(idx >= 0);
    X509_EXTENSION* ext = X509_get_ext(cert, idx);
    assert(X509_EXTENSION_get_critical(ext) == 1);
    printf("  [PASS] %s extension present and critical\n", ext_names[i]);
  }

  // Check public key matches device pubkey
  EVP_PKEY* cert_pkey = X509_get_pubkey(cert);
  assert(cert_pkey != NULL);

  uint8_t cert_pubkey[SIM_P256_PUBKEY_SIZE];
  assert(sim_pkey_get_raw_pubkey(cert_pkey, cert_pubkey));

  const uint8_t* device_pubkey = sim_get_device_pubkey();
  assert(memcmp(cert_pubkey, device_pubkey, SIM_P256_PUBKEY_SIZE) == 0);
  printf("  [PASS] Certificate public key matches device public key\n");

  EVP_PKEY_free(cert_pkey);
  X509_free(cert);

  printf("  Certificate format test PASSED\n");
}

static void test_cert_chain_verification(void) {
  printf("Testing certificate chain...\n");

  // Get all certs in the chain
  size_t device_len, batch_len, factory_len, root_len;
  const uint8_t* device_der = sim_get_device_cert(&device_len);
  const uint8_t* batch_der = sim_get_batch_cert(&batch_len);
  const uint8_t* factory_der = sim_get_factory_cert(&factory_len);
  const uint8_t* root_der = sim_get_root_cert(&root_len);

  assert(device_der && batch_der && factory_der && root_der);

  // Parse all certs
  const uint8_t* p;
  p = device_der;
  X509* device = d2i_X509(NULL, &p, (long)device_len);
  p = batch_der;
  X509* batch = d2i_X509(NULL, &p, (long)batch_len);
  p = factory_der;
  X509* factory = d2i_X509(NULL, &p, (long)factory_len);
  p = root_der;
  X509* root = d2i_X509(NULL, &p, (long)root_len);

  assert(device && batch && factory && root);
  printf("  [PASS] All 4 certificates parsed successfully\n");

  // Verify chain: device -> batch -> factory -> root (self-signed)
  EVP_PKEY* batch_pkey = X509_get_pubkey(batch);
  EVP_PKEY* factory_pkey = X509_get_pubkey(factory);
  EVP_PKEY* root_pkey = X509_get_pubkey(root);

  assert(X509_verify(device, batch_pkey) == 1);
  printf("  [PASS] Device cert signed by Batch\n");

  assert(X509_verify(batch, factory_pkey) == 1);
  printf("  [PASS] Batch cert signed by Factory\n");

  assert(X509_verify(factory, root_pkey) == 1);
  printf("  [PASS] Factory cert signed by Root\n");

  assert(X509_verify(root, root_pkey) == 1);
  printf("  [PASS] Root cert is self-signed\n");

  EVP_PKEY_free(batch_pkey);
  EVP_PKEY_free(factory_pkey);
  EVP_PKEY_free(root_pkey);
  X509_free(device);
  X509_free(batch);
  X509_free(factory);
  X509_free(root);

  printf("  Certificate chain test PASSED\n");
}

int main(int argc, char* argv[]) {
  (void)argc;
  (void)argv;

  printf("=== sim_provisioning Unit Tests ===\n\n");

  // Force provisioning for tests
  setenv("CORE_SIM_PROVISION", "1", 1);

  if (!sim_provision_init()) {
    fprintf(stderr, "FAILED: Could not initialize provisioning\n");
    return 1;
  }
  printf("\n");

  test_signature_format();
  printf("\n");

  test_certificate_format();
  printf("\n");

  test_cert_chain_verification();
  printf("\n");

  printf("=== ALL TESTS PASSED ===\n");
  return 0;
}
