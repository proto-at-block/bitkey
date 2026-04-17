#include "battery_rescale.h"

#include <criterion/criterion.h>

#include <string.h>

bool rtos_in_isr(void) {
  return false;
}

// Test suite for battery_rescale_init
TestSuite(battery_rescale_init);

Test(battery_rescale_init, initializes_state_to_zero) {
  battery_rescale_state_t state;
  memset(&state, 0xFF, sizeof(state));  // Fill with garbage

  battery_rescale_init(&state);

  cr_assert_eq(state.rescale_base_soc, 0);
  cr_assert_eq(state.rescaling_active, false);
  cr_assert_eq(state.charging_complete, false);
}

// Test suite for battery_rescale_on_charging_started
TestSuite(battery_rescale_on_charging_started);

Test(battery_rescale_on_charging_started, clears_charging_complete_flag) {
  battery_rescale_state_t state = {
    .rescale_base_soc = 98,
    .rescaling_active = true,
    .charging_complete = true,
  };

  battery_rescale_on_charging_started(&state);

  cr_assert_eq(state.charging_complete, false);
  cr_assert_eq(state.rescaling_active, true);  // Should not change
  cr_assert_eq(state.rescale_base_soc, 98);    // Should not change
}

// Test suite for battery_rescale_on_charging_complete
TestSuite(battery_rescale_on_charging_complete);

Test(battery_rescale_on_charging_complete, activates_rescaling_at_98_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  uint8_t display_percent = battery_rescale_on_charging_complete(&state, 98);

  cr_assert_eq(state.rescaling_active, true);
  cr_assert_eq(state.rescale_base_soc, 98);
  cr_assert_eq(state.charging_complete, true);
  cr_assert_eq(display_percent, 100);  // Should show 100%
}

Test(battery_rescale_on_charging_complete, activates_rescaling_at_99_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  uint8_t display_percent = battery_rescale_on_charging_complete(&state, 99);

  cr_assert_eq(state.rescaling_active, true);
  cr_assert_eq(state.rescale_base_soc, 99);
  cr_assert_eq(display_percent, 100);
}

Test(battery_rescale_on_charging_complete, activates_rescaling_at_100_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  uint8_t display_percent = battery_rescale_on_charging_complete(&state, 100);

  cr_assert_eq(state.rescaling_active, true);
  cr_assert_eq(state.rescale_base_soc, 100);
  cr_assert_eq(display_percent, 100);
}

Test(battery_rescale_on_charging_complete, does_not_activate_below_95_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  uint8_t display_percent = battery_rescale_on_charging_complete(&state, 94);

  cr_assert_eq(state.rescaling_active, false);
  cr_assert_eq(state.charging_complete, true);
  cr_assert_eq(display_percent, 100);  // Still shows 100% because charging_complete
}

Test(battery_rescale_on_charging_complete, updates_baseline_when_already_active) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  // First activation
  battery_rescale_on_charging_complete(&state, 98);
  cr_assert_eq(state.rescale_base_soc, 98);

  // Second activation with different SOC
  battery_rescale_on_charging_complete(&state, 97);
  cr_assert_eq(state.rescale_base_soc, 97);
  cr_assert_eq(state.rescaling_active, true);
}

// Test suite for battery_rescale_on_unplugged
TestSuite(battery_rescale_on_unplugged);

Test(battery_rescale_on_unplugged, clears_charging_complete_flag) {
  battery_rescale_state_t state = {
    .rescale_base_soc = 98,
    .rescaling_active = true,
    .charging_complete = true,
  };

  battery_rescale_on_unplugged(&state, 98);

  cr_assert_eq(state.charging_complete, false);
}

Test(battery_rescale_on_unplugged, updates_baseline_when_rescaling_active) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);
  battery_rescale_on_charging_complete(&state, 99);

  // Unplug at 97% (battery dropped while still plugged)
  battery_rescale_on_unplugged(&state, 97);

  cr_assert_eq(state.rescale_base_soc, 97);
  cr_assert_eq(state.rescaling_active, true);
}

Test(battery_rescale_on_unplugged, does_not_update_baseline_when_not_active) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  battery_rescale_on_unplugged(&state, 97);

  cr_assert_eq(state.rescale_base_soc, 0);  // Should remain uninitialized
  cr_assert_eq(state.rescaling_active, false);
}

Test(battery_rescale_on_unplugged, does_not_update_baseline_below_95_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);
  battery_rescale_on_charging_complete(&state, 98);

  battery_rescale_on_unplugged(&state, 94);

  cr_assert_eq(state.rescale_base_soc, 98);  // Should not update
}

