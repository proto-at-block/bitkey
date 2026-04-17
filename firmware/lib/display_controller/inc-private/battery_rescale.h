#pragma once

#include <stdbool.h>
#include <stdint.h>

// Battery display rescaling state
typedef struct {
  uint8_t rescale_base_soc;  // Real SOC when we show 100% (typically 98-100)
  bool rescaling_active;     // Whether rescaling is active
  bool charging_complete;    // Charging finished but still plugged
} battery_rescale_state_t;

void battery_rescale_init(battery_rescale_state_t* state);
void battery_rescale_on_charging_started(battery_rescale_state_t* state);
void battery_rescale_on_unplugged(battery_rescale_state_t* state, uint8_t raw_soc);
uint8_t battery_rescale_on_charging_complete(battery_rescale_state_t* state, uint8_t raw_soc);
uint8_t battery_rescale_get_display_percent(const battery_rescale_state_t* state, uint8_t raw_soc);
