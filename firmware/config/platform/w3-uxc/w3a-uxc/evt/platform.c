// W3A-UXC-EVT platform configuration for STM32U5
#include "FreeRTOS.h"
#include "display_driver.h"
#include "gfx.h"
#include "mcu_gpio.h"
#include "mcu_qspi.h"
#include "mcu_usart.h"
#include "serial.h"
#include "stm32u5xx.h"
#include "touch.h"

#include <stdint.h>

// FreeRTOS heap
uint8_t ucHeap[configTOTAL_HEAP_SIZE];

// Display QSPI configuration for W3A-UXC-EVT
// Using OCTOSPI1 with Quad SPI mode
// Pin mappings:
// - PA2:  OCTOSPIM_P1_NCS
// - PB0:  OCTOSPIM_P1_IO1
// - PB10: OCTOSPIM_P1_CLK
// - PE12: OCTOSPIM_P1_IO0
// - PE14: OCTOSPIM_P1_IO2
// - PE15: OCTOSPIM_P1_IO3
mcu_qspi_config_t display_qspi_config = {.port = OCTOSPI1,
                                         .clk =
                                           {
                                             .port = GPIOA,
                                             .pin = 3,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_10,  // AF10 for OCTOSPIM_P1_CLK
                                             .speed = MCU_GPIO_SPEED_VERY_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE  //
                                           },
                                         .cs =
                                           {
                                             .port = GPIOA,
                                             .pin = 2,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_10,  // AF10 for OCTOSPIM_P1_NCS
                                             .speed = MCU_GPIO_SPEED_VERY_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE  //
                                           },
                                         .io0 =
                                           {
                                             .port = GPIOB,
                                             .pin = 1,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_10,  // AF10 for OCTOSPIM_P1_IO0
                                             .speed = MCU_GPIO_SPEED_VERY_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE  //
                                           },
                                         .io1 =
                                           {
                                             .port = GPIOB,
                                             .pin = 0,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_10,  // AF10 for OCTOSPIM_P1_IO1
                                             .speed = MCU_GPIO_SPEED_VERY_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE  //
                                           },
                                         .io2 =
                                           {
                                             .port = GPIOA,
                                             .pin = 7,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_10,  // AF10 for OCTOSPIM_P1_IO2
                                             .speed = MCU_GPIO_SPEED_VERY_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE  //
                                           },
                                         .io3 =
                                           {
                                             .port = GPIOA,
                                             .pin = 6,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_10,  // AF10 for OCTOSPIM_P1_IO3
                                             .speed = MCU_GPIO_SPEED_VERY_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE  //
                                           },
                                         .fifo_threshold = 8,  // FIFO threshold
                                         .cs_high_time = 1,    // 1 cycle CS high time
                                         .mode = MCU_QSPI_MODE_QUAD,
                                         .sample_shifting = false};

display_config_t display_config = {.gfx_config =
                                     {
                                       .display_qspi_config = &display_qspi_config,
                                       .rst = {.port = GPIOC,
                                               .pin = 5,
                                               .mode = MCU_GPIO_MODE_OUTPUT,
                                               .af = 0,
                                               .speed = MCU_GPIO_SPEED_LOW,
                                               .pupd = MCU_GPIO_PUPD_NONE},
                                       .te = {.port = GPIOC,
                                              .pin = 9,
                                              .mode = MCU_GPIO_MODE_INPUT,
                                              .af = 0,
                                              .speed = MCU_GPIO_SPEED_LOW,
                                              .pupd = MCU_GPIO_PULL_UP},
                                       .display_width = 466,
                                       .display_height = 466,
#ifndef DISPLAY_ROTATE_180
                                       .rotate_180 = false,
#else
                                       .rotate_180 = (bool)DISPLAY_ROTATE_180,
#endif
                                     },
                                   .pwr_on_delay = 100u,     // 100 ms
                                   .pwr_off_delay = 100u,    // 100 ms
                                   .update_period_ms = 16u,  // ~60Hz
                                   .pwr = {
                                     .pwr_1v8_en =
                                       &(mcu_gpio_config_t){
                                         .port = GPIOC,
                                         .pin = 6,
                                         .mode = MCU_GPIO_MODE_OUTPUT,
                                       },
                                     .pwr_3v3_en = NULL,
                                     .pwr_vbat_en =
                                       &(mcu_gpio_config_t){
                                         .port = GPIOC,
                                         .pin = 7,
                                         .mode = MCU_GPIO_MODE_OUTPUT,
                                       },
                                     .pwr_avdd_en =
                                       &(mcu_gpio_config_t){
                                         .port = GPIOC,
                                         .pin = 8,
                                         .mode = MCU_GPIO_MODE_OUTPUT,
                                       },
                                   }};

