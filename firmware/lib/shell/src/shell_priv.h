#pragma once

#include <stdint.h>

#define SHELL_MAX_LINE_LEN         (2048)
#define SHELL_MAX_HISTORY_LINE_LEN (128)
#define SHELL_HISTORY_DEPTH        (10)
#define SHELL_MAX_ARGS             (10)

typedef struct {
  char line_buffer[SHELL_MAX_LINE_LEN];
  uint32_t line_len;
  const char* echo;
  uint32_t escape_sequence_index;
  struct {
    char buffer[SHELL_HISTORY_DEPTH][SHELL_MAX_HISTORY_LINE_LEN];
    uint32_t start_index;
    int32_t index;
    uint32_t len;
  } history;
  struct {
    bool is_running;
    uint32_t argc;
    char* argv[SHELL_MAX_ARGS];
  } cmd;
  const shell_config_t* config;
} shell_context_t;

shell_context_t* shell_context(void);
