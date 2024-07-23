#include "filesystem.h"
#include "log.h"

bool fs_util_write_global(char* filename, uint8_t* data, uint32_t size) {
  ASSERT(data && size > 0);

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_CREAT | FS_O_RDWR) != 0) {
    LOGE("Failed to open %s", filename);
    return false;
  }

  if (fs_file_write(file, data, size) != (int32_t)size) {
    LOGE("Failed to write %ld bytes to %s", size, filename);
    fs_close_global(file);
    return false;
  }

  return (fs_close_global(file) == 0);
}

bool fs_util_read_global(char* filename, uint8_t* data, uint32_t size) {
  ASSERT(data && size > 0);

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_RDWR) != 0) {
    LOGE("Failed to open %s", filename);
    return false;
  }

  uint32_t ret = fs_file_read(file, data, size);
  if (ret != size) {
    LOGE("Partial read: (%ld != %ld)", ret, size);
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
    LOGE("Failed to open %s", filename);
    return false;
  }

  uint32_t size = fs_file_size(file);
  if (size == 0) {
    LOGW("File is empty");
    *size_out = size;
    return (fs_close_global(file) == 0);
  }

  if (size > max_size) {
    LOGE("File is too large: %ld > %ld", size, max_size);
    fs_close_global(file);
    return false;
  }

  uint32_t ret = fs_file_read(file, data, size);
  if (ret != size) {
    LOGE("Partial read: (%ld != %ld)", ret, size);
    fs_close_global(file);
    return false;
  }

  *size_out = size;
  return (fs_close_global(file) == 0);
}
