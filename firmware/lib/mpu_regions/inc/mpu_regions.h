#pragma once

#include "attributes.h"

#include <assert.h>
#include <stdint.h>

#define PERIPHERAL_ADDR_MSC       ((void*)0x40030000)
#define PERIPHERAL_SIZE_MSC       (0x4000)
#define PERIPHERAL_ADDR_CMU       ((void*)0x40008000)
#define PERIPHERAL_SIZE_CMU       (0x4000)
#define PERIPHERAL_ADDR_GPIO      ((void*)0x4003C000)
#define PERIPHERAL_SIZE_GPIO      (0x4000)
#define PERIPHERAL_ADDR_I2C1      ((void*)0x40068000)
#define PERIPHERAL_SIZE_I2C1      (0x4000)
#define PERIPHERAL_ADDR_TIMER0    ((void*)0x40048000)
#define PERIPHERAL_SIZE_TIMER0    (0x4000)
#define PERIPHERAL_ADDR_TIMER1    ((void*)0x4004C000)
#define PERIPHERAL_SIZE_TIMER1    (0x4000)
#define PERIPHERAL_ADDR_EUSART1   ((void*)0x400A0000)
#define PERIPHERAL_SIZE_EUSART1   (0x4000)
#define PERIPHERAL_ADDR_LDMA      ((void*)0x40040000)
#define PERIPHERAL_SIZE_LDMA      (0x4000)
#define PERIPHERAL_ADDR_LDMAXBAR  ((void*)0x40044000)
#define PERIPHERAL_SIZE_LDMAXBAR  (0x4000)
#define PERIPHERAL_ADDR_SEMAILBOX ((void*)0x4C000000)
#define PERIPHERAL_SIZE_SEMAILBOX (0x80)

#define REGION_RO (tskMPU_REGION_READ_ONLY | tskMPU_REGION_EXECUTE_NEVER)
#define REGION_RW (tskMPU_REGION_READ_WRITE | tskMPU_REGION_EXECUTE_NEVER)
#define REGION_RX tskMPU_REGION_READ_ONLY

#define MPU_REGIONS_HELPER                                        \
  X(fwup_task_data_start, __fwup_task_data_start__)               \
  X(fwup_task_data_end, __fwup_task_data_end__)                   \
  X(ramfunc_start, __ramfunc_start__)                             \
  X(ramfunc_end, __ramfunc_end__)                                 \
  X(metadata_size, app_a_metadata_size)                           \
  X(properties_size, __application_a_properties_size)             \
  X(boot_size, __application_a_boot_size)                         \
  X(signature_size, __application_a_signature_size)               \
  X(bl_addr, bl_base_addr)                                        \
  X(bl_size, bl_slot_size)                                        \
  X(fwup_task_bss_start, __fwup_task_bss_start__)                 \
  X(fwup_task_bss_end, __fwup_task_bss_end__)                     \
  X(led_task_data_start, __led_task_data_start__)                 \
  X(led_task_data_end, __led_task_data_end__)                     \
  X(nfc_task_data_start, __nfc_task_data_start__)                 \
  X(nfc_task_data_end, __nfc_task_data_end__)                     \
  X(nfc_task_bss_start, __nfc_task_bss_start__)                   \
  X(nfc_task_bss_end, __nfc_task_bss_end__)                       \
  X(shared_task_bss_start, __shared_task_bss_start__)             \
  X(shared_task_bss_end, __shared_task_bss_end__)                 \
  X(shared_task_data_start, __shared_task_data_start__)           \
  X(shared_task_data_end, __shared_task_data_end__)               \
  X(shared_task_protected_start, __shared_task_protected_start__) \
  X(shared_task_protected_end, __shared_task_protected_end__)     \
  X(fs_start, flash_filesystem_addr)                              \
  X(fs_size, flash_filesystem_size)

#define X(arg1, arg2) extern int arg2;
MPU_REGIONS_HELPER
#undef X

void MemManage_Handler(void);
void mpu_regions_init(void);
