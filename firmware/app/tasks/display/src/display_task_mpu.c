#include <mpu_auto.h>
#include <rtos.h>
#include <stdint.h>

extern uintptr_t __display_task_data_start__;
extern uintptr_t __display_task_data_end__;
extern uintptr_t __shared_task_data_start__;
extern uintptr_t __shared_task_data_end__;
extern uintptr_t __shared_task_bss_start__;
extern uintptr_t __shared_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;

DECLARE_TASK_MPU(display);
DECLARE_TASK_MPU(display_send);

void display_task_mpu_init(void) {
  MemoryRegion_t* regions = _display_thread_regions.regions;
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

  /* Display task data */
  mpu_set_region(regions, idx++, (void*)&__display_task_data_start__,
                 mpu_calc_region_size(&__display_task_data_start__, &__display_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Due to limited MPU regions, task must be privileged */
  _display_thread_regions.privilege = rtos_thread_privileged_bit;
}

void display_send_task_mpu_init(void) {
  MemoryRegion_t* regions = _display_send_thread_regions.regions;
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

  /* Display send task needs to be privileged to access UC buffers */
  _display_send_thread_regions.privilege = rtos_thread_privileged_bit;
}
