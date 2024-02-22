#include "battery.h"

#include "attributes.h"
#include "bitops.h"
#include "filesystem.h"
#include "log.h"
#include "mcu_devinfo.h"
#include "sysevent.h"

#define BATTERY_FILE_NAME ("battery.txt")

bool battery_set_variant(const uint32_t variant) {
  ASSERT(variant);

  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);
  bool result = false;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, BATTERY_FILE_NAME, FS_O_CREAT | FS_O_RDWR);
  if (ret != 0) {
    return false;
  }

  if (fs_file_write(file, &variant, sizeof(uint32_t)) != sizeof(uint32_t)) {
    goto out;
  }

  // LOGI is noisy, but this should only be called during manufacturing
  LOGI("Setting battery variant to: %ld", variant);

  result = true;

out:
  (void)fs_close_global(file);
  return result;
}

bool battery_get_variant(uint32_t* variant) {
  ASSERT(variant);

  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);
  bool result = false;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, BATTERY_FILE_NAME, FS_O_RDONLY);
  if (ret != 0) {
    return false;
  }

  int32_t size = fs_file_size(file);
  if (size < 0 || size != sizeof(uint32_t)) {
    goto out;
  }

  ret = fs_file_read(file, variant, size);
  if (ret != size) {
    goto out;
  }

  result = true;

out:
  (void)fs_close_global(file);
  return result;
}

void battery_print_variant(void) {
  uint32_t variant = 0;

  if (battery_get_variant(&variant)) {
    LOGI("Battery variant: %ld", variant);
  } else {
    LOGE("Failed to read battery variant");
  }
}
