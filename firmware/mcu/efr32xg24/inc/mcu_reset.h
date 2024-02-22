#pragma once

#include <stdint.h>

typedef enum {
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
void mcu_reset_with_reason(mcu_reset_reason_t reason);
void mcu_reset_set_reason(mcu_reset_reason_t reason);
mcu_reset_reason_t mcu_reset_get_reason(void);

// ...and the rest are for (2).
uint32_t mcu_reset_rmu_cause_get(void);
void mcu_reset_rmu_clear(void);
