#pragma once

#include "exti.h"
#include "mcu_gpio.h"

#include <stdbool.h>
#include <stdint.h>

// Alias button_config_t to exti_config_t
typedef exti_config_t button_config_t;

typedef enum {
  HAL_BUTTON_LEFT = 0,
  HAL_BUTTON_RIGHT = 1,
  HAL_BUTTON_BOTH = 2,
  // Note: HAL_BUTTON_COUNT is the number of physical buttons, not including BOTH
  HAL_BUTTON_COUNT = 2,  // Only LEFT and RIGHT are physical buttons
} hal_button_id_t;

typedef enum {
  BUTTON_EVENT_SHORT_PRESS,
  BUTTON_EVENT_LONG_PRESS_START,
  BUTTON_EVENT_LONG_PRESS_STOP,
} button_event_type_t;

typedef struct {
  hal_button_id_t button;
  button_event_type_t type;
  uint32_t timestamp_ms;
  uint32_t duration_ms;
} button_event_t;

void button_init(void);
bool button_update_state(void);
bool button_get_event(button_event_t* event);
