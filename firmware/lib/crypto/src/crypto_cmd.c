#include "aes.h"
#include "attestation.h"
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

static void gcm_test(void) {
  uint8_t key_buf[32] = {0};
  uint8_t iv[12] = {0};
  uint8_t aad[16] = {0};
  uint8_t plaintext[16] = {0};
  uint8_t ciphertext[16] = {0};
  uint8_t tag[16] = {0};

  if (!crypto_random(key_buf, sizeof(key_buf))) {
    printf("Failed to generate key\n");
    return;
  }
  if (!crypto_random(iv, sizeof(iv))) {
    printf("Failed to generate iv\n");
    return;
  }

  key_handle_t key = {
    .alg = ALG_AES_256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = key_buf,
    .key.size = sizeof(key_buf),
  };

  if (!aes_gcm_encrypt(plaintext, ciphertext, sizeof(plaintext), iv, tag, aad, sizeof(aad), &key)) {
    printf("Failed to encrypt\n");
    return;
  }

  printf("Ciphertext: ");
  dumphex(ciphertext, sizeof(ciphertext));
  printf("Tag: ");
  dumphex(tag, sizeof(tag));

#if 0
  tag[0] ^= 1;
#endif
#if 0
  ciphertext[0] ^= 1;
#endif

  if (!aes_gcm_decrypt(ciphertext, plaintext, sizeof(plaintext), iv, tag, aad, sizeof(aad), &key)) {
    printf("Failed to decrypt\n");
    return;
  }

  printf("Plaintext: ");
  dumphex(plaintext, sizeof(plaintext));
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
    gcm_test();
  } else if (crypto_cmd_args.rng->header.found) {
    randomness_test();
  }
}

static void cmd_crypto_register(void) {
  crypto_cmd_args.schnorr = ARG_LIT_OPT('s', "schnorr", "schnorr signature test");
  crypto_cmd_args.attestation = ARG_LIT_OPT('a', "attestation", "attestation test");
  crypto_cmd_args.ecc = ARG_INT_OPT('e', "ecc", "ecc signature test");
  crypto_cmd_args.gcm = ARG_LIT_OPT('g', "gcm", "gcm test");
  crypto_cmd_args.rng = ARG_LIT_OPT('r', "rng", "rng randomness test");
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
