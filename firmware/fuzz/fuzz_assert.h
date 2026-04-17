/**
 * fuzz_assert.h — LibFuzzer-compatible ASSERT override.
 *
 * Include this file AFTER all firmware headers in fuzz targets that exercise
 * code paths guarded by ASSERT.  The standard host ASSERT calls exit(9876),
 * which kills the in-process LibFuzzer engine.  Redefining it to
 * __builtin_trap() makes violations appear as SIGILL crashes that LibFuzzer
 * can detect, record, and reproduce without terminating the fuzzer process.
 *
 * Usage (in fuzz harness .cc files):
 *
 *   extern "C" {
 *   #include "firmware_header.h"
 *   // ... other firmware includes ...
 *   #include "fuzz_assert.h"   // <-- last, after all firmware headers
 *   }
 *
 * This does NOT affect production builds.  It is only compiled when
 * explicitly #include'd inside a fuzz target.
 */

#pragma once

#undef ASSERT
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ASSERT(expr)    \
  do {                  \
    if (!(expr)) {      \
      __builtin_trap(); \
    }                   \
  } while (false)
