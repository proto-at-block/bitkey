/**
 * @file confirmation_manager.c
 * @brief Centralized confirmation manager implementation
 */

#include "confirmation_manager.h"

#include "attributes.h"
#include "log.h"
#include "rtos_mutex.h"
#include "rtos_thread.h"
#include "secure_rng.h"
#include "secutils.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

// Timeout for pending confirmations: 60 seconds
#define CONFIRMATION_TIMEOUT_MS (60 * 1000)

// Static assertions to ensure sizes match proto definitions
_Static_assert(CONFIRMATION_HANDLE_SIZE == sizeof(((fwpb_wallet_rsp*)0)->response_handle.bytes),
               "Handle size must match proto response_handle size");
_Static_assert(CONFIRMATION_HANDLE_SIZE == sizeof(((fwpb_wallet_rsp*)0)->confirmation_handle.bytes),
               "Handle size must match proto confirmation_handle size");
_Static_assert(MAX_OPERATION_DATA_SIZE >= sizeof(fwpb_fwup_start_cmd),
               "Operation data size too small for FWUP command");

typedef struct {
  secure_bool_t active;
  secure_bool_t user_approved;  // SECURE_TRUE if user approved on device screen
  confirmation_type_t type;
  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint32_t timestamp;  // Timestamp in milliseconds
  uint8_t operation_data[MAX_OPERATION_DATA_SIZE];
  size_t operation_data_size;
} pending_confirmation_t;

static SHARED_TASK_BSS pending_confirmation_t pending_confirmation = {0};

static void reset_pending_confirmation(void) {
  memset(&pending_confirmation, 0, sizeof(pending_confirmation));
  pending_confirmation.active = SECURE_FALSE;
  pending_confirmation.user_approved = SECURE_FALSE;
}

// Mutex for thread-safe access to confirmation state
static SHARED_TASK_BSS rtos_mutex_t confirmation_mutex = {0};

// Handler table for confirmation result dispatch
// Index corresponds to confirmation_type_t enum values
static SHARED_TASK_BSS confirmation_result_handler_t result_handlers[CONFIRMATION_TYPE_COUNT] = {0};

void confirmation_manager_init(void) {
  rtos_mutex_create(&confirmation_mutex);
  // reset to init the secure_bool_t values since the struct is stored in bss to save space
  reset_pending_confirmation();
}

