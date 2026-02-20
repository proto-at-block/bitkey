#include "mfgtest_task_port.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "button.h"
#include "display_controller.h"
#include "ipc.h"
#include "log.h"
#include "mfgtest_task_impl.h"
#include "proto_helpers.h"
#include "rtos_thread.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_messaging.h"
#include "uxc.pb.h"
#include "wallet.pb.h"
#include "wca.h"

#include <limits.h>
#include <stdbool.h>
#include <stdint.h>

// Verify touch data response fits in a single APDU to avoid fragmentation issues.
// The max_count of 25 touch points in mfgtest.proto was chosen to keep this under the limit.
_Static_assert(fwpb_mfgtest_touch_data_rsp_size <= WCA_MAX_PROTO_SIZE,
               "mfgtest_touch_data_rsp exceeds max WCA proto size - reduce max_count in proto");

extern mfgtest_priv_t mfgtest_priv;

// Touch data collection buffer
#define MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS 500

typedef enum {
  MFGTEST_TASK_PORT_TOUCH_DATA_MODE_CIRCULAR = 0,
  MFGTEST_TASK_PORT_TOUCH_DATA_MODE_STOP_WHEN_FULL = 1
} mfgtest_task_port_touch_data_mode_t;

typedef struct {
  uint16_t x;
  uint16_t y;
  uint32_t timestamp_ms;
} mfgtest_task_port_touch_data_point_t;

static struct {
  mfgtest_task_port_touch_data_point_t points[MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS];
  uint32_t head;
  uint32_t tail;
  uint32_t count;
  uint32_t dropped;
  mfgtest_task_port_touch_data_mode_t mode;
  bool active;
  bool paused;
  bool full;
} mfgtest_task_port_touch_data_buffer = {0};

static void _mfgtest_task_port_handle_coproc_gpio_response(void* proto, void* UNUSED(context)) {
  // Pass through pointer. MfgTest task will handle free'ing the data.
  ipc_send(mfgtest_port, proto, sizeof(proto), IPC_MFGTEST_COPROC_GPIO_RESPONSE);
}

void mfgtest_task_port_init(void) {
  uc_route_register(fwpb_uxc_msg_device_mfgtest_gpio_rsp_tag,
                    _mfgtest_task_port_handle_coproc_gpio_response, NULL);
}