// Test suite for battery_rescale_get_display_percent
TestSuite(battery_rescale_get_display_percent);

Test(battery_rescale_get_display_percent, returns_100_when_charging_complete) {
  battery_rescale_state_t state = {
    .rescale_base_soc = 98,
    .rescaling_active = true,
    .charging_complete = true,
  };

  cr_assert_eq(battery_rescale_get_display_percent(&state, 98), 100);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 50), 100);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 0), 100);
}

Test(battery_rescale_get_display_percent, returns_raw_when_not_active) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  cr_assert_eq(battery_rescale_get_display_percent(&state, 100), 100);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 75), 75);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 50), 50);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 0), 0);
}

Test(battery_rescale_get_display_percent, rescales_correctly_from_98_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);
  battery_rescale_on_charging_complete(&state, 98);
  state.charging_complete = false;  // Simulate unplug

  // 98% raw → 100% display
  cr_assert_eq(battery_rescale_get_display_percent(&state, 98), 100);

  // 49% raw → 50% display (49 * 100 / 98 = 50)
  cr_assert_eq(battery_rescale_get_display_percent(&state, 49), 50);

  // 0% raw → 0% display
  cr_assert_eq(battery_rescale_get_display_percent(&state, 0), 0);
}

Test(battery_rescale_get_display_percent, rescales_correctly_from_99_percent) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);
  battery_rescale_on_charging_complete(&state, 99);
  state.charging_complete = false;

  // 99% raw → 100% display
  cr_assert_eq(battery_rescale_get_display_percent(&state, 99), 100);

  // 50% raw → 50% display (50 * 100 / 99 = 50)
  cr_assert_eq(battery_rescale_get_display_percent(&state, 50), 50);
}

Test(battery_rescale_get_display_percent, clamps_to_100_when_raw_exceeds_base) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);
  battery_rescale_on_charging_complete(&state, 98);
  state.charging_complete = false;

  // If raw SOC somehow increases above baseline
  cr_assert_eq(battery_rescale_get_display_percent(&state, 99), 100);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 100), 100);
}

Test(battery_rescale_get_display_percent, handles_zero_baseline_defensively) {
  battery_rescale_state_t state = {
    .rescale_base_soc = 0,  // State corruption: active but zero baseline
    .rescaling_active = true,
    .charging_complete = false,
  };

  // Should return raw SOC instead of dividing by zero
  cr_assert_eq(battery_rescale_get_display_percent(&state, 75), 75);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 50), 50);
  cr_assert_eq(battery_rescale_get_display_percent(&state, 0), 0);
}

// Integration test: full charge/unplug/discharge cycle
TestSuite(battery_rescale_integration);

Test(battery_rescale_integration, full_charge_unplug_discharge_cycle) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  // Start charging
  battery_rescale_on_charging_started(&state);
  cr_assert_eq(state.charging_complete, false);

  // Charging complete at 98%
  uint8_t display = battery_rescale_on_charging_complete(&state, 98);
  cr_assert_eq(display, 100);
  cr_assert_eq(state.rescaling_active, true);
  cr_assert_eq(state.charging_complete, true);

  // Unplug
  battery_rescale_on_unplugged(&state, 98);
  cr_assert_eq(state.charging_complete, false);
  cr_assert_eq(state.rescaling_active, true);

  // Discharge: 98% raw should show as 100%
  display = battery_rescale_get_display_percent(&state, 98);
  cr_assert_eq(display, 100);

  // Discharge: 49% raw should show as ~50%
  display = battery_rescale_get_display_percent(&state, 49);
  cr_assert_eq(display, 50);

  // Discharge: 0% raw should show as 0%
  display = battery_rescale_get_display_percent(&state, 0);
  cr_assert_eq(display, 0);
}

Test(battery_rescale_integration, handles_soc_drop_while_plugged) {
  battery_rescale_state_t state;
  battery_rescale_init(&state);

  // Charging complete at 99%
  battery_rescale_on_charging_complete(&state, 99);
  cr_assert_eq(state.rescale_base_soc, 99);

  // SOC drops to 97% while still plugged (maintenance charging)
  // Unplug should update baseline
  battery_rescale_on_unplugged(&state, 97);
  cr_assert_eq(state.rescale_base_soc, 97);

  // Now 97% raw should display as 100%
  uint8_t display = battery_rescale_get_display_percent(&state, 97);
  cr_assert_eq(display, 100);
}
