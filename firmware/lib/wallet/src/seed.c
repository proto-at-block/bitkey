#include "filesystem.h"
#include "hex.h"
#include "hkdf.h"
#include "log.h"
#include "secure_rng.h"
#include "seed_impl.h"
#include "wallet_impl.h"
#include "wkek.h"

typedef struct {
  uint8_t bytes[SEED_SIZE];
} seed_t;

static void seed_zero(seed_t* const seed) {
  memzero(seed, sizeof(seed_t));
}

int seed_remove_files() {
  return fs_remove(SEED_PATH);
}

static seed_res_t master_key_lazy_init(extended_key_t* master_key, seed_t* seed) {
  bool seed_loaded = wkek_read_and_decrypt(SEED_PATH, seed->bytes, sizeof(seed->bytes));

  if (!seed_loaded) {
    ASSERT(crypto_random(seed->bytes, sizeof(seed->bytes)));
  }

  if (!bip32_derive_master_key(seed->bytes, sizeof(seed->bytes), master_key)) {
    return SEED_RES_ERR_MASTER_DERIVE;
  }

  if (!seed_loaded && !wkek_encrypt_and_store(SEED_PATH, seed->bytes, sizeof(seed->bytes))) {
    return SEED_RES_ERR_SEED_WRITE;
  }

  return SEED_RES_OK;
}

seed_res_t seed_as_extended_key(extended_key_t* key) {
  if (!wkek_lazy_init()) {
    return SEED_RES_ERR_WKEK;
  }

  seed_t seed CLEANUP(seed_zero);
  return master_key_lazy_init(key, &seed);
}

seed_res_t seed_derive_bip32(const derivation_path_t path, extended_key_t* key,
                             fingerprint_t* master_fingerprint,
                             fingerprint_t* childs_parent_fingerprint) {
  if (!wkek_lazy_init()) {
    return SEED_RES_ERR_WKEK;
  }

  extended_key_t master_key __attribute__((__cleanup__(bip32_zero_key)));
  seed_t seed __attribute__((__cleanup__(seed_zero)));

  seed_res_t result = master_key_lazy_init(&master_key, &seed);
  if (result != SEED_RES_OK) {
    return result;
  }

  if (!bip32_compute_fingerprint(&master_key, master_fingerprint->bytes)) {
    return SEED_RES_ERR_MASTER_FINGERPRINT;
  }

  if (path.num_indices == 0) {
    memcpy(key, &master_key, sizeof(extended_key_t));
    memzero(childs_parent_fingerprint, sizeof(fingerprint_t));
    return SEED_RES_OK;
  }

  if (!bip32_derive_path_priv(&master_key, key, childs_parent_fingerprint->bytes, &path)) {
    return SEED_RES_ERR_DERIVE_CHILD;
  }

  return SEED_RES_OK;
}

seed_res_t seed_derive_hkdf(key_algorithm_t algorithm, uint8_t* label, size_t label_len,
                            key_handle_t* privkey_out, key_handle_t* pubkey_out) {
  if (!wkek_lazy_init()) {
    return SEED_RES_ERR_WKEK;
  }

  extended_key_t master_key CLEANUP(bip32_zero_key);
  seed_t seed CLEANUP(seed_zero);

  key_handle_t seed_key_handle CLEANUP(zeroize_key) = {
    .alg = ALG_KEY_DERIVATION,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = seed.bytes,
    .key.size = SEED_SIZE,
  };

  // Need to derive the master key here, because if the seed is lazy-initialized, we
  // still need to make sure that the master key can successfully be derived.
  seed_res_t result = master_key_lazy_init(&master_key, &seed);
  if (result != SEED_RES_OK) {
    goto err;
  }

  if (privkey_out->key.size != SEED_DERIVE_HKDF_PRIVKEY_SIZE) {
    result = SEED_RES_ERR_DERIVE_CHILD;
    goto err;
  }

  privkey_out->alg = ALG_KEY_DERIVATION;  // Must set to derive.
  if (!crypto_hkdf(&seed_key_handle, ALG_SHA256, NULL, 0, label, label_len, privkey_out)) {
    result = SEED_RES_ERR_DERIVE_CHILD;
    goto err;
  }

  // Update key handle to reflect its new purpose
  privkey_out->alg = algorithm;
  privkey_out->acl =
    SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY;

  pubkey_out->acl =
    (SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY);

  if (!crypto_ecc_validate_private_key(privkey_out)) {
    result = SEED_RES_ERR_DERIVE_CHILD;
    goto err;
  }

  if (!export_pubkey(privkey_out, pubkey_out)) {
    result = SEED_RES_ERR_DERIVE_CHILD;
    goto err;
  }

  return SEED_RES_OK;
err:
  zeroize_key(privkey_out);
  return result;
}
