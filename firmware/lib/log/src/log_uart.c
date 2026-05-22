#include "log_uart.h"

#ifdef EMBEDDED_BUILD

#include "attributes.h"
#include "cobs.h"
#include "mcu_usart.h"
#include "memfault/core/build_info.h"
#include "memfault/core/compact_log_serializer.h"
#include "memfault/util/cbor.h"
#include "memfault/util/crc16_ccitt.h"
#include "printf.h"
#include "rtos.h"
#include "serial.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

extern serial_config_t serial_config;

// Total framing buffer. nanocobs guarantees in-place encoding succeeds for any
// payload up to COBS_TINYFRAME_SAFE_BUFFER_SIZE (256). The encoded layout
// reserves byte 0 (becomes COBS code byte) and byte len-1 (becomes 0x00 frame
// delimiter), leaving 254 bytes for the raw frame:
//   [magic 0xBF | type | level | payload | crc16-le].
//
// We allocate one extra leading byte before the COBS frame and keep it set to
// 0x00 — that's the inter-frame delimiter the host parser needs even when
// other UART writers (shell prompt, _putchar) emit between two log frames.
// Putting it in the same buffer means we ship leading-byte + frame in a single
// `mcu_usart_write()` so no other writer can interleave between them.
//
// Both emit functions encode in place into this single buffer to keep the
// per-call stack frame small (~257 B), critical because LOG* runs in caller
// context and many RTOS tasks have stacks in the 512–1024 B range.
#define LOG_UART_FRAME_BUFFER_SIZE COBS_TINYFRAME_SAFE_BUFFER_SIZE    // COBS frame proper
#define LOG_UART_BUFFER_SIZE       (LOG_UART_FRAME_BUFFER_SIZE + 1u)  // +1 leading 0x00
#define LOG_UART_LEADING_OFFSET    0u
#define LOG_UART_FRAME_OFFSET      1u  // start of COBS frame
#define LOG_UART_PAYLOAD_OFFSET \
  (LOG_UART_FRAME_OFFSET + 4u)   // leading + sentinel + magic + type + level
#define LOG_UART_HEADER_SIZE 3u  // magic + type + level
#define LOG_UART_CRC_SIZE    2u
#define LOG_UART_MAX_PAYLOAD \
  (LOG_UART_FRAME_BUFFER_SIZE - 2u /* COBS sentinels */ - LOG_UART_HEADER_SIZE - LOG_UART_CRC_SIZE)

_Static_assert(LOG_UART_FRAME_BUFFER_SIZE == 256,
               "tinyframe buffer must be 256 to use safe encode");

typedef struct {
  uint8_t* buf;     // points at the first byte of the payload region
  size_t cursor;    // bytes written so far
  size_t capacity;  // bytes available
  bool overflow;
} payload_writer_t;

static void payload_write_cb(void* ctx, uint32_t offset, const void* data, size_t data_len) {
  (void)offset;
  payload_writer_t* w = (payload_writer_t*)ctx;
  if (w->overflow || data_len > (w->capacity - w->cursor)) {
    w->overflow = true;
    return;
  }
  memcpy(&w->buf[w->cursor], data, data_len);
  w->cursor += data_len;
}

// Finishes the frame: header bytes are already in place, payload occupies
// buf[LOG_UART_PAYLOAD_OFFSET..LOG_UART_PAYLOAD_OFFSET + payload_len). Appends
// CRC, COBS-encodes in place, and writes to UART.
//
// Marked SYSCALL because RTOS_THREAD_WITH_PRIVILEGE invokes portRAISE_PRIVILEGE,
// which only succeeds when the caller resides in the freertos_system_calls
// linker section under FreeRTOS-MPU.
SYSCALL NO_OPTIMIZE static void log_uart_finish(uint8_t* buf, size_t payload_len) {
  if (payload_len > LOG_UART_MAX_PAYLOAD) {
    return;
  }

  const size_t crc_off = LOG_UART_PAYLOAD_OFFSET + payload_len;
  // Length of the COBS frame proper (sentinel + header + payload + crc + trailing sentinel).
  const size_t frame_len = LOG_UART_HEADER_SIZE + payload_len + LOG_UART_CRC_SIZE + 2u;
  // Total bytes to put on the wire: leading 0x00 + COBS frame.
  const size_t tx_len = LOG_UART_FRAME_OFFSET + frame_len;

  // CRC covers magic + type + level + payload, which start one byte past the
  // COBS sentinel slot inside the frame region.
  const uint16_t crc = memfault_crc16_ccitt_compute(MEMFAULT_CRC16_CCITT_INITIAL_VALUE,
                                                    &buf[LOG_UART_FRAME_OFFSET + 1u],
                                                    LOG_UART_HEADER_SIZE + payload_len);
  buf[crc_off] = (uint8_t)(crc & 0xFFu);
  buf[crc_off + 1u] = (uint8_t)((crc >> 8) & 0xFFu);
  buf[tx_len - 1u] = COBS_TINYFRAME_SENTINEL_VALUE;

  if (cobs_encode_tinyframe(&buf[LOG_UART_FRAME_OFFSET], frame_len) != COBS_RET_SUCCESS) {
    return;
  }

  // Single atomic write: leading 0x00 (already in buf[0] from init_header) +
  // COBS-encoded frame. Combining them into one mcu_usart_write() guarantees
  // no other UART writer (shell prompt, _putchar, panic puts) can interleave
  // bytes between the leading delimiter and the frame, which would otherwise
  // break the host decoder's "one delimiter + one COBS frame" contract.
  //
  // mcu_usart_tx_write truncates `len` to the TX ring's available space and
  // returns the actual byte count, so under bursty logging it can enqueue
  // only a prefix of the frame — leaving the host parser straddling a
  // partial COBS frame. On short write, push a best-effort 0x00 resync hint
  // so the host realigns at the next frame boundary instead of trying to
  // decode the truncated bytes.
  RTOS_THREAD_WITH_PRIVILEGE({
    const uint32_t wrote = mcu_usart_write(&serial_config.usart, buf, (uint32_t)tx_len);
    if (wrote != (uint32_t)tx_len) {
      const uint8_t resync = 0x00u;
      (void)mcu_usart_write(&serial_config.usart, &resync, 1u);
    }
  });
}

