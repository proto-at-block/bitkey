#include "fwup.h"
#include "mpu_auto.h"

#include <stddef.h>

extern int __fwup_task_data_start__;
extern int __fwup_task_data_end__;
extern int __fwup_task_bss_start__;
extern int __fwup_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;
extern int flash_filesystem_addr;
extern int flash_filesystem_size;
extern int __ramfunc_start__;
extern int __ramfunc_end__;
extern int app_a_metadata_size;
extern int __application_a_properties_size;
extern int __application_a_boot_size;
extern int __application_a_signature_size;
extern int bl_base_addr;
extern int bl_slot_size;

DECLARE_TASK_MPU(fwup);

void fwup_task_mpu_init(void) {
  MemoryRegion_t* regions = _fwup_thread_regions.regions;
  int idx = 0;

  uint32_t slot_size = fwup_slot_size();
  void* inactive_slot_addr = fwup_target_slot_address();
  void* current_slot_addr = fwup_current_slot_address();
  uint32_t metadata_size = (uint32_t)&app_a_metadata_size;
  uint32_t properties_size = (uint32_t)&__application_a_properties_size;

  /* FWUP task data */
  /* Note: __fwup_task_data_start__ == __shared_task_data_start__ in linker script */
  mpu_set_region(regions, idx++, (void*)&__fwup_task_data_start__,
                 mpu_calc_region_size(&__fwup_task_data_start__, &__fwup_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* FWUP task BSS */
  mpu_set_region(regions, idx++, (void*)&__fwup_task_bss_start__,
                 mpu_calc_region_size(&__fwup_task_bss_start__, &__fwup_task_bss_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared protected data (read-only) */
  mpu_set_region(
    regions, idx++, (void*)&__shared_task_protected_start__,
    mpu_calc_region_size(&__shared_task_protected_start__, &__shared_task_protected_end__),
    MPU_PARAMS_RO_NOEXEC);

  /* Filesystem (read-only) */
  mpu_set_region(regions, idx++, (void*)&flash_filesystem_addr, (uint32_t)&flash_filesystem_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* RAM functions (executable) */
  mpu_set_region(regions, idx++, (void*)&__ramfunc_start__,
                 mpu_calc_region_size(&__ramfunc_start__, &__ramfunc_end__), MPU_PARAMS_RO_EXEC);

  /* Inactive firmware slot */
  mpu_set_region(regions, idx++, inactive_slot_addr, slot_size, MPU_PARAMS_RW_NOEXEC);

  /* Current firmware metadata */
  mpu_set_region(regions, idx++, current_slot_addr, metadata_size + properties_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* Bootloader region */
  mpu_set_region(regions, idx++, (void*)&bl_base_addr, (uint32_t)&bl_slot_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* CMU peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_CMU_ADDR, MPU_PERIPHERAL_CMU_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* MSC peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_MSC_ADDR, MPU_PERIPHERAL_MSC_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* SEMAILBOX peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_SEMAILBOX_ADDR, MPU_PERIPHERAL_SEMAILBOX_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  _fwup_thread_regions.privilege = rtos_thread_unprivileged_bit;
}
