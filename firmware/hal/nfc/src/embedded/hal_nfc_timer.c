#include "assert.h"
#include "hal_nfc_timer_impl.h"
#include "log.h"
#include "rtos.h"

// ST-RFAL allocates many timers at once; the reference code limits it to 10,
// so we match that here. Depending on our usage, we may need to change this value.
// If so, we may need to bump the configTIMER_TASK_STACK_DEPTH and
// configTIMER_TASK_QUEUE_LENGTH values as well.
rtos_timer_t NFC_TASK_DATA timers[MAX_NFC_TIMERS] = {
  {
    .name = "nfc-0",
  },
  {
    .name = "nfc-1",
  },
  {
    .name = "nfc-2",
  },
  {
    .name = "nfc-3",
  },
  {
    .name = "nfc-4",
  },
  {
    .name = "nfc-5",
  },
  {
    .name = "nfc-6",
  },
  {
    .name = "nfc-7",
  },
  {
    .name = "nfc-8",
  },
  {
    .name = "nfc-9",
  },
};

// We return indices offset by 1000 to distinguish between callers who
// intentionally mean index '0', and those who've accidentally passed '0' in the
// case of a default initialized int.
// ST-RFAL will sometimes erroneously attempt to stop timers with index '0', so this
// is necessary.
#define TIMER_INDEX_OFFSET (1000)

uint32_t nfc_timer_create(uint32_t duration_ms) {
  for (uint32_t i = 0; i < MAX_NFC_TIMERS; i++) {
    if (!timers[i].active) {
      rtos_timer_start(&timers[i], duration_ms);
      return TIMER_INDEX_OFFSET + i;
    }
  }

  LOGE("No timers available!");
  ASSERT(false);
}

bool nfc_timer_expired(uint32_t index) {
  if (index == 0) {
    // Handle special bug in ST-RFAL, see comment above.
    return true;
  }
  index -= TIMER_INDEX_OFFSET;
  ASSERT(index <= MAX_NFC_TIMERS);
  return rtos_timer_expired(&timers[index]);
}

void nfc_timer_stop(uint32_t index) {
  if (index == 0) {
    // Handle special bug in ST-RFAL, see comment above.
    return;
  }
  index -= TIMER_INDEX_OFFSET;
  ASSERT(index <= MAX_NFC_TIMERS);
  rtos_timer_stop(&timers[index]);
}

void nfc_timer_init(rtos_timer_callback_t callback) {
  for (uint32_t i = 0; i < MAX_NFC_TIMERS; i++) {
    rtos_timer_create_static(&timers[i], callback);
  }
}

void nfc_timer_stop_all(void) {
  for (uint32_t i = 0; i < MAX_NFC_TIMERS; i++) {
    if (timers[i].active) {
      rtos_timer_stop(&timers[i]);
    }
  }
}
