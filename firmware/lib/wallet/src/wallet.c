#include "wallet.h"

#include "aes.h"
#include "assert.h"
#include "attributes.h"
#include "bip32.h"
#include "bitlog.h"
#include "key_management.h"
#include "log.h"
#include "rtos.h"
#include "secure_rng.h"
#include "secutils.h"
#include "wallet_impl.h"
#include "wkek_impl.h"

mempool_t* wallet_pool;

#define BASE_PATH_LENGTH (3)
#define TXN_PATH_LENGTH  (5)

static bool wallet_generate_key(extended_key_t* key) {
  uint8_t seed[32];
  ASSERT(crypto_random(seed, sizeof(seed)));
  bool ret = bip32_derive_master_key(seed, sizeof(seed), key);
  memzero(seed, sizeof(seed));
  return ret;
}

static wallet_res_t create_keybundle_key(const wallet_key_bundle_type_t type,
                                         const wallet_key_domain_t domain) {
  wallet_res_t result = WALLET_RES_ERR;

  extended_key_t* unwrapped_key = mempool_alloc(wallet_pool, sizeof(extended_key_t));
  if (!wallet_generate_key(unwrapped_key)) {
    result = WALLET_RES_MASTER_KEY_ERR;
    goto out;
  }

  if (!wallet_key_encrypt_and_store(type, domain, unwrapped_key)) {
    result = WALLET_RES_STORAGE_ERR;
    goto out;
  }

  result = WALLET_RES_OK;

out:
  if (result != WALLET_RES_OK) {
    BITLOG_EVENT(wallet_generate_key, result);
  }
  mempool_free(wallet_pool, unwrapped_key);
  return result;
}

static void fill_base_path(uint32_t* path, uint32_t path_len) {
  ASSERT(path_len >= BASE_PATH_LENGTH);

  fwpb_btc_network network;
  ASSERT(wallet_get_network_type(&network));

  path[0] = 84 | BIP32_HARDENED_BIT;
  switch (network) {
    case BITCOIN:
      path[1] = 0 | BIP32_HARDENED_BIT;
      break;
    default:
      path[1] = 1 | BIP32_HARDENED_BIT;
      break;
  }
  path[2] = 0 | BIP32_HARDENED_BIT;
}

static void fill_txn_path(uint32_t* path, uint32_t path_len, uint32_t change,
                          uint32_t address_index) {
  ASSERT(change == 0 || change == 1);
  ASSERT(path_len == TXN_PATH_LENGTH);
  fill_base_path(path, path_len);
  path[3] = change;
  path[4] = address_index;
}

static wallet_res_t check_keybundle_and_load_wkek(void) {
  wallet_res_t result = WALLET_RES_ERR;

  if (!wallet_keys_exist(WALLET_KEY_BUNDLE_ACTIVE)) {
    result = WALLET_RES_NOT_CREATED;
    goto out;
  }

  if (!wkek_lazy_init()) {
    result = WALLET_RES_WKEK_ERR;
    goto out;
  }

  result = WALLET_RES_OK;

out:
  return result;
}

static wallet_res_t derive_origin_info(extended_key_t* master_key_priv,
                                       key_descriptor_t* descriptor) {
  ASSERT(descriptor);

  wallet_res_t result = WALLET_RES_KEY_DERIVATION_ERR;

  uint32_t* indices = (uint32_t*)mempool_alloc(wallet_pool, BASE_PATH_LENGTH * sizeof(uint32_t));

  derivation_path_t path = {
    .indices = indices,
    .num_indices = BASE_PATH_LENGTH,
  };
  fill_base_path(path.indices, path.num_indices);

  // Populate fingerprint and path information.
  if (!bip32_fingerprint_for_path(master_key_priv, &path, descriptor->origin_fingerprint)) {
    goto out;
  }
  memcpy(descriptor->origin_path, path.indices, path.num_indices * sizeof(uint32_t));

  result = WALLET_RES_OK;

out:
  mempool_free(wallet_pool, indices);
  return result;
}

static wallet_res_t derive_key(derivation_path_t* path, const wallet_key_bundle_type_t type,
                               const wallet_key_domain_t key_domain, extended_key_t* key_out,
                               uint8_t parent_fingerprint_out[BIP32_KEY_FINGERPRINT_SIZE],
                               key_descriptor_t* descriptor) {
  wallet_res_t result = check_keybundle_and_load_wkek();
  if (result != WALLET_RES_OK) {
    return result;
  }

  extended_key_t* key = (extended_key_t*)mempool_alloc(wallet_pool, sizeof(extended_key_t));
  if (!wallet_key_load(type, key_domain, key)) {
    result = WALLET_RES_STORAGE_ERR;
    goto out;
  }

  if (!bip32_derive_path_priv(key, key_out, parent_fingerprint_out, path)) {
    result = WALLET_RES_KEY_DERIVATION_ERR;
    goto out;
  }

  if (descriptor) {
    if (derive_origin_info(key, descriptor) != WALLET_RES_OK) {
      result = WALLET_RES_KEY_DERIVATION_ERR;
      goto out;
    }
  }

  result = WALLET_RES_OK;

out:
  mempool_free(wallet_pool, key);
  return result;
}

