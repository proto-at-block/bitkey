#include "criterion_test_utils.h"
#include "ew.h"
#include "fff.h"
#include "psbt.h"
#include "rtos.h"

#include <criterion/criterion.h>

#include <secp256k1.h>
#include <stdlib.h>
#include <string.h>
#include <wally_psbt.h>

// Forward declaration for getentropy (macOS/Linux)
extern int getentropy(void* buffer, size_t length);

#pragma mark - Fakes

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

#pragma mark - Platform setup

static bool platform_crypto_random(uint8_t* out, size_t len) {
  return getentropy(out, len) == 0 ? 0 : 2;
}

static void platform_secure_memzero(void* p, size_t n) {
  volatile uint8_t* vp = (volatile uint8_t*)p;
  while (n--) {
    *vp++ = 0;
  }
}

static void* platform_malloc(size_t n) {
  return malloc(n);
}

static void platform_free(void* p) {
  free(p);
}

static bool wally_psbt_serialize_alloc(const struct wally_psbt* psbt, uint8_t** bytes_out,
                                       size_t* len_out) {
  if (!psbt || !bytes_out || !len_out) {
    return false;
  }

  size_t psbt_len = 0;
  if (wally_psbt_get_length(psbt, 0, &psbt_len) != WALLY_OK) {
    return false;
  }

  uint8_t* psbt_bytes = malloc(psbt_len);
  if (!psbt_bytes) {
    return false;
  }

  size_t written = 0;
  if (wally_psbt_to_bytes(psbt, 0, psbt_bytes, psbt_len, &written) != WALLY_OK) {
    free(psbt_bytes);
    return false;
  }

  *bytes_out = psbt_bytes;
  *len_out = written;
  return true;
}

static ew_api_t platform_api = {
  .crypto_random = platform_crypto_random,
  .secure_memzero = platform_secure_memzero,
  .malloc = platform_malloc,
  .free = platform_free,
  .ecdsa_sign = NULL,
  .ecdsa_verify = NULL,
};

#pragma mark - Test fixtures
static void psbt_setup(void) {
  ew_error_t err = ew_init(&platform_api);
  cr_assert_eq(err, EW_OK, "Failed to initialize libew");
}

static void psbt_teardown(void) {
  ew_cleanup();
}

#pragma mark - Parameter Validation Tests

Test(psbt_test, null_bytes_returns_error, .init = psbt_setup, .fini = psbt_teardown) {
  psbt_info_t info;
  psbt_error_t err = psbt_get_info(NULL, 100, EW_NETWORK_MAINNET, &info);
  cr_assert_eq(err, PSBT_ERROR_INVALID_PARAM);
}

Test(psbt_test, zero_len_returns_error, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t dummy[1] = {0};
  psbt_info_t info;
  psbt_error_t err = psbt_get_info(dummy, 0, EW_NETWORK_MAINNET, &info);
  cr_assert_eq(err, PSBT_ERROR_INVALID_PARAM);
}

Test(psbt_test, null_info_out_returns_error, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t dummy[1] = {0};
  psbt_error_t err = psbt_get_info(dummy, 1, EW_NETWORK_MAINNET, NULL);
  cr_assert_eq(err, PSBT_ERROR_INVALID_PARAM);
}

#pragma mark - Parse Error Tests
Test(psbt_test, invalid_psbt_returns_parse_failed, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t invalid_data[] = {0x00, 0x01, 0x02, 0x03};
  psbt_info_t info;
  psbt_error_t err = psbt_get_info(invalid_data, sizeof(invalid_data), EW_NETWORK_MAINNET, &info);
  cr_assert_eq(err, PSBT_ERROR_PARSE_FAILED);
}

#pragma mark - Valid PSBT Tests

