#include "proto_helpers.h"

#include "assert.h"
#include "ipc.h"
#include "log.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "wallet.pb.h"
#include "wstring.h"

fwpb_wallet_cmd* proto_get_cmd(uint8_t* serialized_cmd, uint32_t length) {
  ASSERT(serialized_cmd);

  fwpb_wallet_cmd* cmd = (fwpb_wallet_cmd*)ipc_proto_alloc(sizeof(fwpb_wallet_cmd));
  ASSERT(cmd);
  memzero(cmd, sizeof(fwpb_wallet_cmd));

  pb_istream_t istream = pb_istream_from_buffer(serialized_cmd, length);
  if (!pb_decode(&istream, fwpb_wallet_cmd_fields, cmd)) {
    LOGE("failed to decode proto");
    goto fail;
  }

  static auth_set_timestamp_t SHARED_TASK_DATA msg;
  msg.timestamp = cmd->timestamp;
  ipc_send(auth_port, &msg, sizeof(msg), IPC_AUTH_SET_TIMESTAMP);

  return cmd;

fail:
  ipc_proto_free((uint8_t*)cmd);
  ASSERT_EMBEDDED_ONLY(false);  // Don't do anything with a malformed proto.
  return NULL;
}

fwpb_wallet_rsp* proto_get_rsp(void) {
  fwpb_wallet_rsp* rsp = (fwpb_wallet_rsp*)ipc_proto_alloc(sizeof(fwpb_wallet_rsp));
  ASSERT(rsp);
  memzero(rsp, sizeof(fwpb_wallet_rsp));
  return rsp;
}

void proto_send_rsp_without_free(fwpb_wallet_rsp* rsp) {
  uint8_t* buffer = ipc_proto_get_response_buffer();
  pb_ostream_t ostream = pb_ostream_from_buffer(buffer, sizeof(fwpb_wallet_rsp));

  if (!pb_encode(&ostream, fwpb_wallet_rsp_fields, rsp)) {
    LOGE("failed to encode proto");
    //  TODO Change to a generic error? For now just send it anyway.
  }

  uint32_t proto_length = ostream.bytes_written;
  ipc_proto_send_response_buffer(buffer, proto_length);
}

void proto_free_buffers(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  if (cmd != NULL) {
    ipc_proto_free((uint8_t*)cmd);
  }
  if (rsp != NULL) {
    ipc_proto_free((uint8_t*)rsp);
  }
}

void proto_send_rsp(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  proto_send_rsp_without_free(rsp);
  proto_free_buffers(cmd, rsp);
}