// Helper function for deriving an extended_key_t from a path.
// key_descriptor_t may be NULL.
// IMPORTANT: Caller must mempool_free() the allocated key and fingerprint, even in the case of
// failure.
static wallet_res_t key_for_path(derivation_path_t* path, const wallet_key_bundle_type_t type,
                                 const wallet_key_domain_t key_domain, extended_key_t** key_out,
                                 uint8_t** parent_fingerprint_out,
                                 key_descriptor_t** descriptor_out) {
  *key_out = mempool_alloc(wallet_pool, sizeof(extended_key_t));
  *parent_fingerprint_out = mempool_alloc(wallet_pool, BIP32_KEY_FINGERPRINT_SIZE);

  if (descriptor_out) {
    return derive_key(path, type, key_domain, *key_out, *parent_fingerprint_out, *descriptor_out);
  } else {
    return derive_key(path, type, key_domain, *key_out, *parent_fingerprint_out, NULL);
  }
}

// TODO Add this back in once we need schnorr.
#if 0
static wallet_res_t schnorr_sign(extended_key_t* txn_key, uint8_t digest[SHA256_DIGEST_SIZE],
                                 uint8_t signature_out[ECC_SIG_SIZE]) {
  // libsecp256k1 needs us to load the keypair; `txn_key` is just the private key.
  // This buffer is for both.
  uint8_t* txn_keypair_bytes = mempool_alloc(wallet_pool, SECP256K1_KEYPAIR_SIZE);

  wallet_res_t result = WALLET_RES_ERR;

  key_handle_t txn_key_handle = {
    .alg = ALG_ECC_SECP256K1,  // Not actually used, since this key is software only.
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = txn_keypair_bytes,
    .key.size = SECP256K1_KEYPAIR_SIZE,
  };
  if (!crypto_ecc_secp256k1_load_keypair(txn_key->key, &txn_key_handle)) {
    result = WALLET_RES_SIGNING_ERR;
    goto out;
  }
  if (!crypto_ecc_secp256k1_schnorr_sign_hash32(&txn_key_handle, digest, signature_out,
                                                ECC_SIG_SIZE)) {
    result = WALLET_RES_SIGNING_ERR;
    memzero(signature_out, ECC_SIG_SIZE);  // Clear signature if signing failed.
    goto out;
  }

  result = WALLET_RES_OK;

out:
  mempool_free(wallet_pool, txn_keypair_bytes);
  return result;
}
#endif

void wallet_init(mempool_t* mempool) {
  ASSERT_EMBEDDED_ONLY(mempool && !wallet_pool);
  wallet_pool = mempool;
  crypto_ecc_secp256k1_init();
  wkek_init();
}

wallet_res_t wallet_create_keybundle(wallet_key_bundle_type_t type) {
  ASSERT(wallet_pool);

  if (!wkek_lazy_init()) {
    return WALLET_RES_WKEK_ERR;
  }

  if (wallet_keys_exist(type)) {
    return WALLET_RES_ALREADY_CREATED;
  }

  // Generate, encrypt and store the three wallet key types
  wallet_res_t result = create_keybundle_key(type, WALLET_KEY_DOMAIN_AUTH);
  if (result != WALLET_RES_OK) {
    return result;
  }

  result = create_keybundle_key(type, WALLET_KEY_DOMAIN_CONFIG);
  if (result != WALLET_RES_OK) {
    return result;
  }

  result = create_keybundle_key(type, WALLET_KEY_DOMAIN_SPEND);
  if (result != WALLET_RES_OK) {
    return result;
  }

  return result;
}

bool wallet_keybundle_exists(wallet_key_bundle_type_t type) {
  return wallet_keys_exist(type);
}

