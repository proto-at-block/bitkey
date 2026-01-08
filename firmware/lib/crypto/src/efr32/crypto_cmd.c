#include "aes.h"
#include "attestation.h"
#include "crypto_test.h"
#include "dudero.h"
#include "ecc.h"
#include "hex.h"
#include "key_management.h"
#include "log.h"
#include "printf.h"
#include "secure_rng.h"
#include "shell_argparse.h"
#include "shell_cmd.h"

#include <stdlib.h>
#include <string.h>

static struct {
  arg_lit_t* schnorr;
  arg_int_t* ecc;
  arg_lit_t* attestation;
  arg_lit_t* gcm;
  arg_lit_t* rng;
  arg_lit_t* curve25519;
  arg_end_t* end;
} crypto_cmd_args;

void schnorr_test(void) {
  crypto_ecc_secp256k1_init();

  key_handle_t key = {0};
  uint8_t buf[96] = {0};
  key.key.bytes = &buf[0];
  key.key.size = sizeof(buf);
  if (!crypto_ecc_secp256k1_generate_keypair(&key)) {
    printf("Failed to generate keypair\n");
    return;
  }

  uint8_t sig[64] = {0};
  unsigned char msg[13] = "Hello, world!";
  if (!crypto_ecc_secp256k1_schnorr_sign(&key, msg, sizeof(msg), sig, sizeof(sig))) {
    printf("Failed to sign\n");
    goto out;
  }

  bool verify_result = false;
  if (!crypto_ecc_secp256k1_schnorr_verify(&key, msg, sizeof(msg), sig, sizeof(sig),
                                           &verify_result)) {
    printf("Failed to verify\n");
    goto out;
  }

  sig[0] ^= 1;  // Corrupt signature
  bool verify_result2 = false;
  bool verify_should_be_false = verify_result;
  if (!crypto_ecc_secp256k1_schnorr_verify(&key, msg, sizeof(msg), sig, sizeof(sig),
                                           &verify_result2)) {
    printf("Failed to verify\n");
    goto out;
  }
  if (verify_should_be_false && (verify_result2)) {
    printf("Verification on corrupted sig passed, but it shouldn't have\n");
    goto out;
  }

out:
  printf("Signature valid? %s\n", verify_result ? "true" : "false");
  printf("Keypair: ");
  dumphex(key.key.bytes, key.key.size);
  printf("Signature: ");
  dumphex(sig, sizeof(sig));
}

void attestation_test(void) {
  uint8_t challenge[16] = {0};
  memset(challenge, 'a', sizeof(challenge));
  uint8_t signature[64] = {0};
  if (!crypto_sign_challenge(challenge, sizeof(challenge), signature, sizeof(signature))) {
    printf("Failed to sign challenge\n");
    return;
  }

  printf("Challenge: ");
  dumphex(challenge, sizeof(challenge));
  printf("Signature: ");
  dumphex(signature, sizeof(signature));
}

void crypto_test_curve25519(void) {
  uint8_t our_privkey_buf[EC_PRIVKEY_SIZE_X25519] = {
    0x05, 0x09, 0xa7, 0x09, 0x3b, 0x48, 0x38, 0xc8, 0xf7, 0x84, 0x97, 0x7b, 0x95, 0x18, 0x21, 0xe8,
    0x2f, 0x22, 0x55, 0xd3, 0xc0, 0x13, 0x93, 0xe0, 0xa2, 0xe6, 0x69, 0x1d, 0x0d, 0x3e, 0xa3, 0xcb,
  };

  uint8_t their_pubkey_buf[EC_PUBKEY_SIZE_X25519] = {
    0x61, 0x41, 0xcd, 0xe6, 0x3b, 0x6f, 0xf5, 0x2e, 0xe6, 0xfc, 0xed, 0xd3, 0x59, 0x52, 0xac, 0x85,
    0x57, 0xf0, 0x11, 0x39, 0xb0, 0x6e, 0x98, 0xa2, 0x2b, 0x66, 0x91, 0x60, 0x26, 0x8a, 0xf1, 0x7d,
  };

  uint8_t expected_secret[EC_SECRET_SIZE_X25519] = {
    0x4e, 0xce, 0xdb, 0x30, 0x17, 0x5b, 0xb7, 0xbc, 0xed, 0xb8, 0x46, 0x41, 0x1c, 0xe8, 0xc3, 0xf2,
    0xff, 0xcb, 0xcd, 0x72, 0xac, 0xb4, 0x08, 0x71, 0xc4, 0x91, 0xec, 0x87, 0x63, 0xc0, 0x7e, 0x6f,
  };

  key_handle_t our_privkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = our_privkey_buf,
    .key.size = sizeof(our_privkey_buf),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY,
  };

  key_handle_t their_pubkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = their_pubkey_buf,
    .key.size = sizeof(their_pubkey_buf),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
  };

  uint8_t shared_secret_buf[EC_SECRET_SIZE_X25519] = {0};
  key_handle_t shared_secret = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = shared_secret_buf,
    .key.size = sizeof(shared_secret_buf),
  };

  if (!crypto_ecc_compute_shared_secret(&our_privkey, &their_pubkey, &shared_secret)) {
    LOGE("Failed to compute shared secret");
    return;
  }

  if (memcmp(shared_secret_buf, expected_secret, sizeof(expected_secret)) != 0) {
    LOGE("Secret does not match expected.");
    return;
  }

  LOGI("25519_ecdh_test PASS");
}

