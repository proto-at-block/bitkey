/**
 * unlock_fuzz.cc — PIN/secret unlock retry-counter and delay state machine fuzzer.
 *
 * Drives unlock_check_secret() with arbitrary secret bytes, exercising the
 * retry-counter logic, delay enforcement, attempt-limit responses, and the
 * memcmp_s constant-time comparison.
 *
 * unlock_dep uses unlock_fake_storage_dep in non-embedded builds, so all
 * storage functions (retry_counter_read/write, unlock_secret_read/write, etc.)
 * are provided by unlock_storage_fake.c.  The remaining hardware stubs:
 *
 *   rtos_timer_*           — inline no-ops; delay behaviour is tested
 *                            through the fake storage's retry counter
 *   sleep_inhibit/clear    — inline no-ops
 *   auth_authenticate_unlock_secret — inline no-op
 *   unlock_perform_wipe_state       — inline no-op
 *
 * State is fully reset at the start of each fuzz iteration by calling
 * unlock_init_and_begin_delay(), preventing fake_retry_counter accumulation
 * across iterations from degrading coverage to only the limit-response path.
 *
 * The timer-expired flag is fuzz-controlled, exercising both the normal
 * comparison path (timer_expired=true) and the UNLOCK_WAITING_ON_DELAY path
 * (timer_expired=false).
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "attributes.h"
#include "fff.h"
#include "rtos.h"
#include "unlock.h"
#include "unlock_impl.h"  /* for retry_counter_write() to reset fake_retry_counter */
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"

DEFINE_FFF_GLOBALS;

/* --- RTOS timer stubs ----------------------------------------------------- */

FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);
FAKE_VOID_FUNC(rtos_timer_restart, rtos_timer_t*);

/* g_timer_expired is set per-iteration from fuzz data to reach both the
 * comparison path (true) and the UNLOCK_WAITING_ON_DELAY path (false). */
static bool g_timer_expired = true;

bool rtos_timer_expired(rtos_timer_t* UNUSED(t)) { return g_timer_expired; }
uint32_t rtos_timer_remaining_ms(rtos_timer_t* UNUSED(t)) {
  return g_timer_expired ? 0 : 1000;
}

/* --- RTOS mutex / semaphore / event-group stubs --------------------------- */

FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*,
                uint32_t, bool, bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_event_group_set_bits_from_isr, rtos_event_group_t*,
                uint32_t, bool*);

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) { return true; }
bool rtos_mutex_take(rtos_mutex_t* UNUSED(a), uint32_t UNUSED(b)) { return true; }
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) { return true; }
bool rtos_in_isr(void) { return false; }
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) { return true; }
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) { return true; }
uint32_t rtos_event_group_get_bits(rtos_event_group_t* UNUSED(g)) { return 0; }

/* --- Hardware stubs ------------------------------------------------------- */

void sleep_inhibit(uint32_t UNUSED(ms)) {}
void sleep_clear_inhibit(void) {}
void auth_authenticate_unlock_secret(void) {}

/* unlock_perform_wipe_state is extern'd in unlock.c for non-embedded builds. */
void unlock_perform_wipe_state(void) {}

void detect_glitch(void) {}
void secure_glitch_random_delay(void) {}
uint16_t crypto_rand_short(void) { return 1; }
uint32_t clock_get_freq(void) { return 1; }
bool bd_error_str(char* UNUSED(s), const size_t UNUSED(n), const int UNUSED(e)) { return true; }

}  // extern "C"

/* -------------------------------------------------------------------------- */

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < sizeof(unlock_secret_t)) {
    return 0;
  }

  FuzzedDataProvider fuzzed_data(data, size);

  /* Reset fake_retry_counter explicitly: unlock_storage_init() (called by
   * unlock_init_and_begin_delay) is a no-op in the fake backend and does not
   * zero fake_retry_counter, so without this the counter accumulates across
   * iterations and degenerates coverage to only the ATTEMPT_LIMIT path. */
  retry_counter_write(0);
  unlock_init_and_begin_delay();

  /* Set timer-expired flag from fuzz data: true exercises the comparison
   * path; false exercises the UNLOCK_WAITING_ON_DELAY path. */
  g_timer_expired = fuzzed_data.ConsumeBool();

  /* Reset FFF call counts. */
  RESET_FAKE(rtos_timer_start);
  RESET_FAKE(rtos_timer_stop);
  RESET_FAKE(rtos_queue_send);
  RESET_FAKE(rtos_queue_recv);
  RESET_FAKE(rtos_event_group_set_bits);
  RESET_FAKE(rtos_event_group_wait_bits);
  RESET_FAKE(rtos_event_group_clear_bits);
  RESET_FAKE(rtos_event_group_set_bits_from_isr);

  /* Provision a secret so unlock_check_secret doesn't bail immediately. */
  std::vector<uint8_t> secret_bytes =
    fuzzed_data.ConsumeBytes<uint8_t>(sizeof(unlock_secret_t));
  secret_bytes.resize(sizeof(unlock_secret_t), 0);
  unlock_secret_t secret_to_provision;
  memcpy(secret_to_provision.bytes, secret_bytes.data(), sizeof(unlock_secret_t));
  (void)unlock_provision_secret(&secret_to_provision);

  /* Try multiple unlock attempts with varying secrets to exercise the retry
   * counter increment, delay table, and limit-response branches. */
  uint32_t prev_retry_counter = 0;
  bool first_attempt = true;
  while (fuzzed_data.remaining_bytes() >= sizeof(unlock_secret_t)) {
    std::vector<uint8_t> attempt_bytes =
      fuzzed_data.ConsumeBytes<uint8_t>(sizeof(unlock_secret_t));
    attempt_bytes.resize(sizeof(unlock_secret_t), 0);

    unlock_secret_t attempt;
    memcpy(attempt.bytes, attempt_bytes.data(), sizeof(unlock_secret_t));

    uint32_t remaining_delay_ms = 0;
    uint32_t retry_counter      = 0;
    const unlock_err_t err =
      unlock_check_secret(&attempt, &remaining_delay_ms, &retry_counter);

    /* When the timer is expired (normal path), a wrong-secret result must
     * have a retry counter that is >= the previous value. */
    if (g_timer_expired && err == UNLOCK_WRONG_SECRET && !first_attempt) {
      ASSERT(retry_counter >= prev_retry_counter);
    }
    prev_retry_counter = retry_counter;
    first_attempt = false;
    (void)err;
  }

  return 0;
}
