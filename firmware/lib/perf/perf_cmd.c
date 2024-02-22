#include "perf.h"
#include "printf.h"
#include "shell_cmd.h"

static struct {
  arg_str_t* pattern;
  arg_end_t* end;
} cmd_perf_args;

static void cmd_perf_register(void);
static void cmd_perf_run(int argc, char** argv);

static void cmd_perf_register(void) {
  cmd_perf_args.pattern = ARG_STR_OPT(0, NULL, "search pattern string (? or *)");
  cmd_perf_args.end = ARG_END();

  static shell_command_t cmd = {
    .command = "perf",
    .help = "print performance counters",
    .handler = cmd_perf_run,
    .argtable = &cmd_perf_args,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("perf", cmd_perf_register);

static void cmd_perf_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_perf_args);

  if (nerrors) {
    return;
  }

  if (cmd_perf_args.pattern->header.found) {
    const char* pattern = cmd_perf_args.pattern->value;
    perf_print_search(pattern);
  } else {
    perf_print_all();
  }
}
