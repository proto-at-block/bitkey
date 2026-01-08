#include "display_controller.h"

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

extern secure_bool_t refresh_auth(void);

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
static void enter_flow(flow_id_t flow, const void* data);
static void flow_approve(void);
static void flow_cancel(void);
static void flow_exit(void);
static void refresh_screen(void);
static bool handle_global_buttons(display_controller_t* ctrl, const button_event_payload_t* event);

// Global controller instance
display_controller_t UI_TASK_DATA controller = {
  .is_locked = true,  // Start locked
  .current_flow = FLOW_ONBOARDING,
  .previous_flow = FLOW_COUNT,  // No previous flow initially
  .show_screen.which_params = fwpb_display_show_screen_onboarding_tag,
  .nav = {.generic = {0}},
};

static void display_controller_trigger_enrollment(uint8_t index) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_DATA auth_start_fingerprint_enrollment_internal_t cmd;
  cmd.index = index;

  // Labels managed by phone - simply write generic label at creation.
  snprintf(cmd.label, sizeof(cmd.label), "Fingerprint %d", index + 1);

  ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_START_FINGERPRINT_ENROLLMENT_INTERNAL);
#endif
}

static void display_controller_query_fingerprint_status(void) {
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

// Flow handlers with new interface
static const flow_handler_t menu_handler = {
  .on_enter = display_controller_menu_on_enter,
  .on_exit = display_controller_menu_on_exit,
  .on_button_press = display_controller_menu_on_button_press,
  .on_tick = display_controller_menu_on_tick,
  .on_event = NULL,
};

static const flow_handler_t money_movement_handler = {
  .on_enter = display_controller_money_movement_on_enter,
  .on_exit = display_controller_money_movement_on_exit,
  .on_button_press = display_controller_money_movement_on_button_press,
  .on_tick = display_controller_money_movement_on_tick,
  .on_event = NULL,
};

static const flow_handler_t brightness_handler = {
  .on_enter = display_controller_brightness_on_enter,
  .on_exit = display_controller_brightness_on_exit,
  .on_button_press = display_controller_brightness_on_button_press,
  .on_tick = display_controller_brightness_on_tick,
  .on_event = NULL,
};

static const flow_handler_t info_handler = {
  .on_enter = display_controller_info_on_enter,
  .on_exit = display_controller_info_on_exit,
  .on_button_press = display_controller_info_on_button_press,
  .on_tick = display_controller_info_on_tick,
  .on_event = NULL,
};

static const flow_handler_t onboarding_handler = {
  .on_enter = display_controller_onboarding_on_enter,
  .on_exit = display_controller_onboarding_on_exit,
  .on_button_press = display_controller_onboarding_on_button_press,
  .on_tick = display_controller_onboarding_on_tick,
  .on_event = NULL,
};

static const flow_handler_t fingerprint_handler = {
  .on_enter = display_controller_fingerprint_on_enter,
  .on_exit = display_controller_fingerprint_on_exit,
  .on_button_press = display_controller_fingerprint_on_button_press,
  .on_tick = display_controller_fingerprint_on_tick,
  .on_event = display_controller_fingerprint_on_event,
};

static const flow_handler_t firmware_update_handler = {
  .on_enter = display_controller_firmware_update_on_enter,
  .on_exit = display_controller_firmware_update_on_exit,
  .on_button_press = display_controller_firmware_update_on_button_press,
  .on_tick = display_controller_firmware_update_on_tick,
  .on_event = display_controller_firmware_update_on_event,
};

static const flow_handler_t fingerprint_menu_handler = {
  .on_enter = display_controller_fingerprint_menu_on_enter,
  .on_exit = display_controller_fingerprint_menu_on_exit,
  .on_button_press = display_controller_fingerprint_menu_on_button_press,
  .on_tick = display_controller_fingerprint_menu_on_tick,
  .on_event = NULL,
};

static const flow_handler_t fingerprint_remove_handler = {
  .on_enter = display_controller_fingerprint_remove_on_enter,
  .on_exit = display_controller_fingerprint_remove_on_exit,
  .on_button_press = display_controller_fingerprint_remove_on_process,
  .on_tick = display_controller_fingerprint_remove_on_tick,
  .on_event = NULL,
};

static const flow_handler_t mfg_handler = {
  .on_enter = display_controller_mfg_on_enter,
  .on_exit = display_controller_mfg_on_exit,
  .on_button_press = display_controller_mfg_on_button_press,
  .on_tick = display_controller_mfg_on_tick,
  .on_event = display_controller_mfg_on_event,
};

// Array mapping flow IDs to flow handlers
static const flow_handler_t* flow_handlers[FLOW_COUNT] = {
  [FLOW_ONBOARDING] = &onboarding_handler,
  [FLOW_MENU] = &menu_handler,
  [FLOW_TRANSACTION] = &money_movement_handler,
  [FLOW_FINGERPRINT_MGMT] = &fingerprint_handler,
  [FLOW_FINGERPRINTS_MENU] = &fingerprint_menu_handler,
  [FLOW_FINGERPRINT_REMOVE] = &fingerprint_remove_handler,
  [FLOW_RECOVERY] = NULL,  // Future
  [FLOW_FIRMWARE_UPDATE] = &firmware_update_handler,
  [FLOW_WIPE] = NULL,                // Future
  [FLOW_PRIVILEGED_ACTIONS] = NULL,  // Future
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

  if (onboarding_complete() != SECURE_TRUE) {
    // Start onboarding flow
    controller.is_locked = false;
    controller.current_flow = FLOW_ONBOARDING;
    controller.show_screen.which_params = fwpb_display_show_screen_onboarding_tag;
  } else {
    // Device is locked, prepare lock screen params
    controller.is_locked = true;
    controller.current_flow = FLOW_COUNT;
    controller.show_screen.which_params = fwpb_display_show_screen_locked_tag;
  }
}

void display_controller_tick(void) {
  // Handle flow ticks if in flow
  if (in_flow()) {
    const flow_handler_t* handler = flow_handlers[controller.current_flow];
    if (handler && handler->on_tick) {
      handler->on_tick(&controller);
    }

    // Check if tick handler requested flow exit
    if (controller.pending_flow_exit) {
      controller.pending_flow_exit = false;
      flow_exit();
    }
  }
}

void display_controller_show_initial_screen(void) {
  LOGI("UXC ready received, showing initial screen at %ldms", rtos_thread_systime());
  LOGI("Initial screen which_params = %d", (int)controller.show_screen.which_params);
  controller.initial_screen_shown = true;
  refresh_screen();
}

void display_controller_handle_ui_event(ui_event_type_t event, const void* data, uint32_t len) {
  switch (event) {
    case UI_EVENT_BUTTON: {
      if (data && len == sizeof(button_event_payload_t)) {
        const button_event_payload_t* button_event = (const button_event_payload_t*)data;

        // LOGD("Button event: button=%d, type=%d, locked=%d, flow=%d",
        //      button_event->button, button_event->type, controller.is_locked,
        //      controller.current_flow);

        // Try global button handlers first
        if (handle_global_buttons(&controller, button_event)) {
          LOGD("Event handled globally");
          return;  // Event handled globally
        }

        // If accepting input, pass to flow handler and process returned action
        if (accepting_input()) {
          const flow_handler_t* handler = flow_handlers[controller.current_flow];
          if (handler && handler->on_button_press) {
            flow_action_t action = handler->on_button_press(&controller, button_event);
            LOGD("Flow handler returned action: %d", action);

            // Process the action
            switch (action) {
              case FLOW_ACTION_APPROVE:
                flow_approve();
                break;
              case FLOW_ACTION_CANCEL:
                flow_cancel();
                break;
              case FLOW_ACTION_REFRESH:
                refresh_screen();
                break;
              case FLOW_ACTION_EXIT:
                flow_exit();
                break;
              case FLOW_ACTION_START_ENROLLMENT:
                display_controller_trigger_enrollment(controller.nav.fingerprint.slot_index);
                break;
              case FLOW_ACTION_QUERY_FINGERPRINTS:
                display_controller_query_fingerprint_status();
                break;
              case FLOW_ACTION_DELETE_FINGERPRINT:
                // Use detail_index from fingerprint_menu or fingerprint_remove nav state
                if (controller.current_flow == FLOW_FINGERPRINTS_MENU) {
                  display_controller_delete_fingerprint(
                    controller.nav.fingerprint_menu.detail_index);
                } else if (controller.current_flow == FLOW_FINGERPRINT_REMOVE) {
                  display_controller_delete_fingerprint(
                    controller.nav.fingerprint_remove.fingerprint_index);
                }
                // Query updated enrollment status after deletion
                display_controller_query_fingerprint_status();
                break;
              case FLOW_ACTION_POWER_OFF:
#ifdef EMBEDDED_BUILD
                ipc_send_empty(sysinfo_port, IPC_SYSINFO_POWER_OFF);
#endif
                flow_exit();
                break;
              case FLOW_ACTION_NONE:
              default:
                // No action needed
                break;
            }
          } else {
            LOGW("No handler or on_button_press for flow %d", controller.current_flow);
          }
        }
      }
      break;
    }

    case UI_EVENT_DISPLAY_READY: {
      display_controller_show_initial_screen();
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

    case UI_EVENT_START_SEND_TRANSACTION: {
      if (data && len == sizeof(send_transaction_data_t)) {
        struct {
          transaction_type_t type;
          send_transaction_data_t data;
        } send_wrapper;
        send_wrapper.type = TRANSACTION_TYPE_SEND;
        memcpy(&send_wrapper.data, data, sizeof(send_transaction_data_t));
        enter_flow(FLOW_TRANSACTION, &send_wrapper);
      }
      break;
    }

    case UI_EVENT_START_RECEIVE_TRANSACTION: {
      if (data && len == sizeof(receive_transaction_data_t)) {
        struct {
          transaction_type_t type;
          receive_transaction_data_t data;
        } receive_wrapper;
        receive_wrapper.type = TRANSACTION_TYPE_RECEIVE;
        memcpy(&receive_wrapper.data, data, sizeof(receive_transaction_data_t));
        enter_flow(FLOW_TRANSACTION, &receive_wrapper);
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_START: {
      // Prevents resetting the page when enrollment is triggered internally
      if (controller.current_flow != FLOW_FINGERPRINT_MGMT) {
        enter_flow(FLOW_FINGERPRINT_MGMT, data);
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_PROGRESS_GOOD:
      // 'break' intentionally omitted.
    case UI_EVENT_ENROLLMENT_PROGRESS_BAD:
      // 'break' intentionally omitted.
    case UI_EVENT_ENROLLMENT_FAILED: {
      if (controller.current_flow == FLOW_FINGERPRINT_MGMT) {
        const flow_handler_t* handler = flow_handlers[controller.current_flow];
        if (handler && handler->on_event) {
          handler->on_event(&controller, event, data, len);
        }
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_COMPLETE: {
      if (controller.current_flow == FLOW_FINGERPRINT_MGMT) {
        const flow_handler_t* handler = flow_handlers[controller.current_flow];
        if (handler && handler->on_event) {
          handler->on_event(&controller, event, data, len);
        }
        // Query updated fingerprint enrollment status after completion
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

    case UI_EVENT_FWUP_COMPLETE:
      // 'break' intentionally omitted.
    case UI_EVENT_FWUP_FAILED: {
      if (controller.current_flow == FLOW_FIRMWARE_UPDATE) {
        const flow_handler_t* handler = flow_handlers[controller.current_flow];
        if (handler && handler->on_event) {
          handler->on_event(&controller, event, data, len);
        }
      }
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

      // Then refresh screens that depend on this state
      if (controller.is_locked) {
        display_controller_lock_screen_on_event(&controller, event, data, len);
      } else if (in_flow()) {
        // Dispatch to current flow's event handler if it has one
        const flow_handler_t* handler = flow_handlers[controller.current_flow];
        if (handler && handler->on_event) {
          handler->on_event(&controller, event, data, len);
        }
      }
      break;
    }

    case UI_EVENT_MFGTEST_SHOW_SCREEN: {
      if (data && len == sizeof(mfgtest_show_screen_payload_t)) {
        const mfgtest_show_screen_payload_t* payload = (const mfgtest_show_screen_payload_t*)data;
        if (payload->test_mode != 0) {
          // Enter MFG flow to show the requested test screen
          enter_flow(FLOW_MFG, payload);
        } else {
          // test_mode == 0 means exit MFG flow
          if (controller.current_flow == FLOW_MFG) {
            flow_exit();
          }
        }
      }
      break;
    }

    case UI_EVENT_CAPTOUCH:
      // 'break' intentionally omitted.
    case UI_EVENT_MFGTEST_TOUCH:
      // 'break' intentionally omitted.
    case UI_EVENT_MFGTEST_TOUCH_TEST_STATUS:
      // 'break' intentionally omitted.
    case UI_EVENT_FINGER_DOWN_FROM_LOCKED:
      // 'break' intentionally omitted.
    case UI_EVENT_FINGER_DOWN_FROM_UNLOCKED: {
      if (controller.current_flow == FLOW_MFG) {
        const flow_handler_t* handler = flow_handlers[controller.current_flow];
        if (handler && handler->on_event) {
          handler->on_event(&controller, event, data, len);
        }
      }
      break;
    }

    default:
      break;
  }
}

// ========================================================================
// Static Helper Functions
// ========================================================================

static void lock_device(void) {
  controller.is_locked = true;
  controller.current_flow = FLOW_COUNT;
  controller.show_screen.which_params = fwpb_display_show_screen_locked_tag;

  // Reset menu state when locking
  controller.nav.menu.selected_item =
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_BACK;  // Reset to first item (Back button)
  controller.saved_menu_selection = fwpb_display_menu_item_DISPLAY_MENU_ITEM_BACK;
  controller.nav.fingerprint_menu.selected_item = 0;  // Reset fingerprint menu too
  controller.previous_flow = FLOW_COUNT;              // Clear flow history

  // Use lock screen handler to set up the screen parameters
  display_controller_lock_screen_on_enter(&controller, NULL);

  display_controller_show_screen(&controller, fwpb_display_show_screen_locked_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                 TRANSITION_DURATION_QUICK);
}

static void unlock_device(void) {
  controller.is_locked = false;
  controller.current_flow = FLOW_COUNT;
  controller.show_screen.which_params = fwpb_display_show_screen_scan_tag;
  controller.show_screen.params.scan.action =
    fwpb_display_params_scan_display_params_scan_action_TAP;
  display_controller_show_screen(&controller, fwpb_display_show_screen_scan_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                 TRANSITION_DURATION_STANDARD);
}

static void enter_flow(flow_id_t flow, const void* data) {
  const flow_id_t prev_flow = controller.current_flow;
  controller.current_flow = flow;

  // Clear the params union before entering any new flow
  memset(&controller.show_screen.params, 0, sizeof(controller.show_screen.params));

  // Call flow's on_enter handler to set up initial screen
  const flow_handler_t* handler = flow_handlers[flow];
  if (handler && handler->on_enter) {
    handler->on_enter(&controller, data);
  }

  // Determine transition based on context
  fwpb_display_transition transition = fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT;

  // Special cases for different transitions
  if (flow == FLOW_MENU) {
    if ((prev_flow == FLOW_COUNT) || (prev_flow == FLOW_MENU)) {
      // Entering the menu always slides up.
      transition = fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_UP;
    } else {
      // Returning from a sub-menu always slides right.
      transition = fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_RIGHT;
    }
  } else if ((flow == FLOW_FINGERPRINTS_MENU) && (prev_flow == FLOW_FINGERPRINT_REMOVE)) {
    // Returning to fingerprints menu from detail screen slides right.
    transition = fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_RIGHT;
  } else if (prev_flow == FLOW_MENU) {
    // Entering submenu from menu always slides left.
    transition = fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT;
  }

  // Show initial screen with appropriate transition
  display_controller_show_screen(&controller, controller.show_screen.which_params, transition,
                                 TRANSITION_DURATION_STANDARD);

  // Query fingerprint status when entering menu
  // This ensures status is fresh before user navigates to fingerprints submenu
  if (flow == FLOW_MENU) {
    display_controller_query_fingerprint_status();
  }
}

static void flow_approve(void) {
  // User pressed Verify - show scan screen waiting for NFC
  controller.show_screen.which_params = fwpb_display_show_screen_scan_tag;

  // Set appropriate scan context based on current flow
  switch (controller.current_flow) {
    case FLOW_TRANSACTION:
      controller.show_screen.params.scan.action =
        fwpb_display_params_scan_display_params_scan_action_SIGN;
      break;

    case FLOW_FIRMWARE_UPDATE:
      controller.show_screen.params.scan.action =
        fwpb_display_params_scan_display_params_scan_action_VERIFY;
      break;

    default:
      controller.show_screen.params.scan.action =
        fwpb_display_params_scan_display_params_scan_action_CONFIRM;
      break;
  }

  display_controller_show_screen(&controller, fwpb_display_show_screen_scan_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT,
                                 TRANSITION_DURATION_STANDARD);
}

static void flow_cancel(void) {
  // User pressed Cancel - exit flow
  if (!in_flow()) {
    return;
  }
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_exit) {
    handler->on_exit(&controller);
  }

  controller.current_flow = FLOW_COUNT;
  controller.show_screen.which_params = fwpb_display_show_screen_scan_tag;
  controller.show_screen.params.scan.action =
    fwpb_display_params_scan_display_params_scan_action_TAP;
  display_controller_show_screen(&controller, fwpb_display_show_screen_scan_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_RIGHT,
                                 TRANSITION_DURATION_STANDARD);
}

static void flow_exit(void) {
  // Exit flow without cancel (for onboarding, menu)
  if (!in_flow()) {
    return;
  }

  flow_id_t current_flow = controller.current_flow;
  flow_id_t prev_flow = controller.previous_flow;

  LOGD("flow_exit: current=%d, previous=%d, selected=%d", current_flow, prev_flow,
       controller.nav.fingerprint_menu.selected_item);

  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_exit) {
    handler->on_exit(&controller);
  }

  // Check if menu selected a submenu that needs to enter a new flow
  if (current_flow == FLOW_MENU && controller.nav.menu.submenu_index != 0) {
    uint8_t submenu = controller.nav.menu.submenu_index;
    controller.nav.menu.submenu_index = 0;  // Reset

    // Save the current menu selection before entering submenu
    controller.saved_menu_selection = controller.nav.menu.selected_item;

    switch (submenu) {
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS:
        controller.previous_flow = FLOW_MENU;  // Remember we came from menu
        enter_flow(FLOW_BRIGHTNESS, NULL);
        return;
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS:
        controller.previous_flow = FLOW_MENU;  // Remember we came from menu
        enter_flow(FLOW_FINGERPRINTS_MENU, NULL);
        return;
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT:
        // Enter info flow for About screen
        controller.nav.info.showing_regulatory = false;
        controller.previous_flow = FLOW_MENU;  // Remember we came from menu
        enter_flow(FLOW_INFO, &controller.device_info);
        return;
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY:
        // Enter info flow for Regulatory screen
        controller.nav.info.showing_regulatory = true;
        controller.previous_flow = FLOW_MENU;  // Remember we came from menu
        enter_flow(FLOW_INFO, NULL);
        return;
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE:
        lock_device();
        return;
#ifdef MFGTEST
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST:
        // Show test gesture screen directly (not a flow, just a screen)
        controller.current_flow = FLOW_COUNT;
        controller.show_screen.which_params = fwpb_display_show_screen_test_gesture_tag;
        controller.show_screen.transition = fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT;
        controller.show_screen.duration_ms = TRANSITION_DURATION_STANDARD;
        controller.previous_flow = FLOW_MENU;
        controller.current_flow = FLOW_MENU;
        display_controller_show_screen(&controller, fwpb_display_show_screen_test_gesture_tag,
                                       fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT,
                                       TRANSITION_DURATION_STANDARD);
        return;
#endif
      default:
        break;
    }
  }

  // Check if fingerprints menu is exiting (regardless of where we came from)
  if (current_flow == FLOW_FINGERPRINTS_MENU) {
    // Check what was selected in the fingerprints menu
    if (controller.nav.fingerprint_menu.selected_item > 0 &&
        controller.nav.fingerprint_menu.selected_item <=
          ARRAY_SIZE(controller.fingerprint_enrolled)) {
      // A fingerprint slot was selected
      uint8_t index = controller.nav.fingerprint_menu.selected_item - 1;

      if (controller.fingerprint_enrolled[index]) {
        // Transition to fingerprint detail screen for enrolled fingerprint
        controller.previous_flow = FLOW_FINGERPRINTS_MENU;
        // Pass the fingerprint index directly as a uint8_t
        uint8_t fingerprint_index = index;
        enter_flow(FLOW_FINGERPRINT_REMOVE, &fingerprint_index);
        return;
      } else {
        // Start fingerprint enrollment for empty slot
        LOGD("Starting fingerprint enrollment for slot %d", index + 1);
        controller.previous_flow = FLOW_FINGERPRINTS_MENU;
        // Set the slot index before entering enrollment flow
        controller.nav.fingerprint.slot_index = index;
        enter_flow(FLOW_FINGERPRINT_MGMT, NULL);
        return;
      }
    } else {
      // Back button was selected - return to main menu
      LOGD("Returning to main menu from fingerprints menu");
      controller.nav.menu.selected_item = controller.saved_menu_selection;
      enter_flow(FLOW_MENU, NULL);
      controller.previous_flow = FLOW_COUNT;  // Clear after entering
      return;
    }
  }

  // Check if we should return to a previous flow from other submenus
  if (prev_flow == FLOW_MENU && (current_flow == FLOW_INFO || current_flow == FLOW_BRIGHTNESS)) {
    // Restore the saved menu selection
    controller.nav.menu.selected_item = controller.saved_menu_selection;
    // Keep previous_flow set so enter_flow knows we're returning from a submenu
    enter_flow(FLOW_MENU, NULL);
    controller.previous_flow = FLOW_COUNT;  // Clear after entering
    return;
  }

  // Check if returning from fingerprint detail or enrollment to fingerprints menu
  if (prev_flow == FLOW_FINGERPRINTS_MENU &&
      (current_flow == FLOW_FINGERPRINT_MGMT || current_flow == FLOW_FINGERPRINT_REMOVE)) {
    // Return to fingerprints menu after enrollment or detail view
    if (current_flow == FLOW_FINGERPRINT_REMOVE) {
      // Set previous_flow so the menu knows where we came from
      controller.previous_flow = FLOW_FINGERPRINT_REMOVE;
      enter_flow(FLOW_FINGERPRINTS_MENU, NULL);
    } else {
      // Set previous_flow so the menu knows we're coming from enrollment
      controller.previous_flow = FLOW_FINGERPRINT_MGMT;
      enter_flow(FLOW_FINGERPRINTS_MENU, NULL);
    }
    // After entering, set it back to MENU for future navigation
    controller.previous_flow = FLOW_MENU;
    return;
  }

  // Special case: After onboarding, go to fingerprint enrollment
  if (current_flow == FLOW_ONBOARDING) {
    LOGD("Onboarding complete -> entering fingerprint enrollment");
    controller.previous_flow = FLOW_COUNT;  // Clear so fingerprint slides left
    enter_flow(FLOW_FINGERPRINT_MGMT, NULL);
    return;
  }

  // Special case: After fingerprint enrollment, go to menu
  if (current_flow == FLOW_FINGERPRINT_MGMT) {
    LOGD("Fingerprint enrollment complete -> entering menu");
    controller.previous_flow = FLOW_COUNT;  // Clear so menu slides left
    enter_flow(FLOW_MENU, NULL);
    return;
  }

  // Safety check: Never leave the controller in a flow state without a proper flow
  if (in_flow()) {
    LOGW("Exiting flow but still in_flow(), resetting");
  }

  controller.previous_flow = FLOW_COUNT;  // Clear previous flow
  controller.current_flow = FLOW_COUNT;   // Clear current flow
  controller.show_screen.which_params = fwpb_display_show_screen_scan_tag;
  controller.show_screen.params.scan.action =
    fwpb_display_params_scan_display_params_scan_action_TAP;
  display_controller_show_screen(&controller, fwpb_display_show_screen_scan_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_DOWN,
                                 TRANSITION_DURATION_STANDARD);
}

static void refresh_screen(void) {
  // Re-display current screen with updated params (for page navigation)
  // Use slide transitions for better visual feedback during navigation
  fwpb_display_transition transition = fwpb_display_transition_DISPLAY_TRANSITION_NONE;

  display_controller_show_screen(&controller, controller.show_screen.which_params, transition,
                                 TRANSITION_DURATION_QUICK);
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

  // Safety check: Validate that we're in a proper state to show this screen
  bool valid_state = false;

  switch (params_tag) {
    case fwpb_display_show_screen_locked_tag:
      valid_state = ctrl->is_locked;
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
        LOGE("Trying to show screen %lu but not accepting_input()", (unsigned long)params_tag);
      }
      break;
  }

  // If invalid state, log detailed error and return
  if (!valid_state) {
    LOGE("Invalid state for screen %lu (is_locked=%d, in_flow=%d)", (unsigned long)params_tag,
         ctrl->is_locked, in_flow());
    return;
  }

  ctrl->show_screen.which_params = params_tag;

  // Update the controller's show_screen struct with new transition and duration
  ctrl->show_screen.transition = transition;
  ctrl->show_screen.duration_ms = duration_ms;
  // Note: which_params is already set by the caller

  // Create command with the full show_screen struct
  fwpb_display_command cmd = {.which_command = fwpb_display_command_show_screen_tag,
                              .command = {.show_screen = ctrl->show_screen}};

  display_controller_send_command(&cmd);
}

static bool handle_global_buttons(display_controller_t* ctrl, const button_event_payload_t* event) {
  // R-long press = lock device (works from any state)
  if (event->type == BUTTON_PRESS_LONG_STOP && event->button == BUTTON_RIGHT) {
    lock_device();
    return true;
  }

#ifdef MFGTEST
  // L-long press = start run-in test (works from any state, mfgtest builds only)
  if (event->type == BUTTON_PRESS_LONG_STOP && event->button == BUTTON_LEFT) {
    LOGI("Starting run-in test via long-left button");
    enter_flow(FLOW_MFG, NULL);
    return true;
  }
#endif

  // R-single press = menu access (only when unlocked and not in a flow)
  if ((event->type == BUTTON_PRESS_SINGLE) && (event->button == BUTTON_RIGHT) &&
      (!ctrl->is_locked && !in_flow())) {
    enter_flow(FLOW_MENU, NULL);
    return true;
  }

  return false;  // Not handled, pass to flow handler
}
