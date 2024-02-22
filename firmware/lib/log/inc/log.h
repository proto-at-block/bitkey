#pragma once

#include "assert.h"
#include "shell_vt100.h"

#include <string.h>

#define LOG_COLOUR_INFO   SHELL_COLOUR(SHELL_COLOUR_GREEN)
#define LOG_COLOUR_WARN   SHELL_COLOUR(SHELL_COLOUR_YELLOW)
#define LOG_COLOUR_DEBUG  SHELL_COLOUR(SHELL_COLOUR_CYAN)
#define LOG_COLOUR_ERROR  SHELL_COLOUR(SHELL_COLOUR_RED)
#define LOG_FORMAT(level) LOG_COLOUR_##level

// Log levels from louder to quieter.
#define LOG_LEVELS(X) \
  X(DEBUG)            \
  X(INFO)             \
  X(WARN)             \
  X(ERROR)            \
  X(NONE)

#define GENERATE_ENUM(e) LOG_##e,
typedef enum { LOG_LEVELS(GENERATE_ENUM) } log_level_t;

#define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
void _log(log_level_t level, const char* colour, const char* file, int line, const char* format,
          ...);

#ifdef EMBEDDED_BUILD
#include "memfault/core/log.h"

#define _TRANSLATE_LOG_LEVEL(level)                                                 \
  ({                                                                                \
    eMemfaultPlatformLogLevel memfault_level = kMemfaultPlatformLogLevel_NumLevels; \
    switch (level) {                                                                \
      case LOG_DEBUG:                                                               \
        memfault_level = kMemfaultPlatformLogLevel_Debug;                           \
        break;                                                                      \
      case LOG_INFO:                                                                \
        memfault_level = kMemfaultPlatformLogLevel_Info;                            \
        break;                                                                      \
      case LOG_WARN:                                                                \
        memfault_level = kMemfaultPlatformLogLevel_Warning;                         \
        break;                                                                      \
      case LOG_ERROR:                                                               \
        memfault_level = kMemfaultPlatformLogLevel_Error;                           \
        break;                                                                      \
    }                                                                               \
    memfault_level;                                                                 \
  })

#if defined(DISABLE_PRINTF)
#define __log(level, colour, ...)
#else
#define __log(level, colour, ...) _log(level, colour, __VA_ARGS__)
#endif

#define _LOG(level, colour, ...)                                            \
  do {                                                                      \
    eMemfaultPlatformLogLevel memfault_level = _TRANSLATE_LOG_LEVEL(level); \
    MEMFAULT_COMPACT_LOG_SAVE(memfault_level, __VA_ARGS__);                 \
    __log(level, colour, __FILENAME__, __LINE__, __VA_ARGS__);              \
  } while (0)

#else

#define _LOG(level, colour, ...) _log(level, colour, __FILENAME__, __LINE__, __VA_ARGS__)

#endif

// Public API

void log_set_level(log_level_t level);

#define LOGI(...) _LOG(LOG_INFO, LOG_FORMAT(INFO), __VA_ARGS__)
#define LOGD(...) _LOG(LOG_DEBUG, LOG_FORMAT(DEBUG), __VA_ARGS__)
#define LOGW(...) _LOG(LOG_WARN, LOG_FORMAT(WARN), __VA_ARGS__)
#define LOGE(...) _LOG(LOG_ERROR, LOG_FORMAT(ERROR), __VA_ARGS__)

// Assert with a custom error message.
//
// NOTE: This function has to live here instead of lib/assert
// because it depends on memfault's compact logging, and lib/assert is
// used in lower-level code that doesn't link against memfault's sdk.
#define ASSERT_LOG(expr, ...) \
  do {                        \
    if (!(expr)) {            \
      LOGE(__VA_ARGS__);      \
      _assert_handler();      \
    }                         \
  } while (false)
