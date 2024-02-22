#pragma once

#include "mcu.h"
#include "mcu_i2c.h"
#include "rtos.h"

typedef struct {
  struct {
    mcu_i2c_bus_config_t* bus;
    mcu_i2c_device_t* device;
  } i2c;
  mcu_gpio_config_t irq;
  uint32_t transfer_timeout_ms;  // How long to block on an I2C send or recv
} nfc_config_t;

#define HAL_NFC_MAX_TIMERS (10U)

void hal_nfc_init(rtos_timer_callback_t timer_callback);

// Reads interrupt registers in ST25 over I2C.
void hal_nfc_wfi(void);
void hal_nfc_handle_interrupts(void);

typedef bool (*hal_nfc_callback)(uint8_t* /* rx */, uint32_t /* rx_len */, uint8_t* /* tx */,
                                 uint32_t* /* tx_len */);
void hal_nfc_worker(hal_nfc_callback callback);
