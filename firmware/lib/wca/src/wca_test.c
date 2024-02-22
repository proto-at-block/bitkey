#include "criterion_test_utils.h"
#include "fff.h"
#include "mempool.h"
#include "pb_common.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "secutils.h"
#include "test.pb.h"
#include "wca.h"
#include "wca_impl.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(refresh_auth);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);

secure_bool_t onboarding_auth_is_setup(void) {
  return SECURE_TRUE;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}

#define WCA_BUF_LEN       (250)
#define WCA_LARGE_BUF_LEN (1024)

extern wca_priv_t wca_priv;
static mempool_t* mempool = NULL;

#define UINT16_HI(val) ((val & 0xff00) >> 8)
#define UINT16_LO(val) ((val & 0x00ff))

bool nop(void) {
  return true;
}

void setup(void) {
#define REGIONS(X) X(test_pool, r0, 4096, 1)
  mempool = mempool_create(test_pool);
#undef REGIONS
  wca_api_t api = {
    .mempool = mempool,
    .sem_take = &nop,
    .sem_give = &nop,
  };
  wca_init(&api);
}

Test(wca, version) {
  uint8_t version_cmd[] = {WCA_CLA, WCA_INS_VERSION, 0, 0};
  uint8_t rsp[WCA_BUF_LEN] = {0};
  uint32_t rsp_len = sizeof(rsp);

  cr_assert(wca_handle_command(version_cmd, sizeof(version_cmd), rsp, &rsp_len));

  uint8_t expected_rsp[SW_SIZE + 2] = {0};
  expected_rsp[0] = 0;
  expected_rsp[1] = 1;
  RSP_OK(expected_rsp, 2);

  cr_assert(rsp_len == sizeof(expected_rsp));
  cr_util_cmp_buffers(&expected_rsp, &rsp, SW_SIZE);
}

// Send a proto which fits inside a single APDU
Test(wca, proto, .init = setup) {
  // Encode proto
  fwpb_test_cmd sent_proto = fwpb_test_cmd_init_default;
  sent_proto.which_msg = fwpb_test_cmd_manifest_tag;
  sent_proto.msg.manifest.version = 0xaabb;

  uint8_t buffer[sizeof(fwpb_test_cmd)] = {0};
  pb_ostream_t ostream = pb_ostream_from_buffer(buffer, sizeof(buffer));

  bool status = pb_encode(&ostream, fwpb_test_cmd_fields, &sent_proto);
  cr_assert(status);
  uint32_t proto_length = ostream.bytes_written;

  // Build APDU (emulating what would be done on mobile)
  const uint32_t apdu_overhead = LC + 1;
  const uint32_t proto_cmd_len = apdu_overhead + proto_length;
  uint8_t* proto_cmd = malloc(proto_cmd_len);
  uint8_t apdu_start[] = {WCA_CLA, WCA_INS_PROTO, UINT16_HI(proto_length), UINT16_LO(proto_length),
                          proto_length};
  memcpy(proto_cmd, apdu_start, sizeof(apdu_start));
  memcpy(&proto_cmd[sizeof(apdu_start)], buffer, proto_length);

  // Ship it
  uint8_t rsp[WCA_BUF_LEN] = {0};
  uint32_t rsp_len = sizeof(rsp);
  cr_assert(wca_handle_command(proto_cmd, proto_cmd_len, rsp, &rsp_len));

  // Check it
  uint8_t expected_rsp[SW_SIZE] = {0};
  RSP_OK(expected_rsp, 0);

  fwpb_test_cmd received_proto = fwpb_test_cmd_init_default;
  pb_istream_t istream = pb_istream_from_buffer(wca_priv.encoded_proto_cmd_ctx.buffer,
                                                wca_priv.encoded_proto_cmd_ctx.size);
  cr_assert(pb_decode(&istream, fwpb_test_cmd_fields, &received_proto));

  cr_assert(received_proto.which_msg == fwpb_test_cmd_manifest_tag);
  cr_assert(received_proto.msg.manifest.version == sent_proto.msg.manifest.version);

  free(proto_cmd);
}