// PSBT with 1 input (500,000,000 sats) and 1 output (499,990,000 sats)
// This PSBT has BIP32 derivation info on the output (making it a "change" output)
// Fee: 10,000 sats
// From BIP 174, Case: PSBT with one P2WSH input of a 2-of-2 multisig. witnessScript, keypaths, and
// global xpubs are available. Contains no signatures. Outputs filled.
static const char* PSBT_ONE_P2WSH_INPUT_ONE_CHANGE_OUTPUT =
  "cHNidP8BAFICAAAAAZ38ZijCbFiZ/hvT3DOGZb/VXXraEPYiCXPfLTht7BJ2AQAAAAD/////"
  "AfA9zR0AAAAAFgAUezoAv9wU0neVwrdJAdCdpu8TNXkAAAAATwEENYfPAto/"
  "0AiAAAAAlwSLGtBEWx7IJ1UXcnyHtOTrwYogP/"
  "oPlMAVZr046QADUbdDiH7h1A3DKmBDck8tZFmztaTXPa7I+64EcvO8Q+IM2QxqT64AAIAAAACATwEENYfPAto/"
  "0AiAAAABuQRSQnE5zXjCz/"
  "JES+NTzVhgXj5RMoXlKLQH+uP2FzUD0wpel8itvFV9rCrZp+"
  "OcFyLrrGnmaLbyZnzB1nHIPKsM2QxqT64AAIABAACAAAEBKwBlzR0AAAAAIgAgLFSGEmxJeAeagU4TcV1l82RZ5NbMre0m"
  "bQUIZFuvpjIBBUdSIQKdoSzbWyNWkrkVNq/"
  "v5ckcOrlHPY5DtTODarRWKZyIcSEDNys0I07Xz5wf6l0F1EFVeSe+"
  "lUKxYusC4ass6AIkwAtSriIGAp2hLNtbI1aSuRU2r+/"
  "lyRw6uUc9jkO1M4NqtFYpnIhxENkMak+uAACAAAAAgAAAAAAiBgM3KzQjTtfPnB/"
  "qXQXUQVV5J76VQrFi6wLhqyzoAiTACxDZDGpPrgAAgAEAAIAAAAAAACICA57/"
  "H1R6HV+S36K6evaslxpL0DukpzSwMVaiVritOh75EO3kXMUAAACAAAAAgAEAAIAA";

Test(psbt_test, one_input_one_change_output, .init = psbt_setup, .fini = psbt_teardown) {
  // Decode base64 to raw bytes
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_ONE_CHANGE_OUTPUT, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  psbt_info_t info;
  psbt_error_t err = psbt_get_info(psbt_bytes, psbt_len, EW_NETWORK_MAINNET, &info);
  cr_assert_eq(err, PSBT_OK, "Expected PSBT_OK");
  cr_assert_eq(info.send_amount_sats, 0, "Should have no amount");

  // This PSBT has only a change output (has keypath), so no external destination
  cr_assert_eq(info.has_destination, false, "Should not have external destination");
  cr_assert_eq(info.change_amount_sats, 499990000, "Change should be 499990000 sats");
  cr_assert_eq(info.fee_amount_sats, 10000, "Fee should be 10000 sats");
}

static const char* PSBT_ONE_P2WSH_INPUT_ONE_EXTERNAL_OUTPUT =
  "cHNidP8BAFICAAAAAZ38ZijCbFiZ/hvT3DOGZb/VXXraEPYiCXPfLTht7BJ2AQAAAAD/////"
  "AfA9zR0AAAAAFgAUezoAv9wU0neVwrdJAdCdpu8TNXkAAAAATwEENYfPAto/"
  "0AiAAAAAlwSLGtBEWx7IJ1UXcnyHtOTrwYogP/"
  "oPlMAVZr046QADUbdDiH7h1A3DKmBDck8tZFmztaTXPa7I+64EcvO8Q+IM2QxqT64AAIAAAACATwEENYfPAto/"
  "0AiAAAABuQRSQnE5zXjCz/"
  "JES+NTzVhgXj5RMoXlKLQH+uP2FzUD0wpel8itvFV9rCrZp+"
  "OcFyLrrGnmaLbyZnzB1nHIPKsM2QxqT64AAIABAACAAAEBKwBlzR0AAAAAIgAgLFSGEmxJeAeagU4TcV1l82RZ5NbMre0mbQ"
  "UIZFuvpjIBBUdSIQKdoSzbWyNWkrkVNq/"
  "v5ckcOrlHPY5DtTODarRWKZyIcSEDNys0I07Xz5wf6l0F1EFVeSe+lUKxYusC4ass6AIkwAtSriIGAp2hLNtbI1aSuRU2r+/"
  "lyRw6uUc9jkO1M4NqtFYpnIhxENkMak+uAACAAAAAgAAAAAAiBgM3KzQjTtfPnB/"
  "qXQXUQVV5J76VQrFi6wLhqyzoAiTACxDZDGpPrgAAgAEAAIAAAAAAAAA=";

