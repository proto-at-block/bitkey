#include "mpu_auto.h"
#include "rtos.h"

#include <stdint.h>

extern uintptr_t __shared_task_data_start__;
extern uintptr_t __shared_task_data_end__;
extern uintptr_t __shared_task_bss_start__;
extern uintptr_t __shared_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;

DECLARE_TASK_MPU(sysinfo);

void sysinfo_task_mpu_init(void) {
  MemoryRegion_t* regions = _sysinfo_thread_regions.regions;
  unsigned int idx = 0;

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

  /* Needs to access all peripherals */
  _sysinfo_thread_regions.privilege = rtos_thread_privileged_bit;
}
