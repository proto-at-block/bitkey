#include "cobs.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "uc.h"
#include "uc_impl.h"
#include "uc_route.h"
#include "uc_route_impl.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdint.h>
#include <string.h>

#define UC_TEST_BUF_SIZE 1024u

DEFINE_FFF_GLOBALS;

// RTOS Event Group
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_get_bits, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(bool, rtos_event_group_set_bits_from_isr, rtos_event_group_t*, const uint32_t,
                bool*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*, const uint32_t, bool,
                bool, uint32_t);

// RTOS Mutex
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

// RTOS Queue
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);

// RTOS Semaphore
FAKE_VOID_FUNC(rtos_semaphore_create_counting, rtos_semaphore_t*, uint32_t, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_semaphore_give, rtos_semaphore_t*);
FAKE_VALUE_FUNC(bool, rtos_semaphore_take, rtos_semaphore_t*, uint32_t);

// RTOS Timer
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);
FAKE_VOID_FUNC(rtos_timer_restart, rtos_timer_t*);
FAKE_VALUE_FUNC(bool, rtos_timer_expired, rtos_timer_t*);

typedef struct {
  uint8_t* enc_buffer;
  size_t enc_buffer_len;
  size_t enc_len;
  size_t call_count;
} send_context_t;

typedef struct {
  fwpb_uxc_msg_host msg_host;
  fwpb_uxc_msg_device msg_device;
  uint32_t proto_tag;
  bool host;
  size_t call_count;
} recv_context_t;

static send_context_t send_ctx = {0};

static void on_recv(void* context, uint32_t proto_tag, void* proto, size_t proto_len) {
  if (context == NULL) {
    return;
  }

  recv_context_t* recv_context = context;
  if (recv_context->host) {
    cr_assert_eq(sizeof(fwpb_uxc_msg_host), proto_len);
    recv_context->msg_host = *(fwpb_uxc_msg_host*)proto;
  } else {
    cr_assert_eq(sizeof(fwpb_uxc_msg_device), proto_len);
    recv_context->msg_device = *(fwpb_uxc_msg_device*)proto;
  }

  recv_context->proto_tag = proto_tag;
  recv_context->call_count++;
}

static uint32_t on_send(void* context, const uint8_t* data, size_t data_len) {
  if (context == NULL) {
    return 0;
  }

  send_context_t* send_context = context;
  send_context->call_count++;

  if (send_context->enc_buffer == NULL) {
    return 0;
  }

  const size_t rd_size =
    (data_len > send_context->enc_buffer_len ? send_context->enc_buffer_len : data_len);
  memcpy(&send_context->enc_buffer[send_context->enc_len], data, rd_size);
  send_context->enc_len += rd_size;
  return rd_size;
}

static void on_proto(void* proto, void* context) {
  cr_assert(context != NULL);
  *((void**)context) = proto;
}

static void test_uc_init_mode_device(void) {
  memset(&send_ctx, 0u, sizeof(send_ctx));

  uc_init_mode(fwpb_uxc_msg_device_fields, sizeof(fwpb_uxc_msg_device), fwpb_uxc_msg_host_fields,
               sizeof(fwpb_uxc_msg_host), on_send, &send_ctx);
}

static void test_uc_init_mode_host(void) {
  memset(&send_ctx, 0u, sizeof(send_ctx));

  uc_init_mode(fwpb_uxc_msg_host_fields, sizeof(fwpb_uxc_msg_host), fwpb_uxc_msg_device_fields,
               sizeof(fwpb_uxc_msg_device), on_send, &send_ctx);
}

