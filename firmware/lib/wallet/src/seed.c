#include "attributes.h"
#include "filesystem.h"
#include "secure_rng.h"
#include "secutils.h"
#include "seed_impl.h"
#include "wallet.h"
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
