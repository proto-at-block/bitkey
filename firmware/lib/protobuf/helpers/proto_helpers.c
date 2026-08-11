#include "proto_helpers.h"

#include "aes.h"
#include "assert.h"
#include "attributes.h"
#include "ipc.h"
#include "log.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "wallet.pb.h"
#include "wstring.h"

// Stale response detection: the NFC thread binds cmd_seq into the routed IPC
// command before queueing it. proto_get_cmd copies that immutable per-message
// seq into the decoded cmd allocation, and proto_send_rsp echoes it back into
// the IPC response sequence.
//
// For async coprocessor callbacks (where cmd was freed before the response
// arrives), callers must carry the seq through the async responder and use
// proto_send_rsp_with_seq() to echo the correct value.
//
// Responses whose seq is zero or does not match g_cmd_seq are stale and are
// dropped while holding the shared response buffer lock. This keeps a late
// producer from evicting the active response if task priorities change in the
// future.
//
// UXC-backed async responses echo the seq over the Core/UXC wire (uxc_msg.seq).
// A response with no wire seq is uncorrelatable and is dropped by
// proto_uxc_take_rsp_seq(). FWUP has a narrow local fallback so old UXC firmware
// can still be updated over NFC.
static uint32_t g_cmd_seq SHARED_TASK_DATA = 0;

void proto_set_cmd_seq(uint32_t seq) {
  g_cmd_seq = seq;
}

void proto_retire_cmd_seq(uint32_t seq) {
  (void)ipc_proto_get_response_buffer();  // Takes mutex shared with response producers.
  if (g_cmd_seq == seq) {
    // WCA command sequences never use zero. After a timeout or NFC session
    // reset, retire the active seq so a late producer for the old command
    // cannot populate GET_RESPONSE in this or a later NFC session.
    g_cmd_seq = 0;
  }
  if (ipc_proto_get_response_seq() == seq) {
    ipc_proto_set_response_seq(0);
  }
  ipc_proto_release_response_buffer();
}

void proto_set_rsp_seq(uint32_t seq) {
  ipc_proto_set_response_seq(seq);
}

uint32_t proto_get_last_rsp_seq(void) {
  return ipc_proto_get_response_seq();
}

// Store cmd_seq at the end of the decoded cmd allocation so each task owns its
// response correlation state.
#define CMD_METADATA_PTR(cmd) ((proto_cmd_metadata_t*)((uint8_t*)(cmd) + PROTO_CMD_METADATA_OFFSET))

uint32_t proto_get_cmd_seq(const fwpb_wallet_cmd* cmd) {
  ASSERT(cmd != NULL);
  return CMD_METADATA_PTR(cmd)->seq;
}

fwpb_wallet_cmd* proto_get_cmd(uint8_t* serialized_cmd, uint32_t length) {
  ASSERT(serialized_cmd);

  uint8_t* encoded_cmd = serialized_cmd;
  uint32_t encoded_length = length;
  uint32_t cmd_seq = g_cmd_seq;
  uint8_t* routed_cmd = NULL;

  // Production WCA task routing passes pool-owned ipc_proto_routed_cmd_t buffers
  // here. Raw host/fuzz decode callers must use non-pool buffers; otherwise the
  // first bytes of protobuf data would be interpreted as route metadata.
  if (ipc_proto_owns_buffer(serialized_cmd) && length >= sizeof(ipc_proto_routed_cmd_t)) {
    ipc_proto_routed_cmd_t* routed = (ipc_proto_routed_cmd_t*)serialized_cmd;
    encoded_cmd = routed->encoded_cmd;
    encoded_length = length - sizeof(ipc_proto_routed_cmd_t);
    cmd_seq = routed->seq;
    routed_cmd = serialized_cmd;
  }

  fwpb_wallet_cmd* cmd = (fwpb_wallet_cmd*)ipc_proto_alloc(PROTO_DECODED_CMD_ALLOC_SIZE);
  ASSERT(cmd);
  memzero(cmd, PROTO_DECODED_CMD_ALLOC_SIZE);
  CMD_METADATA_PTR(cmd)->seq = cmd_seq;

  pb_istream_t istream = pb_istream_from_buffer(encoded_cmd, encoded_length);
  if (!pb_decode(&istream, fwpb_wallet_cmd_fields, cmd)) {
    LOGE("proto decode fail");
    goto fail;
  }

  if (routed_cmd != NULL) {
    ipc_proto_free(routed_cmd);
  }

  static auth_set_timestamp_t SHARED_TASK_BSS msg;
  msg.timestamp = cmd->timestamp;
  ipc_send(auth_port, &msg, sizeof(msg), IPC_AUTH_SET_TIMESTAMP);

  return cmd;

fail:
  if (routed_cmd != NULL) {
    ipc_proto_free(routed_cmd);
  }
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

static bool send_rsp_internal(uint32_t seq, fwpb_wallet_rsp* rsp) {
  ASSERT(rsp != NULL);

  uint8_t* buffer = ipc_proto_get_response_buffer();  // Takes mutex
  if ((seq == 0) || (seq != g_cmd_seq)) {
    LOGW("Dropping stale proto response seq %lu current %lu", (unsigned long)seq,
         (unsigned long)g_cmd_seq);
    ipc_proto_release_response_buffer();
    return false;
  }

  uint32_t proto_length = encode_rsp_with_fallback(buffer, rsp);
  // Set g_rsp_seq under the mutex, before giving the semaphore.
  // This ensures the seq is atomically paired with the buffer contents.
  ipc_proto_set_response_seq(seq);
  ipc_proto_send_response_buffer(buffer, proto_length);  // Gives sem, releases mutex
  return true;
}

void proto_send_rsp_without_free(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  ASSERT(cmd != NULL);
  uint32_t seq = CMD_METADATA_PTR(cmd)->seq;
  send_rsp_internal(seq, rsp);
}

void proto_free_buffers(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  if (cmd != NULL) {
    ipc_proto_free((uint8_t*)cmd);
  }
  if (rsp != NULL) {
    ipc_proto_free((uint8_t*)rsp);
  }
}

bool proto_send_rsp_with_seq(uint32_t seq, fwpb_wallet_rsp* rsp) {
  bool sent = send_rsp_internal(seq, rsp);
  proto_free_buffers(NULL, rsp);
  return sent;
}

void proto_send_rsp(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  ASSERT(cmd != NULL);
  // Use per-allocation seq (correct even if g_cmd_seq was advanced for a newer cmd).
  // Async coprocessor callbacks must use proto_send_rsp_with_seq() instead.
  uint32_t seq = CMD_METADATA_PTR(cmd)->seq;
  send_rsp_internal(seq, rsp);
  proto_free_buffers(cmd, rsp);
}

void proto_uxc_prepare_cmd(fwpb_uxc_msg_host* msg, const fwpb_wallet_cmd* cmd) {
  ASSERT(msg != NULL);
  ASSERT(cmd != NULL);

  // NFC-forwarded UXC commands must call this before uc_send(). UXC mirrors this
  // into its response so Core can correlate it.
  msg->seq = proto_get_cmd_seq(cmd);
}

bool proto_uxc_take_rsp_seq(const fwpb_uxc_msg_device* msg, uint32_t* seq, const char* name) {
  ASSERT(msg != NULL);
  ASSERT(seq != NULL);

  if (msg->seq != 0) {
    *seq = msg->seq;
    return true;
  }

  LOGW("%s response missing seq", name);
  return false;
}
