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
#define REGIONS(X) X(proto_helpers_test_pool, proto_scratch, sizeof(fwpb_wallet_rsp), 4)
  pool = mempool_create(proto_helpers_test_pool);
#undef REGIONS

  captured_rsp_len = 0;
  memzero(proto_rsp_buffer, sizeof(proto_rsp_buffer));
  memzero(captured_rsp, sizeof(captured_rsp));
  ipc_proto_register_api(pool, proto_rsp_buffer, &proto_ready_cb);
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
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_SUCCESS;
  rsp->which_msg = fwpb_wallet_rsp_empty_rsp_tag;
  rsp->response_handle.size = sizeof(rsp->response_handle.bytes) + 1;

  proto_send_rsp_without_free(rsp);

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

  proto_free_buffers(NULL, rsp);
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
