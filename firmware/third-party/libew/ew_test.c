#include "ew.h"
#include "wally_psbt.h"

#include <assert.h>
#include <secp256k1.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wally_address.h>
#include <wally_core.h>
#include <wally_crypto.h>
#include <wally_script.h>

// Forward declaration for getentropy (macOS)
extern int getentropy(void* buffer, size_t length);

// Simple test framework
static int tests_run = 0;
static int tests_passed = 0;
static int tests_failed = 0;

#define TEST_ASSERT(condition, message)                           \
  do {                                                            \
    tests_run++;                                                  \
    if (!(condition)) {                                           \
      printf("  FAIL: %s:%d: %s\n", __FILE__, __LINE__, message); \
      tests_failed++;                                             \
      return 1;                                                   \
    } else {                                                      \
      tests_passed++;                                             \
    }                                                             \
  } while (0)

#define TEST_ASSERT_EQ(a, b, message) TEST_ASSERT((a) == (b), message)

#define TEST_ASSERT_STR_EQ(a, b, message) TEST_ASSERT(strcmp((a), (b)) == 0, message)

#define TEST_ASSERT_NEQ(a, b, message) TEST_ASSERT((a) != (b), message)

#define RUN_TEST(test_func)                \
  do {                                     \
    printf("Running %s...\n", #test_func); \
    int result = test_func();              \
    if (result != 0) {                     \
      printf("  FAILED\n");                \
    } else {                               \
      printf("  PASSED\n");                \
    }                                      \
  } while (0)

// Platform API callbacks for testing
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

// Custom ECDSA signing using libsecp256k1 directly
static int platform_ecdsa_sign(const uint8_t* priv_key, size_t priv_key_len, const uint8_t* bytes,
                               size_t bytes_len, const uint8_t* aux_rand, size_t aux_rand_len,
                               uint32_t flags, uint8_t* bytes_out, size_t len) {
  (void)aux_rand;
  (void)aux_rand_len;
  (void)flags;

  secp256k1_context* ctx = secp256k1_context_create(SECP256K1_CONTEXT_SIGN);
  if (!ctx) {
    return WALLY_ERROR;
  }

  if (!priv_key || priv_key_len != 32 || !bytes || bytes_len != 32 || !bytes_out) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  secp256k1_ecdsa_signature sig;
  if (!secp256k1_ecdsa_sign(ctx, &sig, bytes, priv_key, NULL, NULL)) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  secp256k1_ecdsa_signature_normalize(ctx, &sig, &sig);

  if (len == 64) {
    secp256k1_ecdsa_signature_serialize_compact(ctx, bytes_out, &sig);
  } else {
    size_t sig_len = len;
    if (!secp256k1_ecdsa_signature_serialize_der(ctx, bytes_out, &sig_len, &sig)) {
      secp256k1_context_destroy(ctx);
      return WALLY_ERROR;
    }
  }
  secp256k1_context_destroy(ctx);
  return WALLY_OK;
}

// Custom ECDSA verification using libsecp256k1 directly
static int platform_ecdsa_verify(const uint8_t* pub_key, size_t pub_key_len, const uint8_t* bytes,
                                 size_t bytes_len, uint32_t flags, const uint8_t* sig,
                                 size_t sig_len) {
  (void)flags;

  secp256k1_context* ctx = secp256k1_context_create(SECP256K1_CONTEXT_VERIFY);
  if (!ctx) {
    return WALLY_ERROR;
  }

  if (!pub_key || !bytes || bytes_len != 32 || !sig) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  secp256k1_pubkey pubkey;
  if (!secp256k1_ec_pubkey_parse(ctx, &pubkey, pub_key, pub_key_len)) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  secp256k1_ecdsa_signature signature;
  if (!secp256k1_ecdsa_signature_parse_der(ctx, &signature, sig, sig_len)) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  int result = secp256k1_ecdsa_verify(ctx, &signature, bytes, &pubkey);
  secp256k1_context_destroy(ctx);

  return result ? WALLY_OK : WALLY_ERROR;
}

static ew_api_t platform_api = {
  .crypto_random = platform_crypto_random,
  .secure_memzero = platform_secure_memzero,
  .malloc = platform_malloc,
  .free = platform_free,
  .ecdsa_sign = platform_ecdsa_sign,
  .ecdsa_verify = platform_ecdsa_verify,
};

// Setup function to initialize libew before each test
static void setup(void) {
  ew_error_t err = ew_init(&platform_api);
  assert(err == EW_OK);
}

// Teardown function to cleanup libew after each test
static void teardown(void) {
  ew_cleanup();
}

// ============================================================================
// Initialization and parameter validation tests
// ============================================================================

static int test_not_initialized_returns_error(void) {
  setup();
  ew_cleanup();  // Ensure not initialized
  char address[100] = {0};
  uint8_t script[] = {0x76, 0xa9, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x88, 0xac};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_NOT_INITIALIZED, "Should return EW_ERROR_NOT_INITIALIZED");
  setup();  // Reinitialize for teardown
  teardown();
  return 0;
}

