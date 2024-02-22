#include "FuzzedDataProvider.h"
extern "C" {
#include "fff.h"
#include "t4t.h"
#include "t4t_impl.h"
}

#include <stddef.h>
#include <stdint.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(refresh_auth);

void setup_select_state(bool select_cc) {
  // T4T select requires a bunch of magic numbers, and has to be called before
  // update or read -- so call it with the right params to ensure we don't make
  // the fuzzer spin trying to find magic numbers.
  uint8_t select_cmd[] = {0x00, 0xA4, 0x04, 0x00, 0x07, 0xD2, 0x76,
                          0x00, 0x00, 0x85, 0x01, 0x01, 0x00};
  uint8_t select_rsp[SW_SIZE] = {0};
  uint32_t select_rsp_len = SW_SIZE;
  t4t_select(select_cmd, sizeof(select_cmd), select_rsp, &select_rsp_len);

  // Now that NDEF tag application is selected, select the file
  uint8_t select_file_cmd[] = {0x00, 0xA4, 0x00, 0x0C, 0x02, 0x00, 0x00};
  const uint8_t CC_FID_ARR[] = {CC_FID >> 8, CC_FID & 0xFF};
  const uint8_t NDEF_FID_ARR[] = {NDEF_FID >> 8, NDEF_FID & 0xFF};

  if (select_cc) {
    memcpy(&select_file_cmd[5], CC_FID_ARR, 2);
  } else {
    memcpy(&select_file_cmd[5], NDEF_FID_ARR, 2);
  }

  t4t_select(select_file_cmd, sizeof(select_file_cmd), select_rsp, &select_rsp_len);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  while (fuzzed_data.remaining_bytes() > 0) {
    uint8_t cmd_buf[T4T_BUF_LEN] = {0};
    uint8_t rsp_buf[T4T_BUF_LEN] = {0};

    bool select_cc = fuzzed_data.ConsumeBool();
    uint32_t cmd_len = fuzzed_data.ConsumeIntegralInRange<uint16_t>(4, T4T_BUF_LEN);
    uint32_t rsp_len = fuzzed_data.ConsumeIntegralInRange<uint16_t>(2, T4T_BUF_LEN);
    cmd_len = fuzzed_data.ConsumeData(cmd_buf, cmd_len);
    rsp_len = fuzzed_data.ConsumeData(rsp_buf, rsp_len);

    if (rsp_len < 2 || cmd_len < 4) {
      continue;
    }

    cmd_buf[CLA] = T4T_CLA;
    int ins = fuzzed_data.ConsumeIntegralInRange(0, 4);  // keep in sync with number of cases
    switch (ins) {
      case 0:
        cmd_buf[INS] = T4T_INS_SELECT;
        break;
      case 1:
        cmd_buf[INS] = T4T_INS_READ;
        break;
      case 2:
        cmd_buf[INS] = T4T_INS_UPDATE;
        break;
      case 3:
        cmd_buf[INS] = T4T_INS_UPDATE_ODO;
        break;
      case 4:  // passthrough case
      default:
        break;
    }

    setup_select_state(select_cc);
    t4t_handle_command(cmd_buf, cmd_len, rsp_buf, &rsp_len);
  }

  return 0;
}
