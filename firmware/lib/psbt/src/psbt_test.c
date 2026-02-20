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

// PSBT generated spending 2-of-3 P2WSH outputs, with BIP67 sorted, keypaths present in each input.
static const char* PSBT_ONE_P2WSH_INPUT_BIP67_SORTED_KEYPATHS =
  "cHNidP8BAH0CAAAAAfh3uudRPGzzvI7Digs6Pakpk8YVtsExRepPMBTt5geXAAAAAAD/////"
  "AlDDAAAAAAAAFgAUS7LVBJvqzVpf/3RM0Zo5ddjU0NkIew4AAAAAACIAIIGNkQEX22NAOzuk"
  "By0YubLDiyFlGDrwzLSmDkmSZsN1AAAAAAABAStAQg8AAAAAACIAIIGNkQEX22NAOzukBy0Y"
  "ubLDiyFlGDrwzLSmDkmSZsN1AQVpUiEC0F2DwR5vmGtLC3hjiX0veoWz5dAE5vegIgA4qZ3v"
  "/jUhAwwLjLEs2f5oWuH5TJ5yvPztfIJHRuxmazcf/kI/+H5EIQPzelBDZcD2eC0SfhHaaR4j"
  "EpQMN31qncErcTpHu9PRtlOuIgYC0F2DwR5vmGtLC3hjiX0veoWz5dAE5vegIgA4qZ3v/jUYy"
  "9rQNlQAAIABAACAAAAAgAAAAAAMAAAAIgYDDAuMsSzZ/mha4flMnnK8/O18gkdG7GZrNx/+Q"
  "j/4fkQYy9rQNlQAAIABAACAAAAAgAAAAAAOAAAAIgYD83pQQ2XA9ngtEn4R2mkeIxKUDDd9a"
  "p3BK3E6R7vT0bYYy9rQNlQAAIABAACAAAAAgAAAAAANAAAAAAAA";

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

// PSBT generated with witness_utxo intentionally omitted.
static const char* PSBT_ONE_P2WSH_INPUT_MISSING_UTXO =
  "cHNidP8BAH0CAAAAAVwxtqayGsOyn/4tZoFIW46GSl4wY1bvR7Ddyuan+e5ZAAAAAAD/////"
  "AlDDAAAAAAAAFgAUH1wOVKREwVY0dVMvCnpaHoDcdIoIew4AAAAAACIAIFsAoKxTjCqfRg7d"
  "yAVzn7NMYs41dJIZrwpsrYKT7W8qAAAAAAABBWlSIQIuaXUVLW423Qx5SfwObugUMwRvAaOR"
  "kb2Uu7hp7BGJ4CEDXEClfPLMDgFgANazfOXlj+Jwiw/0gYPxyZqhHd/7t88hA6+VoSD79Z1g"
  "DgfRBJrBRmVZAWexRGzL5OsaLfCE2NveU64iBgIuaXUVLW423Qx5SfwObugUMwRvAaORkb2U"
  "u7hp7BGJ4BjL2tA2VAAAgAEAAIAAAACAAAAAABIAAAAiBgNcQKV88swOAWAA1rN85eWP4nCL"
  "D/SBg/HJmqEd3/u3zxjL2tA2VAAAgAEAAIAAAACAAAAAABMAAAAiBgOvlaEg+/WdYA4H0QSa"
  "wUZlWQFnsURsy+TrGi3whNjb3hjL2tA2VAAAgAEAAIAAAACAAAAAABEAAAAAAAA=";

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

#pragma mark - P2WSH Signing Data Tests

Test(psbt_test, p2wsh_signing_data_ok, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_BIP67_SORTED_KEYPATHS, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = psbt_p2wsh_input_signing_data_from_psbt(psbt, 0, &signing_data);
  cr_assert_eq(err, PSBT_OK);
  cr_assert_eq(signing_data.keypath_count, 3);
  cr_assert(signing_data.witness_script_len > 0);

  ew_psbt_free(psbt);
}

Test(psbt_test, p2wsh_signing_data_sighash_none, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_SIGHASH_NONE, psbt_bytes, sizeof(psbt_bytes),
                               &psbt_len) == EW_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = psbt_p2wsh_input_signing_data_from_psbt(psbt, 0, &signing_data);
  cr_assert_eq(err, PSBT_OK);
  cr_assert_eq(signing_data.sighash_type, 0x02);

  ew_psbt_free(psbt);
}

