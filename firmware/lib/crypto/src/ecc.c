#include "ecc.h"

#include "assert.h"
#include "hash.h"
#include "hex.h"
#include "secp256k1.h"
#include "secp256k1_extrakeys.h"
#include "secp256k1_preallocated.h"
#include "secp256k1_schnorrsig.h"
#include "secure_rng.h"
#include "secutils.h"

#include <string.h>

#ifdef EMBEDDED_BUILD
#include "rtos_mutex.h"
#include "secure_engine.h"

#define CTX_SIZE (180u)
static rtos_mutex_t ctx_lock;

#else
#define rtos_mutex_create(unused)
#define rtos_mutex_lock(unused)
#define rtos_mutex_unlock(unused)
#define CTX_SIZE (1024u)
#endif

#define KEY_SIZE (32u)

uint8_t ctx_buf[CTX_SIZE] = {0};
static secp256k1_context* ctx = NULL;
#define ENSURE_CTX() ASSERT(ctx != NULL)

void crypto_ecc_secp256k1_init(void) {
  if (ctx != NULL) {
    return;
  }
  ctx = secp256k1_context_preallocated_create(&ctx_buf[0],
                                              SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);
  rtos_mutex_create(&ctx_lock);
}

bool crypto_ecc_secp256k1_generate_keypair(key_handle_t* key) {
  ASSERT(key && key->key.bytes && (key->key.size == SECP256K1_KEYPAIR_SIZE));
  ENSURE_CTX();

  uint8_t secret_key[32];
  secp256k1_keypair* keypair = (secp256k1_keypair*)key->key.bytes;

  for (;;) {  // Ensure key is not 0 or out of range.
    if (!crypto_random(secret_key, sizeof(secret_key))) {
      return false;
    }

    if (secp256k1_keypair_create(ctx, keypair, secret_key)) {
      break;
    }
  }

  memzero(secret_key, sizeof(secret_key));
  return true;
}

bool crypto_ecc_secp256k1_load_keypair(uint8_t privkey[SECP256K1_KEY_SIZE], key_handle_t* key) {
  ASSERT(privkey && key);
  ENSURE_CTX();
  secp256k1_keypair* keypair = (secp256k1_keypair*)key->key.bytes;
  return secp256k1_keypair_create(ctx, keypair, privkey);
}

bool crypto_ecc_secp256k1_priv_verify(const uint8_t privkey[SECP256K1_KEY_SIZE]) {
  ASSERT(privkey);
  ENSURE_CTX();
  return secp256k1_ec_seckey_verify(ctx, privkey);
}

bool crypto_ecc_secp256k1_schnorr_sign_hash32(key_handle_t* key, uint8_t* hash, uint8_t* signature,
                                              uint32_t signature_size) {
  ASSERT(key && hash && signature && (signature_size == ECC_SIG_SIZE));
  ENSURE_CTX();

  rtos_mutex_lock(&ctx_lock);
  bool status = false;

  uint8_t blinding_seed[32];
  if (!crypto_random(&blinding_seed[0], 32)) {
    goto out;
  }
  if (!secp256k1_context_randomize(ctx, blinding_seed)) {
    goto out;
  }

  secp256k1_keypair* keypair = (secp256k1_keypair*)key->key.bytes;

  // BIP-340
  uint8_t auxiliary_rand[32];
  if (!crypto_random(&auxiliary_rand[0], 32)) {
    goto out;
  }
  if (!secp256k1_schnorrsig_sign32(ctx, signature, hash, keypair, auxiliary_rand)) {
    goto out;
  }

  // Verify after signing to prevent using a corrupted signature, which can leak the private key.
  // https://github.com/bitcoin/bips/blob/master/bip-0340.mediawiki#cite_note-14
  secp256k1_xonly_pubkey pubkey;
  if (!secp256k1_keypair_xonly_pub(ctx, &pubkey, NULL, keypair)) {
    goto out;
  }
  bool verify_result =
    (bool)secp256k1_schnorrsig_verify(ctx, signature, hash, SHA256_DIGEST_SIZE, &pubkey);
  if (!verify_result) {
    goto out;
  }

  status = true;

out:
  rtos_mutex_unlock(&ctx_lock);
  if (!status) {
    memzero(signature, signature_size);
    memzero(key->key.bytes, key->key.size);
  }
  return status;
}

bool crypto_ecc_secp256k1_schnorr_verify_hash32(key_handle_t* key, uint8_t* hash,
                                                uint8_t* signature, uint32_t signature_size,
                                                bool* verify_result) {
  ASSERT(key && hash && signature && (signature_size == ECC_SIG_SIZE));
  ENSURE_CTX();

  rtos_mutex_lock(&ctx_lock);
  bool status = false;

  *verify_result = false;

  secp256k1_xonly_pubkey pubkey;
  secp256k1_keypair* keypair = (secp256k1_keypair*)key->key.bytes;
  if (!secp256k1_keypair_xonly_pub(ctx, &pubkey, NULL, keypair)) {
    goto out;
  }

  *verify_result =
    (bool)secp256k1_schnorrsig_verify(ctx, signature, hash, SHA256_DIGEST_SIZE, &pubkey);
  status = true;

out:
  rtos_mutex_unlock(&ctx_lock);
  if (!status) {
    memzero(signature, signature_size);
    memzero(key->key.bytes, key->key.size);
  }
  return status;
}