void proto_cont_roundtrip(uint8_t ins) {
  // Encode proto
  fwpb_test_cmd sent_proto = fwpb_test_cmd_init_default;
  sent_proto.which_msg = fwpb_test_cmd_foo_tag;
  memset(sent_proto.msg.foo.bar.bytes, 'a', sizeof(sent_proto.msg.foo.bar.bytes));
  sent_proto.msg.foo.bar.size = sizeof(sent_proto.msg.foo.bar.bytes);

  uint8_t buffer[sizeof(fwpb_test_cmd)] = {0};
  pb_ostream_t ostream = pb_ostream_from_buffer(buffer, sizeof(buffer));

  bool status = pb_encode(&ostream, fwpb_test_cmd_fields, &sent_proto);
  cr_assert(status);
  uint32_t proto_length = ostream.bytes_written;

  // Build APDUs (emulating what would be done on mobile)
  uint32_t buffer_off = 0;

  const uint32_t apdu_overhead = LC + 1;
  {
    uint8_t first_part[WCA_BUF_LEN] = {0};
    uint8_t apdu_start[] = {
      WCA_CLA, ins, UINT16_HI(proto_length), UINT16_LO(proto_length), WCA_BUF_LEN - apdu_overhead,
    };
    memcpy(first_part, apdu_start, sizeof(apdu_start));
    memcpy(&first_part[sizeof(apdu_start)], buffer, WCA_BUF_LEN - sizeof(apdu_start));
    buffer_off += (WCA_BUF_LEN - sizeof(apdu_start));

    // Ship first part
    uint8_t rsp[WCA_BUF_LEN] = {0};
    uint32_t rsp_len = sizeof(rsp);
    cr_assert(wca_handle_command(first_part, sizeof(first_part), rsp, &rsp_len));

    // Check it
    uint8_t expected_rsp[SW_SIZE] = {0};
    RSP_OK(expected_rsp, 0);
  }

  // Ship the rest
  {
    while (buffer_off < sizeof(buffer)) {
      uint32_t remaining = sizeof(buffer) - buffer_off;
      uint32_t buffer_chunk_size = BLK_MIN(WCA_BUF_LEN - apdu_overhead, remaining);
      uint8_t data[WCA_BUF_LEN] = {0};
      uint8_t apdu_start[] = {WCA_CLA, WCA_INS_PROTO_CONT, 0, 0, buffer_chunk_size};
      memcpy(data, apdu_start, sizeof(apdu_start));
      memcpy(&data[sizeof(apdu_start)], &buffer[buffer_off], buffer_chunk_size);
      buffer_off += buffer_chunk_size;

      uint8_t rsp[WCA_BUF_LEN] = {0};
      uint32_t rsp_len = sizeof(rsp);
      cr_assert(wca_handle_command(data, sizeof(data), rsp, &rsp_len));

      uint8_t expected_rsp[SW_SIZE] = {0};
      RSP_OK(expected_rsp, 0);
    }
  }

  // Decode the received proto
  fwpb_test_cmd received_proto = fwpb_test_cmd_init_default;
  pb_istream_t istream = pb_istream_from_buffer(wca_priv.encoded_proto_cmd_ctx.buffer,
                                                wca_priv.encoded_proto_cmd_ctx.size);
  cr_assert(pb_decode(&istream, fwpb_test_cmd_fields, &received_proto));

  cr_util_cmp_buffers(sent_proto.msg.foo.bar.bytes, received_proto.msg.foo.bar.bytes,
                      sent_proto.msg.foo.bar.size);
}

void proto_cont_roundtrip_large_buf(uint8_t ins) {
  // Encode proto
  fwpb_test_cmd sent_proto = fwpb_test_cmd_init_default;
  sent_proto.which_msg = fwpb_test_cmd_foo_tag;
  memset(sent_proto.msg.foo.bar.bytes, 'd', sizeof(sent_proto.msg.foo.bar.bytes));
  sent_proto.msg.foo.bar.size = sizeof(sent_proto.msg.foo.bar.bytes);

  uint8_t buffer[sizeof(fwpb_test_cmd)] = {0};
  pb_ostream_t ostream = pb_ostream_from_buffer(buffer, sizeof(buffer));

  bool status = pb_encode(&ostream, fwpb_test_cmd_fields, &sent_proto);
  cr_assert(status);
  uint32_t proto_length = ostream.bytes_written;

  // Build APDUs (emulating what would be done on mobile)
  uint32_t buffer_off = 0;

  const uint32_t apdu_overhead = 7;

  {
    uint8_t first_part[WCA_LARGE_BUF_LEN] = {0};
    uint16_t lc = WCA_LARGE_BUF_LEN - apdu_overhead;

    uint8_t apdu_start[] = {
      WCA_CLA, ins,           UINT16_HI(proto_length), UINT16_LO(proto_length),
      0x00,    UINT16_HI(lc), UINT16_LO(lc),
    };
    memcpy(first_part, apdu_start, sizeof(apdu_start));
    memcpy(&first_part[sizeof(apdu_start)], buffer, WCA_LARGE_BUF_LEN - sizeof(apdu_start));
    buffer_off += (WCA_LARGE_BUF_LEN - sizeof(apdu_start));

    // Ship first part
    uint8_t rsp[WCA_LARGE_BUF_LEN] = {0};
    uint32_t rsp_len = sizeof(rsp);
    cr_assert(wca_handle_command(first_part, sizeof(first_part), rsp, &rsp_len));

    // Check it
    uint8_t expected_rsp[SW_SIZE] = {0};
    RSP_OK(expected_rsp, 0);
  }

  // Ship the rest
  {
    while (buffer_off < sizeof(buffer)) {
      uint32_t remaining = sizeof(buffer) - buffer_off;
      uint32_t buffer_chunk_size = BLK_MIN(WCA_LARGE_BUF_LEN - apdu_overhead, remaining);
      uint8_t data[WCA_LARGE_BUF_LEN] = {0};
      uint8_t apdu_start[] = {WCA_CLA, WCA_INS_PROTO_CONT, 0, 0, buffer_chunk_size};
      memcpy(data, apdu_start, sizeof(apdu_start));
      memcpy(&data[sizeof(apdu_start)], &buffer[buffer_off], buffer_chunk_size);
      buffer_off += buffer_chunk_size;

      uint8_t rsp[WCA_LARGE_BUF_LEN] = {0};
      uint32_t rsp_len = sizeof(rsp);
      cr_assert(wca_handle_command(data, sizeof(data), rsp, &rsp_len));

      uint8_t expected_rsp[SW_SIZE] = {0};
      RSP_OK(expected_rsp, 0);
    }
  }

  // Decode the received proto
  fwpb_test_cmd received_proto = fwpb_test_cmd_init_default;
  pb_istream_t istream = pb_istream_from_buffer(wca_priv.encoded_proto_cmd_ctx.buffer,
                                                wca_priv.encoded_proto_cmd_ctx.size);
  cr_assert(pb_decode(&istream, fwpb_test_cmd_fields, &received_proto));

  cr_util_cmp_buffers(sent_proto.msg.foo.bar.bytes, received_proto.msg.foo.bar.bytes,
                      sent_proto.msg.foo.bar.size);
}

