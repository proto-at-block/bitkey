#pragma once

#include "bip32.h"
#include "hash.h"
#include "ipc.h"
#include "rtos.h"

#include <stdbool.h>

typedef enum {
  CRYPTO_TASK_WAITING = 0,
  CRYPTO_TASK_IN_PROGRESS = 1,
  CRYPTO_TASK_SUCCESS = 2,
  CRYPTO_TASK_ERROR = 3,
  CRYPTO_TASK_DERIVATION_FAILED = 5,
  CRYPTO_TASK_SIGNING_FAILED = 6,
  CRYPTO_TASK_POLICY_VIOLATION = 7,
} crypto_task_status_t;

rtos_thread_t* crypto_task_create(void);
crypto_task_status_t crypto_task_get_status(void);
void crypto_task_reset_status(void);
bool crypto_task_get_and_clear_signature(uint8_t expected_hash[SHA256_DIGEST_SIZE],
                                         uint32_t* expected_indices, uint32_t num_indices,
                                         uint8_t signature[ECC_SIG_SIZE]);
void crypto_task_set_parameters(derivation_path_t* derivation_path,
                                uint8_t hash[SHA256_DIGEST_SIZE]);

void key_manager_task_register_listeners(void);
void key_manager_task_handle_uxc_session_response(ipc_ref_t* message);
void key_manager_task_handle_uxc_boot(void);
