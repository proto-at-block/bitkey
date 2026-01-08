#include "memfault/core/platform/debug_log.h"
#include "memfault/ports/reboot_reason.h"

#include "em_device.h"

#include <stdint.h>

#if MEMFAULT_ENABLE_REBOOT_DIAG_DUMP
#define MEMFAULT_PRINT_RESET_INFO(...) MEMFAULT_LOG_INFO(__VA_ARGS__)
#else
#define MEMFAULT_PRINT_RESET_INFO(...)
#endif

eMemfaultRebootReason memfault_translate_rmu_cause_to_memfault_enum(uint32_t reset_cause) {
  if (reset_cause & EMU_RSTCAUSE_POR) {
    MEMFAULT_PRINT_RESET_INFO(" Power on Reset");
    return kMfltRebootReason_PowerOnReset;
  } else if (reset_cause & EMU_RSTCAUSE_AVDDBOD) {
    MEMFAULT_PRINT_RESET_INFO(" AVDD Brown Out");
    return kMfltRebootReason_BrownOutReset;
  } else if (reset_cause & EMU_RSTCAUSE_IOVDD0BOD) {
    MEMFAULT_PRINT_RESET_INFO(" IOVDD0 Brown Out");
    return kMfltRebootReason_BrownOutReset;
  } else if (reset_cause & EMU_RSTCAUSE_DVDDBOD) {
    MEMFAULT_PRINT_RESET_INFO(" DVDD Brown Out");
    return kMfltRebootReason_BrownOutReset;
  } else if (reset_cause & EMU_RSTCAUSE_DVDDLEBOD) {
    MEMFAULT_PRINT_RESET_INFO(" DVDDLE Brown Out");
    return kMfltRebootReason_BrownOutReset;
  } else if (reset_cause & EMU_RSTCAUSE_DECBOD) {
    MEMFAULT_PRINT_RESET_INFO(" DEC Brown Out");
    return kMfltRebootReason_BrownOutReset;
  } else if (reset_cause & EMU_RSTCAUSE_LOCKUP) {
    MEMFAULT_PRINT_RESET_INFO(" Lockup");
    return kMfltRebootReason_Lockup;
  } else if (reset_cause & EMU_RSTCAUSE_SYSREQ) {
    MEMFAULT_PRINT_RESET_INFO(" Software");
    return kMfltRebootReason_SoftwareReset;
  } else if (reset_cause & EMU_RSTCAUSE_WDOG0) {
    MEMFAULT_PRINT_RESET_INFO(" Watchdog 0");
    return kMfltRebootReason_HardwareWatchdog;
  } else if (reset_cause & EMU_RSTCAUSE_WDOG1) {
    MEMFAULT_PRINT_RESET_INFO(" Watchdog 1");
    return kMfltRebootReason_HardwareWatchdog;
  } else if (reset_cause & EMU_RSTCAUSE_EM4) {
    MEMFAULT_PRINT_RESET_INFO(" EM4 Wakeup");
    return kMfltRebootReason_DeepSleep;
  } else if (reset_cause & EMU_RSTCAUSE_VREGIN) {
    MEMFAULT_PRINT_RESET_INFO(" VREGIN");
    return kMfltRebootReason_UnknownError;
  } else if (reset_cause & EMU_RSTCAUSE_PIN) {
    MEMFAULT_PRINT_RESET_INFO(" Pin Reset");
    return kMfltRebootReason_PinReset;
  }

  MEMFAULT_PRINT_RESET_INFO(" Unknown");
  return kMfltRebootReason_Unknown;
}
