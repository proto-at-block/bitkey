#pragma once

/**
 * @file sim_crypto_utils.h
 * @brief Shared OpenSSL helper functions for core-sim
 *
 * Provides version-agnostic wrappers for common P-256 operations,
 * abstracting differences between OpenSSL 1.x and 3.x APIs.
 */

#include <openssl/bn.h>
#include <openssl/ec.h>
#include <openssl/ecdsa.h>
#include <openssl/evp.h>
#include <openssl/x509.h>

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#if OPENSSL_VERSION_NUMBER >= 0x30000000L
#include <openssl/core_names.h>
#include <openssl/param_build.h>
#endif

#define SIM_P256_PRIVKEY_SIZE             32
#define SIM_P256_PUBKEY_SIZE              64
#define SIM_P256_RAW_SIG_SIZE             64
#define SIM_P256_UNCOMPRESSED_PUBKEY_SIZE 65

/**
 * Create an EVP_PKEY from raw P-256 public key bytes.
 *
 * @param pubkey Raw public key (32-byte X || 32-byte Y)
 * @return New EVP_PKEY or NULL on failure. Caller must free with EVP_PKEY_free().
 */
static inline EVP_PKEY* sim_pkey_from_raw_pubkey(const uint8_t pubkey[SIM_P256_PUBKEY_SIZE]) {
  EVP_PKEY* pkey = NULL;

#if OPENSSL_VERSION_NUMBER >= 0x30000000L
  OSSL_PARAM_BLD* bld = OSSL_PARAM_BLD_new();
  if (!bld)
    return NULL;

  uint8_t uncompressed[SIM_P256_UNCOMPRESSED_PUBKEY_SIZE];
  uncompressed[0] = 0x04;
  memcpy(uncompressed + 1, pubkey, SIM_P256_PUBKEY_SIZE);

  OSSL_PARAM_BLD_push_utf8_string(bld, OSSL_PKEY_PARAM_GROUP_NAME, "prime256v1", 0);
  OSSL_PARAM_BLD_push_octet_string(bld, OSSL_PKEY_PARAM_PUB_KEY, uncompressed,
                                   sizeof(uncompressed));

  OSSL_PARAM* params = OSSL_PARAM_BLD_to_param(bld);
  OSSL_PARAM_BLD_free(bld);
  if (!params)
    return NULL;

  EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_from_name(NULL, "EC", NULL);
  if (ctx && EVP_PKEY_fromdata_init(ctx) > 0) {
    EVP_PKEY_fromdata(ctx, &pkey, EVP_PKEY_PUBLIC_KEY, params);
  }

  OSSL_PARAM_free(params);
  EVP_PKEY_CTX_free(ctx);
#else
  EC_KEY* ec = EC_KEY_new_by_curve_name(NID_X9_62_prime256v1);
  if (!ec)
    return NULL;

  const EC_GROUP* group = EC_KEY_get0_group(ec);
  EC_POINT* point = EC_POINT_new(group);
  BIGNUM* x = BN_bin2bn(pubkey, 32, NULL);
  BIGNUM* y = BN_bin2bn(pubkey + 32, 32, NULL);

  if (point && x && y && EC_POINT_set_affine_coordinates(group, point, x, y, NULL)) {
    EC_KEY_set_public_key(ec, point);
    pkey = EVP_PKEY_new();
    if (pkey)
      EVP_PKEY_set1_EC_KEY(pkey, ec);
  }

  EC_POINT_free(point);
  BN_free(x);
  BN_free(y);
  EC_KEY_free(ec);
#endif

  return pkey;
}

/**
 * Create an EVP_PKEY from raw P-256 keypair bytes.
 *
 * @param privkey Raw private key (32 bytes)
 * @param pubkey Raw public key (32-byte X || 32-byte Y)
 * @return New EVP_PKEY or NULL on failure. Caller must free with EVP_PKEY_free().
 */
