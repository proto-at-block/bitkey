#include "attributes.h"
#include "confirmation_manager.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "log.h"
#include "wallet.pb.h"

#include <stdio.h>
#include <string.h>

// Page states (matches screen_firmware_update.c)
typedef enum {
  PAGE_CONFIRMATION = 0,
  PAGE_SCANNING = 1,
  PAGE_IN_PROGRESS = 2,
} fwup_page_t;

void display_controller_firmware_update_on_enter(display_controller_t* controller,
                                                 const void* entry_data) {
  // Clear all params and nav data
  memset(&controller->show_screen.params.firmware_update, 0,
         sizeof(controller->show_screen.params.firmware_update));
  memset(&controller->nav.firmware_update, 0, sizeof(controller->nav.firmware_update));

  // Show confirmation screen
  if (!entry_data) {
    LOGE("FWUP: on_enter called without entry_data, using defaults");
    controller->show_screen.params.firmware_update.page = (uint32_t)PAGE_CONFIRMATION;
    controller->show_screen.which_params = fwpb_display_show_screen_firmware_update_tag;
    return;
  }

  const fwpb_fwup_start_cmd* cmd_data = (const fwpb_fwup_start_cmd*)entry_data;

  controller->show_screen.params.firmware_update.mcu_role = cmd_data->mcu_role;
  controller->show_screen.params.firmware_update.page = (uint32_t)PAGE_CONFIRMATION;

  controller->show_screen.which_params = fwpb_display_show_screen_firmware_update_tag;
}

void display_controller_firmware_update_on_exit(display_controller_t* controller) {
  // Clear confirmation state regardless of exit path
  confirmation_manager_clear();

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

  if (!controller) {
    return flow_result_handled();
  }

  switch (event) {
    case UI_EVENT_FWUP_START: {
      // FWUP started - transition from scanning page to in_progress page
      controller->show_screen.params.firmware_update.page = (uint32_t)PAGE_IN_PROGRESS;
      controller->show_screen.params.firmware_update.version[0] = '\0';

      display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      return flow_result_handled();
    }

    case UI_EVENT_FWUP_COMPLETE: {
      // Firmware update completed successfully - exit to appropriate screen
      return flow_result_exit_to_scan();
    }

    case UI_EVENT_FWUP_FAILED: {
      // Firmware update failed - exit to appropriate screen
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

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE) {
    // User confirmed firmware update on device screen (held ring)
    confirmation_manager_approve();

    // Transition to scanning page
    controller->show_screen.params.firmware_update.page = (uint32_t)PAGE_SCANNING;
    display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                   TRANSITION_DURATION_NONE);

    // When FWUP actually starts, UI_EVENT_FWUP_START will transition to in_progress screen
    return flow_result_handled();
  }

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // User cancelled - exit (confirmation cleared in on_exit)
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
