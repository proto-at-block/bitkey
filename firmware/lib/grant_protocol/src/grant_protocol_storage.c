#include "assert.h"
#include "filesystem.h"
#include "grant_protocol.h"
#include "grant_protocol_storage_impl.h"
#include "log.h"

#include <string.h>

static grant_protocol_result_t write_to_filesystem(const char* path, const uint8_t* data,
                                                   uint32_t size) {
  ASSERT(path && data && size > 0);

  grant_protocol_result_t result = GRANT_RESULT_ERROR_STORAGE;
  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, path, FS_O_RDWR | FS_O_CREAT | FS_O_TRUNC);
  if (ret != 0) {
    LOGE("Failed to open grant storage file %s for writing: %d", path, ret);
    return result;
  }

  ret = fs_file_write(file, data, size);
  if (ret != (int32_t)size) {
    LOGE("Failed to write full grant request (%d != %ld)", ret, size);
    goto out;
  }

  result = GRANT_RESULT_OK;

out:
  if (file) {
    (void)fs_close_global(file);
  }
  return result;
}

static grant_protocol_result_t read_from_filesystem(const char* path, uint8_t* data,
                                                    uint32_t size) {
  ASSERT(path && data && size > 0);

  grant_protocol_result_t result = GRANT_RESULT_ERROR_STORAGE;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, path, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Failed to open grant storage file %s for reading: %d", path, ret);
    return result;
  }

  ret = fs_file_read(file, data, size);
  if (ret != (int32_t)size) {
    LOGE("Failed to read full grant request (%d != %ld)", ret, size);
    goto out;
  }

  result = GRANT_RESULT_OK;

out:
  if (file) {
    (void)fs_close_global(file);
  }
  return result;
}

grant_protocol_result_t grant_storage_read_request(grant_request_t* request) {
  ASSERT(request);
  return read_from_filesystem(GRANT_REQUEST_PATH, (uint8_t*)request, sizeof(grant_request_t));
}

grant_protocol_result_t grant_storage_write_request(const grant_request_t* request) {
  ASSERT(request);
  return write_to_filesystem(GRANT_REQUEST_PATH, (const uint8_t*)request, sizeof(grant_request_t));
}

grant_protocol_result_t grant_storage_delete_request(void) {
  if (fs_file_exists(GRANT_REQUEST_PATH)) {
    if (fs_remove(GRANT_REQUEST_PATH) != 0) {
      LOGE("Failed to delete grant request file");
      return GRANT_RESULT_ERROR_STORAGE;
    }
    LOGD("Deleted grant request file");
  }
  return GRANT_RESULT_OK;
}