static int test_null_script_returns_error(void) {
  setup();
  char address[100] = {0};
  ew_error_t err = ew_script_to_address(NULL, 25, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM, "Should return EW_ERROR_INVALID_PARAM");
  teardown();
  return 0;
}

static int test_zero_script_len_returns_error(void) {
  setup();
  uint8_t script[] = {0x76, 0xa9, 0x14};
  char address[100] = {0};
  ew_error_t err = ew_script_to_address(script, 0, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM, "Should return EW_ERROR_INVALID_PARAM");
  teardown();
  return 0;
}

static int test_null_address_out_returns_error(void) {
  setup();
  uint8_t script[] = {0x76, 0xa9, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x88, 0xac};
  ew_error_t err = ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, NULL, 100);
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM, "Should return EW_ERROR_INVALID_PARAM");
  teardown();
  return 0;
}

static int test_zero_address_len_returns_error(void) {
  setup();
  uint8_t script[] = {0x76, 0xa9, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x88, 0xac};
  char address[1] = {0};
  ew_error_t err = ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, 0);
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM, "Should return EW_ERROR_INVALID_PARAM");
  teardown();
  return 0;
}

static int test_invalid_script_returns_error(void) {
  setup();
  uint8_t invalid_script[] = {0x00, 0x01, 0x02};  // Invalid script
  char address[100] = {0};
  ew_error_t err = ew_script_to_address(invalid_script, sizeof(invalid_script), EW_NETWORK_MAINNET,
                                        address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_SCRIPT_PUBKEY,
                 "Should return EW_ERROR_INVALID_SCRIPT_PUBKEY");
  teardown();
  return 0;
}

// ============================================================================
// Segwit address tests
// ============================================================================

// Helper to create P2WPKH scriptPubKey: OP_0 (0x00) + 0x14 (20) + 20 bytes hash160
static void create_p2wpkh_script(const uint8_t* hash160, uint8_t* script_out) {
  script_out[0] = 0x00;  // OP_0
  script_out[1] = 0x14;  // Push 20 bytes
  memcpy(script_out + 2, hash160, 20);
}

// Helper to create P2WSH scriptPubKey: OP_0 (0x00) + 0x20 (32) + 32 bytes sha256
static void create_p2wsh_script(const uint8_t* sha256_hash, uint8_t* script_out) {
  script_out[0] = 0x00;  // OP_0
  script_out[1] = 0x20;  // Push 32 bytes
  memcpy(script_out + 2, sha256_hash, 32);
}

// Helper to create P2TR scriptPubKey: OP_1 (0x51) + 0x20 (32) + 32 bytes x-only pubkey
static void create_p2tr_script(const uint8_t* xonly_pubkey, uint8_t* script_out) {
  script_out[0] = 0x51;  // OP_1
  script_out[1] = 0x20;  // Push 32 bytes
  memcpy(script_out + 2, xonly_pubkey, 32);
}

