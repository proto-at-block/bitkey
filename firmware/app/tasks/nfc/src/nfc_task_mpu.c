#include "mpu_auto.h"

#include <stddef.h>
extern int __nfc_task_data_start__;
extern int __nfc_task_data_end__;
extern int __nfc_task_bss_start__;
extern int __nfc_task_bss_end__;
extern int __shared_task_data_start__;
extern int __shared_task_data_end__;
extern int __shared_task_bss_start__;
extern int __shared_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;
extern int flash_filesystem_addr;
extern int flash_filesystem_size;

DECLARE_TASK_MPU(nfc);
DECLARE_TASK_MPU(nfc_isr);

void nfc_task_mpu_init(void) {
  MemoryRegion_t* regions = _nfc_thread_regions.regions;
  int idx = 0;

  /* Shared task data */
  mpu_set_region(regions, idx++, (void*)&__shared_task_data_start__,
                 mpu_calc_region_size(&__shared_task_data_start__, &__shared_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared task BSS */
  mpu_set_region(regions, idx++, (void*)&__shared_task_bss_start__,
                 mpu_calc_region_size(&__shared_task_bss_start__, &__shared_task_bss_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared protected data (read-only) */
  mpu_set_region(
    regions, idx++, (void*)&__shared_task_protected_start__,
    mpu_calc_region_size(&__shared_task_protected_start__, &__shared_task_protected_end__),
    MPU_PARAMS_RO_NOEXEC);

  /* Filesystem (read-only) */
  mpu_set_region(regions, idx++, (void*)&flash_filesystem_addr, (uint32_t)&flash_filesystem_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* NFC task data */
  mpu_set_region(regions, idx++, (void*)&__nfc_task_data_start__,
                 mpu_calc_region_size(&__nfc_task_data_start__, &__nfc_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* NFC task BSS */
  mpu_set_region(regions, idx++, (void*)&__nfc_task_bss_start__,
                 mpu_calc_region_size(&__nfc_task_bss_start__, &__nfc_task_bss_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* GPIO peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_GPIO_ADDR, MPU_PERIPHERAL_GPIO_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* I2C1 peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_I2C1_ADDR, MPU_PERIPHERAL_I2C1_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* CMU peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_CMU_ADDR, MPU_PERIPHERAL_CMU_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  _nfc_thread_regions.privilege = rtos_thread_privileged_bit;
  _nfc_isr_thread_regions.privilege = rtos_thread_privileged_bit;
}
