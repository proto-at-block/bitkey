#include "shell_help.h"

#include "printf.h"
#include "shell.h"
#include "shell_cmd.h"
#include "utlist.h"

#include <string.h>

static void help_cmd_register(void);
static void help_cmd_handler(int argc, char** argv);

static void help_cmd_register(void) {
  static shell_command_t help_cmd = {
    .command = "help",
    .help = "prints all available commands",
    .handler = help_cmd_handler,
  };

  shell_command_register(&help_cmd);
}
SHELL_CMD_REGISTER("help", help_cmd_register);

static void help_cmd_handler(int argc, char** argv) {
  (void)argc;
  (void)argv;
  /* Print summary of each command */
  shell_command_t* cmd_list = cmd_list_get();
  shell_command_t* it;

  cmd_list_lock();
  size_t padding = 0;
  /* Find longest command name */
  LL_FOREACH (cmd_list, it) {
    const size_t len = strlen(it->command);
    if (padding < len) {
      padding = len;
    }
  }
  padding += strlen("    ");

  /* Print each command */
  LL_FOREACH (cmd_list, it) {
    size_t len = printf("%s", it->command);
    if (it->help != NULL) {
      len += printf("    ");
      printf("%*s%s", padding - len, "", it->help);
    }
    printf("\n");
  }
  cmd_list_unlock();
}

void print_command_usage(const shell_command_t* cmd) {
  /* First line: command name */
  printf("Usage:\n", cmd->command);

  if (cmd->help == NULL) {
    return;
  }
  printf("%s\n", cmd->help);

  if (cmd->argtable == NULL) {
    return;
  }

  arg_header_t** table = (arg_header_t**)cmd->argtable;
  size_t print_width = 0;
  size_t len = 0;
  for (uint32_t i = 0; table[i]->type != ARG_TYPE_END; i++) {
    arg_header_t* arg = table[i];
    len = 0;
    if (arg->shortname) {
      len += strlen("-x");
    }
    if (arg->shortname && arg->longname) {
      len += strlen(", ");
    }
    if (arg->longname) {
      len += strlen(arg->longname) + 2;
    }
    switch (arg->type) {
      case ARG_TYPE_INT:
        len += strlen("=<int>");
        break;
      case ARG_TYPE_BOOL:
        len += strlen("=<bool>");
        break;
      case ARG_TYPE_STR:
        len += strlen("=<str>");
        break;
      case ARG_TYPE_LIT:
        /* literals have no value */
        /* fall-through */
      default:
        break;
    }
    len = (len + 3) - ((len + 3) & 3);
    if (print_width < len) {
      print_width = len;
    }
  }
  print_width += strlen("    ");

  for (uint32_t i = 0; table[i]->type != ARG_TYPE_END; i++) {
    arg_header_t* arg = table[i];
    size_t pos = printf("    ");
    if (arg->shortname) {
      pos += printf("-%c", arg->shortname);
    }
    if (arg->shortname && arg->longname) {
      pos += printf(", ");
    }
    if (arg->longname) {
      pos += printf("--%s", arg->longname);
    }
    switch (arg->type) {
      case ARG_TYPE_INT:
        pos += printf("=<int>");
        break;
      case ARG_TYPE_BOOL:
        pos += printf("=<bool>");
        break;
      case ARG_TYPE_STR:
        pos += printf("=<str>");
        break;
      default:
        break;
    }

    /* Print option help description */
    size_t padding = 0;
    if (pos <= print_width) {
      padding = print_width - pos;
    } else {
      printf("\n");
      padding = print_width;
    }
    printf("%*s%s\n", (int)padding + 2, "", arg->help);
  }
}
