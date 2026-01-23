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

void display_controller_firmware_update_on_enter(display_controller_t* controller,
                                                 const void* entry_data) {
  // Store firmware update data in nav union
  if (entry_data) {
    memcpy(&controller->nav.firmware_update.data, entry_data, sizeof(firmware_update_data_t));
  } else {
    memset(&controller->nav.firmware_update.data, 0, sizeof(firmware_update_data_t));
  }

  // Set up firmware update screen parameters
  if (entry_data) {
    // On-device FWUP with hash/version data - start at verification page
    controller->nav.firmware_update.current_page = FWUP_PAGE_HASH_VERIFICATION;
    controller->nav.firmware_update.showing_confirm = false;

    strncpy(controller->show_screen.params.firmware_update.hash,
            controller->nav.firmware_update.data.digest,
            sizeof(controller->show_screen.params.firmware_update.hash) - 1);

    // Format version string (convert from hex to semantic version)
    uint32_t major = (controller->nav.firmware_update.data.version >> 16) & 0xFF;
    uint32_t minor = (controller->nav.firmware_update.data.version >> 8) & 0xFF;
    uint32_t patch = controller->nav.firmware_update.data.version & 0xFF;
    snprintf(controller->show_screen.params.firmware_update.version,
             sizeof(controller->show_screen.params.firmware_update.version), "v%u.%u.%u",
             (unsigned int)major, (unsigned int)minor, (unsigned int)patch);
  } else {
    controller->nav.firmware_update.current_page = FWUP_PAGE_UPDATING;
    controller->nav.firmware_update.showing_confirm = false;
  }

  controller->show_screen.which_params = fwpb_display_show_screen_firmware_update_tag;
}

void display_controller_firmware_update_on_exit(display_controller_t* controller) {
  // Clean up firmware update data
  memset(&controller->nav.firmware_update, 0, sizeof(controller->nav.firmware_update));
}

flow_action_result_t display_controller_firmware_update_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_firmware_update_on_event(display_controller_t* controller,
                                                                 ui_event_type_t event,
                                                                 const void* data, uint32_t len) {
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
      // Exit flow directly (no more pending_flow_exit!)
      return flow_result_exit_to_scan();
    }

    case UI_EVENT_FWUP_FAILED: {
      // Firmware update failed
      controller->show_screen.params.error.status =
        fwpb_display_params_error_display_params_error_status_FIRMWARE_UPDATE_FAILED;
      display_controller_show_screen(controller, fwpb_display_show_screen_error_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                     TRANSITION_DURATION_NONE);
      // Exit flow directly (no more pending_flow_exit!)
      return flow_result_exit_to_scan();
    }

    default: {
      // Ignore other events
    } break;
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_firmware_update_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;
  (void)controller;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE) {
    // Verify firmware
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL ||
             action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT ||
             action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // Cancel or exit
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
