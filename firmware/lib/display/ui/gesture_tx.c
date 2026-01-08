#include "gesture_tx.h"

#include "display_send.h"
#include "log.h"

#include <stddef.h>
#include <string.h>

static void gesture_tx_global_event_handler(lv_event_t* e);

// Gesture payload structure
typedef struct {
  fwpb_display_touch_display_touch_event event;
  uint32_t x;
  uint32_t y;
} gesture_payload_t;

_Static_assert(sizeof(gesture_payload_t) <= DISPLAY_SEND_PAYLOAD_MAX_SIZE,
               "gesture_payload_t exceeds DISPLAY_SEND_PAYLOAD_MAX_SIZE");

// Handler to encode gesture payload into protobuf
static void gesture_encode_handler(fwpb_uxc_msg_device* proto, const void* payload) {
  const gesture_payload_t* gesture = (const gesture_payload_t*)payload;

  proto->which_msg = fwpb_uxc_msg_device_display_touch_tag;
  proto->msg.display_touch.event = gesture->event;
  proto->msg.display_touch.has_coord = true;
  proto->msg.display_touch.coord.x = gesture->x;
  proto->msg.display_touch.coord.y = gesture->y;
}

// Forward a gesture to Core via display send queue
static void gesture_tx_forward_to_core(fwpb_display_touch_display_touch_event gesture) {
  // Get touch coordinates from active input device
  lv_indev_t* indev = lv_indev_get_act();
  lv_point_t point;
  if (indev) {
    lv_indev_get_point(indev, &point);
  } else {
    point.x = 0;
    point.y = 0;
  }

  // Build gesture payload
  gesture_payload_t payload = {
    .event = gesture,
    .x = (uint32_t)point.x,
    .y = (uint32_t)point.y,
  };

  // Build message with handler and payload
  display_send_msg_t msg = {
    .handler = gesture_encode_handler,
  };
  memcpy(msg.payload, &payload, sizeof(payload));

  display_send_queue_msg(&msg);
}

// Global event handler for gesture detection
// Catches gestures from all screens and forwards to Core
// Events bubble up from child widgets (buttons, etc.) to the screen
static void gesture_tx_global_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  fwpb_display_touch_display_touch_event proto_event;

  switch (code) {
    case LV_EVENT_GESTURE: {
      // Get gesture direction from LVGL input device
      lv_indev_t* indev = lv_indev_get_act();
      if (!indev) {
        return;
      }

      lv_dir_t dir = lv_indev_get_gesture_dir(indev);

      // Convert LVGL direction to proto event type
      switch (dir) {
        case LV_DIR_LEFT:
          proto_event =
            fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_SWIPE_LEFT;
          break;
        case LV_DIR_RIGHT:
          proto_event =
            fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_SWIPE_RIGHT;
          break;
        case LV_DIR_TOP:
          proto_event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_SWIPE_UP;
          break;
        case LV_DIR_BOTTOM:
          proto_event =
            fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_SWIPE_DOWN;
          break;
        default:
          return;
      }
      break;
    }

    case LV_EVENT_DOUBLE_CLICKED:
      proto_event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_DOUBLE_TAP;
      break;

    case LV_EVENT_TRIPLE_CLICKED:
      proto_event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_TRIPLE_TAP;
      break;

    case LV_EVENT_LONG_PRESSED:
      proto_event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_GESTURE_LONG_PRESS;
      break;

    default:
      return;
  }

  gesture_tx_forward_to_core(proto_event);
}

// Attach gesture handlers to a screen
// Called automatically from ui.c when screens are loaded
void gesture_tx_attach_to_screen(lv_obj_t* screen) {
  if (!screen) {
    return;
  }

  // Attach gesture event handlers
  lv_obj_add_event_cb(screen, gesture_tx_global_event_handler, LV_EVENT_GESTURE, NULL);
  lv_obj_add_event_cb(screen, gesture_tx_global_event_handler, LV_EVENT_DOUBLE_CLICKED, NULL);
  lv_obj_add_event_cb(screen, gesture_tx_global_event_handler, LV_EVENT_TRIPLE_CLICKED, NULL);
  lv_obj_add_event_cb(screen, gesture_tx_global_event_handler, LV_EVENT_LONG_PRESSED, NULL);
}