void ecc_test(key_algorithm_t alg) {
  uint8_t message[32] = {0};
  memset(message, 'a', sizeof(message));

  hash_alg_t hash_alg = (alg == ALG_ECC_ED25519) ? ALG_SHA512 : ALG_SHA256;
  uint32_t hash_size = (alg == ALG_ECC_ED25519) ? SHA512_DIGEST_SIZE : SHA256_DIGEST_SIZE;

  uint8_t hash[SHA512_DIGEST_SIZE] = {0};
  if (!crypto_hash(message, sizeof(message), hash, hash_size, hash_alg)) {
    return;
  }

  uint8_t signature[ECC_SIG_SIZE] = {0};

  uint8_t key_buf[32 * SECP256K1_CUSTOM_DOMAIN_OVERHEAD] = {0};
  // Note: you can set both SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY and
  // SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY to get the keypair when generating a key.
  key_handle_t key = {
    .alg = alg,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = key_buf,
    .key.size = sizeof(key_buf),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  if (!generate_key(&key)) {
    printf("Couldn't generate key\n");
    return;
  }

  if (!crypto_ecc_validate_private_key(&key)) {
    printf("Invalid private key\n");
    return;
  }

  if (crypto_ecc_sign_hash(&key, hash, hash_size, signature) != SECURE_TRUE) {
    printf("Failed to sign\n");
    return;
  }

  printf("Signature: ");
  dumphex(signature, sizeof(signature));

  if (crypto_ecc_sign_hash(&key, hash, hash_size, signature) != SECURE_TRUE) {
    printf("Failed to sign\n");
    return;
  }
  if (crypto_ecc_verify_hash(&key, hash, hash_size, signature) != SECURE_TRUE) {
    printf("Failed to verify\n");
    return;
  }

  printf("Signature 2: ");
  dumphex(signature, sizeof(signature));
}

void randomness_test(void) {
  dudero_ctx_t ctx;
  dudero_stream_init(&ctx);
  for (size_t i = 0; i < 256; i++) {
    uint8_t r = 0;
    if (!crypto_random(&r, 1)) {
      printf("Failed crypto_random\n");
      return;
    }
    dudero_stream_add(&ctx, r);
  }
  if (dudero_stream_finish(&ctx) != DUDERO_RET_OK) {
    printf("randomness_test FAIL\n");
  } else {
    printf("randomness_test PASS\n");
  }
  return;
}

static void cmd_crypto_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&crypto_cmd_args);

  if (nerrors)
    return;

  if (crypto_cmd_args.schnorr->header.found) {
    schnorr_test();
  } else if (crypto_cmd_args.attestation->header.found) {
    attestation_test();
  } else if (crypto_cmd_args.ecc->header.found) {
    ecc_test(crypto_cmd_args.ecc->value);
  } else if (crypto_cmd_args.gcm->header.found) {
    crypto_test_gcm();
  } else if (crypto_cmd_args.rng->header.found) {
    randomness_test();
  } else if (crypto_cmd_args.curve25519->header.found) {
    crypto_test_curve25519();
  }
}

static void cmd_crypto_register(void) {
  crypto_cmd_args.schnorr = ARG_LIT_OPT('s', "schnorr", "schnorr signature test");
  crypto_cmd_args.attestation = ARG_LIT_OPT('a', "attestation", "attestation test");
  crypto_cmd_args.ecc = ARG_INT_OPT('e', "ecc", "ecc signature test");
  crypto_cmd_args.gcm = ARG_LIT_OPT('g', "gcm", "gcm test");
  crypto_cmd_args.rng = ARG_LIT_OPT('r', "rng", "rng randomness test");
  crypto_cmd_args.curve25519 = ARG_LIT_OPT('c', "curve25519", "run curve 25519 test");
  crypto_cmd_args.end = ARG_END();

  static shell_command_t crypto_cmd = {
    .command = "crypto",
    .help = "cryptography commands",
    .handler = cmd_crypto_run,
    .argtable = &crypto_cmd_args,
  };
  shell_command_register(&crypto_cmd);
}
SHELL_CMD_REGISTER("crypto", cmd_crypto_register);