Test(uc, crc16) {
  uc_msg_hdr_t hdr = {0};
  uint8_t payload[] = {0x49, 0x27, 0x6D, 0x20, 0x77, 0x6F, 0x72, 0x72, 0x69, 0x65, 0x64,
                       0x20, 0x74, 0x68, 0x61, 0x74, 0x20, 0x74, 0x68, 0x65, 0x20, 0x62,
                       0x61, 0x62, 0x79, 0x20, 0x74, 0x68, 0x69, 0x6E, 0x6B, 0x73, 0x20,
                       0x70, 0x65, 0x6F, 0x70, 0x6C, 0x65, 0x20, 0x63, 0x61, 0x6E, 0x27,
                       0x74, 0x20, 0x63, 0x68, 0x61, 0x6E, 0x67, 0x65, 0x2E};
  uint8_t msg_data[sizeof(uc_msg_hdr_t) + sizeof(payload)];

  hdr.proto_tag = 0x1337u;
  hdr.send_seq_num = 0x13u;
  hdr.ack_seq_num = 0x7u;
  hdr.flags = UC_MSG_HDR_FLAGS_ACK;
  hdr.payload_len = sizeof(payload);
  memcpy(&msg_data[0], &hdr, sizeof(hdr));
  memcpy(&msg_data[sizeof(hdr)], payload, sizeof(payload));

  cr_assert_eq(48783, uc_compute_crc((uc_msg_t*)msg_data));
}

Test(uc, init_device) {
  test_uc_init_mode_device();
}

Test(uc, init_host) {
  test_uc_init_mode_host();
}

Test(uc, decode_crc_mismatch) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;

  uint8_t payload[] = {0x47, 0x69, 0x6D, 0x6D, 0x65, 0x20, 0x74, 0x68, 0x61, 0x74, 0x2E};
  uint8_t data[sizeof(uc_msg_hdr_t) + sizeof(payload)];
  uc_msg_t* msg = (uc_msg_t*)data;

  memcpy(msg->payload, payload, sizeof(payload));

  msg->hdr.proto_tag = 0x00u;
  msg->hdr.send_seq_num = 0x00u;
  msg->hdr.ack_seq_num = 0x00u;
  msg->hdr.flags = UC_MSG_HDR_FLAGS_ACK;
  msg->hdr.payload_len = sizeof(payload);
  msg->hdr.crc = ~uc_compute_crc((uc_msg_t*)data);

  uint32_t bytes_consumed;
  uint8_t enc[UC_TEST_BUF_SIZE];
  size_t enc_len;
  cobs_ret_t ret = cobs_encode(data, sizeof(data), enc, sizeof(enc), &enc_len);
  cr_assert_eq(COBS_RET_SUCCESS, ret);

  test_uc_init_mode_device();
  const uc_err_t err = uc_decode(enc, enc_len, NULL, NULL, &bytes_consumed);
  cr_assert_eq(UC_ERR_CRC_MISMATCH, err);
  cr_assert_eq(enc_len, bytes_consumed);

  // Ensure message buffer mutex was locked and unlocked.
  cr_assert_eq(1, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(1, rtos_mutex_unlock_fake.call_count);
}

Test(uc, decode_invalid_length) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;

  uint8_t payload[] = {0x54, 0x72, 0x69, 0x70, 0x6C, 0x65, 0x73, 0x20, 0x6D, 0x61,
                       0x6B, 0x65, 0x73, 0x20, 0x69, 0x74, 0x20, 0x73, 0x61, 0x66,
                       0x65, 0x2C, 0x20, 0x74, 0x72, 0x69, 0x70, 0x6C, 0x65, 0x73,
                       0x20, 0x69, 0x73, 0x20, 0x62, 0x65, 0x73, 0x74, 0x2E};
  uint8_t data[sizeof(uc_msg_hdr_t) + sizeof(payload)];
  uc_msg_t* msg = (uc_msg_t*)data;

  memcpy(msg->payload, payload, sizeof(payload));

  msg->hdr.proto_tag = 0x00u;
  msg->hdr.send_seq_num = 0x00u;
  msg->hdr.ack_seq_num = 0x00u;
  msg->hdr.flags = UC_MSG_HDR_FLAGS_ACK;
  msg->hdr.payload_len = sizeof(payload) - 1;
  msg->hdr.crc = uc_compute_crc((uc_msg_t*)data);

  uint32_t bytes_consumed;
  uint8_t enc[UC_TEST_BUF_SIZE];
  size_t enc_len;
  cobs_ret_t ret = cobs_encode(data, sizeof(data), enc, sizeof(enc), &enc_len);
  cr_assert_eq(COBS_RET_SUCCESS, ret);

  test_uc_init_mode_host();
  const uc_err_t err = uc_decode(enc, enc_len, NULL, NULL, &bytes_consumed);
  cr_assert_eq(UC_ERR_SIZE_MISMATCH, err);
  cr_assert_eq(enc_len, bytes_consumed);

  // Ensure message buffer mutex was locked and unlocked.
  cr_assert_eq(1, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(1, rtos_mutex_unlock_fake.call_count);
}