static inline void log_uart_init_header(uint8_t* buf, uint8_t type,
                                        eMemfaultPlatformLogLevel level) {
  buf[LOG_UART_LEADING_OFFSET] = 0x00u;                        // permanent leading delimiter
  buf[LOG_UART_FRAME_OFFSET] = COBS_TINYFRAME_SENTINEL_VALUE;  // COBS code-byte slot
  buf[LOG_UART_FRAME_OFFSET + 1u] = LOG_UART_MAGIC;
  buf[LOG_UART_FRAME_OFFSET + 2u] = type;
  buf[LOG_UART_FRAME_OFFSET + 3u] = (uint8_t)level;
}

SYSCALL NO_OPTIMIZE void log_uart_emit_compact(eMemfaultPlatformLogLevel level, uint32_t log_id,
                                               uint32_t compressed_fmt, ...) {
  uint8_t buf[LOG_UART_BUFFER_SIZE];
  log_uart_init_header(buf, LOG_UART_TYPE_COMPACT, level);

  payload_writer_t writer = {
    .buf = &buf[LOG_UART_PAYLOAD_OFFSET],
    .cursor = 0u,
    .capacity = LOG_UART_MAX_PAYLOAD,
    .overflow = false,
  };

  sMemfaultCborEncoder encoder;
  memfault_cbor_encoder_init(&encoder, payload_write_cb, &writer, writer.capacity);

  va_list args;
  va_start(args, compressed_fmt);
  const bool ok = memfault_vlog_compact_serialize(&encoder, log_id, compressed_fmt, args);
  va_end(args);

  (void)memfault_cbor_encoder_deinit(&encoder);
  if (!ok || writer.overflow) {
    return;
  }

  log_uart_finish(buf, writer.cursor);
}

// Called from `main()` before `vTaskStartScheduler()`. The SYSCALL section
// attribute and `RTOS_THREAD_WITH_PRIVILEGE` block (inside log_uart_finish)
// are pre-scheduler-safe: `rtos_thread_is_privileged()` returns true when no
// task is running yet (the CPU is in handler/privileged mode pre-scheduler),
// so the privilege transition is a no-op and the inner write executes
// directly. This was verified at boot — the banner frame appears on UART
// immediately after `serial_init()`.
SYSCALL NO_OPTIMIZE void log_uart_emit_build_id(void) {
  sMemfaultBuildInfo info;
  if (!memfault_build_info_read(&info)) {
    return;
  }

  uint8_t buf[LOG_UART_BUFFER_SIZE];
  log_uart_init_header(buf, LOG_UART_TYPE_BUILD_ID, kMemfaultPlatformLogLevel_Info);

  _Static_assert(MEMFAULT_BUILD_ID_LEN <= LOG_UART_MAX_PAYLOAD,
                 "build id must fit in one frame payload");
  memcpy(&buf[LOG_UART_PAYLOAD_OFFSET], info.build_id, MEMFAULT_BUILD_ID_LEN);

  log_uart_finish(buf, MEMFAULT_BUILD_ID_LEN);
}

SYSCALL NO_OPTIMIZE void log_uart_emit_raw(eMemfaultPlatformLogLevel level, const char* file,
                                           int line, const char* format, ...) {
  uint8_t buf[LOG_UART_BUFFER_SIZE];
  log_uart_init_header(buf, LOG_UART_TYPE_RAW, level);

  char* text = (char*)&buf[LOG_UART_PAYLOAD_OFFSET];
  const size_t cap = LOG_UART_MAX_PAYLOAD;

  // Decorate with file:line so raw frames stay self-describing on the wire.
  int prefix_len = snprintf(text, cap, "(%s:%d) ", file, line);
  if (prefix_len < 0) {
    return;
  }
  if ((size_t)prefix_len >= cap) {
    prefix_len = (int)cap;
  }

  int body_len = 0;
  if ((size_t)prefix_len < cap) {
    va_list args;
    va_start(args, format);
    body_len = vsnprintf(&text[prefix_len], cap - (size_t)prefix_len, format, args);
    va_end(args);
    if (body_len < 0) {
      body_len = 0;
    }
  }

  // (v)snprintf returns the would-be length excluding the NUL; on truncation
  // the buffer holds (cap - 1) chars + a NUL at text[cap - 1]. Clamp to cap-1
  // so that NUL byte never goes out on the wire.
  size_t total = (size_t)prefix_len + (size_t)body_len;
  if (total >= cap) {
    total = cap - 1u;
  }

  log_uart_finish(buf, total);
}

#endif  // EMBEDDED_BUILD
