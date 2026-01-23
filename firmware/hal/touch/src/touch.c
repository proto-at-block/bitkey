#include "touch.h"

#include "assert.h"
#include "attributes.h"
#include "exti.h"
#include "log.h"
#include "mcu.h"
#include "mcu_i2c.h"
#include "rtos.h"
#include "touch_ft3169.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Private touch state structure.
 */
typedef struct {
  touch_config_t config;          /**< Touch configuration. */
  exti_config_t exti_config;      /**< Interrupt configuration. */
  uint8_t flow_work_cnt_last;     /**< Last flow work counter value. */
  uint8_t flow_work_hold_cnt;     /**< Number of consecutive identical flow counts. */
  touch_event_t last_touch_event; /**< Cached coordinates for active touch. */
  uint32_t last_esd_check_ms;     /**< Timestamp of last ESD check. */
} touch_priv_t;

touch_priv_t _touch_priv = {0};

static bool _touch_check_chip_id(void);
static bool _touch_fetch_touch_data(ft3169_touch_data_t* data, size_t data_size);
static bool _touch_handle_fw_recovery(const ft3169_touch_data_t* data);
static bool _touch_hw_reset(void);
static bool _touch_i2c_read(uint8_t reg_addr, uint8_t* rx_buf, size_t rx_len);
static bool _touch_i2c_transfer(mcu_i2c_transfer_seq_t* seq);
static bool _touch_i2c_write(uint8_t reg_addr, uint8_t* tx_buf, size_t tx_len);
static bool _touch_read_reg(uint8_t reg_addr, uint8_t* value);
static bool _touch_reset_device(void);
static bool _touch_set_mode(uint8_t mode);
static bool _touch_decode_data(const ft3169_touch_data_t* data, touch_event_t* event);
static bool _touch_write_reg(uint8_t reg_addr, uint8_t value);

void touch_init(const touch_config_t* config) {
  ASSERT(config != NULL);

  // Store configuration and callback.
  _touch_priv.config = *config;

  switch (config->interface_type) {
    case TOUCH_INTERFACE_I2C:
      // Initialize I2C bus and device.
      mcu_i2c_bus_init(&config->interface.i2c.config, &config->interface.i2c.device, true);
      break;

    case TOUCH_INTERFACE_NONE:
      // 'break' intentionally omitted.

    default:
      ASSERT(false);
  }

  if (_touch_priv.config.gpio.reset != NULL) {
    // Configure reset GPIO.
    mcu_gpio_configure(_touch_priv.config.gpio.reset, false);
  }

  // Enable 1v8 power to the touch controller before enabling interrupts.
  if (_touch_priv.config.gpio.pwr.pwr_1v8_en != NULL) {
    mcu_gpio_configure(_touch_priv.config.gpio.pwr.pwr_1v8_en, false);
  }

  // Enable AVDD power to the touch controller.
  if (_touch_priv.config.gpio.pwr.pwr_avdd_en != NULL) {
    mcu_gpio_configure(_touch_priv.config.gpio.pwr.pwr_avdd_en, false);
  }

  rtos_thread_sleep(10);

  if (_touch_priv.config.gpio.reset != NULL) {
    // Configure reset GPIO deasserted (active low).
    mcu_gpio_configure(_touch_priv.config.gpio.reset, false);
  }

  // Enable 1v8 power to the touch controller before enabling interrupts.
  if (_touch_priv.config.gpio.pwr.pwr_1v8_en != NULL) {
    mcu_gpio_configure(_touch_priv.config.gpio.pwr.pwr_1v8_en, true);
  }

  // Enable AVDD power to the touch controller.
  if (_touch_priv.config.gpio.pwr.pwr_avdd_en != NULL) {
    mcu_gpio_configure(_touch_priv.config.gpio.pwr.pwr_avdd_en, true);
  }

  if (_touch_priv.config.gpio.interrupt != NULL) {
    // Per FT3169 application note 1.2, signal is active low.
    _touch_priv.exti_config.gpio = *_touch_priv.config.gpio.interrupt;
    _touch_priv.exti_config.trigger = EXTI_TRIGGER_FALLING;
    exti_enable(&_touch_priv.exti_config);
  }

  _touch_priv.flow_work_cnt_last = 0;
  _touch_priv.flow_work_hold_cnt = 0;
  _touch_priv.last_touch_event = (touch_event_t){0};
  _touch_priv.last_esd_check_ms = 0;
}

bool touch_enable(void) {
  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return false;
  }

  if (!_touch_reset_device()) {
    return false;
  }

  _touch_priv.flow_work_cnt_last = 0;
  _touch_priv.flow_work_hold_cnt = 0;

  return true;
}

bool touch_disable(void) {
  // Per FT3169 datasheet figure 3-6
  // Assert reset (active low) and hold.
  if (_touch_priv.config.gpio.reset != NULL) {
    mcu_gpio_clear(_touch_priv.config.gpio.reset);
  }

  return true;
}

