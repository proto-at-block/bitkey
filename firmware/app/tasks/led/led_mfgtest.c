#include "animation.h"
#include "attributes.h"
#include "exti.h"
#include "ipc.h"
#include "led_task.h"
#include "power.h"
#include "rtos.h"
#include "sysevent.h"

extern power_config_t power_config;

void led_mfgtest_thread(void* UNUSED(args)) {
  sysevent_wait(SYSEVENT_POWER_READY, true);

  for (;;) {
    // Cap touch graze LED animation.
    if (exti_wait(&power_config.cap_touch_detect, RTOS_EVENT_GROUP_TIMEOUT_MAX, true)) {
      static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_MFGTEST_CAPTOUCH,
                                                        .immediate = true};
      ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
    }
  }
}

void led_mfgtest_task_create(void) {
  rtos_thread_t* led_mfgtest_thread_handle =
    rtos_thread_create(led_mfgtest_thread, NULL, RTOS_THREAD_PRIORITY_LOW, 1024);
  ASSERT(led_mfgtest_thread_handle);
}
