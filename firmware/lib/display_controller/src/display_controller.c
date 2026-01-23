#include "display_controller.h"

#include "auth.h"
#include "display_controller_internal.h"
#include "log.h"
#include "rtos.h"
#include "secutils.h"
#include "uc.h"
#include "uxc.pb.h"
#ifdef EMBEDDED_BUILD
#include "ipc.h"
#include "onboarding.h"
#include "sysevent.h"
#endif

#include <arithmetic.h>
#include <attributes.h>
#include <stdio.h>
#include <string.h>

#ifndef EMBEDDED_BUILD
// External functions and stub types for simulation
extern fwpb_display_result ui_execute_command(const fwpb_display_command* cmd);
extern secure_bool_t onboarding_complete(void);
extern void onboarding_wipe_state(void);
typedef struct {
  uint8_t count;
  uint8_t indices[3];
  char labels[3][32];
} auth_enrolled_fingerprints_response_t;
#endif

static fwpb_display_result display_controller_send_command(const fwpb_display_command* cmd) {
  if (!cmd) {
    return fwpb_display_result_DISPLAY_RESULT_INVALID_PARAM;
  }

#ifdef EMBEDDED_BUILD
  // Allocate protobuf message for UXC communication
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  if (!msg) {
    LOGE("Failed to allocate UC proto message");
    return fwpb_display_result_DISPLAY_RESULT_ERROR;
  }

  // Set message type to display command
  msg->which_msg = fwpb_uxc_msg_host_display_cmd_tag;

  // Copy the display command directly (it's already in protobuf format)
  memcpy(&msg->msg.display_cmd, cmd, sizeof(fwpb_display_command));

  // Send the message over UART
  bool success = uc_send(msg);

  if (!success) {
    LOGE("Failed to send display command, which_params: %d", cmd->command.show_screen.which_params);
    return fwpb_display_result_DISPLAY_RESULT_ERROR;
  }

  refresh_auth();
  return fwpb_display_result_DISPLAY_RESULT_SUCCESS;
#else
  // Direct execution for w3-uxc simulation
  return ui_execute_command(cmd);
#endif
}

// Forward declarations for static functions
static void lock_device(void);
static void unlock_device(void);
static void enter_flow(flow_id_t flow, const void* entry_data);
static void handle_flow_action_result(flow_action_result_t result);
static void refresh_screen(void);

// Global controller instance
display_controller_t UI_TASK_DATA controller = {
  .is_locked = true,  // Start locked
  .current_flow = FLOW_ONBOARDING,
  .show_screen.which_params = fwpb_display_show_screen_onboarding_tag,
  .nav_stack_depth = 0,
};

// Display flags sent with every show_screen command
static SHARED_TASK_DATA uint32_t s_display_flags = 0;

void display_controller_query_fingerprint_status(void) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_DATA auth_get_enrolled_fingerprints_internal_t cmd;

  ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_GET_ENROLLED_FINGERPRINTS_INTERNAL);
#endif
}

static void display_controller_delete_fingerprint(uint8_t index) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_DATA auth_delete_fingerprint_internal_t cmd;
  cmd.index = index;

  ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_DELETE_FINGERPRINT_INTERNAL);
#endif
}

// Extern declaration for scan flow handler
extern const flow_handler_t scan_handler;

// Flow handlers with new interface
static const flow_handler_t menu_handler = {
  .on_enter = display_controller_menu_on_enter,
  .on_exit = display_controller_menu_on_exit,
  .on_tick = display_controller_menu_on_tick,
  .on_event = NULL,
  .on_action = display_controller_menu_on_action,
};

static const flow_handler_t money_movement_handler = {
  .on_enter = display_controller_money_movement_on_enter,
  .on_exit = display_controller_money_movement_on_exit,
  .on_tick = display_controller_money_movement_on_tick,
  .on_event = display_controller_money_movement_on_event,
  .on_action = display_controller_money_movement_on_action,
};

static const flow_handler_t brightness_handler = {
  .on_enter = display_controller_brightness_on_enter,
  .on_exit = display_controller_brightness_on_exit,
  .on_tick = display_controller_brightness_on_tick,
  .on_event = NULL,
  .on_action = display_controller_brightness_on_action,
};

