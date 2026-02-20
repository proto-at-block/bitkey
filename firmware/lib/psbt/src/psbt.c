#include "psbt.h"

#include "bitops.h"
#include "ew.h"
#include "mempool.h"
#include "secure_rng.h"
#include "wstring.h"

#include <string.h>

static void ew_secure_memzero(void* const pnt, const size_t len) {
  memzero(pnt, len);
}

#define WALLY_MEMPOOL_REGIONS(X) X(wally, addr, 96, 2)

static mempool_t* wally_pool = NULL;

static void* ew_malloc_wrapper(size_t size) {
  if (!wally_pool || size == 0) {
    return NULL;
  }
  return mempool_alloc(wally_pool, size);
}

static void ew_free_wrapper(void* ptr) {
  if (!wally_pool || !ptr) {
    return;
  }
  mempool_free(wally_pool, ptr);
}

bool psbt_lib_init(void) {
#define REGIONS WALLY_MEMPOOL_REGIONS
  wally_pool = mempool_create(wally);
#undef REGIONS

  if (!wally_pool) {
    return false;
  }

  ew_api_t api = {
    .crypto_random = (ew_crypto_random_cb_t)crypto_random,
    .secure_memzero = ew_secure_memzero,
    .malloc = ew_malloc_wrapper,
    .free = ew_free_wrapper,
    .ecdsa_sign = NULL,
    .ecdsa_verify = NULL,
  };

  ew_error_t result = ew_init(&api);
  if (result != EW_OK) {
    return false;
  }

  return true;
}

#define PSBT_MAX_OUTPUTS          2  // At most 2 outputs: 1 external, 1 change
#define PSBT_MAX_EXTERNAL_OUTPUTS 1
#define PSBT_MAX_CHANGE_OUTPUTS   1
#define PSBT_INDEX_INVALID        (-1)

