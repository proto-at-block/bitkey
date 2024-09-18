#include "attributes.h"
#include "bip32.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "rtos.h"
#include "seed.h"

#include <stdatomic.h>

// This is a helper task that handles signing asynchonously.
//
// Signing is a relatively slow operation -- at least 100ms including time for
// BIP32 derivation. Despite not hitting the NFC timing constraint imposed by FWI=10,
// we have observed flaky behavior with newer iOS devices when signing synchronously.

static struct {
  atomic_int status;
  derivation_path_t derivation_path;
  uint32_t indices_scratch[BIP32_MAX_DERIVATION_DEPTH];
  uint8_t hash[SHA256_DIGEST_SIZE];
  uint8_t signature[ECC_SIG_SIZE];
} crypto_thread_priv = {
  .status = CRYPTO_TASK_WAITING,
  .derivation_path =
    {
      .indices = crypto_thread_priv.indices_scratch,
      .num_indices = 0,
    },
  .hash = {0},
  .signature = {0},
};

crypto_task_status_t crypto_task_get_status(void) {
  crypto_task_status_t out = crypto_thread_priv.status;
  return out;
}

void crypto_task_reset_status(void) {
  crypto_thread_priv.status = CRYPTO_TASK_WAITING;
}

bool crypto_task_get_and_clear_signature(uint8_t expected_hash[SHA256_DIGEST_SIZE],
                                         uint32_t* expected_indices, uint32_t num_indices,
                                         uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(signature);
  ASSERT(crypto_thread_priv.status == CRYPTO_TASK_SUCCESS);

  bool success = false;

  const bool hashes_match = memcmp(crypto_thread_priv.hash, expected_hash, SHA256_DIGEST_SIZE) == 0;
  const bool indices_match = memcmp(crypto_thread_priv.indices_scratch, expected_indices,
                                    num_indices * sizeof(uint32_t)) == 0;
  if (!hashes_match) {
    LOGE("Wrong hash");
  } else if (!indices_match) {
    LOGE("Wrong indices");
  } else {
    memcpy(signature, crypto_thread_priv.signature, ECC_SIG_SIZE);
    success = true;
  }

  memzero(crypto_thread_priv.signature, ECC_SIG_SIZE);
  memzero(crypto_thread_priv.hash, SHA256_DIGEST_SIZE);
  memzero(crypto_thread_priv.indices_scratch, sizeof(crypto_thread_priv.indices_scratch));

  crypto_thread_priv.status = CRYPTO_TASK_WAITING;

  return success;
}

void crypto_task_set_parameters(derivation_path_t* derivation_path,
                                uint8_t hash[SHA256_DIGEST_SIZE]) {
  ASSERT(hash);
  ASSERT(crypto_thread_priv.status == CRYPTO_TASK_WAITING);

  memcpy(crypto_thread_priv.hash, hash, SHA256_DIGEST_SIZE);

  ASSERT(derivation_path->num_indices <= BIP32_MAX_DERIVATION_DEPTH);
  memcpy(crypto_thread_priv.indices_scratch, derivation_path->indices,
         derivation_path->num_indices * sizeof(uint32_t));
  crypto_thread_priv.derivation_path.num_indices = derivation_path->num_indices;

  crypto_thread_priv.status = CRYPTO_TASK_IN_PROGRESS;
}

static crypto_task_status_t crypto_derive_and_sign(void) {
  ASSERT(crypto_thread_priv.status == CRYPTO_TASK_IN_PROGRESS);

#if 0
  // Add this sleep to make sure that the async logic works
  rtos_thread_sleep(1000);
#endif

  extended_key_t key_priv CLEANUP(bip32_zero_key);
  fingerprint_t key_priv_master_fingerprint;
  fingerprint_t key_priv_childs_parent_fingerprint;

  crypto_task_status_t rsp = CRYPTO_TASK_ERROR;

  seed_res_t seed_res =
    seed_derive_bip32(crypto_thread_priv.derivation_path, &key_priv, &key_priv_master_fingerprint,
                      &key_priv_childs_parent_fingerprint);
  if (seed_res != SEED_RES_OK) {
    rsp = CRYPTO_TASK_DERIVATION_FAILED;
    LOGE("seed_derive failed: %d", seed_res);
    goto out;
  }

  if (!bip32_sign(&key_priv, crypto_thread_priv.hash, crypto_thread_priv.signature)) {
    rsp = CRYPTO_TASK_ERROR;
    LOGE("bip32_ecdsa_sign failed");
    goto out;
  }

  rsp = CRYPTO_TASK_SUCCESS;

out:
  crypto_thread_priv.status = rsp;
  return rsp;
}

void crypto_thread(void* UNUSED(args)) {
  for (;;) {
    if (!rtos_notification_wait_signal(RTOS_NOTIFICATION_TIMEOUT_MAX)) {
      LOGE("Failed to wait for signal");
    }
    crypto_derive_and_sign();
  }
}

rtos_thread_t* crypto_task_create(void) {
  rtos_thread_t* crypto_task_handle =
    rtos_thread_create(crypto_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 8192);
  ASSERT(crypto_task_handle);
  return crypto_task_handle;
}
