#include "log.h"
#include "max17262.h"
#include "printf.h"
#include "shell_cmd.h"

static struct {
  arg_lit_t* validate;
  arg_lit_t* status;
  arg_lit_t* reset;
  arg_lit_t* clear;
  arg_end_t* end;
} fuel_cmd_args;

static void fuel_cmd_register(void);
static void fuel_cmd_handler(int argc, char** argv);

static void fuel_cmd_register(void) {
  fuel_cmd_args.validate = ARG_LIT_OPT('v', "validate", "validate communications");
  fuel_cmd_args.status = ARG_LIT_OPT('s', "status", "prints status");
  fuel_cmd_args.reset = ARG_LIT_OPT('r', "reset", "performs POR initialisation");
  fuel_cmd_args.clear = ARG_LIT_OPT('c', "clear", "clears modelgauge config");
  fuel_cmd_args.end = ARG_END();

  static shell_command_t SHARED_TASK_DATA chg_cmd = {
    .command = "fuel",
    .help = "battery fuel gauge",
    .handler = fuel_cmd_handler,
    .argtable = &fuel_cmd_args,
  };

  shell_command_register(&chg_cmd);
}
SHELL_CMD_REGISTER("fuel", fuel_cmd_register);

static void fuel_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&fuel_cmd_args);

  if (nerrors) {
    return;
  }

  if (fuel_cmd_args.validate->header.found) {
    const bool fuel_gauge_valid = max17262_validate();
    if (fuel_gauge_valid) {
      printf("Fuel gauge communication is valid\n");
    } else {
      printf("Fuel gauge communication error\n");
    }
  }

  if (fuel_cmd_args.status->header.found) {
    max17262_regdump_t registers = {0};
    max17262_get_regdump(&registers);
    printf("RepSOC = %0.2f%%\n", (double)registers.soc / 1000);
    printf("VCell = %u mV\n", registers.vcell);
    printf("AvgCurrent = %d mA\n", registers.avg_current);
    printf("Cycles = %d\n", registers.cycles);
  }

  if (fuel_cmd_args.reset->header.found) {
    if (max17262_por_initialise()) {
      printf("POR complete\n");
    } else {
      printf("POR failed\n");
    }
  }

  if (fuel_cmd_args.clear->header.found) {
    max17262_clear_modelgauge();
    printf("Modelgauge config cleared\n");
  }
}