// TODO: Factor out common code with Schnorr / ECDSA.
NO_OPTIMIZE bool crypto_ecc_secp256k1_ecdsa_sign_hash32(key_handle_t* privkey, uint8_t* hash,
                                                        uint8_t* signature,
                                                        uint32_t signature_size) {
  ASSERT(privkey && hash && signature && (signature_size == ECC_SIG_SIZE));
  ENSURE_CTX();

  rtos_mutex_lock(&ctx_lock);
  bool status = false;
  volatile secure_bool_t sign_ok = SECURE_FALSE;

  uint8_t blinding_seed[32];
  if (!crypto_random(&blinding_seed[0], 32)) {
    goto out;
  }
  if (!secp256k1_context_randomize(ctx, blinding_seed)) {
    goto out;
  }

  secp256k1_ecdsa_signature sig;
  SECURE_DO_ONCE({
    if (secp256k1_ecdsa_sign(ctx, &sig, hash, privkey->key.bytes, NULL, NULL)) {
      sign_ok = SECURE_TRUE;
    }
  });

  // Warning: Compiler may optimize the redundancy away.
  SECURE_IF_FAILIN(sign_ok != SECURE_TRUE) { goto out; }

  secp256k1_ecdsa_signature_serialize_compact(ctx, signature, &sig);

  status = true;

out:
  rtos_mutex_unlock(&ctx_lock);

  if (!status) {
    memzero(signature, signature_size);
    memzero(privkey->key.bytes, privkey->key.size);
  }

  SECURE_IF_FAILIN((sign_ok != SECURE_TRUE)) {
    memzero(signature, signature_size);
    memzero(privkey->key.bytes, privkey->key.size);
  }

  return status;
}

bool crypto_ecc_secp256k1_schnorr_sign(key_handle_t* key, uint8_t* message, uint32_t message_size,
                                       uint8_t* signature, uint32_t signature_size) {
  ASSERT(key && message && signature && (signature_size == ECC_SIG_SIZE));

  uint8_t message_digest[SHA256_DIGEST_SIZE];
  uint32_t digest_size = sizeof(message_digest);
  if (!crypto_hash(message, message_size, &message_digest[0], digest_size, ALG_SHA256)) {
    return false;
  }

  return crypto_ecc_secp256k1_schnorr_sign_hash32(key, message_digest, signature, signature_size);
}

bool crypto_ecc_secp256k1_schnorr_verify(key_handle_t* key, uint8_t* message, uint32_t message_size,
                                         uint8_t* signature, uint32_t signature_size,
                                         bool* verify_result) {
  ASSERT(key && message && signature && (signature_size == ECC_SIG_SIZE));

  uint8_t message_digest[SHA256_DIGEST_SIZE];
  uint32_t digest_size = sizeof(message_digest);
  if (!crypto_hash(message, message_size, &message_digest[0], digest_size, ALG_SHA256)) {
    return false;
  }

  return crypto_ecc_secp256k1_schnorr_verify_hash32(key, message_digest, signature, signature_size,
                                                    verify_result);
}

bool crypto_ecc_secp256k1_priv_to_xonly_pub(uint8_t privkey[SECP256K1_KEY_SIZE],
                                            uint8_t pubkey_out[SECP256K1_KEY_SIZE],
                                            bool* y_is_even) {
  ASSERT(privkey && pubkey_out);
  ENSURE_CTX();

  bool status = false;
  rtos_mutex_lock(&ctx_lock);

  secp256k1_pubkey pubkey;
  if (!secp256k1_ec_pubkey_create(ctx, &pubkey, privkey)) {
    goto out;
  }

  secp256k1_xonly_pubkey pubkey_xonly;
  int parity = 2;
  if (!secp256k1_xonly_pubkey_from_pubkey(ctx, &pubkey_xonly, &parity, &pubkey)) {
    goto out;
  }

  ASSERT(parity == 0 || parity == 1);
  // See secp256k1_extrakeys_ge_even_y(), which states:
  // "Keeps a group element as is if it has an even Y and otherwise negates it. y_parity is set to 0
  // in the former case and to 1 in the latter case."
  *y_is_even = (parity == 0);

  if (!secp256k1_xonly_pubkey_serialize(ctx, pubkey_out, &pubkey_xonly)) {
    goto out;
  }

  status = true;

out:
  rtos_mutex_unlock(&ctx_lock);
  if (!status) {
    memzero(privkey, KEY_SIZE);
  }
  return status;
}

