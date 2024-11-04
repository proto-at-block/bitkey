#include "aes.h"
#include "arithmetic.h"
#include "attributes.h"
#include "bip32.h"
#include "bitlog.h"
#include "derived_key_cache_impl.h"
#include "filesystem.h"
#include "log.h"
#include "seed.h"
#include "wallet.h"

// NOTE: This cache should be zeroed out via `wallet_clear_derived_key_cache` after each signing
// session.
STATIC_VISIBLE_FOR_TESTING derived_key_cache_t derived_key_cache = {
  .version = DERIVED_KEY_CACHE_VERSION,
};

STATIC_VISIBLE_FOR_TESTING bool derived_key_cache_initialized = false;

// Returns whether `a` is a subset of `b`.
static bool derivation_path_is_subset(derivation_path_t a, derivation_path_t b) {
  if (b.num_indices < a.num_indices) {
    return false;
  }

  for (uint32_t i = 0; i < a.num_indices; i++) {
    if (a.indices[i] != b.indices[i]) {
      return false;
    }
  }

  return true;
}

static const derivation_path_t* get_derivation_path(derivation_path_parts_t parts) {
  for (uint32_t i = 0; i < ARRAY_SIZE(DERIVATION_PATHS); i++) {
    const derivation_path_parts_t* match = DERIVATION_PATHS[i];
    if (parts.purpose == match->purpose && parts.coin_type == match->coin_type &&
        parts.change == match->change) {
      return &match->path;
    }
  }

  return NULL;
}

static const derivation_path_parts_t* get_derivation_path_parts(derivation_path_t path) {
  for (uint32_t i = 0; i < ARRAY_SIZE(DERIVATION_PATHS); i++) {
    const derivation_path_parts_t* parts = DERIVATION_PATHS[i];
    if (derivation_path_is_subset(parts->path, path)) {
      return parts;
    }
  }

  return NULL;
}

static bool derive_key_priv_from_seed(extended_key_t* key_priv, derivation_path_t derivation_path) {
  extended_key_t master_key CLEANUP(bip32_zero_key);

  if (seed_as_extended_key(&master_key) != SEED_RES_OK) {
    return false;
  }

  return bip32_derive_path_priv(&master_key, key_priv, NULL, &derivation_path);
}

static bool create_derived_key_cache(coin_type_t coin_type) {
  derivation_path_parts_t parts;

  parts.purpose = PURPOSE_BIP84;
  parts.coin_type = coin_type;
  parts.change = CHANGE_EXTERNAL;
  const derivation_path_t* path = get_derivation_path(parts);
  ASSERT(path);
  if (!derive_key_priv_from_seed(&derived_key_cache.bip84_external_key, *path)) {
    LOGE("Failed to derive BIP-84 external private key");
    return false;
  }

  parts.purpose = PURPOSE_BIP84;
  parts.coin_type = coin_type;
  parts.change = CHANGE_INTERNAL;
  path = get_derivation_path(parts);
  ASSERT(path);
  if (!derive_key_priv_from_seed(&derived_key_cache.bip84_internal_key, *path)) {
    LOGE("Failed to derive BIP-84 internal private key");
    return false;
  }

  parts.purpose = PURPOSE_W1_AUTH;
  parts.coin_type = COIN_TYPE_UNKNOWN;
  parts.change = CHANGE_UNKNOWN;
  path = get_derivation_path(parts);
  ASSERT(path);
  if (!derive_key_priv_from_seed(&derived_key_cache.w1_auth_key, *path)) {
    LOGE("Failed to derive W1 auth private key");
    return false;
  }

  if (!wkek_encrypt_and_store(DERIVED_KEY_CACHE_PATH, (uint8_t*)&derived_key_cache,
                              sizeof(derived_key_cache))) {
    LOGE("Failed to store derived key cache");
    return false;
  }

  return true;
}