static int test_p2wpkh_mainnet(void) {
  setup();
  // Known P2WPKH test vector: hash160 of a known pubkey
  uint8_t hash160[20] = {0x75, 0x1e, 0x76, 0xe8, 0x19, 0x91, 0x96, 0xd4, 0x54, 0x94,
                         0x1c, 0x45, 0xd1, 0xb3, 0xa3, 0x23, 0xf1, 0x43, 0x3b, 0xd6};
  uint8_t script[22];
  create_p2wpkh_script(hash160, script);

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  TEST_ASSERT_STR_EQ(address, "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                     "Should match expected P2WPKH address");
  teardown();
  return 0;
}

static int test_p2wpkh_testnet(void) {
  setup();
  uint8_t hash160[20] = {0x75, 0x1e, 0x76, 0xe8, 0x19, 0x91, 0x96, 0xd4, 0x54, 0x94,
                         0x1c, 0x45, 0xd1, 0xb3, 0xa3, 0x23, 0xf1, 0x43, 0x3b, 0xd6};
  uint8_t script[22];
  create_p2wpkh_script(hash160, script);

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_TESTNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  TEST_ASSERT_STR_EQ(address, "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
                     "Should match expected P2WPKH testnet address");
  teardown();
  return 0;
}

static int test_p2wsh_mainnet(void) {
  setup();
  // Known P2WSH test vector: sha256 of a known script
  uint8_t sha256[32] = {0x18, 0x63, 0x14, 0x3c, 0x14, 0xc5, 0x16, 0x68, 0x04, 0xbd, 0x19,
                        0x20, 0x33, 0x56, 0xda, 0x13, 0x6c, 0x98, 0x56, 0x78, 0xcd, 0x4d,
                        0x27, 0xa1, 0xb8, 0xc6, 0x32, 0x96, 0x04, 0x90, 0x32, 0x62};
  uint8_t script[34];
  create_p2wsh_script(sha256, script);

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  TEST_ASSERT_STR_EQ(address, "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
                     "Should match expected P2WSH address");
  teardown();
  return 0;
}

// Test vector from BIP350: https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki
static int test_p2wsh_testnet(void) {
  setup();
  uint8_t sha256[32] = {0x18, 0x63, 0x14, 0x3c, 0x14, 0xc5, 0x16, 0x68, 0x04, 0xbd, 0x19,
                        0x20, 0x33, 0x56, 0xda, 0x13, 0x6c, 0x98, 0x56, 0x78, 0xcd, 0x4d,
                        0x27, 0xa1, 0xb8, 0xc6, 0x32, 0x96, 0x04, 0x90, 0x32, 0x62};
  uint8_t script[34];
  create_p2wsh_script(sha256, script);

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_TESTNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  TEST_ASSERT_STR_EQ(address, "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                     "Should match expected P2WSH testnet address");
  teardown();
  return 0;
}

// Test vector from BIP350: https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki
static int test_p2tr_mainnet(void) {
  setup();
  // Known P2TR test vector: x-only pubkey
  uint8_t xonly_pubkey[32] = {0x79, 0xbe, 0x66, 0x7e, 0xf9, 0xdc, 0xbb, 0xac, 0x55, 0xa0, 0x62,
                              0x95, 0xce, 0x87, 0x0b, 0x07, 0x02, 0x9b, 0xfc, 0xdb, 0x2d, 0xce,
                              0x28, 0xd9, 0x59, 0xf2, 0x81, 0x5b, 0x16, 0xf8, 0x17, 0x98};
  uint8_t script[34];
  create_p2tr_script(xonly_pubkey, script);

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // P2TR addresses are longer, verify it starts with bc1p
  TEST_ASSERT_EQ(strncmp(address, "bc1p", 4), 0, "Should start with bc1p");
  TEST_ASSERT_STR_EQ(address, "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0",
                     "Should match expected P2TR mainnet address");
  teardown();
  return 0;
}

// Test vector from BIP350: https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki
static int test_p2tr_testnet(void) {
  setup();
  uint8_t xonly_pubkey[32] = {0x79, 0xbe, 0x66, 0x7e, 0xf9, 0xdc, 0xbb, 0xac, 0x55, 0xa0, 0x62,
                              0x95, 0xce, 0x87, 0x0b, 0x07, 0x02, 0x9b, 0xfc, 0xdb, 0x2d, 0xce,
                              0x28, 0xd9, 0x59, 0xf2, 0x81, 0x5b, 0x16, 0xf8, 0x17, 0x98};
  uint8_t script[34];
  create_p2tr_script(xonly_pubkey, script);

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_TESTNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // P2TR testnet addresses start with tb1p
  TEST_ASSERT_STR_EQ(address, "tb1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vq47zagq",
                     "Should match expected P2TR testnet address");
  teardown();
  return 0;
}

static int test_malformed_segwit_script_returns_error(void) {
  setup();
  // Malformed segwit script: wrong length for P2WPKH (should be 22, but we use 21)
  uint8_t malformed_script[21] = {0x00, 0x14};  // OP_0 + push 20, but script too short
  memset(malformed_script + 2, 0, 19);          // Fill rest with zeros

  char address[100] = {0};
  ew_error_t err = ew_script_to_address(malformed_script, sizeof(malformed_script),
                                        EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_SCRIPT_PUBKEY,
                 "Should return EW_ERROR_INVALID_SCRIPT_PUBKEY");
  teardown();
  return 0;
}

static int test_segwit_buffer_too_small(void) {
  setup();
  uint8_t hash160[20] = {0x75, 0x1e, 0x76, 0xe8, 0x19, 0x91, 0x96, 0xd4, 0x54, 0x94,
                         0x1c, 0x48, 0x38, 0xc7, 0xfb, 0x23, 0xb1, 0xa0, 0x39, 0x21};
  uint8_t script[22];
  create_p2wpkh_script(hash160, script);

  char address[10] = {0};  // Too small buffer
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_ADDRESS_CONVERSION_FAILED,
                 "Should return EW_ERROR_ADDRESS_CONVERSION_FAILED");
  teardown();
  return 0;
}

// ============================================================================
// Legacy address tests
// ============================================================================

static int test_p2pkh_mainnet(void) {
  setup();
  // Known P2PKH test vector
  uint8_t hash160[20] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                         0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00, 0x11, 0x22, 0x33};
  uint8_t script[25];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2pkh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                                &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2PKH script");
  TEST_ASSERT_EQ(written, 25, "Script should be 25 bytes");

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // Verify it's a valid mainnet P2PKH address (starts with '1')
  TEST_ASSERT_EQ(address[0], '1', "Mainnet P2PKH should start with '1'");
  teardown();
  return 0;
}

