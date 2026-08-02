/**
 * @file ui_backend_headless.c
 * @brief Minimal UI backend for headless core-sim testing
 *
 * Drains ui_port and forwards events to display_controller.
 * Provides automatic ticking for display state machine.
 */

#include "display_controller.h"
#include "rtos.h"
#include "ui_backend.h"

static uint32_t last_tick;

static void headless_init(void) {}

static void headless_show_event(ui_event_type_t event) {
  display_controller_handle_ui_event(event, NULL, 0);
}

static void headless_show_event_with_data(ui_event_type_t event, const uint8_t* data,
                                          uint32_t len) {
  display_controller_handle_ui_event(event, data, len);
}

static void headless_set_idle_state(ui_event_type_t idle_state) {
  (void)idle_state;
}

static void headless_clear(void) {}

static void headless_run(void) {
  display_controller_tick();
  rtos_thread_sleep_until(&last_tick, 20);
}

static const ui_backend_ops_t headless_ops = {
  .init = headless_init,
  .show_event = headless_show_event,
  .show_event_with_data = headless_show_event_with_data,
  .set_idle_state = headless_set_idle_state,
  .clear = headless_clear,
  .run = headless_run,
};

const ui_backend_ops_t* ui_backend_get(void) {
  return &headless_ops;
}
