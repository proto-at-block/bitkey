#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "ui_events.h"

#include <string.h>

#define UNLOCK_DISPLAY_TICKS 4  // 80ms (20ms per tick)

void display_controller_locked_on_enter(display_controller_t* controller, const void* entry_data) {
  (void)entry_data;

  // Initialize flow state
  controller->nav.locked.unlocking = false;
  controller->nav.locked.unlock_timer = 0;

  // Set up locked screen parameters
  controller->show_screen.params.locked.battery_percent = controller->battery_percent;
  controller->show_screen.params.locked.is_charging = controller->is_charging;
  controller->show_screen.params.locked.show_unlocked = false;  // Start locked

  controller->show_screen.which_params = fwpb_display_show_screen_locked_tag;
  display_controller_show_screen(controller, fwpb_display_show_screen_locked_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                 TRANSITION_DURATION_STANDARD);
}

void display_controller_locked_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_locked_on_tick(display_controller_t* controller) {
  // Handle unlock animation timer
  if (controller->nav.locked.unlocking && controller->nav.locked.unlock_timer > 0) {
    controller->nav.locked.unlock_timer--;

    if (controller->nav.locked.unlock_timer == 0) {
      return flow_result_exit_to_scan();
    }
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_locked_on_event(display_controller_t* controller,
                                                        ui_event_type_t event, const void* data,
                                                        uint32_t len) {
  switch (event) {
#ifdef MFGTEST
    case UI_EVENT_CAPTOUCH:
      // 'break' intentionally omitted.
#endif
    case UI_EVENT_AUTH_SUCCESS:
      // Start unlock animation
      if (!controller->nav.locked.unlocking) {
        controller->nav.locked.unlocking = true;
        controller->nav.locked.unlock_timer = UNLOCK_DISPLAY_TICKS;

        // Update screen to show unlocked icon
        controller->show_screen.params.locked.show_unlocked = true;
        flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                   TRANSITION_DURATION_NONE);
      }
      break;

    case UI_EVENT_BATTERY_SOC:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_UNPLUGGED:
      // Update battery display
      if (event == UI_EVENT_BATTERY_SOC && data && len == sizeof(battery_soc_data_t)) {
        const battery_soc_data_t* battery = (const battery_soc_data_t*)data;
        controller->show_screen.params.locked.battery_percent = battery->battery_percent;
      }
      controller->show_screen.params.locked.is_charging = controller->is_charging;

      // Maintain current unlock state
      flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                 TRANSITION_DURATION_NONE);
      break;

    case UI_EVENT_CHARGING_FINISHED:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_FINISHED_PERSISTENT:
      // Charging is complete but still plugged in
      // Keep showing the charging indicator (battery is full but still connected)
      break;

    default:
      // Ignore other events
      break;
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_locked_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)data;

  // Lock screen exits to scan on any action (unlock is via fingerprint event)
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