static int test_p2pkh_testnet(void) {
  setup();
  uint8_t hash160[20] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                         0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00, 0x11, 0x22, 0x33};
  uint8_t script[25];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2pkh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                                &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2PKH script");

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_TESTNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // Verify it's a valid testnet P2PKH address (starts with 'm' or 'n')
  TEST_ASSERT((address[0] == 'm' || address[0] == 'n'),
              "Testnet P2PKH should start with 'm' or 'n'");
  teardown();
  return 0;
}

static int test_p2sh_mainnet(void) {
  setup();
  // Known P2SH test vector
  uint8_t hash160[20] = {0x3f, 0x6a, 0x88, 0x85, 0xa3, 0x08, 0xd3, 0x13, 0x19, 0x8a,
                         0x2e, 0x03, 0x70, 0x73, 0x44, 0xa4, 0x09, 0x38, 0x22, 0x29};
  uint8_t script[23];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2sh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                               &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2SH script");
  TEST_ASSERT_EQ(written, 23, "Script should be 23 bytes");

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // Verify it's a valid mainnet P2SH address (starts with '3')
  TEST_ASSERT_EQ(address[0], '3', "Mainnet P2SH should start with '3'");
  teardown();
  return 0;
}

static int test_p2sh_testnet(void) {
  setup();
  uint8_t hash160[20] = {0x3f, 0x6a, 0x88, 0x85, 0xa3, 0x08, 0xd3, 0x13, 0x19, 0x8a,
                         0x2e, 0x03, 0x70, 0x73, 0x44, 0xa4, 0x09, 0x38, 0x22, 0x29};
  uint8_t script[23];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2sh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                               &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2SH script");

  char address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_TESTNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // Verify it's a valid testnet P2SH address (starts with '2')
  TEST_ASSERT_EQ(address[0], '2', "Testnet P2SH should start with '2'");
  teardown();
  return 0;
}