// Send a proto which has to be split across multiple APDUs
Test(wca, proto_cont, .init = setup) {
  proto_cont_roundtrip(WCA_INS_PROTO);
}

// Same test as above but with a larger buffer
Test(wca, proto_cont_large_buf, .init = setup) {
  proto_cont_roundtrip_large_buf(WCA_INS_PROTO);
}

Test(wca, drain_response, .init = setup) {
  proto_cont_roundtrip_large_buf(WCA_INS_PROTO);

  uint8_t bar_bytes[sizeof(fwpb_foo_bar_t)];
  memset(bar_bytes, 'd', sizeof(bar_bytes));

  // Encode a proto into the response scratch buffer
  {
    uint8_t buffer[sizeof(fwpb_test_rsp)] = {0};

    fwpb_test_rsp response_proto = fwpb_test_rsp_init_default;
    pb_ostream_t ostream = pb_ostream_from_buffer(buffer, sizeof(buffer));
    response_proto.which_msg = fwpb_test_rsp_foo_tag;
    memcpy(response_proto.msg.foo.bar.bytes, bar_bytes, sizeof(bar_bytes));
    // memset(response_proto.msg.foo.bar.bytes, 'd', sizeof(response_proto.msg.foo.bar.bytes));
    response_proto.msg.foo.bar.size = sizeof(response_proto.msg.foo.bar.bytes);
    response_proto.msg.foo.baz = 1234;
    cr_assert(pb_encode(&ostream, fwpb_test_rsp_fields, &response_proto));
    uint32_t proto_length = ostream.bytes_written;

    memcpy(wca_priv.encoded_proto_rsp_ctx.buffer, buffer, proto_length);
    wca_priv.encoded_proto_rsp_ctx.size = proto_length;
  }

  // Get the response
  {
    uint8_t full_rsp_buffer[sizeof(fwpb_test_rsp)] = {0};
    uint16_t offset = 0;

    uint8_t cmd[] = {WCA_CLA, WCA_INS_GET_RESPONSE, 0, 0};
    uint8_t rsp_apdu[WCA_LARGE_BUF_LEN] = {0};
    uint32_t rsp_len = sizeof(rsp_apdu);

    cr_assert(wca_handle_command(cmd, sizeof(cmd), rsp_apdu, &rsp_len));

    // Should be data remaining
    uint16_t status_words = ntohs(*(uint16_t*)&rsp_apdu[sizeof(rsp_apdu) - SW_SIZE]);
    cr_assert(status_words == 0x610b);

    memcpy(full_rsp_buffer, rsp_apdu, rsp_len - SW_SIZE);
    offset += (rsp_len - SW_SIZE);

    // Get the rest
    cr_assert(wca_handle_command(cmd, sizeof(cmd), rsp_apdu, &rsp_len));
    status_words = ntohs(*(uint16_t*)&rsp_apdu[rsp_len - SW_SIZE]);

    cr_assert(status_words == 0x9000);

    memcpy(&full_rsp_buffer[offset], rsp_apdu, rsp_len - SW_SIZE);
    offset += (rsp_len - SW_SIZE);

    // Decode
    fwpb_test_rsp received_proto = fwpb_test_rsp_init_default;
    pb_istream_t istream = pb_istream_from_buffer(full_rsp_buffer, offset);
    cr_assert(pb_decode(&istream, fwpb_test_rsp_fields, &received_proto));

    cr_assert(received_proto.which_msg == fwpb_test_rsp_foo_tag);
    cr_util_cmp_buffers(received_proto.msg.foo.bar.bytes, bar_bytes,
                        received_proto.msg.foo.bar.size);
    cr_assert(received_proto.msg.foo.baz == 1234);
  }
}
