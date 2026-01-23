#include "unlock.h"

#include "auth.h"
#include "hex.h"
#include "ipc.h"
#include "log.h"
#include "rtos.h"
#include "sleep.h"
#include "unlock_impl.h"
#include "wallet.h"
#include "wstring.h"

unlock_ctx_t unlock_ctx = {
  .limit_response = RESPONSE_DELAY,
  .delay_timer =
    {
      .name = "unlock",
    },
  .initialized = false,
};

// Map attempt count to delay in ms.
//
// This table is treated as 1-indexed, so that you can think of it in terms of
// unlock attempts; the first entry is for the first failed attempt, etc.
STATIC_VISIBLE_FOR_TESTING const uint32_t delay_table[ATTEMPT_LIMIT + 1] = {
  0,                 // Unused.
  0,                 // Attempt 1
  0,                 // 2
  0,                 // 3
  10 * 1000,         // 4; 10 seconds
  10 * 1000,         // 5; 10 seconds
  60 * 1000,         // 6; 1 minute
  (60 * 1000 * 5),   // 7; 5 minutes
  (60 * 1000 * 10),  // 8; 10 minutes
  (60 * 1000 * 30),  // 9; 30 minutes
  (60 * 1000 * 30),  // 10; 30 minutes
};

// For unit testing.
#ifdef EMBEDDED_BUILD
static void unlock_perform_wipe_state(void) {
  ipc_send_empty(key_manager_port, IPC_KEY_MANAGER_REMOVE_WALLET_STATE);
}
#else
extern void unlock_perform_wipe_state(void);
#endif

static uint32_t get_delay_for_retry_count(uint32_t count) {
  if (count == 0) {
    return 0;
  }

  // Check if delay period was already waited out.
  unlock_delay_status_t delay_status = DELAY_INCOMPLETE;
  if ((retry_counter_read_delay_period_status(&delay_status) == UNLOCK_OK) &&
      (delay_status == DELAY_COMPLETE)) {
    LOGI("Delay period for attempt %ld already waited out", count);
    return 0;
  }

  if (count >= ATTEMPT_LIMIT) {
    return delay_table[ATTEMPT_LIMIT];
  }

  return delay_table[count];
}

static void delay_callback(rtos_timer_handle_t UNUSED(timer)) {
  LOGD("Unlock delay complete");

  if (retry_counter_write_delay_period_status(DELAY_COMPLETE) != UNLOCK_OK) {
    LOGE("Failed to write delay period status");
  }

  rtos_timer_stop(&unlock_ctx.delay_timer);
}

static void begin_delay(uint32_t current_count) {
  uint32_t delay_ms = get_delay_for_retry_count(current_count);
  if (delay_ms == 0) {
    return;
  }

  // Inhibit sleep for the delay duration to ensure device stays on
  LOGD("Inhibiting sleep for %ld ms delay", delay_ms);
  sleep_inhibit(delay_ms);

  LOGD("Delaying for %ld ms", delay_ms);
  rtos_timer_start(&unlock_ctx.delay_timer, delay_ms);
}

static uint32_t get_remaining_delay_ms(void) {
  if (rtos_timer_expired(&unlock_ctx.delay_timer)) {
    // Timer isn't running.
    return 0;
  }

  return rtos_timer_remaining_ms(&unlock_ctx.delay_timer);
}

static secure_bool_t compare_secrets(unlock_secret_t* secret, unlock_secret_t* stored_secret) {
  bool match = (memcmp_s(secret->bytes, stored_secret->bytes, sizeof(secret->bytes)) == 0);
  return match ? SECURE_TRUE : SECURE_FALSE;
}

static void perform_limit_response(void) {
  LOGW("Performing limit response %d!", unlock_ctx.limit_response);

  switch (unlock_ctx.limit_response) {
    case RESPONSE_DELAY:
      // No action; delay will happen like normal.
      return;
    case RESPONSE_WIPE_STATE:
      unlock_perform_wipe_state();
      return;
    default:
      LOGW("Unknown limit response");
      return;
  }
}

