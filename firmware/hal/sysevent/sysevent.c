#include "sysevent.h"

#include "attributes.h"
#include "rtos_event_groups.h"

static SHARED_TASK_DATA rtos_event_group_t sys_events = {0};

void sysevent_init(void) {
  rtos_event_group_create(&sys_events);
}

void sysevent_set(const sysevent_t events) {
  rtos_event_group_set_bits(&sys_events, (uint32_t)events);
}

bool sysevent_get(const sysevent_t event) {
  uint32_t bits = rtos_event_group_get_bits(&sys_events);
  return bits & (uint32_t)event;
}

void sysevent_clear(const sysevent_t events) {
  rtos_event_group_clear_bits(&sys_events, (uint32_t)events);
}

void sysevent_wait(const sysevent_t events, const bool wait_for_all) {
  rtos_event_group_wait_bits(&sys_events, (uint32_t)events, false, wait_for_all,
                             RTOS_EVENT_GROUP_TIMEOUT_MAX);
}
