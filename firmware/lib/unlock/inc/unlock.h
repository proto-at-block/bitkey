#pragma once

#include "hash.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  UNLOCK_OK = 0,
  UNLOCK_WRONG_SECRET,
  UNLOCK_STORAGE_ERR,
  UNLOCK_NO_SECRET_PROVISIONED,
  UNLOCK_WAITING_ON_DELAY,
  UNLOCK_LIMIT_RESPONSE_TAKEN,
  UNLOCK_NOT_INITIALIZED,
} unlock_err_t;

typedef struct {
  uint8_t bytes[SHA256_DIGEST_SIZE];
} unlock_secret_t;

typedef enum {
  RESPONSE_DELAY = 0,
  RESPONSE_WIPE_STATE,
} unlock_limit_response_t;

typedef enum {
  DELAY_COMPLETE = 0,
  DELAY_INCOMPLETE = 0xf,
} unlock_delay_status_t;

#define DEFAULT_LIMIT_RESPONSE RESPONSE_WIPE_STATE
#define ATTEMPT_LIMIT          10

void unlock_init_and_begin_delay(void);

unlock_err_t unlock_check_secret(unlock_secret_t* secret, uint32_t* remaining_delay_ms,
                                 uint32_t* retry_counter);
unlock_err_t unlock_provision_secret(unlock_secret_t* secret);
unlock_err_t unlock_secret_exists(bool* exists);
unlock_err_t unlock_set_configured_limit_response(unlock_limit_response_t response);

// For when the device is unlocked via biometrics.
unlock_err_t unlock_reset_retry_counter(void);

void unlock_wipe_state(void);
