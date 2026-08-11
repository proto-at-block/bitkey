/**
 * @file sim_provisioning.c
 * @brief Runtime provisioning for core-sim attestation testing
 *
 * When CORE_SIM_PROVISION=1 environment variable is set, the simulator will
 * generate a device identity at startup (P-256 keypair, serial number, and
 * X.509 certificate signed by the dev batch key).
 *
 * Device identity is persisted to disk and restored on subsequent runs,
 * eliminating the need to re-provision after each restart.
 */

#include "sim_provisioning.h"

#include "sim_crypto_utils.h"
#include "sim_dev_certs.h"
#include "sim_persistence.h"

#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/rand.h>
#include <openssl/x509v3.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SERIAL_SIZE             8
#define PROVISION_STATE_VERSION 1
#define PROVISION_STATE_FILE    "provision_state.bin"

// Persistent state structure for device identity
typedef struct __attribute__((packed)) {
  uint8_t version;
  uint8_t device_privkey[SIM_P256_PRIVKEY_SIZE];  // 32 bytes
  uint8_t device_pubkey[SIM_P256_PUBKEY_SIZE];    // 65 bytes
  uint8_t serial[SERIAL_SIZE];                    // 8 bytes
  uint16_t device_cert_len;
  uint8_t device_cert[SIM_CERT_MAX_SIZE];
} sim_provision_state_t;

// Provisioned state
static bool g_provisioned = false;
static uint8_t g_device_privkey[SIM_P256_PRIVKEY_SIZE];
static uint8_t g_device_pubkey[SIM_P256_PUBKEY_SIZE];
static uint8_t g_serial[SERIAL_SIZE];
static uint8_t g_device_cert[SIM_CERT_MAX_SIZE];
static size_t g_device_cert_len = 0;

// Load provisioning state from persistent storage
static bool load_provision_state(void) {
  sim_provision_state_t state;

  if (!sim_persistence_load(PROVISION_STATE_FILE, &state, sizeof(state))) {
    return false;
  }

  // Validate version
  if (state.version != PROVISION_STATE_VERSION) {
    fprintf(stderr, "[sim_provisioning] Ignoring saved state with version %u (expected %u)\n",
            state.version, PROVISION_STATE_VERSION);
    return false;
  }

  // Validate cert length
  if (state.device_cert_len == 0 || state.device_cert_len > SIM_CERT_MAX_SIZE) {
    fprintf(stderr, "[sim_provisioning] Invalid cert length in saved state: %u\n",
            state.device_cert_len);
    return false;
  }

  // Restore state
  memcpy(g_device_privkey, state.device_privkey, sizeof(g_device_privkey));
  memcpy(g_device_pubkey, state.device_pubkey, sizeof(g_device_pubkey));
  memcpy(g_serial, state.serial, sizeof(g_serial));
  memcpy(g_device_cert, state.device_cert, state.device_cert_len);
  g_device_cert_len = state.device_cert_len;
  g_provisioned = true;

  return true;
}

// Save provisioning state to persistent storage
static bool save_provision_state(void) {
  sim_provision_state_t state = {
    .version = PROVISION_STATE_VERSION,
    .device_cert_len = (uint16_t)g_device_cert_len,
  };

  memcpy(state.device_privkey, g_device_privkey, sizeof(state.device_privkey));
  memcpy(state.device_pubkey, g_device_pubkey, sizeof(state.device_pubkey));
  memcpy(state.serial, g_serial, sizeof(state.serial));
  memcpy(state.device_cert, g_device_cert, g_device_cert_len);

  return sim_persistence_save(PROVISION_STATE_FILE, &state, sizeof(state));
}

static void print_serial(void) {
  fprintf(stderr, "[sim_provisioning] Serial: ");
  for (int i = 0; i < SERIAL_SIZE; i++) {
    fprintf(stderr, "%02X", g_serial[i]);
  }
  fprintf(stderr, "\n");
}

