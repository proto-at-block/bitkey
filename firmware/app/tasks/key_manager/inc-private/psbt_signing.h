#pragma once

#include "psbt.h"
#include "rtos.h"

#include <stddef.h>
#include <stdint.h>

typedef enum {
  KEY_MANAGER_PSBT_SIGN_OK = 0,
  KEY_MANAGER_PSBT_SIGN_INVALID_PARAM,
  KEY_MANAGER_PSBT_SIGN_PSBT_ERROR,
  KEY_MANAGER_PSBT_SIGN_KEYPATH_MISMATCH,
  KEY_MANAGER_PSBT_SIGN_CRYPTO_BUSY,
  KEY_MANAGER_PSBT_SIGN_DERIVATION_FAILED,
  KEY_MANAGER_PSBT_SIGN_SIGNING_FAILED,
  KEY_MANAGER_PSBT_SIGN_POLICY_VIOLATION,
  KEY_MANAGER_PSBT_SIGN_CRYPTO_ERROR,
  KEY_MANAGER_PSBT_SIGN_OUTPUT_TOO_SMALL,
} key_manager_psbt_sign_result_t;

typedef struct {
  size_t input_index;
  psbt_p2wsh_signing_data_t signing_data;
} key_manager_psbt_input_t;

typedef struct {
  size_t input_index;
  uint8_t pubkey[PSBT_P2WSH_PUBKEY_LEN];
  uint8_t signature[PSBT_SIGNATURE_MAX_LEN];
  size_t signature_len;
  uint32_t sighash_type;
} key_manager_psbt_signature_t;

key_manager_psbt_sign_result_t key_manager_psbt_sign_p2wsh_inputs(
  const key_manager_psbt_input_t* inputs, size_t input_count, rtos_thread_t* crypto_thread,
  key_manager_psbt_signature_t* sigs_out, size_t sigs_out_len, size_t* sigs_written);

key_manager_psbt_sign_result_t key_manager_psbt_sign_p2wsh_psbt(
  const uint8_t* psbt_bytes, size_t psbt_len, rtos_thread_t* crypto_thread, uint8_t* psbt_out,
  size_t psbt_out_len, size_t* psbt_out_written);