// PSBT generated spending 2-of-3 P2WSH output with SIGHASH_NONE set on the input.
// We use a non-default sighash here (instead of SIGHASH_ALL) to ensure we don't
// silently fall back to PSBT_SIGHASH_ALL when an explicit input sighash is present.
static const char* PSBT_ONE_P2WSH_INPUT_SIGHASH_NONE =
  "cHNidP8BAH0CAAAAAY5XDGTnqYwk5JUHj1ifcmfoAQxeyoZ/gtGpTqEibiV/AAAAAAD/////"
  "AlDDAAAAAAAAFgAUXde3kgB/0k8Dl6kqrrl7Zg6vahQIew4AAAAAACIAIOLl7MsYBzoKr1it"
  "LpJ3lNgTipPvM8MIxLCPe2oClkR/AAAAAAABAStAQg8AAAAAACIAIOLl7MsYBzoKr1itLpJ3"
  "lNgTipPvM8MIxLCPe2oClkR/AQMEAgAAAAEFaVIhAs3eRp0TAFOI2tR5KKN/IfrYwAeRS3su"
  "+KyugB8p0MtxIQNRyrXD7maAfN41pEHUsBDqKK+9LdnaJifspDK/NO1PDiED8cXUW2oKRU9m"
  "t79RX5QIwejTDUwOZzzvC22Nbk59bMlTriIGAs3eRp0TAFOI2tR5KKN/IfrYwAeRS3su+Kyu"
  "gB8p0MtxGMva0DZUAACAAQAAgAAAAIAAAAAAFwAAACIGA1HKtcPuZoB83jWkQdSwEOoor70t"
  "2domJ+ykMr807U8OGMva0DZUAACAAQAAgAAAAIAAAAAAFgAAACIGA/HF1FtqCkVPZre/UV+U"
  "CMHo0w1MDmc87wttjW5OfWzJGMva0DZUAACAAQAAgAAAAIAAAAAAGAAAAAAAAA==";

Test(psbt_test, one_input_no_outputs, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_ONE_EXTERNAL_OUTPUT, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  psbt_info_t info;
  psbt_error_t err = psbt_get_info(psbt_bytes, psbt_len, EW_NETWORK_MAINNET, &info);

  cr_assert_eq(err, PSBT_OK, "Expected PSBT_OK");
  cr_assert_eq(info.send_amount_sats, 499990000, "Should have correct amount (499990000 sats)");

  // This PSBT has only a external output (no keypath), so no change output
  cr_assert_eq(info.has_destination, true, "Should have external destination");
  cr_assert_eq(info.change_amount_sats, 0, "Should have no change amount");
  cr_assert_eq(info.fee_amount_sats, 10000, "Fee should be 10000 sats");
}

