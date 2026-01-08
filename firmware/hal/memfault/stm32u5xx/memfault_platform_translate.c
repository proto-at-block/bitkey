#include "memfault/core/platform/debug_log.h"
#include "memfault/ports/reboot_reason.h"
#include "stm32u5xx.h"

#include <stdint.h>

#if MEMFAULT_ENABLE_REBOOT_DIAG_DUMP
#define MEMFAULT_PRINT_RESET_INFO(...) MEMFAULT_LOG_INFO(__VA_ARGS__)
#else
#define MEMFAULT_PRINT_RESET_INFO(...)
#endif

#define MEMFAULT_RMU_RST_CAUSE_LOW_POWER_RESET \
  0x80000000u  //<! Entering low power operation, e.g. standby/stop
#define MEMFAULT_RMU_RST_CAUSE_WINDOW_WDG 0x40000000u  //<! Window watchdog end of count condition
#define MEMFAULT_RMU_RST_CAUSE_INDEPENDENT_WDG \
  0x20000000u  //<! Independent watchdog end of count condition
#define MEMFAULT_RMU_RST_CAUSE_SOFTWARE_RESET      0x10000000u  //<! Reset triggered by firmware
#define MEMFAULT_RMU_RST_CAUSE_BROWNOUT            0x08000000u  //<! Brownout reset
#define MEMFAULT_RMU_RST_CAUSE_PIN_RESET           0x04000000u  //<! Pin reset via nRST
#define MEMFAULT_RMU_RST_CAUSE_OPTION_BYTE_LOADING 0x02000000u  //<! Reset from option-byte loading

eMemfaultRebootReason memfault_translate_rmu_cause_to_memfault_enum(uint32_t reset_cause) {
  if (reset_cause & MEMFAULT_RMU_RST_CAUSE_OPTION_BYTE_LOADING) {
    MEMFAULT_PRINT_RESET_INFO(" Option Byte");
    return kMfltRebootReason_Unknown;
  } else if (reset_cause & MEMFAULT_RMU_RST_CAUSE_BROWNOUT) {
    MEMFAULT_PRINT_RESET_INFO(" Brown Out");
    return kMfltRebootReason_BrownOutReset;
  } else if (reset_cause & MEMFAULT_RMU_RST_CAUSE_SOFTWARE_RESET) {
    MEMFAULT_PRINT_RESET_INFO(" Software");
    return kMfltRebootReason_SoftwareReset;
  } else if (reset_cause & MEMFAULT_RMU_RST_CAUSE_WINDOW_WDG) {
    MEMFAULT_PRINT_RESET_INFO(" Window Watchdog");
    return kMfltRebootReason_HardwareWatchdog;
  } else if (reset_cause & MEMFAULT_RMU_RST_CAUSE_INDEPENDENT_WDG) {
    MEMFAULT_PRINT_RESET_INFO(" Independent Watchdog");
    return kMfltRebootReason_HardwareWatchdog;
  } else if (reset_cause & MEMFAULT_RMU_RST_CAUSE_LOW_POWER_RESET) {
    MEMFAULT_PRINT_RESET_INFO(" Standby / Stop");
    return kMfltRebootReason_DeepSleep;
  } else if (reset_cause & MEMFAULT_RMU_RST_CAUSE_PIN_RESET) {
    MEMFAULT_PRINT_RESET_INFO(" Pin Reset");
    return kMfltRebootReason_PinReset;
  }

  MEMFAULT_PRINT_RESET_INFO(" Unknown");
  return kMfltRebootReason_Unknown;
}
