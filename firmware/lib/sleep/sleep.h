#pragma once

#include <stdint.h>

#define DEFAULT_POWER_TIMEOUT_MS (60000)

typedef void (*sleep_timer_callback_t)(void*);

void sleep_init(sleep_timer_callback_t callback);
void sleep_set_power_timeout(uint32_t timeout);
uint32_t sleep_get_power_timeout(void);
void sleep_refresh_power_timer(void);
