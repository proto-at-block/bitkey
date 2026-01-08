#include "hal_nfc.h"

#include "assert.h"
#include "exti.h"
#include "hal_nfc_impl.h"
#include "hal_nfc_listener_impl.h"
#include "hal_nfc_loopback_impl.h"
#include "hal_nfc_reader_impl.h"
#include "hal_nfc_timer_impl.h"
#include "log.h"
#include "mcu_i2c_opt.h"
#include "platform.h"
#include "rfal_nfc.h"
#include "rfal_platform.h"
#include "rfal_rf.h"
#include "rtos.h"
#include "st25r3916_irq.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

extern nfc_config_t nfc_config;

hal_nfc_priv_t hal_nfc_priv NFC_TASK_DATA = {
  .discovery_cfg = {0},
  .transfer_in_progress = false,
  .transfer_timeout_ms = 500,
};

static void hal_nfc_i2c_init(void);
static void hal_nfc_enter_mode(hal_nfc_mode_t mode);

void hal_nfc_init(hal_nfc_mode_t mode, rtos_timer_callback_t timer_callback) {
  nfc_timer_init(timer_callback);  // Timers used by ST-RFAL
  hal_nfc_i2c_init();

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
  // Initialize RTOS primitives.
  rtos_event_group_create(&hal_nfc_priv.nfc_events);
#endif

  // Set state to all be the same.
  hal_nfc_priv.current_mode = mode;
  hal_nfc_priv.prev_mode = HAL_NFC_MODE_NONE;
  hal_nfc_priv.next_mode = mode;
#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
  hal_nfc_priv.card_detection_timeout_ms = 0;
#endif

  // Enter initial NFC mode.
  hal_nfc_enter_mode(mode);
}

void hal_nfc_set_mode(hal_nfc_mode_t mode) {
  hal_nfc_priv.next_mode = mode;
}

hal_nfc_mode_t hal_nfc_get_mode(void) {
  return hal_nfc_priv.current_mode;
}

void hal_nfc_wfi(void) {
  exti_wait(&hal_nfc_priv.irq_cfg, RTOS_EVENT_GROUP_TIMEOUT_MAX, true);
}

void hal_nfc_handle_interrupts(void) {
  st25r3916Isr();
}

void hal_nfc_worker(hal_nfc_callback_t callback) {
  rfalNfcWorker();

  // Update NFC mode is transitioning.
  const hal_nfc_mode_t current_mode = hal_nfc_priv.current_mode;
  const hal_nfc_mode_t next_mode = hal_nfc_priv.next_mode;
  if (current_mode != next_mode) {
    hal_nfc_enter_mode(next_mode);
  }

  switch (hal_nfc_get_mode()) {
    case HAL_NFC_MODE_LISTENER:
      hal_nfc_listener_run(callback);
      break;

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
    case HAL_NFC_MODE_READER:
      hal_nfc_reader_run(callback);
      break;

    case HAL_NFC_MODE_LOOPBACK_A:
      // 'break' intentionally omitted.
    case HAL_NFC_MODE_LOOPBACK_B:
      hal_nfc_loopback_run(callback);
      break;
#endif

    case HAL_NFC_MODE_NONE:
      // No-op.
      break;

    default:
      ASSERT(false);
  }
}

static void hal_nfc_i2c_init(void) {
  // Configure interrupts
  hal_nfc_priv.irq_cfg.gpio.mode = MCU_GPIO_MODE_INPUT;
  hal_nfc_priv.irq_cfg.gpio.port = nfc_config.irq.port;
  hal_nfc_priv.irq_cfg.gpio.pin = nfc_config.irq.pin;
  hal_nfc_priv.irq_cfg.trigger = EXTI_TRIGGER_RISING;
  exti_enable(&hal_nfc_priv.irq_cfg);

  mcu_i2c_bus_init(nfc_config.i2c.bus, nfc_config.i2c.device, true);
}

