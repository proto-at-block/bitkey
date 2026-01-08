#include "mpu_auto.h"

#include <stddef.h>

extern int __ui_task_data_start__;
extern int __ui_task_data_end__;
extern int __ui_task_funcs_start__;
extern int __ui_task_funcs_end__;
extern int __shared_task_bss_start__;
extern int __shared_task_bss_end__;
extern int __shared_task_data_start__;
extern int __shared_task_data_end__;
extern int __shared_task_protected_start__;
extern int __shared_task_protected_end__;
extern int flash_filesystem_addr;
extern int flash_filesystem_size;
extern int bl_metadata_page;
extern int bl_metadata_size;
extern int app_a_metadata_page;
extern int app_a_metadata_size;
extern int app_b_metadata_page;
extern int app_b_metadata_size;
#ifdef MFGTEST
extern int __nfc_task_data_start__;
extern int __nfc_task_data_end__;
#endif

DECLARE_TASK_MPU(ui);

void ui_task_mpu_init(void) {
  MemoryRegion_t* regions = _ui_thread_regions.regions;
  int idx = 0;

  /* UI task data */
  mpu_set_region(regions, idx++, (void*)&__ui_task_data_start__,
                 mpu_calc_region_size(&__ui_task_data_start__, &__ui_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* UI task funcs */
  mpu_set_region(regions, idx++, (void*)&__ui_task_funcs_start__,
                 mpu_calc_region_size(&__ui_task_funcs_start__, &__ui_task_funcs_end__),
                 MPU_PARAMS_RO_NOEXEC);

  /* Shared task data */
  mpu_set_region(regions, idx++, (void*)&__shared_task_data_start__,
                 mpu_calc_region_size(&__shared_task_data_start__, &__shared_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Shared task bss */
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

  /* GPIO peripheral - needed for reading button state */
  mpu_set_region(regions, idx++, MPU_PERIPHERAL_GPIO_ADDR, MPU_PERIPHERAL_GPIO_SIZE,
                 MPU_PARAMS_PERIPHERAL);

  /* Bootloader metadata (read-only) */
  mpu_set_region(regions, idx++, (void*)&bl_metadata_page, (uint32_t)&bl_metadata_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* App A metadata (read-only) */
  mpu_set_region(regions, idx++, (void*)&app_a_metadata_page, (uint32_t)&app_a_metadata_size,
                 MPU_PARAMS_RO_NOEXEC);

  /* App B metadata (read-only) */
  mpu_set_region(regions, idx++, (void*)&app_b_metadata_page, (uint32_t)&app_b_metadata_size,
                 MPU_PARAMS_RO_NOEXEC);

#ifdef MFGTEST
  /* NFC task data */
  mpu_set_region(regions, idx++, (void*)&__nfc_task_data_start__,
                 mpu_calc_region_size(&__nfc_task_data_start__, &__nfc_task_data_end__),
                 MPU_PARAMS_RW_NOEXEC);

  /* Required for run-in testing. */
  _ui_thread_regions.privilege = rtos_thread_privileged_bit;
#else
  _ui_thread_regions.privilege = rtos_thread_unprivileged_bit;
#endif
}
