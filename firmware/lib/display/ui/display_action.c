#include "display_action.h"

#include "display_send.h"

#include <string.h>

// Action payload structure
typedef struct {
  fwpb_display_action_display_action_type action;
  uint32_t data;
} action_payload_t;

_Static_assert(sizeof(action_payload_t) <= DISPLAY_SEND_PAYLOAD_MAX_SIZE,
               "action_payload_t exceeds DISPLAY_SEND_PAYLOAD_MAX_SIZE");

// Handler to encode action payload into protobuf
static void action_encode_handler(fwpb_uxc_msg_device* proto, const void* payload) {
  const action_payload_t* action_payload = (const action_payload_t*)payload;

  proto->which_msg = fwpb_uxc_msg_device_display_action_tag;
  proto->msg.display_action.action = action_payload->action;
  proto->msg.display_action.data = action_payload->data;
}

void display_send_action(fwpb_display_action_display_action_type action, uint32_t data) {
  // Build action payload
  action_payload_t payload = {
    .action = action,
    .data = data,
  };

  // Build message with handler and payload
  display_send_msg_t msg = {
    .handler = action_encode_handler,
  };
  memcpy(msg.payload, &payload, sizeof(payload));

  display_send_queue_msg(&msg);
}
