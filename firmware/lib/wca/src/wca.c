#include "wca.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "ipc.h"
#include "log.h"
#include "pb.h"
#include "pb_decode.h"
#include "ui_messaging.h"
#include "wallet.pb.h"
#include "wca_impl.h"
#include "wstring.h"

static const uint16_t WCA_VERSION = 1;

wca_priv_t wca_priv SHARED_TASK_BSS = {
  .mempool = NULL,
  .encoded_proto_rsp_ctx = {{0}},
  .encoded_proto_cmd_ctx = {{0}},
};

PB_STATIC_ASSERT(sizeof(pb_size_t) == sizeof(uint16_t), "wrong pb_size_t");

static void clear_proto_cmd_ctx(void) {
  // The command buffer is routed through IPC by reference, so a timed-out WCA
  // exchange must not clear bytes that a queued consumer may still decode.
  wca_priv.encoded_proto_cmd_ctx.tag = 0;
  wca_priv.encoded_proto_cmd_ctx.size = 0;
  wca_priv.encoded_proto_cmd_ctx.offset = 0;
}

static void clear_proto_rsp_ctx(void) {
  wca_sem_take_nowait_t sem_take_nowait = wca_priv.encoded_proto_rsp_ctx.sem_take_nowait;

  if (sem_take_nowait != NULL) {
    while (sem_take_nowait()) {
    }
  }

  // The response scratch buffer is shared with IPC producers and protected by
  // the IPC response lock. Reset only the consumer-visible state here.
  wca_priv.encoded_proto_rsp_ctx.offset = 0;
  wca_priv.encoded_proto_rsp_ctx.size = 0;
}

static void clear_proto_state(void) {
  clear_proto_rsp_ctx();
  clear_proto_cmd_ctx();
}

void wca_reset_session_state(void) {
  clear_proto_state();
}

static void handle_proto_response(uint8_t* encoded_proto, uint32_t size) {
  if (!encoded_proto && (size == 0)) {
    // Empty response, just give the semaphore.
    clear_proto_rsp_ctx();
    wca_priv.encoded_proto_rsp_ctx.sem_give();
    return;
  }
  ASSERT(size <= sizeof(wca_priv.encoded_proto_rsp_ctx.buffer));
  memcpy(wca_priv.encoded_proto_rsp_ctx.buffer, encoded_proto, size);
  wca_priv.encoded_proto_rsp_ctx.size = size;
  wca_priv.encoded_proto_rsp_ctx.offset = 0;
  wca_priv.encoded_proto_rsp_ctx.sem_give();
}

static inline bool received_full_proto(void) {
  return (wca_priv.encoded_proto_cmd_ctx.offset >= wca_priv.encoded_proto_cmd_ctx.size);
}

static bool wca_has_readable_lc(const uint8_t* cmd, uint32_t cmd_len) {
  // PROTO and PROTO_CONT parse Lc at cmd[4], which also proves the P1/P2 header
  // bytes before it are readable. A nonzero Lc is the one-byte short form; zero
  // marks the three-byte extended form, so cmd[5] and cmd[6] must also be
  // present before lc_to_int() can safely read the length.
  return cmd_len > LC && (cmd[LC] != 0 || cmd_len >= LC + 3);
}

void drain_response_buffer(uint8_t* rsp, uint32_t* rsp_len) {
  uint32_t in_len = *rsp_len;

  if (wca_priv.encoded_proto_rsp_ctx.size == 0) {
    RSP_OK(rsp, 0);
    *rsp_len = SW_SIZE;
    return;
  }

  const uint32_t max_bytes = in_len - SW_SIZE;  // Always need two bytes for status words
  const uint32_t data_bytes_written = BLK_MIN(max_bytes, wca_priv.encoded_proto_rsp_ctx.size);
  memcpy(rsp, &wca_priv.encoded_proto_rsp_ctx.buffer[wca_priv.encoded_proto_rsp_ctx.offset],
         data_bytes_written);
  wca_priv.encoded_proto_rsp_ctx.offset += data_bytes_written;
  wca_priv.encoded_proto_rsp_ctx.size -= data_bytes_written;

  if (wca_priv.encoded_proto_rsp_ctx.size > 0) {
    // Bytes remain: indicate in status words
    RSP_BYTES_REMAIN(rsp, data_bytes_written);
    rsp[data_bytes_written + 1] = (wca_priv.encoded_proto_rsp_ctx.size > UINT8_MAX)
                                    ? UINT8_MAX
                                    : wca_priv.encoded_proto_rsp_ctx.size;
  } else {
    RSP_OK(rsp, data_bytes_written);
  }

  *rsp_len = data_bytes_written + SW_SIZE;
}

