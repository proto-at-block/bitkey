#include "criterion_test_utils.h"
#include "ecc.h"
#include "fff.h"
#include "hash.h"
#include "hex.h"
#include "ripemd160_impl.h"
#include "secp256k1.h"
#include "secp256k1_extrakeys.h"
#include "secp256k1_preallocated.h"
#include "secp256k1_schnorrsig.h"
#include "secure_rng.h"

#include <criterion/criterion.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, crypto_hkdf, key_handle_t*, hash_alg_t, uint8_t*, size_t, uint8_t*, size_t,
                key_handle_t*);
FAKE_VALUE_FUNC(bool, export_pubkey, key_handle_t*, key_handle_t*);

static int fill_random(unsigned char* data, size_t size) {
  memset(data, 'a', size);
  return 1;
}

// NOTE: This test is almost entirely copied from the schnorr example in libsecp256k1.
Test(crypto_test, schnorr_roundtrip) {
  unsigned char msg_hash[32] = {
    0x31, 0x5F, 0x5B, 0xDB, 0x76, 0xD0, 0x78, 0xC4, 0x3B, 0x8A, 0xC0, 0x06, 0x4E, 0x4A, 0x01, 0x64,
    0x61, 0x2B, 0x1F, 0xCE, 0x77, 0xC8, 0x69, 0x34, 0x5B, 0xFC, 0x94, 0xC7, 0x58, 0x94, 0xED, 0xD3,
  };
  unsigned char seckey[32];
  unsigned char randomize[32];
  unsigned char auxiliary_rand[32];
  unsigned char serialized_pubkey[32];
  unsigned char signature[64];
  int is_signature_valid;
  int return_val;
  secp256k1_xonly_pubkey pubkey;
  secp256k1_keypair keypair;
  /* The specification in secp256k1_extrakeys.h states that `secp256k1_keypair_create`
   * needs a context object initialized for signing. And in secp256k1_schnorrsig.h
   * they state that `secp256k1_schnorrsig_verify` needs a context initialized for
   * verification, which is why we create a context for both signing and verification
   * with the SECP256K1_CONTEXT_SIGN and SECP256K1_CONTEXT_VERIFY flags. */
  secp256k1_context* ctx =
    secp256k1_context_create(SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);
  if (!fill_random(randomize, sizeof(randomize))) {
    printf("Failed to generate randomness\n");
    return;
  }
  /* Randomizing the context is recommended to protect against side-channel
   * leakage See `secp256k1_context_randomize` in secp256k1.h for more
   * information about it. This should never fail. */
  return_val = secp256k1_context_randomize(ctx, randomize);
  cr_assert(return_val);

  /*** Key Generation ***/

  /* If the secret key is zero or out of range (bigger than secp256k1's
   * order), we try to sample a new key. Note that the probability of this
   * happening is negligible. */
  while (1) {
    if (!fill_random(seckey, sizeof(seckey))) {
      printf("Failed to generate randomness\n");
      return;
    }
    /* Try to create a keypair with a valid context, it should only fail if
     * the secret key is zero or out of range. */
    if (secp256k1_keypair_create(ctx, &keypair, seckey)) {
      break;
    }
  }

  /* Extract the X-only public key from the keypair. We pass NULL for
   * `pk_parity` as the parity isn't needed for signing or verification.
   * `secp256k1_keypair_xonly_pub` supports returning the parity for
   * other use cases such as tests or verifying Taproot tweaks.
   * This should never fail with a valid context and public key. */
  return_val = secp256k1_keypair_xonly_pub(ctx, &pubkey, NULL, &keypair);
  cr_assert(return_val);

  /* Serialize the public key. Should always return 1 for a valid public key. */
  return_val = secp256k1_xonly_pubkey_serialize(ctx, serialized_pubkey, &pubkey);
  cr_assert(return_val);

  /*** Signing ***/

  /* Generate 32 bytes of randomness to use with BIP-340 schnorr signing. */
  if (!fill_random(auxiliary_rand, sizeof(auxiliary_rand))) {
    printf("Failed to generate randomness\n");
    return;
  }

  /* Generate a Schnorr signature.
   *
   * We use the secp256k1_schnorrsig_sign32 function that provides a simple
   * interface for signing 32-byte messages (which in our case is a hash of
   * the actual message). BIP-340 recommends passing 32 bytes of randomness
   * to the signing function to improve security against side-channel attacks.
   * Signing with a valid context, a 32-byte message, a verified keypair, and
   * any 32 bytes of auxiliary random data should never fail. */
  return_val = secp256k1_schnorrsig_sign32(ctx, signature, msg_hash, &keypair, auxiliary_rand);
  cr_assert(return_val);

  /*** Verification ***/

  /* Deserialize the public key. This will return 0 if the public key can't
   * be parsed correctly */
  if (!secp256k1_xonly_pubkey_parse(ctx, &pubkey, serialized_pubkey)) {
    printf("Failed parsing the public key\n");
    return;
  }

  /* Verify a signature. This will return 1 if it's valid and 0 if it's not. */
  is_signature_valid = secp256k1_schnorrsig_verify(ctx, signature, msg_hash, 32, &pubkey);

  printf("Is the signature valid? %s\n", is_signature_valid ? "true" : "false");
  printf("Secret Key: ");
  dumphex(seckey, sizeof(seckey));
  printf("Public Key: ");
  dumphex(serialized_pubkey, sizeof(serialized_pubkey));
  printf("Signature: ");
  dumphex(signature, sizeof(signature));

  /* This will clear everything from the context and free the memory */
  secp256k1_context_destroy(ctx);

  /* It's best practice to try to clear secrets from memory after using them.
   * This is done because some bugs can allow an attacker to leak memory, for
   * example through "out of bounds" array access (see Heartbleed), Or the OS
   * swapping them to disk. Hence, we overwrite the secret key buffer with zeros.
   *
   * TODO: Prevent these writes from being optimized out, as any good compiler
   * will remove any writes that aren't used. */
  memset(seckey, 0, sizeof(seckey));
}

