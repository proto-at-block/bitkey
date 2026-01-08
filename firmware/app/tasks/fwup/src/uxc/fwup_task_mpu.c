#include "mpu_auto.h"

#include <stddef.h>

extern int __fwup_task_data_start__;
extern int __fwup_task_data_end__;
extern int __fwup_task_bss_start__;
extern int __fwup_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;
extern int __ramfunc_start__;
extern int __ramfunc_end__;

DECLARE_TASK_MPU(fwup);

void fwup_task_mpu_init(void) {
  MemoryRegion_t* regions = _fwup_thread_regions.regions;
  int idx = 0;

  /* FWUP task data */
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

  _fwup_thread_regions.privilege = rtos_thread_privileged_bit;
}
