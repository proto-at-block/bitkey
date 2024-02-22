#pragma once

#include "FreeRTOS.h"
#include "task.h"

typedef struct {
  MemoryRegion_t regions[portNUM_CONFIGURABLE_REGIONS];
  uint32_t privilege;
} rtos_thread_mpu_t;

#define rtos_thread_is_privileged    portIS_PRIVILEGED
#define rtos_thread_raise_privilege  portRAISE_PRIVILEGE
#define rtos_thread_reset_privilege  portRESET_PRIVILEGE
#define rtos_thread_privileged_bit   (portPRIVILEGE_BIT)
#define rtos_thread_unprivileged_bit (0)

extern rtos_thread_mpu_t _fwup_thread_regions;
extern rtos_thread_mpu_t _led_thread_regions;
extern rtos_thread_mpu_t _nfc_isr_thread_regions;
extern rtos_thread_mpu_t _nfc_thread_regions;
extern rtos_thread_mpu_t _auth_main_thread_regions;
extern rtos_thread_mpu_t _auth_matching_thread_regions;
extern rtos_thread_mpu_t _key_manager_thread_regions;
extern rtos_thread_mpu_t _led_mfgtest_thread_regions;
extern rtos_thread_mpu_t _mfgtest_thread_regions;
extern rtos_thread_mpu_t _sysinfo_thread_regions;
extern rtos_thread_mpu_t _charger_thread_regions;
extern rtos_thread_mpu_t _fuel_gauge_thread_regions;
extern rtos_thread_mpu_t _tamper_thread_regions;
extern rtos_thread_mpu_t _fs_mount_task_regions;
#ifndef CONFIG_PROD
extern rtos_thread_mpu_t _shell_thread_regions;
#endif
