#include "psbt.h"

#include "bitops.h"
#include "ew.h"
#include "hash.h"
#include "mempool.h"
#include "secure_rng.h"
#include "wstring.h"

#include <string.h>

static void ew_secure_memzero(void* const pnt, const size_t len) {
  memzero(pnt, len);
}

static bool ew_crypto_random_shim(uint8_t* out, size_t len) {
  return !crypto_random(out, (uint32_t)len);
}

// TODO(W-16140): Pool sized for 1 input / 1 output. Resize regions to support up to 5 inputs.
// See KEY_MANAGER_MAX_INPUTS in key_manager_psbt_limits.h.
#define WALLY_MEMPOOL_REGIONS(X) \
  X(wally, r0, 48, 20)           \
  X(wally, r1, 192, 8)           \
  X(wally, r2, 896, 2)

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
    .crypto_random = (ew_crypto_random_cb_t)ew_crypto_random_shim,
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

    // NOTE [W-15437]: The psbt library layer only has app-supplied pubkeys, not the stored keyset.
    // Change output validation against the wallet policy (wallet_change_output_belongs_to_policy)
    // must be performed by the caller once the keyset is available. The raw_tx path enforces this
    // in key_manager_task_port.c. The PSBT path (psbt_get_info) currently lacks keyset access at
    // this layer and requires a future architectural change to pass the keyset in or validate
    // output scripts above this layer.
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

static psbt_error_t psbt_sum_inputs_from_fields(const psbt_tx_input_info_t* inputs,
                                                size_t input_count, uint64_t* total_input_sats) {
  if (!total_input_sats || (!inputs && input_count > 0)) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *total_input_sats = 0;
  for (size_t i = 0; i < input_count; i++) {
    if (UINT64_MAX - *total_input_sats < inputs[i].amount_sats) {
      return PSBT_ERROR_INVALID_SHAPE;
    }
    *total_input_sats += inputs[i].amount_sats;
  }

  return PSBT_OK;
}