static bool handle_proto_exchange(uint8_t* rsp, uint32_t* rsp_len) {
  clear_proto_rsp_ctx();

#ifndef EMBEDDED_BUILD
  return true;  // TODO Replace with function hook for unit tests
#endif

  // Send proto to receiving task.
  bool status =
    ipc_proto_route(wca_priv.encoded_proto_cmd_ctx.tag, wca_priv.encoded_proto_cmd_ctx.buffer,
                    wca_priv.encoded_proto_cmd_ctx.size);
  if (!status) {
    MFLOGE("Failed to route proto %d", wca_priv.encoded_proto_cmd_ctx.tag);
    UI_SHOW_EVENT(UI_EVENT_NFC_ERROR);
    clear_proto_state();
    return false;
  }

  status = wca_priv.encoded_proto_rsp_ctx.sem_take();  // Wait until task gives us a response

  if (!status) {
    // Expired.
    MFLOGE("Proto rsp timeout");
    UI_SHOW_EVENT(UI_EVENT_NFC_ERROR);
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    clear_proto_state();
    return false;
  }

  // Handle response
  drain_response_buffer(rsp, rsp_len);

  return true;
}

void wca_init(wca_api_t* api) {
  wca_priv.mempool = api->mempool;
  wca_reset_session_state();
  wca_priv.encoded_proto_rsp_ctx.sem_take = api->sem_take;
  wca_priv.encoded_proto_rsp_ctx.sem_take_nowait = api->sem_take_nowait;
  wca_priv.encoded_proto_rsp_ctx.sem_give = api->sem_give;
  ipc_proto_register_api(wca_priv.mempool, wca_priv.encoded_proto_rsp_ctx.buffer,
                         &handle_proto_response);
}

bool wca_handle_command(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len) {
  ASSERT(*rsp_len >= SW_SIZE);

  if (!wca_is_valid(cmd, cmd_len)) {
    goto err;
  }

  // if updating this switch, propagate changes to wca_fuzz.cc
  switch (cmd[INS]) {
    case WCA_INS_VERSION:
      return wca_version(cmd, cmd_len, rsp, rsp_len);
    case WCA_INS_PROTO:
      return wca_proto(cmd, cmd_len, rsp, rsp_len);
    case WCA_INS_PROTO_CONT:
      return wca_proto_cont(cmd, cmd_len, rsp, rsp_len);
    case WCA_INS_GET_RESPONSE:
      return wca_get_response(cmd, cmd_len, rsp, rsp_len);
    default:
      goto err;
  }

err:
  clear_proto_state();
  *rsp_len = SW_SIZE;
  RSP_UNSUPPORTED_INS(rsp, 0);
  return false;
}

bool wca_version(uint8_t* UNUSED(cmd), uint32_t UNUSED(cmd_len), uint8_t* rsp, uint32_t* rsp_len) {
  *rsp_len = 4;
  uint16_t be_version = htons(WCA_VERSION);
  memcpy(rsp, &be_version, sizeof(WCA_VERSION));
  RSP_OK(rsp, 2);
  return true;
}