Test(crypto_test, ripemd160) {
  unsigned char data[] = "abc";
  uint8_t digest[20] = {0};
  cr_assert(mbedtls_ripemd160(data, strlen((char*)data), digest));

  uint8_t expected[] = {
    0x8e, 0xb2, 0x08, 0xf7, 0xe0, 0x5d, 0x98, 0x7a, 0x9b, 0x04,
    0x4a, 0x8e, 0x98, 0xc6, 0xb0, 0x87, 0xf1, 0x5a, 0x0b, 0xfc,
  };

  cr_util_cmp_buffers(digest, expected, sizeof(expected));
}

Test(crypto_test, crypto_ecc_validate_private_key_p256) {
  uint8_t zeros[ECC_PRIVKEY_SIZE] = {0};

  key_handle_t key = {
    .alg = ALG_ECC_P256,
    .key.bytes = zeros,
    .key.size = ECC_PRIVKEY_SIZE,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  // All-zero is invalid
  cr_assert(crypto_ecc_validate_private_key(&key) == false);

  // Greater than curve order is invalid
  uint8_t p256_n[] = {
    0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
    0xbc, 0xe6, 0xfa, 0xad, 0xa7, 0x17, 0x9e, 0x84, 0xf3, 0xb9, 0xca, 0xc2, 0xfc, 0x63, 0x25, 0x51,
  };
  key.key.bytes = p256_n;

  // Pick a random byte, add a random value to it. All of these should be invalid.
  for (int i = 0; i < 100000; i++) {
    uint8_t random_byte = rand() % ECC_PRIVKEY_SIZE;

    // Must ensure no overflow.
    uint8_t random_value = rand() % (256 - p256_n[random_byte]);
    key.key.bytes[random_byte] += random_value;

    cr_assert(crypto_ecc_validate_private_key(&key) == false);

    // Reset
    key.key.bytes[random_byte] -= random_value;
  }

  // Within 0 to curve order is valid

  // Pick a random byte, subtract a random value from it. All of these should be valid.
  for (int i = 0; i < 100000; i++) {
    uint8_t random_byte = rand() % ECC_PRIVKEY_SIZE;
    uint8_t random_value = (rand() % (p256_n[random_byte] + 1));

    if (p256_n[random_byte] == 0 || random_value == 0) {
      continue;
    }

    key.key.bytes[random_byte] -= random_value;
    cr_assert(crypto_ecc_validate_private_key(&key) == true);
    key.key.bytes[random_byte] += random_value;
  }

  // Generate a bunch of private keys. Probabilistically, they should all be valid.

#if 0
  const uint32_t loops = 10000000;  // 10 million
#endif
  // Don't try so many loops in CI
  const uint32_t loops = 10000;

  for (int i = 0; i < loops; i++) {
    uint8_t random_bytes[ECC_PRIVKEY_SIZE] = {0};
    cr_assert(crypto_random(random_bytes, ECC_PRIVKEY_SIZE));

    key.key.bytes = random_bytes;

    cr_assert(crypto_ecc_validate_private_key(&key));
  }
}

Test(crypto_test, crypto_ecc_validate_private_key_ed25519) {
  key_handle_t key = {
    .alg = ALG_ECC_ED25519,
    .key.size = ECC_PRIVKEY_SIZE,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  // Generate a bunch of private keys. Probabilistically, they should all be valid.
#if 0
  const uint32_t loops = 10000000;  // 10 million
#endif
  // Don't try so many loops in CI
  const uint32_t loops = 10000;

  for (int i = 0; i < loops; i++) {
    uint8_t random_bytes[ECC_PRIVKEY_SIZE] = {0};
    cr_assert(crypto_random(random_bytes, ECC_PRIVKEY_SIZE));

    key.key.bytes = random_bytes;

    cr_assert(crypto_ecc_validate_private_key(&key));
  }
}