static int test_legacy_buffer_too_small(void) {
  setup();
  uint8_t hash160[20] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                         0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00, 0x11, 0x22, 0x33};
  uint8_t script[25];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2pkh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                                &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2PKH script");

  char address[10] = {0};  // Too small buffer
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_ERROR_ADDRESS_CONVERSION_FAILED,
                 "Should return EW_ERROR_ADDRESS_CONVERSION_FAILED");
  teardown();
  return 0;
}

// ============================================================================
// Boundary and regression tests
// ============================================================================

static int test_exact_buffer_size_segwit(void) {
  setup();
  uint8_t hash160[20] = {0x75, 0x1e, 0x76, 0xe8, 0x19, 0x91, 0x96, 0xd4, 0x54, 0x94,
                         0x1c, 0x48, 0x38, 0xc7, 0xfb, 0x23, 0xb1, 0xa0, 0x39, 0x21};
  uint8_t script[22];
  create_p2wpkh_script(hash160, script);

  // First get the address to know its length
  char temp_address[100] = {0};
  ew_error_t err = ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, temp_address,
                                        sizeof(temp_address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  size_t addr_len = strlen(temp_address);

  // Now test with exact buffer size (addr_len + 1 for null terminator)
  char address[100] = {0};
  err = ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, addr_len + 1);
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed with exact buffer size");
  TEST_ASSERT_STR_EQ(address, temp_address, "Addresses should match");
  teardown();
  return 0;
}

static int test_exact_buffer_size_legacy(void) {
  setup();
  uint8_t hash160[20] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                         0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00, 0x11, 0x22, 0x33};
  uint8_t script[25];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2pkh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                                &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2PKH script");

  // First get the address to know its length
  char temp_address[100] = {0};
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_MAINNET, temp_address, sizeof(temp_address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  size_t addr_len = strlen(temp_address);

  // Now test with exact buffer size (addr_len + 1 for null terminator)
  char address[100] = {0};
  err = ew_script_to_address(script, written, EW_NETWORK_MAINNET, address, addr_len + 1);
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed with exact buffer size");
  TEST_ASSERT_STR_EQ(address, temp_address, "Addresses should match");
  teardown();
  return 0;
}

