#include "display_task.h"

#include "attributes.h"
#include "bitlog.h"
#include "display_driver.h"
#include "display_send.h"
#include "log.h"
#include "mcu_gpio.h"
#include "printf.h"
#include "rtos.h"
#include "rtos_queue.h"
#include "secutils.h"
#include "sysevent.h"
#include "uc.h"
#include "uc_route.h"
#include "ui.h"
#include "uxc.pb.h"

#define DISPLAY_TASK_STACK_SIZE 8192
#define DISPLAY_TASK_PRIORITY   RTOS_THREAD_PRIORITY_NORMAL
#define DISPLAY_TASK_QUEUE_SIZE 5

// Display send task configuration
#define DISPLAY_SEND_TASK_STACK_SIZE 1024
#define DISPLAY_SEND_TASK_PRIORITY   RTOS_THREAD_PRIORITY_HIGH
#define DISPLAY_SEND_QUEUE_SIZE      8

// Display configuration
extern display_config_t display_config;

// Display command queue
static rtos_queue_t* display_cmd_queue = NULL;

// Display send queue (for sending gestures to Core)
static rtos_queue_t* display_send_queue = NULL;

// Track dropped messages for telemetry
static uint32_t display_send_dropped_count = 0;
#define DISPLAY_SEND_DROP_LOG_INTERVAL 10

static bool display_send_queue_msg_impl(const display_send_msg_t* msg) {
  if (!display_send_queue || !msg) {
    return false;
  }

  if (!rtos_queue_send(display_send_queue, (void*)msg, 0)) {
    display_send_dropped_count++;
    // Log every DISPLAY_SEND_DROP_LOG_INTERVAL drops
    if (display_send_dropped_count >= DISPLAY_SEND_DROP_LOG_INTERVAL) {
      BITLOG_EVENT(display_send_dropped, display_send_dropped_count);
      display_send_dropped_count = 0;
    }
    return false;
  }

  return true;
}

/**
 * @brief Display send task thread - handles sending messages to Core.
 *
 * This task waits on the display_send_queue and sends messages
 * to Core using uc_send(). Each message contains a handler function
 * that encodes the payload into the protobuf. This serializes sends
 * and avoids blocking the display task or LVGL event handlers.
 */
static void display_send_thread(void* UNUSED(args)) {
  // Wait for power to be ready before processing messages
  sysevent_wait(SYSEVENT_POWER_READY, true);

  // Create send queue
  display_send_queue = rtos_queue_create(display_send, display_send_msg_t, DISPLAY_SEND_QUEUE_SIZE);
  if (!display_send_queue) {
    LOGE("Failed to create display send queue");
    return;
  }

  // Register the queue implementation with lib/display
  display_send_register(display_send_queue_msg_impl);

  // Process queued messages forever
  for (;;) {
    display_send_msg_t msg;
    if (rtos_queue_recv(display_send_queue, &msg, RTOS_QUEUE_TIMEOUT_MAX)) {
      if (!msg.handler) {
        LOGW("Display send message has no handler");
        continue;
      }

      // Allocate protobuf and let handler encode it
      fwpb_uxc_msg_device* proto = uc_alloc_send_proto();
      if (proto) {
        msg.handler(proto, msg.payload);
        if (msg.flags & DISPLAY_SEND_FLAG_IMMEDIATE) {
          uc_send_immediate(proto);
        } else {
          uc_send(proto);
        }
        // Signal completion if caller provided a flag
        if (msg.sent) {
          *msg.sent = true;
        }
      } else {
        LOGW("Failed to allocate proto for display send");
      }
    }
  }
}

static bool display_process_commands(void) {
  if (!display_cmd_queue) {
    return false;
  }

  fwpb_uxc_msg_host* msg = NULL;
  if (!rtos_queue_recv(display_cmd_queue, &msg, 0)) {
    return false;  // No message available
  }

  if (!msg) {
    return false;
  }

  bool processed = false;

  if (msg->which_msg == fwpb_uxc_msg_host_display_cmd_tag) {
    // Execute the display command
    fwpb_display_result result = ui_execute_command(&msg->msg.display_cmd);

    // TODO: Send response back to w3-core? For now just log the result
    if (result != fwpb_display_result_DISPLAY_RESULT_SUCCESS) {
      LOGW("Display command failed with result: %d", result);
    }

    processed = true;
  } else {
    LOGW("Received unexpected message on display queue (which_msg=%d)", msg->which_msg);
  }

  // Free the received message
  uc_free_recv_proto(msg);

  return processed;
}

NO_OPTIMIZE void display_thread(void* UNUSED(args)) {
  SECURE_ASSERT(rtos_thread_is_privileged() == true);

  printf("Display task started\n");

  // Wait for power to be ready
  sysevent_wait(SYSEVENT_POWER_READY, true);

  display_init();

  // Create display command queue
  display_cmd_queue = rtos_queue_create(display_cmd, void*, DISPLAY_TASK_QUEUE_SIZE);
  if (!display_cmd_queue) {
    LOGE("Failed to create display command queue");
    return;
  }

  // Register the queue for display command messages from w3-core
  uc_route_register_queue(fwpb_uxc_msg_host_display_cmd_tag, display_cmd_queue);

  // Send display ready response to signal we're ready to receive commands
  fwpb_uxc_msg_device* ready_msg = uc_alloc_send_proto();
  if (ready_msg) {
    ready_msg->which_msg = fwpb_uxc_msg_device_display_rsp_tag;
    ready_msg->msg.display_rsp.which_response = fwpb_display_response_ready_tag;
    ready_msg->msg.display_rsp.response.ready = true;
    uc_send(ready_msg);
  }

  uint32_t next_wake_time = rtos_thread_systime();

  // Main display update loop
  for (;;) {
    // Process any pending display commands from w3-core
    display_process_commands();

    // Update display
    display_update();

    // Calculate next wake time and sleep until then
    next_wake_time += display_config.update_period_ms;
    uint32_t current_time = rtos_thread_systime();
    if (next_wake_time > current_time) {
      uint32_t sleep_time = next_wake_time - current_time;
      rtos_thread_sleep(sleep_time);
    } else {
      // We've fallen behind schedule, reset to current time
      next_wake_time = current_time;
    }
  }
}

void display_task_create(void) {
  rtos_thread_t* display_thread_handle =
    rtos_thread_create(display_thread, NULL, DISPLAY_TASK_PRIORITY, DISPLAY_TASK_STACK_SIZE);
  ASSERT(display_thread_handle);

  rtos_thread_t* display_send_thread_handle = rtos_thread_create(
    display_send_thread, NULL, DISPLAY_SEND_TASK_PRIORITY, DISPLAY_SEND_TASK_STACK_SIZE);
  ASSERT(display_send_thread_handle);
}