Test(uc, decode_plaintext) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;

  uint8_t payload[] = {
    0x35, 0x35, 0x20, 0x62, 0x75, 0x72, 0x67, 0x65, 0x72, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x66,
    0x72, 0x69, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x74, 0x61, 0x63, 0x6F, 0x73, 0x2C, 0x20,
    0x35, 0x35, 0x20, 0x70, 0x69, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x43, 0x6F, 0x6B, 0x65,
    0x73, 0x2C, 0x20, 0x31, 0x30, 0x30, 0x20, 0x74, 0x61, 0x74, 0x65, 0x72, 0x20, 0x74, 0x6F, 0x74,
    0x73, 0x2C, 0x20, 0x31, 0x30, 0x30, 0x20, 0x70, 0x69, 0x7A, 0x7A, 0x61, 0x73, 0x2C, 0x20, 0x31,
    0x30, 0x30, 0x20, 0x74, 0x65, 0x6E, 0x64, 0x65, 0x72, 0x73, 0x2C, 0x20, 0x31, 0x30, 0x30, 0x20,
    0x6D, 0x65, 0x61, 0x74, 0x62, 0x61, 0x6C, 0x6C, 0x73, 0x2C, 0x20, 0x31, 0x30, 0x30, 0x20, 0x63,
    0x6F, 0x66, 0x66, 0x65, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x77, 0x69, 0x6E, 0x67, 0x73,
    0x2C, 0x20, 0x35, 0x35, 0x20, 0x73, 0x68, 0x61, 0x6B, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20,
    0x70, 0x61, 0x6E, 0x63, 0x61, 0x6B, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x70, 0x61, 0x73,
    0x74, 0x61, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x70, 0x65, 0x70, 0x70, 0x65, 0x72, 0x73, 0x2C,
    0x20, 0x61, 0x6E, 0x64, 0x20, 0x31, 0x35, 0x35, 0x20, 0x74, 0x61, 0x74, 0x65, 0x72, 0x73};

  fwpb_uxc_msg_host uxc_msg = fwpb_uxc_msg_host_init_default;
  uxc_msg.which_msg = fwpb_uxc_msg_host_fwup_transfer_cmd_tag;
  uxc_msg.msg.fwup_transfer_cmd.sequence_id = 0x13;
  uxc_msg.msg.fwup_transfer_cmd.offset = 0;
  uxc_msg.msg.fwup_transfer_cmd.mode = fwpb_fwup_mode_FWUP_MODE_NORMAL;
  uxc_msg.msg.fwup_transfer_cmd.fwup_data.size = sizeof(payload);
  memcpy(uxc_msg.msg.fwup_transfer_cmd.fwup_data.bytes, payload, sizeof(payload));

  uint8_t data[UC_TEST_BUF_SIZE];
  uc_msg_t* msg = (uc_msg_t*)data;
  pb_ostream_t ostream = pb_ostream_from_buffer(msg->payload, sizeof(data) - sizeof(msg->hdr));
  cr_assert(pb_encode(&ostream, fwpb_uxc_msg_host_fields, &uxc_msg));

  msg->hdr.proto_tag = fwpb_uxc_msg_host_fwup_transfer_cmd_tag;
  msg->hdr.send_seq_num = 0x01u;
  msg->hdr.ack_seq_num = 0x00u;
  msg->hdr.flags = UC_MSG_HDR_FLAGS_ACK;
  msg->hdr.payload_len = ostream.bytes_written;
  msg->hdr.crc = uc_compute_crc(msg);

  uint32_t bytes_consumed;
  uint8_t enc[UC_TEST_BUF_SIZE];
  size_t enc_len;
  cobs_ret_t ret =
    cobs_encode(data, sizeof(msg->hdr) + msg->hdr.payload_len, enc, sizeof(enc), &enc_len);
  cr_assert_eq(COBS_RET_SUCCESS, ret);

  recv_context_t recv_context = {.host = true};
  test_uc_init_mode_device();
  const uc_err_t err = uc_decode(enc, enc_len, on_recv, (void*)&recv_context, &bytes_consumed);

  cr_assert_eq(UC_ERR_NONE, err);
  cr_assert_eq(enc_len, bytes_consumed);
  cr_assert_eq(fwpb_uxc_msg_host_fwup_transfer_cmd_tag, recv_context.msg_host.which_msg);
  cr_assert_eq(recv_context.proto_tag, recv_context.msg_host.which_msg);
  cr_assert_eq(uxc_msg.msg.fwup_transfer_cmd.sequence_id,
               recv_context.msg_host.msg.fwup_transfer_cmd.sequence_id);
  cr_assert_eq(uxc_msg.msg.fwup_transfer_cmd.mode,
               recv_context.msg_host.msg.fwup_transfer_cmd.mode);
  cr_assert_eq(uxc_msg.msg.fwup_transfer_cmd.offset,
               recv_context.msg_host.msg.fwup_transfer_cmd.offset);
  cr_assert_eq(sizeof(payload), recv_context.msg_host.msg.fwup_transfer_cmd.fwup_data.size);
  cr_assert_arr_eq(uxc_msg.msg.fwup_transfer_cmd.fwup_data.bytes,
                   recv_context.msg_host.msg.fwup_transfer_cmd.fwup_data.bytes, sizeof(payload));

  // Ensure message buffer mutex was locked and unlocked.
  cr_assert_eq(1, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(1, rtos_mutex_unlock_fake.call_count);
}