void derived_key_cache_lazy_init(coin_type_t coin_type) {
  if (coin_type == COIN_TYPE_UNKNOWN) {
    return;
  }
  if (derived_key_cache_initialized) {
    return;
  }

  if (!fs_file_exists(DERIVED_KEY_CACHE_PATH)) {
    // All the required keys have been loaded into our cache if `create_derived_key_cache` succeeds.
    derived_key_cache_initialized = create_derived_key_cache(coin_type);
  } else {
    // NOTE: Version should be handled here when bumped.
    derived_key_cache_initialized = wkek_read_and_decrypt(
      DERIVED_KEY_CACHE_PATH, (uint8_t*)&derived_key_cache, sizeof(derived_key_cache));
    if (derived_key_cache.version != DERIVED_KEY_CACHE_VERSION) {
      if (fs_remove(DERIVED_KEY_CACHE_PATH) >= 0) {
        derived_key_cache.version = DERIVED_KEY_CACHE_VERSION;
        derived_key_cache_initialized = create_derived_key_cache(coin_type);
      } else {
        LOGE("Failed to remove derived key cache with unknown version %d",
             derived_key_cache.version);
        BITLOG_EVENT(wallet_derived_key_cache_version_err, derived_key_cache.version);
      }
    }
  }
}

NO_OPTIMIZE void wallet_clear_derived_key_cache(void) {
  bip32_zero_key(&derived_key_cache.bip84_external_key);
  bip32_zero_key(&derived_key_cache.bip84_internal_key);
  bip32_zero_key(&derived_key_cache.w1_auth_key);
  derived_key_cache_initialized = false;
}

// Assumes the `cached_key` is a subset of `key_priv`.
static bool maybe_derive_from_cached_key(extended_key_t* key_priv,
                                         derivation_path_t derivation_path,
                                         extended_key_t* cached_key, uint32_t common_levels) {
  ASSERT(derivation_path.num_indices >= common_levels);

  if (derivation_path.num_indices == common_levels) {
    memcpy(key_priv, cached_key, sizeof(extended_key_t));
    return true;
  }

  // We assume the common levels were already derived by the cached key, so we only derive the
  // remaining ones.
  derivation_path_t path_from_cached_key;
  path_from_cached_key.indices = &derivation_path.indices[common_levels];
  path_from_cached_key.num_indices = derivation_path.num_indices - common_levels;

  return bip32_derive_path_priv(cached_key, key_priv, NULL, &path_from_cached_key);
}

bool wallet_derive_key_priv_using_cache(extended_key_t* key_priv,
                                        derivation_path_t derivation_path) {
  const derivation_path_parts_t* parts = get_derivation_path_parts(derivation_path);
  if (parts) {
    // We have a known cached path, ensure the cache is initialized and derive from it instead of
    // from the seed.
    derived_key_cache_lazy_init(parts->coin_type);

    if (derived_key_cache_initialized) {
      if (parts->purpose == PURPOSE_BIP84 && parts->change == CHANGE_EXTERNAL) {
        ASSERT(parts->coin_type == COIN_TYPE_MAINNET || parts->coin_type == COIN_TYPE_TESTNET);
        return maybe_derive_from_cached_key(key_priv, derivation_path,
                                            &derived_key_cache.bip84_external_key,
                                            parts->path.num_indices);
      }
      if (parts->purpose == PURPOSE_BIP84 && parts->change == CHANGE_INTERNAL) {
        ASSERT(parts->coin_type == COIN_TYPE_MAINNET || parts->coin_type == COIN_TYPE_TESTNET);
        return maybe_derive_from_cached_key(key_priv, derivation_path,
                                            &derived_key_cache.bip84_internal_key,
                                            parts->path.num_indices);
      }
      if (parts->purpose == PURPOSE_W1_AUTH) {
        ASSERT(parts->coin_type == COIN_TYPE_UNKNOWN && parts->change == CHANGE_UNKNOWN);
        return maybe_derive_from_cached_key(
          key_priv, derivation_path, &derived_key_cache.w1_auth_key, parts->path.num_indices);
      }
    }
  }

  // We don't support caching for this derivation path, so we always re-derive it from our seed.
  return derive_key_priv_from_seed(key_priv, derivation_path);
}