wallet_res_t wallet_get_pubkey(const wallet_key_bundle_type_t type,
                               const wallet_key_domain_t domain, extended_key_t* key_pub) {
  wallet_res_t result = WALLET_RES_OK;

  extended_key_t* key = NULL;
  uint8_t* parent_fingerprint = NULL;
  uint32_t* indices = NULL;

  result = check_keybundle_and_load_wkek();
  if (result != WALLET_RES_OK) {
    return result;
  }

  if (domain == WALLET_KEY_DOMAIN_SPEND) {
    fwpb_btc_network network;
    ASSERT(wallet_get_network_type(&network));

    // Prepare the path and derive the origin key
    indices = mempool_alloc(wallet_pool, BASE_PATH_LENGTH * sizeof(uint32_t));
    derivation_path_t path = {
      .indices = indices,
      .num_indices = BASE_PATH_LENGTH,
    };
    fill_base_path(path.indices, path.num_indices);

    result = key_for_path(&path, type, domain, &key, &parent_fingerprint, NULL);
    if (result != WALLET_RES_OK) {
      goto out;
    }
  } else {
    key = mempool_alloc(wallet_pool, sizeof(extended_key_t));
    if (!wallet_key_load(type, domain, key)) {
      LOGE("WALLET_RES_STORAGE_ERR");
      result = WALLET_RES_STORAGE_ERR;
      goto out;
    }
  }

  if (!bip32_priv_to_pub(key, key_pub)) {
    LOGE("WALLET_RES_KEY_DERIVATION_ERR");
    result = WALLET_RES_KEY_DERIVATION_ERR;
    goto out;
  }

out:
  mempool_free(wallet_pool, key);
  mempool_free(wallet_pool, parent_fingerprint);
  mempool_free(wallet_pool, indices);
  return result;
}

// Performs sha256(auth_pubkey) + sha256(config_pubkey) + sha256(spend_pubkey)
wallet_res_t wallet_keybundle_id(const wallet_key_bundle_type_t type, uint8_t* id_digest) {
  wallet_res_t result = WALLET_RES_OK;

  extended_key_t* pubkey = (extended_key_t*)mempool_alloc(wallet_pool, sizeof(extended_key_t));
  memset(id_digest, 0, SHA256_DIGEST_SIZE);

  // Public key used here is prefix[1]+key[BIP32_KEY_SIZE]
  uint8_t pub_keys[WALLET_KEY_DOMAIN_MAX][BIP32_KEY_SIZE + 1] = {0};
  for (wallet_key_domain_t domain = WALLET_KEY_DOMAIN_AUTH; domain < WALLET_KEY_DOMAIN_MAX;
       domain++) {
    wallet_get_pubkey(type, domain, pubkey);
    memcpy(pub_keys[domain], (uint8_t*)&pubkey->prefix, BIP32_KEY_SIZE + 1);
  }

  if (!crypto_hash((uint8_t*)pub_keys, ((BIP32_KEY_SIZE + 1) * WALLET_KEY_DOMAIN_MAX), id_digest,
                   SHA256_DIGEST_SIZE, ALG_SHA256)) {
    result = WALLET_RES_ERR;
    LOGE("crypto_hash");
    goto out;
  }

  result = WALLET_RES_OK;

out:
  mempool_free(wallet_pool, pubkey);
  return result;
}

wallet_res_t wallet_csek_encrypt(uint8_t* unwrapped_csek, uint8_t* wrapped_csek_out,
                                 uint32_t length, uint8_t iv_out[AES_GCM_IV_LENGTH],
                                 uint8_t tag_out[AES_GCM_TAG_LENGTH]) {
  ASSERT(unwrapped_csek && wrapped_csek_out && iv_out && tag_out);
  ASSERT(length == CSEK_LENGTH);

  if (!wkek_lazy_init()) {
    return WALLET_RES_WKEK_ERR;
  }

  if (!wkek_encrypt(unwrapped_csek, wrapped_csek_out, length, iv_out, tag_out)) {
    return WALLET_RES_SEALING_ERR;
  }

  return WALLET_RES_OK;
}

wallet_res_t wallet_csek_decrypt(uint8_t* wrapped_csek, uint8_t* unwrapped_csek_out,
                                 uint32_t length, uint8_t iv[AES_GCM_IV_LENGTH],
                                 uint8_t tag[AES_GCM_TAG_LENGTH]) {
  ASSERT(wrapped_csek && unwrapped_csek_out && iv && tag);

  if (!wkek_lazy_init()) {
    return WALLET_RES_WKEK_ERR;
  }

  if (!wkek_decrypt(wrapped_csek, unwrapped_csek_out, length, iv, tag)) {
    return WALLET_RES_UNSEALING_ERR;
  }

  return WALLET_RES_OK;
}

