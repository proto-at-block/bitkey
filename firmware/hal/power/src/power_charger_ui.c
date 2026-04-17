#include "power_charger_ui.h"

static const uint32_t CHARGER_UI_NEAR_FULL_SOC_MILLIPERCENT = 98500u;  // 98.500%
static const uint32_t CHARGER_UI_COMPLETE_EXIT_MILLIPERCENT = 95000u;  // 95.000%

static bool charger_mode_is_done(power_charger_mode_t mode) {
  return (mode == POWER_CHARGER_MODE_DONE) || (mode == POWER_CHARGER_MODE_JEITA_DONE);
}

static bool charger_mode_allows_ui_complete_fallback(power_charger_mode_t mode) {
  return (mode == POWER_CHARGER_MODE_CV) || (mode == POWER_CHARGER_MODE_JEITA_CV) ||
         (mode == POWER_CHARGER_MODE_TOP_OFF) || (mode == POWER_CHARGER_MODE_JEITA_TOP_OFF);
}

bool power_charger_ui_is_complete(bool plugged, bool charging, power_charger_mode_t mode,
                                  bool allow_ui_complete, bool soc_valid,
                                  uint32_t soc_millipercent) {
  const bool charger_idle = plugged && !charging;
  const bool soc_below_complete_exit =
    soc_valid && soc_millipercent < CHARGER_UI_COMPLETE_EXIT_MILLIPERCENT;

  if (allow_ui_complete && charger_mode_is_done(mode) && !soc_below_complete_exit) {
    return true;
  }

  // Some near-full packs settle just below 100.000% and never transition the
  // charger into literal DONE. Once plug/replug boost handling has settled, if
  // the charger is otherwise idle in a real near-full phase and SOC is above
  // the near-full threshold, treat it as complete so the lock screen does not
  // appear stuck at a high percentage.
  return allow_ui_complete && charger_idle && charger_mode_allows_ui_complete_fallback(mode) &&
         soc_valid && soc_millipercent >= CHARGER_UI_NEAR_FULL_SOC_MILLIPERCENT;
}

power_charger_ui_event_t power_charger_ui_update_state(bool plugged, bool charging,
                                                       power_charger_mode_t mode,
                                                       bool allow_ui_complete, bool soc_valid,
                                                       uint32_t soc_millipercent,
                                                       power_charger_ui_state_t* ui_state) {
  const bool charger_idle = plugged && !charging;
  bool charger_ui_complete = power_charger_ui_is_complete(
    plugged, charging, mode, allow_ui_complete, soc_valid, soc_millipercent);

  // Hysteresis: once COMPLETE, stay COMPLETE while plugged unless SOC drops
  // below the exit threshold, the charger leaves a plausible full/near-full
  // mode, or boost settling temporarily makes UI completion unsafe again.
  // Invalid SOC does not force an exit.
  if (ui_state->idle_state == POWER_CHARGER_UI_IDLE_STATE_COMPLETE && plugged) {
    const bool soc_below_exit =
      soc_valid && soc_millipercent < CHARGER_UI_COMPLETE_EXIT_MILLIPERCENT;
    const bool charger_mode_compatible =
      allow_ui_complete &&
      (charger_mode_is_done(mode) || charger_mode_allows_ui_complete_fallback(mode));
    if (!soc_below_exit && charger_mode_compatible) {
      return POWER_CHARGER_UI_EVENT_NONE;
    }

    ui_state->idle_state = POWER_CHARGER_UI_IDLE_STATE_NONE;
    ui_state->charging_active = false;
    charger_ui_complete = false;
  }

  if (plugged) {
    if (charging) {
      if (!ui_state->charging_active) {
        ui_state->charging_active = true;
        ui_state->idle_state = POWER_CHARGER_UI_IDLE_STATE_NONE;
        return POWER_CHARGER_UI_EVENT_CHARGING;
      }
    } else if (charger_idle) {
      const power_charger_ui_idle_state_t next_idle_state =
        charger_ui_complete ? POWER_CHARGER_UI_IDLE_STATE_COMPLETE
                            : POWER_CHARGER_UI_IDLE_STATE_CHARGING;
      const power_charger_ui_event_t idle_event =
        charger_ui_complete ? POWER_CHARGER_UI_EVENT_COMPLETE : POWER_CHARGER_UI_EVENT_CHARGING;

      ui_state->charging_active = false;
      if (ui_state->idle_state != next_idle_state) {
        ui_state->idle_state = next_idle_state;
        return idle_event;
      }
    }
  } else if (ui_state->charging_active ||
             ui_state->idle_state != POWER_CHARGER_UI_IDLE_STATE_NONE) {
    ui_state->charging_active = false;
    ui_state->idle_state = POWER_CHARGER_UI_IDLE_STATE_NONE;
    return POWER_CHARGER_UI_EVENT_UNPLUGGED;
  }

  return POWER_CHARGER_UI_EVENT_NONE;
}
