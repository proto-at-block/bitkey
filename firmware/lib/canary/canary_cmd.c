#include "shell_cmd.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

#define BUFFER_SIZE   (32)
#define OVERFLOW_SIZE (BUFFER_SIZE + 4)

void __attribute__((noinline)) trigger_memset_stack_overflow(void) {
  uint8_t buf[BUFFER_SIZE];
  memset(&buf[0], 0x0, OVERFLOW_SIZE);
}

static struct {
  arg_lit_t* stack_overflow;
  arg_end_t* end;
} canary_args;

static void cmd_canary_register(void);
static void cmd_canary_run(int argc, char** argv);

static void cmd_canary_register(void) {
  canary_args.stack_overflow =
    ARG_LIT_OPT('s', "stack_overflow", "trigger a stack overflow to overwrite canary");
  canary_args.end = ARG_END();

  static shell_command_t canary_cmd = {
    .command = "canary",
    .help = "stack canary testing",
    .handler = cmd_canary_run,
    .argtable = &canary_args,
  };
  shell_command_register(&canary_cmd);
}
SHELL_CMD_REGISTER("canary", cmd_canary_register);

static void cmd_canary_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&canary_args);
  if (nerrors) {
    return;
  }

  if (canary_args.stack_overflow->header.found) {
    trigger_memset_stack_overflow();
  }
}
