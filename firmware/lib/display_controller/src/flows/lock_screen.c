#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"

#include <string.h>

void display_controller_lock_screen_on_enter(display_controller_t* controller, const void* data) {
  (void)data;

  // Set up lock screen parameters
  controller->show_screen.params.locked.battery_percent = controller->battery_percent;
  controller->show_screen.params.locked.is_charging = controller->is_charging;

  controller->show_screen.which_params = fwpb_display_show_screen_locked_tag;
}

void display_controller_lock_screen_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_t display_controller_lock_screen_on_button_press(display_controller_t* controller,
                                                             const button_event_payload_t* event) {
  (void)controller;
  (void)event;
  // Lock screen doesn't handle button presses directly - auth is handled separately
  return FLOW_ACTION_NONE;
}

void display_controller_lock_screen_on_tick(display_controller_t* controller) {
  (void)controller;
}

void display_controller_lock_screen_on_event(display_controller_t* controller,
                                             ui_event_type_t event, const void* data,
                                             uint32_t len) {
  (void)data;
  (void)len;

  switch (event) {
    case UI_EVENT_BATTERY_SOC:
    case UI_EVENT_CHARGING:
    case UI_EVENT_CHARGING_UNPLUGGED:
      // Global state already updated - just refresh lock screen display
      controller->show_screen.params.locked.battery_percent = controller->battery_percent;
      controller->show_screen.params.locked.is_charging = controller->is_charging;
      display_controller_show_screen(controller, fwpb_display_show_screen_locked_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);
      break;

    case UI_EVENT_CHARGING_FINISHED:
    case UI_EVENT_CHARGING_FINISHED_PERSISTENT:
      // Charging is complete but still plugged in
      // Keep showing the charging indicator (battery is full but still connected)
      // Note: We could potentially show a different icon here for "fully charged"
      // but for now we keep the charging indicator visible
      break;

    default:
      // Ignore other events
      break;
  }
}
