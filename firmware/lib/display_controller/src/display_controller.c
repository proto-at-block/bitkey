#include "display_controller.h"

#include "auth.h"
#include "battery_rescale.h"
#include "display_controller_internal.h"
#include "log.h"
#include "rtos.h"
#include "secutils.h"
#include "uc.h"
#include "uxc.pb.h"
#ifdef EMBEDDED_BUILD
#include "ipc.h"
#include "onboarding.h"
#include "power.h"
#include "sysevent.h"
#endif

#include <arithmetic.h>
#include <attributes.h>
#include <stdio.h>
#include <string.h>

bool display_controller_update_power_off_send_failures(const fwpb_display_command* cmd,
                                                       bool is_plugged_in, uint8_t* failure_count) {
  if (failure_count == NULL) {
    return false;
  }

  if ((cmd == NULL) || (cmd->which_command != fwpb_display_command_show_screen_tag) ||
      (cmd->command.show_screen.which_params != fwpb_display_show_screen_power_off_tag) ||
      !is_plugged_in) {
    *failure_count = 0;
    return false;
  }

  if (*failure_count >= DISPLAY_POWER_OFF_RESET_THRESHOLD) {
    return false;
  }

  (*failure_count)++;
  return *failure_count == DISPLAY_POWER_OFF_RESET_THRESHOLD;
}

#ifdef EMBEDDED_BUILD
static SHARED_TASK_BSS uint8_t s_power_off_send_failures = 0;

static void display_controller_reset_power_off_send_failures(void) {
  s_power_off_send_failures = 0;
}

static bool display_controller_track_power_off_send_failure(const fwpb_display_command* cmd) {
  return display_controller_update_power_off_send_failures(cmd, power_is_plugged_in(),
                                                           &s_power_off_send_failures);
}
#else
static void display_controller_reset_power_off_send_failures(void) {}
#endif

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
    LOGE("UC proto alloc fail");
    return fwpb_display_result_DISPLAY_RESULT_ERROR;
  }

  // Set message type to display command
  msg->which_msg = fwpb_uxc_msg_host_display_cmd_tag;

  // Copy the display command directly (it's already in protobuf format)
  memcpy(&msg->msg.display_cmd, cmd, sizeof(fwpb_display_command));

  // Display transitions are latency-sensitive and can come in bursts during
  // rapid UI navigation, so request an immediate ACK from the UXC.
  bool success = uc_send_immediate(msg);

  if (!success) {
    if (display_controller_track_power_off_send_failure(cmd)) {
      LOGW("Reset MCUs: pwr-off disp send fail");
      sysevent_set(SYSEVENT_FORCE_POWER_OFF_RESET);
    }
    if (cmd->which_command == fwpb_display_command_show_screen_tag) {
      LOGE("Display cmd fail: command=%u show_screen=%u", (unsigned)cmd->which_command,
           (unsigned)cmd->command.show_screen.which_params);
    } else {
      LOGE("Display cmd fail: command=%u", (unsigned)cmd->which_command);
    }
    return fwpb_display_result_DISPLAY_RESULT_ERROR;
  }

  display_controller_reset_power_off_send_failures();
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
static void mark_device_locked(void);
static void enter_flow(flow_id_t flow, const void* entry_data, bool clear_nav_stack);
static void handle_flow_action_result(flow_action_result_t result);
static void refresh_screen(void);
static bool display_controller_menu_item_visible(fwpb_display_menu_item item);

// Global controller instance
display_controller_t UI_TASK_DATA controller = {
  .is_locked = true,  // Start locked
  .current_flow = FLOW_ONBOARDING,
  .menu_root_return_flow = FLOW_SCAN,
  .show_screen.which_params = fwpb_display_show_screen_onboarding_tag,
  .nav_stack_depth = 0,
};

// Display flags sent with every show_screen command
static SHARED_TASK_DATA uint32_t s_display_flags = fwpb_display_flag_DISPLAY_FLAG_ROTATE_180;

