#include "arithmetic.h"
#include "log.h"
#include "memfault/components.h"
#include "telemetry_storage.h"

#include <stdbool.h>
#include <string.h>

uint8_t active_coredump[TELEMETRY_COREDUMP_SIZE] = {0};

void memfault_platform_coredump_storage_get_info(sMfltCoredumpStorageInfo* info) {
  *info = (sMfltCoredumpStorageInfo){.size = sizeof(active_coredump)};
}

bool memfault_platform_coredump_storage_read(uint32_t offset, void* data, size_t read_len) {
  memcpy(data, &active_coredump[offset], read_len);
  return true;
}

bool memfault_platform_coredump_storage_erase(uint32_t offset, size_t erase_size) {
  memset(&active_coredump[offset], 0, erase_size);
  return true;
}

bool memfault_platform_coredump_storage_write(uint32_t offset, const void* data, size_t data_len) {
  memcpy(&active_coredump[offset], data, data_len);
  return true;
}

void memfault_platform_coredump_storage_clear(void) {
  memset(active_coredump, 0, sizeof(active_coredump));
}
