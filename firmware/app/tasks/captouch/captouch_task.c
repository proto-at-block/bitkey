#include "captouch_task.h"

#include "exti.h"
#include "mcu.h"
#include "power.h"
#include "rtos.h"
#include "rtos_thread.h"
#include "sysevent.h"

#include <string.h>

extern power_config_t power_config;

#define LONG_PRESS_THRESHOLD_MS (10000)

void captouch_thread(void* UNUSED(args)) {
  uint32_t start_time = 0;
  uint32_t end_time = 0;
  uint32_t duration = 0;

  sysevent_wait(SYSEVENT_POWER_READY, true);
  exti_clear(&power_config.cap_touch_detect);

  for (;;) {
    // This triggers on both rising and falling edges.
    if (exti_wait(&power_config.cap_touch_detect, RTOS_EVENT_GROUP_TIMEOUT_MAX, true)) {
      if (start_time == 0) {
        // Finger down.
        start_time = rtos_thread_systime();
      } else {
        // Finger up.
        end_time = rtos_thread_systime();
        duration = end_time - start_time;
        start_time = 0;

        if (duration > LONG_PRESS_THRESHOLD_MS) {
          sysevent_set(SYSEVENT_BREAK_GLASS_READY);
        }
      }
    }
  }
}

void captouch_task_create(void) {
  rtos_thread_t* captouch_thread_handle =
    rtos_thread_create(captouch_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 512);
  ASSERT(captouch_thread_handle);
}