void mfgtest_task_port_handle_button_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_button_rsp_tag;

  fwpb_mfgtest_button_cmd* cmd = &wallet_cmd->msg.mfgtest_button_cmd;
  fwpb_mfgtest_button_rsp* rsp = &wallet_rsp->msg.mfgtest_button_rsp;

  // Default to success
  rsp->rsp_status = fwpb_mfgtest_button_rsp_mfgtest_button_rsp_status_SUCCESS;

  switch (cmd->action) {
    case fwpb_mfgtest_button_cmd_mfgtest_button_action_GET_EVENTS:
    case fwpb_mfgtest_button_cmd_mfgtest_button_action_CLEAR_EVENTS: {
      // Consume button events from the shared event buffer
      button_event_t hal_event;
      uint32_t event_count = 0;
      bool return_events =
        (cmd->action == fwpb_mfgtest_button_cmd_mfgtest_button_action_GET_EVENTS);

      // Read events from buffer, optionally converting to proto format
      while (button_get_event(&hal_event)) {
        if (return_events && event_count < sizeof(rsp->events) / sizeof(rsp->events[0])) {
          fwpb_mfgtest_button_event* proto_event = &rsp->events[event_count];

          // Map button ID
          switch (hal_event.button) {
            case HAL_BUTTON_LEFT:
              proto_event->button = fwpb_mfgtest_button_event_button_id_LEFT;
              break;
            case HAL_BUTTON_RIGHT:
              proto_event->button = fwpb_mfgtest_button_event_button_id_RIGHT;
              break;
            case HAL_BUTTON_BOTH:
              proto_event->button = fwpb_mfgtest_button_event_button_id_BOTH;
              break;
          }

          // Map event type
          switch (hal_event.type) {
            case BUTTON_EVENT_SHORT_PRESS:
              proto_event->type = fwpb_mfgtest_button_event_event_type_SHORT_PRESS;
              break;
            case BUTTON_EVENT_LONG_PRESS_START:
              proto_event->type = fwpb_mfgtest_button_event_event_type_LONG_PRESS_START;
              break;
            case BUTTON_EVENT_LONG_PRESS_STOP:
              proto_event->type = fwpb_mfgtest_button_event_event_type_LONG_PRESS_STOP;
              break;
          }

          proto_event->timestamp_ms = hal_event.timestamp_ms;
          proto_event->duration_ms = hal_event.duration_ms;
        }
        event_count++;
      }

      uint32_t max_events = sizeof(rsp->events) / sizeof(rsp->events[0]);
      rsp->events_count = return_events ? (event_count < max_events ? event_count : max_events) : 0;
      rsp->bypass_enabled = false;  // Not tracked, mfgtest knows if it enabled bypass

      LOGI("Button %s: count=%lu", return_events ? "get events" : "events cleared", event_count);
      break;
    }

    case fwpb_mfgtest_button_cmd_mfgtest_button_action_SET_UI_BYPASS: {
      // Send bypass control event (functional: disables button processing)
      mfgtest_button_bypass_payload_t bypass_payload = {
        .bypass_enabled = cmd->bypass_enabled,
      };
      UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_BUTTON_BYPASS, &bypass_payload,
                              sizeof(bypass_payload));

      // Send show screen event (visual: displays warning or exits)
      mfgtest_show_screen_payload_t screen_payload = {
        .test_mode = cmd->bypass_enabled
                       ? fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BUTTON_BYPASS_WARNING
                       : 0,  // 0 = exit
        .timeout_ms = 0,
        .custom_rgb = 0,
        .brightness_percent = 0,
      };
      UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_SHOW_SCREEN, &screen_payload,
                              sizeof(screen_payload));

      rsp->bypass_enabled = cmd->bypass_enabled;
      LOGI("Button UI bypass set to: %d", rsp->bypass_enabled);
      break;
    }

    case fwpb_mfgtest_button_cmd_mfgtest_button_action_UNSPECIFIED:
    default:
      LOGE("Unknown button action: %d", cmd->action);
      rsp->rsp_status = fwpb_mfgtest_button_rsp_mfgtest_button_rsp_status_ERROR;
      break;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_show_screen_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_show_screen_rsp_tag;

  fwpb_mfgtest_show_screen_cmd* cmd = &wallet_cmd->msg.mfgtest_show_screen_cmd;
  fwpb_mfgtest_show_screen_rsp* rsp = &wallet_rsp->msg.mfgtest_show_screen_rsp;

  // Map public mfgtest_screen_mode enum to internal display_mfg_test_mode enum
  fwpb_display_mfg_test_mode display_mode;
  switch (cmd->screen_mode) {
    case fwpb_mfgtest_show_screen_cmd_mfgtest_screen_mode_EXIT:
      display_mode = 0;  // Special case: 0 means exit
      break;
    case fwpb_mfgtest_show_screen_cmd_mfgtest_screen_mode_BURNIN_GRID:
      display_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BURNIN_GRID;
      break;
    case fwpb_mfgtest_show_screen_cmd_mfgtest_screen_mode_COLOR_BARS:
      display_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COLOR_BARS;
      break;
    case fwpb_mfgtest_show_screen_cmd_mfgtest_screen_mode_SCROLLING_H:
      display_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_SCROLLING_H;
      break;
    case fwpb_mfgtest_show_screen_cmd_mfgtest_screen_mode_CUSTOM_COLOR:
      display_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR;
      break;
    default:
      LOGE("Unknown screen mode: %d", cmd->screen_mode);
      rsp->rsp_status = fwpb_mfgtest_show_screen_rsp_mfgtest_show_screen_rsp_status_ERROR;
      proto_send_rsp(wallet_cmd, wallet_rsp);
      return;
  }

  // Send UI event to show the requested mfg test screen
  // brightness_percent: 0 = don't change, 1-100 = set brightness percent
  mfgtest_show_screen_payload_t payload = {
    .test_mode = display_mode,
    .custom_rgb = cmd->custom_rgb,
    .brightness_percent = (cmd->brightness > 100) ? 100 : (uint8_t)cmd->brightness,
  };
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_SHOW_SCREEN, &payload, sizeof(payload));

  LOGI("Show mfg screen: mode=%d, brightness=%lu", cmd->screen_mode,
       (unsigned long)cmd->brightness);
  rsp->rsp_status = fwpb_mfgtest_show_screen_rsp_mfgtest_show_screen_rsp_status_SUCCESS;

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_touch_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_touch_test_rsp_tag;

  fwpb_mfgtest_touch_test_cmd* cmd = &wallet_cmd->msg.mfgtest_touch_test_cmd;
  fwpb_mfgtest_touch_test_rsp* rsp = &wallet_rsp->msg.mfgtest_touch_test_rsp;
  switch (cmd->cmd_id) {
    case fwpb_mfgtest_touch_test_cmd_mfgtest_touch_test_cmd_id_START: {
      // Send UI event to show the touch test boxes screen.
      mfgtest_show_screen_payload_t payload = {
        .test_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_TOUCH_TEST_BOXES,
        .timeout_ms = BLK_MIN(UINT32_MAX / 1000u, cmd->timeout) * 1000u,
        .custom_rgb = 0,
        .brightness_percent = 0,
      };
      UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_SHOW_SCREEN, &payload, sizeof(payload));
      rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_SUCCESS;
      break;
    }

    case fwpb_mfgtest_touch_test_cmd_mfgtest_touch_test_cmd_id_REQUEST_DATA: {
      if (mfgtest_priv.touch_test_has_result) {
        rsp->touch_event.event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_TOUCH;
        rsp->touch_event.coord.x = mfgtest_priv.touch_test_result.x;
        rsp->touch_event.coord.y = mfgtest_priv.touch_test_result.y;
        rsp->touch_event.has_coord = true;
        rsp->has_touch_event = !mfgtest_priv.touch_test_result.timeout;
        rsp->boxes_remaining = mfgtest_priv.touch_test_result.boxes_remaining;
        rsp->timeout = mfgtest_priv.touch_test_result.timeout;

        // Determine status based on timeout and boxes_remaining
        if (mfgtest_priv.touch_test_result.timeout) {
          rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_TIMED_OUT;
        } else if (mfgtest_priv.touch_test_result.boxes_remaining == 0) {
          rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_SUCCESS;
        } else {
          rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_FAILED;
        }
        mfgtest_priv.touch_test_has_result = false;
      } else {
        rsp->has_touch_event = false;
        rsp->boxes_remaining = 0xFFFF;
        rsp->timeout = false;
        rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_FAILED;
      }

      // Exit the touch test screen (whether result available or not)
      mfgtest_show_screen_payload_t exit_payload = {
        .test_mode = 0,  // 0 = exit mfg test screen
        .timeout_ms = 0,
        .custom_rgb = 0,
        .brightness_percent = 0,
      };
      UI_SHOW_EVENT_WITH_DATA(UI_EVENT_MFGTEST_SHOW_SCREEN, &exit_payload, sizeof(exit_payload));

      break;
    }

    case fwpb_mfgtest_touch_test_cmd_mfgtest_touch_test_cmd_id_UNSPECIFIED:
      // 'break' intentionally omitted.

    default:
      rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_ERROR;
      break;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_coproc_gpio_command(fwpb_wallet_cmd* wallet_cmd) {
  fwpb_mfgtest_gpio_cmd* cmd = &wallet_cmd->msg.mfgtest_gpio_cmd;
  fwpb_uxc_msg_host* msg_host = uc_alloc_send_proto();
  ASSERT(msg_host != NULL);

  msg_host->which_msg = fwpb_uxc_msg_host_mfgtest_gpio_cmd_tag;
  msg_host->msg.mfgtest_gpio_cmd.action = cmd->action;
  msg_host->msg.mfgtest_gpio_cmd.port = cmd->port;
  msg_host->msg.mfgtest_gpio_cmd.pin = cmd->pin;
  msg_host->msg.mfgtest_gpio_cmd.mcu_role = cmd->mcu_role;
  ipc_proto_free((uint8_t*)wallet_cmd);

  if (!uc_send(msg_host)) {
    // Failed to send, so just fake a response.
    fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
    fwpb_mfgtest_gpio_rsp* rsp = &wallet_rsp->msg.mfgtest_gpio_rsp;

    wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_gpio_rsp_tag;
    rsp->output = 0;

    proto_send_rsp(NULL, wallet_rsp);
  }
}