// UART configuration for debug output
// Using USART1 on PA9 (TX) and PA10 (RX)
serial_config_t serial_config = {.usart = {.tx =
                                             {
                                               .port = GPIOA,
                                               .pin = 9,
                                               .mode = MCU_GPIO_MODE_ALTERNATE,
                                               .af = MCU_GPIO_AF_MODE_7,  // AF7 for USART1_TX
                                               .speed = MCU_GPIO_SPEED_HIGH,
                                               .pupd = MCU_GPIO_PUPD_NONE  //
                                             },
                                           .rx =
                                             {
                                               .port = GPIOA,
                                               .pin = 10,
                                               .mode = MCU_GPIO_MODE_ALTERNATE,
                                               .af = MCU_GPIO_AF_MODE_7,  // AF7 for USART1_RX
                                               .speed = MCU_GPIO_SPEED_HIGH,
                                               .pupd = MCU_GPIO_PUPD_NONE  //
                                             },
                                           .clock = 0,  // Not used on STM32U5
                                           .baudrate = 115200,
                                           .usart = {.base = USART1, .index = 0},
                                           .rx_irq_timeout = false},
                                 .retarget_printf = {
                                   .enable = true,
                                   .target = USART1  //
                                 }};

// UART configuration for comms task (to companion MCU).
// Using USART3 on PC10 (TX) and PC11 (RX)
mcu_usart_config_t comms_usart_config = {.tx =
                                           {
                                             .port = GPIOC,
                                             .pin = 10,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_7,
                                             .speed = MCU_GPIO_SPEED_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE,
                                             .low_voltage = true,
                                           },
                                         .rx =
                                           {
                                             .port = GPIOC,
                                             .pin = 11,
                                             .mode = MCU_GPIO_MODE_ALTERNATE,
                                             .af = MCU_GPIO_AF_MODE_7,
                                             .speed = MCU_GPIO_SPEED_HIGH,
                                             .pupd = MCU_GPIO_PUPD_NONE,
                                             .low_voltage = true,
                                           },
                                         .clock = 0,
                                         .baudrate = 2000000,
                                         .usart =
                                           {
                                             .base = USART3,
                                             .index = 0,
                                           },
                                         .rx_irq_timeout = false};

// Boot status GPIO
const mcu_gpio_config_t boot_status_config = {
  .port = GPIOE,
  .pin = 8u,
  .mode = MCU_GPIO_MODE_OUTPUT,
  .af = MCU_GPIO_AF_MODE_0,
  .speed = MCU_GPIO_SPEED_MEDIUM,
  .pupd = MCU_GPIO_PUPD_NONE,
};

const touch_config_t touch_config = {.interface = {.i2c =
                                                     {
                                                       .config =
                                                         {
                                                           .peripheral = MCU_I2C1,
                                                           .sda =
                                                             {
                                                               .port = GPIOB,
                                                               .pin = 7u,
                                                               .mode = MCU_GPIO_MODE_ALTERNATE,
                                                               .af = MCU_GPIO_AF_MODE_4,
                                                               .speed = MCU_GPIO_SPEED_LOW,
                                                               .pupd = MCU_GPIO_PUPD_NONE,
                                                               .low_voltage = true,
                                                             },
                                                           .scl =
                                                             {
                                                               .port = GPIOB,
                                                               .pin = 6u,
                                                               .mode = MCU_GPIO_MODE_ALTERNATE,
                                                               .af = MCU_GPIO_AF_MODE_4,
                                                               .speed = MCU_GPIO_SPEED_LOW,
                                                               .pupd = MCU_GPIO_PUPD_NONE,
                                                               .low_voltage = true,
                                                             },
                                                         },
                                                       .device =
                                                         {
                                                           .addr = 0x38,
                                                           .peripheral = MCU_I2C1,
                                                           .freq = MCU_I2C_FREQ_100K,
                                                         },
                                                     }},
                                     .interface_type = TOUCH_INTERFACE_I2C,
                                     .gpio = {
                                       .pwr =
                                         {
                                           .pwr_1v8_en =
                                             &(mcu_gpio_config_t){
                                               .port = GPIOD,
                                               .pin = 14,
                                               .mode = MCU_GPIO_MODE_OUTPUT,
                                             },
                                           .pwr_avdd_en =
                                             &(mcu_gpio_config_t){
                                               .port = GPIOD,
                                               .pin = 15,
                                               .mode = MCU_GPIO_MODE_OUTPUT,
                                             },
                                         },
                                       .interrupt =
                                         &(mcu_gpio_config_t){
                                           .port = GPIOB,
                                           .pin = 8u,
                                           .mode = MCU_GPIO_MODE_INPUT,
                                           .af = MCU_GPIO_AF_MODE_0,
                                           .speed = MCU_GPIO_SPEED_LOW,
                                           .pupd = MCU_GPIO_PULL_UP,
                                         },
                                       .reset =
                                         &(mcu_gpio_config_t){
                                           .port = GPIOD,
                                           .pin = 5u,
                                           .mode = MCU_GPIO_MODE_OUTPUT,
                                           .af = MCU_GPIO_AF_MODE_0,
                                           .speed = MCU_GPIO_SPEED_MEDIUM,
                                           .pupd = MCU_GPIO_PUPD_NONE,
                                         },
                                     }};
