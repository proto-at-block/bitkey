#pragma once

// These are debug log macros used within the Memfault SDK.
// They exist mostly for development purposes to catch misconfiguration.
// We use printf() here to break a circular dependency with
// the memfault-log module.
#include "printf.h"

#define _MEMFAULT_DEBUGGING_LOG(fmt, ...) \
  do {                                    \
    printf("[memfault] ");                \
    printf(fmt, ##__VA_ARGS__);           \
    printf("\n");                         \
  } while (0)

#define MEMFAULT_LOG_DEBUG(fmt, ...) _MEMFAULT_DEBUGGING_LOG(fmt, ##__VA_ARGS__)
#define MEMFAULT_LOG_INFO(fmt, ...)  _MEMFAULT_DEBUGGING_LOG(fmt, ##__VA_ARGS__)
#define MEMFAULT_LOG_WARN(fmt, ...)  _MEMFAULT_DEBUGGING_LOG(fmt, ##__VA_ARGS__)
#define MEMFAULT_LOG_ERROR(fmt, ...) _MEMFAULT_DEBUGGING_LOG(fmt, ##__VA_ARGS__)
