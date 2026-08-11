/**
 * @file uxc_transport.c
 * @brief UXC message transport layer
 *
 * Implements UXC message routing over socket, mirroring the real firmware's
 * UC protocol and uc_route pattern.
 */

#include "uxc_transport.h"

#include "handler_device.h"
#include "handler_emulator.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "stdio_defs.h"
#include "uxc_socket_server.h"

#include <string.h>

#define TRANSPORT_LOG(fmt, ...) LOG_MODULE("uxc_transport", fmt, ##__VA_ARGS__)

// UI message subtypes (first byte of MSG_TYPE_UI payload)
#define UI_SUBTYPE_UXC_HOST         0x10
#define UI_SUBTYPE_UXC_DEVICE       0x11
#define UI_SUBTYPE_UNLOCK           0x12
#define UI_SUBTYPE_UI_EVENT         0x13
#define UI_SUBTYPE_RESET_ONBOARDING 0x14
#define UI_SUBTYPE_FINGER_TOUCH     0x15

#define MAX_HANDLERS 8

static struct {
  uint32_t tag;
  uxc_route_handler_t handler;
  void* context;
} g_handlers[MAX_HANDLERS];
static int g_handler_count = 0;

static uint8_t g_recv_buf[4096];
static uint32_t g_recv_len = 0;

static bool send_ui_message(const uint8_t* payload, uint32_t payload_len) {
  if (!uxc_socket_is_connected()) {
    return false;
  }

  uint8_t header[5] = {MSG_TYPE_UI};
  pack_be32(header + 1, payload_len);

  return uxc_socket_send(header, sizeof(header)) && uxc_socket_send(payload, payload_len);
}

void uxc_transport_init(void) {
  g_handler_count = 0;
  g_recv_len = 0;
  TRANSPORT_LOG("initialized");
}

void uxc_transport_register_handler(uint32_t tag, uxc_route_handler_t handler, void* context) {
  if (g_handler_count >= MAX_HANDLERS) {
    TRANSPORT_LOG("too many handlers registered");
    return;
  }
  int i = g_handler_count++;
  g_handlers[i].tag = tag;
  g_handlers[i].handler = handler;
  g_handlers[i].context = context;
  TRANSPORT_LOG("registered handler for tag %u", tag);
}

bool uxc_transport_send_host_msg(const fwpb_uxc_msg_host* msg) {
  uint8_t buf[4096];
  buf[0] = UI_SUBTYPE_UXC_HOST;

  pb_ostream_t stream = pb_ostream_from_buffer(buf + 1, sizeof(buf) - 1);
  if (!pb_encode(&stream, fwpb_uxc_msg_host_fields, msg)) {
    TRANSPORT_LOG("failed to encode host message");
    return false;
  }

  uint32_t payload_len = 1 + stream.bytes_written;
  if (!send_ui_message(buf, payload_len)) {
    return false;
  }

  TRANSPORT_LOG("sent host message (tag=%u, %u bytes)", msg->which_msg, payload_len);
  return true;
}

bool uxc_transport_send_ui_event(uint32_t event_type, const void* data, uint32_t data_len) {
  uint8_t buf[4096];
  const uint32_t header_size = 9;  // subtype(1) + event_type(4) + data_len(4)

  if (data_len > sizeof(buf) - header_size) {
    TRANSPORT_LOG("UI event data too large");
    return false;
  }

  buf[0] = UI_SUBTYPE_UI_EVENT;
  pack_le32(buf + 1, event_type);
  pack_le32(buf + 5, data_len);

  if (data && data_len > 0) {
    memcpy(buf + header_size, data, data_len);
  }

  if (!send_ui_message(buf, header_size + data_len)) {
    return false;
  }

  TRANSPORT_LOG("sent UI event %u (%u bytes data)", event_type, data_len);
  return true;
}

