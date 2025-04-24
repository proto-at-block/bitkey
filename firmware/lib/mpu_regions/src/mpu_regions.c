#include "mpu_regions.h"

#include "fwup.h"
#include "log.h"
#include "mcu.h"
#include "mcu_reset.h"
#include "proto_helpers.h"
#include "rtos_mpu.h"

#include <string.h>

/* Task Privileges
 * unprivileged: fwup, led, nfc_isr, nfc_thread
 * privileged: auth matching, auth main, key manager, sysinfo, power, tamper, fs mount
 *             led_mfgtest, mfgtest, shell
 */

/* mpu regions for each thread */
rtos_thread_mpu_t _fwup_thread_regions;
rtos_thread_mpu_t _led_thread_regions;
rtos_thread_mpu_t _captouch_thread_regions;
rtos_thread_mpu_t _nfc_isr_thread_regions;
rtos_thread_mpu_t _nfc_thread_regions;
rtos_thread_mpu_t _auth_matching_thread_regions;  // TODO(W-4578)
rtos_thread_mpu_t _auth_main_thread_regions;      // TODO(W-4578)
rtos_thread_mpu_t _key_manager_thread_regions;    // TODO(W-4579)
rtos_thread_mpu_t _led_mfgtest_thread_regions;
rtos_thread_mpu_t _mfgtest_thread_regions;
rtos_thread_mpu_t _sysinfo_thread_regions;
rtos_thread_mpu_t _charger_thread_regions;
rtos_thread_mpu_t _fuel_gauge_thread_regions;
rtos_thread_mpu_t _tamper_thread_regions;
rtos_thread_mpu_t _fs_mount_task_regions;
rtos_thread_mpu_t _crypto_thread_regions;
#ifndef CONFIG_PROD
rtos_thread_mpu_t _shell_thread_regions;
#endif

