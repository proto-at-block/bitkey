#include "ui_task.h"

#include "assert.h"
#include "attributes.h"
#include "ipc.h"
#include "ipc_messages_ui_port.h"
#include "log.h"
#include "rtos.h"
#include "secutils.h"
#include "sysevent.h"
#include "ui_backend.h"
#include "ui_events.h"

#ifdef UI_BACKEND_LED
#define UI_TASK_STACK_SIZE 1024
#else
#define UI_TASK_STACK_SIZE 4096
#endif
#define UI_TASK_PRIORITY RTOS_THREAD_PRIORITY_LOW
#define UI_QUEUE_DEPTH   (10ul)

static struct {
  rtos_queue_t* queue;
  const ui_backend_ops_t* backend;
} ui_priv UI_TASK_DATA = {0};

static void service_ipc(void) {
  ipc_ref_t message = {0};
  // UI task receives messages from ui_port
  if (!ipc_recv_opt(ui_port, &message, (ipc_options_t){.timeout_ms = 0})) {
    return;
  }

  if (!ui_priv.backend) {
    LOGW("UI backend not initialized");
    return;
  }

  switch (message.tag) {
    case IPC_UI_SHOW_EVENT: {
      ui_show_event_t* msg = (ui_show_event_t*)message.object;
      if (msg && ui_priv.backend->show_event) {
        ui_priv.backend->show_event((ui_event_type_t)msg->event);
      }
    } break;

    case IPC_UI_SHOW_EVENT_WITH_DATA: {
      ui_show_event_with_data_t* msg = (ui_show_event_with_data_t*)message.object;
      if (msg && ui_priv.backend->show_event_with_data) {
        ui_priv.backend->show_event_with_data((ui_event_type_t)msg->event, msg->data,
                                              msg->data_len);
      }
    } break;

    case IPC_UI_SET_IDLE_STATE: {
      ui_set_idle_state_t* msg = (ui_set_idle_state_t*)message.object;
      if (msg && ui_priv.backend->set_idle_state) {
        ui_priv.backend->set_idle_state((ui_event_type_t)msg->idle_state);
      }
    } break;

    case IPC_UI_DISPLAY_ACTION: {
      ui_display_action_t* msg = (ui_display_action_t*)message.object;
      if (msg && ui_priv.backend->handle_display_action) {
        ui_priv.backend->handle_display_action(msg->action, msg->data);
      }
    } break;

    default:
      LOGW("UI task received unknown message: %ld", message.tag);
      break;
  }
}

NO_OPTIMIZE void ui_thread(void* UNUSED(args)) {
#ifdef MFGTEST
  const bool privileged = true;
#else
  const bool privileged = false;
#endif
  SECURE_ASSERT(rtos_thread_is_privileged() == privileged);

  sysevent_wait(SYSEVENT_POWER_READY, true);

  ui_priv.backend = ui_backend_get();
  ASSERT(ui_priv.backend);
  ASSERT(ui_priv.backend->show_event);
  ASSERT(ui_priv.backend->show_event_with_data);
  ASSERT(ui_priv.backend->set_idle_state);
  ASSERT(ui_priv.backend->clear);

  if (ui_priv.backend->init) {
    ui_priv.backend->init();
  }

  for (;;) {
    service_ipc();

    if (ui_priv.backend && ui_priv.backend->run) {
      ui_priv.backend->run();
    } else {
      rtos_thread_sleep(50);
    }
  }
}

void ui_task_create(void) {
  // Create the queue and register with IPC
  ui_priv.queue = rtos_queue_create(ui_queue, ipc_ref_t, UI_QUEUE_DEPTH);
  ASSERT(ui_priv.queue);
  // UI task handles ui_port for all platforms
  ipc_register_port(ui_port, ui_priv.queue);

  rtos_thread_t* ui_thread_handle =
    rtos_thread_create(ui_thread, NULL, UI_TASK_PRIORITY, UI_TASK_STACK_SIZE);
  ASSERT(ui_thread_handle);
}
