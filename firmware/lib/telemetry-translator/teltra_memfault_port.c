#include "memfault/core/log.h"
#include "memfault/core/platform/device_info.h"
#include "memfault/core/platform/system_time.h"
#include "telemetry_translator.h"
#include "time.h"

#include <stdbool.h>
#include <stdio.h>

bool memfault_platform_time_get_current(sMemfaultCurrentTime* t) {
  *t = (sMemfaultCurrentTime){
    .type = kMemfaultCurrentTimeType_UnixEpochTimeSec,
    .info = {.unix_timestamp_secs = TIME_PLACEHOLDER_PATTERN},
  };
  return true;
}

teltra_device_info_t g_device_info = {
  .device_serial = {0},
  .software_version = {0},
  .software_type = {0},
  .hardware_version = {0},
};

void memfault_platform_get_device_info(struct MemfaultDeviceInfo* info) {
  info->device_serial = g_device_info.device_serial;
  info->hardware_version = g_device_info.hardware_version;
  info->software_type = g_device_info.software_type;
  info->software_version = g_device_info.software_version;
}

void memfault_platform_halt_if_debugging(void) {}

bool memfault_arch_is_inside_isr(void) {
  return false;
}

void memfault_log_save(eMemfaultPlatformLogLevel level, const char* fmt, ...) {
  (void)level;
  (void)fmt;
}

void memfault_platform_log(eMemfaultPlatformLogLevel level, const char* fmt, ...) {
  (void)level;
  (void)fmt;
}
