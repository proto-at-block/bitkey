#include "attributes.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "hal_nfc.h"
#include "log.h"
#include "nfc_control.h"
#include "rtos.h"

#include <criterion/criterion.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(_putchar, char);
FAKE_VOID_FUNC(secure_glitch_random_delay);

// Stub for variadic _log — can't use FAKE_VOID_FUNC with "...".
void _log(log_level_t UNUSED(level), const char* UNUSED(colour), const char* UNUSED(file),
          int UNUSED(line), const char* UNUSED(format), ...) {}

// Controllable time for testing expiry.
static uint32_t fake_time_ms = 0;
uint32_t rtos_thread_systime(void) {
  return fake_time_ms;
}

// Track mode via get/set so nfc_control can save and restore.
static hal_nfc_mode_t current_mode = HAL_NFC_MODE_LISTENER;
void hal_nfc_set_mode(hal_nfc_mode_t mode) {
  current_mode = mode;
}
hal_nfc_mode_t hal_nfc_get_mode(void) {
  return current_mode;
}

// Mutex stubs — no contention in tests.
void rtos_mutex_create(rtos_mutex_t* UNUSED(m)) {}
bool rtos_mutex_lock(rtos_mutex_t* UNUSED(m)) {
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(m)) {
  return true;
}

static void setup(void) {
  fake_time_ms = 0;
  current_mode = HAL_NFC_MODE_LISTENER;
  nfc_control_init();
}

// ---------------------------------------------------------------------------
// Basic disable / enable
// ---------------------------------------------------------------------------

Test(nfc_control, disable_sets_mode_none, .init = setup) {
  nfc_disable_token_t token = nfc_disable(1000);
  cr_assert(token != NFC_CONTROL_INVALID_TOKEN);
  cr_assert(current_mode == HAL_NFC_MODE_NONE);
}

Test(nfc_control, enable_restores_listener, .init = setup) {
  nfc_disable_token_t token = nfc_disable(1000);
  nfc_enable(token);
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);
}

Test(nfc_control, enable_invalid_token_is_noop, .init = setup) {
  nfc_disable(1000);
  current_mode = HAL_NFC_MODE_NONE;
  nfc_enable(NFC_CONTROL_INVALID_TOKEN);
  cr_assert(current_mode == HAL_NFC_MODE_NONE);
}

// ---------------------------------------------------------------------------
// Multi-token: NFC stays disabled until ALL tokens released
// ---------------------------------------------------------------------------

Test(nfc_control, stays_disabled_until_all_released, .init = setup) {
  nfc_disable_token_t t1 = nfc_disable(0);
  nfc_disable_token_t t2 = nfc_disable(0);

  nfc_enable(t1);
  cr_assert(current_mode == HAL_NFC_MODE_NONE);

  nfc_enable(t2);
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);
}

// ---------------------------------------------------------------------------
// Poll is a no-op when nothing has been disabled
// ---------------------------------------------------------------------------

Test(nfc_control, poll_noop_when_no_tokens, .init = setup) {
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);
  nfc_control_poll();
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);
}

// ---------------------------------------------------------------------------
// Timeout expiry via poll
// ---------------------------------------------------------------------------

Test(nfc_control, poll_expires_and_restores_listener, .init = setup) {
  nfc_disable(5000);
  cr_assert(current_mode == HAL_NFC_MODE_NONE);

  // Time hasn't elapsed — poll should not re-enable.
  fake_time_ms = 4999;
  nfc_control_poll();
  cr_assert(current_mode == HAL_NFC_MODE_NONE);

  // Advance past expiry — poll should re-enable.
  fake_time_ms = 5000;
  nfc_control_poll();
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);
}

Test(nfc_control, poll_does_not_restore_if_other_token_active, .init = setup) {
  nfc_disable(2000);
  nfc_disable(0);  // no timeout — must be explicitly released

  fake_time_ms = 2000;
  nfc_control_poll();

  // First token expired but the no-timeout token is still active.
  cr_assert(current_mode == HAL_NFC_MODE_NONE);
}

// ---------------------------------------------------------------------------
// Stale token cannot release a reused slot
// ---------------------------------------------------------------------------

Test(nfc_control, stale_token_cannot_release_reused_slot, .init = setup) {
  nfc_disable_token_t old = nfc_disable(1000);

  // Expire the token via poll.
  fake_time_ms = 1000;
  nfc_control_poll();
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);

  // New caller reuses the same slot.
  nfc_disable_token_t fresh = nfc_disable(5000);
  cr_assert(current_mode == HAL_NFC_MODE_NONE);

  // Old token must not release the new caller's slot.
  nfc_enable(old);
  cr_assert(current_mode == HAL_NFC_MODE_NONE);

  // New token can release it.
  nfc_enable(fresh);
  cr_assert(current_mode == HAL_NFC_MODE_LISTENER);
}

// ---------------------------------------------------------------------------
// Slot exhaustion
// ---------------------------------------------------------------------------

Test(nfc_control, returns_invalid_when_slots_full, .init = setup) {
  for (int i = 0; i < NFC_CONTROL_MAX_TOKENS; i++) {
    cr_assert(nfc_disable(0) != NFC_CONTROL_INVALID_TOKEN);
  }
  cr_assert(nfc_disable(0) == NFC_CONTROL_INVALID_TOKEN);
}
