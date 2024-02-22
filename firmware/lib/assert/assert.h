#pragma once

#include "attributes.h"

#include <stdbool.h>
#include <stdlib.h>

#undef assert
#define assert ASSERT

#ifdef EMBEDDED_BUILD

typedef void (*assert_handler_t)(void*, void*);

void assert_init(assert_handler_t handler_cb);
NO_RETURN void _assert_handler(void);

#define ASSERT(expr)     \
  do {                   \
    if (!(expr)) {       \
      _assert_handler(); \
    }                    \
  } while (false)

#if 0
#include "printf.h"
#define ASSERT(expr)                                                          \
  do {                                                                        \
    if (!(expr)) {                                                            \
      LOGE("[%s:%d] assert '%s' in %s", __FILE__, __LINE__, #expr, __func__); \
      abort();                                                                \
    }                                                                         \
  } while (false)
#endif

#define ASSERT_E(expr, exitcode) ASSERT(expr)

// Conditional assert which only crashes on embedded target. Use this for ASSERTs
// that would mess up libfuzzer, but should still crash on target.
#define ASSERT_EMBEDDED_ONLY ASSERT

#else  // Host builds, e.g. for unit tests

#include <stdio.h>

// ASSERT with a specific exit code so that unit tests can check which
// assertion in a function happened.
#define ASSERT_E(expr, exitcode)                                                  \
  do {                                                                            \
    if (!(expr)) {                                                                \
      printf("[%s:%d] assert '%s' in %s\n", __FILE__, __LINE__, #expr, __func__); \
      exit(exitcode);                                                             \
    }                                                                             \
  } while (false)

#define ASSERT(expr)         ASSERT_E(expr, 9876)
#define ASSERT_EMBEDDED_ONLY (void)

#endif
