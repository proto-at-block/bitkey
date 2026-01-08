#include "mpu_auto.h"

#include <stddef.h>

extern int __ui_task_data_start__;
extern int __ui_task_data_end__;
extern int __ui_task_funcs_start__;
extern int __ui_task_funcs_end__;
extern int __shared_task_data_start__;
extern int __shared_task_data_end__;
extern int __shared_task_bss_start__;
extern int __shared_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;
extern int flash_filesystem_addr;
extern int flash_filesystem_size;

DECLARE_TASK_MPU(ui);

void ui_task_mpu_init(void) {
  MemoryRegion_t* regions = _ui_thread_regions.regions;
  int idx = 0;

  /* UI task data */
  mpu_set_region(regions, idx++, (void*)&__ui_task_data_start__,
                 mpu_calc_region_size(&__ui_task_data_start__, &__ui_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* UI task funcs (read-execute) */
  mpu_set_region(regions, idx++, (void*)&__ui_task_funcs_start__,
                 mpu_calc_region_size(&__ui_task_funcs_start__, &__ui_task_funcs_end__),
                 MPU_PARAMS_RO_NOEXEC);

  /* Shared task bss */
  mpu_set_region(regions, idx++, (void*)&__shared_task_bss_start__,
                 mpu_calc_region_size(&__shared_task_bss_start__, &__shared_task_bss_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared task data */
  mpu_set_region(regions, idx++, (void*)&__shared_task_data_start__,
                 mpu_calc_region_size(&__shared_task_data_start__, &__shared_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared protected data (read-only) */
  mpu_set_region(
    regions, idx++, (void*)&__shared_task_protected_start__,
    mpu_calc_region_size(&__shared_task_protected_start__, &__shared_task_protected_end__),
    MPU_PARAMS_RO_NOEXEC);

  /* Filesystem (read-only) */
  mpu_set_region(regions, idx++, (void*)&flash_filesystem_addr, (uint32_t)&flash_filesystem_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* GPIO peripheral - needed for LED control */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_GPIO_ADDR, MPU_PERIPHERAL_GPIO_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* Timer0 peripheral - needed for LED PWM/timing */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_TIMER0_ADDR, MPU_PERIPHERAL_TIMER0_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* Timer1 peripheral - needed for LED PWM/timing */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_TIMER1_ADDR, MPU_PERIPHERAL_TIMER1_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  _ui_thread_regions.privilege = rtos_thread_unprivileged_bit;
}