void mfgtest_task_port_handle_coproc_gpio_response(ipc_ref_t* message) {
  ASSERT(message != NULL);

  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  fwpb_uxc_msg_device* msg_device = (fwpb_uxc_msg_device*)message->object;
  ASSERT(msg_device != NULL);
  ASSERT(msg_device->which_msg == fwpb_uxc_msg_device_mfgtest_gpio_rsp_tag);

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_gpio_rsp_tag;
  wallet_rsp->msg.mfgtest_gpio_rsp.output = msg_device->msg.mfgtest_gpio_rsp.output;
  uc_free_recv_proto(msg_device);
  proto_send_rsp(NULL, wallet_rsp);
}

// Touch data buffer helper functions

static void _mfgtest_task_port_touch_data_start(mfgtest_task_port_touch_data_mode_t mode) {
  rtos_thread_enter_critical();
  mfgtest_task_port_touch_data_buffer.head = 0;
  mfgtest_task_port_touch_data_buffer.tail = 0;
  mfgtest_task_port_touch_data_buffer.count = 0;
  mfgtest_task_port_touch_data_buffer.dropped = 0;
  mfgtest_task_port_touch_data_buffer.mode = mode;
  mfgtest_task_port_touch_data_buffer.active = true;
  mfgtest_task_port_touch_data_buffer.paused = false;
  mfgtest_task_port_touch_data_buffer.full = false;
  rtos_thread_exit_critical();
}