static inline EVP_PKEY* sim_pkey_from_raw_keypair(const uint8_t privkey[SIM_P256_PRIVKEY_SIZE],
                                                  const uint8_t pubkey[SIM_P256_PUBKEY_SIZE]) {
  EVP_PKEY* pkey = NULL;

#if OPENSSL_VERSION_NUMBER >= 0x30000000L
  OSSL_PARAM_BLD* bld = OSSL_PARAM_BLD_new();
  if (!bld)
    return NULL;

  uint8_t uncompressed[SIM_P256_UNCOMPRESSED_PUBKEY_SIZE];
  uncompressed[0] = 0x04;
  memcpy(uncompressed + 1, pubkey, SIM_P256_PUBKEY_SIZE);

  BIGNUM* priv_bn = BN_bin2bn(privkey, SIM_P256_PRIVKEY_SIZE, NULL);
  if (!priv_bn) {
    OSSL_PARAM_BLD_free(bld);
    return NULL;
  }

  OSSL_PARAM_BLD_push_utf8_string(bld, OSSL_PKEY_PARAM_GROUP_NAME, "prime256v1", 0);
  OSSL_PARAM_BLD_push_octet_string(bld, OSSL_PKEY_PARAM_PUB_KEY, uncompressed,
                                   sizeof(uncompressed));
  OSSL_PARAM_BLD_push_BN(bld, OSSL_PKEY_PARAM_PRIV_KEY, priv_bn);

  OSSL_PARAM* params = OSSL_PARAM_BLD_to_param(bld);
  BN_free(priv_bn);
  OSSL_PARAM_BLD_free(bld);
  if (!params)
    return NULL;

  EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_from_name(NULL, "EC", NULL);
  if (ctx && EVP_PKEY_fromdata_init(ctx) > 0) {
    EVP_PKEY_fromdata(ctx, &pkey, EVP_PKEY_KEYPAIR, params);
  }

  OSSL_PARAM_free(params);
  EVP_PKEY_CTX_free(ctx);
#else
  EC_KEY* ec = EC_KEY_new_by_curve_name(NID_X9_62_prime256v1);
  if (!ec)
    return NULL;

  BIGNUM* priv_bn = BN_bin2bn(privkey, SIM_P256_PRIVKEY_SIZE, NULL);
  if (!priv_bn || !EC_KEY_set_private_key(ec, priv_bn)) {
    BN_free(priv_bn);
    EC_KEY_free(ec);
    return NULL;
  }
  BN_free(priv_bn);

  const EC_GROUP* group = EC_KEY_get0_group(ec);
  EC_POINT* point = EC_POINT_new(group);
  BIGNUM* x = BN_bin2bn(pubkey, 32, NULL);
  BIGNUM* y = BN_bin2bn(pubkey + 32, 32, NULL);

  if (point && x && y && EC_POINT_set_affine_coordinates(group, point, x, y, NULL)) {
    EC_KEY_set_public_key(ec, point);
    pkey = EVP_PKEY_new();
    if (pkey)
      EVP_PKEY_set1_EC_KEY(pkey, ec);
  }

  EC_POINT_free(point);
  BN_free(x);
  BN_free(y);
  EC_KEY_free(ec);
#endif

  return pkey;
}

/**
 * Extract raw public key bytes from EVP_PKEY.
 *
 * @param pkey Source key
 * @param out_pubkey Output buffer (64 bytes: 32-byte X || 32-byte Y)
 * @return true on success
 */
static inline bool sim_pkey_get_raw_pubkey(EVP_PKEY* pkey,
                                           uint8_t out_pubkey[SIM_P256_PUBKEY_SIZE]) {
#if OPENSSL_VERSION_NUMBER >= 0x30000000L
  uint8_t uncompressed[SIM_P256_UNCOMPRESSED_PUBKEY_SIZE];
  size_t len = sizeof(uncompressed);
  if (EVP_PKEY_get_octet_string_param(pkey, OSSL_PKEY_PARAM_PUB_KEY, uncompressed, len, &len) !=
      1) {
    return false;
  }
  memcpy(out_pubkey, uncompressed + 1, SIM_P256_PUBKEY_SIZE);
  return true;
#else
  const EC_KEY* ec = EVP_PKEY_get0_EC_KEY(pkey);
  if (!ec)
    return false;

  const EC_GROUP* group = EC_KEY_get0_group(ec);
  const EC_POINT* point = EC_KEY_get0_public_key(ec);
  if (!group || !point)
    return false;

  BIGNUM* x = BN_new();
  BIGNUM* y = BN_new();
  bool ok = false;

  if (x && y && EC_POINT_get_affine_coordinates(group, point, x, y, NULL)) {
    ok = (BN_bn2binpad(x, out_pubkey, 32) == 32 && BN_bn2binpad(y, out_pubkey + 32, 32) == 32);
  }

  BN_free(x);
  BN_free(y);
  return ok;
#endif
}

/**
 * Extract raw private key bytes from EVP_PKEY.
 *
 * @param pkey Source key
 * @param out_privkey Output buffer (32 bytes)
 * @return true on success
 */