Test(uc, decode_split) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;

  uint8_t fragment[] = {0x59, 0x6F, 0x75, 0x20, 0x67, 0x6F, 0x74, 0x74,
                        0x61, 0x20, 0x67, 0x69, 0x76, 0x65, 0x2E};

  fwpb_uxc_msg_device uxc_msg = fwpb_uxc_msg_device_init_default;
  uxc_msg.which_msg = fwpb_uxc_msg_device_events_get_rsp_tag;
  uxc_msg.msg.events_get_rsp.has_fragment = true;
  uxc_msg.msg.events_get_rsp.fragment.remaining_size = 0xDEADBEEFu;
  uxc_msg.msg.events_get_rsp.fragment.data.size = sizeof(fragment);
  memcpy(uxc_msg.msg.events_get_rsp.fragment.data.bytes, fragment, sizeof(fragment));
  uxc_msg.msg.events_get_rsp.rsp_status = fwpb_events_get_rsp_events_get_rsp_status_SUCCESS;
  uxc_msg.msg.events_get_rsp.version = 0x1337u;

  uint8_t data[UC_TEST_BUF_SIZE];
  uc_msg_t* msg = (uc_msg_t*)data;
  pb_ostream_t ostream = pb_ostream_from_buffer(msg->payload, sizeof(data) - sizeof(msg->hdr));
  cr_assert(pb_encode(&ostream, fwpb_uxc_msg_device_fields, &uxc_msg));

  msg->hdr.proto_tag = fwpb_uxc_msg_device_events_get_rsp_tag;
  msg->hdr.send_seq_num = 0x01u;
  msg->hdr.ack_seq_num = 0x00u;
  msg->hdr.flags = 0;
  msg->hdr.payload_len = ostream.bytes_written;
  msg->hdr.crc = uc_compute_crc(msg);

  uint32_t bytes_consumed;
  uint8_t enc[UC_TEST_BUF_SIZE];
  size_t enc_len;
  cobs_ret_t ret =
    cobs_encode(data, sizeof(msg->hdr) + msg->hdr.payload_len, enc, sizeof(enc), &enc_len);
  cr_assert_eq(COBS_RET_SUCCESS, ret);

  test_uc_init_mode_host();

  recv_context_t recv_context = {.host = false, .call_count = 0};
  uc_err_t err = uc_decode(enc, enc_len - 10u, on_recv, (void*)&recv_context, &bytes_consumed);
  cr_assert_eq(UC_ERR_NONE, err);
  cr_assert_eq(0, recv_context.call_count);
  cr_assert_eq(enc_len - 10u, bytes_consumed);

  err = uc_decode(enc + bytes_consumed, enc_len - bytes_consumed, on_recv, (void*)&recv_context,
                  &bytes_consumed);
  cr_assert_eq(UC_ERR_NONE, err);
  cr_assert_eq(1, recv_context.call_count);
  cr_assert_eq(10u, bytes_consumed);

  fwpb_uxc_msg_device* recv_msg = &recv_context.msg_device;
  cr_assert_eq(fwpb_uxc_msg_device_events_get_rsp_tag, recv_msg->which_msg);
  cr_assert_eq(uxc_msg.msg.events_get_rsp.rsp_status, recv_msg->msg.events_get_rsp.rsp_status);
  cr_assert_eq(uxc_msg.msg.events_get_rsp.version, recv_msg->msg.events_get_rsp.version);
  cr_assert_eq(uxc_msg.msg.events_get_rsp.has_fragment, recv_msg->msg.events_get_rsp.has_fragment);
  cr_assert_eq(uxc_msg.msg.events_get_rsp.fragment.remaining_size,
               recv_msg->msg.events_get_rsp.fragment.remaining_size);
  cr_assert_eq(uxc_msg.msg.events_get_rsp.fragment.data.size,
               recv_msg->msg.events_get_rsp.fragment.data.size);
  cr_assert_arr_eq(uxc_msg.msg.events_get_rsp.fragment.data.bytes,
                   recv_msg->msg.events_get_rsp.fragment.data.bytes,
                   uxc_msg.msg.events_get_rsp.fragment.data.size);

  cr_assert_eq(1, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(1, rtos_mutex_unlock_fake.call_count);
}

