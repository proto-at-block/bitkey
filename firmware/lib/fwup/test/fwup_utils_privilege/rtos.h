#pragma once

#include <stdbool.h>

void fwup_utils_test_set_privileged(bool privileged);
bool fwup_utils_test_is_privileged(void);

#define RTOS_THREAD_WITH_PRIVILEGE(...)                          \
  do {                                                           \
    const bool was_privileged = fwup_utils_test_is_privileged(); \
    if (!was_privileged) {                                       \
      fwup_utils_test_set_privileged(true);                      \
    }                                                            \
    { __VA_ARGS__; }                                             \
    if (!was_privileged) {                                       \
      fwup_utils_test_set_privileged(false);                     \
    }                                                            \
  } while (0)