bool uxc_transport_send_reset_onboarding(void) {
  bool sent = send_ui_message((uint8_t[]){UI_SUBTYPE_RESET_ONBOARDING}, 1);
  if (sent) {
    TRANSPORT_LOG("sent reset onboarding");
  }
  return sent;
}

static void dispatch_device_msg(const fwpb_uxc_msg_device* msg) {
  for (int i = 0; i < g_handler_count; i++) {
    if (g_handlers[i].tag == msg->which_msg) {
      g_handlers[i].handler(msg, g_handlers[i].context);
      return;
    }
  }
  TRANSPORT_LOG("no handler for device message tag %u", msg->which_msg);
}

static void consume_message(size_t total_len) {
  memmove(g_recv_buf, g_recv_buf + total_len, g_recv_len - total_len);
  g_recv_len -= total_len;
}

static bool try_parse_message(void) {
  const size_t header_len = 5;

  if (g_recv_len < header_len) {
    return false;
  }

  uint8_t msg_type = g_recv_buf[0];
  uint32_t payload_len = unpack_be32(g_recv_buf + 1);
  if (payload_len > sizeof(g_recv_buf) - header_len) {
    TRANSPORT_LOG("payload too large: %u > %zu", payload_len, sizeof(g_recv_buf) - header_len);
    uxc_socket_close_client();
    g_recv_len = 0;
    return false;
  }

  size_t total_len = header_len + (size_t)payload_len;

  if (g_recv_len < total_len) {
    return false;
  }

  // We only handle MSG_TYPE_UI with UXC_DEVICE subtype
  if (msg_type != MSG_TYPE_UI) {
    TRANSPORT_LOG("unexpected message type 0x%02x", msg_type);
    goto done;
  }

  if (payload_len < 1) {
    TRANSPORT_LOG("UI message too short");
    goto done;
  }

  uint8_t subtype = g_recv_buf[5];

  if (subtype == UI_SUBTYPE_UNLOCK) {
    TRANSPORT_LOG("unlock command received, setting auth state");
    core_sim_set_authenticated(true);
    goto done;
  }

  if (subtype == UI_SUBTYPE_FINGER_TOUCH) {
    // Forward to emulator handler (payload byte: 1=good, 0=bad)
    uint8_t finger_payload = (payload_len > 1) ? g_recv_buf[6] : 1;
    uint8_t rsp[16];
    uint32_t rsp_len = sizeof(rsp);
    stdio_handle_emulator_command(UI_CMD_SIMULATE_FINGER_TOUCH, &finger_payload, 1, rsp, &rsp_len);
    TRANSPORT_LOG("finger touch handled (pass=%u)", finger_payload);
    goto done;
  }

  if (subtype != UI_SUBTYPE_UXC_DEVICE) {
    TRANSPORT_LOG("unexpected UI subtype 0x%02x", subtype);
    goto done;
  }

  fwpb_uxc_msg_device device_msg = fwpb_uxc_msg_device_init_default;
  pb_istream_t stream = pb_istream_from_buffer(g_recv_buf + 6, payload_len - 1);
  if (!pb_decode(&stream, fwpb_uxc_msg_device_fields, &device_msg)) {
    TRANSPORT_LOG("failed to decode device message");
    goto done;
  }

  TRANSPORT_LOG("received device message (tag=%u)", device_msg.which_msg);
  dispatch_device_msg(&device_msg);

done:
  consume_message(total_len);
  return true;
}

bool uxc_transport_poll(void) {
  if (!uxc_socket_is_connected()) {
    return false;
  }

  int space = sizeof(g_recv_buf) - g_recv_len;
  if (space > 0) {
    int n = uxc_socket_recv(g_recv_buf + g_recv_len, space);
    if (n > 0) {
      g_recv_len += n;
    } else if (n < 0) {
      // Disconnected
      g_recv_len = 0;
      return false;
    }
  }

  bool processed = false;
  while (try_parse_message()) {
    processed = true;
  }

  return processed;
}