Test(uc, decode_encrypted) {
  // TODO(W-13812): Add test for encrypted data.
  cr_assert(true);
}

Test(uc, encode_plaintext) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;

  uint8_t cert[] = {0x59, 0x6F, 0x75, 0x72, 0x20, 0x6E, 0x61, 0x6D, 0x65, 0x20,
                    0x69, 0x73, 0x20, 0x42, 0x69, 0x6C, 0x6C, 0x79, 0x3F};

  fwpb_uxc_msg_device uxc_msg = fwpb_uxc_msg_device_init_default;
  uxc_msg.which_msg = fwpb_uxc_msg_device_cert_get_rsp_tag;
  uxc_msg.msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_SUCCESS;
  uxc_msg.msg.cert_get_rsp.cert.size = sizeof(cert);
  memcpy(uxc_msg.msg.cert_get_rsp.cert.bytes, cert, sizeof(cert));

  // Re-try 3 times: NACK -> TIMEOUT -> ACK
  const uint32_t bits[] = {UC_EVENT_NACK, UC_EVENT_TIMEOUT, UC_EVENT_ACK};
  SET_RETURN_SEQ(rtos_event_group_wait_bits, (uint32_t*)bits, sizeof(bits));

  test_uc_init_mode_device();

  uint8_t buffer[UC_TEST_BUF_SIZE];
  send_ctx = (send_context_t){.enc_buffer = (uint8_t*)buffer,
                              .enc_buffer_len = sizeof(buffer),
                              .enc_len = 0,
                              .call_count = 0};

  const uc_err_t err =
    uc_encode(uxc_msg.which_msg, (void*)&uxc_msg, sizeof(uxc_msg), 0, on_send, &send_ctx);
  cr_assert_eq(UC_ERR_NONE, err);

  uint8_t dec[UC_TEST_BUF_SIZE];
  size_t dec_len;
  cobs_ret_t ret = cobs_decode(send_ctx.enc_buffer, send_ctx.enc_len, dec, sizeof(dec), &dec_len);
  cr_assert_eq(COBS_RET_SUCCESS, ret);

  uc_msg_t* msg = (uc_msg_t*)dec;
  cr_assert_eq(1u, msg->hdr.send_seq_num);
  cr_assert_eq(UC_MSG_HDR_FLAGS_ACK | UC_MSG_HDR_FLAGS_FIRST_MSG, msg->hdr.flags);
  cr_assert_eq(fwpb_uxc_msg_device_cert_get_rsp_tag, msg->hdr.proto_tag);

  // Validate that proto decodes and matches.
  pb_istream_t istream;
  fwpb_uxc_msg_device dec_msg;
  istream = pb_istream_from_buffer(msg->payload, msg->hdr.payload_len);
  cr_assert(pb_decode(&istream, fwpb_uxc_msg_device_fields, &dec_msg));
  cr_assert_eq(fwpb_uxc_msg_device_cert_get_rsp_tag, dec_msg.which_msg);
  cr_assert_eq(sizeof(cert), dec_msg.msg.cert_get_rsp.cert.size);
  cr_assert_arr_eq(uxc_msg.msg.cert_get_rsp.cert.bytes, dec_msg.msg.cert_get_rsp.cert.bytes,
                   sizeof(cert));

  // Validate three attempts to send the message.
  cr_assert_eq(3, send_ctx.call_count);
  cr_assert_eq(3, rtos_event_group_clear_bits_fake.call_count);
  cr_assert_eq(3, rtos_event_group_wait_bits_fake.call_count);

  // Ensure message buffer and write mutexes were locked and unlocked.
  cr_assert_eq(2, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(2, rtos_mutex_unlock_fake.call_count);
}

