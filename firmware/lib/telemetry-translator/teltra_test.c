#include "criterion_test_utils.h"
#include "fff.h"
#include "hex.h"
#include "memfault/core/platform/device_info.h"
#include "telemetry_translator.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdio.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(_putchar, char);

static inline uint24_t _truncate_24(uint32_t val) {
  uint24_t out = UINT24((uintptr_t)val & 0x00FFFFFF);
  return out;
}

Test(teltra, translate) {
  bitlog_event_t e = {
    .timestamp_delta = 0xabcd,
    .event = 123,
    .status = 45,
    .pc = _truncate_24(0x11223344),
    .lr = _truncate_24(0xaabbccdd),
  };

  uint8_t memfault_chunk[512] = {0};
  size_t len = sizeof(memfault_chunk);

  teltra_device_info_t d = {
    .device_serial = "fakeserial",
    .hardware_version = "evtd",
    .software_version = "9.9.999",
    .software_type = "app-a-dev",
  };
  teltra_err_t ret = teltra_translate(&d, &e, memfault_chunk, &len);
  cr_assert(ret == TELTRA_OK);
}
