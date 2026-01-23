#include "assert.h"
#include "attributes.h"
#include "auth.h"
#include "board_id.h"
#include "button.h"
#include "coproc_power.h"
#include "display_controller.h"
#include "ipc.h"
#include "log.h"
#include "metadata.h"
#include "rtos.h"
#include "sleep.h"
#include "sysinfo.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_backend.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "uxc.pb.h"

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

// Display state
static struct {
  ui_event_type_t current_event;
  ui_event_type_t idle_state;
  uint8_t event_data[128];
  uint32_t event_data_len;
  bool button_bypass_enabled;
  uint32_t last_wake_time_ms;
} display_state UI_TASK_DATA = {0};

#define DISPLAY_BACKEND_POLL_INTERVAL_MS 20

static void handle_touch_event(void* proto, void* UNUSED(context)) {
  fwpb_uxc_msg_device* msg = (fwpb_uxc_msg_device*)proto;
  if (!msg || msg->which_msg != fwpb_uxc_msg_device_display_touch_tag) {
    uc_free_recv_proto(proto);
    return;
  }

  sleep_refresh_power_timer();
  refresh_auth();

#ifdef MFGTEST
  fwpb_display_touch* touch = &msg->msg.display_touch;

  if (touch->event == fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_TOUCH) {
    ui_event_touch_t touch_event = {.x = touch->coord.x, .y = touch->coord.y};
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_TOUCH, (uint8_t*)&touch_event, sizeof(touch_event));
  }
#endif

  uc_free_recv_proto(proto);
}

#ifdef MFGTEST
static void handle_touch_test_status(void* proto, void* UNUSED(context)) {
  fwpb_uxc_msg_device* msg = (fwpb_uxc_msg_device*)proto;
  if (msg && (msg->which_msg == fwpb_uxc_msg_device_mfg_touch_test_status_tag)) {
    mfgtest_touch_test_status_payload_t status = {
      .boxes_remaining = (uint16_t)msg->msg.mfg_touch_test_status.boxes_remaining};
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_TOUCH_TEST_STATUS, (uint8_t*)&status, sizeof(status));
  }
  uc_free_recv_proto(proto);
}
#endif

static void handle_display_action(void* proto, void* UNUSED(context)) {
  fwpb_uxc_msg_device* msg = (fwpb_uxc_msg_device*)proto;
  if (!msg || msg->which_msg != fwpb_uxc_msg_device_display_action_tag) {
    uc_free_recv_proto(proto);
    return;
  }

  sleep_refresh_power_timer();

  fwpb_display_action* action = &msg->msg.display_action;

  // Send IPC message to UI task
  static SHARED_TASK_DATA ui_display_action_t action_msg;
  action_msg.action = (uint32_t)action->action;
  action_msg.data = (uint32_t)action->data;
  ipc_send(ui_port, &action_msg, sizeof(action_msg), IPC_UI_DISPLAY_ACTION);

  uc_free_recv_proto(proto);
}

static void display_backend_init(void) {
  display_state.idle_state = UI_EVENT_IDLE;
  display_state.current_event = UI_EVENT_IDLE;

  // Handle display action events
  uc_route_register(fwpb_uxc_msg_device_display_action_tag, handle_display_action, NULL);

  // Handle touch events
  uc_route_register(fwpb_uxc_msg_device_display_touch_tag, handle_touch_event, NULL);

#ifdef MFGTEST
  // Handle touch test status updates.
  uc_route_register(fwpb_uxc_msg_device_mfg_touch_test_status_tag, handle_touch_test_status, NULL);
#endif

  coproc_power_on();

  // Initialize display controller immediately - process events while coproc boots
  display_controller_init();

  // Set display rotation based on board ID at boot
  // Touch boards need 180 degree rotation (both wake and nowake variants)
#if DISPLAY_ROTATE_180
  display_controller_set_rotation(true);
#else
  uint8_t board_id = 0;
  if (board_id_read(&board_id)) {
    bool needs_rotation =
      (board_id == W3_BOARD_ID_TOUCH_WAKE) || (board_id == W3_BOARD_ID_TOUCH_NOWAKE);
    display_controller_set_rotation(needs_rotation);
  } else {
    LOGW("board_id_read failed; using default rotation");
  }
#endif
}

static void display_backend_show_event(ui_event_type_t event) {
  display_state.current_event = event;

  display_controller_handle_ui_event(event, NULL, 0);

  // LOGD("Display event: %d", event);
}

