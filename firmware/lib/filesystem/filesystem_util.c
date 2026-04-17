#include "filesystem.h"
#include "log.h"

#include <inttypes.h>

bool fs_util_write_global(char* filename, uint8_t* data, uint32_t size) {
  ASSERT(data && size > 0);

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR) != 0) {
    LOGE("Open fail: %s", filename);
    return false;
  }

  if (fs_file_write(file, data, size) != (int32_t)size) {
    LOGE("Write fail: %ld bytes to %s", size, filename);
    fs_close_global(file);
    return false;
  }

  return (fs_close_global(file) == 0);
}

bool fs_util_read_global(char* filename, uint8_t* data, uint32_t size) {
  ASSERT(data && size > 0);

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_RDWR) != 0) {
    LOGE("Open fail: %s", filename);
    return false;
  }

  int32_t ret = fs_file_read(file, data, size);
  if (ret < 0 || (uint32_t)ret != size) {
    LOGE("Partial read: %" PRId32 " != %" PRIu32, ret, size);
    fs_close_global(file);
    return false;
  }

  return (fs_close_global(file) == 0);
}

bool fs_util_read_all_global(char* filename, uint8_t* data, uint32_t max_size, uint32_t* size_out) {
  ASSERT(data && size_out);

  *size_out = 0;

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_RDWR) != 0) {
    LOGE("Open fail: %s", filename);
    return false;
  }

  int32_t size_or_err = fs_file_size(file);
  if (size_or_err <= 0) {
    if (size_or_err == 0) {
      LOGW("File empty");
      *size_out = 0;
      return (fs_close_global(file) == 0);
    }
    LOGE("Size fail: %" PRId32, size_or_err);
    fs_close_global(file);
    return false;
  }
  uint32_t size = (uint32_t)size_or_err;

  if (size > max_size) {
    LOGE("File too large: %" PRIu32 " > %" PRIu32, size, max_size);
    fs_close_global(file);
    return false;
  }

  int32_t ret = fs_file_read(file, data, size);
  if (ret < 0 || (uint32_t)ret != size) {
    LOGE("Partial read: %" PRId32 " != %" PRIu32, ret, size);
    fs_close_global(file);
    return false;
  }

  *size_out = size;
  return (fs_close_global(file) == 0);
}
