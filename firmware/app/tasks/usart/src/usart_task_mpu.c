
#include <mpu_auto.h>
#include <rtos.h>
#include <stdint.h>

extern uintptr_t __usart_task_data_start__;
extern uintptr_t __usart_task_data_end__;
extern uintptr_t __shared_task_data_start__;
extern uintptr_t __shared_task_data_end__;
extern uintptr_t __shared_task_bss_start__;
extern uintptr_t __shared_task_bss_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;

DECLARE_TASK_MPU(usart_task);

void usart_task_mpu_init(void) {
  MemoryRegion_t* regions = _usart_task_thread_regions.regions;
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

  /* USART task data */
  mpu_set_region(regions, idx++, (void*)&__usart_task_data_start__,
                 mpu_calc_region_size(&__usart_task_data_start__, &__usart_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

#ifdef EFR32MG24B010F1536IM48
  /* Shared task data */
  mpu_set_region(regions, idx++, (void*)&__shared_task_data_start__,
                 mpu_calc_region_size(&__shared_task_data_start__, &__shared_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* CMU peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_CMU_ADDR, MPU_PERIPHERAL_CMU_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* GPIO peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_GPIO_ADDR, MPU_PERIPHERAL_GPIO_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* USART peripheral */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_EUSART0_ADDR, MPU_PERIPHERAL_EUSART0_SIZE,
                 MPU_PARAMS_PERIPHERAL);
#endif

  /* Privileged required to access peripherals. */
  _usart_task_thread_regions.privilege = rtos_thread_privileged_bit;
}