static int test_null_terminator_preserved_segwit(void) {
  setup();
  uint8_t hash160[20] = {0x75, 0x1e, 0x76, 0xe8, 0x19, 0x91, 0x96, 0xd4, 0x54, 0x94,
                         0x1c, 0x48, 0x38, 0xc7, 0xfb, 0x23, 0xb1, 0xa0, 0x39, 0x21};
  uint8_t script[22];
  create_p2wpkh_script(hash160, script);

  char address[100];
  memset(address, 0xFF, sizeof(address));  // Fill with non-zero
  ew_error_t err =
    ew_script_to_address(script, sizeof(script), EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // Verify null terminator is present
  size_t addr_len = strlen(address);
  TEST_ASSERT_EQ(address[addr_len], '\0', "Null terminator should be present");
  teardown();
  return 0;
}

static int test_null_terminator_preserved_legacy(void) {
  setup();
  uint8_t hash160[20] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                         0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x00, 0x11, 0x22, 0x33};
  uint8_t script[25];
  size_t written = 0;
  int ret = wally_scriptpubkey_p2pkh_from_bytes(hash160, sizeof(hash160), 0, script, sizeof(script),
                                                &written);
  TEST_ASSERT_EQ(ret, WALLY_OK, "Failed to create P2PKH script");

  char address[100];
  memset(address, 0xFF, sizeof(address));  // Fill with non-zero
  ew_error_t err =
    ew_script_to_address(script, written, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should succeed");
  // Verify null terminator is present
  size_t addr_len = strlen(address);
  TEST_ASSERT_EQ(address[addr_len], '\0', "Null terminator should be present");
  teardown();
  return 0;
}

// ============================================================================
// PSBT tests
// ============================================================================

static int test_psbt_from_base64_invalid_param(void) {
  setup();
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(NULL, &psbt);
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM,
                 "Should return EW_ERROR_INVALID_PARAM for NULL base64");
  teardown();
  return 0;
}

static int test_psbt_from_base64_invalid_psbt(void) {
  setup();
  ew_psbt_t* psbt = NULL;

  // Taken from BIP 174 test vectors
  ew_error_t err = ew_psbt_from_base64(
    "AgAAAAEmgXE3Ht/yhek3re6ks3t4AAwFZsuzrWRkFxPKQhcb9gAAAABqRzBEAiBwsiRRI+a/"
    "R01gxbUMBD1MaRpdJDXwmjSnZiqdwlF5CgIgATKcqdrPKAvfMHQOwDkEIkIsgctFg5RXrrdvwS7dlbMBIQJlfRGNM1e44P"
    "TCzUbbezn22cONmnCry5st5dyNv+TOMf7///8C09/"
    "1BQAAAAAZdqkU0MWZA8W6woaHYOkP1SGkZlqnZSCIrADh9QUAAAAAF6kUNUXm4zuDLEcFDyTT7rk8nAOUi8eHsy4TAA==",
    &psbt);
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PSBT,
                 "Should return EW_ERROR_INVALID_PSBT for invalid base64");
  teardown();
  return 0;
}

static int test_psbt_from_base64_success(void) {
  setup();

  // Base64 encoded PSBT from unsigned psbt
  const char* base64 =
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
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should successfully parse valid PSBT");
  TEST_ASSERT_NEQ(psbt, NULL, "PSBT should not be NULL");

  size_t num_inputs = 0;
  err = ew_psbt_get_num_inputs(psbt, &num_inputs);
  TEST_ASSERT_EQ(err, EW_OK, "Should get number of inputs");
  TEST_ASSERT_EQ(num_inputs, 1, "Should have 1 input");

  size_t num_outputs = ew_psbt_get_num_outputs(psbt);
  TEST_ASSERT_EQ(num_outputs, 1, "Should have 1 output");

  ew_psbt_free(psbt);
  teardown();
  return 0;
}

// PSBT with 1 Input (500000000 sats) and 1 Output (499990000 sats)
// from https://github.com/bitcoin/bips/blob/master/bip-0174.mediawiki#user-content-Test_Vectors
const char* p2wsh_2_of_2_psbt_v0_base64 =
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

