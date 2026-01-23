#include "ew.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <wally_address.h>
#include <wally_bip32.h>
#include <wally_core.h>
#include <wally_crypto.h>
#include <wally_psbt.h>
#include <wally_script.h>
#include <wally_transaction.h>

static struct {
  bool initialized;
} ctx = {
  .initialized = false,
};

static ew_api_t api = {0};

struct ew_psbt {
  struct wally_psbt* inner;
};

// Bech32 human-readable prefixes for addresses per BIP173
static const char* EW_BECH32_MAINNET_PREFIX = "bc";
static const char* EW_BECH32_TESTNET_PREFIX = "tb";

/* libwally init + ops override + secp context randomization */
static ew_error_t internal_wally_init(void) {
  if (wally_init(0) != WALLY_OK) {
    return EW_ERROR_WALLY_INIT_FAILED;
  }

  /* Override libwally alloc/zero/sign/verify with platform's; libwally copies this struct. */
  struct wally_operations ops = {
    .struct_size = sizeof(struct wally_operations),
    .malloc_fn = api.malloc,
    .free_fn = api.free,
    .bzero_fn = api.secure_memzero,
    .secp_context_fn = NULL,
    .get_error_fn = NULL,
    .set_error_fn = NULL,
    .ec_sig_from_bytes_fn = api.ecdsa_sign,
    .ec_sig_verify_fn = api.ecdsa_verify,
  };

  if (wally_set_operations(&ops) != WALLY_OK) {
    return EW_ERROR_WALLY_INIT_FAILED;
  }

  /* Randomize libsecp context for hardening */
  uint8_t buf[WALLY_SECP_RANDOMIZE_LEN] = {0};
  ew_error_t err = api.crypto_random(buf, sizeof(buf));
  if (err != EW_OK) {
    api.secure_memzero(buf, sizeof(buf));
    wally_cleanup(0);
    return err;
  }

  if (wally_secp_randomize(buf, sizeof(buf)) != WALLY_OK) {
    api.secure_memzero(buf, sizeof(buf));
    wally_cleanup(0);
    return EW_ERROR_INTERNAL;
  }

  api.secure_memzero(buf, sizeof(buf));
  return EW_OK;
}

ew_error_t ew_init(const ew_api_t* platform_api) {
  if (ctx.initialized) {
    return EW_OK;
  }

  if (!platform_api) {
    return EW_ERROR_INVALID_PARAM;
  }

  // Validate required callbacks up-front so we never register or call through NULL function
  // pointers. Optional callbacks (like custom ECDSA sign/verify) may legitimately be NULL and are
  // handled elsewhere.
  if (!platform_api->crypto_random || !platform_api->secure_memzero || !platform_api->malloc ||
      !platform_api->free) {
    return EW_ERROR_INVALID_PARAM;
  }

  memcpy(&api, platform_api, sizeof(api));

  ew_error_t err = internal_wally_init();
  if (err != EW_OK) {
    return err;
  }

  ctx.initialized = true;
  return EW_OK;
}

void ew_cleanup(void) {
  if (ctx.initialized) {
    wally_cleanup(0);
    memset(&api, 0, sizeof(api));
    ctx.initialized = false;
  }
}

ew_error_t ew_seed_generate(uint8_t seed_out[EW_SEED_SIZE]) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!seed_out) {
    return EW_ERROR_INVALID_PARAM;
  }

  ew_error_t err = api.crypto_random(seed_out, EW_SEED_SIZE);
  if (err != EW_OK) {
    api.secure_memzero(seed_out, EW_SEED_SIZE);
    return err;
  }

  return EW_OK;
}

/*
 * Calculate the maximum size needed for a signed PSBT.
 * This provides a conservative upper bound without actually signing.
 */
