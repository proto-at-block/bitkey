#pragma once

#include "assert.h"
#include "rtos_mpu.h"
#ifdef EFR32MG24B010F1536IM48
#include "mpu_efr32xg22.h"
#endif

#ifdef STM32U585xx
#include "mpu_stm32u5xx.h"
#endif

#include <stddef.h>
#include <stdint.h>

#define DECLARE_TASK_MPU(task_name) rtos_thread_mpu_t _##task_name##_thread_regions = {0}

static inline size_t mpu_calc_region_size(void* start, void* end) {
  return (uintptr_t)end - (uintptr_t)start;
}

#define MPU_PARAMS_RW_NOEXEC  (tskMPU_REGION_READ_WRITE | tskMPU_REGION_EXECUTE_NEVER)
#define MPU_PARAMS_RO_NOEXEC  (tskMPU_REGION_READ_ONLY | tskMPU_REGION_EXECUTE_NEVER)
#define MPU_PARAMS_RO_EXEC    (tskMPU_REGION_READ_ONLY)
#define MPU_PARAMS_PERIPHERAL (MPU_PARAMS_RW_NOEXEC | tskMPU_REGION_DEVICE_MEMORY)

static inline void mpu_set_region(MemoryRegion_t* regions, int idx, void* base, size_t size,
                                  uint32_t params) {
  ASSERT(idx >= 0 && idx < (int)portNUM_CONFIGURABLE_REGIONS);
  regions[idx].pvBaseAddress = base;
  regions[idx].ulLengthInBytes = size;
  regions[idx].ulParameters = params;
}

void* fwup_target_slot_address(void);
void* fwup_current_slot_address(void);
size_t fwup_slot_size(void);

static inline int mpu_setup_privileged_default(MemoryRegion_t* regions) {
  extern int __shared_task_protected_start__;
  extern int __shared_task_protected_end__;
  extern int __ramfunc_start__;
  extern int __ramfunc_end__;
  extern int app_a_metadata_size;
  extern int __application_a_properties_size;
  extern int __application_a_signature_size;
  extern int __application_a_boot_size;
  extern int bl_base_addr;
  extern int bl_slot_size;

  int idx = 0;

  void* inactive_slot_addr = fwup_target_slot_address();
  size_t slot_size = fwup_slot_size();
  void* current_slot_addr = fwup_current_slot_address();
  size_t metadata_size = (size_t)&app_a_metadata_size;
  size_t properties_size = (size_t)&__application_a_properties_size;
  size_t signature_size = (size_t)&__application_a_signature_size;
  size_t boot_size = (size_t)&__application_a_boot_size;
  void* current_slot_signature_addr =
    current_slot_addr + metadata_size + properties_size + boot_size;

  mpu_set_region(
    regions, idx++, (void*)&__shared_task_protected_start__,
    mpu_calc_region_size(&__shared_task_protected_start__, &__shared_task_protected_end__),
    MPU_PARAMS_RO_NOEXEC);

  mpu_set_region(regions, idx++, inactive_slot_addr, slot_size, MPU_PARAMS_RW_NOEXEC);

  mpu_set_region(regions, idx++, (void*)&bl_base_addr, (size_t)&bl_slot_size, MPU_PARAMS_RO_NOEXEC);

  mpu_set_region(regions, idx++, (void*)&__ramfunc_start__,
                 mpu_calc_region_size(&__ramfunc_start__, &__ramfunc_end__), MPU_PARAMS_RO_EXEC);

  mpu_set_region(regions, idx++, current_slot_addr, metadata_size + properties_size,
                 MPU_PARAMS_RO_NOEXEC);

  mpu_set_region(regions, idx++, current_slot_signature_addr, signature_size, MPU_PARAMS_RO_NOEXEC);

  return idx;
}
