#include "mpu_auto.h"

#include <stddef.h>

extern int __fwup_task_data_start__;
extern int __fwup_task_data_end__;
extern int __fwup_task_bss_start__;
extern int __fwup_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;

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

  /* SystemView needs privileged access to the DWT cycle counter and RTT
     control block in the PPB region (0xE000xxxx), which the MPU blocks
     for unprivileged threads. */
#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  _fwup_thread_regions.privilege = rtos_thread_privileged_bit;
#else
  _fwup_thread_regions.privilege = rtos_thread_unprivileged_bit;
#endif
}