bool touch_get_coordinates(touch_event_t* event) {
  ASSERT(event != NULL);

  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return false;
  }

  FT3169_TOUCH_DATA(touch_data, FT3169_MAX_TOUCH_POINTS);
  if (!_touch_fetch_touch_data(touch_data, touch_data_size)) {
    return false;
  }

  // Clear EXTI on read as 'INT' will be driven high.
  if (_touch_priv.config.gpio.interrupt != NULL) {
    exti_clear(&_touch_priv.exti_config);
  }

  if (_touch_handle_fw_recovery(touch_data)) {
    return false;
  }

  if (!_touch_decode_data(touch_data, event)) {
    return false;
  }

  // Save the latest touch event information.
  touch_set_latest_event(event);

  return true;
}

bool touch_pend_event(touch_event_t* event, uint32_t timeout_ms) {
  ASSERT(event != NULL);
  if (_touch_priv.config.gpio.interrupt == NULL) {
    // Cannot pend if no interrupt is present.
    return false;
  }

  if (!exti_wait(&_touch_priv.exti_config, timeout_ms, false /* clear */)) {
    // Timed out waiting for 'INT' signal.
    return false;
  }
  if (!touch_get_coordinates(event)) {
    // Failed to get touch coordinates (note: no gesture support today).
    return false;
  }

  return true;
}

static bool _touch_set_mode(uint8_t mode) {
  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return false;
  }

  // Bits 6:4 for the mode selection.
  uint8_t device_mode = (mode << 4u);
  return _touch_i2c_write(FT3169_REG_MODE_SWITCH, &device_mode, sizeof(device_mode));
}

static bool _touch_fetch_touch_data(ft3169_touch_data_t* data, size_t data_size) {
  return _touch_i2c_read(FT3169_REG_GESTURE, (uint8_t*)data, data_size);
}

static bool _touch_handle_fw_recovery(const ft3169_touch_data_t* data) {
  if ((data->num_points == 0xFF) && (*(uint8_t*)&data->touch[0].touch_xh == 0xFF)) {
    (void)_touch_reset_device();
    return true;
  }
  return false;
}

static bool _touch_decode_data(const ft3169_touch_data_t* data, touch_event_t* event) {
  const uint8_t raw_points = data->num_points & 0x0F;

  if (raw_points == 0) {
    return false;
  }
  event->timestamp_ms = rtos_thread_systime();

  switch (data->touch[0].touch_xh.event_flag) {
    case FT3169_EVENT_PRESS_DOWN:
      event->event_type = TOUCH_EVENT_TOUCH_DOWN;
      break;
    case FT3169_EVENT_CONTACT:
      event->event_type = TOUCH_EVENT_CONTACT;
      break;
    case FT3169_EVENT_LIFT_UP:
      event->event_type = TOUCH_EVENT_TOUCH_UP;
      break;
    case FT3169_EVENT_INVALID:
    case FT3169_EVENT_NO_EVENT:
      return false;
      break;
    default:
      return false;
  }

  event->coord.x = FT3169_TOUCH_COORD_X(&data->touch[0]);
  event->coord.y = FT3169_TOUCH_COORD_Y(&data->touch[0]);

  return true;
}

static bool _touch_i2c_write(uint8_t reg_addr, uint8_t* tx_buf, size_t tx_len) {
  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return false;
  }

  mcu_i2c_transfer_seq_t seq = {
    .flags = MCU_I2C_FLAG_WRITE_WRITE,
    .buf =
      {
        {
          .data = &reg_addr,
          .len = sizeof(reg_addr),
        },
        {
          .data = tx_buf,
          .len = (uint16_t)tx_len,
        },
      },
  };

  return _touch_i2c_transfer(&seq);
}

static bool _touch_i2c_read(uint8_t reg_addr, uint8_t* rx_buf, size_t rx_len) {
  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return false;
  }

  mcu_i2c_transfer_seq_t seq = {
    .flags = MCU_I2C_FLAG_WRITE_READ,
    .buf =
      {
        {
          .data = &reg_addr,
          .len = sizeof(reg_addr),
        },
        {
          .data = rx_buf,
          .len = (uint16_t)rx_len,
        },
      },
  };

  return _touch_i2c_transfer(&seq);
}

static bool _touch_i2c_transfer(mcu_i2c_transfer_seq_t* seq) {
  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return false;
  }

  for (uint8_t attempt = 0; attempt < FT3169_I2C_MAX_RETRIES; attempt++) {
    const mcu_i2c_err_t result =
      mcu_i2c_transfer(&_touch_priv.config.interface.i2c.device, seq, FT3169_I2C_TIMEOUT_MS);
    if (result == MCU_I2C_TRANSFER_DONE) {
      return true;
    }
    rtos_thread_sleep(1);
  }

  return false;
}

static bool _touch_write_reg(uint8_t reg_addr, uint8_t value) {
  return _touch_i2c_write(reg_addr, &value, sizeof(value));
}

static bool _touch_read_reg(uint8_t reg_addr, uint8_t* value) {
  return _touch_i2c_read(reg_addr, value, sizeof(*value));
}

