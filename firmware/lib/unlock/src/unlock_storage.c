#include "attributes.h"
#include "filesystem.h"
#include "fwup.h"
#include "indexfs.h"
#include "log.h"
#include "mcu_flash.h"
#include "sysevent.h"
#include "unlock_impl.h"
#include "wallet.h"

#include <string.h>

#define UNLOCK_SECRET_PATH  ("unlock-secret.bin")
#define LIMIT_RESPONSE_PATH ("limit-response.bin")

extern unlock_ctx_t unlock_ctx;
static indexfs_t* unlock_retry_counter_fs = NULL;

static unlock_err_t write_to_filesystem(char* path, uint8_t* data, uint32_t size) {
  ASSERT(path && data && size > 0);

  unlock_err_t result = UNLOCK_STORAGE_ERR;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, path, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    return result;
  }

  ret = fs_file_write(file, data, size);
  if (ret != (int32_t)size) {
    LOGE("Partial write");
    goto out;
  }

  result = UNLOCK_OK;

out:
  (void)fs_close_global(file);
  return result;
}

static unlock_err_t read_from_filesystem(char* path, uint8_t* data, uint32_t size) {
  ASSERT(path && data && size > 0);

  unlock_err_t result = UNLOCK_STORAGE_ERR;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, path, FS_O_RDONLY);
  if (ret != 0) {
    return result;
  }

  ret = fs_file_read(file, data, size);
  if (ret != (int32_t)size) {
    LOGE("Partial read");
    goto out;
  }

  result = UNLOCK_OK;

out:
  (void)fs_close_global(file);
  return result;
}

unlock_err_t unlock_storage_init(void) {
  unlock_err_t err = UNLOCK_OK;

  unlock_retry_counter_fs = indexfs_create(INDEXFS_TYPE_MONOTONIC, unlock_retry_counter_fs,
                                           fwup_target_slot_address(), MCU_FLASH_PAGE_SIZE);
  if (!unlock_retry_counter_fs) {
    err = UNLOCK_STORAGE_ERR;
    LOGE("Couldn't create retry counter filesystem");
  }

  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);

  return err;
}

unlock_err_t retry_counter_read(uint32_t* retry_counter) {
  *retry_counter = indexfs_count(unlock_retry_counter_fs);
  return UNLOCK_OK;
}

unlock_err_t retry_counter_write(uint32_t new_value) {
  if (new_value == 0) {
    LOGD("Clearing retry counter");
    if (!indexfs_clear(unlock_retry_counter_fs)) {
      LOGE("Failed to clear retry counter");
      return UNLOCK_STORAGE_ERR;
    }
  } else if (!indexfs_increment(unlock_retry_counter_fs)) {
    LOGE("Failed to increment retry counter");
    return UNLOCK_STORAGE_ERR;
  }

  // Read and check.
  uint32_t just_written = indexfs_count(unlock_retry_counter_fs);
  if (just_written != new_value) {
    LOGE("Failed to write retry counter: %ld != %ld", just_written, new_value);
    return UNLOCK_STORAGE_ERR;
  }

  return UNLOCK_OK;
}

unlock_err_t retry_counter_read_delay_period_status(unlock_delay_status_t* status) {
  if (!indexfs_get_flag(unlock_retry_counter_fs, status)) {
    LOGE("Failed to read delay period status");
    return UNLOCK_STORAGE_ERR;
  }
  return UNLOCK_OK;
}

unlock_err_t retry_counter_write_delay_period_status(unlock_delay_status_t status) {
  if (!indexfs_set_flag(unlock_retry_counter_fs, status)) {
    LOGE("Failed to write delay period status");
    return UNLOCK_STORAGE_ERR;
  }
  return UNLOCK_OK;
}

unlock_err_t unlock_secret_read(unlock_secret_t* secret) {
  // Note: internally, this function will access the `wallet_pool`, which ideally is not
  // accessed from this library.
  return wkek_read_and_decrypt(UNLOCK_SECRET_PATH, secret->bytes, sizeof(secret->bytes))
           ? UNLOCK_OK
           : UNLOCK_STORAGE_ERR;
}

unlock_err_t unlock_secret_write(unlock_secret_t* secret) {
  // See comment in `unlock_secret_read`.
  return wkek_encrypt_and_store(UNLOCK_SECRET_PATH, secret->bytes, sizeof(secret->bytes))
           ? UNLOCK_OK
           : UNLOCK_STORAGE_ERR;
}

unlock_err_t unlock_secret_exists(bool* exists) {
  *exists = fs_file_exists(UNLOCK_SECRET_PATH);
  return UNLOCK_OK;
}

unlock_err_t limit_response_read(unlock_limit_response_t* limit_response) {
  unlock_err_t result =
    read_from_filesystem(LIMIT_RESPONSE_PATH, (uint8_t*)limit_response, sizeof(*limit_response));

  if (result != UNLOCK_OK) {
    *limit_response = DEFAULT_LIMIT_RESPONSE;

    unlock_err_t write_result =
      write_to_filesystem(LIMIT_RESPONSE_PATH, (uint8_t*)limit_response, sizeof(*limit_response));
    if (write_result != UNLOCK_OK) {
      LOGE("Couldn't write default limit response: %d", write_result);
    }
  }

  return result;
}

unlock_err_t limit_response_write(unlock_limit_response_t limit_response) {
  return write_to_filesystem(LIMIT_RESPONSE_PATH, (uint8_t*)&limit_response,
                             sizeof(limit_response));
}

void unlock_wipe_state(void) {
  fs_remove(UNLOCK_SECRET_PATH);
  fs_remove(LIMIT_RESPONSE_PATH);
  unlock_reset_retry_counter();
}
