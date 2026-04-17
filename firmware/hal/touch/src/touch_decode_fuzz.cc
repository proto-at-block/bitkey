/**
 * touch_decode_fuzz.cc — FT3169 touch-controller I2C decode fuzzer.
 *
 * Security finding covered:
 *   BCW-40: _touch_decode_data() in touch.c switches on
 *           data->touch[0].touch_xh.event_flag BEFORE checking that
 *           raw_points > 0.  When num_points == 0 the ft3169_touch_data_t
 *           allocation contains only the 2-byte header (gesture + num_points)
 *           with no touch-point slots.  Accessing touch[0] is then a
 *           heap-buffer-overflow detectable by ASAN.
 *
 * Approach:
 *   The vulnerable code path from _touch_decode_data() is reproduced inline.
 *   A std::vector<uint8_t> of exactly FT3169_TOUCH_DATA_SIZE(num_points) bytes
 *   is heap-allocated, so ASAN places a redzone immediately after the allocation.
 *   When the fuzzer drives num_points == 0, the switch on touch[0].event_flag
 *   reads 6 bytes past the end of the allocation and ASAN reports a
 *   heap-buffer-overflow with the reproducing fuzz input.
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "attributes.h"
#include "touch_ft3169.h"
}  // extern "C"

#include <stdint.h>
#include <string.h>
#include <vector>

/* Minimal touch_event_t stub matching the fields used in _touch_decode_data. */
typedef enum {
  TOUCH_EVENT_TOUCH_DOWN,
  TOUCH_EVENT_TOUCH_UP,
  TOUCH_EVENT_CONTACT,
} touch_event_type_t;

typedef struct {
  uint16_t x;
  uint16_t y;
} touch_coord_t;

typedef struct {
  touch_event_type_t event_type;
  touch_coord_t coord;
  uint32_t timestamp_ms;
} touch_event_t;

/* Reproduces the vulnerable switch from _touch_decode_data() in touch.c.
 *
 * BCW-40: data->touch[0].touch_xh.event_flag is accessed inside the switch
 * statement BEFORE raw_points > 0 is verified.  When num_points == 0 and
 * the backing buffer holds only sizeof(ft3169_touch_data_t) == 2 bytes,
 * accessing touch[0] (6 bytes starting at offset 2) is OOB.
 *
 * Under ASAN this raises heap-buffer-overflow on any fuzz input with
 * num_points == 0. */
static bool fuzz_decode_data(const ft3169_touch_data_t* data, touch_event_t* event) {
  const uint8_t raw_points = data->num_points & 0x0F;

  event->timestamp_ms = 0; /* stub for rtos_thread_systime() */

  /* BCW-40: Vulnerable access — touch[0] read before raw_points check. */
  switch (data->touch[0].touch_xh.event_flag) {
    case FT3169_EVENT_PRESS_DOWN:
      event->event_type = TOUCH_EVENT_TOUCH_DOWN;
      break;
    case FT3169_EVENT_CONTACT:
      event->event_type = TOUCH_EVENT_CONTACT;
      break;
    case FT3169_EVENT_LIFT_UP:
      event->event_type = TOUCH_EVENT_TOUCH_UP;
      break;
    case FT3169_EVENT_INVALID:
    case FT3169_EVENT_NO_EVENT:
    default:
      if (raw_points == 0) {
        return false;
      }
      event->event_type = TOUCH_EVENT_CONTACT;
      break;
  }

  if (raw_points > 0) {
    event->coord.x = FT3169_TOUCH_COORD_X(&data->touch[0]);
    event->coord.y = FT3169_TOUCH_COORD_Y(&data->touch[0]);
  }

  return true;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < sizeof(ft3169_touch_data_t)) {
    return 0;
  }

  FuzzedDataProvider fuzzed_data(data, size);

  /* BCW-40: Drive num_points from 0 to FT3169_MAX_TOUCH_POINTS.
   * When num_points == 0 the allocation is only 2 bytes; touch[0] is OOB. */
  uint8_t num_points =
    fuzzed_data.ConsumeIntegralInRange<uint8_t>(0, FT3169_MAX_TOUCH_POINTS);

  /* Allocate exactly enough bytes for the header + num_points touch slots.
   * ASAN places a redzone after this allocation so any OOB access is caught. */
  const size_t touch_data_size = FT3169_TOUCH_DATA_SIZE(num_points);
  std::vector<uint8_t> buf(touch_data_size, 0);

  /* Fill the buffer with fuzz bytes (capped to actual allocation size). */
  std::vector<uint8_t> fill = fuzzed_data.ConsumeBytes<uint8_t>(touch_data_size);
  memcpy(buf.data(), fill.data(), fill.size());

  /* Fix num_points to match the allocated size so the header is consistent. */
  ft3169_touch_data_t* touch_data = reinterpret_cast<ft3169_touch_data_t*>(buf.data());
  touch_data->num_points = num_points;

  touch_event_t event = {};
  (void)fuzz_decode_data(touch_data, &event);

  return 0;
}