Test(psbt_test, p2wsh_signing_data_uncompressed_pubkey, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_BIP67_SORTED_KEYPATHS, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  struct wally_psbt* wally_psbt = NULL;
  cr_assert_eq(wally_psbt_from_bytes(psbt_bytes, psbt_len, 0, &wally_psbt), WALLY_OK);

  struct wally_map* keypaths = &wally_psbt->inputs[0].keypaths;
  size_t keypath_count = 0;
  cr_assert_eq(wally_map_get_num_items(keypaths, &keypath_count), WALLY_OK);
  cr_assert_eq(keypath_count, 3);

  size_t key_len = 0;
  cr_assert_eq(wally_map_get_item_key_length(keypaths, 0, &key_len), WALLY_OK);
  cr_assert_eq(key_len, EC_PUBLIC_KEY_LEN);

  uint8_t key[EC_PUBLIC_KEY_UNCOMPRESSED_LEN] = {0};
  size_t key_written = 0;
  cr_assert_eq(wally_map_get_item_key(keypaths, 0, key, key_len, &key_written), WALLY_OK);
  cr_assert_eq(key_written, key_len);

  uint8_t fingerprint[BIP32_KEY_FINGERPRINT_LEN] = {0};
  cr_assert_eq(
    wally_map_keypath_get_item_fingerprint(keypaths, 0, fingerprint, sizeof(fingerprint)),
    WALLY_OK);

  size_t path_len = 0;
  cr_assert_eq(wally_map_keypath_get_item_path_len(keypaths, 0, &path_len), WALLY_OK);
  cr_assert(path_len <= PSBT_BIP32_PATH_MAX_LEN);

  uint32_t path[PSBT_BIP32_PATH_MAX_LEN] = {0};
  size_t path_written = 0;
  cr_assert_eq(wally_map_keypath_get_item_path(keypaths, 0, path, path_len, &path_written),
               WALLY_OK);
  cr_assert_eq(path_written, path_len);

  secp256k1_context* ctx =
    secp256k1_context_create(SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);
  cr_assert(ctx != NULL);

  secp256k1_pubkey pubkey = {0};
  cr_assert(secp256k1_ec_pubkey_parse(ctx, &pubkey, key, key_len));

  uint8_t uncompressed[EC_PUBLIC_KEY_UNCOMPRESSED_LEN] = {0};
  size_t uncompressed_len = sizeof(uncompressed);
  cr_assert(secp256k1_ec_pubkey_serialize(ctx, uncompressed, &uncompressed_len, &pubkey,
                                          SECP256K1_EC_UNCOMPRESSED));

  cr_assert_eq(wally_map_remove(keypaths, key, key_len), WALLY_OK);
  cr_assert_eq(wally_map_keypath_add(keypaths, uncompressed, uncompressed_len, fingerprint,
                                     sizeof(fingerprint), path, path_len),
               WALLY_OK);

  uint8_t* modified_psbt = NULL;
  size_t modified_psbt_len = 0;
  cr_assert(wally_psbt_serialize_alloc(wally_psbt, &modified_psbt, &modified_psbt_len));

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(modified_psbt, modified_psbt_len, &psbt), EW_OK);

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = psbt_p2wsh_input_signing_data_from_psbt(psbt, 0, &signing_data);
  cr_assert_eq(err, PSBT_ERROR_INVALID_KEYPATH);

  ew_psbt_free(psbt);
  wally_psbt_free(wally_psbt);
  secp256k1_context_destroy(ctx);
  free(modified_psbt);
}

Test(psbt_test, p2wsh_signing_data_invalid_keypath, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_ONE_CHANGE_OUTPUT, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = psbt_p2wsh_input_signing_data_from_psbt(psbt, 0, &signing_data);
  cr_assert_eq(err, PSBT_ERROR_INVALID_KEYPATH);

  ew_psbt_free(psbt);
}

Test(psbt_test, p2wsh_signing_data_script_mismatch, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_BIP67_SORTED_KEYPATHS, psbt_bytes,
                               sizeof(psbt_bytes), &psbt_len) == EW_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  const uint8_t* script = NULL;
  size_t script_len = 0;
  cr_assert_eq(ew_psbt_input_get_witness_utxo(psbt, 0, &script, &script_len, NULL), EW_OK);
  cr_assert(script != NULL);
  cr_assert(script_len > 0);

  uint8_t* mutable_script = (uint8_t*)script;
  mutable_script[script_len - 1] ^= 0x01;

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = psbt_p2wsh_input_signing_data_from_psbt(psbt, 0, &signing_data);
  cr_assert_eq(err, PSBT_ERROR_SCRIPT_MISMATCH);

  ew_psbt_free(psbt);
}

Test(psbt_test, p2wsh_signing_data_missing_utxo, .init = psbt_setup, .fini = psbt_teardown) {
  uint8_t psbt_bytes[2048];
  size_t psbt_len = 0;
  cr_assert(ew_base64_to_bytes(PSBT_ONE_P2WSH_INPUT_MISSING_UTXO, psbt_bytes, sizeof(psbt_bytes),
                               &psbt_len) == EW_OK);

  ew_psbt_t* psbt = NULL;
  cr_assert_eq(ew_psbt_from_bytes(psbt_bytes, psbt_len, &psbt), EW_OK);

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = psbt_p2wsh_input_signing_data_from_psbt(psbt, 0, &signing_data);
  cr_assert_eq(err, PSBT_ERROR_MISSING_UTXO);

  ew_psbt_free(psbt);
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
