#include "arithmetic.h"
#include "bio_platform_config.h"
#include "board_id.h"
#include "button.h"
#include "coproc.h"
#include "hal_nfc.h"
#include "power.h"
#include "serial.h"

#define PORT_A (0)
#define PORT_B (1)
#define PORT_C (2)
#define PORT_D (3)

// FreeRTOS heap
uint8_t ucHeap[configTOTAL_HEAP_SIZE];

serial_config_t serial_config = {
  .usart =
    {
      .tx = {.port = PORT_D, .pin = 4},
      .rx = {.port = PORT_D, .pin = 5},
      .clock = MCU_USART0_CLOCK,
      .baudrate = 115200,
      .usart = {.base = MCU_USART0, .index = 0},
      .rx_irq_timeout = true,
    },
  .retarget_printf =
    {
      .enable = true,
      .target = MCU_USART0,
    },
};

const button_config_t button_configs[HAL_BUTTON_COUNT] = {
  [HAL_BUTTON_LEFT] =
    {
      .gpio = {.port = PORT_B, .pin = 4, .mode = MCU_GPIO_MODE_INPUT_PULL},
      .trigger = EXTI_TRIGGER_BOTH,
    },
  [HAL_BUTTON_RIGHT] =
    {
      .gpio = {.port = PORT_A, .pin = 6, .mode = MCU_GPIO_MODE_INPUT_PULL},
      .trigger = EXTI_TRIGGER_BOTH,
    },
};

mcu_usart_config_t comms_usart_config = {
  .tx = {.port = PORT_A, .pin = 3},
  .rx = {.port = PORT_A, .pin = 5},
  .clock = cmuClock_EUSART0,
  .baudrate = 2000000,
  .usart = {.base = (void*)EUSART0, .index = 0},
  .rx_irq_timeout = true,
};

mcu_i2c_bus_config_t nfc_i2c_bus_config = {
  .peripheral = MCU_I2C1,
  .sda = {.port = PORT_D, .pin = 0},
  .scl = {.port = PORT_D, .pin = 1},
};

mcu_i2c_device_t nfc_i2c_device_config = {
  .addr = (0x50 << 1),
  .peripheral = MCU_I2C1,
  .freq = MCU_I2C_FREQ_400K,
};

nfc_config_t nfc_config = {
  .i2c =
    {
      .bus = &nfc_i2c_bus_config,
      .device = &nfc_i2c_device_config,
    },
  .irq = {.port = PORT_A, .pin = 0},
  .transfer_timeout_ms = 5000,
};

bio_config_t fpc_config = {
  .spi_config =
    {
      .port = (void*)EUSART1,
      .mosi = {.port = PORT_C, .pin = 4},
      .miso = {.port = PORT_C, .pin = 5},
      .clk = {.port = PORT_C, .pin = 6},
      .cs = {.port = PORT_C, .pin = 7, .mode = MCU_GPIO_MODE_PUSH_PULL},
      .master = true,
      .frame_len = 8U,
      .bit_order = MCU_SPI_BIT_ORDER_MSB_FIRST,
      .clock_mode = MCU_SPI_CLOCK_ORDER_MODE0,
      .auto_cs = false,
      .bitrate = 3000000,
    },
  .rst =
    {
      .port = PORT_C,
      .pin = 8,
      .mode = MCU_GPIO_MODE_OPEN_DRAIN,
    },
  .exti =
    {
      .gpio =
        {
          .port = PORT_B,
          .pin = 3,
          .mode = MCU_GPIO_MODE_INPUT,
        },
      .trigger = EXTI_TRIGGER_RISING,
    },
};

board_id_config_t board_id_config = {
  .board_id0 =
    {
      .port = PORT_C,
      .pin = 9,
      .mode = MCU_GPIO_MODE_INPUT,
    },
  .board_id1 =
    {
      .port = PORT_C,
      .pin = 3,
      .mode = MCU_GPIO_MODE_INPUT,
    },
};

mcu_i2c_bus_config_t power_i2c_config = {
  .peripheral = MCU_I2C0,
  .sda = {.port = PORT_B, .pin = 2},
  .scl = {.port = PORT_B, .pin = 0},
};

mcu_i2c_device_t max77734_i2c_config = {
  .addr = (0x48 << 1),
  .peripheral = MCU_I2C0,
  .freq = MCU_I2C_FREQ_400K,
};

mcu_i2c_device_t max17262_i2c_config = {
  .addr = (0x36 << 1),
  .peripheral = MCU_I2C0,
  .freq = MCU_I2C_FREQ_400K,
};

power_config_t power_config = {
  .power_retain =
    {
      // NOTE: power_retain is also used by bootloader, but duplicated in loader/main.c
      // because the BL does not depend on platform config or the power driver.
      .port = PORT_B,
      .pin = 5,
      .mode = MCU_GPIO_MODE_PUSH_PULL,
    },
  .five_volt_boost =
    {
      .port = PORT_D,
      .pin = 2,
      .mode = MCU_GPIO_MODE_PUSH_PULL,
    },
  .cap_touch_detect =
    {
      .gpio =
        {
          .port = PORT_B,
          .pin = 1,
          .mode = MCU_GPIO_MODE_INPUT_PULL,
        },
      .trigger = EXTI_TRIGGER_FALLING,
    },
  .cap_touch_cal =
    {
      .gpio =
        {
          .port = PORT_D,
          .pin = 3,
          .mode = MCU_GPIO_MODE_PUSH_PULL,
        },
      .hold_ms = 22000,
    },
  .charger_irq =
    {
      .gpio =
        {
          .port = PORT_C,
          .pin = 0,
          .mode = MCU_GPIO_MODE_INPUT,
        },
      .trigger = EXTI_TRIGGER_FALLING,
    },
  .usb_detect_irq =
    {
      .gpio =
        {
          .port = PORT_A,
          .pin = 7,
          .mode = MCU_GPIO_MODE_INPUT_PULL,
        },
      .trigger = EXTI_TRIGGER_FALLING,
    },
  .fuel_gauge_irq =
    {
      .gpio =
        {
          .port = PORT_A,
          .pin = 8,  // MAX17262 ALRT pin
          .mode = MCU_GPIO_MODE_INPUT,
        },
      .trigger = EXTI_TRIGGER_FALLING,  // ALRT is active low
    },
  .ldo =
    {
      .ldo_en = 0b01,  // Forced LDO ON
      .ldo_pm = 0b01,  // Normal mode (safe for inrush current at boot)
    },
};

coproc_cfg_t uxc_coproc_cfg = {.power = {
                                 .en =
                                   {
                                     .gpio =
                                       &(mcu_gpio_config_t){
                                         .port = PORT_C,
                                         .pin = 1u,
                                         .mode = MCU_GPIO_MODE_PUSH_PULL,
                                       },
                                   },
                                 .reset =
                                   {
                                     .gpio =
                                       &(mcu_gpio_config_t){
                                         .port = PORT_C,
                                         .pin = 2u,
                                         .mode = MCU_GPIO_MODE_PUSH_PULL,
                                       },
                                   },
                                 .boot = {.gpio =
                                            &(mcu_gpio_config_t){
                                              .port = PORT_A,
                                              .pin = 4u,
                                              .mode = MCU_GPIO_MODE_INPUT,
                                            }},
                                 .power_on_timeout_ms = 1500u,
                                 .power_off_duration_ms = 100u,
                               }};