static void build_raw_tx_fields_from_psbt(ew_psbt_t* psbt, psbt_tx_input_info_t* inputs_out,
                                          size_t* input_count_out,
                                          psbt_tx_output_info_t* outputs_out,
                                          size_t* output_count_out) {
  cr_assert_not_null(psbt);
  cr_assert_not_null(inputs_out);
  cr_assert_not_null(input_count_out);
  cr_assert_not_null(outputs_out);
  cr_assert_not_null(output_count_out);

  size_t input_count = 0;
  cr_assert_eq(ew_psbt_get_num_inputs(psbt, &input_count), EW_OK);
  *input_count_out = input_count;

  for (size_t i = 0; i < input_count; i++) {
    bool has_amount = false;
    uint64_t amount = 0;
    cr_assert_eq(ew_psbt_input_get_amount(psbt, i, &has_amount, &amount), EW_OK);
    cr_assert(has_amount);
    inputs_out[i].amount_sats = amount;
  }

  const size_t output_count = ew_psbt_get_num_outputs(psbt);
  *output_count_out = output_count;

  for (size_t i = 0; i < output_count; i++) {
    bool has_keypath = false;
    cr_assert_eq(ew_psbt_output_has_keypath(psbt, i, &has_keypath), EW_OK);

    const uint8_t* script = NULL;
    size_t script_len = 0;
    bool has_amount = false;
    uint64_t amount = 0;
    cr_assert_eq(ew_psbt_output_get_info(psbt, i, &script, &script_len, &has_amount, &amount),
                 EW_OK);
    cr_assert(has_amount);

    outputs_out[i].amount_sats = amount;
    outputs_out[i].script_pubkey = script;
    outputs_out[i].script_pubkey_len = script_len;
    outputs_out[i].has_keypath = has_keypath;
  }
}

