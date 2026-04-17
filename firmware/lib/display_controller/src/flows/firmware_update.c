#include "attributes.h"
#include "display_controller.h"

#ifdef EMBEDDED_BUILD
#include "confirmation_manager.h"
#endif
#include "display_controller_internal.h"
#include "log.h"
#include "ui_events.h"
#include "wallet.pb.h"

#include <stdio.h>
#include <string.h>

// Page values must match screen_firmware_update.c.
typedef enum {
  PAGE_CONFIRMATION = 0,
  PAGE_IN_PROGRESS = 2,
  PAGE_SUCCESS = 3,
  PAGE_VERIFYING = 4,
  PAGE_FAILED = 5,
} fwup_page_t;

// Number of display ticks (~20ms each) to show the success page
// before transitioning to the scan screen for mid-sequence updates.
#define FWUP_SUCCESS_DISPLAY_TICKS (2000 / 20)

/**
 * @brief Updates the version string shown for the firmware update based on the
 * received confirmation data.
 *
 * @param[out] controller  Display controller instance to update.
 * @param[in]  data        Firmware update confirmation message.
 */
static void display_controller_firmware_update_set_version(display_controller_t* controller,
                                                           const fwup_confirmation_data_t* data);
static void display_controller_firmware_update_sync_params(display_controller_t* controller);

void display_controller_firmware_update_on_enter(display_controller_t* controller,
                                                 const void* entry_data) {
  // Clear all nav data; show_screen params are rebuilt from nav state.
  memset(&controller->nav.firmware_update, 0, sizeof(controller->nav.firmware_update));

  if (!entry_data) {
    LOGE("FWUP: on_enter no entry_data");
    controller->nav.firmware_update.params.page = (uint32_t)PAGE_CONFIRMATION;
    display_controller_firmware_update_sync_params(controller);
    return;
  }

  const fwup_confirmation_data_t* data = (const fwup_confirmation_data_t*)entry_data;
  display_controller_firmware_update_set_version(controller, data);

  // Skip straight to in-progress when confirmation is not needed (e.g., device
  // not onboarded, or version already confirmed on another core).
  if (data->skip_confirmation) {
    controller->nav.firmware_update.params.page = (uint32_t)PAGE_IN_PROGRESS;
  } else {
    controller->nav.firmware_update.params.page = (uint32_t)PAGE_CONFIRMATION;
  }

  display_controller_firmware_update_sync_params(controller);
}

void display_controller_firmware_update_on_exit(display_controller_t* controller) {
#ifdef EMBEDDED_BUILD
  // Clear confirmation state regardless of exit path
  confirmation_manager_clear();
#endif

  // Clean up firmware update data
  memset(&controller->nav.firmware_update, 0, sizeof(controller->nav.firmware_update));
}

flow_action_result_t display_controller_firmware_update_on_tick(display_controller_t* controller) {
#ifdef EMBEDDED_BUILD
  // Exit promptly if the pending confirmation expires while the user is still
  // on the FWUP approval screen.
  if (controller->nav.firmware_update.handoff_timer == 0 && confirmation_manager_is_expired()) {
    confirmation_manager_clear();
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }
#endif

  display_controller_tick_handoff_to_scan(controller,
                                          &controller->nav.firmware_update.handoff_timer);
  return flow_result_handled();
}

static void display_controller_firmware_update_set_version(display_controller_t* controller,
                                                           const fwup_confirmation_data_t* data) {
  if (data->version_str[0] != '\0') {
    strncpy(controller->nav.firmware_update.params.version, data->version_str,
            sizeof(controller->nav.firmware_update.params.version) - 1);
    controller->nav.firmware_update.params
      .version[sizeof(controller->nav.firmware_update.params.version) - 1] = '\0';
  } else {
    LOGW("FWUP: no version in confirmation data");
    controller->nav.firmware_update.params.version[0] = '\0';
  }
}

static void display_controller_firmware_update_sync_params(display_controller_t* controller) {
  controller->show_screen.params.firmware_update = controller->nav.firmware_update.params;
  controller->show_screen.which_params = fwpb_display_show_screen_firmware_update_tag;
}

flow_action_result_t display_controller_firmware_update_on_event(display_controller_t* controller,
                                                                 ui_event_type_t event,
                                                                 const void* data, uint32_t len) {
  if (!controller) {
    return flow_result_handled();
  }

  switch (event) {
    case UI_EVENT_FWUP_CONFIRMATION: {
      // A retried FWUP confirmation should behave like a fresh approval screen.
      // Cancel any in-flight handoff countdown so a stale timer cannot bounce
      // the user back to scan while the approval page is visible again.
      controller->nav.firmware_update.handoff_timer = 0;
      controller->nav.firmware_update.params.page = (uint32_t)PAGE_CONFIRMATION;

      if ((data != NULL) && (len == sizeof(fwup_confirmation_data_t))) {
        display_controller_firmware_update_set_version(controller,
                                                       (const fwup_confirmation_data_t*)data);
      } else {
        controller->nav.firmware_update.params.version[0] = '\0';
      }

      display_controller_firmware_update_sync_params(controller);
      display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      return flow_result_handled();
    }

    case UI_EVENT_FWUP_START: {
      // FWUP may start before the handoff countdown completes, so stop the
      // timer before rebuilding the in-progress screen from cached state.
      controller->nav.firmware_update.handoff_timer = 0;
      controller->nav.firmware_update.params.page = (uint32_t)PAGE_IN_PROGRESS;

      display_controller_firmware_update_sync_params(controller);
      display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      return flow_result_handled();
    }

    case UI_EVENT_FWUP_COMPLETE: {
      bool is_final = (data != NULL) && (len == sizeof(bool)) && *(const bool*)data;

      // Show the success page. For the final MCU the device resets shortly
      // after, so the page stays visible until power-off. For mid-sequence
      // (UXC done, Core next) the handoff timer transitions to the scan
      // screen after FWUP_SUCCESS_DISPLAY_MS so the user sees success
      // before "continue on phone".
      controller->nav.firmware_update.params.page = (uint32_t)PAGE_SUCCESS;
      display_controller_firmware_update_sync_params(controller);
      display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      if (!is_final) {
        controller->nav.firmware_update.handoff_timer = FWUP_SUCCESS_DISPLAY_TICKS;
      }
      return flow_result_handled();
    }

    case UI_EVENT_FWUP_VERIFYING: {
      controller->nav.firmware_update.params.page = (uint32_t)PAGE_VERIFYING;

      display_controller_firmware_update_sync_params(controller);
      display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      return flow_result_handled();
    }

    case UI_EVENT_FWUP_FAILED: {
      controller->nav.firmware_update.params.page = (uint32_t)PAGE_FAILED;

      display_controller_firmware_update_sync_params(controller);
      display_controller_show_screen(controller, fwpb_display_show_screen_firmware_update_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      return flow_result_handled();
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
#ifdef EMBEDDED_BUILD
    // User confirmed firmware update on device screen (held ring)
    confirmation_manager_approve();
#endif

    // Transition to confirm scan screen on next tick.
    controller->nav.firmware_update.handoff_timer = 1;

    // When FWUP actually starts, UI_EVENT_FWUP_START will transition to in_progress screen
    return flow_result_handled();
  }

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU) {
    // User cancelled - exit (confirmation cleared in on_exit)
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