bool wca_proto(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len) {
  uint32_t in_len = *rsp_len;
  *rsp_len = SW_SIZE;
  clear_proto_state();

  if (!wca_has_readable_lc(cmd, cmd_len)) {
    goto err;
  }

  const uint32_t desired_size = (cmd[P1] << 8) | cmd[P2];
  if (desired_size > COMMAND_BUFFER_SIZE) {
    goto err;
  }

  uint16_t num_bytes = lc_to_int(&cmd[LC]);
  uint16_t data_off = is_short_coding(num_bytes) ? LC + 1 : LC + 3;

  const uint32_t remaining_cmd_bytes = cmd_len - data_off;
  if (remaining_cmd_bytes > cmd_len || num_bytes > remaining_cmd_bytes) {
    goto err;
  }

  // Determine which message in the oneof was sent.
  pb_wire_type_t wire_type;
  uint32_t tag = 0;
  bool eof = false;
  pb_istream_t istream = pb_istream_from_buffer(&cmd[data_off], remaining_cmd_bytes);
  if (!pb_decode_tag(&istream, &wire_type, &tag, &eof)) {
    MFLOGE("Failed to check tag");
    goto err;
  }
  wca_priv.encoded_proto_cmd_ctx.tag = tag;
  wca_priv.encoded_proto_cmd_ctx.size = desired_size;  // Size of the full protobuf
  wca_priv.encoded_proto_cmd_ctx.offset = 0;

  if (num_bytes > wca_priv.encoded_proto_cmd_ctx.size) {
    goto err;
  }

  memcpy(wca_priv.encoded_proto_cmd_ctx.buffer, &cmd[data_off], num_bytes);
  wca_priv.encoded_proto_cmd_ctx.offset += num_bytes;

  if (received_full_proto()) {
    *rsp_len = in_len;
    return handle_proto_exchange(rsp, rsp_len);  // Status words set internally
  }

  RSP_OK(rsp, 0);
  return true;
err:
  clear_proto_state();
  RSP_FCI_GENERIC_FAILURE(rsp, 0);
  return false;
}

bool wca_proto_cont(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len) {
  uint32_t in_len = *rsp_len;
  *rsp_len = SW_SIZE;

  if (!wca_has_readable_lc(cmd, cmd_len)) {
    goto err;
  }

  uint16_t num_bytes = lc_to_int(&cmd[LC]);
  uint16_t data_off = is_short_coding(num_bytes) ? LC + 1 : LC + 3;

  const uint32_t remaining_cmd_bytes = cmd_len - data_off;
  if (remaining_cmd_bytes > cmd_len || num_bytes > remaining_cmd_bytes) {
    goto err;
  }

  if ((wca_priv.encoded_proto_cmd_ctx.size == 0) || received_full_proto()) {
    goto err;
  }

  if (wca_priv.encoded_proto_cmd_ctx.offset > wca_priv.encoded_proto_cmd_ctx.size) {
    goto err;
  }

  const uint32_t max_proto_bytes =
    wca_priv.encoded_proto_cmd_ctx.size - wca_priv.encoded_proto_cmd_ctx.offset;
  if (num_bytes > max_proto_bytes) {
    goto err;
  }

  memcpy(&wca_priv.encoded_proto_cmd_ctx.buffer[wca_priv.encoded_proto_cmd_ctx.offset],
         &cmd[data_off], num_bytes);
  wca_priv.encoded_proto_cmd_ctx.offset += num_bytes;

  if (received_full_proto()) {
    *rsp_len = in_len;
    return handle_proto_exchange(rsp, rsp_len);  // Status words set internally
  }

  RSP_OK(rsp, 0);
  return true;

err:
  clear_proto_state();
  RSP_FCI_GENERIC_FAILURE(rsp, 0);
  return false;
}

bool wca_get_response(uint8_t* UNUSED(cmd), uint32_t UNUSED(cmd_len), uint8_t* rsp,
                      uint32_t* rsp_len) {
  if (wca_priv.encoded_proto_rsp_ctx.size == 0) {
    clear_proto_state();
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    *rsp_len = SW_SIZE;
    return false;
  }

  drain_response_buffer(rsp, rsp_len);
  return true;
}

bool wca_is_wca(const uint8_t* cmd, uint32_t cmd_len) {
  // This is an NFC demux check, not full command validation. Route any frame
  // with the WCA CLA byte to wca_handle_command() so malformed WCA traffic can
  // still receive APDU status words instead of being treated as unknown NFC.
  return cmd != NULL && cmd_len > CLA && cmd[CLA] == WCA_CLA;
}

bool wca_is_valid(const uint8_t* cmd, uint32_t cmd_len) {
  // Semantic command validation needs the four-byte APDU header so handlers can
  // safely inspect INS/P1/P2. Data-bearing commands get an extra Lc check below
  // before dispatch because their handlers parse the length field at cmd[4].
  if ((cmd_len < (P2 + 1)) || !wca_is_wca(cmd, cmd_len)) {
    return false;
  }

  // PROTO and PROTO_CONT carry protobuf bytes, so they must include a readable
  // Lc field. Without this, a four-byte APDU would pass routing and the handler
  // would read past the end of the command buffer.
  if (cmd[INS] == WCA_INS_PROTO || cmd[INS] == WCA_INS_PROTO_CONT) {
    return wca_has_readable_lc(cmd, cmd_len);
  }

  return true;
}
