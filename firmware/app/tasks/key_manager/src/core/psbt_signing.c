#include "psbt_signing.h"

#include "attributes.h"
#include "bip32.h"
#include "key_manager_task_impl.h"
#include "rtos_notification.h"
#include "rtos_thread.h"
#include "wallet.h"

#include <string.h>

static key_manager_psbt_sign_result_t psbt_sign_hash_with_crypto_task(
  rtos_thread_t* crypto_thread, const derivation_path_t* path,
  const uint8_t hash[SHA256_DIGEST_SIZE], uint8_t signature_out[ECC_SIG_SIZE]) {
  if (!crypto_thread || !path || !hash || !signature_out) {
    return KEY_MANAGER_PSBT_SIGN_INVALID_PARAM;
  }

  if (crypto_task_get_status() != CRYPTO_TASK_WAITING) {
    return KEY_MANAGER_PSBT_SIGN_CRYPTO_BUSY;
  }

  crypto_task_set_parameters((derivation_path_t*)path, (uint8_t*)hash);
  rtos_notification_signal(crypto_thread);

  for (;;) {
    switch (crypto_task_get_status()) {
      case CRYPTO_TASK_IN_PROGRESS:
        rtos_thread_sleep(1);
        break;
      case CRYPTO_TASK_SUCCESS:
        if (!crypto_task_get_and_clear_signature((uint8_t*)hash, path->indices, path->num_indices,
                                                 signature_out)) {
          return KEY_MANAGER_PSBT_SIGN_CRYPTO_ERROR;
        }
        return KEY_MANAGER_PSBT_SIGN_OK;
      case CRYPTO_TASK_DERIVATION_FAILED:
        crypto_task_reset_status();
        return KEY_MANAGER_PSBT_SIGN_DERIVATION_FAILED;
      case CRYPTO_TASK_SIGNING_FAILED:
        crypto_task_reset_status();
        return KEY_MANAGER_PSBT_SIGN_SIGNING_FAILED;
      case CRYPTO_TASK_POLICY_VIOLATION:
        crypto_task_reset_status();
        return KEY_MANAGER_PSBT_SIGN_POLICY_VIOLATION;
      case CRYPTO_TASK_ERROR:
      default:
        crypto_task_reset_status();
        return KEY_MANAGER_PSBT_SIGN_CRYPTO_ERROR;
    }
  }
}

static bool psbt_pubkey_from_extended(const extended_key_t* key_pub,
                                      uint8_t pubkey_out[PSBT_P2WSH_PUBKEY_LEN]) {
  if (!key_pub || !pubkey_out) {
    return false;
  }

  pubkey_out[0] = key_pub->prefix;
  memcpy(&pubkey_out[1], key_pub->key, BIP32_KEY_SIZE);
  return true;
}

