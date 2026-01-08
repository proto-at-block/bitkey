#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"

#include <stdio.h>
#include <string.h>

typedef enum {
  FWUP_PAGE_HASH_VERIFICATION = 0,
  FWUP_PAGE_CONFIRM = 1,
  FWUP_PAGE_UPDATING = 2,
} firmware_update_page_t;

static UI_TASK_DATA firmware_update_data_t update_data;

void display_controller_firmware_update_on_enter(display_controller_t* controller,
                                                 const void* data) {
  // Store firmware update data
  if (data) {
    memcpy(&update_data, data, sizeof(firmware_update_data_t));
  } else {
    memset(&update_data, 0, sizeof(firmware_update_data_t));
  }

  // Set up firmware update screen parameters
  if (data) {
    // On-device FWUP with hash/version data - start at verification page
    controller->nav.firmware_update.current_page = FWUP_PAGE_HASH_VERIFICATION;
    controller->nav.firmware_update.showing_confirm = false;

    strncpy(controller->show_screen.params.firmware_update.hash, update_data.digest,
            sizeof(controller->show_screen.params.firmware_update.hash) - 1);

    // Format version string (convert from hex to semantic version)
    uint32_t major = (update_data.version >> 16) & 0xFF;
    uint32_t minor = (update_data.version >> 8) & 0xFF;
    uint32_t patch = update_data.version & 0xFF;
    snprintf(controller->show_screen.params.firmware_update.version,
             sizeof(controller->show_screen.params.firmware_update.version), "v%u.%u.%u",
             (unsigned int)major, (unsigned int)minor, (unsigned int)patch);

    controller->show_screen.params.firmware_update.current_page = FWUP_PAGE_HASH_VERIFICATION;
    controller->show_screen.params.firmware_update.button_selection = 0;  // Default to Verify
  } else {
    controller->nav.firmware_update.current_page = FWUP_PAGE_UPDATING;
    controller->nav.firmware_update.showing_confirm = false;
    controller->show_screen.params.firmware_update.current_page = FWUP_PAGE_UPDATING;
  }

  controller->show_screen.which_params = fwpb_display_show_screen_firmware_update_tag;
}

flow_action_t display_controller_firmware_update_on_button_press(
  display_controller_t* controller, const button_event_payload_t* event) {
  if (event->type == BUTTON_PRESS_SINGLE) {
    if (controller->nav.firmware_update.current_page == FWUP_PAGE_HASH_VERIFICATION) {
      // Hash verification page
      if (event->button == BUTTON_RIGHT) {
        controller->nav.firmware_update.current_page = FWUP_PAGE_CONFIRM;
        controller->nav.firmware_update.showing_confirm = true;
        controller->show_screen.params.firmware_update.current_page = FWUP_PAGE_CONFIRM;
        controller->show_screen.params.firmware_update.button_selection = 0;  // Default to Verify
        return FLOW_ACTION_REFRESH;
      }
    } else if (controller->nav.firmware_update.current_page == FWUP_PAGE_CONFIRM) {
      // Confirm page
      if (event->button == BUTTON_LEFT) {
        // Go back to hash verification page or toggle between buttons
        if (controller->show_screen.params.firmware_update.button_selection > 0) {
          // Toggle to Verify button
          controller->show_screen.params.firmware_update.button_selection = 0;
          return FLOW_ACTION_REFRESH;
        } else {
          // Go back to hash verification page
          controller->nav.firmware_update.current_page = FWUP_PAGE_HASH_VERIFICATION;
          controller->nav.firmware_update.showing_confirm = false;
          controller->show_screen.params.firmware_update.current_page = FWUP_PAGE_HASH_VERIFICATION;
          return FLOW_ACTION_REFRESH;
        }
      } else if (event->button == BUTTON_RIGHT) {
        // Toggle between Verify and Cancel
        controller->show_screen.params.firmware_update.button_selection =
          (controller->show_screen.params.firmware_update.button_selection == 0) ? 1 : 0;
        return FLOW_ACTION_REFRESH;
      } else if (event->button == BUTTON_BOTH) {
        // Confirm selection
        if (controller->show_screen.params.firmware_update.button_selection == 0) {
          return FLOW_ACTION_APPROVE;  // Verify selected - FWUP will send UI_EVENT_FWUP_START
        } else {
          return FLOW_ACTION_CANCEL;  // Cancel selected
        }
      }
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_firmware_update_on_exit(display_controller_t* controller) {
  (void)controller;
  memset(&update_data, 0, sizeof(update_data));
}

void display_controller_firmware_update_on_tick(display_controller_t* controller) {
  (void)controller;
}

void display_controller_firmware_update_on_event(display_controller_t* controller,
                                                 ui_event_type_t event, const void* data,
                                                 uint32_t len) {
  (void)data;
  (void)len;

  switch (event) {
    case UI_EVENT_FWUP_COMPLETE: {
      // Firmware update completed successfully
      controller->show_screen.params.success.status =
        fwpb_display_params_success_display_params_success_status_FIRMWARE_UPDATED;
      display_controller_show_screen(controller, fwpb_display_show_screen_success_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                     TRANSITION_DURATION_NONE);
      controller->pending_flow_exit = true;
    } break;

    case UI_EVENT_FWUP_FAILED: {
      // Firmware update failed
      controller->show_screen.params.error.status =
        fwpb_display_params_error_display_params_error_status_FIRMWARE_UPDATE_FAILED;
      display_controller_show_screen(controller, fwpb_display_show_screen_error_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                     TRANSITION_DURATION_NONE);
      controller->pending_flow_exit = true;
    } break;

    default: {
      // Ignore other events
    } break;
  }
}
