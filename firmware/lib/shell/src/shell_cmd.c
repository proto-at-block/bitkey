#include "shell_cmd.h"

#include "printf.h"
#include "shell.h"
#include "shell_priv.h"
#include "utlist.h"

#include <string.h>

static shell_command_t* cmd_list = NULL;
static bool initialised = false;

static void cmd_init(void);
static int compare_command_name(shell_command_t* a, shell_command_t* b);

void shell_command_register(shell_command_t* cmd) {
  if (!cmd || cmd->command == NULL) {
    return; /* Invalid arg */
  }

  /* First char of command can't be a space */
  if (strchr(cmd->command, ' ') != NULL) {
    return; /* Invalid arg */
  }

  shell_command_t* item = NULL;
  item = (shell_command_t*)find_command(cmd->command);
  if (item) {
    return; /* Command already registered */
  }

  /* Insert command in sorted alphabetical order */
  cmd_list_lock();
  cmd->next = NULL;
  LL_INSERT_INORDER(cmd_list, cmd, compare_command_name);
  cmd_list_unlock();
}

const shell_command_t* find_command(const char* name) {
  const shell_command_t* cmd = NULL;
  shell_command_t* it;

  const int len = (int)strlen(name);

  cmd_list_lock();
  LL_FOREACH (cmd_list, it) {
    if ((int)strlen(it->command) == len && (int)strcmp(name, it->command) == 0) {
      cmd = it;
      break;
    }
  }
  cmd_list_unlock();

  return cmd;
}

bool cmd_list_lock(void) {
  if (!initialised) {
    cmd_init();
  }

  shell_context_t* ctx = shell_context();
  if (ctx->config && ctx->config->mutex_lock) {
    return ctx->config->mutex_lock();
  }

  return true;
}

bool cmd_list_unlock(void) {
  shell_context_t* ctx = shell_context();
  if (ctx->config && ctx->config->mutex_unlock) {
    return ctx->config->mutex_unlock();
  }

  return true;
}

shell_command_t* cmd_list_get(void) {
  return cmd_list;
}

static void cmd_init(void) {
  shell_context_t* ctx = shell_context();
  if (ctx->config && ctx->config->mutex_create) {
    ctx->config->mutex_create();
  }
  initialised = true;
}

static int compare_command_name(shell_command_t* a, shell_command_t* b) {
  return strcmp(a->command, b->command);
}