ew_error_t ew_psbt_get_max_signed_size(const uint8_t* psbt_bytes, size_t psbt_len,
                                       size_t* size_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!psbt_bytes || psbt_len == 0 || !size_out) {
    return EW_ERROR_INVALID_PARAM;
  }

  /* Parse PSBT to get number of inputs */
  struct wally_psbt* psbt = NULL;
  int ret = wally_psbt_from_bytes(psbt_bytes, psbt_len, WALLY_PSBT_PARSE_FLAG_STRICT, &psbt);
  if (ret != WALLY_OK || !psbt) {
    return EW_ERROR_INVALID_PSBT;
  }

  size_t num_inputs = psbt->num_inputs;
  wally_psbt_free(psbt);

  /* Calculate maximum possible size:
   * - Each signature adds up to 72 bytes (DER encoding)
   * - PSBT encoding overhead per signature:
   *   - 1 byte key length
   *   - 1 byte key type (PSBT_IN_PARTIAL_SIG = 0x02)
   *   - 33 bytes public key (part of the key)
   *   - 1 byte value length
   *   - Total overhead: ~37 bytes
   * - Conservative total: 72 + 37 = 109 bytes per input
   */
  const size_t MAX_SIG_SIZE = 110;
  *size_out = psbt_len + (num_inputs * MAX_SIG_SIZE);

  return EW_OK;
}

/*
 * Parse PSBT from bytes (not base64), sign all applicable inputs using the seed-derived master key.
 * The caller must clear the seed after calling this function.
 */
ew_error_t ew_psbt_sign(const uint8_t* psbt_bytes, size_t psbt_len, uint8_t* psbt_out,
                        size_t psbt_out_size, size_t* psbt_out_len,
                        const uint8_t seed[EW_SEED_SIZE], bool network_mainnet) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!psbt_bytes || psbt_len == 0 || !psbt_out || psbt_out_size == 0 || !psbt_out_len || !seed) {
    return EW_ERROR_INVALID_PARAM;
  }

  /* Clear output buffer (non-secret) and length */
  memset(psbt_out, 0, psbt_out_size);
  *psbt_out_len = 0;

  /* Parse PSBT from bytes */
  struct wally_psbt* psbt = NULL;
  int ret = wally_psbt_from_bytes(psbt_bytes, psbt_len, WALLY_PSBT_PARSE_FLAG_STRICT, &psbt);
  if (ret != WALLY_OK || !psbt) {
    return EW_ERROR_INVALID_PSBT;
  }

  /* Derive master key from seed */
  struct ext_key master;
  uint32_t version = network_mainnet ? BIP32_VER_MAIN_PRIVATE : BIP32_VER_TEST_PRIVATE;

  ret = bip32_key_from_seed(seed, EW_SEED_SIZE, version, 0 /* compute hash160 for fingerprint */,
                            &master);
  if (ret != WALLY_OK) {
    wally_psbt_free(psbt);
    api.secure_memzero(&master, sizeof(master));
    return EW_ERROR_INTERNAL;
  }

  /* Sign using libwally's signing (which will use custom callbacks if set via ew_api_t) */
  ret = wally_psbt_sign_bip32(psbt, &master, EC_FLAG_GRIND_R);
  api.secure_memzero(&master, sizeof(master));
  if (ret != WALLY_OK) {
    wally_psbt_free(psbt);
    return EW_ERROR_SIGNING_FAILED;
  }

  /* Check that we signed something at all; although not technically an error, we should never
   * receive a PSBT that has no matching inputs. */
  bool signed_something = false;
  for (size_t i = 0; i < psbt->num_inputs; i++) {
    if (psbt->inputs[i].signatures.num_items > 0) {
      signed_something = true;
      break;
    }
  }

  if (!signed_something) {
    wally_psbt_free(psbt);
    return EW_ERROR_NO_MATCHING_INPUTS;
  }

  /* Serialize signed PSBT back to bytes */
  size_t written = 0;
  ret = wally_psbt_to_bytes(psbt, 0, psbt_out, psbt_out_size, &written);
  wally_psbt_free(psbt);

  if (ret != WALLY_OK) {
    return EW_ERROR_INVALID_PARAM;
  }

  *psbt_out_len = written;
  return EW_OK;
}