wallet_res_t wallet_sign_txn(const wallet_key_domain_t key_domain,
                             uint8_t digest[SHA256_DIGEST_SIZE], uint8_t signature[ECC_SIG_SIZE],
                             uint32_t change, uint32_t address_index,
                             key_descriptor_t* descriptor) {
  ASSERT(digest && signature && descriptor);

  fwpb_btc_network network;
  ASSERT(wallet_get_network_type(&network));

  wallet_res_t result = WALLET_RES_ERR;

  // Prepare the path and derive the key for this transaction.
  uint32_t* indices = (uint32_t*)mempool_alloc(wallet_pool, TXN_PATH_LENGTH * sizeof(uint32_t));
  derivation_path_t path = {
    .indices = indices,
    .num_indices = TXN_PATH_LENGTH,
  };
  fill_txn_path(path.indices, path.num_indices, change, address_index);

  extended_key_t* txn_key = NULL;
  extended_key_t* txn_key_pub = NULL;
  uint8_t* parent_fingerprint = NULL;
  result = key_for_path(&path, WALLET_KEY_BUNDLE_ACTIVE, key_domain, &txn_key, &parent_fingerprint,
                        &descriptor);
  if (result != WALLET_RES_OK) {
    goto out;
  }

  if (!bip32_sign(txn_key, digest, signature)) {
    result = WALLET_RES_SIGNING_ERR;
    goto out;
  }

  // Done signing, finish populating the output descriptor.
  // Note the origin information was filled in above.
  txn_key_pub = mempool_alloc(wallet_pool, sizeof(extended_key_t));
  if (!bip32_priv_to_pub(txn_key, txn_key_pub)) {
    result = WALLET_RES_KEY_DERIVATION_ERR;
    goto out;
  }

  if (!bip32_serialize_ext_key(txn_key_pub, NULL, parent_fingerprint,
                               (network == BITCOIN) ? MAINNET_PUB : TESTNET_PUB, address_index,
                               TXN_PATH_LENGTH, descriptor->serialized_bip32_key,
                               BIP32_SERIALIZED_EXT_KEY_SIZE)) {
    result = WALLET_RES_SERIALIZATION_ERR;
    goto out;
  }

  memcpy(descriptor->xpub_path, &path.indices[BASE_PATH_LENGTH],
         (path.num_indices - BASE_PATH_LENGTH) * sizeof(uint32_t));

  result = WALLET_RES_OK;

out:
  if (result != WALLET_RES_OK) {
    BITLOG_EVENT(wallet_sign_txn, result);
  }

  mempool_free(wallet_pool, txn_key);
  mempool_free(wallet_pool, parent_fingerprint);
  mempool_free(wallet_pool, indices);
  mempool_free(wallet_pool, txn_key_pub);
  return result;
}

wallet_res_t wallet_get_descriptor(const wallet_key_bundle_type_t type,
                                   const wallet_key_domain_t key_domain,
                                   key_descriptor_t* descriptor) {
  ASSERT(descriptor);

  fwpb_btc_network network;
  ASSERT(wallet_get_network_type(&network));

  wallet_res_t result = WALLET_RES_ERR;

  extended_key_t* key = NULL;
  extended_key_t* key_pub = NULL;
  uint8_t* parent_fingerprint = NULL;

  // Prepare the path and derive the origin key
  uint32_t* indices = mempool_alloc(wallet_pool, BASE_PATH_LENGTH * sizeof(uint32_t));
  derivation_path_t path = {
    .indices = indices,
    .num_indices = BASE_PATH_LENGTH,
  };
  fill_base_path(path.indices, path.num_indices);

  result = key_for_path(&path, type, key_domain, &key, &parent_fingerprint, &descriptor);
  if (result != WALLET_RES_OK) {
    goto out;
  }

  key_pub = mempool_alloc(wallet_pool, sizeof(extended_key_t));
  if (!bip32_priv_to_pub(key, key_pub)) {
    result = WALLET_RES_KEY_DERIVATION_ERR;
    goto out;
  }

  if (!bip32_serialize_ext_key(key_pub, NULL, parent_fingerprint,
                               (network == BITCOIN) ? MAINNET_PUB : TESTNET_PUB, 0, TXN_PATH_LENGTH,
                               descriptor->serialized_bip32_key, BIP32_SERIALIZED_EXT_KEY_SIZE)) {
    result = WALLET_RES_SERIALIZATION_ERR;
    goto out;
  }

  result = WALLET_RES_OK;

out:
  mempool_free(wallet_pool, key);
  mempool_free(wallet_pool, parent_fingerprint);
  mempool_free(wallet_pool, key_pub);
  mempool_free(wallet_pool, indices);
  return result;
}

bool wallet_created(void) {
  return wkek_exists() && wallet_keys_exist(WALLET_KEY_BUNDLE_ACTIVE);
}
