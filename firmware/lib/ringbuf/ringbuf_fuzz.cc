#include "FuzzedDataProvider.h"
extern "C" {
#include "fff.h"
#include "ringbuf.h"
}

#include <stddef.h>
#include <stdint.h>

#define BUF_SIZE 64

DEFINE_FFF_GLOBALS;

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) {
  return true;
}
bool bio_fingerprint_exists(void) {
  return true;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  ringbuf_t ringbuf;
  uint8_t main_buf[BUF_SIZE] = {0};
  int jitter = fuzzed_data.ConsumeIntegralInRange(0, 5);
  int sub_buf_len = fuzzed_data.ConsumeIntegralInRange(BUF_SIZE / 2, BUF_SIZE + jitter);
  uint8_t* sub_buf = (uint8_t*)malloc(sub_buf_len);

  while (fuzzed_data.remaining_bytes() > 0) {
    int sub_buf_data_len = 0;
    uint8_t data = 0;

    ringbuf.buf = main_buf;
    ringbuf.max_size = sizeof(main_buf);
    ringbuf.head = 0;
    ringbuf.tail = 0;
    ringbuf.lock = NULL;
    ringbuf.full = false;

    jitter = fuzzed_data.ConsumeIntegralInRange(0, 5);      // sometimes try to go past the buffer
    int select = fuzzed_data.ConsumeIntegralInRange(0, 7);  // keep in sync with number of cases
    switch (select) {
      case 0:
        ringbuf_push(&ringbuf, fuzzed_data.ConsumeIntegral<uint8_t>());
        break;
      case 1:
        ringbuf_pop(&ringbuf, &data);
        break;
      case 2:
        sub_buf_data_len = fuzzed_data.ConsumeData(sub_buf, sub_buf_len);
        ringbuf_push_buf(&ringbuf, sub_buf, sub_buf_data_len);
        break;
      case 3:
        ringbuf_pop_buf(&ringbuf, sub_buf, sub_buf_len);
        break;
      case 4:
        ringbuf_advance(&ringbuf, fuzzed_data.ConsumeIntegralInRange(0, sub_buf_len));
        break;
      case 5:
        ringbuf_peek(&ringbuf, fuzzed_data.ConsumeIntegralInRange(0, BUF_SIZE + jitter), &data);
        break;
      case 6:
        ringbuf_copy_most_recent(&ringbuf, sub_buf,
                                 fuzzed_data.ConsumeIntegralInRange(0, sub_buf_len));
        break;
      case 7:
        ringbuf_clear(&ringbuf);
        break;
      default:
        break;
    }
  }

  free(sub_buf);
  return 0;
}
