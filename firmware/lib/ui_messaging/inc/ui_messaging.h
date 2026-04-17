#pragma once

#include "arithmetic.h"
#include "attributes.h"
#include "ipc.h"
#include "ui_events.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

_Static_assert(sizeof(((ui_show_event_with_data_t*)0)->data) == UI_EVENT_MAX_DATA_SIZE,
               "ui_show_event_with_data_t.data size must match UI_EVENT_MAX_DATA_SIZE");

#define UI_SHOW_EVENT(event_value)                                   \
  do {                                                               \
    static ui_show_event_t SHARED_TASK_BSS _ui_msg = {0};            \
    _ui_msg.event = (uint32_t)(event_value);                         \
    ipc_send(ui_port, &_ui_msg, sizeof(_ui_msg), IPC_UI_SHOW_EVENT); \
  } while (0)

#define UI_SHOW_EVENT_WITH_DATA(event_value, payload_ptr, payload_size)                    \
  do {                                                                                     \
    static ui_show_event_with_data_t SHARED_TASK_BSS _ui_event_msg = {0};                  \
    uint32_t _ui_len = (payload_size);                                                     \
    const void* _ui_ptr = (payload_ptr);                                                   \
    _ui_event_msg.event = (uint32_t)(event_value);                                         \
    uint32_t _ui_max_len = sizeof(_ui_event_msg.data);                                     \
    uint32_t _ui_copy_len = BLK_MIN(_ui_len, _ui_max_len);                                 \
    _ui_event_msg.data_len = _ui_copy_len;                                                 \
    if (_ui_ptr && _ui_copy_len > 0) {                                                     \
      memcpy(&_ui_event_msg.data, _ui_ptr, _ui_copy_len);                                  \
    }                                                                                      \
    ipc_send(ui_port, &_ui_event_msg, sizeof(_ui_event_msg), IPC_UI_SHOW_EVENT_WITH_DATA); \
  } while (0)

// Send a confirmation event to the display controller.
// Defined in ui_messaging.c with a single static IPC buffer (saves ~2.6KB vs per-call-site
// macro expansion on RAM-constrained targets).
void ui_show_confirmation(const char* text, bool lock);

#define UI_SET_IDLE_STATE(idle_state_value)                                        \
  do {                                                                             \
    static ui_set_idle_state_t SHARED_TASK_BSS _ui_idle_msg = {0};                 \
    _ui_idle_msg.idle_state = (uint32_t)(idle_state_value);                        \
    ipc_send(ui_port, &_ui_idle_msg, sizeof(_ui_idle_msg), IPC_UI_SET_IDLE_STATE); \
  } while (0)
