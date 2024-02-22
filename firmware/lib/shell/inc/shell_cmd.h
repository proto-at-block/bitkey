#pragma once

#include "shell_argparse.h"

#include <stdbool.h>
#include <stdint.h>

/* Shell commands init */
typedef void (*cmdregister_t)(void);
typedef struct {
  const char* fn_name;
  cmdregister_t fn_register;
} shellcmd_t;

extern shellcmd_t __shell_cmds_start[], __shell_cmds_end[];

#define __define_shell_cmd(rname, rfn)                                                     \
  static shellcmd_t __shell_cmd_##rfn __attribute__((__section__(".shell_cmds.register"))) \
  __attribute__((__used__)) = {                                                            \
    .fn_name = rname,                                                                      \
    .fn_register = rfn,                                                                    \
  };

#define SHELL_CMD_REGISTER(rname, rfn) __define_shell_cmd(rname, rfn)

#define SHELL_CMD_REGISTER_ALL()                                             \
  {                                                                          \
    for (shellcmd_t* fn = __shell_cmds_start; fn < __shell_cmds_end; fn++) { \
      if (fn->fn_register) {                                                 \
        fn->fn_register();                                                   \
      }                                                                      \
    }                                                                        \
  }

/* Shell command definition */
typedef void (*shell_cmd_handler_t)(int argc, char** argv);

typedef struct shell_cmd_ {
  const char* command;
  const char* help;
  shell_cmd_handler_t handler;
  void* argtable;

  /* Internal pointer to next command in linked list */
  struct shell_cmd_* next;
} shell_command_t;

void shell_command_register(shell_command_t* command);
const shell_command_t* find_command(const char* name);
bool cmd_list_lock(void);
bool cmd_list_unlock(void);
shell_command_t* cmd_list_get(void);