key_manager_psbt_sign_result_t key_manager_psbt_sign_p2wsh_inputs(
  const key_manager_psbt_input_t* inputs, size_t input_count, rtos_thread_t* crypto_thread,
  key_manager_psbt_signature_t* sigs_out, size_t sigs_out_len, size_t* sigs_written) {
  if (!inputs || input_count == 0 || !crypto_thread || !sigs_out || !sigs_written) {
    return KEY_MANAGER_PSBT_SIGN_INVALID_PARAM;
  }

  *sigs_written = 0;

  if (sigs_out_len < input_count) {
    return KEY_MANAGER_PSBT_SIGN_OUTPUT_TOO_SMALL;
  }

  for (size_t i = 0; i < input_count; i++) {
    const key_manager_psbt_input_t* input = &inputs[i];
    const psbt_p2wsh_signing_data_t* signing_data = &input->signing_data;

    const psbt_keypath_t* matching_keypath = NULL;
    size_t match_count = 0;

    for (size_t j = 0; j < signing_data->keypath_count; j++) {
      const psbt_keypath_t* keypath = &signing_data->keypaths[j];
      derivation_path_t path = {
        .indices = (uint32_t*)keypath->path,
        .num_indices = keypath->path_len,
      };

      extended_key_t key_priv CLEANUP(bip32_zero_key);
      if (!wallet_derive_key_priv_using_cache(&key_priv, path)) {
        continue;
      }

      extended_key_t key_pub CLEANUP(bip32_zero_key);
      if (!bip32_priv_to_pub(&key_priv, &key_pub)) {
        continue;
      }

      uint8_t derived_pubkey[PSBT_P2WSH_PUBKEY_LEN] = {0};
      if (!psbt_pubkey_from_extended(&key_pub, derived_pubkey)) {
        continue;
      }

      if (memcmp(derived_pubkey, keypath->pubkey, PSBT_P2WSH_PUBKEY_LEN) == 0) {
        matching_keypath = keypath;
        match_count++;
        if (match_count > 1) {
          break;
        }
      }
    }

    if (match_count != 1 || !matching_keypath) {
      *sigs_written = 0;
      return KEY_MANAGER_PSBT_SIGN_KEYPATH_MISMATCH;
    }

    derivation_path_t match_path = {
      .indices = (uint32_t*)matching_keypath->path,
      .num_indices = matching_keypath->path_len,
    };

    uint8_t compact_sig[ECC_SIG_SIZE] = {0};
    key_manager_psbt_sign_result_t sign_result = psbt_sign_hash_with_crypto_task(
      crypto_thread, &match_path, signing_data->sighash, compact_sig);
    if (sign_result != KEY_MANAGER_PSBT_SIGN_OK) {
      *sigs_written = 0;
      return sign_result;
    }

    uint8_t der_sig[PSBT_DER_SIGNATURE_MAX_LEN] = {0};
    size_t der_sig_len = 0;
    if (psbt_compact_sig_to_der(compact_sig, sizeof(compact_sig), der_sig, sizeof(der_sig),
                                &der_sig_len) != PSBT_OK) {
      *sigs_written = 0;
      return KEY_MANAGER_PSBT_SIGN_CRYPTO_ERROR;
    }

    if (der_sig_len + 1 > PSBT_SIGNATURE_MAX_LEN) {
      *sigs_written = 0;
      return KEY_MANAGER_PSBT_SIGN_CRYPTO_ERROR;
    }

    key_manager_psbt_signature_t* out_sig = &sigs_out[*sigs_written];
    out_sig->input_index = input->input_index;
    memcpy(out_sig->pubkey, matching_keypath->pubkey, PSBT_P2WSH_PUBKEY_LEN);
    memcpy(out_sig->signature, der_sig, der_sig_len);
    out_sig->signature[der_sig_len] = (uint8_t)(signing_data->sighash_type & 0xff);
    out_sig->signature_len = der_sig_len + 1;
    out_sig->sighash_type = signing_data->sighash_type;
    (*sigs_written)++;
  }

  return KEY_MANAGER_PSBT_SIGN_OK;
}

key_manager_psbt_sign_result_t key_manager_psbt_sign_p2wsh_psbt(
  const uint8_t* psbt_bytes, size_t psbt_len, rtos_thread_t* crypto_thread, uint8_t* psbt_out,
  size_t psbt_out_len, size_t* psbt_out_written) {
  if (!psbt_bytes || psbt_len == 0 || !crypto_thread || !psbt_out || psbt_out_len == 0 ||
      !psbt_out_written) {
    return KEY_MANAGER_PSBT_SIGN_INVALID_PARAM;
  }

  *psbt_out_written = 0;

  ew_psbt_t* psbt = NULL;
  if (ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt) != EW_OK) {
    return KEY_MANAGER_PSBT_SIGN_PSBT_ERROR;
  }

  size_t num_inputs = 0;
  if (ew_psbt_get_num_inputs(psbt, &num_inputs) != EW_OK) {
    ew_psbt_free(psbt);
    return KEY_MANAGER_PSBT_SIGN_PSBT_ERROR;
  }

  for (size_t i = 0; i < num_inputs; i++) {
    key_manager_psbt_input_t input = {
      .input_index = i,
      .signing_data = {0},
    };

    if (psbt_p2wsh_input_signing_data_from_psbt(psbt, i, &input.signing_data) != PSBT_OK) {
      ew_psbt_free(psbt);
      return KEY_MANAGER_PSBT_SIGN_PSBT_ERROR;
    }

    key_manager_psbt_signature_t sig = {0};
    size_t sigs_written = 0;
    key_manager_psbt_sign_result_t sign_result =
      key_manager_psbt_sign_p2wsh_inputs(&input, 1, crypto_thread, &sig, 1, &sigs_written);
    if (sign_result != KEY_MANAGER_PSBT_SIGN_OK) {
      ew_psbt_free(psbt);
      return sign_result;
    }
    if (sigs_written != 1) {
      ew_psbt_free(psbt);
      return KEY_MANAGER_PSBT_SIGN_CRYPTO_ERROR;
    }

    if (ew_psbt_input_add_signature(psbt, i, sig.pubkey, PSBT_P2WSH_PUBKEY_LEN, sig.signature,
                                    sig.signature_len) != EW_OK) {
      ew_psbt_free(psbt);
      return KEY_MANAGER_PSBT_SIGN_PSBT_ERROR;
    }
  }

  if (ew_psbt_to_bytes(psbt, psbt_out, psbt_out_len, psbt_out_written) != EW_OK) {
    ew_psbt_free(psbt);
    return KEY_MANAGER_PSBT_SIGN_OUTPUT_TOO_SMALL;
  }

  ew_psbt_free(psbt);
  return KEY_MANAGER_PSBT_SIGN_OK;
}