static void _mfgtest_task_port_touch_data_stop(void) {
  rtos_thread_enter_critical();
  mfgtest_task_port_touch_data_buffer.active = false;
  mfgtest_task_port_touch_data_buffer.paused = false;
  mfgtest_task_port_touch_data_buffer.head = 0;
  mfgtest_task_port_touch_data_buffer.tail = 0;
  mfgtest_task_port_touch_data_buffer.count = 0;
  mfgtest_task_port_touch_data_buffer.dropped = 0;
  mfgtest_task_port_touch_data_buffer.full = false;
  rtos_thread_exit_critical();
}

static bool _mfgtest_task_port_touch_data_is_active(void) {
  rtos_thread_enter_critical();
  bool active = mfgtest_task_port_touch_data_buffer.active;
  rtos_thread_exit_critical();
  return active;
}

static uint32_t _mfgtest_task_port_touch_data_get_dropped_count(void) {
  rtos_thread_enter_critical();
  uint32_t dropped = mfgtest_task_port_touch_data_buffer.dropped;
  rtos_thread_exit_critical();
  return dropped;
}

static void _mfgtest_task_port_touch_data_add(uint16_t x, uint16_t y, uint32_t timestamp_ms) {
  rtos_thread_enter_critical();

  if (!mfgtest_task_port_touch_data_buffer.active || mfgtest_task_port_touch_data_buffer.paused) {
    rtos_thread_exit_critical();
    return;
  }

  if (mfgtest_task_port_touch_data_buffer.count >= MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS) {
    if (mfgtest_task_port_touch_data_buffer.mode ==
        MFGTEST_TASK_PORT_TOUCH_DATA_MODE_STOP_WHEN_FULL) {
      mfgtest_task_port_touch_data_buffer.full = true;
    } else {
      // CIRCULAR mode: drop oldest point
      mfgtest_task_port_touch_data_buffer.tail = (mfgtest_task_port_touch_data_buffer.tail + 1) %
                                                 MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS;
      mfgtest_task_port_touch_data_buffer.count--;
      mfgtest_task_port_touch_data_buffer.dropped++;
    }
  }

  if (mfgtest_task_port_touch_data_buffer.count < MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS) {
    mfgtest_task_port_touch_data_buffer.points[mfgtest_task_port_touch_data_buffer.head].x = x;
    mfgtest_task_port_touch_data_buffer.points[mfgtest_task_port_touch_data_buffer.head].y = y;
    mfgtest_task_port_touch_data_buffer.points[mfgtest_task_port_touch_data_buffer.head]
      .timestamp_ms = timestamp_ms;
    mfgtest_task_port_touch_data_buffer.head = (mfgtest_task_port_touch_data_buffer.head + 1) %
                                               MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS;
    mfgtest_task_port_touch_data_buffer.count++;
  }

  rtos_thread_exit_critical();
}

static bool _mfgtest_task_port_touch_data_populate_response(fwpb_mfgtest_touch_data_rsp* rsp) {
  if (rsp == NULL) {
    return false;
  }

  rtos_thread_enter_critical();

  if (!mfgtest_task_port_touch_data_buffer.active) {
    rtos_thread_exit_critical();
    return false;
  }

  // Pause collection during fetch
  mfgtest_task_port_touch_data_buffer.paused = true;

  // Fetch points directly into proto (max 25 per proto definition)
  uint32_t max_points = sizeof(rsp->points) / sizeof(rsp->points[0]);
  uint32_t fetched = 0;

  while (fetched < max_points && mfgtest_task_port_touch_data_buffer.count > 0) {
    rsp->points[fetched].x =
      mfgtest_task_port_touch_data_buffer.points[mfgtest_task_port_touch_data_buffer.tail].x;
    rsp->points[fetched].y =
      mfgtest_task_port_touch_data_buffer.points[mfgtest_task_port_touch_data_buffer.tail].y;
    rsp->points[fetched].timestamp_ms =
      mfgtest_task_port_touch_data_buffer.points[mfgtest_task_port_touch_data_buffer.tail]
        .timestamp_ms;
    mfgtest_task_port_touch_data_buffer.tail = (mfgtest_task_port_touch_data_buffer.tail + 1) %
                                               MFGTEST_TASK_PORT_TOUCH_DATA_BUFFER_MAX_POINTS;
    mfgtest_task_port_touch_data_buffer.count--;
    fetched++;
  }

  // Clear full flag if we fetched points
  if (fetched > 0 && mfgtest_task_port_touch_data_buffer.full) {
    mfgtest_task_port_touch_data_buffer.full = false;
  }

  // Populate response fields
  rsp->points_count = fetched;
  rsp->points_remaining = mfgtest_task_port_touch_data_buffer.count;
  rsp->collection_active = true;
  rsp->buffer_full = mfgtest_task_port_touch_data_buffer.full;
  rsp->dropped_count = mfgtest_task_port_touch_data_buffer.dropped;
  rsp->rsp_status = fwpb_mfgtest_touch_data_rsp_mfgtest_touch_data_rsp_status_SUCCESS;

  // Resume collection
  mfgtest_task_port_touch_data_buffer.paused = false;

  rtos_thread_exit_critical();
  return true;
}

