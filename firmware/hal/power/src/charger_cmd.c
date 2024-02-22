#include "battery.h"
#include "log.h"
#include "max77734.h"
#include "printf.h"
#include "shell_cmd.h"

static struct {
  arg_lit_t* dump;
  arg_lit_t* status;
  arg_lit_t* mode;
  arg_lit_t* battery;
  arg_end_t* end;
} chg_cmd_args;

static void chg_cmd_register(void);
static void chg_cmd_handler(int argc, char** argv);

static void chg_cmd_register(void) {
  chg_cmd_args.dump = ARG_LIT_OPT('d', "dump", "print out all settings");
  chg_cmd_args.status = ARG_LIT_OPT('s', "status", "print status");
  chg_cmd_args.mode = ARG_LIT_OPT('m', "mode", "print mode");
  chg_cmd_args.battery = ARG_LIT_OPT('b', "battery", "print battery variant");
  chg_cmd_args.end = ARG_END();

  static shell_command_t SHARED_TASK_DATA chg_cmd = {
    .command = "charger",
    .help = "battery charger settings",
    .handler = chg_cmd_handler,
    .argtable = &chg_cmd_args,
  };

  shell_command_register(&chg_cmd);
}
SHELL_CMD_REGISTER("charger", chg_cmd_register);

static void chg_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&chg_cmd_args);

  if (nerrors) {
    return;
  }

  if (chg_cmd_args.dump->header.found) {
    max77734_print_registers();
  }

  if (chg_cmd_args.status->header.found) {
    max77734_print_status();
  }

  if (chg_cmd_args.mode->header.found) {
    max77734_print_mode();
  }

  if (chg_cmd_args.battery->header.found) {
    battery_print_variant();
  }
}