Test(uc, encode_encrypted) {
  // TODO(W-13812: Add test for encrypted data.
  cr_assert(true);
}

Test(uc, encode_decode) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;
  rtos_event_group_wait_bits_fake.return_val = UC_EVENT_ACK;

  uint8_t coredump[] = {0x49, 0x20, 0x6A, 0x75, 0x73, 0x74, 0x20, 0x67, 0x6F, 0x74,
                        0x20, 0x73, 0x63, 0x72, 0x65, 0x61, 0x6D, 0x65, 0x64, 0x20,
                        0x61, 0x74, 0x20, 0x62, 0x79, 0x20, 0x46, 0x72, 0x65, 0x64,
                        0x64, 0x79, 0x20, 0x4B, 0x72, 0x75, 0x65, 0x67, 0x65, 0x72};

  fwpb_uxc_msg_device uxc_msg = fwpb_uxc_msg_device_init_default;
  uxc_msg.which_msg = fwpb_uxc_msg_device_coredump_get_rsp_tag;
  uxc_msg.msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_SUCCESS;
  uxc_msg.msg.coredump_get_rsp.coredump_count = 1;
  uxc_msg.msg.coredump_get_rsp.has_coredump_fragment = true;
  uxc_msg.msg.coredump_get_rsp.coredump_fragment.offset = 0;
  uxc_msg.msg.coredump_get_rsp.coredump_fragment.complete = true;
  uxc_msg.msg.coredump_get_rsp.coredump_fragment.coredumps_remaining = 0;
  uxc_msg.msg.coredump_get_rsp.coredump_fragment.data.size = sizeof(coredump);
  memcpy(uxc_msg.msg.coredump_get_rsp.coredump_fragment.data.bytes, coredump, sizeof(coredump));

  test_uc_init_mode_device();

  uint8_t buffer[UC_TEST_BUF_SIZE];
  send_ctx = (send_context_t){
    .enc_buffer = (uint8_t*)buffer, .enc_buffer_len = sizeof(buffer), .enc_len = 0};

  uc_err_t err =
    uc_encode(uxc_msg.which_msg, (void*)&uxc_msg, sizeof(uxc_msg), 0, on_send, &send_ctx);
  cr_assert_eq(UC_ERR_NONE, err);

  const size_t enc_len = send_ctx.enc_len;
  uint32_t bytes_consumed;
  recv_context_t recv_context = {.host = false};
  test_uc_init_mode_host();
  err = uc_decode((uint8_t*)buffer, enc_len, on_recv, (void*)&recv_context, &bytes_consumed);
  cr_assert_eq(UC_ERR_NONE, err);
  cr_assert_eq(enc_len, bytes_consumed);

  fwpb_uxc_msg_device* recv_msg = &recv_context.msg_device;
  cr_assert_eq(fwpb_uxc_msg_device_coredump_get_rsp_tag, recv_msg->which_msg);
  cr_assert_eq(fwpb_coredump_get_rsp_coredump_get_rsp_status_SUCCESS,
               recv_msg->msg.coredump_get_rsp.rsp_status);
  cr_assert_eq(1, recv_msg->msg.coredump_get_rsp.coredump_count);
  cr_assert(recv_msg->msg.coredump_get_rsp.has_coredump_fragment);
  cr_assert_eq(0, recv_msg->msg.coredump_get_rsp.coredump_fragment.offset);
  cr_assert(recv_msg->msg.coredump_get_rsp.coredump_fragment.complete);
  cr_assert_eq(0, recv_msg->msg.coredump_get_rsp.coredump_fragment.coredumps_remaining);
  cr_assert_eq(sizeof(coredump), recv_msg->msg.coredump_get_rsp.coredump_fragment.data.size);
  cr_assert_arr_eq(coredump, recv_msg->msg.coredump_get_rsp.coredump_fragment.data.bytes,
                   sizeof(coredump));

  cr_assert_eq(3, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(3, rtos_mutex_unlock_fake.call_count);
}