void display_controller_query_fingerprint_status(void) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_BSS auth_get_enrolled_fingerprints_internal_t cmd;

  ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_GET_ENROLLED_FINGERPRINTS_INTERNAL);
#endif
}

static void display_controller_delete_fingerprint(uint8_t index) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_BSS auth_delete_fingerprint_internal_t cmd;
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

#ifdef MFGTEST
static const flow_handler_t mfg_handler = {
  .on_enter = display_controller_mfg_on_enter,
  .on_exit = display_controller_mfg_on_exit,
  .on_tick = display_controller_mfg_on_tick,
  .on_event = display_controller_mfg_on_event,
  .on_action = display_controller_mfg_on_action,
};
#endif

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

static const flow_handler_t confirmation_handler = {
  .on_enter = display_controller_confirmation_on_enter,
  .on_exit = display_controller_confirmation_on_exit,
  .on_tick = display_controller_confirmation_on_tick,
  .on_event = display_controller_confirmation_on_event,
  .on_action = display_controller_confirmation_on_action,
};

static const flow_handler_t onboarding_complete_handler = {
  .on_enter = display_controller_onboarding_complete_on_enter,
  .on_exit = display_controller_onboarding_complete_on_exit,
  .on_tick = display_controller_onboarding_complete_on_tick,
  .on_event = NULL,
  .on_action = display_controller_onboarding_complete_on_action,
};

static const flow_handler_t game_handler = {
  .on_enter = display_controller_game_on_enter,
  .on_exit = display_controller_game_on_exit,
  .on_tick = display_controller_game_on_tick,
  .on_event = NULL,
  .on_action = display_controller_game_on_action,
};

static const flow_handler_t power_off_handler = {
  .on_enter = display_controller_power_off_on_enter,
  .on_exit = display_controller_power_off_on_exit,
  .on_tick = display_controller_power_off_on_tick,
  .on_event = display_controller_power_off_on_event,
  .on_action = display_controller_power_off_on_action,
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
#ifdef MFGTEST
  [FLOW_MFG] = &mfg_handler,
#endif
  [FLOW_CONFIRMATION] = &confirmation_handler,
  [FLOW_ONBOARDING_COMPLETE] = &onboarding_complete_handler,
  [FLOW_GAME] = &game_handler,
  [FLOW_POWER_OFF] = &power_off_handler,
};

// Returns true if we have a valid active flow (safe to access flow_handlers)
static inline bool in_flow(void) {
  return controller.current_flow < FLOW_COUNT;
}

// The power-off screen is terminal. Ignore subsequent interactions until sysinfo
// finishes powering down or resets the device.
static inline bool power_off_screen_active(void) {
  return controller.current_flow == FLOW_POWER_OFF;
}

static inline bool onboarding_complete_terminal_active(void) {
  return controller.current_flow == FLOW_ONBOARDING_COMPLETE;
}

static bool onboarding_complete_allows_ui_event(ui_event_type_t event) {
  switch (event) {
    case UI_EVENT_SET_DEVICE_INFO:
    case UI_EVENT_POWER_OFF:
    case UI_EVENT_BATTERY_SOC:
    case UI_EVENT_CHARGING:
    case UI_EVENT_CHARGING_FINISHED:
    case UI_EVENT_CHARGING_FINISHED_PERSISTENT:
    case UI_EVENT_CHARGING_UNPLUGGED:
      return true;
    default:
      return false;
  }
}

// Returns true if in a flow and accepting user input
static inline bool accepting_input(void) {
  return !controller.is_locked && in_flow();
}

static bool display_controller_root_menu_returns_to_onboarding(void) {
  return controller.current_flow == FLOW_MENU && controller.nav_stack_depth == 0 &&
         controller.menu_root_return_flow == FLOW_ONBOARDING;
}

