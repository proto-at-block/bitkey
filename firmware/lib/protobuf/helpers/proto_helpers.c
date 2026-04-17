#include "proto_helpers.h"

#include "aes.h"
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
    LOGE("proto decode fail");
    goto fail;
  }

  static auth_set_timestamp_t SHARED_TASK_BSS msg;
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

_Static_assert(sizeof(((fwpb_secure_channel_message*)0)->nonce.bytes) == AES_GCM_IV_LENGTH,
               "secure channel nonce protobuf size must match AES-GCM IV length");
_Static_assert(sizeof(((fwpb_secure_channel_message*)0)->mac.bytes) == AES_GCM_TAG_LENGTH,
               "secure channel mac protobuf size must match AES-GCM tag length");

bool proto_secure_channel_message_has_valid_sizes(const fwpb_secure_channel_message* message,
                                                  size_t ciphertext_size) {
  if (message == NULL) {
    return false;
  }

  return message->ciphertext.size == ciphertext_size && message->nonce.size == AES_GCM_IV_LENGTH &&
         message->mac.size == AES_GCM_TAG_LENGTH;
}

static uint32_t encode_rsp_with_fallback(uint8_t* buffer, const fwpb_wallet_rsp* rsp) {
  // Keep the fallback response out of task stacks. fwpb_wallet_rsp is ~2.4 KiB on W3, while
  // several task stacks are 2 KiB, so a local fallback object can hard fault while recovering
  // from an encode error.
  static const fwpb_wallet_rsp error_rsp = {
    .status = fwpb_status_ERROR,
  };
  pb_ostream_t ostream = pb_ostream_from_buffer(buffer, fwpb_wallet_rsp_size);

  if (!pb_encode(&ostream, fwpb_wallet_rsp_fields, rsp)) {
    LOGE("proto encode fail");

    memzero(buffer, fwpb_wallet_rsp_size);
    ostream = pb_ostream_from_buffer(buffer, fwpb_wallet_rsp_size);
    if (!pb_encode(&ostream, fwpb_wallet_rsp_fields, &error_rsp)) {
      ASSERT(false);
      return 0;
    }
  }

  return ostream.bytes_written;
}

void proto_send_rsp_without_free(fwpb_wallet_rsp* rsp) {
  uint8_t* buffer = ipc_proto_get_response_buffer();
  uint32_t proto_length = encode_rsp_with_fallback(buffer, rsp);
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
