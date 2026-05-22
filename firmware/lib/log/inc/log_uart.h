#pragma once

#include <stdarg.h>
#include <stdint.h>

#ifdef EMBEDDED_BUILD
#include "memfault/core/platform/debug_log.h"
#endif

// Tokenized UART log framing.
//
// Wire format (decoded, before COBS):
//   [ magic 0xBF (1) | type (1) | level (1) | payload | crc16-ccitt LE (2) ]
// COBS-encoded with a trailing 0x00 frame delimiter.
//
// Types:
//   0x01 compact log: payload is a CBOR array of [log_id, args...] as produced
//                     by Memfault's compact log serializer. Decoded host-side
//                     against the firmware ELF's `log_fmt` section.
//   0x02 raw text:    payload is UTF-8 bytes (no NUL).

#define LOG_UART_MAGIC         0xBFu
#define LOG_UART_TYPE_COMPACT  0x01u
#define LOG_UART_TYPE_RAW      0x02u
#define LOG_UART_TYPE_BUILD_ID 0x10u

#ifdef EMBEDDED_BUILD

// Emit a tokenized compact log frame on the debug UART.
//
// Mirrors the `memfault_compact_log_save` signature so the same arguments
// produced by the MEMFAULT_COMPACT_LOG_SAVE macro can be forwarded directly.
void log_uart_emit_compact(eMemfaultPlatformLogLevel level, uint32_t log_id,
                           uint32_t compressed_fmt, ...);

// Emit a raw (un-tokenized) text log frame on the debug UART. Used by callers
// that opt out of tokenization (panic, bootloader, mfgtest, early boot).
void log_uart_emit_raw(eMemfaultPlatformLogLevel level, const char* file, int line,
                       const char* format, ...);

// Emit the GNU build ID banner so the host decoder can confirm the connected
// firmware matches the user-supplied ELF. Should be called once at boot, after
// `serial_init()` and before any LOGI(). Frame payload is the 20-byte build ID.
// The level byte is reserved on this frame type — currently set to Info purely
// for cosmetic consistency. Host decoders should not interpret it.
void log_uart_emit_build_id(void);

#endif  // EMBEDDED_BUILD
