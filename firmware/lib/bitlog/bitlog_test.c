#include "bitlog.h"
#include "bitlog_impl.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "hex.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

DEFINE_FFF_GLOBALS;

FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

extern uint8_t bitlog_storage[];
extern bitlog_priv_t bitlog_priv;

static uint32_t fake_time = 0;

#define MASKED_PC (BITLOG_UNIT_TEST_PC & 0x00FFFFFF)
#define MASKED_LR (BITLOG_UNIT_TEST_LR & 0x00FFFFFF)

uint32_t timestamp(void) {
  return ++fake_time;
}

void setup(void) {
  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });
}

static void check_event(bitlog_event_t* e, uint16_t event, uint8_t status) {
  cr_assert(e->event == event);
  cr_assert(e->status == status);
  cr_assert(e->timestamp_delta == 1);
  cr_assert(e->pc.v == MASKED_PC);
  cr_assert(e->lr.v == MASKED_LR);
}

Test(bitlog, store_one_event, .init = setup) {
  BITLOG_EVENT(0xab, 0xcd);
  bitlog_event_t e;
  bitlog_most_recent_event(&e);
  check_event(&e, 0xab, 0xcd);
}

Test(bitlog, fill_events, .init = setup) {
  for (uint32_t i = 0; i < BITLOG_MAX_EVENTS; i++) {
    BITLOG_EVENT(i, i & 0xff);
    bitlog_event_t e;
    bitlog_most_recent_event(&e);
    check_event(&e, i, i & 0xff);
  }

  // Overflow, should record new events.
  for (uint32_t i = 0; i < BITLOG_MAX_EVENTS; i++) {
    uint16_t event = i + 10;
    uint8_t status = event & 0xff;
    BITLOG_EVENT(event, status);
    bitlog_event_t e;
    bitlog_most_recent_event(&e);
    check_event(&e, event, status);
  }
}

Test(bitlog, drain_events, .init = setup) {
  uint8_t generated_events[BITLOG_STORAGE_SIZE] = {0};
  uint32_t off = 0;

  for (uint32_t i = 0; i < BITLOG_MAX_EVENTS; i++) {
    BITLOG_EVENT(i, i & 0xff);
    bitlog_event_t e;
    bitlog_most_recent_event(&e);
    check_event(&e, i, i & 0xff);

    memcpy(&generated_events[off], &e, sizeof(e));
    off += sizeof(e);
  }

  // Drain one event
  uint8_t single_event[sizeof(bitlog_event_t)] = {0};
  uint32_t written;
  cr_assert(bitlog_drain(single_event, sizeof(single_event), &written) ==
            BITLOG_STORAGE_SIZE - sizeof(bitlog_event_t));
  cr_assert(written == sizeof(bitlog_event_t));

  // Drain events
  uint8_t drained_events[BITLOG_STORAGE_SIZE - sizeof(bitlog_event_t)] = {0};
  cr_assert(bitlog_drain(drained_events, sizeof(drained_events), &written) == 0);
  cr_assert(written == sizeof(drained_events));
  cr_util_cmp_buffers(&generated_events[sizeof(bitlog_event_t)], drained_events,
                      sizeof(drained_events));
}

Test(bitlog, overflow_and_drain, .init = setup) {
  // Size this buffer to be twice as big.
  uint8_t generated_events[BITLOG_STORAGE_SIZE * 2] = {0};
  uint32_t off = 0;

  for (uint32_t i = 0; i < BITLOG_MAX_EVENTS; i++) {
    BITLOG_EVENT(i, i & 0xff);
    bitlog_event_t e;
    bitlog_most_recent_event(&e);
    check_event(&e, i, i & 0xff);

    memcpy(&generated_events[off], &e, sizeof(e));
    off += sizeof(e);
  }

  // Overflow, should record new events.
  for (uint32_t i = 0; i < BITLOG_MAX_EVENTS; i++) {
    uint16_t event = i + 10;
    uint8_t status = event & 0xff;
    BITLOG_EVENT(event, status);
    bitlog_event_t e;
    bitlog_most_recent_event(&e);
    check_event(&e, event, status);

    memcpy(&generated_events[off], &e, sizeof(e));
    off += sizeof(e);
  }

  // Drain events. The events we drain should be at the halfway point of `generated_events`.
  uint8_t drained_events[BITLOG_STORAGE_SIZE] = {0};
  uint32_t written;
  cr_assert(bitlog_drain(drained_events, sizeof(drained_events), &written) == 0);
  cr_assert(written == BITLOG_STORAGE_SIZE);
  cr_util_cmp_buffers(&generated_events[BITLOG_STORAGE_SIZE], drained_events,
                      sizeof(drained_events));
}
