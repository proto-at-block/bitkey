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

// Manufacturing-test builds always emit raw ASCII over UART — factory stations
// don't have ELFs to decode tokenized frames against.
#if defined(MFGTEST) && !defined(LOG_FORCE_RAW)
#define LOG_FORCE_RAW 1
#endif

#if defined(LOG_TOKENIZED) && !defined(DISABLE_PRINTF) && !defined(LOG_FORCE_RAW)
#include "log_uart.h"
#endif

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

// `_LOG` always feeds Memfault's compact log RAM ring (so cloud upload still
// works regardless of UART path). The UART transport is selected at compile
// time:
//   - LOG_TOKENIZED defined: emit a binary tokenized frame (small, fast).
//   - otherwise:             emit the legacy formatted ASCII line.
//
// A translation unit can opt out of tokenization by defining `LOG_FORCE_RAW`
// before including this header, which forces every LOG* call in that TU onto
// the formatted ASCII path. Use this for panic/early-boot/mfgtest code where a
// missing token DB would lose information.
//
// IMPORTANT: on the tokenized path, `__VA_ARGS__` is evaluated by both the
// Memfault save and the UART emit. Avoid passing expressions with side
// effects (mutex acquires, counter increments, single-shot getters) — they
// will run twice. Pre-compute into a local instead.
#if defined(LOG_TOKENIZED) && !defined(DISABLE_PRINTF) && !defined(LOG_FORCE_RAW)
// `MEMFAULT_LOG_FMT_ELF_SECTION_ENTRY` declares a function-local static
// `_memfault_log_fmt_ptr`, so its address must be taken from the same scope it
// is declared in. We therefore inline the contents of `MEMFAULT_COMPACT_LOG_SAVE`
// here so that both the Memfault save (RAM ring) and `log_uart_emit_compact`
// (UART) reference the same token offset and only one entry is emitted per
// call site.
//
// The Memfault save runs unconditionally (matches pre-tokenization behavior:
// the cloud-upload RAM ring captures every level for crash diagnostics). The
// UART emit honors the runtime `log_get_level()` filter so `log_set_level()`
// can quiet the UART for sensitive flows, mirroring the pre-tokenization
// `_log()` semantics.
#define _LOG_TOKENIZED_BODY(level, memfault_level, format, ...)                       \
  MEMFAULT_LOGGING_RUN_COMPILE_TIME_CHECKS(format, ##__VA_ARGS__);                    \
  MEMFAULT_LOG_FMT_ELF_SECTION_ENTRY(format, ##__VA_ARGS__);                          \
  memfault_compact_log_save(memfault_level, MEMFAULT_LOG_FMT_ELF_SECTION_ENTRY_PTR,   \
                            MFLT_GET_COMPRESSED_LOG_FMT(__VA_ARGS__), ##__VA_ARGS__); \
  if ((level) >= log_get_level()) {                                                   \
    log_uart_emit_compact(memfault_level, MEMFAULT_LOG_FMT_ELF_SECTION_ENTRY_PTR,     \
                          MFLT_GET_COMPRESSED_LOG_FMT(__VA_ARGS__), ##__VA_ARGS__);   \
  }

#define _LOG(level, colour, ...)                                            \
  do {                                                                      \
    eMemfaultPlatformLogLevel memfault_level = _TRANSLATE_LOG_LEVEL(level); \
    _LOG_TOKENIZED_BODY(level, memfault_level, __VA_ARGS__);                \
    (void)colour;                                                           \
  } while (0)
#else
#define _LOG(level, colour, ...)                                            \
  do {                                                                      \
    eMemfaultPlatformLogLevel memfault_level = _TRANSLATE_LOG_LEVEL(level); \
    MEMFAULT_COMPACT_LOG_SAVE(memfault_level, __VA_ARGS__);                 \
    __log(level, colour, __FILENAME__, __LINE__, __VA_ARGS__);              \
  } while (0)
#endif

// Compact-only: skips the local printf fallback so the format string is
// stripped from flash at link time (only Memfault decoders see it). The
// `colour` argument is intentionally absent so no ANSI escape strings end up
// in .rodata. Use in flash-constrained paths.
#define _LOG_COMPACT(level, ...)                                            \
  do {                                                                      \
    eMemfaultPlatformLogLevel memfault_level = _TRANSLATE_LOG_LEVEL(level); \
    MEMFAULT_COMPACT_LOG_SAVE(memfault_level, __VA_ARGS__);                 \
  } while (0)

#else

#define _LOG(level, colour, ...) _log(level, colour, __FILENAME__, __LINE__, __VA_ARGS__)
#define _LOG_COMPACT(level, ...) _log(level, "", __FILENAME__, __LINE__, __VA_ARGS__)

#endif

// Public API

void log_set_level(log_level_t level);
log_level_t log_get_level(void);

#define LOGI(...) _LOG(LOG_INFO, LOG_FORMAT(INFO), __VA_ARGS__)
#define LOGD(...) _LOG(LOG_DEBUG, LOG_FORMAT(DEBUG), __VA_ARGS__)
#define LOGW(...) _LOG(LOG_WARN, LOG_FORMAT(WARN), __VA_ARGS__)
#define LOGE(...) _LOG(LOG_ERROR, LOG_FORMAT(ERROR), __VA_ARGS__)

// Per-call escape hatch: emit a raw (un-tokenized) UART log frame regardless of
// the build-wide tokenization setting. The host decoder renders these as plain
// text without consulting the ELF token database. Use sparingly — for panics,
// pre-RTOS boot, mfgtest, and other situations where a missing token DB would
// be unacceptable. To force every LOG* call in a TU onto the raw path, define
// `LOG_FORCE_RAW` before including this header.
#if defined(DISABLE_PRINTF)
#define _LOG_RAW(level, colour_tok, ...) ((void)0)
#elif defined(EMBEDDED_BUILD) && defined(LOG_TOKENIZED)
// Honor `log_set_level()` on the raw UART path too, matching both the
// tokenized compact-log path (_LOG_TOKENIZED_BODY) and the legacy `_log()`
// fallback. Without this gate, raising the threshold via log_set_level()
// silences LOGI/LOGD but raw frames would still go out on the wire.
#define _LOG_RAW(level, colour_tok, ...)                                                   \
  do {                                                                                     \
    if ((level) >= log_get_level()) {                                                      \
      log_uart_emit_raw(_TRANSLATE_LOG_LEVEL(level), __FILENAME__, __LINE__, __VA_ARGS__); \
    }                                                                                      \
  } while (0)
#else
// Legacy ASCII fallback: pick the colour matching the level so LOGE_RAW stays
// red, LOGW_RAW yellow, etc. (otherwise everything would render in INFO green).
// `_log()` honors `g_level` internally.
#define _LOG_RAW(level, colour_tok, ...) \
  _log(level, LOG_FORMAT(colour_tok), __FILENAME__, __LINE__, __VA_ARGS__)
#endif

#define LOGI_RAW(...) _LOG_RAW(LOG_INFO, INFO, __VA_ARGS__)
#define LOGD_RAW(...) _LOG_RAW(LOG_DEBUG, DEBUG, __VA_ARGS__)
#define LOGW_RAW(...) _LOG_RAW(LOG_WARN, WARN, __VA_ARGS__)
#define LOGE_RAW(...) _LOG_RAW(LOG_ERROR, ERROR, __VA_ARGS__)

// Memfault-only variants: format strings are stripped from flash. No console
// output on the device. Use when flash is tight or for high-volume breadcrumb
// logs that don't need to appear on the serial console.
#define MFLOGI(...) _LOG_COMPACT(LOG_INFO, __VA_ARGS__)
#define MFLOGD(...) _LOG_COMPACT(LOG_DEBUG, __VA_ARGS__)
#define MFLOGW(...) _LOG_COMPACT(LOG_WARN, __VA_ARGS__)
#define MFLOGE(...) _LOG_COMPACT(LOG_ERROR, __VA_ARGS__)

#ifdef EMBEDDED_BUILD
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
#elif defined(FUZZ_BUILD)
// Fuzz builds: evaluate expr for side-effects but don't abort.
// Production code after ASSERT_LOG typically returns an error code.
#define ASSERT_LOG(expr, ...) \
  do {                        \
    (void)(expr);             \
  } while (false)
#else
#define ASSERT_LOG(expr, ...) \
  do {                        \
    if (!(expr)) {            \
      LOGE(__VA_ARGS__);      \
      abort();                \
    }                         \
  } while (false)
#endif
