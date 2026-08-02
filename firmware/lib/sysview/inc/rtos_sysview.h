#pragma once

#include <stdbool.h>
#include <stdint.h>

/*
 * Wallet SystemView shim.
 *
 * Provides a single entry point used by the rtos_mutex / rtos_semaphore /
 * rtos_queue create-time naming macros. Maintaining a registry inside this
 * file lets us:
 *
 *   1. Buffer name calls that happen before SEGGER_SYSVIEW_Conf() runs (very
 *      common: anything created in mcu_init / hal_*_init) so they are
 *      replayed once SystemView is up.
 *   2. Re-emit every name on each session restart so a host that attaches
 *      mid-run still sees a complete resource table.
 *
 * When sysview is disabled at build time the call collapses to a no-op.
 */

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1) && defined(EMBEDDED_BUILD)
void rtos_sysview_register_resource(uint32_t handle, const char* name);
unsigned int rtos_sysview_register_marker(const char* name);
void rtos_sysview_monitor_init(uint32_t timestamp_freq_hz);
void rtos_sysview_task_switched_in(uint32_t handle, const char* name, bool is_idle);
void rtos_sysview_task_switched_out(uint32_t handle, const char* name, bool is_idle);
void rtos_sysview_irq_disable_track_start(const char* caller_name);
void rtos_sysview_irq_disable_track_stop(bool restore_enables_irq);
uint32_t rtos_sysview_lock(void);
void rtos_sysview_unlock(uint32_t lock_state);
#else
static inline void rtos_sysview_register_resource(uint32_t handle, const char* name) {
  (void)handle;
  (void)name;
}

static inline unsigned int rtos_sysview_register_marker(const char* name) {
  (void)name;
  return 0u;
}

static inline void rtos_sysview_monitor_init(uint32_t timestamp_freq_hz) {
  (void)timestamp_freq_hz;
}

static inline void rtos_sysview_task_switched_in(uint32_t handle, const char* name, bool is_idle) {
  (void)handle;
  (void)name;
  (void)is_idle;
}

static inline void rtos_sysview_task_switched_out(uint32_t handle, const char* name, bool is_idle) {
  (void)handle;
  (void)name;
  (void)is_idle;
}

static inline void rtos_sysview_irq_disable_track_start(const char* caller_name) {
  (void)caller_name;
}

static inline void rtos_sysview_irq_disable_track_stop(bool restore_enables_irq) {
  (void)restore_enables_irq;
}

static inline uint32_t rtos_sysview_lock(void) {
  return 0u;
}

static inline void rtos_sysview_unlock(uint32_t lock_state) {
  (void)lock_state;
}
#endif
