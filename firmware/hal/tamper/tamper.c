#include "tamper.h"

#include "assert.h"
#include "bitlog.h"
#include "log.h"
#include "mcu_reset.h"
#include "rtos.h"
#include "secutils.h"

#include "em_device.h"

#include <string.h>

// Bit 17
#define DIGITAL_GLITCH_MASK (0x20000)
// Bit 18
#define VOLTAGE_GLITCH_MASK (0x40000)

#define RESET_SOURCES_MASK (DIGITAL_GLITCH_MASK + VOLTAGE_GLITCH_MASK)

static rtos_thread_t* tamper_thread_handle = NULL;

void SETAMPERHOST_IRQHandler(void) {
  rtos_notification_signal(tamper_thread_handle);
}

static bool tamper_get_cause(tamper_cause_t* cause) {
  if (se_get_status(&cause->se_status) != SL_STATUS_OK) {
    return false;
  }

  // Some tamper events can cause a reset.
  cause->reset_reason = mcu_reset_rmu_cause_get();
  mcu_reset_rmu_clear();

  return true;
}

static void tamper_thread(void* UNUSED(args)) {
  for (;;) {
    rtos_notification_wait_signal(RTOS_NOTIFICATION_TIMEOUT_MAX);

    // Here, we handle all tamper events that are routed to software, such as
    // events with higher false-positive rate.
    tamper_cause_t tamper_cause = {0};
    if (!tamper_get_cause(&tamper_cause)) {
      LOGE("Failed to get tamper cause");
      mcu_reset_with_reason(MCU_RESET_TAMPER);
    }

    // Reset for specified tamper sources.
    if (tamper_cause.se_status.tamper_status & RESET_SOURCES_MASK ||
        tamper_cause.se_status.tamper_status_raw & RESET_SOURCES_MASK) {
      mcu_reset_with_reason(MCU_RESET_TAMPER);
    }

    // Otherwise, log it.
    BITLOG_EVENT(tamper_status, tamper_cause.se_status.tamper_status);
    BITLOG_EVENT(tamper_status_raw, tamper_cause.se_status.tamper_status_raw);
    BITLOG_EVENT(tamper_reset, tamper_cause.reset_reason);
    LOGI("tamper_cause: tamper_status=%lu tamper_status_raw=%lu reset_reason=%lu",
         tamper_cause.se_status.tamper_status, tamper_cause.se_status.tamper_status_raw,
         tamper_cause.reset_reason);
  }
}

NO_OPTIMIZE void tamper_init(void) {
  SECURE_DO({
    NVIC_ClearPendingIRQ(SETAMPERHOST_IRQn);
    NVIC_EnableIRQ(SETAMPERHOST_IRQn);
  });
  tamper_thread_handle = rtos_thread_create(tamper_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
  SECURE_ASSERT(tamper_thread_handle);
}