static int test_psbt_get_version(void) {
  setup();
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(p2wsh_2_of_2_psbt_v0_base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should parse PSBT");

  uint32_t version = ew_psbt_get_version(psbt);
  TEST_ASSERT_EQ(version, WALLY_PSBT_VERSION_0, "Should have version 0");

  ew_psbt_free(psbt);
  psbt = NULL;

  // Make the v2 PSBT a static const at file scope to avoid repeated string literal in future and
  // improve test consistency, but for now proceed as a local variable for minimal change.
  const char* p2wsh_2_of_2_psbt_v2_base64 =
    "cHNidP8BAgQCAAAAAQQBAQEFAQIB+wQCAAAAAAEAUgIAAAABwaolbiFLlqGCL5PeQr/ztfP/"
    "jQUZMG41FddRWl6AWxIAAAAAAP////"
    "8BGMaaOwAAAAAWABSwo68UQghBJpPKfRZoUrUtsK7wbgAAAAABAR8Yxpo7AAAAABYAFLCjrxRCCEEmk8p9FmhStS2wrvBu"
    "AQ4gCwrZIUGcHIcZc11y3HOfnqngY40f5MHu8PmUQISBX8gBDwQAAAAAACICAtYB+EhGpnVfd2vgDj2d6PsQrMk1+"
    "4PEX7AWLUytWreSGPadhz5UAACAAQAAgAAAAIAAAAAAKgAAAAEDCAAIry8AAAAAAQQWABTEMPZMR1baMQ29GghVcu8pmSY"
    "nLAAiAgLjb7/1PdU0Bwz4/"
    "TlmFGgPNXqbhdtzQL8c+nRdKtezQBj2nYc+"
    "VAAAgAEAAIAAAACAAQAAAGQAAAABAwiLvesLAAAAAAEEFgAUTdGTrJZKVqwbnhzKhFT+L0dPhRMA";

  err = ew_psbt_from_base64(p2wsh_2_of_2_psbt_v2_base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should parse PSBT v2");
  version = ew_psbt_get_version(psbt);
  TEST_ASSERT_EQ(version, WALLY_PSBT_VERSION_2, "Should have version 2");

  ew_psbt_free(psbt);

  teardown();
  return 0;
}

static int test_psbt_input_get_amount(void) {
  setup();
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(p2wsh_2_of_2_psbt_v0_base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should parse PSBT");

  bool has_amount = false;
  uint64_t amount = 0;
  err = ew_psbt_input_get_amount(psbt, 0, &has_amount, &amount);
  TEST_ASSERT_EQ(err, EW_OK, "Should successfully get input amount");
  TEST_ASSERT_EQ(has_amount, true, "Should have amount");
  TEST_ASSERT_EQ(amount, 500000000, "Should have correct amount (500000000 sats)");

  ew_psbt_free(psbt);
  teardown();
  return 0;
}

static int test_psbt_input_get_amount_out_of_bounds(void) {
  setup();
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(p2wsh_2_of_2_psbt_v0_base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should parse PSBT");

  bool has_amount = false;
  uint64_t amount = 0;
  err = ew_psbt_input_get_amount(psbt, 1, &has_amount, &amount);
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM,
                 "Should return EW_ERROR_INVALID_PARAM for out-of-bounds input index");

  ew_psbt_free(psbt);
  teardown();
  return 0;
}

static int test_psbt_output_get_info(void) {
  setup();
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(p2wsh_2_of_2_psbt_v0_base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should parse PSBT");

  const uint8_t* script = NULL;
  size_t script_len = 0;
  bool has_amount = false;
  uint64_t amount = 0;
  err = ew_psbt_output_get_info(psbt, 0, &script, &script_len, &has_amount, &amount);
  TEST_ASSERT_EQ(err, EW_OK, "Should successfully get output info");
  TEST_ASSERT_NEQ(script, NULL, "Script should not be NULL");
  TEST_ASSERT_EQ(script_len, 22, "Script length should be 22 bytes");
  TEST_ASSERT_EQ(has_amount, true, "Should have amount");
  TEST_ASSERT_EQ(amount, 499990000, "Should have correct amount (499990000 sats)");

  // Output address is P2WPKH (see ew_script_to_address docs for more details)
  char address[90] = {0};
  err = ew_script_to_address(script, script_len, EW_NETWORK_MAINNET, address, sizeof(address));
  TEST_ASSERT_EQ(err, EW_OK, "Should successfully convert script to address");
  TEST_ASSERT_STR_EQ(address, "bc1q0vaqp07uznf809wzkaysr5ya5mh3xdter5ypge",
                     "Should have correct address");

  ew_psbt_free(psbt);
  teardown();
  return 0;
}

static int test_psbt_output_get_info_out_of_bounds(void) {
  setup();
  ew_psbt_t* psbt = NULL;
  ew_error_t err = ew_psbt_from_base64(p2wsh_2_of_2_psbt_v0_base64, &psbt);
  TEST_ASSERT_EQ(err, EW_OK, "Should parse PSBT");

  const uint8_t* script = NULL;
  size_t script_len = 0;
  bool has_amount = false;
  uint64_t amount = 0;
  err = ew_psbt_output_get_info(psbt, 1, &script, &script_len, &has_amount, &amount);
  TEST_ASSERT_EQ(err, EW_ERROR_INVALID_PARAM,
                 "Should return EW_ERROR_INVALID_PARAM for out-of-bounds output index");

  ew_psbt_free(psbt);
  teardown();
  return 0;
}

static int test_psbt_free_null(void) {
  setup();
  ew_psbt_free(NULL);
  teardown();
  return 0;
}

// ============================================================================
// Test runner
// ============================================================================

int main(void) {
  printf("=== LibEW ew_script_to_address Tests ===\n\n");

  // Reset counters
  tests_run = 0;
  tests_passed = 0;
  tests_failed = 0;

  // Initialization and parameter validation tests
  printf("--- Initialization and Parameter Validation Tests ---\n");
  RUN_TEST(test_not_initialized_returns_error);
  RUN_TEST(test_null_script_returns_error);
  RUN_TEST(test_zero_script_len_returns_error);
  RUN_TEST(test_null_address_out_returns_error);
  RUN_TEST(test_zero_address_len_returns_error);
  RUN_TEST(test_invalid_script_returns_error);

  // Segwit address tests
  printf("\n--- Segwit Address Tests ---\n");
  RUN_TEST(test_p2wpkh_mainnet);
  RUN_TEST(test_p2wpkh_testnet);
  RUN_TEST(test_p2wsh_mainnet);
  RUN_TEST(test_p2wsh_testnet);
  RUN_TEST(test_p2tr_mainnet);
  RUN_TEST(test_p2tr_testnet);
  RUN_TEST(test_malformed_segwit_script_returns_error);
  RUN_TEST(test_segwit_buffer_too_small);

  // Legacy address tests
  printf("\n--- Legacy Address Tests ---\n");
  RUN_TEST(test_p2pkh_mainnet);
  RUN_TEST(test_p2pkh_testnet);
  RUN_TEST(test_p2sh_mainnet);
  RUN_TEST(test_p2sh_testnet);
  RUN_TEST(test_legacy_buffer_too_small);

  // Boundary and regression tests
  printf("\n--- Boundary and Regression Tests ---\n");
  RUN_TEST(test_exact_buffer_size_segwit);
  RUN_TEST(test_exact_buffer_size_legacy);
  RUN_TEST(test_null_terminator_preserved_segwit);
  RUN_TEST(test_null_terminator_preserved_legacy);

  // PSBT tests
  printf("\n--- PSBT Tests ---\n");
  RUN_TEST(test_psbt_from_base64_invalid_param);
  RUN_TEST(test_psbt_from_base64_invalid_psbt);
  RUN_TEST(test_psbt_from_base64_success);
  RUN_TEST(test_psbt_get_version);
  RUN_TEST(test_psbt_input_get_amount);
  RUN_TEST(test_psbt_input_get_amount_out_of_bounds);
  RUN_TEST(test_psbt_output_get_info);
  RUN_TEST(test_psbt_output_get_info_out_of_bounds);
  RUN_TEST(test_psbt_free_null);

  // Print summary
  printf("\n=== Test Summary ===\n");
  printf("Total tests run: %d\n", tests_run);
  printf("Passed: %d\n", tests_passed);
  printf("Failed: %d\n", tests_failed);

  if (tests_failed == 0) {
    printf("\nAll tests passed!\n");
    return 0;
  } else {
    printf("\nSome tests failed!\n");
    return 1;
  }
}