static bool generate_device_keypair(void) {
  EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_EC, NULL);
  EVP_PKEY* pkey = NULL;
  bool success = false;

  if (!ctx)
    return false;

  if (EVP_PKEY_keygen_init(ctx) <= 0 ||
      EVP_PKEY_CTX_set_ec_paramgen_curve_nid(ctx, NID_X9_62_prime256v1) <= 0 ||
      EVP_PKEY_keygen(ctx, &pkey) <= 0) {
    goto cleanup;
  }

  success = sim_pkey_get_raw_privkey(pkey, g_device_privkey) &&
            sim_pkey_get_raw_pubkey(pkey, g_device_pubkey);

cleanup:
  EVP_PKEY_CTX_free(ctx);
  EVP_PKEY_free(pkey);
  return success;
}

static EVP_PKEY* load_batch_signing_key(void) {
  BIO* bio = BIO_new_mem_buf(dev_batch_key_pem, (int)dev_batch_key_pem_len);
  if (!bio)
    return NULL;

  EVP_PKEY* key = PEM_read_bio_PrivateKey(bio, NULL, NULL, NULL);
  BIO_free(bio);
  return key;
}

static bool add_cert_extension(X509* cert, X509* issuer, int nid, const char* value) {
  X509V3_CTX ctx;
  X509V3_set_ctx_nodb(&ctx);
  X509V3_set_ctx(&ctx, issuer, cert, NULL, NULL, 0);

  X509_EXTENSION* ext = X509V3_EXT_conf_nid(NULL, &ctx, nid, value);
  if (!ext)
    return false;

  X509_add_ext(cert, ext, -1);
  X509_EXTENSION_free(ext);
  return true;
}

static bool generate_device_cert(void) {
  X509* cert = NULL;
  X509* batch_cert = NULL;
  EVP_PKEY* device_pkey = NULL;
  EVP_PKEY* batch_pkey = NULL;
  X509_NAME* subject = NULL;
  bool success = false;

  // Load batch signing key
  batch_pkey = load_batch_signing_key();
  if (!batch_pkey) {
    fprintf(stderr, "[sim_provisioning] Failed to load batch signing key\n");
    goto cleanup;
  }

  // Parse batch cert for issuer name
  const uint8_t* p = dev_batch_cert_der;
  batch_cert = d2i_X509(NULL, &p, (long)dev_batch_cert_der_len);
  if (!batch_cert) {
    fprintf(stderr, "[sim_provisioning] Failed to parse batch certificate\n");
    goto cleanup;
  }

  // Create device key from raw bytes
  device_pkey = sim_pkey_from_raw_keypair(g_device_privkey, g_device_pubkey);
  if (!device_pkey) {
    fprintf(stderr, "[sim_provisioning] Failed to create device key\n");
    goto cleanup;
  }

  // Create certificate
  cert = X509_new();
  if (!cert)
    goto cleanup;

  // Version 3
  X509_set_version(cert, 2);

  // Random serial number
  ASN1_INTEGER* serial_asn1 = ASN1_INTEGER_new();
  BIGNUM* serial_bn = BN_new();
  BN_rand(serial_bn, 64, BN_RAND_TOP_ANY, BN_RAND_BOTTOM_ANY);
  BN_to_ASN1_INTEGER(serial_bn, serial_asn1);
  X509_set_serialNumber(cert, serial_asn1);
  ASN1_INTEGER_free(serial_asn1);
  BN_free(serial_bn);

  // Validity: now to +100 years
  X509_gmtime_adj(X509_getm_notBefore(cert), 0);
  X509_gmtime_adj(X509_getm_notAfter(cert), 60L * 60 * 24 * 365 * 100);

  // Subject: CN=Block Inc EUI:<serial> S:SE ID:MCU
  char cn[128];
  snprintf(cn, sizeof(cn), "Block Inc EUI:%02X%02X%02X%02X%02X%02X%02X%02X S:SE ID:MCU",
           g_serial[0], g_serial[1], g_serial[2], g_serial[3], g_serial[4], g_serial[5],
           g_serial[6], g_serial[7]);

  subject = X509_NAME_new();
  X509_NAME_add_entry_by_txt(subject, "C", MBSTRING_ASC, (unsigned char*)"US", -1, -1, 0);
  X509_NAME_add_entry_by_txt(subject, "O", MBSTRING_ASC, (unsigned char*)"Block Inc", -1, -1, 0);
  X509_NAME_add_entry_by_txt(subject, "CN", MBSTRING_ASC, (unsigned char*)cn, -1, -1, 0);
  X509_set_subject_name(cert, subject);

  // Issuer from batch cert
  X509_set_issuer_name(cert, X509_get_subject_name(batch_cert));

  // Public key
  X509_set_pubkey(cert, device_pkey);

  // Extensions (all critical for attestation validation)
  add_cert_extension(cert, batch_cert, NID_basic_constraints, "critical,CA:FALSE");
  add_cert_extension(cert, batch_cert, NID_key_usage, "critical,digitalSignature,nonRepudiation");
  add_cert_extension(cert, batch_cert, NID_ext_key_usage, "critical,clientAuth");

  // Sign with batch key
  if (!X509_sign(cert, batch_pkey, EVP_sha256())) {
    fprintf(stderr, "[sim_provisioning] Failed to sign certificate\n");
    goto cleanup;
  }

  // Convert to DER
  int der_len = i2d_X509(cert, NULL);
  if (der_len <= 0 || der_len > SIM_CERT_MAX_SIZE) {
    fprintf(stderr, "[sim_provisioning] Certificate too large or encoding failed\n");
    goto cleanup;
  }

  uint8_t* der_ptr = g_device_cert;
  i2d_X509(cert, &der_ptr);
  g_device_cert_len = (size_t)der_len;
  success = true;

cleanup:
  X509_free(cert);
  X509_free(batch_cert);
  EVP_PKEY_free(device_pkey);
  EVP_PKEY_free(batch_pkey);
  X509_NAME_free(subject);
  return success;
}

