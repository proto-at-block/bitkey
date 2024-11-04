#include "aes.h"
#include "attributes.h"
#include "bip32.h"
#include "filesystem.h"
#include "hex.h"
#include "key_management.h"
#include "log.h"
#include "mempool.h"
#include "rtos.h"
#include "secure_rng.h"
#include "secutils.h"
#include "seed_impl.h"
#include "wallet.h"
#include "wallet_impl.h"
#include "wkek_impl.h"

extern mempool_t* wallet_pool;

#define BTC_NETWORK_PATH ("btc-network-type.bin")

// The order of these must match with wallet_key_domain_t
static char* wallet_key_paths[3][3] = {
  [WALLET_KEY_BUNDLE_ACTIVE] =
    {
      WALLET_ACTIVE_AUTH_KEY_PATH,
      WALLET_ACTIVE_CONFIG_KEY_PATH,
      WALLET_ACTIVE_SPEND_KEY_PATH,
    },
  [WALLET_KEY_BUNDLE_RECOVERY] =
    {
      WALLET_RECOVERY_AUTH_KEY_PATH,
      WALLET_RECOVERY_CONFIG_KEY_PATH,
      WALLET_RECOVERY_SPEND_KEY_PATH,
    },
  [WALLET_KEY_BUNDLE_INACTIVE] =
    {
      WALLET_INACTIVE_AUTH_KEY_PATH,
      WALLET_INACTIVE_CONFIG_KEY_PATH,
      WALLET_INACTIVE_SPEND_KEY_PATH,
    },
};

bool wkek_encrypt_and_store(char* filename, const uint8_t* data, uint32_t size) {
  const uint32_t blob_size = size + AES_GCM_OVERHEAD;
  bool result = false;
  uint8_t* blob = mempool_alloc(wallet_pool, blob_size);

  uint8_t* iv = &blob[0];
  uint8_t* ciphertext = &blob[AES_GCM_IV_LENGTH];
  uint8_t* tag = &blob[AES_GCM_IV_LENGTH + size];

  if (!wkek_encrypt(data, ciphertext, size, iv, tag)) {
    LOGD("Failed to encrypt");
    goto out;
  }

  result = fs_util_write_global(filename, blob, blob_size);

out:
  mempool_free(wallet_pool, blob);
  return result;
}

bool wkek_read_and_decrypt(char* filename, uint8_t* data_out, uint32_t size) {
  const uint32_t blob_size = size + AES_GCM_OVERHEAD;
  bool result = false;
  uint8_t* blob = mempool_alloc(wallet_pool, blob_size);

  // Check the key file exists
  if (!fs_file_exists(filename)) {
    goto out;
  }

  // Read encrypted data from flash.
  if (!fs_util_read_global(filename, blob, blob_size)) {
    goto out;
  }

  // Decrypt and place in `data_out`.
  uint8_t* iv = &blob[0];
  uint8_t* ciphertext = &blob[AES_GCM_IV_LENGTH];
  uint8_t* tag = &blob[AES_GCM_IV_LENGTH + size];

  result = wkek_decrypt(ciphertext, data_out, size, iv, tag);

out:
  mempool_free(wallet_pool, blob);
  return result;
}

bool wallet_keys_exist(const wallet_key_bundle_type_t type) {
  if (fs_file_exists(wallet_key_paths[type][WALLET_KEY_DOMAIN_AUTH]) &&
      fs_file_exists(wallet_key_paths[type][WALLET_KEY_DOMAIN_CONFIG]) &&
      fs_file_exists(wallet_key_paths[type][WALLET_KEY_DOMAIN_SPEND])) {
    return true;
  }

  return false;
}

bool wallet_key_encrypt_and_store(const wallet_key_bundle_type_t type,
                                  const wallet_key_domain_t domain,
                                  extended_key_t* plaintext_master_key) {
  return wkek_encrypt_and_store(wallet_key_paths[type][domain], (uint8_t*)plaintext_master_key,
                                sizeof(extended_key_t));
}

bool wallet_key_load(const wallet_key_bundle_type_t type, const wallet_key_domain_t domain,
                     extended_key_t* plaintext_key_out) {
  return wkek_read_and_decrypt(wallet_key_paths[type][domain], (uint8_t*)plaintext_key_out,
                               sizeof(extended_key_t));
}

bool wallet_set_network_type(fwpb_btc_network network) {
  return fs_util_write_global(BTC_NETWORK_PATH, (uint8_t*)&network, sizeof(fwpb_btc_network));
}

bool wallet_get_network_type(fwpb_btc_network* network_out) {
  return fs_util_read_global(BTC_NETWORK_PATH, (uint8_t*)network_out, sizeof(fwpb_btc_network));
}

void wallet_remove_files(void) {
  wkek_remove_files();
  fs_remove(WALLET_ACTIVE_AUTH_KEY_PATH);
  fs_remove(WALLET_ACTIVE_CONFIG_KEY_PATH);
  fs_remove(WALLET_ACTIVE_SPEND_KEY_PATH);
  fs_remove(WALLET_RECOVERY_AUTH_KEY_PATH);
  fs_remove(WALLET_RECOVERY_CONFIG_KEY_PATH);
  fs_remove(WALLET_RECOVERY_SPEND_KEY_PATH);
  fs_remove(WALLET_INACTIVE_AUTH_KEY_PATH);
  fs_remove(WALLET_INACTIVE_CONFIG_KEY_PATH);
  fs_remove(WALLET_INACTIVE_SPEND_KEY_PATH);
  fs_remove(BTC_NETWORK_PATH);
  fs_remove(DERIVED_KEY_CACHE_PATH);
  seed_remove_files();
}

bool wallet_swap_keybundles(const wallet_key_bundle_type_t src,
                            const wallet_key_bundle_type_t dst) {
  if (fs_rename(wallet_key_paths[src][WALLET_KEY_DOMAIN_AUTH],
                wallet_key_paths[dst][WALLET_KEY_DOMAIN_AUTH]) != 0) {
    return false;
  }
  if (fs_rename(wallet_key_paths[src][WALLET_KEY_DOMAIN_CONFIG],
                wallet_key_paths[dst][WALLET_KEY_DOMAIN_CONFIG]) != 0) {
    return false;
  }
  if (fs_rename(wallet_key_paths[src][WALLET_KEY_DOMAIN_SPEND],
                wallet_key_paths[dst][WALLET_KEY_DOMAIN_SPEND]) != 0) {
    return false;
  }

  return true;
}
