#pragma once

#include "attributes.h"
#include "bitlog_platform.h"
#include "memfault/core/trace_reason_user.h"

#include <stdbool.h>
#include <stdint.h>

// Super compact logs.

typedef struct PACKED {
  uint16_t timestamp_delta;  // Time since last event
  uint16_t event;
  uint8_t status;
  uint24_t pc;
  uint24_t lr;
} bitlog_event_t;
_Static_assert(sizeof(bitlog_event_t) == 11, "unexpected bitlog size");

// Private API, don't call.

// We use Bitlogs to transfer Memfault events in a more compact format over NFC,
// so Bitlog events *are* Memfault events. This can be redefined if needed.
#ifdef UNIT_TEST
// For unit tests, don't perform any translation.
#define BITLOG_EVENT_NAME(event) event
#else
#define BITLOG_EVENT_NAME(event) MEMFAULT_TRACE_REASON(event)
#endif

void _bitlog_record_event(uint16_t event, uint8_t status, void* pc, void* lr);

// Public API.

typedef uint32_t (*bitlog_timestamp_t)(void);
typedef struct {
  bitlog_timestamp_t timestamp_cb;
} bitlog_api_t;

void bitlog_init(bitlog_api_t api);

// Record an event.
#define BITLOG_EVENT(event, status)                                 \
  ({                                                                \
    void* pc = __GET_PC();                                          \
    void* lr = __GET_LR();                                          \
    _bitlog_record_event(BITLOG_EVENT_NAME(event), status, pc, lr); \
  })

bool bitlog_most_recent_event(bitlog_event_t* event_out);

// Copy as many events as possible into an output buffer.
// Returns the remaining size of the backing ringbuf.
uint32_t bitlog_drain(uint8_t* buffer, uint32_t buffer_length, uint32_t* bytes_written);

void bitlog_print(bitlog_event_t* event);