ew_error_t ew_script_to_address(const uint8_t* script, size_t script_len, ew_network_t network,
                                char* address_out, size_t address_len) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }

  if (!script || script_len == 0 || !address_out || address_len == 0) {
    return EW_ERROR_INVALID_PARAM;
  }

  size_t script_type = 0;
  int ret = wally_scriptpubkey_get_type(script, script_len, &script_type);
  if (ret != WALLY_OK) {
    return EW_ERROR_INVALID_SCRIPT_PUBKEY;
  }

  bool is_segwit = (script_type & WALLY_SCRIPT_TYPE_P2WPKH) ||
                   (script_type & WALLY_SCRIPT_TYPE_P2WSH) ||
                   (script_type & WALLY_SCRIPT_TYPE_P2TR);

  if (is_segwit) {
    const char* addr_family;
    switch (network) {
      case EW_NETWORK_MAINNET:
        addr_family = EW_BECH32_MAINNET_PREFIX;
        break;
      default:
        addr_family = EW_BECH32_TESTNET_PREFIX;
        break;
    }

    char* segwit_addr = NULL;
    ret = wally_addr_segwit_from_bytes(script, script_len, addr_family, 0, &segwit_addr);
    if (ret != WALLY_OK || !segwit_addr) {
      return EW_ERROR_INVALID_SCRIPT_PUBKEY;
    }

    size_t segwit_len = strlen(segwit_addr);
    if (segwit_len + 1 > address_len) {  // +1 for null terminator
      wally_free_string(segwit_addr);
      return EW_ERROR_ADDRESS_CONVERSION_FAILED;
    }

    memcpy(address_out, segwit_addr, segwit_len + 1);
    wally_free_string(segwit_addr);
    return EW_OK;
  } else {
    uint32_t wally_network = (network == EW_NETWORK_MAINNET) ? WALLY_NETWORK_BITCOIN_MAINNET
                                                             : WALLY_NETWORK_BITCOIN_TESTNET;
    char* legacy_addr = NULL;
    ret = wally_scriptpubkey_to_address(script, script_len, wally_network, &legacy_addr);
    if (ret != WALLY_OK || !legacy_addr) {
      return EW_ERROR_INVALID_SCRIPT_PUBKEY;
    }

    size_t legacy_len = strlen(legacy_addr);
    if (legacy_len >= address_len) {
      wally_free_string(legacy_addr);
      return EW_ERROR_ADDRESS_CONVERSION_FAILED;
    }

    memcpy(address_out, legacy_addr, legacy_len + 1);
    wally_free_string(legacy_addr);
    return EW_OK;
  }
}

void ew_psbt_free(ew_psbt_t* psbt) {
  if (!psbt) {
    return;
  }

  if (psbt->inner) {
    wally_psbt_free(psbt->inner);
  }
  api.free(psbt);
}

ew_error_t ew_psbt_get_num_inputs(const ew_psbt_t* psbt, size_t* num_inputs_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }

  if (!psbt || !psbt->inner || !num_inputs_out) {
    return EW_ERROR_INVALID_PARAM;
  }

  *num_inputs_out = psbt->inner->num_inputs;
  return EW_OK;
}

size_t ew_psbt_get_num_outputs(const ew_psbt_t* psbt) {
  if (!psbt || !psbt->inner) {
    return 0;
  }
  return psbt->inner->num_outputs;
}

uint32_t ew_psbt_get_version(const ew_psbt_t* psbt) {
  if (!psbt || !psbt->inner) {
    return 0;
  }
  return psbt->inner->version;
}

ew_error_t ew_psbt_input_get_amount(const ew_psbt_t* psbt, size_t index, bool* has_amount_out,
                                    uint64_t* amount_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!psbt || !psbt->inner || index >= psbt->inner->num_inputs) {
    return EW_ERROR_INVALID_PARAM;
  }

  const struct wally_psbt_input* input = &psbt->inner->inputs[index];
  bool has_amount = false;
  uint64_t amount = 0;

  if (input->witness_utxo) {
    amount = input->witness_utxo->satoshi;
    has_amount = true;
  } else if (input->utxo) {
    uint32_t utxo_index = input->index;
    if (input->utxo->num_outputs > utxo_index) {
      amount = input->utxo->outputs[utxo_index].satoshi;
      has_amount = true;
    }
  }

  if (has_amount_out) {
    *has_amount_out = has_amount;
  }
  if (amount_out) {
    *amount_out = has_amount ? amount : 0;
  }

  return has_amount ? EW_OK : EW_ERROR_MISSING_UTXO;
}

