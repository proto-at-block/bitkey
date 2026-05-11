#include "display_task.h"

#include "attributes.h"
#include "bitlog.h"
#include "display_action.h"
#include "display_driver.h"
#include "display_send.h"
#include "log.h"
#include "mcu_gpio.h"
#include "mcu_reset.h"
#include "printf.h"
#include "rtos.h"
#include "rtos_queue.h"
#include "secutils.h"
#include "sysevent.h"
#include "uc.h"
#include "uc_route.h"
#include "ui.h"
#include "uxc.pb.h"

#define UXC_SEND_FAILURE_RESET_THRESHOLD 2u

#define DISPLAY_TASK_STACK_SIZE 8192
#define DISPLAY_TASK_PRIORITY   RTOS_THREAD_PRIORITY_NORMAL
#define DISPLAY_TASK_QUEUE_SIZE 5

// Display send task configuration
#define DISPLAY_SEND_TASK_STACK_SIZE 1024
#define DISPLAY_SEND_TASK_PRIORITY   RTOS_THREAD_PRIORITY_HIGH
#define DISPLAY_SEND_QUEUE_SIZE      8

#define DISPLAY_SEND_DROP_LOG_INTERVAL 10
#define DISPLAY_READY_RETRY_MS         250

// Display configuration
extern display_config_t display_config;

// Display command queue
static rtos_queue_t* display_cmd_queue SHARED_TASK_BSS = NULL;

// Display send queue (for sending gestures to Core)
static rtos_queue_t* display_send_queue SHARED_TASK_BSS = NULL;

// Track dropped messages for telemetry
static uint32_t display_send_dropped_count SHARED_TASK_BSS = 0;
static bool display_first_command_applied SHARED_TASK_BSS = false;
static uint32_t display_ready_last_send_ms SHARED_TASK_BSS = 0;

static bool display_send_queue_msg_impl(const display_send_msg_t* msg);

static void display_send_ready_handler(fwpb_uxc_msg_device* proto, const void* UNUSED(payload)) {
  proto->which_msg = fwpb_uxc_msg_device_display_action_tag;
  proto->msg.display_action.action = fwpb_display_action_display_action_type_DISPLAY_ACTION_READY;
  proto->msg.display_action.data = 0;
}

static void display_queue_ready(void) {
  const display_send_msg_t msg = {
    .handler = display_send_ready_handler,
    .flags = DISPLAY_SEND_FLAG_NONE,
  };

  display_ready_last_send_ms = rtos_thread_systime();
  if (!display_send_queue_msg_impl(&msg)) {
    LOGW("Display ready queue fail");
  }
}

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
  uint8_t send_failures = 0;

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
        bool success;
        if (msg.flags & DISPLAY_SEND_FLAG_IMMEDIATE) {
          success = uc_send_immediate(proto);
        } else {
          success = uc_send(proto);
        }

        if (success) {
          send_failures = 0;
        } else if (++send_failures >= UXC_SEND_FAILURE_RESET_THRESHOLD) {
          LOGW("Reset UXC: %u send failures", send_failures);
          mcu_reset_with_reason(MCU_RESET_SOFTWARE);
        }

        // Signal completion if caller provided a flag
        if (msg.sent) {
          *msg.sent = true;
        }
      } else {
        LOGW("Display send proto alloc fail");
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
    if (msg->msg.display_cmd.which_command == fwpb_display_command_show_screen_tag) {
      // Copy command to stack and free recv buffer immediately to avoid
      // holding a shared UC recv buffer during rendering.
      fwpb_display_command cmd_local = msg->msg.display_cmd;
      uc_free_recv_proto(msg);

      fwpb_display_result result = ui_execute_command(&cmd_local);
      if (result != fwpb_display_result_DISPLAY_RESULT_SUCCESS) {
        LOGW("Display command failed with result: %d", result);
      } else {
        display_first_command_applied = true;
      }

      return true;
    }
  } else {
    LOGW("Unexpected display msg (which=%d)", msg->which_msg);
  }

  // Free the received message
  uc_free_recv_proto(msg);

  return processed;
}

NO_OPTIMIZE void display_thread(void* UNUSED(args)) {
  SECURE_ASSERT(rtos_thread_is_privileged() == true);

  // Wait for power to be ready
  sysevent_wait(SYSEVENT_POWER_READY, true);

  display_init();

  // Register rotation callback so UI layer can apply rotation when flags change
  ui_set_rotation_callback(display_set_rotation);

  // Register the queue for display command messages from w3-core
  uc_route_register_queue(fwpb_uxc_msg_host_display_cmd_tag, display_cmd_queue);

  sysevent_wait_with_timeout(SYSEVENT_UXC_SECURE_COMMS_ESTABLISHED, true, 1000);
  display_queue_ready();

  uint32_t next_wake_time = rtos_thread_systime();

  // Main display update loop
  for (;;) {
    // Process any pending display commands from w3-core
    display_process_commands();

    // Retry READY until Core proves it can drive the display. This recovers
    // from a missed READY on boot as well as a dropped first display command.
    if (!display_first_command_applied &&
        RTOS_DEADLINE(display_ready_last_send_ms, DISPLAY_READY_RETRY_MS)) {
      display_queue_ready();
    }

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
  // Create display command queue
  display_cmd_queue = rtos_queue_create(display_cmd, void*, DISPLAY_TASK_QUEUE_SIZE);
  ASSERT(display_cmd_queue != NULL);

  // Create send queue
  display_send_queue = rtos_queue_create(display_send, display_send_msg_t, DISPLAY_SEND_QUEUE_SIZE);
  ASSERT(display_send_queue != NULL);

  rtos_thread_t* display_thread_handle =
    rtos_thread_create(display_thread, NULL, DISPLAY_TASK_PRIORITY, DISPLAY_TASK_STACK_SIZE);
  ASSERT(display_thread_handle);

  rtos_thread_t* display_send_thread_handle = rtos_thread_create(
    display_send_thread, NULL, DISPLAY_SEND_TASK_PRIORITY, DISPLAY_SEND_TASK_STACK_SIZE);
  ASSERT(display_send_thread_handle);
}
