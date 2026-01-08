#include "shell.h"

#include "shell_argparse.h"
#include "shell_cmd.h"
#include "shell_priv.h"
#include "shell_vt100.h"

#include <limits.h>
#include <stdarg.h>
#include <string.h>

#ifndef SHELL_PROMPT_TEXT
#error "SHELL_PROMPT_TEXT must be defined"
#endif

#define SHELL_PROMPT SHELL_BOLD(SHELL_COLOUR_MAGENTA) SHELL_PROMPT_TEXT SHELL_COLOUR_RESET

#define WRITE_ERROR(e) \
  shell_write(SHELL_COLOUR(SHELL_COLOUR_RED) "\r\nERROR: " e "\n" SHELL_COLOUR_RESET);

#define IS_PRINTABLE(c) (c >= ' ' && c <= '~') /* ASCII 32-126 */

typedef union {
  intptr_t value_int;
  const char* value_str;
  void* ptr;
} parsed_arg_t;

static shell_context_t ctx = {0};

static void flush_echo(void);
static void clear_input(uint32_t len);
static void process_line(void);
static void update_history(void);
static void reset_context(void);
static bool line_buffer_push(const char c);

void shell_init(const shell_config_t* config) {
  if (config->vprintf_func == NULL) {
    return;
  }

  ctx.config = config;

  shell_write("\n");
  reset_context();
}

int shell_write(const char* format, ...) {
  if (ctx.config->vprintf_func == NULL) {
    return 0;
  }

  va_list va;
  va_start(va, format);
  const int result = ctx.config->vprintf_func(format, va);
  va_end(va);
  return result;
}

shell_context_t* shell_context(void) {
  return &ctx;
}

void shell_process(const uint8_t* data, uint32_t length) {
  for (uint32_t i = 0; i < length; i++) {
    const char c = data[i];

    /* Handle escape sequences */
    if (ctx.escape_sequence_index == 0 && c == SHELL_VT100_ASCII_ESC) {
      /* Escape sequence start */
      ctx.escape_sequence_index++;
      continue;
    } else if (ctx.escape_sequence_index == 1) {
      if (c == '[') {
        ctx.escape_sequence_index++;
      } else {
        /* Invalid escape sequence */
        ctx.escape_sequence_index = 0;
      }
      continue;
    } else if (ctx.escape_sequence_index == 2) {
      /* Process the escape sequence */
      ctx.escape_sequence_index = 0;

      bool replace_line = false;
      switch (c) {
        /* Up arrow key */
        case SHELL_VT100_ASCII_UP:
          if (ctx.history.index + 1 < (int32_t)ctx.history.len) {
            ctx.history.index++;
            replace_line = true;
          }
          break;

        /* Down arrow key */
        case SHELL_VT100_ASCII_DOWN:
          if (ctx.history.index > -1) {
            ctx.history.index--;
            replace_line = true;
          }
          break;

        default:
          break;
      }

      /* Replace the current line with a line from history, both in memory and in the terminal */
      if (replace_line) {
        const char* history_line =
          (ctx.history.index == -1)
            ? ""
            : ctx.history.buffer[(ctx.history.start_index + ctx.history.len - ctx.history.index) %
                                 SHELL_HISTORY_DEPTH];
        clear_input(strlen(history_line));
        strncpy(ctx.line_buffer, history_line, SHELL_MAX_HISTORY_LINE_LEN);
        ctx.line_len = strlen(ctx.line_buffer);
        shell_write(ctx.line_buffer);
      }
      continue;
    }

    /* Handle control characters */
    switch (c) {
      /* Newline (process line input) */
      case '\n': /* falls-through */
      case '\r':
        flush_echo();
        shell_write("\n");
        process_line();
        reset_context();
        break;

        /* Control+C (cancel line input) */
      case SHELL_VT100_ASCII_CTRL_C:
        flush_echo();
        shell_write("\n");
        reset_context();
        ctx.echo = NULL;
        break;

      /* Backspace (walk back line buffer) */
      case SHELL_VT100_ASCII_BSPACE:
        flush_echo();

        if (ctx.line_len) {
          shell_write("\b \b"); /* Backspace, replace char with space (blank), backspace again */
          ctx.line_buffer[--ctx.line_len] = '\0';
        }
        break;

      default:
        break;
    }

    /* Skip non-printable characters */
    if (!IS_PRINTABLE(c)) {
      continue;
    }

    /* Echo printable characters */
    if (!ctx.echo) {
      ctx.echo = &ctx.line_buffer[ctx.line_len];
    }

    /* Push printable characters into the line buffer */
    if (line_buffer_push(c) == false) {
      /* Line buffer overflow */
      WRITE_ERROR("shell buffer overflow");
      reset_context();
      ctx.echo = NULL;
    }
  }

  flush_echo();
}