bool sim_provision_init(void) {
  // Try to restore saved provisioning state first (only if persistence is enabled)
  if (sim_persistence_enabled() && load_provision_state()) {
    fprintf(stderr, "[sim_provisioning] Restored device identity from disk\n");
    print_serial();
    fprintf(stderr, "[sim_provisioning] Device cert size: %zu bytes\n", g_device_cert_len);
    return true;
  }

  // No saved state - check if we should generate new identity
  const char* env = getenv("CORE_SIM_PROVISION");
  if (!env || strcmp(env, "1") != 0) {
    fprintf(stderr, "[sim_provisioning] No saved identity and provisioning disabled\n");
    fprintf(stderr, "[sim_provisioning] Set CORE_SIM_PROVISION=1 to generate new identity\n");
    return false;
  }

  fprintf(stderr, "[sim_provisioning] Generating new device identity...\n");

  if (RAND_bytes(g_serial, SERIAL_SIZE) != 1) {
    fprintf(stderr, "[sim_provisioning] Failed to generate random serial\n");
    return false;
  }
  print_serial();

  if (!generate_device_keypair()) {
    fprintf(stderr, "[sim_provisioning] Failed to generate device keypair\n");
    return false;
  }

  if (!generate_device_cert()) {
    fprintf(stderr, "[sim_provisioning] Failed to generate device certificate\n");
    return false;
  }

  g_provisioned = true;

  // Save newly generated state (only if persistence is enabled)
  if (sim_persistence_enabled()) {
    if (save_provision_state()) {
      fprintf(stderr, "[sim_provisioning] Saved device identity to disk\n");
    } else {
      fprintf(stderr, "[sim_provisioning] Warning: Failed to save device identity\n");
    }
  }

  fprintf(stderr, "[sim_provisioning] Provisioning complete. Device cert size: %zu bytes\n",
          g_device_cert_len);
  return true;
}

bool sim_is_provisioned(void) {
  return g_provisioned;
}

