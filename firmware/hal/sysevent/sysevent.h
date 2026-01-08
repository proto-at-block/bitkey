#pragma once

#include <stdbool.h>

typedef enum {
  SYSEVENT_POWER_READY = (1 << 0),
  SYSEVENT_FILESYSTEM_READY = (1 << 1),
  SYSEVENT_SLEEP_TIMER_READY = (1 << 2),
  SYSEVENT_FEATURE_FLAGS_READY = (1 << 3),
  SYSEVENT_BREAK_GLASS_READY = (1 << 4),
  SYSEVENT_TEMP_HIGH = (1 << 5),
  SYSEVENT_TEMP_LOW = (1 << 6),
  SYSEVENT_USB_THERMAL_FAULT = (1 << 7),
} sysevent_t;

void sysevent_init(void);
void sysevent_set(const sysevent_t events);
void sysevent_set_from_isr(const sysevent_t events);
bool sysevent_get(const sysevent_t event);
void sysevent_clear(const sysevent_t events);
void sysevent_wait(const sysevent_t events, const bool wait_for_all);