bool crypto_ecc_secp256k1_priv_to_sec_encoded_pub(
  uint8_t privkey[SECP256K1_KEY_SIZE], uint8_t sec_encoded_pubkey_out[SECP256K1_SEC1_KEY_SIZE]) {
  ASSERT(privkey && sec_encoded_pubkey_out);
  bool y_is_even;
  if (!crypto_ecc_secp256k1_priv_to_xonly_pub(privkey, &sec_encoded_pubkey_out[1], &y_is_even)) {
    return false;
  }
  sec_encoded_pubkey_out[0] = y_is_even ? 0x02 : 0x03;
  return true;
}

bool crypto_ecc_secp256k1_priv_tweak_add(uint8_t privkey[SECP256K1_KEY_SIZE], uint8_t* tweak) {
  ASSERT(privkey && tweak);
  rtos_mutex_lock(&ctx_lock);
  bool ret = (secp256k1_ec_seckey_tweak_add(ctx, privkey, tweak) == 1);
  rtos_mutex_unlock(&ctx_lock);
  return ret;
}

bool crypto_ecc_secp256k1_pub_tweak_add(uint8_t sec_encoded_pubkey[SECP256K1_SEC1_KEY_SIZE],
                                        uint8_t tweak[SECP256K1_KEY_SIZE]) {
  ASSERT(sec_encoded_pubkey && tweak);
  rtos_mutex_lock(&ctx_lock);

  bool status = false;
  secp256k1_pubkey pubkey;
  if (secp256k1_ec_pubkey_parse(ctx, &pubkey, sec_encoded_pubkey, SECP256K1_SEC1_KEY_SIZE) != 1) {
    goto out;
  }

  if (!secp256k1_ec_pubkey_tweak_add(ctx, &pubkey, tweak)) {
    goto out;
  }

  size_t output_length = 33;
  if (!secp256k1_ec_pubkey_serialize(ctx, sec_encoded_pubkey, &output_length, &pubkey,
                                     SECP256K1_EC_COMPRESSED)) {
    goto out;
  }

  status = true;

out:
  rtos_mutex_unlock(&ctx_lock);
  return status;
}

bool crypto_ecc_secp256k1_normalize_signature(uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(signature);
  ENSURE_CTX();

  secp256k1_ecdsa_signature sig;
  if (!secp256k1_ecdsa_signature_parse_compact(ctx, &sig, signature)) {
    return false;
  }

  // Normalize the signature to have low s value
  if (secp256k1_ecdsa_signature_normalize(ctx, &sig /* populate output */, &sig) == 1) {
    // The signature was normalized (s value was high)
    // Re-serialize the signature back to compact format
    if (!secp256k1_ecdsa_signature_serialize_compact(ctx, signature, &sig)) {
      return false;
    }
  } else {
    // The signature was already normalized (s value was low)
    // No need to re-serialize since signature remains the same
  }

  return true;
}

static inline bool all_zeroes(uint8_t* buf, uint32_t size) {
  volatile uint8_t v = 0;
  for (size_t i = 0; i < size; i++) {
    v |= buf[i];
  }
  return v == 0;
}

static void clamp_ed25519_private_key(uint8_t* privkey) {
  // https://tools.ietf.org/html/rfc8032#section-5.1.5
  privkey[0] &= 248;
  privkey[31] &= 127;
  privkey[31] |= 64;
}

// We follow essentially §3.2.1 https://www.secg.org/sec1-v2.pdf#page=29
bool crypto_ecc_validate_private_key(key_handle_t* privkey) {
  ASSERT(privkey);

  if (all_zeroes(privkey->key.bytes, privkey->key.size)) {
    return false;
  }

  uint8_t p256_n[] = {
    0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
    0xbc, 0xe6, 0xfa, 0xad, 0xa7, 0x17, 0x9e, 0x84, 0xf3, 0xb9, 0xca, 0xc2, 0xfc, 0x63, 0x25, 0x51,
  };

  uint8_t* n;
  switch (privkey->alg) {
    case ALG_ECC_P256:
      n = p256_n;
      break;
    case ALG_ECC_ED25519:
      clamp_ed25519_private_key(privkey->key.bytes);
      return true;
    default:
      return false;
  }

  return (memcmp_s(privkey->key.bytes, n, privkey->key.size) < 0);
}

_Static_assert(32 == SECP256K1_KEY_SIZE, "problem with SECP256K1_KEY_SIZE");
_Static_assert((SECP256K1_KEY_SIZE + 1) == SECP256K1_SEC1_KEY_SIZE,
               "problem with SECP256K1_SEC1_KEY_SIZE");
_Static_assert(SECP256K1_KEYPAIR_SIZE == sizeof(secp256k1_keypair),
               "problem with SECP256K1_KEYPAIR_SIZE");