static bool display_controller_in_onboarding_owned_menu_session(void) {
  if (controller.menu_root_return_flow != FLOW_ONBOARDING) {
    return false;
  }

  if (controller.current_flow == FLOW_MENU) {
    return true;
  }

  for (uint8_t i = 0; i < controller.nav_stack_depth; i++) {
    if (controller.nav_stack[i].flow == FLOW_MENU) {
      return true;
    }
  }

  return false;
}

static void display_controller_begin_root_menu_session(flow_id_t origin_flow) {
  controller.menu_root_return_flow = (origin_flow == FLOW_ONBOARDING) ? FLOW_ONBOARDING : FLOW_SCAN;
}

static bool display_controller_in_pre_onboarding_context(void) {
  return controller.current_flow == FLOW_ONBOARDING ||
         display_controller_in_onboarding_owned_menu_session();
}

static bool display_controller_menu_item_visible(fwpb_display_menu_item item) {
  switch (item) {
    case fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE:
      return display_controller_menu_show_lock_device();
    case fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS:
      return display_controller_menu_show_fingerprints();
    case fwpb_display_menu_item_DISPLAY_MENU_ITEM_BACK:
    case fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY:
      return false;
    default:
      return true;
  }
}

static bool post_onboarding_menu_item_visible(void) {
  if (display_controller_root_menu_returns_to_onboarding()) {
    return false;
  }

  return onboarding_complete() == SECURE_TRUE;
}

bool display_controller_menu_show_lock_device(void) {
  return post_onboarding_menu_item_visible();
}

bool display_controller_menu_show_fingerprints(void) {
  return post_onboarding_menu_item_visible();
}

fwpb_display_menu_item display_controller_default_root_menu_selection(void) {
  if (display_controller_menu_show_lock_device()) {
    return fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE;
  }

  return fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS;
}

fwpb_display_menu_item display_controller_normalize_menu_selection(
  fwpb_display_menu_item selected_item) {
  if (display_controller_menu_item_visible(selected_item)) {
    return selected_item;
  }

  return display_controller_default_root_menu_selection();
}

bool display_controller_lock_device_action_supported(void) {
  return !display_controller_in_pre_onboarding_context() && onboarding_complete() == SECURE_TRUE;
}

void display_controller_init(void) {
  memset(&controller, 0, sizeof(controller));
  controller.is_locked = true;
  // Initialize battery state (will be updated via UI_EVENT_BATTERY_SOC)
  controller.battery_percent = 0;
  controller.battery_percent_raw = 0;
  controller.is_charging = false;
  controller.menu_root_return_flow = FLOW_SCAN;
  controller.onboarding_resume_at_scan = false;
  controller.scan_confirm_on_enter = false;
  controller.scan_confirm_cancel_returns_to_onboarding = false;
  battery_rescale_init(&controller.battery_rescale_state);
  display_controller_reset_power_off_send_failures();
  // Initialize brightness to default (will be updated via UI_EVENT_SET_DEVICE_INFO)
  controller.show_screen.brightness_percent = 80;

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
  controller.uxc_ready = true;

  // Defer the initial screen until device info has been received,
  // so that brightness (and other settings) are correct from the start.
  if (!controller.has_device_info) {
    return;
  }

  controller.initial_screen_shown = true;
  refresh_screen();
}