Test(uc, uc_ack) {
  rtos_mutex_lock_fake.return_val = true;
  rtos_mutex_unlock_fake.return_val = true;

  test_uc_init_mode_device();

  uint8_t buffer[32u];
  send_ctx = (send_context_t){
    .enc_buffer = (uint8_t*)buffer, .enc_buffer_len = sizeof(buffer), .enc_len = 0};

  const uc_err_t err = uc_ack();
  cr_assert_eq(UC_ERR_NONE, err);

  uint8_t dec[UC_TEST_BUF_SIZE];
  size_t dec_len;
  cobs_ret_t ret = cobs_decode(send_ctx.enc_buffer, send_ctx.enc_len, dec, sizeof(dec), &dec_len);
  cr_assert_eq(COBS_RET_SUCCESS, ret);

  uc_msg_t* msg = (uc_msg_t*)dec;
  cr_assert_eq(0, msg->hdr.payload_len);
  cr_assert_eq(1u, msg->hdr.send_seq_num);
  cr_assert_eq(UC_MSG_HDR_FLAGS_ACK, msg->hdr.flags);

  // Ensure message buffer and write mutexes were locked and unlocked.
  cr_assert_eq(2, rtos_mutex_lock_fake.call_count);
  cr_assert_eq(2, rtos_mutex_unlock_fake.call_count);

  // Ensure we did not wait for an ACK.
  cr_assert_eq(2, rtos_event_group_clear_bits_fake.call_count);
  cr_assert_eq(0, rtos_event_group_wait_bits_fake.call_count);
}

Test(uc, idle_no_ack) {
  uc_idle(NULL);

  cr_assert_eq(1, rtos_event_group_get_bits_fake.call_count);
  cr_assert_eq(0, rtos_event_group_clear_bits_fake.call_count);
  cr_assert_eq(0, rtos_event_group_wait_bits_fake.call_count);
}

Test(uc, alloc_free) {
  rtos_semaphore_take_fake.return_val = true;
  rtos_semaphore_give_fake.return_val = true;

  uc_init(on_send, &send_ctx);
  void* mem = uc_alloc_send_proto();
  cr_assert(mem != NULL);

  uc_free_send_proto(mem);

  cr_assert_eq(1, rtos_semaphore_take_fake.call_count);
  cr_assert_eq(1, rtos_semaphore_give_fake.call_count);
}

Test(uc, uc_route) {
  void* actual_proto = NULL;
  void* unused_proto = NULL;
  void* expected_proto = (void*)(uintptr_t)0xBEEFDEADu;

  uc_route_register(1337u, on_proto, (void*)&unused_proto);
  uc_route_register(1338u, on_proto, (void*)&actual_proto);
  uc_route(1338u, expected_proto);
  cr_assert_eq(NULL, unused_proto);
  cr_assert_eq(expected_proto, actual_proto);
}
