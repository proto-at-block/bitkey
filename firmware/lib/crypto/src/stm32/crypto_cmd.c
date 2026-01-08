#include "crypto_test.h"
#include "shell_argparse.h"
#include "shell_cmd.h"

#include <stdint.h>

static struct {
  arg_lit_t* ecdsa;
  arg_lit_t* rng;
  arg_lit_t* drbg;
  arg_lit_t* curve25519;
  arg_lit_t* hkdf;
  arg_lit_t* gcm;
  arg_end_t* end;
} crypto_cmd_args;

static void cmd_crypto_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&crypto_cmd_args);

  if (nerrors) {
    return;
  }

  if (crypto_cmd_args.ecdsa->header.found) {
    crypto_test_ecdsa();
  }
  if (crypto_cmd_args.rng->header.found) {
    crypto_test_random();
  }
  if (crypto_cmd_args.drbg->header.found) {
    crypto_test_drbg();
  }
  if (crypto_cmd_args.curve25519->header.found) {
    crypto_test_curve25519();
  }
  if (crypto_cmd_args.hkdf->header.found) {
    crypto_test_hkdf();
  }
  if (crypto_cmd_args.gcm->header.found) {
    crypto_test_gcm();
  }
}

static void cmd_crypto_register(void) {
  crypto_cmd_args.ecdsa = ARG_LIT_OPT('e', "ecdsa", "run ECDSA signing and verification tests");
  crypto_cmd_args.rng = ARG_LIT_OPT('r', "rng", "run rng randomness test");
  crypto_cmd_args.drbg = ARG_LIT_OPT('d', "drbg", "run drbg test");
  crypto_cmd_args.curve25519 = ARG_LIT_OPT('c', "curve25519", "run curve 25519 test");
  crypto_cmd_args.hkdf = ARG_LIT_OPT('k', "hkdf", "run HKDF test");
  crypto_cmd_args.gcm = ARG_LIT_OPT('g', "gcm", "run AES-GCM test");
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