Test(psbt_test, raw_tx_info_matches_psbt_info_external_output, .init = psbt_setup,
     .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_ONE_EXTERNAL_OUTPUT, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  psbt_info_t from_psbt = {0};
  cr_assert_eq(psbt_get_info(psbt_bytes, psbt_len, EW_NETWORK_MAINNET, &from_psbt), PSBT_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  psbt_tx_input_info_t inputs[4] = {0};
  psbt_tx_output_info_t outputs[4] = {0};
  size_t input_count = 0;
  size_t output_count = 0;
  build_raw_tx_fields_from_psbt(psbt, inputs, &input_count, outputs, &output_count);

  psbt_info_t from_raw = {0};
  cr_assert_eq(psbt_get_info_from_tx_fields(inputs, input_count, outputs, output_count,
                                            EW_NETWORK_MAINNET, &from_raw),
               PSBT_OK);
  cr_assert_eq(from_raw.has_destination, from_psbt.has_destination);
  cr_assert_str_eq(from_raw.destination_address, from_psbt.destination_address);
  cr_assert_eq(from_raw.send_amount_sats, from_psbt.send_amount_sats);
  cr_assert_eq(from_raw.change_amount_sats, from_psbt.change_amount_sats);
  cr_assert_eq(from_raw.fee_amount_sats, from_psbt.fee_amount_sats);

  ew_psbt_free(psbt);
}

Test(psbt_test, raw_tx_info_matches_psbt_info_change_only, .init = psbt_setup,
     .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_ONE_CHANGE_OUTPUT, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  psbt_info_t from_psbt = {0};
  cr_assert_eq(psbt_get_info(psbt_bytes, psbt_len, EW_NETWORK_MAINNET, &from_psbt), PSBT_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  psbt_tx_input_info_t inputs[4] = {0};
  psbt_tx_output_info_t outputs[4] = {0};
  size_t input_count = 0;
  size_t output_count = 0;
  build_raw_tx_fields_from_psbt(psbt, inputs, &input_count, outputs, &output_count);

  psbt_info_t from_raw = {0};
  cr_assert_eq(psbt_get_info_from_tx_fields(inputs, input_count, outputs, output_count,
                                            EW_NETWORK_MAINNET, &from_raw),
               PSBT_OK);
  cr_assert_eq(from_raw.has_destination, from_psbt.has_destination);
  cr_assert_eq(from_raw.send_amount_sats, from_psbt.send_amount_sats);
  cr_assert_eq(from_raw.change_amount_sats, from_psbt.change_amount_sats);
  cr_assert_eq(from_raw.fee_amount_sats, from_psbt.fee_amount_sats);

  ew_psbt_free(psbt);
}

Test(psbt_test, raw_tx_info_two_external_outputs_invalid_shape, .init = psbt_setup,
     .fini = psbt_teardown) {
  const uint8_t p2wpkh_script[] = {
    0x00, 0x14, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
    0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14,
  };

  const psbt_tx_input_info_t inputs[] = {
    {.amount_sats = 100000},
  };
  const psbt_tx_output_info_t outputs[] = {
    {
      .amount_sats = 40000,
      .script_pubkey = p2wpkh_script,
      .script_pubkey_len = sizeof(p2wpkh_script),
      .has_keypath = false,
    },
    {
      .amount_sats = 50000,
      .script_pubkey = p2wpkh_script,
      .script_pubkey_len = sizeof(p2wpkh_script),
      .has_keypath = false,
    },
  };

  psbt_info_t info = {0};
  cr_assert_eq(
    psbt_get_info_from_tx_fields(inputs, sizeof(inputs) / sizeof(inputs[0]), outputs,
                                 sizeof(outputs) / sizeof(outputs[0]), EW_NETWORK_MAINNET, &info),
    PSBT_ERROR_INVALID_SHAPE);
}

Test(psbt_test, raw_tx_info_external_bad_script_returns_address_failed, .init = psbt_setup,
     .fini = psbt_teardown) {
  const uint8_t invalid_script[] = {0x6a, 0x01, 0x01};

  const psbt_tx_input_info_t inputs[] = {
    {.amount_sats = 100000},
  };
  const psbt_tx_output_info_t outputs[] = {
    {
      .amount_sats = 90000,
      .script_pubkey = invalid_script,
      .script_pubkey_len = sizeof(invalid_script),
      .has_keypath = false,
    },
  };

  psbt_info_t info = {0};
  cr_assert_eq(
    psbt_get_info_from_tx_fields(inputs, sizeof(inputs) / sizeof(inputs[0]), outputs,
                                 sizeof(outputs) / sizeof(outputs[0]), EW_NETWORK_MAINNET, &info),
    PSBT_ERROR_ADDRESS_FAILED);
}

#pragma mark - Invalid PSBT Tests
// PSBT with 1 input (500,000,000 sats) and 2 external outputs (499,990,000 sats and 10,000 sats)
static const char* PSBT_ONE_P2WSH_INPUT_TWO_EXTERNAL_OUTPUTS =
  "cHNidP8BAHECAAAAAU42ULGwnFSmlWp8jJhNj94xzbwFKj6H+c8FxxgIKC/WAAAAAAD/////"
  "AlDDAAAAAAAAFgAUQfTrbe8GU7b+cgflYFlcuJTRXz5QwwAAAAAAABYAFGTzyg6O3pPE/2s"
  "f1HJFLxKCZbXEAAAAAAABASsAZc0dAAAAACIAIBx4j6uwnyNwKJV/03mzFWZj80wOLtF37t"
  "AoPhTvBtAmAQVpUiEDRkUGpBQvAHjkJOnYVelrWCKfcE+AwOmAzy3pYlK2L/ghAtzvWNgU"
  "nTKQNUYYNn7aqj7+NoRzxDgW147iRK/tx+vlIQI3NtLSBbCR3Xfxomb5ZmtXkj/sePJmKId"
  "WwUpFJ4PsvFOuAAAA";

Test(psbt_test, one_input_two_external_outputs, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_TWO_EXTERNAL_OUTPUTS, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  psbt_info_t info;
  psbt_error_t err = psbt_get_info(psbt_bytes, psbt_len, EW_NETWORK_MAINNET, &info);
  cr_assert_eq(err, PSBT_ERROR_INVALID_SHAPE);
}

#pragma mark - Signature Helper Tests

Test(psbt_test, psbt_add_signature_roundtrip, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_SIGHASH_NONE, psbt_bytes, sizeof(psbt_bytes),
                               &psbt_len) == EW_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  secp256k1_context* ctx =
    secp256k1_context_create(SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);
  cr_assert(ctx != NULL);

  uint8_t seckey[32] = {0};
  seckey[31] = 0x02;
  uint8_t msg[32] = {0};
  msg[0] = 0x03;

  secp256k1_ecdsa_signature sig = {0};
  cr_assert(secp256k1_ecdsa_sign(ctx, &sig, msg, seckey, NULL, NULL));

  uint8_t compact_sig[ECC_SIG_SIZE] = {0};
  cr_assert(secp256k1_ecdsa_signature_serialize_compact(ctx, compact_sig, &sig));

  uint8_t der_sig[PSBT_DER_SIGNATURE_MAX_LEN] = {0};
  size_t der_sig_len = 0;
  cr_assert_eq(psbt_compact_sig_to_der(compact_sig, sizeof(compact_sig), der_sig, sizeof(der_sig),
                                       &der_sig_len),
               PSBT_OK);

  uint8_t signature[PSBT_SIGNATURE_MAX_LEN] = {0};
  memcpy(signature, der_sig, der_sig_len);
  signature[der_sig_len] = 0x02;
  const size_t signature_len = der_sig_len + 1;

  secp256k1_pubkey pubkey = {0};
  cr_assert(secp256k1_ec_pubkey_create(ctx, &pubkey, seckey));

  uint8_t pubkey_bytes[PSBT_P2WSH_PUBKEY_LEN] = {0};
  size_t pubkey_len = sizeof(pubkey_bytes);
  cr_assert(secp256k1_ec_pubkey_serialize(ctx, pubkey_bytes, &pubkey_len, &pubkey,
                                          SECP256K1_EC_COMPRESSED));

  cr_assert_eq(
    ew_psbt_input_add_signature(psbt, 0, pubkey_bytes, pubkey_len, signature, signature_len),
    EW_OK);

  uint8_t psbt_out[2048];
  size_t psbt_out_len = 0;
  cr_assert_eq(ew_psbt_to_bytes(psbt, psbt_out, sizeof(psbt_out), &psbt_out_len), EW_OK);

  struct wally_psbt* wally_psbt = NULL;
  cr_assert_eq(wally_psbt_from_bytes(psbt_out, psbt_out_len, 0, &wally_psbt), WALLY_OK);
  size_t written = 0;
  cr_assert_eq(
    wally_psbt_input_find_signature(&wally_psbt->inputs[0], pubkey_bytes, pubkey_len, &written),
    WALLY_OK);
  cr_assert(written > 0);

  wally_psbt_free(wally_psbt);
  ew_psbt_free(psbt);
  secp256k1_context_destroy(ctx);
}

Test(psbt_test, psbt_add_signature_sighash_mismatch, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_SIGHASH_NONE, psbt_bytes, sizeof(psbt_bytes),
                               &psbt_len) == EW_OK);

  struct wally_psbt* wally_psbt = NULL;
  cr_assert_eq(wally_psbt_from_bytes(psbt_bytes, psbt_len, 0, &wally_psbt), WALLY_OK);
  cr_assert_eq(wally_psbt_input_set_sighash(&wally_psbt->inputs[0], PSBT_SIGHASH_ALL), WALLY_OK);

  uint8_t* modified_psbt = NULL;
  size_t modified_psbt_len = 0;
  cr_assert(wally_psbt_serialize_alloc(wally_psbt, &modified_psbt, &modified_psbt_len));

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(modified_psbt, modified_psbt_len, &psbt), EW_OK);

  secp256k1_context* ctx =
    secp256k1_context_create(SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);
  cr_assert(ctx != NULL);

  uint8_t seckey[32] = {0};
  seckey[31] = 0x03;
  uint8_t msg[32] = {0};
  msg[0] = 0x04;

  secp256k1_ecdsa_signature sig = {0};
  cr_assert(secp256k1_ecdsa_sign(ctx, &sig, msg, seckey, NULL, NULL));

  uint8_t compact_sig[ECC_SIG_SIZE] = {0};
  cr_assert(secp256k1_ecdsa_signature_serialize_compact(ctx, compact_sig, &sig));

  uint8_t der_sig[PSBT_DER_SIGNATURE_MAX_LEN] = {0};
  size_t der_sig_len = 0;
  cr_assert_eq(psbt_compact_sig_to_der(compact_sig, sizeof(compact_sig), der_sig, sizeof(der_sig),
                                       &der_sig_len),
               PSBT_OK);

  uint8_t signature[PSBT_SIGNATURE_MAX_LEN] = {0};
  memcpy(signature, der_sig, der_sig_len);
  signature[der_sig_len] = 0x02;
  const size_t signature_len = der_sig_len + 1;

  secp256k1_pubkey pubkey = {0};
  cr_assert(secp256k1_ec_pubkey_create(ctx, &pubkey, seckey));

  uint8_t pubkey_bytes[PSBT_P2WSH_PUBKEY_LEN] = {0};
  size_t pubkey_len = sizeof(pubkey_bytes);
  cr_assert(secp256k1_ec_pubkey_serialize(ctx, pubkey_bytes, &pubkey_len, &pubkey,
                                          SECP256K1_EC_COMPRESSED));

  cr_assert_eq(
    ew_psbt_input_add_signature(psbt, 0, pubkey_bytes, pubkey_len, signature, signature_len),
    EW_ERROR_INTERNAL);

  ew_psbt_free(psbt);
  wally_psbt_free(wally_psbt);
  secp256k1_context_destroy(ctx);
  free(modified_psbt);
}

