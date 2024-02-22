#include "metadata.h"
#include "printf.h"
#include "shell_cmd.h"

static struct {
  arg_lit_t* app;
  arg_lit_t* bl;
  arg_end_t* end;
} meta_cmd_args;

static void meta_cmd_register(void);
static void meta_cmd_handler(int argc, char** argv);

static void meta_cmd_register(void) {
  meta_cmd_args.app = ARG_LIT_OPT('a', "application", "prints the application metadata");
  meta_cmd_args.bl = ARG_LIT_OPT('b', "bootloader", "prints the bootloader metadata");
  meta_cmd_args.end = ARG_END();

  static shell_command_t meta_cmd = {
    .command = "meta",
    .help = "print firmware metadata",
    .handler = meta_cmd_handler,
    .argtable = &meta_cmd_args,
  };

  shell_command_register(&meta_cmd);
}
SHELL_CMD_REGISTER("meta", meta_cmd_register);

static void meta_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&meta_cmd_args);

  if (nerrors) {
    return;
  }

  if (meta_cmd_args.bl->header.found) {
    metadata_print(META_TGT_BL);
  } else {
    metadata_print(META_TGT_APP_A);
    metadata_print(META_TGT_APP_B);
  }
}
