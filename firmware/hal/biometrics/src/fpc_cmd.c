#include "aes.h"
#include "bio.h"
#include "bio_impl.h"
#include "log.h"
#include "printf.h"
#include "secure_rng.h"
#include "shell_cmd.h"

static struct {
  arg_lit_t* test;
  arg_int_t* enroll;
  arg_lit_t* match;
  arg_bool_t* provision;
  arg_str_t* write_key;
  arg_lit_t* security_test;
  arg_int_t* failure_analysis;
  arg_end_t* end;
} fpc_cmd_args;

static void fpc_cmd_register(void);
static void fpc_cmd_handler(int argc, char** argv);

static void fpc_cmd_register(void) {
  fpc_cmd_args.test = ARG_LIT_OPT('t', "test", "runs the fpc sensor test suite");
  fpc_cmd_args.enroll = ARG_INT_OPT('e', "enroll", "enroll a fingerprint");
  fpc_cmd_args.match = ARG_LIT_OPT('m', "match", "attempt to match a fingerprint");
  fpc_cmd_args.provision =
    ARG_BOOL_OPT('p', "provision", "provision cryptographic keys. true = dry run, false = real");
  fpc_cmd_args.write_key = ARG_STR_OPT('w', "write-key", "provision a specific hex-encoded key");
  fpc_cmd_args.security_test = ARG_LIT_OPT('s', "security-test", "run security test");
  fpc_cmd_args.failure_analysis = ARG_INT_OPT('f', "failure-analysis", "run FA capture");
  fpc_cmd_args.end = ARG_END();

  static shell_command_t fpc_cmd = {
    .command = "fpc",
    .help = "fingerprint sensor driver functions",
    .handler = fpc_cmd_handler,
    .argtable = &fpc_cmd_args,
  };

  shell_command_register(&fpc_cmd);
}
SHELL_CMD_REGISTER("fpc", fpc_cmd_register);

NO_OPTIMIZE static void fpc_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&fpc_cmd_args);

  if (nerrors) {
    return;
  }

  if (fpc_cmd_args.test->header.found) {
    bio_selftest_result_t result;
    bio_selftest(&result);
  } else if (fpc_cmd_args.enroll->header.found) {
    bio_wait_for_finger_blocking();
    bio_enroll_stats_t stats;
    bio_enroll_finger(fpc_cmd_args.enroll->value, &stats);
  } else if (fpc_cmd_args.match->header.found) {
    secure_bool_t match = SECURE_FALSE;
    bio_template_id_t id = 0;
    bio_wait_for_finger_blocking();
    bio_authenticate_finger(&match, &id, 0);
    SECURE_IF_FAILOUT(match == SECURE_TRUE) { LOGI("Matched to template %d", id); }
    else {
      LOGI("Authentication failed");
    }
  } else if (fpc_cmd_args.provision->header.found) {
    if (bio_provision_cryptographic_keys(fpc_cmd_args.provision->value, true)) {
      LOGI("Provisioned FPC sensor cryptographic keys");
    } else {
      LOGE("Failed to provision FPC sensor cryptographic keys");
    }
  } else if (fpc_cmd_args.write_key->header.found) {
    const char* hex_encoded_key = fpc_cmd_args.write_key->value;
    bio_write_plaintext_key(hex_encoded_key);
  } else if (fpc_cmd_args.security_test->header.found) {
    fpc_bep_security_test_result_t res;
    if (bio_security_test(&res)) {
      LOGI("FPC security test passed");
    } else {
      LOGE("FPC security test failed");
    }
  } else if (fpc_cmd_args.failure_analysis->header.found) {
    fpc_bep_analyze_result_t test_result;
    if (bio_image_analysis_test(fpc_cmd_args.failure_analysis->value, &test_result)) {
      LOGI("Image analysis test passed");
    } else {
      LOGE("Image analysis test failed");
    }
  }
}