// ---------------------------------------------------------------------------
// Session commitment hash tests (W-16257)
//
// Verify that raw_tx_session_commitment_hash() produces stable, unique digests
// that detect any mutation to the canonical signing fields.  These tests are
// the foundation of the "bind confirmation to signing session" invariant:
// if the live signing_session diverges from what was shown to the user, the
// recomputed hash will differ from the one stored in the confirmation manager
// and signing will be rejected.
// ---------------------------------------------------------------------------

static void build_test_session(raw_tx_input_t* inputs, size_t* num_inputs, raw_tx_output_t* outputs,
                               size_t* num_outputs, uint32_t* lock_time, uint32_t* version) {
  *num_inputs = 1;
  *num_outputs = 1;
  *lock_time = 0;
  *version = 2;

  memset(inputs[0].prev_txid, 0xab, 32);
  inputs[0].prev_index = 0;
  inputs[0].sequence = 0xffffffff;
  inputs[0].amount = 100000;
  // BIP84 path: m/84'/0'/0'/0/0
  inputs[0].derivation_path[0] = 84 | 0x80000000u;
  inputs[0].derivation_path[1] = 0 | 0x80000000u;
  inputs[0].derivation_path[2] = 0 | 0x80000000u;
  inputs[0].derivation_path[3] = 0;
  inputs[0].derivation_path[4] = 0;
  inputs[0].derivation_path_len = 5;

  // External output (no derivation path = recipient)
  outputs[0].amount = 90000;
  // Minimal P2WPKH scriptPubKey: OP_0 <20-byte hash>
  outputs[0].destination_spk[0] = 0x00;
  outputs[0].destination_spk[1] = 0x14;
  memset(&outputs[0].destination_spk[2], 0xcd, 20);
  outputs[0].destination_spk_len = 22;
  outputs[0].has_derivation_path = false;
  outputs[0].derivation_path_len = 0;
}

