#pragma once

#include "FreeRTOS.h"
#include "task.h"

#include <stdbool.h>

typedef struct {
  MemoryRegion_t regions[portNUM_CONFIGURABLE_REGIONS];
  uint32_t privilege;
} rtos_thread_mpu_t;

/**
 * @brief Macro indicating if the current thread is privileged.
 *
 * @return `true` if thread is privileged, otherwise `false`.
 *
 * @note This macro should only be used within a `SYSCALL` attributed function.
 */
#ifndef EMBEDDED_BUILD
#define rtos_thread_is_privileged() false
#else
#define rtos_thread_is_privileged portIS_PRIVILEGED
#endif

/**
 * @brief Macro for raising the current thread from un-privileged -> privileged.
 *
 * @note This macro should only be used within a `SYSCALL` attributed function.
 */
#ifndef EMBEDDED_BUILD
#define rtos_thread_raise_privilege()
#else
#define rtos_thread_raise_privilege portRAISE_PRIVILEGE
#endif

/**
 * @brief Macro for resetting the current thread to un-privileged.
 *
 * @note This macro should only be used within a `SYSCALL` attributed function.
 */
#ifndef EMBEDDED_BUILD
#define rtos_thread_reset_privilege()
#else
#define rtos_thread_reset_privilege portRESET_PRIVILEGE
#endif

/**
 * @brief Privileged thread bit setting.
 */
#define rtos_thread_privileged_bit (portPRIVILEGE_BIT)

/**
 * @brief Un-privileged thread bit setting.
 */
#define rtos_thread_unprivileged_bit (0)

/**
 * @brief Forward declaration for #rtos_in_isr().
 */
bool rtos_in_isr(void);

/**
 * @brief Executes the macro body within a privileged section.
 *
 * @details Raises the current privileged state of the active thread from
 * un-privileged to privileged, then executes the macro body. After the
 * function returns, the thread is returned to being un-privileged. If the
 * thread is already privileged or the macro is executing within an ISR, then
 * the privileged state is left as-is.
 *
 * @note This macro should only be used within a `SYSCALL` attributed function.
 */
#define RTOS_THREAD_WITH_PRIVILEGE(...)                                        \
  do {                                                                         \
    const bool is_privileged = (rtos_thread_is_privileged() || rtos_in_isr()); \
    if (!is_privileged) {                                                      \
      rtos_thread_reset_privilege();                                           \
      rtos_thread_raise_privilege();                                           \
    }                                                                          \
    { __VA_ARGS__; }                                                           \
    if (!is_privileged) {                                                      \
      rtos_thread_reset_privilege();                                           \
    }                                                                          \
  } while (0)

/* Common core thread regions (w1 and w3-core) */
extern rtos_thread_mpu_t _captouch_thread_regions;
extern rtos_thread_mpu_t _nfc_isr_thread_regions;
extern rtos_thread_mpu_t _nfc_thread_regions;
extern rtos_thread_mpu_t _auth_main_thread_regions;
extern rtos_thread_mpu_t _auth_matching_thread_regions;
extern rtos_thread_mpu_t _key_manager_thread_regions;
extern rtos_thread_mpu_t _charger_thread_regions;
extern rtos_thread_mpu_t _fuel_gauge_thread_regions;
extern rtos_thread_mpu_t _tamper_thread_regions;
extern rtos_thread_mpu_t _thermal_thread_regions;
extern rtos_thread_mpu_t _fs_mount_task_regions;
extern rtos_thread_mpu_t _crypto_thread_regions;
extern rtos_thread_mpu_t _ui_thread_regions;

/* Common thread regions (w1, w3-core and w3-uxc) */
extern rtos_thread_mpu_t _fwup_thread_regions;
extern rtos_thread_mpu_t _mfgtest_thread_regions;
extern rtos_thread_mpu_t _sysinfo_thread_regions;

/* Common thread regions (w3-core and w3-uxc) */
extern rtos_thread_mpu_t _usart_task_thread_regions;

/* w3-uxc-specific thread regions */
extern rtos_thread_mpu_t _display_thread_regions;
extern rtos_thread_mpu_t _display_send_thread_regions;
extern rtos_thread_mpu_t _touch_task_thread_regions;

#ifndef CONFIG_PROD
extern rtos_thread_mpu_t _shell_thread_regions;
#endif
