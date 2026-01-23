#include "display_controller.h"
#include "display_controller_internal.h"

#include <stdio.h>
#include <string.h>

void display_controller_onboarding_on_enter(display_controller_t* controller,
                                            const void* entry_data) {
  (void)entry_data;

  // Set up screen params
  controller->show_screen.which_params = fwpb_display_show_screen_onboarding_tag;
}

void display_controller_onboarding_on_exit(display_controller_t* controller) {
  (void)controller;

  // Onboarding is marked complete upon successful fingerprint enrollment.
}

flow_action_result_t display_controller_onboarding_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_onboarding_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;
  (void)controller;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // Onboarding complete → enter fingerprint enrollment
    return flow_result_navigate(FLOW_FINGERPRINT_MGMT,
                                fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  return flow_result_handled();
}
