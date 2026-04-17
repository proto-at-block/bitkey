#include "battery_rescale.h"

#include "log.h"

// Battery display rescaling: During maintenance charging, the fuel gauge may report
// less than 100% SOC (typically 98-99%). When "charging complete" is shown, we display
// 100% to match user expectations. To avoid sudden jumps when unplugging (showing 100%
// then 98%), we rescale all subsequent percentages: displayed = (raw × 100) / base_soc.
// This gives a smooth 100% → 0% discharge curve. Persists until device reboot.

#define BATTERY_SOC_MIN_FOR_RESCALE 95  // Minimum SOC to establish rescaling baseline

void battery_rescale_init(battery_rescale_state_t* state) {
  state->rescale_base_soc = 0;
  state->rescaling_active = false;
  state->charging_complete = false;
}

void battery_rescale_on_charging_started(battery_rescale_state_t* state) {
  state->charging_complete = false;
}

void battery_rescale_on_unplugged(battery_rescale_state_t* state, uint8_t raw_soc) {
  state->charging_complete = false;
  // If we completed charging and are now unplugging, update baseline to current SOC
  // to avoid jumps (e.g., completed at 100%, dropped to 97% while plugged, unplug → show 100%)
  if (state->rescaling_active && raw_soc >= BATTERY_SOC_MIN_FOR_RESCALE && raw_soc <= 100) {
    state->rescale_base_soc = raw_soc;
  }
}

uint8_t battery_rescale_on_charging_complete(battery_rescale_state_t* state, uint8_t raw_soc) {
  state->charging_complete = true;
  if (raw_soc >= BATTERY_SOC_MIN_FOR_RESCALE && raw_soc <= 100) {
    state->rescale_base_soc = raw_soc;
    state->rescaling_active = true;
  }
  return battery_rescale_get_display_percent(state, raw_soc);
}

uint8_t battery_rescale_get_display_percent(const battery_rescale_state_t* state, uint8_t raw_soc) {
  // Charging complete - always show 100%
  if (state->charging_complete) {
    return 100;
  }

  // Apply rescaling if active (works both plugged and unplugged)
  if (state->rescaling_active) {
    if (state->rescale_base_soc == 0) {
      return raw_soc;
    }

    // Rescale: map [0, base] → [0, 100]
    // Using uint16_t to prevent overflow: max is 100*100=10000, fits in uint16_t (65535)
    uint16_t scaled = ((uint16_t)raw_soc * 100) / state->rescale_base_soc;

    // Clamp to 100 max (in case of rounding or SOC increase)
    if (scaled > 100) {
      return 100;
    }
    return (uint8_t)scaled;
  }

  // No rescaling active, return raw percentage
  return raw_soc;
}
