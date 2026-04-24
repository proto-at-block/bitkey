#include "mcu_reset.h"
#include "printf.h"
#include "shell_cmd.h"

static void cmd_reset_run(int argc, char** argv) {
  (void)argc;
  (void)argv;
  printf("resetting uxc\n");
  mcu_reset_with_reason(MCU_RESET_FROM_PROTO);
}

static void cmd_reset_register(void) {
  static shell_command_t cmd = {
    .command = "reset",
    .help = "reset this MCU",
    .handler = cmd_reset_run,
    .argtable = NULL,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("reset", cmd_reset_register);
