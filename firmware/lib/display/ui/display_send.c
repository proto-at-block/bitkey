#include "display_send.h"

#include <stddef.h>

// Callback registered by app/tasks/display to handle message sending.
// This pattern avoids a circular dependency: lib/display needs to send messages
// but the queue implementation lives in app/tasks/display. By using a callback,
// lib/display only depends on the interface while app/tasks/display provides
// the implementation.
static display_send_fn_t display_send_fn = NULL;

void display_send_register(display_send_fn_t send_fn) {
  display_send_fn = send_fn;
}

bool display_send_queue_msg(const display_send_msg_t* msg) {
  if (!display_send_fn || !msg) {
    return false;
  }
  return display_send_fn(msg);
}