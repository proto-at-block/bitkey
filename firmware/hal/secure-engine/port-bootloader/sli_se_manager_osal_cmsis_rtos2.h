#pragma once

#include "assert.h"
#include "sl_status.h"

#include "em_core.h"
#include "em_device.h"

// Based on SiLabs Secure Engine Manager API. See license in README.

// This is the bootloader port, which is a noop, since the bootloader does
// not have tasks.

#define SE_MANAGER_SEMBRX_IRQ_PRIORITY (48 >> (8U - __NVIC_PRIO_BITS))
#define SE_MANAGER_OSAL_WAIT_FOREVER   UINT32_MAX

typedef struct {
  int a;
} dummy;
typedef dummy se_manager_osal_mutex_t;
typedef volatile unsigned int se_manager_osal_completion_t;

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
  (void)mutex;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_free_mutex(se_manager_osal_mutex_t* mutex) {
  (void)mutex;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_take_mutex(se_manager_osal_mutex_t* mutex) {
  (void)mutex;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_take_mutex_non_blocking(se_manager_osal_mutex_t* mutex) {
  (void)mutex;
  ASSERT(false);
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_give_mutex(se_manager_osal_mutex_t* mutex) {
  (void)mutex;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_init_completion(se_manager_osal_completion_t* p_comp) {
  (void)p_comp;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_free_completion(se_manager_osal_completion_t* p_comp) {
  (void)p_comp;
  return SL_STATUS_OK;
}

static inline sl_status_t se_manager_osal_wait_completion(se_manager_osal_completion_t* p_comp,
                                                          int ticks) {
  sl_status_t ret;
  if (ticks == (int)SE_MANAGER_OSAL_WAIT_FOREVER) {
    while (*p_comp == osKernelInactive) {
    }
    *p_comp = osKernelInactive;
    ret = SL_STATUS_OK;
  } else {
    while ((*p_comp == osKernelInactive) && (ticks > 0)) {
      ticks--;
    }
    if (*p_comp == osKernelReady) {
      *p_comp = osKernelInactive;
      ret = SL_STATUS_OK;
    } else {
      ret = SL_STATUS_TIMEOUT;
    }
  }

  return ret;
}

static inline sl_status_t se_manager_osal_complete(se_manager_osal_completion_t* p_comp) {
  *p_comp = osKernelReady;
  return SL_STATUS_OK;
}

static inline int32_t se_manager_osal_kernel_lock(void) {
  return SL_STATUS_OK;
}

static inline int32_t se_manager_osal_kernel_restore_lock(int32_t lock) {
  (void)lock;
  return SL_STATUS_OK;
}

static inline osKernelState_t se_manager_osal_kernel_get_state(void) {
  return osKernelRunning;
}