NO_OPTIMIZE confirmation_result_t
confirmation_manager_create(confirmation_type_t type, const void* operation_data, size_t data_size,
                            uint8_t* response_handle_out, size_t response_handle_size,
                            uint8_t* confirmation_handle_out, size_t confirmation_handle_size) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  if (data_size > MAX_OPERATION_DATA_SIZE) {
    LOGE("Op data too large");
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  if (!operation_data || !response_handle_out || !confirmation_handle_out) {
    LOGE("Invalid params");
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  if (response_handle_size != CONFIRMATION_HANDLE_SIZE ||
      confirmation_handle_size != CONFIRMATION_HANDLE_SIZE) {
    LOGE("Bad handle size");
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  if (pending_confirmation.active == SECURE_TRUE) {
    LOGW("Replacing confirm");
    reset_pending_confirmation();
  }

  // Generate cryptographically secure random handles
  if (!crypto_random(pending_confirmation.response_handle,
                     sizeof(pending_confirmation.response_handle))) {
    LOGE("RNG fail");
    reset_pending_confirmation();
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_ERROR;
  }

  if (!crypto_random(pending_confirmation.confirmation_handle,
                     sizeof(pending_confirmation.confirmation_handle))) {
    LOGE("RNG fail");
    reset_pending_confirmation();
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_ERROR;
  }

  // Store confirmation state
  pending_confirmation.active = SECURE_TRUE;
  pending_confirmation.user_approved = SECURE_FALSE;
  pending_confirmation.type = type;
  pending_confirmation.timestamp = rtos_thread_systime();
  memcpy(pending_confirmation.operation_data, operation_data, data_size);
  pending_confirmation.operation_data_size = data_size;

  // Return handles to caller
  memcpy(response_handle_out, pending_confirmation.response_handle,
         sizeof(pending_confirmation.response_handle));
  memcpy(confirmation_handle_out, pending_confirmation.confirmation_handle,
         sizeof(pending_confirmation.confirmation_handle));

  rtos_mutex_unlock(&confirmation_mutex);
  return CONFIRMATION_RESULT_SUCCESS;
}

NO_OPTIMIZE confirmation_result_t confirmation_manager_validate(const uint8_t* response_handle,
                                                                size_t response_handle_size,
                                                                const uint8_t* confirmation_handle,
                                                                size_t confirmation_handle_size) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  SECURE_IF_FAILIN(pending_confirmation.active != SECURE_TRUE) {
    LOGE("No active confirm");
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  if (!response_handle || !confirmation_handle) {
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  if (response_handle_size != CONFIRMATION_HANDLE_SIZE ||
      confirmation_handle_size != CONFIRMATION_HANDLE_SIZE) {
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  // Check for timeout
  uint32_t current_time = rtos_thread_systime();
  uint32_t elapsed_time = current_time - pending_confirmation.timestamp;
  if (elapsed_time > CONFIRMATION_TIMEOUT_MS) {
    LOGE("Confirm timeout");
    reset_pending_confirmation();
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_TIMEOUT;
  }

  // Validate handles
  if (memcmp_s(response_handle, pending_confirmation.response_handle,
               sizeof(pending_confirmation.response_handle)) != 0 ||
      memcmp_s(confirmation_handle, pending_confirmation.confirmation_handle,
               sizeof(pending_confirmation.confirmation_handle)) != 0) {
    LOGE("Invalid handles");
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_INVALID_PARAMS;
  }

  // Check if user approved on device screen
  SECURE_IF_FAILIN(pending_confirmation.user_approved != SECURE_TRUE) {
    LOGE("Not approved on device");
    rtos_mutex_unlock(&confirmation_mutex);
    return CONFIRMATION_RESULT_NOT_APPROVED;
  }

  rtos_mutex_unlock(&confirmation_mutex);
  return CONFIRMATION_RESULT_SUCCESS;
}

bool confirmation_manager_get_operation_data(confirmation_type_t expected_type, void* data_out,
                                             size_t* data_size_out) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  if (pending_confirmation.active != SECURE_TRUE) {
    rtos_mutex_unlock(&confirmation_mutex);
    return false;
  }

  if (pending_confirmation.type != expected_type) {
    LOGE("Confirm type err");
    rtos_mutex_unlock(&confirmation_mutex);
    return false;
  }

  if (!data_out || !data_size_out) {
    rtos_mutex_unlock(&confirmation_mutex);
    return false;
  }

  memcpy(data_out, pending_confirmation.operation_data, pending_confirmation.operation_data_size);
  *data_size_out = pending_confirmation.operation_data_size;

  rtos_mutex_unlock(&confirmation_mutex);
  return true;
}

NO_OPTIMIZE void confirmation_manager_approve(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  // Approval is granted here: FAILOUT so a glitched check declines rather than grants
  SECURE_IF_FAILOUT(pending_confirmation.active == SECURE_TRUE) {
    pending_confirmation.user_approved = SECURE_TRUE;
  }

  rtos_mutex_unlock(&confirmation_mutex);
}

NO_OPTIMIZE bool confirmation_manager_is_approved(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  bool result = false;

  SECURE_IF_FAILOUT((pending_confirmation.active == SECURE_TRUE) &&
                    (pending_confirmation.user_approved == SECURE_TRUE)) {
    result = true;
  }

  rtos_mutex_unlock(&confirmation_mutex);
  return result;
}

void confirmation_manager_clear(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  reset_pending_confirmation();

  rtos_mutex_unlock(&confirmation_mutex);
}

bool confirmation_manager_is_pending(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  bool result = (pending_confirmation.active == SECURE_TRUE);

  rtos_mutex_unlock(&confirmation_mutex);
  return result;
}

confirmation_type_t confirmation_manager_get_type(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  confirmation_type_t result = (pending_confirmation.active == SECURE_TRUE)
                                 ? pending_confirmation.type
                                 : CONFIRMATION_TYPE_NONE;

  rtos_mutex_unlock(&confirmation_mutex);
  return result;
}

void confirmation_manager_refresh_timestamp(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  if (pending_confirmation.active == SECURE_TRUE) {
    pending_confirmation.timestamp = rtos_thread_systime();
  }

  rtos_mutex_unlock(&confirmation_mutex);
}

bool confirmation_manager_is_expired(void) {
  ASSERT(confirmation_mutex.handle != NULL);
  rtos_mutex_lock(&confirmation_mutex);

  bool expired = false;
  if ((pending_confirmation.active == SECURE_TRUE) &&
      (pending_confirmation.user_approved != SECURE_TRUE)) {
    uint32_t elapsed = rtos_thread_systime() - pending_confirmation.timestamp;
    expired = (elapsed > CONFIRMATION_TIMEOUT_MS);
  }

  rtos_mutex_unlock(&confirmation_mutex);
  return expired;
}

void confirmation_manager_register_result_handler(confirmation_type_t type,
                                                  confirmation_result_handler_t handler) {
  if (type < CONFIRMATION_TYPE_COUNT) {
    result_handlers[type] = handler;
  }
}

bool confirmation_manager_dispatch_result(ipc_ref_t* message) {
  confirmation_type_t type = confirmation_manager_get_type();
  if (type < CONFIRMATION_TYPE_COUNT && result_handlers[type] != NULL) {
    result_handlers[type](message);
    return true;
  }
  return false;
}