static void flush_echo(void) {
  if (ctx.echo) {
    shell_write(ctx.echo);
    ctx.echo = NULL;
  }
}

static void clear_input(uint32_t len) {
  if (len < ctx.line_len) {
    const uint32_t backspaces = ctx.line_len - len;
    for (uint32_t i = 0; i < backspaces; i++) {
      shell_write("\b");
    }
    for (uint32_t i = 0; i < backspaces; i++) {
      shell_write(" ");
    }
  }
  shell_write("\r" SHELL_PROMPT);
}

static void process_line(void) {
  shell_command_t* command = NULL;
  char* token_start = NULL;

  /* Reset parser state */
  ctx.cmd.argc = 0;

  /* Add line to history before parsing the command/first token */
  update_history();

  for (uint32_t i = 0; i <= ctx.line_len; i++) {
    const char c = ctx.line_buffer[i];

    /* Token parser */
    /* Limitations:
     *  - Only accepts one space between characters
     *  - Does not support quoted string arguments
     */
    if (c == ' ' || c == '\0') {
      /* Null token (empty line or extra whitespace) */
      if (!token_start) {
        if (command) {
          WRITE_ERROR("extra whitespace between arguments");
        }

        return;
      }

      if (ctx.cmd.argc >= (SHELL_MAX_ARGS - 1)) {
        WRITE_ERROR("too many arguments");
        return;
      }

      /* Split tokens in the line buffer by NULL terminators */
      ctx.line_buffer[i] = '\0';

      /* Build argc/argv table */
      ctx.cmd.argv[ctx.cmd.argc++] = token_start;
      token_start = NULL;
    } else if (!token_start) {
      token_start = &ctx.line_buffer[i];
    }
  }

  /* Parse the first argv[0] command */
  command = (shell_command_t*)find_command(ctx.cmd.argv[0]);
  if (!command) {
    shell_write(SHELL_COLOUR(SHELL_COLOUR_RED) "Command not found: ");
    shell_write(ctx.cmd.argv[0]);
    shell_write("\n" SHELL_COLOUR_RESET);
    return;
  }

#if 0
  printf("argc: %lu\r\n", ctx.cmd.argc);
  for (uint32_t i = 0; i < ctx.cmd.argc; i++) {
    printf(" argv[%lu]: %s\r\n", i, ctx.cmd.argv[i]);
  }
#endif

  /* Run the command */
  // TODO: use a mutex to block console writes while a command is running?
  ctx.cmd.is_running = true;
  if (command->handler != NULL) {
    command->handler(ctx.cmd.argc, ctx.cmd.argv);
  }
  ctx.cmd.is_running = false;
}

static void update_history(void) {
  /* Ignore empty lines */
  if (strlen(ctx.line_buffer) == 0) {
    return;
  }

  /* Ignore specific commands */
  if (memcmp(ctx.line_buffer, "wca", 3) == 0) {
    return;
  }

  const uint32_t write_index =
    (ctx.history.start_index + ctx.history.len + 1) % SHELL_HISTORY_DEPTH;

  /* Ignore duplicate entries */
  if (memcmp(ctx.history.buffer[write_index - 1], ctx.line_buffer, SHELL_MAX_HISTORY_LINE_LEN) ==
      0) {
    return;
  }

  strncpy(ctx.history.buffer[write_index], ctx.line_buffer, SHELL_MAX_HISTORY_LINE_LEN);

  if (ctx.history.len == SHELL_HISTORY_DEPTH) {
    /* Overwrite oldest history line when history depth is reached */
    ctx.history.start_index = (ctx.history.start_index + 1) % SHELL_HISTORY_DEPTH;
  } else {
    ctx.history.len++;
  }
}

static void reset_context(void) {
  ctx.line_len = 0;
  ctx.line_buffer[0] = '\0';
  ctx.escape_sequence_index = 0;
  ctx.history.index = -1;
  ctx.cmd.argc = 0;
  shell_write(SHELL_PROMPT);
}

static bool line_buffer_push(const char c) {
  ctx.line_buffer[ctx.line_len++] = c;
  ctx.line_buffer[ctx.line_len] = '\0';

  if (ctx.line_len == SHELL_MAX_LINE_LEN) {
    return false;
  }

  return true;
}
