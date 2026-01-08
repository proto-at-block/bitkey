#include "display_controller.h"
#include "display_controller_internal.h"

#include <string.h>

void display_controller_info_on_enter(display_controller_t* controller, const void* data) {
  (void)data;

  // Check if we should show regulatory or about
  if (controller->nav.info.showing_regulatory) {
    // Set up regulatory screen
    controller->show_screen.params.regulatory.page = 0;
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

flow_action_t display_controller_info_on_button_press(display_controller_t* controller,
                                                      const button_event_payload_t* event) {
  // Handle Regulatory screen page navigation
  if (controller->nav.info.showing_regulatory) {
    if (event->type == BUTTON_PRESS_SINGLE) {
      if (event->button == BUTTON_LEFT) {
        // Navigate to previous page
        if (controller->show_screen.params.regulatory.page > 0) {
          controller->show_screen.params.regulatory.page--;
          return FLOW_ACTION_REFRESH;
        }
      } else if (event->button == BUTTON_RIGHT) {
        // Navigate to next page
        if (controller->show_screen.params.regulatory.page < 2) {  // 3 total pages (0, 1, 2)
          controller->show_screen.params.regulatory.page++;
          return FLOW_ACTION_REFRESH;
        }
      } else if (event->button == BUTTON_BOTH) {
        // L+R to exit
        return FLOW_ACTION_EXIT;
      }
    }
  } else {
    // About screen - only exit with L+R
    if (event->type == BUTTON_PRESS_SINGLE && event->button == BUTTON_BOTH) {
      return FLOW_ACTION_EXIT;
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_info_on_exit(display_controller_t* controller) {
  (void)controller;
}

void display_controller_info_on_tick(display_controller_t* controller) {
  (void)controller;
}
