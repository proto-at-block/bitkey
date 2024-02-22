#include "log.h"
#include "printf.h"
#include "shell_cmd.h"
#include "unlock.h"
#include "unlock_impl.h"

static struct {
  arg_lit_t* read;
  arg_int_t* write;
  arg_lit_t* clear;
  arg_end_t* end;
} unlock_cmd_args;

static void unlock_cmd_register(void);
static void unlock_cmd_handler(int argc, char** argv);

static void unlock_cmd_register(void) {
  unlock_cmd_args.read = ARG_LIT_OPT('r', "read", "get retry counter");
  unlock_cmd_args.write = ARG_INT_OPT('w', "write", "write retry counter");
  unlock_cmd_args.clear = ARG_LIT_OPT('c', "clear", "clear retry counter");
  unlock_cmd_args.end = ARG_END();

  static shell_command_t SHARED_TASK_DATA chg_cmd = {
    .command = "unlock",
    .help = "unlock cmds",
    .handler = unlock_cmd_handler,
    .argtable = &unlock_cmd_args,
  };

  shell_command_register(&chg_cmd);
}
SHELL_CMD_REGISTER("unlock", unlock_cmd_register);

static void unlock_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&unlock_cmd_args);

  if (nerrors) {
    return;
  }

  if (unlock_cmd_args.read->header.found) {
    uint32_t retry_counter = 0;
    if (retry_counter_read(&retry_counter) != UNLOCK_OK) {
      LOGE("Failed to get retry counter");
      return;
    }
    printf("Retry counter: %u\n", retry_counter);
  } else if (unlock_cmd_args.write->header.found) {
    if (retry_counter_write(unlock_cmd_args.write->value) != UNLOCK_OK) {
      LOGE("Failed to set retry counter");
      return;
    }
    printf("Retry counter set to %d\n", unlock_cmd_args.write->value);
  } else if (unlock_cmd_args.clear->header.found) {
    if (unlock_reset_retry_counter() != UNLOCK_OK) {
      LOGE("Failed to clear retry counter");
      return;
    }
    printf("Retry counter cleared\n");
  } else {
    printf("No command specified\n");
  }
}