static const flow_handler_t info_handler = {
  .on_enter = display_controller_info_on_enter,
  .on_exit = display_controller_info_on_exit,
  .on_tick = display_controller_info_on_tick,
  .on_event = NULL,
  .on_action = display_controller_info_on_action,
};

static const flow_handler_t onboarding_handler = {
  .on_enter = display_controller_onboarding_on_enter,
  .on_exit = display_controller_onboarding_on_exit,
  .on_tick = display_controller_onboarding_on_tick,
  .on_event = NULL,
  .on_action = display_controller_onboarding_on_action,
};

static const flow_handler_t fingerprint_handler = {
  .on_enter = display_controller_fingerprint_on_enter,
  .on_exit = display_controller_fingerprint_on_exit,
  .on_tick = display_controller_fingerprint_on_tick,
  .on_event = display_controller_fingerprint_on_event,
  .on_action = display_controller_fingerprint_on_action,
};

static const flow_handler_t firmware_update_handler = {
  .on_enter = display_controller_firmware_update_on_enter,
  .on_exit = display_controller_firmware_update_on_exit,
  .on_tick = display_controller_firmware_update_on_tick,
  .on_event = display_controller_firmware_update_on_event,
  .on_action = display_controller_firmware_update_on_action,
};

static const flow_handler_t fingerprint_menu_handler = {
  .on_enter = display_controller_fingerprint_menu_on_enter,
  .on_exit = display_controller_fingerprint_menu_on_exit,
  .on_tick = display_controller_fingerprint_menu_on_tick,
  .on_event = display_controller_fingerprint_menu_on_event,
  .on_action = display_controller_fingerprint_menu_on_action,
};

static const flow_handler_t mfg_handler = {
  .on_enter = display_controller_mfg_on_enter,
  .on_exit = display_controller_mfg_on_exit,
  .on_tick = display_controller_mfg_on_tick,
  .on_event = display_controller_mfg_on_event,
  .on_action = display_controller_mfg_on_action,
};

static const flow_handler_t locked_handler = {
  .on_enter = display_controller_locked_on_enter,
  .on_exit = display_controller_locked_on_exit,
  .on_tick = display_controller_locked_on_tick,
  .on_event = display_controller_locked_on_event,
  .on_action = display_controller_locked_on_action,
};

static const flow_handler_t privileged_action_handler = {
  .on_enter = display_controller_privileged_action_on_enter,
  .on_exit = display_controller_privileged_action_on_exit,
  .on_tick = display_controller_privileged_action_on_tick,
  .on_event = display_controller_privileged_action_on_event,
  .on_action = display_controller_privileged_action_on_action,
};

// Array mapping flow IDs to flow handlers
static const flow_handler_t* flow_handlers[FLOW_COUNT] = {
  [FLOW_SCAN] = &scan_handler,
  [FLOW_ONBOARDING] = &onboarding_handler,
  [FLOW_MENU] = &menu_handler,
  [FLOW_TRANSACTION] = &money_movement_handler,
  [FLOW_FINGERPRINT_MGMT] = &fingerprint_handler,
  [FLOW_FINGERPRINTS_MENU] = &fingerprint_menu_handler,
  [FLOW_LOCKED] = &locked_handler,
  [FLOW_RECOVERY] = NULL,  // Future
  [FLOW_FIRMWARE_UPDATE] = &firmware_update_handler,
  [FLOW_WIPE] = NULL,  // Future
  [FLOW_PRIVILEGED_ACTIONS] = &privileged_action_handler,
  [FLOW_BRIGHTNESS] = &brightness_handler,
  [FLOW_INFO] = &info_handler,
  [FLOW_MFG] = &mfg_handler,
};

// Returns true if we have a valid active flow (safe to access flow_handlers)
static inline bool in_flow(void) {
  return controller.current_flow < FLOW_COUNT;
}

// Returns true if in a flow and accepting user input
static inline bool accepting_input(void) {
  return !controller.is_locked && in_flow();
}

void display_controller_init(void) {
  memset(&controller, 0, sizeof(controller));
  controller.is_locked = true;
  // Initialize battery state (will be updated via UI_EVENT_BATTERY_SOC)
  controller.battery_percent = 0;
  controller.is_charging = false;
  // Initialize brightness (will be updated via UI_EVENT_SET_DEVICE_INFO)
  controller.show_screen.brightness_percent = 0;

#ifdef EMBEDDED_BUILD
  // Wait for filesystem to be ready before checking onboarding status
  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);
