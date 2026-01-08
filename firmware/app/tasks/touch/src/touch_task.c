#include "touch_task.h"

#include "assert.h"
#include "attributes.h"
#include "rtos.h"
#include "touch.h"

#ifdef MFGTEST
#include "display_send.h"
#include "log.h"
#endif

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define TOUCH_TASK_PRIORITY   (RTOS_THREAD_PRIORITY_HIGH)
#define TOUCH_TASK_STACK_SIZE (2048u)

extern touch_config_t touch_config;

typedef bool (*touch_event_cb_t)(const touch_event_t* event);
static touch_event_cb_t touch_event_callback = NULL;

#ifdef MFGTEST
/* During MFG test builds, we forward all touch events to Core, we rate limit by
making sure we only have one touch event queued at a time since the touch
controller runs at a high rate. */
typedef struct {
  uint32_t x;
  uint32_t y;
} touch_mfgtest_payload_t;

_Static_assert(sizeof(touch_mfgtest_payload_t) <= DISPLAY_SEND_PAYLOAD_MAX_SIZE,
               "touch_mfgtest_payload_t exceeds DISPLAY_SEND_PAYLOAD_MAX_SIZE");

// Flag to track if previous message has been sent
static volatile bool mfgtest_touch_msg_sent = true;

/**
 * @brief Handler to encode touch mfgtest payload into protobuf.
 */
static void touch_mfgtest_encode_handler(fwpb_uxc_msg_device* proto, const void* payload) {
  const touch_mfgtest_payload_t* touch = (const touch_mfgtest_payload_t*)payload;

  proto->which_msg = fwpb_uxc_msg_device_display_touch_tag;
  proto->msg.display_touch.event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_TOUCH;
  proto->msg.display_touch.has_coord = true;
  proto->msg.display_touch.coord.x = touch->x;
  proto->msg.display_touch.coord.y = touch->y;
}

/**
 * @brief Touch event callback for MFG test builds.
 *
 * Sends touch coordinates to Core via display_send_queue for manufacturing testing.
 * Only queues a new message if the previous one has been sent.
 */
static bool touch_mfgtest_event_cb(const touch_event_t* event) {
  // Skip if previous message hasn't been sent yet
  if (!mfgtest_touch_msg_sent) {
    return false;
  }

  // Build payload
  touch_mfgtest_payload_t payload = {
    .x = event->coord.x,
    .y = event->coord.y,
  };

  // Mark as pending before queueing
  mfgtest_touch_msg_sent = false;

  // Build message
  display_send_msg_t msg = {
    .handler = touch_mfgtest_encode_handler,
    .flags = DISPLAY_SEND_FLAG_IMMEDIATE,
    .sent = &mfgtest_touch_msg_sent,
  };
  memcpy(msg.payload, &payload, sizeof(payload));

  if (!display_send_queue_msg(&msg)) {
    // Queue failed, reset flag so we can try again
    mfgtest_touch_msg_sent = true;
    return false;
  }

  return true;
}

static void touch_task_register_mfgtest_callback(void) {
  touch_event_callback = touch_mfgtest_event_cb;
}
#endif

static void touch_task_thread(void* UNUSED(args)) {
  touch_init(&touch_config);
#ifdef MFGTEST
  touch_task_register_mfgtest_callback();
  if (!touch_enable()) {
    LOGW("Touch controller not detected, touch task exiting");
    rtos_thread_delete(NULL);
  }
#else
  ASSERT(touch_enable());
#endif

  while (1) {
    touch_event_t touch_event;
    if (touch_pend_event(&touch_event, TOUCH_PEND_1000_MS)) {
      // If a callback is registered, invoke it with the touch event
      if (touch_event_callback) {
        (void)touch_event_callback(&touch_event);
      }
    }
    touch_process_esd_check();
  }
}

void touch_task_create(void) {
  rtos_thread_t* thread =
    rtos_thread_create(touch_task_thread, NULL, TOUCH_TASK_PRIORITY, TOUCH_TASK_STACK_SIZE);
  ASSERT(thread != NULL);
}
