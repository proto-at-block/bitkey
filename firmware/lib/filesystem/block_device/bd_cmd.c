#include "lfs_bd.h"
#include "printf.h"
#include "shell_cmd.h"

static struct {
  arg_lit_t* yes;
  arg_end_t* end;
} cmd_erase_args;

static void cmd_erase_register(void);
static void cmd_erase_run(int argc, char** argv);

static void print_bd_error(char* command, const int error);

static void cmd_erase_register(void) {
  cmd_erase_args.yes = ARG_LIT_REQ('y', "yes", "confirm erase");
  cmd_erase_args.end = ARG_END();

  static shell_command_t erase_cmd = {
    .command = "erase",
    .help = "erase filesystem flash sectors",
    .handler = &cmd_erase_run,
    .argtable = &cmd_erase_args,
  };

  shell_command_register(&erase_cmd);
}
SHELL_CMD_REGISTER("erase", cmd_erase_register);

static void cmd_erase_run(int argc, char** argv) {
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_erase_args);
  if (nerrors) {
    return;
  }

  if (cmd_erase_args.yes->header.found) {
    int ret = bd_erase_all();
    if (ret == 0) {
      printf("filesystem erased\ndevice must be reset, fs is in an unknown state!\n");
    } else {
      print_bd_error("erase", ret);
    }
  }
}

static void print_bd_error(char* command, const int error) {
  static char error_buf[64] = {0};
  if (bd_error_str(error_buf, 64, error)) {
    printf("%s: %s\n", command, error_buf);
  }
}