// Create all the local mpu region vars we need then initialize each region
void mpu_regions_init(void) {
#define X(arg1, arg2) const uint32_t arg1 = (uint32_t)&arg2;
  MPU_REGIONS_HELPER
#undef X
  uint32_t fwup_task_data_size = fwup_task_data_end - fwup_task_data_start;
  uint32_t ramfunc_size = ramfunc_end - ramfunc_start;
  uint32_t fwup_task_bss_size = fwup_task_bss_end - fwup_task_bss_start;
  uint32_t slot_size = fwup_slot_size();
  uint32_t nfc_task_data_size = nfc_task_data_end - nfc_task_data_start;
  uint32_t nfc_task_bss_size = nfc_task_bss_end - nfc_task_bss_start;
  uint32_t shared_task_bss_size = shared_task_bss_end - shared_task_bss_start;
  uint32_t led_task_data_size = led_task_data_end - led_task_data_start;
  uint32_t shared_task_data_size = shared_task_data_end - shared_task_data_start;
  uint32_t shared_task_protected_size = shared_task_protected_end - shared_task_protected_start;

  void* inactive_slot_addr = fwup_target_slot_address();
  void* current_slot_addr = fwup_current_slot_address();
  void* current_slot_signature_addr =
    current_slot_addr + metadata_size + properties_size + boot_size;

  rtos_thread_mpu_t privileged_task_default_regions = {
    {
      {(void*)shared_task_protected_start, shared_task_protected_size, REGION_RO},
      {(void*)inactive_slot_addr, slot_size, REGION_RW},
      {(void*)bl_addr, bl_size, REGION_RO},
      {(void*)ramfunc_start, ramfunc_size, REGION_RX},
      {(void*)current_slot_addr, metadata_size + properties_size, REGION_RO},
      {(void*)current_slot_signature_addr, signature_size, REGION_RO},
      // freertos marks active slot as RX
    },
    rtos_thread_privileged_bit,
  };

  rtos_thread_mpu_t led_regions = {
    {
      {(void*)shared_task_data_start, shared_task_data_size, REGION_RW},
      {(void*)shared_task_protected_start, shared_task_protected_size, REGION_RO},
      {(void*)fs_start, fs_size, REGION_RO},
      {(void*)led_task_data_start, led_task_data_size, REGION_RW},
      {PERIPHERAL_ADDR_TIMER0, PERIPHERAL_SIZE_TIMER0, REGION_RW},
      {PERIPHERAL_ADDR_TIMER1, PERIPHERAL_SIZE_TIMER1, REGION_RW},
      {PERIPHERAL_ADDR_GPIO, PERIPHERAL_SIZE_GPIO, REGION_RW},
    },
    rtos_thread_unprivileged_bit,
  };

#if 0
  // TODO(W-4890): Re-enable, or root cause why we can't.
  rtos_thread_mpu_t nfc_isr_regions = {
    {
      {(void*)shared_task_data_start, shared_task_data_size, REGION_RW},
      {(void*)shared_task_protected_start, shared_task_protected_size, REGION_RO},
      {(void*)fs_start, fs_size, REGION_RO},
      {(void*)nfc_task_data_start, nfc_task_data_size, REGION_RW},
      {(void*)nfc_task_bss_start, nfc_task_bss_size, REGION_RW},
      {PERIPHERAL_ADDR_GPIO, PERIPHERAL_SIZE_GPIO, REGION_RW},
      {PERIPHERAL_ADDR_I2C1, PERIPHERAL_SIZE_I2C1, REGION_RW},
    },
    rtos_thread_unprivileged_bit,
  };
#endif

  rtos_thread_mpu_t nfc_regions = {
    {
      {(void*)shared_task_data_start, shared_task_data_size, REGION_RW},
      {(void*)shared_task_bss_start, shared_task_bss_size, REGION_RW},
      {(void*)shared_task_protected_start, shared_task_protected_size, REGION_RO},
      {(void*)fs_start, fs_size, REGION_RO},
      {(void*)nfc_task_data_start, nfc_task_data_size, REGION_RW},
      {(void*)nfc_task_bss_start, nfc_task_bss_size, REGION_RW},
      {PERIPHERAL_ADDR_GPIO, PERIPHERAL_SIZE_GPIO, REGION_RW},
      {PERIPHERAL_ADDR_I2C1, PERIPHERAL_SIZE_I2C1, REGION_RW},
      {PERIPHERAL_ADDR_CMU, PERIPHERAL_SIZE_CMU, REGION_RW},
    },
    rtos_thread_privileged_bit,
  };  // start task off in privileged mode, but drop before the nfc worker

  rtos_thread_mpu_t fwup_regions = {
    {
      /* fwup overlaps data/bss with the shared task region to save 2 mpu slots */
      {(void*)fwup_task_data_start, fwup_task_data_size, REGION_RW},
      {(void*)fwup_task_bss_start, fwup_task_bss_size, REGION_RW},
      {(void*)shared_task_protected_start, shared_task_protected_size, REGION_RO},
      {(void*)fs_start, fs_size, REGION_RO},
      {(void*)ramfunc_start, ramfunc_size, REGION_RX},
      {(void*)inactive_slot_addr, slot_size, REGION_RW},
      {(void*)current_slot_addr, metadata_size + properties_size, REGION_RO},
      {(void*)bl_addr, bl_size, REGION_RO},
      // freertos marks active slot as RX
      {PERIPHERAL_ADDR_CMU, PERIPHERAL_SIZE_CMU, REGION_RW},
      {PERIPHERAL_ADDR_MSC, PERIPHERAL_SIZE_MSC, REGION_RW},
      {PERIPHERAL_ADDR_SEMAILBOX, PERIPHERAL_SIZE_SEMAILBOX, REGION_RW},
    },
    rtos_thread_unprivileged_bit,
  };

  // unprivileged tasks
  memcpy(&_led_thread_regions, &led_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_nfc_thread_regions, &nfc_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_fwup_thread_regions, &fwup_regions, sizeof(rtos_thread_mpu_t));

  // privileged tasks
  memcpy(&_nfc_isr_thread_regions, &privileged_task_default_regions,
         sizeof(rtos_thread_mpu_t));  // TODO(W-4890)
  memcpy(&_auth_matching_thread_regions, &privileged_task_default_regions,
         sizeof(rtos_thread_mpu_t));  // TODO(W-4578)
  memcpy(&_auth_main_thread_regions, &privileged_task_default_regions,
         sizeof(rtos_thread_mpu_t));  // TODO(W-4578)
  memcpy(&_key_manager_thread_regions, &privileged_task_default_regions,
         sizeof(rtos_thread_mpu_t));  // TODO(W-4579)
  memcpy(&_crypto_thread_regions, &privileged_task_default_regions,
         sizeof(rtos_thread_mpu_t));  // TODO(W-4579)
  memcpy(&_sysinfo_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_charger_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_fuel_gauge_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_tamper_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_fs_mount_task_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_led_mfgtest_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_mfgtest_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
  memcpy(&_captouch_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
#ifndef CONFIG_PROD
  memcpy(&_shell_thread_regions, &privileged_task_default_regions, sizeof(rtos_thread_mpu_t));
#endif
}

void MemManage_Handler(void) {
  ASSERT(0);
  mcu_reset_with_reason(MCU_RESET_FAULT);
}
