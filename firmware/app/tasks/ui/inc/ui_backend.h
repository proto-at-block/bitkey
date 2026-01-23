#pragma once

#include "ui_events.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct {
  // Initialize the backend
  void (*init)(void);

  // Handle UI events
  void (*show_event)(ui_event_type_t event);
  void (*show_event_with_data)(ui_event_type_t event, const uint8_t* data, uint32_t len);
  void (*set_idle_state)(ui_event_type_t idle_state);
  void (*clear)(void);

  // Handle display actions from UXC (w3 only)
  void (*handle_display_action)(uint32_t action, uint32_t data);

  // Run periodic updates (e.g., animations)
  void (*run)(void);
} ui_backend_ops_t;

const ui_backend_ops_t* ui_backend_get(void);