ew_error_t ew_psbt_output_get_info(const ew_psbt_t* psbt, size_t index, const uint8_t** script_out,
                                   size_t* script_len_out, bool* has_amount_out,
                                   uint64_t* amount_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!psbt || !psbt->inner || index >= psbt->inner->num_outputs) {
    return EW_ERROR_INVALID_PARAM;
  }

  const struct wally_psbt* inner = psbt->inner;
  const unsigned char* script = NULL;
  size_t script_len = 0;
  uint64_t amount = 0;
  bool has_amount = false;

  if (inner->version == WALLY_PSBT_VERSION_0) {
    if (inner->tx && index < inner->tx->num_outputs) {
      const struct wally_tx_output* txo = &inner->tx->outputs[index];
      script = txo->script;
      script_len = txo->script_len;
      amount = txo->satoshi;
      has_amount = true;
    }
  } else {
    const struct wally_psbt_output* psbt_out = &inner->outputs[index];

    if (psbt_out->script && psbt_out->script_len > 0) {
      script = psbt_out->script;
      script_len = psbt_out->script_len;
    } else if (inner->tx && index < inner->tx->num_outputs) {
      const struct wally_tx_output* txo = &inner->tx->outputs[index];
      script = txo->script;
      script_len = txo->script_len;
    }

    if (psbt_out->has_amount) {
      amount = psbt_out->amount;
      has_amount = true;
    } else if (inner->tx && index < inner->tx->num_outputs) {
      amount = inner->tx->outputs[index].satoshi;
      has_amount = true;
    }
  }

  if (script_out) {
    *script_out = (const uint8_t*)script;
  }
  if (script_len_out) {
    *script_len_out = script_len;
  }
  if (has_amount_out) {
    *has_amount_out = has_amount;
  }
  if (amount_out) {
    *amount_out = has_amount ? amount : 0;
  }

  return EW_OK;
}

ew_error_t ew_psbt_from_base64(const char* base64_psbt, ew_psbt_t** psbt_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!base64_psbt || !psbt_out) {
    return EW_ERROR_INVALID_PARAM;
  }

  struct wally_psbt* parsed = NULL;
  int ret = wally_psbt_from_base64(base64_psbt, WALLY_PSBT_PARSE_FLAG_STRICT, &parsed);
  if (ret != WALLY_OK || !parsed) {
    return EW_ERROR_INVALID_PSBT;
  }

  ew_psbt_t* wrapper = api.malloc(sizeof(*wrapper));
  if (!wrapper) {
    wally_psbt_free(parsed);
    return EW_ERROR_INTERNAL;
  }

  wrapper->inner = parsed;
  *psbt_out = wrapper;
  return EW_OK;
}

ew_error_t ew_psbt_from_bytes(const uint8_t* psbt_bytes, size_t psbt_len, ew_psbt_t** psbt_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!psbt_bytes || psbt_len == 0 || !psbt_out) {
    return EW_ERROR_INVALID_PARAM;
  }

  struct wally_psbt* parsed = NULL;
  int ret = wally_psbt_from_bytes(psbt_bytes, psbt_len, WALLY_PSBT_PARSE_FLAG_STRICT, &parsed);
  if (ret != WALLY_OK || !parsed) {
    return EW_ERROR_INVALID_PSBT;
  }

  ew_psbt_t* wrapper = api.malloc(sizeof(*wrapper));
  if (!wrapper) {
    wally_psbt_free(parsed);
    return EW_ERROR_INTERNAL;
  }

  wrapper->inner = parsed;
  *psbt_out = wrapper;
  return EW_OK;
}

ew_error_t ew_psbt_output_has_keypath(const ew_psbt_t* psbt, size_t index, bool* has_keypath_out) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!psbt || !psbt->inner || !has_keypath_out) {
    return EW_ERROR_INVALID_PARAM;
  }
  if (index >= psbt->inner->num_outputs) {
    return EW_ERROR_INVALID_PARAM;
  }

  const struct wally_psbt_output* output = &psbt->inner->outputs[index];
  *has_keypath_out = (output->keypaths.num_items > 0);
  return EW_OK;
}

ew_error_t ew_base64_to_bytes(const char* base64_psbt, uint8_t* out, size_t out_size,
                              size_t* written) {
  if (!ctx.initialized) {
    return EW_ERROR_NOT_INITIALIZED;
  }
  if (!base64_psbt || !out || out_size == 0 || !written) {
    return EW_ERROR_INVALID_PARAM;
  }

  size_t decoded_len = 0;
  int ret = wally_base64_to_bytes(base64_psbt, 0, out, out_size, &decoded_len);
  if (ret != WALLY_OK) {
    return EW_ERROR_INVALID_PSBT;
  }
  *written = decoded_len;
  return EW_OK;
}
