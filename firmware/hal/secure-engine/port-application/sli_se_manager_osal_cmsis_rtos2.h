#pragma once

#include "assert.h"
#include "rtos.h"
#include "sl_status.h"

#include "em_core.h"
#include "em_device.h"

// Based on SiLabs Secure Engine Manager API. See license in README.

// We provide our own RTOS primitives to the SiLabs SE manager.

#define SE_MANAGER_SEMBRX_IRQ_PRIORITY \
  (configMAX_SYSCALL_INTERRUPT_PRIORITY >> (8U - __NVIC_PRIO_BITS))

#define RUNNING_AT_INTERRUPT_LEVEL   (SCB->ICSR & SCB_ICSR_VECTACTIVE_Msk)
#define SE_MANAGER_OSAL_WAIT_FOREVER RTOS_SEMAPHORE_TIMEOUT_MAX

typedef rtos_mutex_t se_manager_osal_mutex_t;
typedef rtos_semaphore_t se_manager_osal_completion_t;

typedef enum {
  osKernelInactive = 0,
  osKernelReady = 1,
  osKernelRunning = 2,
  osKernelLocked = 3,
  osKernelSuspended = 4,
  osKernelError = -1,
  osKernelReserved = 0x7FFFFFFFU
} osKernelState_t;

static inline sl_status_t se_manager_osal_init_mutex(se_manager_osal_mutex_t* mutex) {
  rtos_mutex_create(mutex);
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_free_mutex(se_manager_osal_mutex_t* mutex) {
  (void)mutex;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_take_mutex(se_manager_osal_mutex_t* mutex) {
  return (rtos_mutex_lock(mutex) ? SL_STATUS_OK : SL_STATUS_FAIL);
}

static inline sl_status_t se_manager_osal_take_mutex_non_blocking(se_manager_osal_mutex_t* mutex) {
  (void)mutex;
  ASSERT(false);
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_give_mutex(se_manager_osal_mutex_t* mutex) {
  return (rtos_mutex_unlock(mutex) ? SL_STATUS_OK : SL_STATUS_FAIL);
}

static inline sl_status_t se_manager_osal_init_completion(se_manager_osal_completion_t* p_comp) {
  rtos_semaphore_create(p_comp);
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_free_completion(se_manager_osal_completion_t* p_comp) {
  (void)p_comp;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_wait_completion(se_manager_osal_completion_t* p_comp,
                                                          int ticks) {
  return (rtos_semaphore_take_ticks(p_comp, (uint32_t)ticks) ? SL_STATUS_OK : SL_STATUS_FAIL);
}

static inline sl_status_t se_manager_osal_complete(se_manager_osal_completion_t* p_comp) {
  rtos_semaphore_give_from_isr(p_comp);
  return SL_STATUS_OK;
}

static inline int32_t se_manager_osal_kernel_lock(void) {
  rtos_thread_enter_critical();
  return SL_STATUS_OK;
}

static inline int32_t se_manager_osal_kernel_restore_lock(int32_t lock) {
  (void)lock;
  rtos_thread_exit_critical();
  return SL_STATUS_OK;
}

static inline osKernelState_t se_manager_osal_kernel_get_state(void) {
  return osKernelRunning;
}
