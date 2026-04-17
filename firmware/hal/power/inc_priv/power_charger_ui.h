#pragma once

#include "power.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  POWER_CHARGER_UI_IDLE_STATE_NONE = 0,
  POWER_CHARGER_UI_IDLE_STATE_CHARGING,
  POWER_CHARGER_UI_IDLE_STATE_COMPLETE,
} power_charger_ui_idle_state_t;

typedef struct {
  bool charging_active;
  power_charger_ui_idle_state_t idle_state;
} power_charger_ui_state_t;

typedef enum {
  POWER_CHARGER_UI_EVENT_NONE = 0,
  POWER_CHARGER_UI_EVENT_CHARGING,
  POWER_CHARGER_UI_EVENT_COMPLETE,
  POWER_CHARGER_UI_EVENT_UNPLUGGED,
} power_charger_ui_event_t;

bool power_charger_ui_is_complete(bool plugged, bool charging, power_charger_mode_t mode,
                                  bool allow_ui_complete, bool soc_valid,
                                  uint32_t soc_millipercent);
power_charger_ui_event_t power_charger_ui_update_state(bool plugged, bool charging,
                                                       power_charger_mode_t mode,
                                                       bool allow_ui_complete, bool soc_valid,
                                                       uint32_t soc_millipercent,
                                                       power_charger_ui_state_t* ui_state);
