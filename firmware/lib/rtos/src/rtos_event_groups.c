#include "assert.h"
#include "rtos.h"

void rtos_event_group_create(rtos_event_group_t* event_group) {
  event_group->handle = xEventGroupCreateStatic(&event_group->buffer);
  ASSERT(event_group->handle != NULL);
}

uint32_t rtos_event_group_get_bits(rtos_event_group_t* event_group) {
  ASSERT(event_group->handle != NULL);
  return (uint32_t)xEventGroupGetBits((EventGroupHandle_t)event_group->handle);
}

uint32_t rtos_event_group_set_bits(rtos_event_group_t* event_group, const uint32_t bits) {
  ASSERT(event_group->handle != NULL);

  return (uint32_t)xEventGroupSetBits((EventGroupHandle_t)event_group->handle,
                                      (const EventBits_t)bits);
}

bool rtos_event_group_set_bits_from_isr(rtos_event_group_t* event_group, const uint32_t bits,
                                        bool* wokenp) {
  ASSERT(event_group->handle != NULL);
  ASSERT(wokenp != NULL);

  portBASE_TYPE xHigherPriorityTaskWoken = pdFALSE;
  portBASE_TYPE result = xEventGroupSetBitsFromISR(
    (EventGroupHandle_t)event_group->handle, (const EventBits_t)bits, &xHigherPriorityTaskWoken);
  *wokenp = *wokenp || xHigherPriorityTaskWoken == pdTRUE;

  return result == pdTRUE;
}

uint32_t rtos_event_group_clear_bits(rtos_event_group_t* event_group, const uint32_t bits) {
  ASSERT(event_group->handle != NULL);

  return (uint32_t)xEventGroupClearBits((EventGroupHandle_t)event_group->handle,
                                        (const EventBits_t)bits);
}

uint32_t rtos_event_group_wait_bits(rtos_event_group_t* event_group, const uint32_t bits,
                                    const bool clear_on_exit, const bool wait_for_all_bits,
                                    uint32_t timeout_ms) {
  ASSERT(event_group->handle != NULL);

  portTickType timeout_ticks;
  if (timeout_ms == RTOS_EVENT_GROUP_TIMEOUT_MAX) {
    timeout_ticks = portMAX_DELAY;
  } else {
    timeout_ticks = MS2TICKS(timeout_ms);
  }

  return (uint32_t)xEventGroupWaitBits((EventGroupHandle_t)event_group->handle,
                                       (const EventBits_t)bits, clear_on_exit, wait_for_all_bits,
                                       timeout_ticks);
}
