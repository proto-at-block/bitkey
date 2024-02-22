#include "log.h"

#include <stdarg.h>

#ifdef EMBEDDED_BUILD
#include "printf.h"
#else
#include <stdio.h>
#endif

#define GENERATE_STRING(s) #s,
static const char* log_level_strings[] = {LOG_LEVELS(GENERATE_STRING)};

static log_level_t g_level SHARED_TASK_DATA = LOG_DEBUG;

void log_set_level(log_level_t level) {
  g_level = level;
}

void _log(log_level_t level, const char* colour, const char* file, int line, const char* format,
          ...) {
  if (level < g_level) {
    return;
  }

  printf("%s[%s](%s:%d) " SHELL_COLOUR_RESET, colour, log_level_strings[level], file, line);

  va_list args;
  va_start(args, format);
  vprintf(format, args);
  va_end(args);

  printf("\n");
}
