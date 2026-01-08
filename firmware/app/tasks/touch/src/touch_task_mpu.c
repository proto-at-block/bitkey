#include <mpu_auto.h>
#include <rtos.h>
#include <stdint.h>

extern uintptr_t __shared_task_data_start__;
extern uintptr_t __shared_task_data_end__;
extern uintptr_t __shared_task_bss_start__;
extern uintptr_t __shared_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;

DECLARE_TASK_MPU(touch_task);

void touch_task_mpu_init(void) {
  MemoryRegion_t* regions = _touch_task_thread_regions.regions;
  unsigned int idx = 0;

  /* Shared task BSS */
  mpu_set_region(regions, idx++, (void*)&__shared_task_bss_start__,
                 mpu_calc_region_size(&__shared_task_bss_start__, &__shared_task_bss_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared protected data (read-only) */
  mpu_set_region(
    regions, idx++, (void*)&__shared_task_protected_start__,
    mpu_calc_region_size(&__shared_task_protected_start__, &__shared_task_protected_end__),
    MPU_PARAMS_RO_NOEXEC);

  /* Privileged required to access peripherals. */
  _touch_task_thread_regions.privilege = rtos_thread_privileged_bit;
}
