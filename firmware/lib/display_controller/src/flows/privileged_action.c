#include "display_controller.h"
#include "display_controller_internal.h"

#include <stdio.h>
#include <string.h>

void display_controller_privileged_action_on_enter(display_controller_t* controller,
                                                   const void* entry_data) {
  (void)entry_data;

  // Data already stored in nav union by scan flow before navigation
  // Copy to show_screen params for display
  memcpy(&controller->show_screen.params.privileged_action,
         &controller->nav.privileged_action.params, sizeof(fwpb_display_params_privileged_action));

  // Set screen type
  controller->show_screen.which_params = fwpb_display_show_screen_privileged_action_tag;
}

void display_controller_privileged_action_on_exit(display_controller_t* controller) {
  // Clean up privileged action data
  memset(&controller->nav.privileged_action, 0, sizeof(controller->nav.privileged_action));
}

flow_action_result_t display_controller_privileged_action_on_tick(
  display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_privileged_action_on_event(display_controller_t* controller,
                                                                   ui_event_type_t event,
                                                                   const void* data, uint32_t len) {
  (void)controller;
  (void)event;
  (void)data;
  (void)len;

  // All data comes via entry_data, no events needed
  return flow_result_handled();
}

flow_action_result_t display_controller_privileged_action_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)data;

  switch (action) {
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE:
      // Display MCU handles page navigation internally (like money_movement)
      // Final approve on scan page triggers completion
      return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                              TRANSITION_DURATION_STANDARD);

    case fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL:
      // Cancel returns to scan screen
      return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                              TRANSITION_DURATION_STANDARD);

    case fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU:
      // Menu button navigates to menu
      return flow_result_navigate(FLOW_MENU, fwpb_display_transition_DISPLAY_TRANSITION_FADE);

    default:
      break;
  }

  return flow_result_handled();
}