#endif

#ifdef MFGTEST
  // In MFG test mode, always start locked regardless of onboarding status
  const bool start_onboarding = false;
#else
  const bool start_onboarding = (onboarding_complete() != SECURE_TRUE);
#endif

  if (start_onboarding) {
    controller.is_locked = false;
    controller.current_flow = FLOW_ONBOARDING;
    controller.show_screen.which_params = fwpb_display_show_screen_onboarding_tag;
  } else {
    controller.is_locked = true;
    controller.current_flow = FLOW_LOCKED;
    controller.show_screen.which_params = fwpb_display_show_screen_locked_tag;
  }
}

void display_controller_tick(void) {
  // Handle flow ticks if in flow
  if (in_flow()) {
    const flow_handler_t* handler = flow_handlers[controller.current_flow];
    if (handler && handler->on_tick) {
      flow_action_result_t result = handler->on_tick(&controller);
      handle_flow_action_result(result);
    }
  }
}

void display_controller_show_initial_screen(void) {
  LOGI("UXC ready received, showing initial screen at %ldms", rtos_thread_systime());
  controller.initial_screen_shown = true;
  refresh_screen();
}

void display_controller_handle_ui_event(ui_event_type_t event, const void* data, uint32_t len) {
  switch (event) {
    case UI_EVENT_BUTTON: {
      if (data && len == sizeof(button_event_payload_t)) {
        const button_event_payload_t* button_event = (const button_event_payload_t*)data;
        LOGD("Button event: type=%d button=%d", button_event->type, button_event->button);
        // Touch-only UI - buttons not used for navigation
      }
      break;
    }

    case UI_EVENT_AUTH_SUCCESS: {
      if (controller.is_locked) {
        unlock_device();
      }

      // If we're in the fingerprints menu and have template index data, trigger animation
      if (controller.current_flow == FLOW_FINGERPRINTS_MENU && data &&
          len == sizeof(fingerprint_auth_data_t)) {
        const fingerprint_auth_data_t* auth_data = (const fingerprint_auth_data_t*)data;

        if (auth_data->template_index < 3) {
          LOGD("Fingerprint %d authenticated - triggering animation", auth_data->template_index);

          // Store which fingerprint to animate and trigger it
          controller.nav.fingerprint_menu.authenticated_index = auth_data->template_index;
          controller.nav.fingerprint_menu.show_authenticated = true;

          // Update params with animation flags
          controller.show_screen.params.menu_fingerprints.show_authenticated = true;
          controller.show_screen.params.menu_fingerprints.authenticated_index =
            auth_data->template_index;

          // Trigger a screen refresh to show the animation
          display_controller_show_screen(
            &controller, fwpb_display_show_screen_menu_fingerprints_tag,
            fwpb_display_transition_DISPLAY_TRANSITION_NONE, TRANSITION_DURATION_NONE);

          // Clear the flag after showing (one-shot trigger like bounce)
          controller.nav.fingerprint_menu.show_authenticated = false;
        }
      }
      break;
    }

    case UI_EVENT_AUTH_LOCKED:
      // 'break' intentionally omitted.
    case UI_EVENT_AUTH_LOCKED_FROM_FWUP:
      // 'break' intentionally omitted.
    case UI_EVENT_AUTH_LOCKED_FROM_ENROLLMENT: {
      lock_device();
      break;
    }

    case UI_EVENT_SET_DEVICE_INFO: {
      if (data && len == sizeof(device_info_t)) {
        const device_info_t* info = (const device_info_t*)data;
        // Store device info
        strncpy(controller.device_info.firmware_version, info->firmware_version,
                sizeof(controller.device_info.firmware_version) - 1);
        strncpy(controller.device_info.hardware_version, info->hardware_version,
                sizeof(controller.device_info.hardware_version) - 1);
        strncpy(controller.device_info.serial_number, info->serial_number,
                sizeof(controller.device_info.serial_number) - 1);
        controller.has_device_info = true;

        // Set brightness
        controller.show_screen.brightness_percent = info->brightness_percent;
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_START: {
      // Prevents resetting the page when enrollment is triggered internally
      if (controller.current_flow != FLOW_FINGERPRINT_MGMT) {
        enter_flow(FLOW_FINGERPRINT_MGMT, NULL);
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_COMPLETE: {
      // Query updated fingerprint enrollment status after completion
      if (controller.current_flow == FLOW_FINGERPRINT_MGMT) {
        display_controller_query_fingerprint_status();
      }
      break;
    }

    case UI_EVENT_FINGERPRINT_STATUS: {
      // Handle fingerprint status response from auth task
      if (data && len == sizeof(auth_enrolled_fingerprints_response_t)) {
        const auth_enrolled_fingerprints_response_t* response =
          (const auth_enrolled_fingerprints_response_t*)data;

        // Clear all enrollment status first
        memset(controller.fingerprint_enrolled, 0, sizeof(controller.fingerprint_enrolled));
        memset(controller.fingerprint_labels, 0, sizeof(controller.fingerprint_labels));

        // Update enrollment status and labels
        for (uint8_t i = 0; i < response->count; i++) {
          uint8_t idx = response->indices[i];
          if (idx < ARRAY_SIZE(controller.fingerprint_enrolled)) {
            controller.fingerprint_enrolled[idx] = true;
            strncpy(controller.fingerprint_labels[idx], response->labels[i], 31);
            controller.fingerprint_labels[idx][31] = '\0';
          }
        }

        LOGD("Updated fingerprint status: %d enrolled, array: [%d, %d, %d]", response->count,
             controller.fingerprint_enrolled[0], controller.fingerprint_enrolled[1],
             controller.fingerprint_enrolled[2]);
      }
      break;
    }

    case UI_EVENT_FWUP_START: {
      enter_flow(FLOW_FIRMWARE_UPDATE, data);
      break;
    }

    case UI_EVENT_SHOW_MENU: {
      enter_flow(FLOW_MENU, NULL);
      break;
    }

    case UI_EVENT_BATTERY_SOC:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_FINISHED:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_FINISHED_PERSISTENT:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_UNPLUGGED: {
      // Update global state first
      if (event == UI_EVENT_BATTERY_SOC && data && len == sizeof(battery_soc_data_t)) {
        const battery_soc_data_t* battery = (const battery_soc_data_t*)data;
        controller.battery_percent = battery->battery_percent;
      } else if (event == UI_EVENT_CHARGING) {
        controller.is_charging = true;
      } else if (event == UI_EVENT_CHARGING_UNPLUGGED) {
        controller.is_charging = false;
      }

      break;
    }

    case UI_EVENT_MFGTEST_SHOW_SCREEN: {
      // Enter MFG flow if not already in it, passing payload as entry_data
      if (!in_flow() || controller.current_flow != FLOW_MFG) {
        enter_flow(FLOW_MFG, data);
      }
      break;
    }

    case UI_EVENT_CAPTOUCH: {
#ifdef MFGTEST
      // In MFGTEST, captouch unlocks the device (auth task doesn't run)
      if (controller.is_locked && controller.current_flow == FLOW_LOCKED) {
        unlock_device();
      }
#endif
      break;
    }

    default:
      break;
  }

  // Finally, route ALL events to current flow if it has an event handler
  // This happens after controller state changes so flows see updated state
  // Flows will ignore events they don't care about
  if (in_flow()) {
    const flow_handler_t* handler = flow_handlers[controller.current_flow];
    if (handler && handler->on_event) {
      flow_action_result_t result = handler->on_event(&controller, event, data, len);
      handle_flow_action_result(result);
    }
  }
}

// ========================================================================
// Static Helper Functions
// ========================================================================

static void lock_device(void) {
  controller.is_locked = true;

  // Reset menu state when locking
  controller.nav.menu.selected_item =
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS;  // Reset to first menu item
  controller.nav.fingerprint_menu.selected_item = 0;        // Reset fingerprint menu too

  // Clear current flow before calling enter_flow to prevent it from pushing to stack
  controller.current_flow = FLOW_SCAN;

  // Clear navigation stack so unlock returns to scan
  controller.nav_stack_depth = 0;

  enter_flow(FLOW_LOCKED, NULL);
}

static void unlock_device(void) {
  controller.is_locked = false;
}

static void enter_flow(flow_id_t flow, const void* entry_data) {
  // Push to navigation stack when leaving menu for a sub-flow
  // Also push when leaving fingerprints menu for enrollment
  if ((controller.current_flow == FLOW_MENU && flow != FLOW_MENU) ||
      (controller.current_flow == FLOW_FINGERPRINTS_MENU && flow == FLOW_FINGERPRINT_MGMT)) {
    // Leaving menu or fingerprints menu, save the selection
    if (controller.nav_stack_depth < ARRAY_SIZE(controller.nav_stack)) {
      controller.nav_stack[controller.nav_stack_depth].flow = controller.current_flow;

      // Save the appropriate selection based on which flow we're leaving
      if (controller.current_flow == FLOW_MENU) {
        controller.nav_stack[controller.nav_stack_depth].saved_selection =
          controller.nav.menu.selected_item;
      } else if (controller.current_flow == FLOW_FINGERPRINTS_MENU) {
        controller.nav_stack[controller.nav_stack_depth].saved_selection =
          controller.nav.fingerprint_menu.selected_item;
      }

      LOGD("Nav stack push: depth=%d, from_flow=%d, to_flow=%d", controller.nav_stack_depth,
           controller.current_flow, flow);
      controller.nav_stack_depth++;
    }
  }

  flow_id_t previous_flow = controller.current_flow;
  controller.current_flow = flow;

  // Clear the params union before entering any new flow
  memset(&controller.show_screen.params, 0, sizeof(controller.show_screen.params));

  // Reset menu selection only when entering menu directly from scan (not from a submenu)
  if (flow == FLOW_MENU && previous_flow == FLOW_SCAN) {
    controller.nav.menu.selected_item = fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS;
  }

  // Call flow's on_enter handler to set up initial screen
  const flow_handler_t* handler = flow_handlers[flow];
  if (handler && handler->on_enter) {
    handler->on_enter(&controller, entry_data);
  }

  // Always use standard fade transition (flows can update screen later if needed)
  display_controller_show_screen(&controller, controller.show_screen.which_params,
                                 fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                 TRANSITION_DURATION_STANDARD);

  // Query fingerprint status when entering menu
  // This ensures status is fresh before user navigates to fingerprints submenu
  if (flow == FLOW_MENU) {
    display_controller_query_fingerprint_status();
  }
}

// Process flow action results - handles navigation, exit, or no-op
static void handle_flow_action_result(flow_action_result_t result) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];

  switch (result.type) {
    case FLOW_RESULT_HANDLED:
      // Flow handled internally, nothing to do
      break;

    case FLOW_RESULT_EXIT_FLOW:
      // Exit current flow and return to caller (or scan if no caller)
      if (handler && handler->on_exit) {
        handler->on_exit(&controller);
      }

      // Pop nav stack and return to caller flow
      if (controller.nav_stack_depth > 0) {
        controller.nav_stack_depth--;
        flow_id_t return_flow = controller.nav_stack[controller.nav_stack_depth].flow;

        // Restore menu selection if returning to MENU or FINGERPRINTS_MENU
        if (return_flow == FLOW_MENU) {
          controller.nav.menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
          LOGD("Restored menu selection to %d (exiting flow, stack depth was %d)",
               controller.nav.menu.selected_item, controller.nav_stack_depth + 1);
        } else if (return_flow == FLOW_FINGERPRINTS_MENU) {
          controller.nav.fingerprint_menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
          LOGD("Restored fingerprint menu selection to %d (exiting flow, stack depth was %d)",
               controller.nav.fingerprint_menu.selected_item, controller.nav_stack_depth + 1);
        }

        enter_flow(return_flow, NULL);
      } else {
        // No caller on stack, return to appropriate idle state based on lock status
        enter_flow(controller.is_locked ? FLOW_LOCKED : FLOW_SCAN, NULL);
      }
      break;

    case FLOW_RESULT_NAVIGATE:
      // Exit current flow, enter new flow with data
      if (handler && handler->on_exit) {
        handler->on_exit(&controller);
      }

      // Check if enter_flow will push to stack (to avoid pop+push which overwrites)
      bool will_push = (controller.current_flow == FLOW_MENU && result.target_flow != FLOW_MENU) ||
                       (controller.current_flow == FLOW_FINGERPRINTS_MENU &&
                        result.target_flow == FLOW_FINGERPRINT_MGMT);

      // Only pop if enter_flow won't push (to avoid overwriting stack entries)
      if (!will_push && controller.nav_stack_depth > 0) {
        controller.nav_stack_depth--;
        // Restore menu selection from the entry we just popped
        if (result.target_flow == FLOW_MENU) {
          uint8_t old_selection = controller.nav.menu.selected_item;
          controller.nav.menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
          LOGD("Restored menu selection from %d to %d (stack depth was %d)", old_selection,
               controller.nav.menu.selected_item, controller.nav_stack_depth + 1);
        } else if (result.target_flow == FLOW_FINGERPRINTS_MENU) {
          uint8_t old_selection = controller.nav.fingerprint_menu.selected_item;
          controller.nav.fingerprint_menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
          LOGD("Restored fingerprint menu selection from %d to %d (stack depth was %d)",
               old_selection, controller.nav.fingerprint_menu.selected_item,
               controller.nav_stack_depth + 1);
        }
      }

      // Pass data pointer from result union
      const void* entry_data = result.has_data ? &result.data : NULL;
      enter_flow(result.target_flow, entry_data);
      break;
  }
}

// Wrapper for flows to update their own screen (enforces ownership)
void flow_update_current_screen(display_controller_t* controller,
                                fwpb_display_transition transition, uint32_t duration_ms) {
  // Flow can only update its own screen (already set in on_enter via which_params)
  // Controller validates we're in a flow
  if (!in_flow()) {
    LOGE("Attempted to update screen outside of flow");
    return;
  }

  display_controller_show_screen(controller, controller->show_screen.which_params, transition,
                                 duration_ms);
}

// Legacy flow functions removed - all flows now use on_action() handlers

static void refresh_screen(void) {
  // Re-display current screen with updated params (for page navigation)
  // Use slide transitions for better visual feedback during navigation
  fwpb_display_transition transition = fwpb_display_transition_DISPLAY_TRANSITION_NONE;

  display_controller_show_screen(&controller, controller.show_screen.which_params, transition,
                                 TRANSITION_DURATION_STANDARD);
}

void display_controller_show_screen(display_controller_t* ctrl, pb_size_t params_tag,
                                    fwpb_display_transition transition, uint32_t duration_ms) {
  if (!ctrl) {
    return;
  }

  // Don't allow showing screens until initial screen has been displayed
  if (!ctrl->initial_screen_shown) {
    return;
  }

  // LOGI("Showing screen: params_tag=%lu, transition=%d", (unsigned long)params_tag, transition);

  // Safety check: Validate that we're in a proper state to show this screen
  bool valid_state = false;

  switch (params_tag) {
    case fwpb_display_show_screen_locked_tag:
      // Allow locked screen when locked OR when in FLOW_LOCKED (for unlock animation)
      valid_state = ctrl->is_locked || ctrl->current_flow == FLOW_LOCKED;
      break;
    case fwpb_display_show_screen_scan_tag:
      // Scan screen valid when unlocked
      valid_state = !ctrl->is_locked;
      break;
    case fwpb_display_show_screen_success_tag:
    case fwpb_display_show_screen_error_tag:
      // These can be shown from any state as temporary feedback
      valid_state = true;
      break;
    case fwpb_display_show_screen_mfg_tag:
      // Manufacturing screen can be shown directly
      valid_state = true;
      break;
    case fwpb_display_show_screen_test_gesture_tag:
    case fwpb_display_show_screen_test_scroll_tag:
    case fwpb_display_show_screen_test_pin_pad_tag:
    case fwpb_display_show_screen_test_carousel_tag:
    case fwpb_display_show_screen_test_slider_tag:
    case fwpb_display_show_screen_test_progress_tag:
      // Test screens can be shown from any state
      valid_state = true;
      break;
    default:
      // All other screens require being in a flow and accepting input
      valid_state = accepting_input();
      if (!valid_state) {
        LOGE(
          "Trying to show screen %lu but not accepting_input() (is_locked=%d, in_flow=%d, "
          "current_flow=%d)",
          (unsigned long)params_tag, ctrl->is_locked, in_flow(), ctrl->current_flow);
      }
      break;
  }

  // If invalid state, log detailed error and return
  if (!valid_state) {
    LOGE("BLOCKED: Invalid state for screen %lu", (unsigned long)params_tag);
    return;
  }

  ctrl->show_screen.which_params = params_tag;

  // Update the controller's show_screen struct with new transition and duration
  ctrl->show_screen.transition = transition;
  ctrl->show_screen.duration_ms = duration_ms;
  // Note: which_params is already set by the caller

  // Set display flags (includes rotation setting from board_id)
  ctrl->show_screen.flags = s_display_flags;

  // Create command with the full show_screen struct
  fwpb_display_command cmd = {.which_command = fwpb_display_command_show_screen_tag,
                              .command = {.show_screen = ctrl->show_screen}};

  display_controller_send_command(&cmd);
}

void display_controller_set_rotation(bool rotate_180) {
  if (rotate_180) {
    s_display_flags |= fwpb_display_flag_DISPLAY_FLAG_ROTATE_180;
  } else {
    s_display_flags &= ~fwpb_display_flag_DISPLAY_FLAG_ROTATE_180;
  }
}

// ========================================================================
// Display Action Handlers
// ========================================================================
void display_controller_handle_action_approve(void) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_action) {
    flow_action_result_t result = handler->on_action(
      &controller, fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
    handle_flow_action_result(result);
  }
}

void display_controller_handle_action_cancel(void) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_action) {
    flow_action_result_t result = handler->on_action(
      &controller, fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL, 0);
    handle_flow_action_result(result);
  }
}

