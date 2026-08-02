#include "aes.h"
#include "arithmetic.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "ipc.h"
#include "mempool.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "proto_helpers.h"
#include "secutils.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdlib.h>
#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);
FAKE_VALUE_FUNC(bool, rtos_in_isr);
FAKE_VOID_FUNC(refresh_auth);

secure_bool_t onboarding_complete(void) {
  return SECURE_TRUE;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}
secure_bool_t is_allowing_fingerprint_enrollment(void) {
  return SECURE_TRUE;
}

static mempool_t* pool = NULL;
static uint8_t proto_rsp_buffer[fwpb_wallet_rsp_size] = {0};
static uint8_t captured_rsp[fwpb_wallet_rsp_size] = {0};
static uint32_t captured_rsp_len = 0;

static void proto_ready_cb(uint8_t* encoded_proto, uint32_t size) {
  captured_rsp_len = size;
  memzero(captured_rsp, sizeof(captured_rsp));

  if ((encoded_proto != NULL) && (size <= sizeof(captured_rsp))) {
    memcpy(captured_rsp, encoded_proto, size);
  }
}

static void setup(void) {
#define REGIONS(X)                                                       \
  X(proto_helpers_test_pool, proto_cmd_scratch, PROTO_CMD_ALLOC_SIZE, 2) \
  X(proto_helpers_test_pool, proto_rsp_scratch, sizeof(fwpb_wallet_rsp), 4)
  pool = mempool_create(proto_helpers_test_pool);
#undef REGIONS

  captured_rsp_len = 0;
  memzero(proto_rsp_buffer, sizeof(proto_rsp_buffer));
  memzero(captured_rsp, sizeof(captured_rsp));
  ipc_proto_register_api(pool, proto_rsp_buffer, &proto_ready_cb);
}

static fwpb_wallet_cmd* make_cmd_with_seq(uint32_t seq) {
  fwpb_wallet_cmd sent_cmd = fwpb_wallet_cmd_init_default;
  sent_cmd.which_msg = fwpb_wallet_cmd_query_authentication_cmd_tag;

  uint8_t encoded_cmd[fwpb_wallet_cmd_size] = {0};
  pb_ostream_t ostream = pb_ostream_from_buffer(encoded_cmd, sizeof(encoded_cmd));
  cr_assert(pb_encode(&ostream, fwpb_wallet_cmd_fields, &sent_cmd));

  proto_set_cmd_seq(seq);
  fwpb_wallet_cmd* cmd = proto_get_cmd(encoded_cmd, ostream.bytes_written);
  cr_assert_not_null(cmd);
  return cmd;
}

Test(proto_helpers, fill_bytes) {
  fwpb_wallet_rsp response_proto = fwpb_wallet_rsp_init_default;
  response_proto.which_msg = fwpb_wallet_rsp_derive_rsp_tag;
  response_proto.msg.derive_rsp.has_descriptor = true;
  response_proto.msg.derive_rsp.descriptor.has_origin_path = true;

  uint8_t origin_fingerprint[] = {0x37, 0x24, 0x9c, 0xd2};

  PROTO_FILL_BYTES(&response_proto, msg.derive_rsp.descriptor.origin_fingerprint,
                   origin_fingerprint, sizeof(origin_fingerprint));
  cr_assert(response_proto.msg.derive_rsp.descriptor.origin_fingerprint.size ==
            sizeof(origin_fingerprint));
  cr_util_cmp_buffers(response_proto.msg.derive_rsp.descriptor.origin_fingerprint.bytes,
                      origin_fingerprint, sizeof(origin_fingerprint));
}

Test(proto_helpers, set_repeated) {
  fwpb_wallet_rsp response_proto = fwpb_wallet_rsp_init_default;
  response_proto.which_msg = fwpb_wallet_rsp_derive_rsp_tag;
  response_proto.msg.derive_rsp.has_descriptor = true;
  response_proto.msg.derive_rsp.descriptor.has_origin_path = true;

  uint32_t origin_derivation_path[] = {84, 1, 0};

  PROTO_FILL_REPEATED(&response_proto, msg.derive_rsp.descriptor.origin_path,
                      origin_derivation_path, ARRAY_SIZE(origin_derivation_path));

  cr_assert(response_proto.msg.derive_rsp.descriptor.origin_path.child_count == 3);
  cr_util_cmp_buffers(response_proto.msg.derive_rsp.descriptor.origin_path.child,
                      origin_derivation_path, 3);
}

Test(proto_helpers, encode_failure_falls_back_to_minimal_error_response, .init = setup) {
  fwpb_wallet_cmd* cmd = make_cmd_with_seq(12);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_empty_rsp_tag;
  rsp->response_handle.size = sizeof(rsp->response_handle.bytes) + 1;

  proto_send_rsp_without_free(cmd, rsp);

  cr_assert_gt(captured_rsp_len, 0);

  fwpb_wallet_rsp decoded_rsp = fwpb_wallet_rsp_init_default;
  pb_istream_t istream = pb_istream_from_buffer(captured_rsp, captured_rsp_len);
  cr_assert(pb_decode(&istream, fwpb_wallet_rsp_fields, &decoded_rsp));
  cr_assert_eq(decoded_rsp.status, fwpb_status_ERROR);
  cr_assert_eq(decoded_rsp.which_msg, 0);
  cr_assert_eq(decoded_rsp.response_handle.size, 0);
  cr_assert_eq(decoded_rsp.confirmation_handle.size, 0);

  cr_assert_eq(rsp->status, fwpb_status_SUCCESS);
  cr_assert_eq(rsp->which_msg, fwpb_wallet_rsp_empty_rsp_tag);
  cr_assert_eq(rsp->response_handle.size, sizeof(rsp->response_handle.bytes) + 1);

  proto_free_buffers(cmd, rsp);
}