void unlock_init_and_begin_delay(void) {
  if (unlock_storage_init() != UNLOCK_OK) {
    LOGE("Couldn't initialize unlock storage");
  }

  unlock_ctx.initialized = true;

  rtos_timer_create_static(&unlock_ctx.delay_timer, delay_callback);

  unlock_limit_response_t limit_response;
  if (limit_response_read(&limit_response) == UNLOCK_OK) {
    unlock_ctx.limit_response = limit_response;
  } else {
    LOGW("Couldn't load limit response; using default");
  }

  uint32_t current_count;
  if (retry_counter_read(&current_count) != UNLOCK_OK) {
    LOGW("Couldn't load retry counter. Defaulting to max attempt limit delay.");
    current_count = ATTEMPT_LIMIT;
  }

  begin_delay(current_count);
}

NO_OPTIMIZE unlock_err_t unlock_check_secret(unlock_secret_t* secret, uint32_t* remaining_delay_ms,
                                             uint32_t* retry_counter) {
  ASSERT(secret && remaining_delay_ms && retry_counter);

  bool unlock_secret_provisioned = false;
  if ((unlock_secret_exists(&unlock_secret_provisioned) != UNLOCK_OK) ||
      !unlock_secret_provisioned) {
    LOGE("No secret provisioned");
    return UNLOCK_NO_SECRET_PROVISIONED;
  }

  uint32_t current_count = ATTEMPT_LIMIT;
  if (retry_counter_read(&current_count) != UNLOCK_OK) {
    return UNLOCK_STORAGE_ERR;
  }

  // Enforce delay.
  *remaining_delay_ms = get_remaining_delay_ms();
  if (*remaining_delay_ms > 0) {
    *retry_counter = current_count;
    LOGI("Waiting on delay: %ld to go", *remaining_delay_ms);
    return UNLOCK_WAITING_ON_DELAY;
  }

  // Increment retry counter before making the comparison.
  if (current_count < UINT32_MAX) {
    current_count++;
  } else {
    LOGW("Retry counter would overflow!");
  }
  if (retry_counter_write(current_count) != UNLOCK_OK) {
    return UNLOCK_STORAGE_ERR;
  }

  // Load secret from flash.
  unlock_secret_t stored_secret = {0};
  unlock_err_t err = unlock_secret_read(&stored_secret);
  if (err != UNLOCK_OK) {
    return err;
  }

  SECURE_DO_FAILOUT(compare_secrets(secret, &stored_secret) == SECURE_TRUE, { goto success; });

  // Wrong secret.
  begin_delay(current_count);
  *remaining_delay_ms = get_remaining_delay_ms();
  *retry_counter = current_count;

  // The count has now surpassed the attempt limit; perform the limit response.
  SECURE_IF_FAILIN(current_count > ATTEMPT_LIMIT) {
    perform_limit_response();
    return UNLOCK_LIMIT_RESPONSE_TAKEN;
  }

  return UNLOCK_WRONG_SECRET;

success:
  if (retry_counter_write(0) != UNLOCK_OK) {
    return UNLOCK_STORAGE_ERR;
  }

  sleep_clear_inhibit();

  rtos_timer_stop(&unlock_ctx.delay_timer);
  *remaining_delay_ms = 0;
  *retry_counter = 0;

  auth_authenticate_unlock_secret();

  return UNLOCK_OK;
}

unlock_err_t unlock_provision_secret(unlock_secret_t* secret) {
  ASSERT(secret);
  return unlock_secret_write(secret);
}

unlock_err_t unlock_set_configured_limit_response(unlock_limit_response_t response) {
  if (limit_response_write(response) != UNLOCK_OK) {
    return UNLOCK_STORAGE_ERR;
  }

  unlock_limit_response_t read_back;
  if (limit_response_read(&read_back) != UNLOCK_OK) {
    return UNLOCK_STORAGE_ERR;
  }

  if (read_back != response) {
    return UNLOCK_STORAGE_ERR;
  }

  return UNLOCK_OK;
}

unlock_err_t unlock_reset_retry_counter(void) {
  // This function may be called from auth_task.c.
  if (!unlock_ctx.initialized) {
    return UNLOCK_NOT_INITIALIZED;
  }

  // Only reset the retry counter if it's not 0.
  uint32_t current_count;
  if (retry_counter_read(&current_count) != UNLOCK_OK) {
    return UNLOCK_STORAGE_ERR;
  }

  if (current_count == 0) {
    return UNLOCK_OK;
  }

  return retry_counter_write(0);
}