void display_controller_handle_action_back(void) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_action) {
    flow_action_result_t result = handler->on_action(
      &controller, fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK, 0);
    handle_flow_action_result(result);
  }
}

void display_controller_handle_action_exit(void) {
  display_controller_handle_action_exit_with_data(0);
}

void display_controller_handle_action_exit_with_data(uint32_t data) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_action) {
    flow_action_result_t result = handler->on_action(
      &controller, fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT, data);
    handle_flow_action_result(result);
  }
}

void display_controller_handle_action_menu(void) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_action) {
    flow_action_result_t result = handler->on_action(
      &controller, fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU, 0);
    handle_flow_action_result(result);
  }
}

void display_controller_handle_action_lock_device(void) {
  lock_device();
}

void display_controller_handle_action_power_off(void) {
#ifdef EMBEDDED_BUILD
  ipc_send_empty(sysinfo_port, IPC_SYSINFO_POWER_OFF);
#endif
  display_controller_handle_action_exit();
}

void display_controller_handle_action_start_enrollment(void) {
  LOGI("handle_action_start_enrollment: current_flow=%d", controller.current_flow);
  if (controller.current_flow != FLOW_FINGERPRINT_MGMT) {
    LOGI("Not in FINGERPRINT_MGMT flow, entering it");
    enter_flow(FLOW_FINGERPRINT_MGMT, NULL);
  } else {
    LOGI("Already in FINGERPRINT_MGMT flow, triggering enrollment");

#ifdef EMBEDDED_BUILD
    // Trigger actual biometric enrollment via auth task
    static SHARED_TASK_DATA auth_start_fingerprint_enrollment_internal_t cmd;
    cmd.index = controller.nav.fingerprint.slot_index;
    strncpy(cmd.label, "Finger", sizeof(cmd.label) - 1);
    ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_START_FINGERPRINT_ENROLLMENT_INTERNAL);
    LOGI("Sent IPC to start enrollment at index %d", cmd.index);
#endif

    // Also send event to update UI
    const flow_handler_t* handler = flow_handlers[controller.current_flow];
    if (handler && handler->on_event) {
      flow_action_result_t result =
        handler->on_event(&controller, UI_EVENT_ENROLLMENT_START, NULL, 0);
      handle_flow_action_result(result);
    }
  }
}

void display_controller_handle_action_delete_fingerprint(uint8_t fingerprint_index) {
  if (fingerprint_index < 3) {
    display_controller_delete_fingerprint(fingerprint_index);
  }
}