bool st_i2c_blocking_send(const uint8_t* tx_buf, uint16_t len, bool last, bool tx_only) {
  mcu_i2c_transfer_opt_seq_t sequence = {
    .buf = (uint8_t*)tx_buf,
    .len = len,
    .last = last,
    .tx_only = tx_only,
  };
  if (!hal_nfc_priv.transfer_in_progress) {
    mcu_i2c_transfer_state_t state = mcu_i2c_transfer_opt_init(nfc_config.i2c.device, &sequence);
    ASSERT_LOG(state != MCU_I2C_STATE_ERROR, "%d", state);
    hal_nfc_priv.transfer_in_progress = true;
  }

  bool ret = true;
  const uint32_t start = rtos_thread_systime();
  while (!RTOS_DEADLINE(start, hal_nfc_priv.transfer_timeout_ms)) {
    mcu_i2c_transfer_state_t state = mcu_i2c_transfer_opt(nfc_config.i2c.device, &sequence);
    if (state == MCU_I2C_STATE_DONE) {
      hal_nfc_priv.transfer_in_progress = false;
      ret = tx_only;  // If we're done, and we only wanted to transmit -- we are okay.
      break;
    } else if (state == MCU_I2C_STATE_WF_RX_BEGIN) {
      ret = true;  // Okay to resume with st_i2c_blocking_recv().
      break;
    } else if (state == MCU_I2C_STATE_WF_TRANSFER_RESUME) {
      ret = true;  // Okay to resume with st_i2c_blocking_send().
      break;
    }
  }

  return ret;
}

bool st_i2c_blocking_recv(const uint8_t* rx_buf, uint16_t len) {
  mcu_i2c_transfer_opt_seq_t sequence = {
    .buf = (uint8_t*)rx_buf,
    .len = len,
    .last = true,
    .tx_only = false,
  };

  const uint32_t start = rtos_thread_systime();
  while (!RTOS_DEADLINE(start, hal_nfc_priv.transfer_timeout_ms)) {
    if (mcu_i2c_transfer_opt(nfc_config.i2c.device, &sequence) == MCU_I2C_STATE_DONE) {
      break;
    }
  }

  hal_nfc_priv.transfer_in_progress = false;
  return true;
}

static void hal_nfc_enter_mode(hal_nfc_mode_t mode) {
  // Clean up the previous mode, if any.
  switch (hal_nfc_priv.prev_mode) {
    case HAL_NFC_MODE_LISTENER:
      hal_nfc_listener_deinit();
      break;

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
    case HAL_NFC_MODE_LOOPBACK_A:
      // 'break' intentionally omitted.

    case HAL_NFC_MODE_LOOPBACK_B:
      hal_nfc_loopback_deinit();
      break;

    case HAL_NFC_MODE_READER:
      hal_nfc_reader_deinit();
      break;
#endif

    case HAL_NFC_MODE_NONE:
      // 'break' intentionally omitted.

    default:
      break;
  }

  // Initialize the NFC controller.
  st_ret_t err = rfalNfcInitialize();
  ASSERT_LOG(err == RFAL_ERR_NONE, "%d", err);

  // Enter the new mode.
  switch (mode) {
    case HAL_NFC_MODE_LISTENER:
      hal_nfc_listener_init();
      break;

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
    case HAL_NFC_MODE_READER:
      hal_nfc_reader_init();
      break;

    case HAL_NFC_MODE_LOOPBACK_A:
      // 'break' intentionally omitted.
    case HAL_NFC_MODE_LOOPBACK_B:
      hal_nfc_loopback_init(mode);
      break;
#endif

    case HAL_NFC_MODE_NONE:
      // No initialization required for NONE mode.
      break;

    default:
      ASSERT(false);
  }

  hal_nfc_priv.prev_mode = hal_nfc_priv.current_mode;
  hal_nfc_priv.current_mode = mode;
  hal_nfc_priv.next_mode = mode;

  // Validate the configuration.
  err = rfalNfcDiscover(&hal_nfc_priv.discovery_cfg);
  ASSERT_LOG(err == RFAL_ERR_NONE, "%d", err);
  (void)rfalNfcDeactivate(RFAL_NFC_DEACTIVATE_IDLE);
}