static psbt_error_t psbt_sum_inputs(ew_psbt_t* psbt, uint64_t* total_input_sats) {
  if (!psbt || !total_input_sats) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *total_input_sats = 0;

  size_t num_inputs = 0;
  ew_error_t err = ew_psbt_get_num_inputs(psbt, &num_inputs);
  if (err != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  for (size_t i = 0; i < num_inputs; i++) {
    bool has_amount = false;
    uint64_t amount = 0;
    err = ew_psbt_input_get_amount(psbt, i, &has_amount, &amount);
    if (err != EW_OK || !has_amount) {
      return PSBT_ERROR_MISSING_UTXO;
    }
    *total_input_sats += amount;
  }

  return PSBT_OK;
}

static psbt_error_t psbt_sum_outputs(ew_psbt_t* psbt, uint64_t* total_output_sats,
                                     int* external_output_index, size_t* external_count,
                                     int* change_output_index, size_t* change_count) {
  if (!psbt || !total_output_sats || !external_output_index || !external_count ||
      !change_output_index || !change_count) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *total_output_sats = 0;
  *external_output_index = PSBT_INDEX_INVALID;
  *change_output_index = PSBT_INDEX_INVALID;
  *external_count = 0;
  *change_count = 0;

  size_t num_outputs = ew_psbt_get_num_outputs(psbt);
  // Validate PSBT shape: must have 1 or 2 outputs
  if (num_outputs == 0 || num_outputs > PSBT_MAX_OUTPUTS) {
    return PSBT_ERROR_INVALID_SHAPE;
  }

  for (size_t i = 0; i < num_outputs; i++) {
    bool has_keypath = false;
    ew_error_t err = ew_psbt_output_has_keypath(psbt, i, &has_keypath);
    if (err != EW_OK) {
      return PSBT_ERROR_PARSE_FAILED;
    }

    // TODO [W-15437]: Assemble spend policy from public keys and validate change output.
    if (has_keypath) {
      *change_output_index = (int)i;
      (*change_count)++;
    } else {
      *external_output_index = (int)i;
      (*external_count)++;
    }

    const uint8_t* script = NULL;
    size_t script_len = 0;
    bool has_amount = false;
    uint64_t amount = 0;
    err = ew_psbt_output_get_info(psbt, i, &script, &script_len, &has_amount, &amount);
    if (err != EW_OK || !has_amount) {
      return PSBT_ERROR_PARSE_FAILED;
    }

    *total_output_sats += amount;
  }

  return PSBT_OK;
}

static psbt_error_t psbt_get_external_destination(ew_psbt_t* psbt, int external_output_index,
                                                  ew_network_t network, char* destination_address,
                                                  uint64_t* send_amount_sats) {
  if (!psbt || !destination_address || !send_amount_sats ||
      external_output_index == PSBT_INDEX_INVALID) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  const uint8_t* script = NULL;
  size_t script_len = 0;
  bool has_amount = false;
  uint64_t amount = 0;

  ew_error_t err = ew_psbt_output_get_info(psbt, (size_t)external_output_index, &script,
                                           &script_len, &has_amount, &amount);
  if (err != EW_OK || !script || script_len == 0) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  err = ew_script_to_address(script, script_len, network, destination_address,
                             DESTINATION_ADDRESS_MAX_LEN);
  if (err != EW_OK) {
    return PSBT_ERROR_ADDRESS_FAILED;
  }

  *send_amount_sats = amount;
  return PSBT_OK;
}

static psbt_error_t psbt_get_change_amount(ew_psbt_t* psbt, int change_output_index,
                                           uint64_t* change_amount_sats) {
  if (!psbt || !change_amount_sats) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  if (change_output_index == PSBT_INDEX_INVALID) {
    *change_amount_sats = 0;
    return PSBT_OK;
  }

  const uint8_t* script = NULL;
  size_t script_len = 0;
  bool has_amount = false;
  uint64_t amount = 0;

  ew_error_t err = ew_psbt_output_get_info(psbt, (size_t)change_output_index, &script, &script_len,
                                           &has_amount, &amount);
  if (err != EW_OK || !has_amount) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  *change_amount_sats = amount;
  return PSBT_OK;
}

psbt_error_t psbt_get_info(const uint8_t* psbt_bytes, size_t psbt_len, ew_network_t network,
                           psbt_info_t* info_out) {
  if (!psbt_bytes || psbt_len == 0 || !info_out) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *info_out = (psbt_info_t){0};

  ew_psbt_t* psbt = NULL;
  ew_error_t ew_err = ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt);
  if (ew_err != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  uint64_t total_input_sats = 0;
  psbt_error_t psbt_err = psbt_sum_inputs(psbt, &total_input_sats);
  if (psbt_err != PSBT_OK) {
    ew_psbt_free(psbt);
    return psbt_err;
  }

  uint64_t total_output_sats = 0;
  int external_output_index = PSBT_INDEX_INVALID;
  int change_output_index = PSBT_INDEX_INVALID;
  size_t external_count = 0;
  size_t change_count = 0;
  psbt_err = psbt_sum_outputs(psbt, &total_output_sats, &external_output_index, &external_count,
                              &change_output_index, &change_count);
  if (psbt_err != PSBT_OK) {
    ew_psbt_free(psbt);
    return psbt_err;
  }

  // Validate shape: at most 1 external, at most 1 change
  if (external_count > PSBT_MAX_EXTERNAL_OUTPUTS || change_count > PSBT_MAX_CHANGE_OUTPUTS) {
    ew_psbt_free(psbt);
    return PSBT_ERROR_INVALID_SHAPE;
  }

  // Fees in bitcoin are implicit. Calculate it here.
  if (total_input_sats < total_output_sats) {
    ew_psbt_free(psbt);
    return PSBT_ERROR_INVALID_SHAPE;  // Invalid: outputs exceed inputs
  }
  info_out->fee_amount_sats = total_input_sats - total_output_sats;

  info_out->has_destination = (external_output_index != PSBT_INDEX_INVALID);
  info_out->send_amount_sats = 0;
  if (info_out->has_destination) {
    psbt_err =
      psbt_get_external_destination(psbt, external_output_index, network,
                                    info_out->destination_address, &info_out->send_amount_sats);
    if (psbt_err != PSBT_OK) {
      ew_psbt_free(psbt);
      return psbt_err;
    }
  }

  psbt_err = psbt_get_change_amount(psbt, change_output_index, &info_out->change_amount_sats);
  if (psbt_err != PSBT_OK) {
    ew_psbt_free(psbt);
    return psbt_err;
  }

  ew_psbt_free(psbt);
  return PSBT_OK;
}

static psbt_error_t psbt_parse_keypath_value(const uint8_t* value, size_t value_len,
                                             psbt_keypath_t* keypath_out) {
  if (!value || !keypath_out || value_len < BIP32_KEY_FINGERPRINT_SIZE) {
    return PSBT_ERROR_INVALID_KEYPATH;
  }

  const size_t path_bytes = value_len - BIP32_KEY_FINGERPRINT_SIZE;
  if (path_bytes % sizeof(uint32_t) != 0) {
    return PSBT_ERROR_INVALID_KEYPATH;
  }

  const size_t path_len = path_bytes / sizeof(uint32_t);
  if (path_len > PSBT_BIP32_PATH_MAX_LEN) {
    return PSBT_ERROR_INVALID_KEYPATH;
  }

  memcpy(keypath_out->fingerprint, value, BIP32_KEY_FINGERPRINT_SIZE);
  keypath_out->path_len = path_len;

  const uint8_t* path_bytes_ptr = value + BIP32_KEY_FINGERPRINT_SIZE;
  for (size_t i = 0; i < path_len; i++) {
    keypath_out->path[i] = read_u32_le(&path_bytes_ptr[i * sizeof(uint32_t)]);
  }

  return PSBT_OK;
}

psbt_error_t psbt_p2wsh_input_signing_data_from_psbt(ew_psbt_t* psbt, size_t input_index,
                                                     psbt_p2wsh_signing_data_t* signing_data_out) {
  if (!psbt || !signing_data_out) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *signing_data_out = (psbt_p2wsh_signing_data_t){0};

  size_t num_inputs = 0;
  if (ew_psbt_get_num_inputs(psbt, &num_inputs) != EW_OK || input_index >= num_inputs) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  size_t keypath_count = 0;
  if (ew_psbt_input_get_keypath_count(psbt, input_index, &keypath_count) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  if (keypath_count != PSBT_P2WSH_MAX_KEYPATHS) {
    return PSBT_ERROR_INVALID_KEYPATH;
  }

  signing_data_out->keypath_count = keypath_count;

  uint8_t pubkeys_concat[PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN] = {0};

  for (size_t i = 0; i < keypath_count; i++) {
    const uint8_t* pubkey = NULL;
    size_t pubkey_len = 0;
    const uint8_t* keypath_value = NULL;
    size_t keypath_value_len = 0;

    if (ew_psbt_input_get_keypath(psbt, input_index, i, &pubkey, &pubkey_len, &keypath_value,
                                  &keypath_value_len) != EW_OK) {
      return PSBT_ERROR_PARSE_FAILED;
    }

    if (!pubkey || pubkey_len != PSBT_P2WSH_PUBKEY_LEN) {
      return PSBT_ERROR_INVALID_KEYPATH;
    }

    memcpy(signing_data_out->keypaths[i].pubkey, pubkey, pubkey_len);
    memcpy(&pubkeys_concat[i * PSBT_P2WSH_PUBKEY_LEN], pubkey, pubkey_len);

    psbt_error_t parse_err =
      psbt_parse_keypath_value(keypath_value, keypath_value_len, &signing_data_out->keypaths[i]);
    if (parse_err != PSBT_OK) {
      return parse_err;
    }
  }

  // TODO(W-15437): Build pubkeys from the stored descriptor and validate against PSBT keypaths.
  size_t witness_script_len = 0;
  if (ew_multisig_witness_script_from_pubkeys(
        pubkeys_concat, sizeof(pubkeys_concat), 2, true, signing_data_out->witness_script,
        sizeof(signing_data_out->witness_script), &witness_script_len) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  signing_data_out->witness_script_len = witness_script_len;

  uint8_t expected_scriptpubkey[PSBT_P2WSH_SCRIPTPUBKEY_MAX_LEN] = {0};
  size_t expected_scriptpubkey_len = 0;
  if (ew_p2wsh_scriptpubkey_from_witness(signing_data_out->witness_script,
                                         signing_data_out->witness_script_len,
                                         expected_scriptpubkey, sizeof(expected_scriptpubkey),
                                         &expected_scriptpubkey_len) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  const uint8_t* utxo_script = NULL;
  size_t utxo_script_len = 0;
  if (ew_psbt_input_get_witness_utxo(psbt, input_index, &utxo_script, &utxo_script_len, NULL) !=
      EW_OK) {
    return PSBT_ERROR_MISSING_UTXO;
  }

  if (!utxo_script || utxo_script_len != expected_scriptpubkey_len ||
      memcmp(utxo_script, expected_scriptpubkey, expected_scriptpubkey_len) != 0) {
    return PSBT_ERROR_SCRIPT_MISMATCH;
  }

  if (ew_psbt_get_input_signature_hash(psbt, input_index, signing_data_out->witness_script,
                                       signing_data_out->witness_script_len,
                                       signing_data_out->sighash) != EW_OK) {
    return PSBT_ERROR_SIGHASH_FAILED;
  }

  uint32_t input_sighash = 0;
  if (ew_psbt_input_get_sighash_type(psbt, input_index, &input_sighash) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }
  signing_data_out->sighash_type = input_sighash ? input_sighash : PSBT_SIGHASH_ALL;

  return PSBT_OK;
}

psbt_error_t psbt_compact_sig_to_der(const uint8_t* sig, size_t sig_len, uint8_t* der_out,
                                     size_t der_out_len, size_t* der_len_out) {
  if (!sig || !der_out || !der_len_out) {
    return PSBT_ERROR_INVALID_PARAM;
  }
  if (sig_len != ECC_SIG_SIZE || der_out_len == 0) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  uint8_t normalized[ECC_SIG_SIZE] = {0};
  if (ew_ec_sig_normalize(sig, sig_len, normalized, sizeof(normalized)) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  if (ew_ec_sig_to_der(normalized, sizeof(normalized), der_out, der_out_len, der_len_out) !=
      EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }

  return PSBT_OK;
}
