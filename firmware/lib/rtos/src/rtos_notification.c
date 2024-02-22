#include "rtos.h"

bool rtos_notification_wait_signal(uint32_t timeout_ms) {
  return xTaskNotifyWait(0, 0, NULL, (portTickType)MS2TICKS(timeout_ms)) == pdTRUE;
}

void rtos_notification_signal(rtos_thread_t* thread) {
  ASSERT(thread);
  xTaskNotify((TaskHandle_t)thread->handle, 0, eNoAction);
}
