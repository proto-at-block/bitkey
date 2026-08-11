/**
 * @file posix_log.c
 * @brief POSIX-specific log implementation for core-sim
 *
 * This overrides lib/log/src/log.c to write to stderr instead of stdout.
 * This is critical because core-sim uses stdout for the binary protocol.
 */

#include "log.h"

#include <stdarg.h>
#include <stdio.h>

#define GENERATE_STRING(s) #s,
static const char* log_level_strings[] = {LOG_LEVELS(GENERATE_STRING)};

static log_level_t g_level = LOG_DEBUG;

void log_set_level(log_level_t level) {
  g_level = level;
}

log_level_t log_get_level(void) {
  return g_level;
}

void _log(log_level_t level, const char* colour, const char* file, int line, const char* format,
          ...) {
  if (level < g_level) {
    return;
  }

  // CRITICAL: Use stderr, not stdout (stdout is used for the binary protocol)
  fprintf(stderr, "%s[%s](%s:%d) " SHELL_COLOUR_RESET, colour, log_level_strings[level], file,
          line);

  va_list args;
  va_start(args, format);
  vfprintf(stderr, format, args);
  va_end(args);

  fprintf(stderr, "\n");
}