// IPC handler for touch points from UI task
void mfgtest_task_port_handle_touch_point(ipc_ref_t* message) {
  mfgtest_touch_point_t* point = (mfgtest_touch_point_t*)message->object;
  _mfgtest_task_port_touch_data_add(point->x, point->y, point->timestamp_ms);
}

void mfgtest_task_port_handle_touch_data_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_touch_data_rsp_tag;

  fwpb_mfgtest_touch_data_cmd* cmd = &wallet_cmd->msg.mfgtest_touch_data_cmd;
  fwpb_mfgtest_touch_data_rsp* rsp = &wallet_rsp->msg.mfgtest_touch_data_rsp;

  switch (cmd->cmd_id) {
    case fwpb_mfgtest_touch_data_cmd_mfgtest_touch_data_cmd_id_START: {
      mfgtest_task_port_touch_data_mode_t mode =
        (cmd->buffer_mode ==
         fwpb_mfgtest_touch_data_cmd_mfgtest_touch_data_buffer_mode_STOP_WHEN_FULL)
          ? MFGTEST_TASK_PORT_TOUCH_DATA_MODE_STOP_WHEN_FULL
          : MFGTEST_TASK_PORT_TOUCH_DATA_MODE_CIRCULAR;
      _mfgtest_task_port_touch_data_start(mode);
      rsp->rsp_status = fwpb_mfgtest_touch_data_rsp_mfgtest_touch_data_rsp_status_SUCCESS;
      rsp->collection_active = true;
      rsp->points_count = 0;
      rsp->points_remaining = 0;
      rsp->buffer_full = false;
      rsp->dropped_count = 0;
      break;
    }

    case fwpb_mfgtest_touch_data_cmd_mfgtest_touch_data_cmd_id_FETCH: {
      if (!_mfgtest_task_port_touch_data_populate_response(rsp)) {
        rsp->rsp_status = fwpb_mfgtest_touch_data_rsp_mfgtest_touch_data_rsp_status_NOT_STARTED;
        rsp->collection_active = false;
        rsp->points_count = 0;
        rsp->points_remaining = 0;
        rsp->buffer_full = false;
        rsp->dropped_count = 0;
      }
      break;
    }

    case fwpb_mfgtest_touch_data_cmd_mfgtest_touch_data_cmd_id_STOP: {
      if (!_mfgtest_task_port_touch_data_is_active()) {
        rsp->rsp_status = fwpb_mfgtest_touch_data_rsp_mfgtest_touch_data_rsp_status_NOT_STARTED;
        rsp->collection_active = false;
        break;
      }

      uint32_t dropped = _mfgtest_task_port_touch_data_get_dropped_count();
      _mfgtest_task_port_touch_data_stop();

      rsp->rsp_status = fwpb_mfgtest_touch_data_rsp_mfgtest_touch_data_rsp_status_SUCCESS;
      rsp->collection_active = false;
      rsp->points_count = 0;
      rsp->points_remaining = 0;
      rsp->buffer_full = false;
      rsp->dropped_count = dropped;

      break;
    }

    case fwpb_mfgtest_touch_data_cmd_mfgtest_touch_data_cmd_id_UNSPECIFIED:
    default:
      LOGE("Unknown touch data cmd_id: %d", cmd->cmd_id);
      rsp->rsp_status = fwpb_mfgtest_touch_data_rsp_mfgtest_touch_data_rsp_status_FAILED;
      break;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}