Test(psbt_test, session_commitment_hash_null_params_returns_false, .init = psbt_setup,
     .fini = psbt_teardown) {
  uint8_t hash[SHA256_DIGEST_SIZE] = {0};
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};

  cr_assert_eq(raw_tx_session_commitment_hash(NULL, 1, outputs, 1, 0, 2, hash), false);
  cr_assert_eq(raw_tx_session_commitment_hash(inputs, 1, NULL, 1, 0, 2, hash), false);
  cr_assert_eq(raw_tx_session_commitment_hash(inputs, 1, outputs, 1, 0, 2, NULL), false);
  cr_assert_eq(raw_tx_session_commitment_hash(inputs, 0, outputs, 1, 0, 2, hash), false);
  cr_assert_eq(raw_tx_session_commitment_hash(inputs, 1, outputs, 0, 0, 2, hash), false);
}

Test(psbt_test, session_commitment_hash_is_deterministic, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash1[SHA256_DIGEST_SIZE] = {0};
  uint8_t hash2[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash1));
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash2));
  cr_assert_arr_eq(hash1, hash2, SHA256_DIGEST_SIZE);
}

Test(psbt_test, session_commitment_hash_changes_on_input_amount_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  inputs[0].amount += 1;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when input amount changes");
}

Test(psbt_test, session_commitment_hash_changes_on_output_amount_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  outputs[0].amount -= 1000;  // fee attack: reduce recipient amount
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when output amount changes");
}