static bool _touch_check_chip_id(void) {
  ft3169_chip_id_t chip_id = {0};
  if (_touch_read_reg(FT3169_REG_CHIP_ID, &chip_id.id_high) &&
      _touch_read_reg(FT3169_REG_CHIP_ID2, &chip_id.id_low) && chip_id.id_high == FT3169_CHIP_IDH &&
      chip_id.id_low == FT3169_CHIP_IDL) {
    return true;
  }

  if (!_touch_write_reg(FT3169_REG_BOOT_START, FT3169_BOOT_TRIGGER_VALUE)) {
    return false;
  }

  rtos_thread_sleep(FT3169_BOOT_ID_DELAY_MS);

  if (!_touch_i2c_read(FT3169_CMD_READ_ID, (uint8_t*)&chip_id, sizeof(chip_id))) {
    return false;
  }

  return (chip_id.id_high == FT3169_CHIP_IDH) && (chip_id.id_low == FT3169_CHIP_IDL);
}

static bool _touch_hw_reset(void) {
  if (_touch_priv.config.gpio.reset != NULL) {
    mcu_gpio_clear(_touch_priv.config.gpio.reset);
    rtos_thread_sleep(FT3169_RESET_PULSE_MS);
    mcu_gpio_set(_touch_priv.config.gpio.reset);
  }

  rtos_thread_sleep(FT3169_INIT_TIME_MS);
  return true;
}

static bool _touch_reset_device(void) {
  bool status = true;

  if (!_touch_hw_reset()) {
    return false;
  }

  if (!_touch_check_chip_id()) {
    return false;
  }

  status &= _touch_set_mode(FT3169_MODE_WORKING);

  /* These settings should be default on power up, but write them to make sure */
  status &= touch_exit_monitor_mode();

  return status;
}

bool touch_enter_monitor_mode(void) {
  bool status = true;
  /* Enter Gesture Mode */
  status &= _touch_write_reg(FT3169_REG_GESTURE_EN, FT3169_GESTURE_ENABLE);
  /* Set Gesture to Wake From Monitor Mode */
  status &= _touch_write_reg(FT3169_REG_GESTURE_MASK, FT3169_GESTURE_MASK_DOUBLE_CLICK);
  /* Set Power Mode to Monitor Mode*/
  status &= _touch_write_reg(FT3169_REG_POWER_MODE, FT3169_POWER_MODE_MONITOR);
  return status;
}

bool touch_exit_monitor_mode(void) {
  bool status = true;
  /* Disable Gesture Mode */
  status &= _touch_write_reg(FT3169_REG_GESTURE_EN, 0x00);
  /* Clear Gesture Mask */
  status &= _touch_write_reg(FT3169_REG_GESTURE_MASK, 0x00);
  /* Set Power Mode to Active */
  status &= _touch_write_reg(FT3169_REG_POWER_MODE, FT3169_POWER_MODE_ACTIVE);
  return status;
}

void touch_set_latest_event(const touch_event_t* event) {
  ASSERT(event != NULL);
  rtos_thread_enter_critical();
  _touch_priv.last_touch_event = *event;
  rtos_thread_exit_critical();
}

void touch_get_latest_event(touch_event_t* event) {
  ASSERT(event != NULL);
  rtos_thread_enter_critical();
  *event = _touch_priv.last_touch_event;
  rtos_thread_exit_critical();
}

/* This function needs to be called every 1 second per
- FT3169 sample code Porting Guide EN.pdf section 3.4
- Sample Code "sample code-for-FT3169(pw=ft3169) fts_esdcheck_process function"
*/
static void _touch_check_esd(void) {
  if (_touch_priv.config.interface_type != TOUCH_INTERFACE_I2C) {
    return;
  }

  uint8_t flow_cnt = 0;
  if (!_touch_read_reg(FT3169_REG_FLOW_WORK_CNT, &flow_cnt)) {
    return;
  }

  uint8_t reg_value = 0;
  if (!_touch_read_reg(FT3169_REG_MODE_SWITCH, &reg_value)) {
    return;
  }

  if ((reg_value == FT3169_REG_WORKMODE_FACTORY_VALUE) ||
      (reg_value == FT3169_REG_WORKMODE_SCAN_VALUE)) {
    return;
  }

  /* Check ESD_HOLD_THRESHOLD every second,
   * it is a heartbeat register that should be incrementing under normal operation.
   * if it doesn't update in 5 seconds, reset the device
   */
  if (flow_cnt == _touch_priv.flow_work_cnt_last) {
    if (++_touch_priv.flow_work_hold_cnt >= FT3169_ESD_HOLD_THRESHOLD) {
      _touch_priv.flow_work_hold_cnt = 0;
      LOGE("FT3169 ESD detected, resetting device");
      (void)_touch_reset_device();
    }
  } else {
    _touch_priv.flow_work_hold_cnt = 0;
  }

  _touch_priv.flow_work_cnt_last = flow_cnt;
}

void touch_process_esd_check(void) {
  // Check if at least 1 second has passed since the last ESD check
  uint32_t current_time_ms = rtos_thread_systime();

  if ((current_time_ms - _touch_priv.last_esd_check_ms) >= FT3169_ESD_CHECK_PERIOD_MS) {
    _touch_priv.last_esd_check_ms = current_time_ms;
    _touch_check_esd();
  }
}