static void display_backend_show_event_with_data(ui_event_type_t event, const uint8_t* data,
                                                 uint32_t len) {
  // Handle button bypass event - disables button event processing
  if (event == UI_EVENT_MFGTEST_BUTTON_BYPASS) {
    if (data && len == sizeof(mfgtest_button_bypass_payload_t)) {
      const mfgtest_button_bypass_payload_t* payload = (const mfgtest_button_bypass_payload_t*)data;
      display_state.button_bypass_enabled = payload->bypass_enabled;
      LOGI("UI button bypass %s", payload->bypass_enabled ? "enabled" : "disabled");
    }
    return;
  }

  display_state.current_event = event;

  // Store event data
  if (data && len > 0) {
    display_state.event_data_len =
      len > sizeof(display_state.event_data) ? sizeof(display_state.event_data) : len;
    memcpy(display_state.event_data, data, display_state.event_data_len);
  } else {
    display_state.event_data_len = 0;
  }

  display_controller_handle_ui_event(event, data, len);

  // LOGD("Display event with data: %d (len: %lu)", event, len);
}

static void display_backend_set_idle_state(ui_event_type_t idle_state) {
  display_state.idle_state = idle_state;

  // TODO: Update display idle screen based on state

  LOGI("Display idle state set to: %d", idle_state);
}

static void display_backend_clear(void) {
  display_state.current_event = UI_EVENT_IDLE;
  display_state.event_data_len = 0;
  memset(display_state.event_data, 0, sizeof(display_state.event_data));

  // TODO: Clear display
  // display_clear_screen();

  LOGI("Display cleared");
}

static void display_backend_handle_display_action(uint32_t action, uint32_t data) {
  // Dispatch to display controller based on action type
  switch (action) {
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_READY:
      display_controller_show_initial_screen();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE:
      display_controller_handle_action_approve();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL:
      display_controller_handle_action_cancel();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK:
      display_controller_handle_action_back();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT:
      display_controller_handle_action_exit_with_data(data);
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU:
      display_controller_handle_action_menu();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_LOCK_DEVICE:
      display_controller_handle_action_lock_device();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_POWER_OFF:
      display_controller_handle_action_power_off();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_START_ENROLLMENT:
      display_controller_handle_action_start_enrollment();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_DELETE_FINGERPRINT:
      display_controller_handle_action_delete_fingerprint((uint8_t)data);
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_SLEEP_READY: {
      bool ready = (data != 0);
      LOGD("UXC sleep ready action received: %d", ready);
      if (ready) {
        ipc_send_empty(sysinfo_port, IPC_SYSINFO_UXC_SLEEP_READY);
      }
      break;
    }
    default:
      LOGW("Unknown display action: %lu", action);
      break;
  }
}

static void display_backend_run(void) {
  button_update_state();

  // Process button events
  if (!display_state.button_bypass_enabled) {
    button_event_t event;
    while (button_get_event(&event)) {
      button_event_payload_t payload = {0};

      LOGI("Got button event: button=%d type=%d", event.button, event.type);

      // Map button IDs from HAL to UI layer
      if (event.button == HAL_BUTTON_LEFT) {
        payload.button = BUTTON_LEFT;
      } else if (event.button == HAL_BUTTON_RIGHT) {
        payload.button = BUTTON_RIGHT;
      } else if (event.button == HAL_BUTTON_BOTH) {
        payload.button = BUTTON_BOTH;
      } else {
        LOGW("Unknown button ID: %d", event.button);
        continue;
      }
      payload.duration_ms = event.duration_ms;

      sleep_refresh_power_timer();

      // Map event types
      switch (event.type) {
        case BUTTON_EVENT_SHORT_PRESS:
          payload.type = BUTTON_PRESS_SINGLE;
          display_controller_handle_ui_event(UI_EVENT_BUTTON, &payload, sizeof(payload));
          break;

        case BUTTON_EVENT_LONG_PRESS_START:
          payload.type = BUTTON_PRESS_LONG_START;
          display_controller_handle_ui_event(UI_EVENT_BUTTON, &payload, sizeof(payload));
          break;

        case BUTTON_EVENT_LONG_PRESS_STOP:
          payload.type = BUTTON_PRESS_LONG_STOP;
          display_controller_handle_ui_event(UI_EVENT_BUTTON, &payload, sizeof(payload));
          break;
      }
    }
  }

  display_controller_tick();

  rtos_thread_sleep_until(&display_state.last_wake_time_ms, DISPLAY_BACKEND_POLL_INTERVAL_MS);
}

// Backend operations table
static const ui_backend_ops_t display_backend_ops = {
  .init = display_backend_init,
  .show_event = display_backend_show_event,
  .show_event_with_data = display_backend_show_event_with_data,
  .set_idle_state = display_backend_set_idle_state,
  .clear = display_backend_clear,
  .handle_display_action = display_backend_handle_display_action,
  .run = display_backend_run,
};

// Backend registration function
const ui_backend_ops_t* ui_backend_get(void) {
  return &display_backend_ops;
}