void display_controller_handle_ui_event(ui_event_type_t event, const void* data, uint32_t len) {
  if (power_off_screen_active() && event != UI_EVENT_POWER_OFF &&
      event != UI_EVENT_SET_DEVICE_INFO) {
    return;
  }

  if (onboarding_complete_terminal_active() && !onboarding_complete_allows_ui_event(event)) {
    return;
  }

  switch (event) {
    case UI_EVENT_BUTTON: {
      if (data && len == sizeof(button_event_payload_t)) {
        // Touch-only UI - buttons not used for navigation
      }
      break;
    }

    case UI_EVENT_AUTH_SUCCESS: {
      if (controller.is_locked) {
        unlock_device();
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

        // If the UXC was ready before device info arrived, show the
        // initial screen now that we have the correct brightness.
        if (controller.uxc_ready && !controller.initial_screen_shown) {
          controller.initial_screen_shown = true;
          refresh_screen();
        }
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_START: {
      // Prevents resetting the page when enrollment is triggered internally
      if (controller.current_flow != FLOW_FINGERPRINT_MGMT) {
        controller.fingerprint_enrollment_is_required =
          display_controller_in_pre_onboarding_context();
        enter_flow(FLOW_FINGERPRINT_MGMT, NULL, false);
      }
      break;
    }

    case UI_EVENT_ENROLLMENT_COMPLETE: {
      if (controller.current_flow == FLOW_FINGERPRINT_MGMT &&
          controller.fingerprint_enrollment_is_required) {
        controller.onboarding_complete_pending = true;
      }

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
      }
      break;
    }

    case UI_EVENT_FWUP_CONFIRMATION:
      // 'break' intentionally omitted.
    case UI_EVENT_FWUP_START: {
      if (controller.current_flow != FLOW_FIRMWARE_UPDATE) {
        enter_flow(FLOW_FIRMWARE_UPDATE, data, true);
      }
      break;
    }

    case UI_EVENT_SHOW_MENU: {
      if (controller.current_flow != FLOW_MENU && controller.nav_stack_depth == 0) {
        display_controller_begin_root_menu_session(controller.current_flow);
      }
      enter_flow(FLOW_MENU, NULL, false);
      break;
    }

    case UI_EVENT_START_SEND_TRANSACTION: {
      if (!controller.is_locked && data && len == sizeof(send_transaction_data_t)) {
        const send_transaction_data_t* send_data = (const send_transaction_data_t*)data;
        flow_transaction_entry_data_t entry = {
          .flow = send_data->flow,  // SEND or SELF_SEND
        };
        memcpy(&entry.data.send, data, sizeof(send_transaction_data_t));
        enter_flow(FLOW_TRANSACTION, &entry, true);
      }
      break;
    }

    case UI_EVENT_START_RECEIVE_TRANSACTION: {
      if (!controller.is_locked && data && len == sizeof(receive_transaction_data_t)) {
        flow_transaction_entry_data_t entry = {
          .flow = fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE,
        };
        memcpy(&entry.data.receive, data, sizeof(receive_transaction_data_t));
        enter_flow(FLOW_TRANSACTION, &entry, true);
      }
      break;
    }

    case UI_EVENT_START_PRIVILEGED_ACTION: {
      if (!controller.is_locked && data && len == sizeof(fwpb_display_params_privileged_action)) {
        enter_flow(FLOW_PRIVILEGED_ACTIONS, data, true);
      }
      break;
    }

    case UI_EVENT_SHOW_CONFIRMATION: {
      if (!controller.is_locked && data && len == sizeof(fwpb_display_params_confirmation)) {
        enter_flow(FLOW_CONFIRMATION, data, true);
      }
      break;
    }

    case UI_EVENT_SHOW_ONBOARDING_COMPLETE: {
      if (display_controller_in_pre_onboarding_context() ||
          controller.onboarding_complete_pending) {
        controller.onboarding_complete_pending = false;
        mark_device_locked();
        enter_flow(FLOW_ONBOARDING_COMPLETE, NULL, true);
      }
      break;
    }

    case UI_EVENT_POWER_OFF:
      memset(&controller.show_screen.params, 0, sizeof(controller.show_screen.params));
      controller.current_flow = FLOW_POWER_OFF;
      display_controller_power_off_on_enter(&controller, NULL);
      display_controller_show_screen(&controller, fwpb_display_show_screen_power_off_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);
      break;

    case UI_EVENT_BATTERY_SOC: {
      if (data && len == sizeof(battery_soc_data_t)) {
        const battery_soc_data_t* battery = (const battery_soc_data_t*)data;
        controller.battery_percent_raw = battery->battery_percent;
        controller.battery_percent = battery_rescale_get_display_percent(
          &controller.battery_rescale_state, battery->battery_percent);
      }
      break;
    }

    case UI_EVENT_CHARGING: {
      controller.is_charging = true;
      battery_rescale_on_charging_started(&controller.battery_rescale_state);
      break;
    }
    case UI_EVENT_CHARGING_FINISHED:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_FINISHED_PERSISTENT: {
      controller.is_charging = true;
      controller.battery_percent = battery_rescale_on_charging_complete(
        &controller.battery_rescale_state, controller.battery_percent_raw);
      break;
    }

    case UI_EVENT_CHARGING_UNPLUGGED: {
      controller.is_charging = false;
      battery_rescale_on_unplugged(&controller.battery_rescale_state,
                                   controller.battery_percent_raw);
      controller.battery_percent = battery_rescale_get_display_percent(
        &controller.battery_rescale_state, controller.battery_percent_raw);
      break;
    }

#ifdef MFGTEST
    case UI_EVENT_MFGTEST_SHOW_SCREEN: {
      // Enter MFG flow if not already in it, passing payload as entry_data
      if (!in_flow() || controller.current_flow != FLOW_MFG) {
        enter_flow(FLOW_MFG, data, false);
      }
      break;
    }
#endif

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
  mark_device_locked();
  enter_flow(FLOW_LOCKED, NULL, true);  // clear nav stack
}

static void mark_device_locked(void) {
  controller.is_locked = true;

  // Clear authentication state - critical for security!
  // Use "without animation" since the caller handles the screen transition.
  // This also avoids a circular callback: deauthenticate -> on_lock -> UI_EVENT_AUTH_LOCKED.
  // Guard with is_authenticated() to avoid redundant deauth work when called from auth-driven
  // lock events where auth already expired.
#ifdef EMBEDDED_BUILD
  if (is_authenticated() == SECURE_TRUE) {
    deauthenticate_without_animation();
  }
#endif

  controller.nav.menu.selected_item = fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE;
  controller.nav.fingerprint_menu.selected_item = 0;
}

static void unlock_device(void) {
  controller.is_locked = false;
}

// Transitions between flows with proper lifecycle management and navigation stack control.
// Phone-initiated flows (transactions, firmware updates) clear the stack, while
// device-driven flows (menu navigation) preserve it for proper back behavior.
static void enter_flow(flow_id_t flow, const void* entry_data, bool clear_nav_stack) {
  // Exit current flow's cleanup before transitioning
  const flow_handler_t* current_handler = flow_handlers[controller.current_flow];
  if (current_handler && current_handler->on_exit) {
    current_handler->on_exit(&controller);
  }

  // Clear navigation stack if requested (phone-driven flows)
  if (clear_nav_stack) {
    controller.nav_stack_depth = 0;
  } else {
    // Push to stack when leaving menu/fingerprints menu for sub-flows
    if ((controller.current_flow == FLOW_MENU && flow != FLOW_MENU) ||
        (controller.current_flow == FLOW_FINGERPRINTS_MENU && flow == FLOW_FINGERPRINT_MGMT)) {
      // Save current selection before entering sub-flow
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

        controller.nav_stack_depth++;
      }
    }
  }

  flow_id_t previous_flow = controller.current_flow;
  controller.current_flow = flow;

  // Clear params before entering new flow
  memset(&controller.show_screen.params, 0, sizeof(controller.show_screen.params));

  // Reset menu selection when entering from a root flow (not from sub-flow)
  if (flow == FLOW_MENU && (previous_flow == FLOW_SCAN || previous_flow == FLOW_ONBOARDING)) {
    controller.nav.menu.selected_item = display_controller_default_root_menu_selection();
  }

  // Initialize flow state via on_enter handler
  const flow_handler_t* handler = flow_handlers[flow];
  if (handler && handler->on_enter) {
    handler->on_enter(&controller, entry_data);
  }

  // Show initial screen with fade transition
  display_controller_show_screen(&controller, controller.show_screen.which_params,
                                 fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                 TRANSITION_DURATION_STANDARD);

  // Refresh fingerprint status when entering menu
  if (flow == FLOW_MENU) {
    display_controller_query_fingerprint_status();
  }
}

// Processes flow action results to handle navigation, exit, or internal flow operations.
static void handle_flow_action_result(flow_action_result_t result) {
  switch (result.type) {
    case FLOW_RESULT_HANDLED: {
      // Flow handled action internally
      break;
    }

    case FLOW_RESULT_EXIT_FLOW: {
      if (!controller.is_locked && controller.current_flow == FLOW_FINGERPRINT_MGMT &&
          controller.fingerprint_enrollment_is_required) {
        controller.scan_confirm_on_enter = true;
        controller.scan_confirm_cancel_returns_to_onboarding = true;
        enter_flow(FLOW_SCAN, NULL, true);
        break;
      }

      // Exit current flow and return to caller (or idle state if no caller)
      // Pop nav stack and restore previous flow
      if (controller.nav_stack_depth > 0) {
        controller.nav_stack_depth--;
        flow_id_t return_flow = controller.nav_stack[controller.nav_stack_depth].flow;

        // Restore saved selection
        if (return_flow == FLOW_MENU) {
          controller.nav.menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
        } else if (return_flow == FLOW_FINGERPRINTS_MENU) {
          controller.nav.fingerprint_menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
        }

        enter_flow(return_flow, NULL, false);
      } else {
        // No caller on stack, return to idle state
        flow_id_t return_flow = controller.is_locked ? FLOW_LOCKED : FLOW_SCAN;

        if (!controller.is_locked && controller.current_flow == FLOW_MENU) {
          return_flow = controller.menu_root_return_flow;
          if (return_flow == FLOW_ONBOARDING) {
            controller.onboarding_resume_at_scan = true;
          }
        }

        enter_flow(return_flow, NULL, true);
      }
      break;
    }

    case FLOW_RESULT_NAVIGATE: {
      // Navigate to new flow with optional data
      // Avoid pop+push when enter_flow will push (prevents overwriting stack)
      bool will_push = (controller.current_flow == FLOW_MENU && result.target_flow != FLOW_MENU) ||
                       (controller.current_flow == FLOW_FINGERPRINTS_MENU &&
                        result.target_flow == FLOW_FINGERPRINT_MGMT);
      const void* entry_data = result.has_data ? &result.data : NULL;

      // Pop stack only if enter_flow won't push (avoids overwriting)
      if (!will_push && controller.nav_stack_depth > 0) {
        controller.nav_stack_depth--;
        // Restore selection from popped entry
        if (result.target_flow == FLOW_MENU) {
          controller.nav.menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
        } else if (result.target_flow == FLOW_FINGERPRINTS_MENU) {
          controller.nav.fingerprint_menu.selected_item =
            controller.nav_stack[controller.nav_stack_depth].saved_selection;
        }
      }

      if (result.target_flow == FLOW_MENU && controller.current_flow != FLOW_MENU &&
          controller.nav_stack_depth == 0) {
        display_controller_begin_root_menu_session(controller.current_flow);
      }

      // Enter new flow with provided data
      enter_flow(result.target_flow, entry_data, false);
      break;
    }

    case FLOW_RESULT_LOCK: {
      lock_device();
      break;
    }
  }
}

// Allows flows to update their own screen with transitions/animations.
// Enforces ownership - flows can only update the screen they initialized in on_enter.
void flow_update_current_screen(display_controller_t* controller,
                                fwpb_display_transition transition, uint32_t duration_ms) {
  if (!in_flow()) {
    LOGE("Screen update outside flow");
    return;
  }

  display_controller_show_screen(controller, controller->show_screen.which_params, transition,
                                 duration_ms);
}

static void refresh_screen(void) {
  // Re-display current screen with updated params
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
    case fwpb_display_show_screen_power_off_tag:
      // Always valid to enter the power off screen.
      valid_state = true;
      break;
    case fwpb_display_show_screen_onboarding_complete_tag:
      // This terminal screen is allowed to render while the controller is
      // otherwise treating the device as locked.
      valid_state = ctrl->current_flow == FLOW_ONBOARDING_COMPLETE;
      break;
    case fwpb_display_show_screen_mfg_tag:
      // Manufacturing screen can be shown directly
      valid_state = true;
      break;
#ifdef MFGTEST
    case fwpb_display_show_screen_touch_debug_tag:
      // Test screens can be shown from any state
      valid_state = true;
      break;
#endif
    default:
      // All other screens require being in a flow and accepting input
      valid_state = accepting_input();
      if (!valid_state) {
        LOGE("Screen %lu reject", (unsigned long)params_tag);
      }
      break;
  }

  // If invalid state, log detailed error and return
  if (!valid_state) {
    LOGE("Blocked screen %lu", (unsigned long)params_tag);
    return;
  }

  ctrl->show_screen.which_params = params_tag;

  // Update the controller's show_screen struct with new transition and duration
  ctrl->show_screen.transition = transition;
  ctrl->show_screen.duration_ms = duration_ms;
  // Note: which_params is already set by the caller

  // Set display flags (includes rotation)
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

void display_controller_debug_show_confirm_scan(void) {
  if (controller.is_locked) {
    unlock_device();
  }
  controller.show_screen.which_params = fwpb_display_show_screen_scan_tag;
  controller.show_screen.params.scan.action =
    fwpb_display_params_scan_display_params_scan_action_CONFIRM;
  controller.show_screen.params.scan.show_error = false;
  display_controller_show_screen(&controller, fwpb_display_show_screen_scan_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_NONE, 0);
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
  if (power_off_screen_active()) {
    return;
  }

  if (!display_controller_lock_device_action_supported()) {
    return;
  }

  lock_device();
}

void display_controller_handle_action_power_off(void) {
#ifdef EMBEDDED_BUILD
  // Sysinfo Task handles power off on embedded systems.
  ipc_send_empty(sysinfo_port, IPC_SYSINFO_POWER_OFF_REQUESTED);
#else
  display_controller_handle_action_exit();
#endif
}

void display_controller_handle_action_start_enrollment(void) {
  if (power_off_screen_active()) {
    return;
  }

  if (controller.current_flow != FLOW_FINGERPRINT_MGMT) {
    controller.fingerprint_enrollment_is_required = display_controller_in_pre_onboarding_context();
    enter_flow(FLOW_FINGERPRINT_MGMT, NULL, false);
  } else {
#ifdef EMBEDDED_BUILD
    // Trigger actual biometric enrollment via auth task
    static SHARED_TASK_BSS auth_start_fingerprint_enrollment_internal_t cmd;
    cmd.index = controller.nav.fingerprint.slot_index;
    (void)snprintf(cmd.label, sizeof(cmd.label), "Fingerprint %u", (unsigned)cmd.index + 1u);

    ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_START_FINGERPRINT_ENROLLMENT_INTERNAL);
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
  if (power_off_screen_active()) {
    return;
  }

  if (fingerprint_index < FINGERPRINT_SLOT_COUNT) {
    controller.fingerprint_enrolled[fingerprint_index] = false;
    memset(controller.fingerprint_labels[fingerprint_index], 0,
           sizeof(controller.fingerprint_labels[fingerprint_index]));

    display_controller_delete_fingerprint(fingerprint_index);
  }
}

void display_controller_handle_action_page_confirmed(void) {
  const flow_handler_t* handler = flow_handlers[controller.current_flow];
  if (handler && handler->on_action) {
    flow_action_result_t result = handler->on_action(
      &controller, fwpb_display_action_display_action_type_DISPLAY_ACTION_PAGE_CONFIRMED, 0);
    handle_flow_action_result(result);
  }
}