static psbt_error_t psbt_sum_outputs_from_fields(const psbt_tx_output_info_t* outputs,
                                                 size_t output_count, uint64_t* total_output_sats,
                                                 int* external_output_index, size_t* external_count,
                                                 int* change_output_index, size_t* change_count) {
  if (!outputs || !total_output_sats || !external_output_index || !external_count ||
      !change_output_index || !change_count) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *total_output_sats = 0;
  *external_output_index = PSBT_INDEX_INVALID;
  *change_output_index = PSBT_INDEX_INVALID;
  *external_count = 0;
  *change_count = 0;

  // Validate tx output shape: must have 1 or 2 outputs.
  if (output_count == 0 || output_count > PSBT_MAX_OUTPUTS) {
    return PSBT_ERROR_INVALID_SHAPE;
  }

  for (size_t i = 0; i < output_count; i++) {
    if (outputs[i].has_keypath) {
      *change_output_index = (int)i;
      (*change_count)++;
    } else {
      *external_output_index = (int)i;
      (*external_count)++;
    }

    if (UINT64_MAX - *total_output_sats < outputs[i].amount_sats) {
      return PSBT_ERROR_INVALID_SHAPE;
    }
    *total_output_sats += outputs[i].amount_sats;
  }

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

psbt_error_t psbt_get_info_from_tx_fields(const psbt_tx_input_info_t* inputs, size_t input_count,
                                          const psbt_tx_output_info_t* outputs, size_t output_count,
                                          ew_network_t network, psbt_info_t* info_out) {
  if (!info_out) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *info_out = (psbt_info_t){0};

  uint64_t total_input_sats = 0;
  psbt_error_t psbt_err = psbt_sum_inputs_from_fields(inputs, input_count, &total_input_sats);
  if (psbt_err != PSBT_OK) {
    return psbt_err;
  }

  uint64_t total_output_sats = 0;
  int external_output_index = PSBT_INDEX_INVALID;
  int change_output_index = PSBT_INDEX_INVALID;
  size_t external_count = 0;
  size_t change_count = 0;
  psbt_err =
    psbt_sum_outputs_from_fields(outputs, output_count, &total_output_sats, &external_output_index,
                                 &external_count, &change_output_index, &change_count);
  if (psbt_err != PSBT_OK) {
    return psbt_err;
  }

  // Validate shape: at most 1 external, at most 1 change.
  if (external_count > PSBT_MAX_EXTERNAL_OUTPUTS || change_count > PSBT_MAX_CHANGE_OUTPUTS) {
    return PSBT_ERROR_INVALID_SHAPE;
  }

  if (total_input_sats < total_output_sats) {
    return PSBT_ERROR_INVALID_SHAPE;
  }
  info_out->fee_amount_sats = total_input_sats - total_output_sats;

  info_out->has_destination = (external_output_index != PSBT_INDEX_INVALID);
  if (info_out->has_destination) {
    const psbt_tx_output_info_t* external = &outputs[external_output_index];
    if (!external->script_pubkey || external->script_pubkey_len == 0) {
      return PSBT_ERROR_PARSE_FAILED;
    }

    if (ew_script_to_address(external->script_pubkey, external->script_pubkey_len, network,
                             info_out->destination_address, DESTINATION_ADDRESS_MAX_LEN) != EW_OK) {
      return PSBT_ERROR_ADDRESS_FAILED;
    }
    info_out->send_amount_sats = external->amount_sats;
  }

  if (change_output_index != PSBT_INDEX_INVALID) {
    info_out->change_amount_sats = outputs[change_output_index].amount_sats;
  }

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

// ---------------------------------------------------------------------------
// Non-PSBT transaction helpers
// ---------------------------------------------------------------------------

psbt_error_t raw_tx_get_info(const raw_tx_input_t* inputs, size_t num_inputs,
                             const raw_tx_output_t* outputs, size_t num_outputs,
                             ew_network_t network, psbt_info_t* info_out) {
  if (!inputs || num_inputs == 0 || !outputs || num_outputs == 0 || !info_out) {
    return PSBT_ERROR_INVALID_PARAM;
  }
  if (num_inputs > RAW_TX_MAX_INPUTS || num_outputs > RAW_TX_MAX_OUTPUTS) {
    return PSBT_ERROR_INVALID_SHAPE;
  }

  memset(info_out, 0, sizeof(*info_out));

  // Sum input amounts (with overflow protection)
  uint64_t total_input_sats = 0;
  for (size_t i = 0; i < num_inputs; i++) {
    if (UINT64_MAX - total_input_sats < inputs[i].amount) {
      return PSBT_ERROR_INVALID_SHAPE;
    }
    total_input_sats += inputs[i].amount;
  }

  // Classify outputs and sum output amounts
  uint64_t total_output_sats = 0;
  int external_index = -1;
  int change_index = -1;
  size_t external_count = 0;
  size_t change_count = 0;

  for (size_t i = 0; i < num_outputs; i++) {
    if (UINT64_MAX - total_output_sats < outputs[i].amount) {
      return PSBT_ERROR_INVALID_SHAPE;
    }
    total_output_sats += outputs[i].amount;

    if (outputs[i].has_derivation_path) {
      // NOTE(W-15437): Policy validation (wallet_change_output_belongs_to_policy) is performed
      // by the caller in key_manager_task_port.c after this function returns and outputs are
      // classified, where the wallet keyset is available. The psbt library layer does not have
      // keyset access.
      change_index = (int)i;
      change_count++;
    } else {
      external_index = (int)i;
      external_count++;
    }
  }

  // Validate shape: at most 1 external + 1 change
  if (external_count > 1 || change_count > 1) {
    return PSBT_ERROR_INVALID_SHAPE;
  }

  // Fee
  if (total_input_sats < total_output_sats) {
    return PSBT_ERROR_INVALID_SHAPE;
  }
  info_out->fee_amount_sats = total_input_sats - total_output_sats;

  // External destination
  if (external_index >= 0) {
    const raw_tx_output_t* ext_out = &outputs[external_index];
    ew_error_t err =
      ew_script_to_address(ext_out->destination_spk, ext_out->destination_spk_len, network,
                           info_out->destination_address, DESTINATION_ADDRESS_MAX_LEN);
    if (err != EW_OK) {
      return PSBT_ERROR_ADDRESS_FAILED;
    }
    info_out->has_destination = true;
    info_out->send_amount_sats = ext_out->amount;
  }

  // Change
  if (change_index >= 0) {
    info_out->change_amount_sats = outputs[change_index].amount;
  }

  return PSBT_OK;
}

// ---------------------------------------------------------------------------
// BIP143 sighash computation from raw fields (zero wally allocations)
// ---------------------------------------------------------------------------
// BIP143 preimage for SIGHASH_ALL:
//   hash256(nVersion || hashPrevouts || hashSequence || outpoint ||
//           scriptCode || amount || nSequence || hashOutputs ||
//           nLocktime || nHashType)
//
// Where hash256 = SHA256(SHA256(x)), and:
//   hashPrevouts = hash256(all outpoints: txid(32) || index(4LE))
//   hashSequence = hash256(all sequences: seq(4LE))
//   hashOutputs  = hash256(all outputs: amount(8LE) || varint(scriptLen) || script)

// Write a uint32 in little-endian to buf. Returns 4.
static inline size_t write_le32(uint8_t* buf, uint32_t val) {
  buf[0] = (uint8_t)(val);
  buf[1] = (uint8_t)(val >> 8);
  buf[2] = (uint8_t)(val >> 16);
  buf[3] = (uint8_t)(val >> 24);
  return 4;
}

// Write a uint64 in little-endian to buf. Returns 8.
static inline size_t write_le64(uint8_t* buf, uint64_t val) {
  buf[0] = (uint8_t)(val);
  buf[1] = (uint8_t)(val >> 8);
  buf[2] = (uint8_t)(val >> 16);
  buf[3] = (uint8_t)(val >> 24);
  buf[4] = (uint8_t)(val >> 32);
  buf[5] = (uint8_t)(val >> 40);
  buf[6] = (uint8_t)(val >> 48);
  buf[7] = (uint8_t)(val >> 56);
  return 8;
}

// Write a Bitcoin varint for lengths < 0xFD. Returns bytes written.
static inline size_t write_varint(uint8_t* buf, size_t val) {
  if (val < 0xFD) {
    buf[0] = (uint8_t)val;
    return 1;
  }
  // Witness scripts for 2-of-3 multisig are ~105 bytes, always < 253.
  // If needed for larger values, extend here.
  return 0;
}

// crypto_sha256_stream_update takes non-const uint8_t* but doesn't modify it.
// Wrap to accept const data without -Wdiscarded-qualifiers.
static bool sha256_feed(void* ctx, const uint8_t* data, size_t len) {
  return crypto_sha256_stream_update(ctx, (uint8_t*)(uintptr_t)data, (uint32_t)len);
}

// Helper: feed a LE32 into a SHA256 stream
static bool sha256_feed_le32(void* ctx, uint32_t val) {
  uint8_t buf[4];
  write_le32(buf, val);
  return sha256_feed(ctx, buf, 4);
}

// Helper: feed a LE64 into a SHA256 stream
static bool sha256_feed_le64(void* ctx, uint64_t val) {
  uint8_t buf[8];
  write_le64(buf, val);
  return sha256_feed(ctx, buf, 8);
}

// Finalize a streaming SHA256 context as double-SHA256 (hash256).
// Computes SHA256(SHA256(data_fed_to_ctx)).
static bool double_sha256_stream_finish(hash_stream_ctx_t* inner_ctx,
                                        uint8_t out[SHA256_DIGEST_SIZE]) {
  uint8_t inner_hash[SHA256_DIGEST_SIZE];
  if (!crypto_sha256_stream_final(inner_ctx, inner_hash))
    return false;
  // Second SHA256 pass
  hash_stream_ctx_t outer;
  if (!crypto_sha256_stream_init(&outer))
    return false;
  if (!sha256_feed(&outer, inner_hash, SHA256_DIGEST_SIZE))
    return false;
  return crypto_sha256_stream_final(&outer, out);
}

static psbt_error_t bip143_sighash(const raw_tx_input_t* inputs, size_t num_inputs,
                                   const raw_tx_output_t* outputs, size_t num_outputs,
                                   uint32_t lock_time, uint32_t version, size_t input_index,
                                   const uint8_t* script_code, size_t script_code_len,
                                   uint32_t sighash_type, uint8_t sighash_out[SHA256_DIGEST_SIZE]) {
  hash_stream_ctx_t ctx;

  // 1. hashPrevouts = hash256(concat of all outpoints)
  uint8_t hash_prevouts[SHA256_DIGEST_SIZE];
  if (!crypto_sha256_stream_init(&ctx))
    return PSBT_ERROR_SIGHASH_FAILED;
  for (size_t i = 0; i < num_inputs; i++) {
    if (!sha256_feed(&ctx, inputs[i].prev_txid, 32))
      return PSBT_ERROR_SIGHASH_FAILED;
    if (!sha256_feed_le32(&ctx, inputs[i].prev_index))
      return PSBT_ERROR_SIGHASH_FAILED;
  }
  if (!double_sha256_stream_finish(&ctx, hash_prevouts))
    return PSBT_ERROR_SIGHASH_FAILED;

  // 2. hashSequence = hash256(concat of all sequences)
  uint8_t hash_sequence[SHA256_DIGEST_SIZE];
  if (!crypto_sha256_stream_init(&ctx))
    return PSBT_ERROR_SIGHASH_FAILED;
  for (size_t i = 0; i < num_inputs; i++) {
    if (!sha256_feed_le32(&ctx, inputs[i].sequence))
      return PSBT_ERROR_SIGHASH_FAILED;
  }
  if (!double_sha256_stream_finish(&ctx, hash_sequence))
    return PSBT_ERROR_SIGHASH_FAILED;

  // 3. hashOutputs = hash256(concat of all outputs)
  uint8_t hash_outputs[SHA256_DIGEST_SIZE];
  if (!crypto_sha256_stream_init(&ctx))
    return PSBT_ERROR_SIGHASH_FAILED;
  for (size_t i = 0; i < num_outputs; i++) {
    if (!sha256_feed_le64(&ctx, outputs[i].amount))
      return PSBT_ERROR_SIGHASH_FAILED;
    uint8_t varbuf[1];
    size_t vlen = write_varint(varbuf, outputs[i].destination_spk_len);
    if (vlen == 0)
      return PSBT_ERROR_SIGHASH_FAILED;
    if (!sha256_feed(&ctx, varbuf, vlen))
      return PSBT_ERROR_SIGHASH_FAILED;
    if (!sha256_feed(&ctx, outputs[i].destination_spk, outputs[i].destination_spk_len))
      return PSBT_ERROR_SIGHASH_FAILED;
  }
  if (!double_sha256_stream_finish(&ctx, hash_outputs))
    return PSBT_ERROR_SIGHASH_FAILED;

  // 4. Compute final sighash = hash256(preimage) using streaming SHA256
  if (!crypto_sha256_stream_init(&ctx))
    return PSBT_ERROR_SIGHASH_FAILED;

  // nVersion
  if (!sha256_feed_le32(&ctx, version))
    return PSBT_ERROR_SIGHASH_FAILED;
  // hashPrevouts, hashSequence
  if (!sha256_feed(&ctx, hash_prevouts, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed(&ctx, hash_sequence, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  // outpoint
  if (!sha256_feed(&ctx, inputs[input_index].prev_txid, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed_le32(&ctx, inputs[input_index].prev_index))
    return PSBT_ERROR_SIGHASH_FAILED;
  // scriptCode (varint-prefixed)
  uint8_t sc_var[1];
  size_t sc_vlen = write_varint(sc_var, script_code_len);
  if (sc_vlen == 0)
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed(&ctx, sc_var, sc_vlen))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed(&ctx, script_code, script_code_len))
    return PSBT_ERROR_SIGHASH_FAILED;
  // amount + nSequence
  if (!sha256_feed_le64(&ctx, inputs[input_index].amount))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed_le32(&ctx, inputs[input_index].sequence))
    return PSBT_ERROR_SIGHASH_FAILED;
  // hashOutputs
  if (!sha256_feed(&ctx, hash_outputs, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  // nLocktime + nHashType
  if (!sha256_feed_le32(&ctx, lock_time))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed_le32(&ctx, sighash_type))
    return PSBT_ERROR_SIGHASH_FAILED;

  if (!double_sha256_stream_finish(&ctx, sighash_out))
    return PSBT_ERROR_SIGHASH_FAILED;

  return PSBT_OK;
}

psbt_error_t raw_tx_p2wsh_input_signing_data(const raw_tx_input_t* inputs, size_t num_inputs,
                                             const raw_tx_output_t* outputs, size_t num_outputs,
                                             uint32_t lock_time, uint32_t version,
                                             size_t input_index, const uint8_t* keyset_pubkeys,
                                             psbt_p2wsh_signing_data_t* signing_data_out) {
  if (!inputs || num_inputs == 0 || !outputs || num_outputs == 0 || !keyset_pubkeys ||
      !signing_data_out || input_index >= num_inputs) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *signing_data_out = (psbt_p2wsh_signing_data_t){0};

  // The keyset_pubkeys are 3 concatenated 33-byte compressed pubkeys
  // (app, hw, server) already derived to the child level for this input.
  signing_data_out->keypath_count = PSBT_P2WSH_MAX_KEYPATHS;
  for (size_t i = 0; i < PSBT_P2WSH_MAX_KEYPATHS; i++) {
    memcpy(signing_data_out->keypaths[i].pubkey, keyset_pubkeys + (i * PSBT_P2WSH_PUBKEY_LEN),
           PSBT_P2WSH_PUBKEY_LEN);

    // Copy derivation path from the input
    const raw_tx_input_t* inp = &inputs[input_index];
    memcpy(signing_data_out->keypaths[i].path, inp->derivation_path,
           inp->derivation_path_len * sizeof(uint32_t));
    signing_data_out->keypaths[i].path_len = inp->derivation_path_len;
  }

  // Build BIP67-sorted 2-of-3 witness script from the pubkeys
  size_t witness_script_len = 0;
  if (ew_multisig_witness_script_from_pubkeys(
        keyset_pubkeys, PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN, 2, true,
        signing_data_out->witness_script, sizeof(signing_data_out->witness_script),
        &witness_script_len) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }
  signing_data_out->witness_script_len = witness_script_len;

  // Compute BIP143 sighash directly from raw fields (no wally allocations)
  signing_data_out->sighash_type = PSBT_SIGHASH_ALL;
  psbt_error_t err =
    bip143_sighash(inputs, num_inputs, outputs, num_outputs, lock_time, version, input_index,
                   signing_data_out->witness_script, witness_script_len, PSBT_SIGHASH_ALL,
                   signing_data_out->sighash);
  if (err != PSBT_OK) {
    return err;
  }

  return PSBT_OK;
}

// ---------------------------------------------------------------------------
// Session commitment hash
// ---------------------------------------------------------------------------

bool raw_tx_session_commitment_hash(const raw_tx_input_t* inputs, size_t num_inputs,
                                    const raw_tx_output_t* outputs, size_t num_outputs,
                                    uint32_t lock_time, uint32_t version,
                                    uint8_t commitment_out[SHA256_DIGEST_SIZE]) {
  if (!inputs || !outputs || !commitment_out || num_inputs == 0 || num_outputs == 0) {
    return false;
  }

  hash_stream_ctx_t ctx;
  if (!crypto_sha256_stream_init(&ctx)) {
    return false;
  }

// Inline helper macros – undef'd at the end of this function.
#define _HASH_U32(val)                                       \
  do {                                                       \
    uint32_t _v = (uint32_t)(val);                           \
    if (!sha256_feed(&ctx, (const uint8_t*)&_v, sizeof(_v))) \
      return false;                                          \
  } while (0)

#define _HASH_U64(val)                                       \
  do {                                                       \
    uint64_t _v = (uint64_t)(val);                           \
    if (!sha256_feed(&ctx, (const uint8_t*)&_v, sizeof(_v))) \
      return false;                                          \
  } while (0)

#define _HASH_BYTES(ptr, len)                             \
  do {                                                    \
    if (!sha256_feed(&ctx, (const uint8_t*)(ptr), (len))) \
      return false;                                       \
  } while (0)

  _HASH_U32(version);
  _HASH_U32(lock_time);
  _HASH_U32((uint32_t)num_inputs);

  for (size_t i = 0; i < num_inputs; i++) {
    const raw_tx_input_t* inp = &inputs[i];
    _HASH_BYTES(inp->prev_txid, sizeof(inp->prev_txid));
    _HASH_U32(inp->prev_index);
    _HASH_U32(inp->sequence);
    _HASH_U64(inp->amount);
    if (inp->derivation_path_len > PSBT_BIP32_PATH_MAX_LEN) {
      return false;
    }
    _HASH_U32((uint32_t)inp->derivation_path_len);
    _HASH_BYTES(inp->derivation_path, inp->derivation_path_len * sizeof(uint32_t));
  }

  _HASH_U32((uint32_t)num_outputs);

  for (size_t i = 0; i < num_outputs; i++) {
    const raw_tx_output_t* out = &outputs[i];
    _HASH_U64(out->amount);
    if (out->destination_spk_len > sizeof(out->destination_spk)) {
      return false;
    }
    _HASH_U32((uint32_t)out->destination_spk_len);
    _HASH_BYTES(out->destination_spk, out->destination_spk_len);
    uint8_t has_path = out->has_derivation_path ? 1u : 0u;
    _HASH_BYTES(&has_path, sizeof(has_path));
    if (out->has_derivation_path) {
      if (out->derivation_path_len > PSBT_BIP32_PATH_MAX_LEN) {
        return false;
      }
      _HASH_U32((uint32_t)out->derivation_path_len);
      _HASH_BYTES(out->derivation_path, out->derivation_path_len * sizeof(uint32_t));
    }
  }

#undef _HASH_U32
#undef _HASH_U64
#undef _HASH_BYTES

  return crypto_sha256_stream_final(&ctx, commitment_out);
}

// BIP143 sighash using precomputed intermediate hashes (for streaming signing).
// This avoids iterating all inputs/outputs, instead using hashes computed incrementally.
static psbt_error_t bip143_sighash_precomputed(
  const uint8_t hash_prevouts[SHA256_DIGEST_SIZE], const uint8_t hash_sequence[SHA256_DIGEST_SIZE],
  const uint8_t hash_outputs[SHA256_DIGEST_SIZE], uint32_t lock_time, uint32_t version,
  const raw_tx_input_t* input, const uint8_t* script_code, size_t script_code_len,
  uint32_t sighash_type, uint8_t sighash_out[SHA256_DIGEST_SIZE]) {
  hash_stream_ctx_t ctx;

  if (!crypto_sha256_stream_init(&ctx))
    return PSBT_ERROR_SIGHASH_FAILED;

  // nVersion
  if (!sha256_feed_le32(&ctx, version))
    return PSBT_ERROR_SIGHASH_FAILED;
  // hashPrevouts, hashSequence (precomputed)
  if (!sha256_feed(&ctx, hash_prevouts, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed(&ctx, hash_sequence, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  // outpoint
  if (!sha256_feed(&ctx, input->prev_txid, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed_le32(&ctx, input->prev_index))
    return PSBT_ERROR_SIGHASH_FAILED;
  // scriptCode (varint-prefixed)
  uint8_t sc_var[1];
  size_t sc_vlen = write_varint(sc_var, script_code_len);
  if (sc_vlen == 0)
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed(&ctx, sc_var, sc_vlen))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed(&ctx, script_code, script_code_len))
    return PSBT_ERROR_SIGHASH_FAILED;
  // amount + nSequence
  if (!sha256_feed_le64(&ctx, input->amount))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed_le32(&ctx, input->sequence))
    return PSBT_ERROR_SIGHASH_FAILED;
  // hashOutputs (precomputed)
  if (!sha256_feed(&ctx, hash_outputs, 32))
    return PSBT_ERROR_SIGHASH_FAILED;
  // nLocktime + nHashType
  if (!sha256_feed_le32(&ctx, lock_time))
    return PSBT_ERROR_SIGHASH_FAILED;
  if (!sha256_feed_le32(&ctx, sighash_type))
    return PSBT_ERROR_SIGHASH_FAILED;

  return double_sha256_stream_finish(&ctx, sighash_out) ? PSBT_OK : PSBT_ERROR_SIGHASH_FAILED;
}

psbt_error_t raw_tx_p2wsh_input_signing_data_precomputed(
  const uint8_t hash_prevouts[SHA256_DIGEST_SIZE], const uint8_t hash_sequence[SHA256_DIGEST_SIZE],
  const uint8_t hash_outputs[SHA256_DIGEST_SIZE], uint32_t lock_time, uint32_t version,
  const raw_tx_input_t* input, const uint8_t* keyset_pubkeys,
  psbt_p2wsh_signing_data_t* signing_data_out) {
  if (!input || !keyset_pubkeys || !signing_data_out || !hash_prevouts || !hash_sequence ||
      !hash_outputs) {
    return PSBT_ERROR_INVALID_PARAM;
  }

  *signing_data_out = (psbt_p2wsh_signing_data_t){0};

  // Copy pubkeys and derivation path into signing data
  signing_data_out->keypath_count = PSBT_P2WSH_MAX_KEYPATHS;
  for (size_t i = 0; i < PSBT_P2WSH_MAX_KEYPATHS; i++) {
    memcpy(signing_data_out->keypaths[i].pubkey, keyset_pubkeys + (i * PSBT_P2WSH_PUBKEY_LEN),
           PSBT_P2WSH_PUBKEY_LEN);
    memcpy(signing_data_out->keypaths[i].path, input->derivation_path,
           input->derivation_path_len * sizeof(uint32_t));
    signing_data_out->keypaths[i].path_len = input->derivation_path_len;
  }

  // Build BIP67-sorted 2-of-3 witness script
  size_t witness_script_len = 0;
  if (ew_multisig_witness_script_from_pubkeys(
        keyset_pubkeys, PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN, 2, true,
        signing_data_out->witness_script, sizeof(signing_data_out->witness_script),
        &witness_script_len) != EW_OK) {
    return PSBT_ERROR_PARSE_FAILED;
  }
  signing_data_out->witness_script_len = witness_script_len;

  // Compute BIP143 sighash using precomputed intermediate hashes
  signing_data_out->sighash_type = PSBT_SIGHASH_ALL;
  return bip143_sighash_precomputed(hash_prevouts, hash_sequence, hash_outputs, lock_time, version,
                                    input, signing_data_out->witness_script, witness_script_len,
                                    PSBT_SIGHASH_ALL, signing_data_out->sighash);
}
