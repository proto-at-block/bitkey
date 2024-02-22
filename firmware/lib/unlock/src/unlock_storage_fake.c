#include "attributes.h"
#include "unlock_impl.h"

#include <string.h>

// sha256('test')
STATIC_VISIBLE_FOR_TESTING uint8_t fake_stored_secret[32] = {
  0x9f, 0x86, 0xd0, 0x81, 0x88, 0x4c, 0x7d, 0x65, 0x9a, 0x2f, 0xea, 0xa0, 0xc5, 0x5a, 0xd0, 0x15,
  0xa3, 0xbf, 0x4f, 0x1b, 0x2b, 0x0b, 0x82, 0x2c, 0xd1, 0x5d, 0x6c, 0x15, 0xb0, 0xf0, 0x0a, 0x08,
};

STATIC_VISIBLE_FOR_TESTING uint32_t fake_retry_counter = 0;
STATIC_VISIBLE_FOR_TESTING unlock_limit_response_t fake_stored_limit_response = RESPONSE_WIPE_STATE;
STATIC_VISIBLE_FOR_TESTING SHARED_TASK_DATA bool secret_provisioned = false;
STATIC_VISIBLE_FOR_TESTING unlock_delay_status_t delay_status = DELAY_INCOMPLETE;

extern unlock_ctx_t unlock_ctx;

unlock_err_t unlock_storage_init(void) {
  return UNLOCK_OK;
}

unlock_err_t retry_counter_read(uint32_t* retry_counter) {
  *retry_counter = fake_retry_counter;
  return UNLOCK_OK;
}

unlock_err_t retry_counter_write(uint32_t new_value) {
  fake_retry_counter = new_value;
  return UNLOCK_OK;
}

unlock_err_t unlock_secret_read(unlock_secret_t* secret) {
  memcpy(secret->bytes, fake_stored_secret, sizeof(secret->bytes));
  return UNLOCK_OK;
}

unlock_err_t unlock_secret_write(unlock_secret_t* secret) {
  memcpy(fake_stored_secret, secret->bytes, sizeof(secret->bytes));
  secret_provisioned = true;
  return UNLOCK_OK;
}

unlock_err_t unlock_secret_exists(bool* exists) {
  *exists = secret_provisioned;
  return UNLOCK_OK;
}

unlock_err_t limit_response_read(unlock_limit_response_t* limit_response) {
  *limit_response = fake_stored_limit_response;
  return UNLOCK_OK;
}

unlock_err_t limit_response_write(unlock_limit_response_t limit_response) {
  fake_stored_limit_response = limit_response;
  return UNLOCK_OK;
}

unlock_err_t retry_counter_read_delay_period_status(unlock_delay_status_t* status) {
  *status = delay_status;
  return UNLOCK_OK;
}

unlock_err_t retry_counter_write_delay_period_status(unlock_delay_status_t status) {
  delay_status = status;
  return UNLOCK_OK;
}
