#include "power_charger_ui.h"

#include <criterion/criterion.h>

TestSuite(power_charger_ui);

Test(power_charger_ui, done_mode_waits_for_boost_settling_before_reporting_complete) {
  power_charger_ui_state_t ui_state = {0};

  const power_charger_ui_event_t event = power_charger_ui_update_state(
    true, false, POWER_CHARGER_MODE_DONE, false, true, 99000u, &ui_state);

  cr_assert_eq(event, POWER_CHARGER_UI_EVENT_CHARGING);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_CHARGING);
  cr_assert_eq(ui_state.charging_active, false);
}

Test(power_charger_ui, done_mode_reports_complete_after_boost_settles) {
  power_charger_ui_state_t ui_state = {0};

  const power_charger_ui_event_t event = power_charger_ui_update_state(
    true, false, POWER_CHARGER_MODE_DONE, true, true, 99000u, &ui_state);

  cr_assert_eq(event, POWER_CHARGER_UI_EVENT_COMPLETE);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_COMPLETE);
  cr_assert_eq(ui_state.charging_active, false);
}

Test(power_charger_ui, near_full_cv_mode_waits_for_boost_settling_before_reporting_complete) {
  power_charger_ui_state_t ui_state = {0};

  const power_charger_ui_event_t unsettled_event = power_charger_ui_update_state(
    true, false, POWER_CHARGER_MODE_CV, false, true, 99000u, &ui_state);

  cr_assert_eq(unsettled_event, POWER_CHARGER_UI_EVENT_CHARGING);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_CHARGING);

  const power_charger_ui_event_t settled_event = power_charger_ui_update_state(
    true, false, POWER_CHARGER_MODE_CV, true, true, 99000u, &ui_state);

  cr_assert_eq(settled_event, POWER_CHARGER_UI_EVENT_COMPLETE);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_COMPLETE);
}

Test(power_charger_ui, latched_complete_exits_when_done_mode_reenters_boost_settling) {
  power_charger_ui_state_t ui_state = {
    .charging_active = false,
    .idle_state = POWER_CHARGER_UI_IDLE_STATE_COMPLETE,
  };

  const power_charger_ui_event_t event = power_charger_ui_update_state(
    true, false, POWER_CHARGER_MODE_DONE, false, true, 99000u, &ui_state);

  cr_assert_eq(event, POWER_CHARGER_UI_EVENT_CHARGING);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_CHARGING);
  cr_assert_eq(ui_state.charging_active, false);
}

Test(power_charger_ui, latched_complete_stays_complete_once_boost_is_settled) {
  power_charger_ui_state_t ui_state = {
    .charging_active = false,
    .idle_state = POWER_CHARGER_UI_IDLE_STATE_COMPLETE,
  };

  const power_charger_ui_event_t event = power_charger_ui_update_state(
    true, false, POWER_CHARGER_MODE_DONE, true, true, 99000u, &ui_state);

  cr_assert_eq(event, POWER_CHARGER_UI_EVENT_NONE);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_COMPLETE);
  cr_assert_eq(ui_state.charging_active, false);
}

Test(power_charger_ui, latched_complete_stays_complete_with_invalid_soc_in_near_full_mode) {
  power_charger_ui_state_t ui_state = {
    .charging_active = false,
    .idle_state = POWER_CHARGER_UI_IDLE_STATE_COMPLETE,
  };

  const power_charger_ui_event_t event =
    power_charger_ui_update_state(true, false, POWER_CHARGER_MODE_CV, true, false, 0u, &ui_state);

  cr_assert_eq(event, POWER_CHARGER_UI_EVENT_NONE);
  cr_assert_eq(ui_state.idle_state, POWER_CHARGER_UI_IDLE_STATE_COMPLETE);
  cr_assert_eq(ui_state.charging_active, false);
}
