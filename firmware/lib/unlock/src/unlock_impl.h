#include "rtos.h"
#include "unlock.h"

typedef struct {
  unlock_limit_response_t limit_response;
  rtos_timer_t delay_timer;
  uint32_t current_retry_count;
  bool initialized;
} unlock_ctx_t;

unlock_err_t unlock_storage_init(void);

unlock_err_t retry_counter_read(uint32_t* retry_counter);
unlock_err_t retry_counter_write(uint32_t new_value);

unlock_err_t retry_counter_read_delay_period_status(unlock_delay_status_t* status);
unlock_err_t retry_counter_write_delay_period_status(unlock_delay_status_t status);

unlock_err_t unlock_secret_read(unlock_secret_t* secret);
unlock_err_t unlock_secret_write(unlock_secret_t* secret);

unlock_err_t limit_response_read(unlock_limit_response_t* limit_response);
unlock_err_t limit_response_write(unlock_limit_response_t limit_response);
