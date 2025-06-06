#include "FuzzedDataProvider.h"
extern "C" {
#include "fff.h"
#include "secutils.h"
#include "wca.h"

FAKE_VOID_FUNC(refresh_auth);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);

secure_bool_t onboarding_complete(void) {
  return SECURE_TRUE;
}
bool bio_fingerprint_exists(void) {
  return true;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}
secure_bool_t is_allowing_fingerprint_enrollment(void) {
  return SECURE_TRUE;
}
}
#include "mempool.h"
#include "wallet.pb.h"
#include "wca_impl.h"

#include <stddef.h>
#include <stdint.h>

#define WCA_BUF_LEN (250)

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);

bool nop(void) {
  return true;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

#define REGIONS(X) X(fuzz_pool, proto_scratch, sizeof(fwpb_wallet_cmd), 3)
  mempool_t* mempool = mempool_create(fuzz_pool);
  wca_api_t api = {
    .mempool = mempool,
    .sem_take = &nop,
    .sem_give = &nop,
  };
  wca_init(&api);
#undef REGIONS

  while (fuzzed_data.remaining_bytes() > 0) {
    uint8_t cmd_buf[WCA_BUF_LEN] = {0};
    uint8_t rsp_buf[WCA_BUF_LEN] = {0};
    uint32_t cmd_len = fuzzed_data.ConsumeIntegralInRange<uint16_t>(4, WCA_BUF_LEN);
    uint32_t rsp_len = fuzzed_data.ConsumeIntegralInRange<uint16_t>(2, WCA_BUF_LEN);
    cmd_len = fuzzed_data.ConsumeData(cmd_buf, cmd_len);
    rsp_len = fuzzed_data.ConsumeData(rsp_buf, rsp_len);

    if (rsp_len < 2 || cmd_len < 4) {
      continue;
    }

    cmd_buf[CLA] = WCA_CLA;
    int ins = fuzzed_data.ConsumeIntegralInRange(0, 4);  // keep in sync with number of cases
    switch (ins) {
      case 0:
        cmd_buf[INS] = WCA_INS_VERSION;
        break;
      case 1:
        cmd_buf[INS] = WCA_INS_PROTO;
        break;
      case 2:
        cmd_buf[INS] = WCA_INS_PROTO_CONT;
        break;
      case 3:
        cmd_buf[INS] = WCA_INS_GET_RESPONSE;
        break;
      case 4:  // passthrough case
      default:
        break;
    }

    wca_handle_command(cmd_buf, cmd_len, rsp_buf, &rsp_len);
  }

  return 0;
}
