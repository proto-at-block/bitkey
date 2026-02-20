#include "secure_channel_cert.h"
#include "secure_channel_test.h"
#include "shell_argparse.h"
#include "shell_cmd.h"

static struct {
  arg_lit_t* cert;
  arg_end_t* end;
} secure_channel_cmd_args;

static void cmd_secure_channel_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&secure_channel_cmd_args);

  if (nerrors) {
    return;
  }

  if (secure_channel_cmd_args.cert->header.found) {
    secure_channel_cert_test();
    return;
  }
}

static void cmd_secure_channel_register(void) {
  secure_channel_cmd_args.cert = ARG_LIT_OPT('c', "cert", "run secure channel certificate tests");
  secure_channel_cmd_args.end = ARG_END();

  static shell_command_t secure_channel_cmd = {
    .command = "sccert",
    .help = "secure channel commands",
    .handler = cmd_secure_channel_run,
    .argtable = &secure_channel_cmd_args,
  };
  shell_command_register(&secure_channel_cmd);
}
SHELL_CMD_REGISTER("sccert", cmd_secure_channel_register);
