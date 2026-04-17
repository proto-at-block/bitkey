#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "ui_events.h"

#include <string.h>

#define ERROR_DISPLAY_TICKS MS_TO_DISPLAY_TICKS(1500)

void display_controller_scan_on_enter(display_controller_t* controller, const void* entry_data) {
  (void)entry_data;

  // Initialize flow state
  controller->nav.scan.error_timer = 0;

  // Set screen params to scan/tap
  controller->show_screen.which_params = fwpb_display_show_screen_scan_tag;
  if (controller->scan_confirm_on_enter) {
    controller->show_screen.params.scan.action =
      fwpb_display_params_scan_display_params_scan_action_CONFIRM;
  } else {
    controller->show_screen.params.scan.action =
      fwpb_display_params_scan_display_params_scan_action_TAP;
  }
  controller->show_screen.params.scan.show_error = false;
  controller->scan_confirm_on_enter = false;
}

void display_controller_scan_on_exit(display_controller_t* controller) {
  // Note: show_screen.params is memset by enter_flow after on_exit, so only
  // nav state needs explicit cleanup here.
  controller->nav.scan.error_timer = 0;
  controller->scan_confirm_cancel_returns_to_onboarding = false;
}

flow_action_result_t display_controller_scan_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)data;

  // Handle menu access from scan screen (primary entry point)
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU) {
    return flow_result_navigate(FLOW_MENU, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL &&
      controller->show_screen.params.scan.action ==
        fwpb_display_params_scan_display_params_scan_action_CONFIRM &&
      controller->scan_confirm_cancel_returns_to_onboarding) {
    controller->onboarding_resume_at_scan = true;
    return flow_result_navigate(FLOW_ONBOARDING, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  // Remaining unhandled actions (e.g. APPROVE, EXIT, BACK, and CANCEL in other cases)
  // have no meaning on the scan screen.
  return flow_result_handled();
}

flow_action_result_t display_controller_scan_on_event(display_controller_t* controller,
                                                      ui_event_type_t event, const void* data,
                                                      uint32_t len) {
  (void)data;
  (void)len;

  switch (event) {
    case UI_EVENT_NFC_ERROR:
      // Show "try again" error overlay briefly
      controller->show_screen.params.scan.show_error = true;
      controller->nav.scan.error_timer = ERROR_DISPLAY_TICKS;
      flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                 TRANSITION_DURATION_NONE);
      break;

    default:
      break;
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_scan_on_tick(display_controller_t* controller) {
  // Handle error display timer
  if (controller->nav.scan.error_timer > 0) {
    controller->nav.scan.error_timer--;

    if (controller->nav.scan.error_timer == 0) {
      controller->show_screen.params.scan.show_error = false;
      flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                 TRANSITION_DURATION_NONE);
    }
  }

  return flow_result_handled();
}

const flow_handler_t scan_handler = {
  .on_enter = display_controller_scan_on_enter,
  .on_exit = display_controller_scan_on_exit,
  .on_action = display_controller_scan_on_action,
  .on_event = display_controller_scan_on_event,
  .on_tick = display_controller_scan_on_tick,
};
