#include "opt.h"

#include "mcu_flash.h"

#include <stddef.h>

extern const mcu_flash_opt_t mcu_flash_opt_prod __attribute__((weak));

static mcu_flash_opt_t const* _production_opt(void) {
  // On platforms that do not define production option bytes (e.g. EFR32),
  // this weak symbol resolves to address 0. Callers handle NULL as unsupported.
  return &mcu_flash_opt_prod;
}

bool opt_device_set_production(void) {
  mcu_flash_opt_t const* prod_opt = _production_opt();
  if (prod_opt == NULL) {
    return false;
  }

  mcu_flash_rdp_t target_rdp = MCU_FLASH_RDP_UNKNOWN;
  if (mcu_flash_opt_get_rdp(prod_opt, &target_rdp) != MCU_FLASH_STATUS_OK) {
    return false;
  }

  mcu_flash_opt_info_t opt_info = {0};
  if (mcu_flash_opt_read(&opt_info) != MCU_FLASH_STATUS_OK) {
    return false;
  }

  // Ensure production option-byte layout is applied before changing RDP.
  // On STM32U5, once RDP2 is set, option bytes are no longer writable.
  if (!mcu_flash_opt_verify(prod_opt)) {
    return false;
  }

  if (opt_info.current_rdp == target_rdp) {
    return true;
  }

  return mcu_flash_opt_write(prod_opt, true) == MCU_FLASH_STATUS_OK;
}

bool opt_device_is_production(void) {
  mcu_flash_opt_t const* prod_opt = _production_opt();
  if (prod_opt == NULL) {
    return false;
  }

  mcu_flash_rdp_t target_rdp = MCU_FLASH_RDP_UNKNOWN;
  if (mcu_flash_opt_get_rdp(prod_opt, &target_rdp) != MCU_FLASH_STATUS_OK) {
    return false;
  }

  mcu_flash_opt_info_t opt_info = {0};
  if (mcu_flash_opt_read(&opt_info) != MCU_FLASH_STATUS_OK) {
    return false;
  }

  return opt_info.current_rdp == target_rdp;
}
