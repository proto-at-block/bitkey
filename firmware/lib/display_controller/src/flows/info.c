#include "display_controller.h"
#include "display_controller_internal.h"

#include <string.h>

void display_controller_info_on_enter(display_controller_t* controller, const void* entry_data) {
  controller->nav.info.screen_type =
    (entry_data == NULL) ? INFO_SCREEN_REGULATORY : INFO_SCREEN_ABOUT;

  // Check if we should show regulatory or about
  if (controller->nav.info.screen_type == INFO_SCREEN_REGULATORY) {
    // Set up regulatory screen
    controller->show_screen.which_params = fwpb_display_show_screen_regulatory_tag;
  } else {
    // Set up device info (About) screen
    if (controller->has_device_info) {
      strncpy(controller->show_screen.params.about.firmware_version,
              controller->device_info.firmware_version,
              sizeof(controller->show_screen.params.about.firmware_version) - 1);
      strncpy(controller->show_screen.params.about.hardware_version,
              controller->device_info.hardware_version,
              sizeof(controller->show_screen.params.about.hardware_version) - 1);
      strncpy(controller->show_screen.params.about.serial_number,
              controller->device_info.serial_number,
              sizeof(controller->show_screen.params.about.serial_number) - 1);
    } else {
      memset(&controller->show_screen.params.about, 0, sizeof(fwpb_display_params_about));
    }

    controller->show_screen.which_params = fwpb_display_show_screen_about_tag;
  }
}

void display_controller_info_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_info_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_info_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;
  (void)controller;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // Use flow_result_exit to properly pop nav stack and restore menu selection
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