const uint8_t* sim_get_device_cert(size_t* out_len) {
  if (!g_provisioned)
    return NULL;
  if (out_len)
    *out_len = g_device_cert_len;
  return g_device_cert;
}

const uint8_t* sim_get_batch_cert(size_t* out_len) {
  if (out_len)
    *out_len = dev_batch_cert_der_len;
  return dev_batch_cert_der;
}

const uint8_t* sim_get_factory_cert(size_t* out_len) {
  if (out_len)
    *out_len = dev_factory_cert_der_len;
  return dev_factory_cert_der;
}

const uint8_t* sim_get_root_cert(size_t* out_len) {
  if (out_len)
    *out_len = dev_root_cert_der_len;
  return dev_root_cert_der;
}

const uint8_t* sim_get_device_pubkey(void) {
  return g_provisioned ? g_device_pubkey : NULL;
}

const uint8_t* sim_get_device_privkey(void) {
  return g_provisioned ? g_device_privkey : NULL;
}

const uint8_t* sim_get_serial(void) {
  return g_provisioned ? g_serial : NULL;
}

size_t sim_sign_with_device_key(const uint8_t* digest, size_t digest_len, uint8_t* sig_out,
                                size_t sig_max_len) {
  if (!g_provisioned || !digest || !sig_out || sig_max_len < SIM_P256_RAW_SIG_SIZE) {
    return 0;
  }

  EVP_PKEY* pkey = sim_pkey_from_raw_keypair(g_device_privkey, g_device_pubkey);
  if (!pkey)
    return 0;

  size_t result = 0;

#if OPENSSL_VERSION_NUMBER >= 0x30000000L
  EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new(pkey, NULL);
  if (!ctx || EVP_PKEY_sign_init(ctx) <= 0) {
    EVP_PKEY_CTX_free(ctx);
    EVP_PKEY_free(pkey);
    return 0;
  }

  // Get DER signature, then convert to raw
  size_t der_len = 0;
  EVP_PKEY_sign(ctx, NULL, &der_len, digest, digest_len);

  uint8_t* der_sig = malloc(der_len);
  if (der_sig && EVP_PKEY_sign(ctx, der_sig, &der_len, digest, digest_len) > 0) {
    const uint8_t* der_ptr = der_sig;
    ECDSA_SIG* sig = d2i_ECDSA_SIG(NULL, &der_ptr, (long)der_len);
    if (sig && sim_sig_to_raw(sig, sig_out)) {
      result = SIM_P256_RAW_SIG_SIZE;
    }
    ECDSA_SIG_free(sig);
  }
  free(der_sig);
  EVP_PKEY_CTX_free(ctx);
#else
  EC_KEY* ec = (EC_KEY*)EVP_PKEY_get0_EC_KEY(pkey);
  if (ec) {
    ECDSA_SIG* sig = ECDSA_do_sign(digest, (int)digest_len, ec);
    if (sig && sim_sig_to_raw(sig, sig_out)) {
      result = SIM_P256_RAW_SIG_SIZE;
    }
    ECDSA_SIG_free(sig);
  }
#endif

  EVP_PKEY_free(pkey);
  return result;
}

bool sim_provision_wipe(void) {
  // Clear in-memory state
  memset(g_device_privkey, 0, sizeof(g_device_privkey));
  memset(g_device_pubkey, 0, sizeof(g_device_pubkey));
  memset(g_serial, 0, sizeof(g_serial));
  memset(g_device_cert, 0, sizeof(g_device_cert));
  g_device_cert_len = 0;
  g_provisioned = false;

  // Delete persisted state (only if persistence is enabled)
  if (sim_persistence_enabled()) {
    if (!sim_persistence_delete(PROVISION_STATE_FILE)) {
      fprintf(stderr, "[sim_provisioning] Warning: Failed to delete persisted state\n");
      return false;
    }
    fprintf(stderr, "[sim_provisioning] Device identity wiped\n");
  }

  return true;
}
