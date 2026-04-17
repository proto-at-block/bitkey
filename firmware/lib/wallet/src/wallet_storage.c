#include "aes.h"
#include "filesystem.h"
#include "log.h"
#include "mempool.h"
#include "seed_impl.h"
#include "wallet.h"
#include "wkek_impl.h"

extern mempool_t* wallet_pool;

bool wkek_encrypt_and_store(char* filename, const uint8_t* data, uint32_t size) {
  const uint32_t blob_size = size + AES_GCM_OVERHEAD;
  bool result = false;
  uint8_t* blob = mempool_alloc(wallet_pool, blob_size);

  uint8_t* iv = &blob[0];
  uint8_t* ciphertext = &blob[AES_GCM_IV_LENGTH];
  uint8_t* tag = &blob[AES_GCM_IV_LENGTH + size];

  if (!wkek_encrypt(data, ciphertext, size, iv, tag)) {
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

void wallet_remove_files(void) {
  // These exist for legacy reasons.
#define WALLET_ACTIVE_AUTH_KEY_PATH     ("encrypted-auth-key-active.bin")
#define WALLET_ACTIVE_CONFIG_KEY_PATH   ("encrypted-config-key-active.bin")
#define WALLET_ACTIVE_SPEND_KEY_PATH    ("encrypted-spend-key-active.bin")
#define WALLET_RECOVERY_AUTH_KEY_PATH   ("encrypted-auth-key-recovery.bin")
#define WALLET_RECOVERY_CONFIG_KEY_PATH ("encrypted-config-key-recovery.bin")
#define WALLET_RECOVERY_SPEND_KEY_PATH  ("encrypted-spend-key-recovery.bin")
#define WALLET_INACTIVE_AUTH_KEY_PATH   ("encrypted-auth-key-inactive.bin")
#define WALLET_INACTIVE_CONFIG_KEY_PATH ("encrypted-config-key-inactive.bin")
#define WALLET_INACTIVE_SPEND_KEY_PATH  ("encrypted-spend-key-inactive.bin")
#define BTC_NETWORK_PATH                ("btc-network-type.bin")

  // Delete derived key cache and keyset metadata FIRST — these contain
  // cached private key material derived from the seed.  If a reset
  // interrupts this sequence after the seed is deleted but before the
  // cache is cleared, re-onboarding could mix stale cached keys (old
  // seed) with freshly derived keys (new seed), splitting the device's
  // auth and spending identities.
  fs_remove(DERIVED_KEY_CACHE_PATH);
  fs_remove(WALLET_KEYSET_PATH);
  fs_remove(BTC_NETWORK_PATH);

  // Delete seed — this is the file that wallet_is_initialized() checks.
  // Removing it after the cache ensures no stale derivation state
  // survives if the seed sentinel flips to "not initialized".
  seed_remove_files();

  // Delete encrypted keys before WKEK.  If interrupted, at least the
  // WKEK still exists and any surviving keys remain decryptable.
  fs_remove(WALLET_ACTIVE_AUTH_KEY_PATH);
  fs_remove(WALLET_ACTIVE_CONFIG_KEY_PATH);
  fs_remove(WALLET_ACTIVE_SPEND_KEY_PATH);
  fs_remove(WALLET_RECOVERY_AUTH_KEY_PATH);
  fs_remove(WALLET_RECOVERY_CONFIG_KEY_PATH);
  fs_remove(WALLET_RECOVERY_SPEND_KEY_PATH);
  fs_remove(WALLET_INACTIVE_AUTH_KEY_PATH);
  fs_remove(WALLET_INACTIVE_CONFIG_KEY_PATH);
  fs_remove(WALLET_INACTIVE_SPEND_KEY_PATH);

  // WKEK last — only delete after all encrypted keys are gone.
  wkek_remove_files();
}