Test(psbt_test, session_commitment_hash_changes_on_output_scriptpubkey_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  // Address substitution attack: flip one byte of destination scriptPubKey
  outputs[0].destination_spk[5] ^= 0xff;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when destination scriptPubKey changes");
}

Test(psbt_test, session_commitment_hash_changes_on_version_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_v2[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_v2));
  uint8_t hash_v1[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time, 1,
                                           hash_v1));
  cr_assert(memcmp(hash_v2, hash_v1, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ for different tx versions");
}

Test(psbt_test, session_commitment_hash_changes_on_lock_time_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  uint8_t hash_locktime[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time + 1,
                                           version, hash_locktime));
  cr_assert(memcmp(hash_orig, hash_locktime, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when lock_time changes");
}

Test(psbt_test, session_commitment_hash_changes_on_input_txid_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  // UTXO substitution: flip one byte of prev_txid
  inputs[0].prev_txid[31] ^= 0x01;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when input txid changes");
}

Test(psbt_test, session_commitment_hash_changes_on_derivation_path_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  // Change last (index) component of the input derivation path
  inputs[0].derivation_path[4] = 99;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when derivation path changes");
}

Test(psbt_test, session_commitment_hash_changes_on_input_sequence_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  inputs[0].sequence ^= 0x01u;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when input sequence changes");
}

Test(psbt_test, session_commitment_hash_changes_on_input_prev_index_mutation, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  inputs[0].prev_index ^= 0x01u;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when input prev_index changes");
}

Test(psbt_test, session_commitment_hash_changes_on_output_derivation_path_mutation,
     .init = psbt_setup, .fini = psbt_teardown) {
  raw_tx_input_t inputs[1] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);

  // Configure output as a change output with a derivation path so that
  // the hash includes the output's derivation_path fields.
  outputs[0].has_derivation_path = true;
  outputs[0].derivation_path[0] = 84 | 0x80000000u;
  outputs[0].derivation_path[1] = 0 | 0x80000000u;
  outputs[0].derivation_path[2] = 0 | 0x80000000u;
  outputs[0].derivation_path[3] = 1;
  outputs[0].derivation_path[4] = 0;
  outputs[0].derivation_path_len = 5;

  uint8_t hash_orig[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_orig));
  // Mutate the last component of the output derivation path
  outputs[0].derivation_path[4] ^= 0x01u;
  uint8_t hash_mutated[SHA256_DIGEST_SIZE] = {0};
  cr_assert(raw_tx_session_commitment_hash(inputs, num_inputs, outputs, num_outputs, lock_time,
                                           version, hash_mutated));
  cr_assert(memcmp(hash_orig, hash_mutated, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ when output derivation path changes");
}

Test(psbt_test, session_commitment_hash_two_inputs_vs_one_differ, .init = psbt_setup,
     .fini = psbt_teardown) {
  raw_tx_input_t inputs[2] = {0};
  raw_tx_output_t outputs[1] = {0};
  size_t num_inputs, num_outputs;
  uint32_t lock_time, version;
  build_test_session(inputs, &num_inputs, outputs, &num_outputs, &lock_time, &version);
  cr_assert_eq(num_inputs, 1u);

  uint8_t hash_one[SHA256_DIGEST_SIZE] = {0};
  cr_assert(
    raw_tx_session_commitment_hash(inputs, 1, outputs, num_outputs, lock_time, version, hash_one));

  inputs[1] = inputs[0];
  inputs[1].prev_index = 1;

  uint8_t hash_two[SHA256_DIGEST_SIZE] = {0};
  cr_assert(
    raw_tx_session_commitment_hash(inputs, 2, outputs, num_outputs, lock_time, version, hash_two));
  cr_assert(memcmp(hash_one, hash_two, SHA256_DIGEST_SIZE) != 0,
            "Hash must differ for different input counts");
}