static inline bool sim_pkey_get_raw_privkey(EVP_PKEY* pkey,
                                            uint8_t out_privkey[SIM_P256_PRIVKEY_SIZE]) {
  BIGNUM* priv_bn = NULL;
  bool ok = false;

#if OPENSSL_VERSION_NUMBER >= 0x30000000L
  if (EVP_PKEY_get_bn_param(pkey, OSSL_PKEY_PARAM_PRIV_KEY, &priv_bn) == 1) {
    ok = (BN_bn2binpad(priv_bn, out_privkey, SIM_P256_PRIVKEY_SIZE) == SIM_P256_PRIVKEY_SIZE);
  }
#else
  const EC_KEY* ec = EVP_PKEY_get0_EC_KEY(pkey);
  if (ec) {
    priv_bn = BN_dup(EC_KEY_get0_private_key(ec));
    if (priv_bn) {
      ok = (BN_bn2binpad(priv_bn, out_privkey, SIM_P256_PRIVKEY_SIZE) == SIM_P256_PRIVKEY_SIZE);
    }
  }
#endif

  BN_free(priv_bn);
  return ok;
}

/**
 * Convert raw R||S signature to ECDSA_SIG structure.
 *
 * @param raw_sig Raw signature (64 bytes: 32-byte R || 32-byte S)
 * @return New ECDSA_SIG or NULL on failure. Caller must free with ECDSA_SIG_free().
 */
static inline ECDSA_SIG* sim_sig_from_raw(const uint8_t raw_sig[SIM_P256_RAW_SIG_SIZE]) {
  ECDSA_SIG* sig = ECDSA_SIG_new();
  if (!sig)
    return NULL;

  BIGNUM* r = BN_bin2bn(raw_sig, 32, NULL);
  BIGNUM* s = BN_bin2bn(raw_sig + 32, 32, NULL);

  if (!r || !s || !ECDSA_SIG_set0(sig, r, s)) {
    BN_free(r);
    BN_free(s);
    ECDSA_SIG_free(sig);
    return NULL;
  }

  return sig;
}

/**
 * Convert ECDSA_SIG to raw R||S format.
 *
 * @param sig Source signature
 * @param out_raw Output buffer (64 bytes: 32-byte R || 32-byte S)
 * @return true on success
 */
static inline bool sim_sig_to_raw(const ECDSA_SIG* sig, uint8_t out_raw[SIM_P256_RAW_SIG_SIZE]) {
  const BIGNUM* r = NULL;
  const BIGNUM* s = NULL;
  ECDSA_SIG_get0(sig, &r, &s);

  return (BN_bn2binpad(r, out_raw, 32) == 32 && BN_bn2binpad(s, out_raw + 32, 32) == 32);
}

/**
 * Verify a raw R||S signature against a digest using public key.
 *
 * @param pubkey Raw public key (64 bytes)
 * @param digest Message digest
 * @param digest_len Length of digest
 * @param sig Raw signature (64 bytes)
 * @return true if signature is valid
 */
static inline bool sim_verify_raw_signature(const uint8_t pubkey[SIM_P256_PUBKEY_SIZE],
                                            const uint8_t* digest, size_t digest_len,
                                            const uint8_t sig[SIM_P256_RAW_SIG_SIZE]) {
  EVP_PKEY* pkey = sim_pkey_from_raw_pubkey(pubkey);
  if (!pkey)
    return false;

  ECDSA_SIG* ecdsa_sig = sim_sig_from_raw(sig);
  if (!ecdsa_sig) {
    EVP_PKEY_free(pkey);
    return false;
  }

  bool result = false;

#if OPENSSL_VERSION_NUMBER >= 0x30000000L
  uint8_t* der_sig = NULL;
  int der_len = i2d_ECDSA_SIG(ecdsa_sig, &der_sig);
  if (der_len > 0 && der_sig) {
    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new(pkey, NULL);
    if (ctx && EVP_PKEY_verify_init(ctx) > 0) {
      result = (EVP_PKEY_verify(ctx, der_sig, der_len, digest, digest_len) == 1);
    }
    EVP_PKEY_CTX_free(ctx);
    OPENSSL_free(der_sig);
  }
#else
  EC_KEY* ec = (EC_KEY*)EVP_PKEY_get0_EC_KEY(pkey);
  if (ec) {
    result = (ECDSA_do_verify(digest, (int)digest_len, ecdsa_sig, ec) == 1);
  }
#endif

  ECDSA_SIG_free(ecdsa_sig);
  EVP_PKEY_free(pkey);
  return result;
}
