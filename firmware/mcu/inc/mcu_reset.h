#pragma once

#include "attributes.h"

#include <stdint.h>

/**
 * @brief Possible reasons why the device reset.
 */
typedef enum {
  /**
   * @brief Unused.
   */
  MCU_RESET_UNKNOWN = 0,

  MCU_RESET_INVALID_SIGNATURE,
  MCU_RESET_FATAL,
  MCU_RESET_INVALID_PROPERTIES,
  MCU_RESET_APP_FAILED_TO_UPDATE_VERSION,
  MCU_RESET_BAD_BOOT_ADDR,
  MCU_RESET_FROM_PROTO,
  MCU_RESET_FAULT,
  MCU_RESET_FWUP,
  MCU_RESET_STACK_CANARY_NOT_SET,
  MCU_RESET_STACK_SMASHING_DETECTED,
  MCU_RESET_WATCHDOG_TIMEOUT,
  MCU_RESET_TAMPER,
  MCU_RESET_ECC_ERROR,
  MCU_RESET_FLASH_BANK_SWAP,

  /**
   * @brief Device reset due to USB being plugged in while trying to power off.
   */
  MCU_RESET_POWER_DOWN_USB_PLUGGED,

  /**
   * @brief Option byte write failed. Device resetting to try again.
   */
  MCU_RESET_OPT_WRITE_FAILED,

  /**
   * @brief Option byte reset succeeded. Device resetting to apply.
   */
  MCU_RESET_OPT_WRITE,

  /**
   * @brief Device reset after wipe to return to onboarding state.
   */
  MCU_RESET_WIPE,

  /**
   * @brief Device faulted but the coredump could not be saved because
   * the filesystem was busy.  The coredump data is lost.
   */
  MCU_RESET_FAULT_COREDUMP_SKIPPED,

  /**
   * @brief Host-initiated firmware reset request.
   */
  MCU_RESET_SOFTWARE,

  /**
   * @brief Shutdown IPC to sysinfo task could not be queued within timeout
   * (sysinfo task unresponsive or queue full). Reset instead of leaving the
   * timer service task blocked and the device in a half-shutdown state.
   */
  MCU_RESET_SHUTDOWN_IPC_FAILED,

  /**
   * @brief `power_set_retain(false)` was called to cut power, but the MCU is
   * still executing afterwards. Something else is holding the rail up; reset
   * so the device doesn't zombify with a terminal power-off UI.
   */
  MCU_RESET_POWER_OFF_FAILED,

  /**
   * @brief Display path wedged during USB-plugged power off (repeated
   * display send failures). Both MCUs are reset to resync state.
   */
  MCU_RESET_DISPLAY_WEDGE,

  /**
   * @brief Unused (reset value is capped to `uint8_t`).
   */
  MCU_RESET_MAX = 255,
} mcu_reset_reason_t;

typedef struct {
  mcu_reset_reason_t reason;
} mcu_reset_info_t;

// We define two sources of reset reasons.
// 1) Defined in mcu_reset_reason_t and set by software.
// 2) Set by the MCU's Reset Management Unit (RMU)
//
// The next group of functions are for (1)...
NO_RETURN void mcu_reset_with_reason(const mcu_reset_reason_t reason);
void mcu_reset_set_reason(const mcu_reset_reason_t reason);
mcu_reset_reason_t mcu_reset_get_reason(void);

// ...and the rest are for (2).
uint32_t mcu_reset_rmu_cause_get(void);
void mcu_reset_rmu_clear(void);