Test(proto_helpers, routed_command_uses_bound_seq, .init = setup) {
  fwpb_wallet_cmd sent_cmd = fwpb_wallet_cmd_init_default;
  sent_cmd.which_msg = fwpb_wallet_cmd_query_authentication_cmd_tag;

  uint8_t encoded_cmd[fwpb_wallet_cmd_size] = {0};
  pb_ostream_t ostream = pb_ostream_from_buffer(encoded_cmd, sizeof(encoded_cmd));
  cr_assert(pb_encode(&ostream, fwpb_wallet_cmd_fields, &sent_cmd));

  const uint32_t routed_size = sizeof(ipc_proto_routed_cmd_t) + ostream.bytes_written;
  ipc_proto_routed_cmd_t* routed_cmd = (ipc_proto_routed_cmd_t*)ipc_proto_alloc(routed_size);
  cr_assert_not_null(routed_cmd);
  routed_cmd->seq = 42;
  memcpy(routed_cmd->encoded_cmd, encoded_cmd, ostream.bytes_written);

  proto_set_cmd_seq(99);
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)routed_cmd, routed_size);
  cr_assert_not_null(cmd);
  cr_assert_eq(proto_get_cmd_seq(cmd), 42);

  proto_free_buffers(cmd, NULL);
}

Test(proto_helpers, stale_response_is_dropped_before_send, .init = setup) {
  fwpb_wallet_cmd* cmd = make_cmd_with_seq(10);
  proto_set_cmd_seq(11);
  proto_set_rsp_seq(11);

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;
  proto_send_rsp(cmd, rsp);

  cr_assert_eq(captured_rsp_len, 0);
  cr_assert_eq(proto_get_last_rsp_seq(), 11);
}

Test(proto_helpers, send_rsp_with_seq_reports_stale_drop, .init = setup) {
  proto_set_cmd_seq(22);

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;

  cr_assert_not(proto_send_rsp_with_seq(21, rsp));
  cr_assert_eq(captured_rsp_len, 0);
}

Test(proto_helpers, retired_cmd_seq_drops_late_same_seq_response, .init = setup) {
  proto_set_cmd_seq(33);
  proto_set_rsp_seq(33);
  proto_retire_cmd_seq(33);
  cr_assert_eq(proto_get_last_rsp_seq(), 0);

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;

  cr_assert_not(proto_send_rsp_with_seq(33, rsp));
  cr_assert_eq(captured_rsp_len, 0);
}

Test(proto_helpers, retiring_old_seq_preserves_newer_active_seq, .init = setup) {
  proto_set_cmd_seq(44);
  proto_set_rsp_seq(44);
  proto_retire_cmd_seq(43);

  cr_assert_eq(proto_get_last_rsp_seq(), 44);

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;

  cr_assert(proto_send_rsp_with_seq(44, rsp));
  cr_assert_gt(captured_rsp_len, 0);
}

Test(proto_helpers, zero_seq_response_is_never_accepted, .init = setup) {
  proto_set_cmd_seq(0);

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;

  cr_assert_not(proto_send_rsp_with_seq(0, rsp));
  cr_assert_eq(captured_rsp_len, 0);
}

Test(proto_helpers, uxc_prepare_cmd_sets_wire_seq, .init = setup) {
  fwpb_wallet_cmd* cmd = make_cmd_with_seq(123);
  fwpb_uxc_msg_host host = fwpb_uxc_msg_host_init_default;
  host.which_msg = fwpb_uxc_msg_host_meta_cmd_tag;

  proto_uxc_prepare_cmd(&host, cmd);
  cr_assert_eq(host.seq, 123);

  proto_free_buffers(cmd, NULL);
}

Test(proto_helpers, uxc_rsp_seq_uses_wire_seq, .init = setup) {
  fwpb_uxc_msg_device device = fwpb_uxc_msg_device_init_default;
  device.seq = 456;
  device.which_msg = fwpb_uxc_msg_device_events_get_rsp_tag;

  uint32_t seq = 0;
  cr_assert(proto_uxc_take_rsp_seq(&device, &seq, "test"));
  cr_assert_eq(seq, 456);
}

Test(proto_helpers, uxc_rsp_seq_rejects_missing_seq, .init = setup) {
  proto_set_cmd_seq(789);

  fwpb_uxc_msg_device device = fwpb_uxc_msg_device_init_default;
  device.which_msg = fwpb_uxc_msg_device_fwup_start_rsp_tag;

  uint32_t seq = 0;
  cr_assert_not(proto_uxc_take_rsp_seq(&device, &seq, "test"));
  cr_assert_eq(seq, 0);
}

Test(proto_helpers, secure_channel_message_requires_exact_sizes) {
  fwpb_secure_channel_message message = fwpb_secure_channel_message_init_default;

  message.ciphertext.size = 32;
  message.nonce.size = AES_GCM_IV_LENGTH;
  message.mac.size = AES_GCM_TAG_LENGTH;

  cr_assert(proto_secure_channel_message_has_valid_sizes(&message, 32));

  message.ciphertext.size = 31;
  cr_assert_not(proto_secure_channel_message_has_valid_sizes(&message, 32));

  message.ciphertext.size = 32;
  message.nonce.size = AES_GCM_IV_LENGTH - 1;
  cr_assert_not(proto_secure_channel_message_has_valid_sizes(&message, 32));

  message.nonce.size = AES_GCM_IV_LENGTH;
  message.mac.size = AES_GCM_TAG_LENGTH - 1;
  cr_assert_not(proto_secure_channel_message_has_valid_sizes(&message, 32));

  cr_assert_not(proto_secure_channel_message_has_valid_sizes(NULL, 32));
}
