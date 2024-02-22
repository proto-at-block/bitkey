#include "FuzzedDataProvider.h"

extern "C" {
#include "assert.h"
#include "fff.h"
#include "ipc.h"
#include "proto_helpers.h"
#include "secutils.h"

void refresh_auth(void) {}
bool bio_fingerprint_exists(void) {
  return true;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);

secure_bool_t onboarding_auth_is_setup(void) {
  return SECURE_TRUE;
}
}

#include "mempool.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "wallet.pb.h"

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);

uint8_t rsp_buf[sizeof(fwpb_wallet_rsp)];

static void handle_proto_fuzz_response(uint8_t* encoded_proto, uint32_t size) {
  memcpy(rsp_buf, encoded_proto, size);
  return;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  /* This is a fuzzing implementation testing the protobuf encode/decode functionality */
  FuzzedDataProvider fuzzed_data(data, size);

#define REGIONS(X)                                            \
  X(fuzz_pool, proto_cmd_scratch, sizeof(fwpb_wallet_cmd), 1) \
  X(fuzz_pool, proto_rsp_scratch, sizeof(fwpb_wallet_rsp), 1)
  mempool_t* mempool = mempool_create(fuzz_pool);
#undef REGIONS
  ipc_proto_register_api(mempool, rsp_buf, &handle_proto_fuzz_response);

  while (fuzzed_data.remaining_bytes() > 0) {
    uint32_t cmd_buf_size;
    std::vector<uint8_t> cmd_buf;
    fwpb_wallet_cmd* cmd = NULL;

    cmd_buf_size = fuzzed_data.ConsumeIntegralInRange<uint32_t>(0, sizeof(fwpb_wallet_cmd));
    cmd_buf = fuzzed_data.ConsumeBytes<uint8_t>(cmd_buf_size);

    if (!cmd_buf.data()) {
      return -1;
    }

    cmd = proto_get_cmd(cmd_buf.data(), cmd_buf.size());
    if (!cmd) {
      return -1;
    }

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    proto_send_rsp(cmd, rsp);
  }
  return 0;
}
