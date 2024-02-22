#pragma once

#include "mcu.h"
#include "mcu_gpio.h"
#include "perf.h"
#include "rtos.h"

typedef enum {
  MCU_SPI_BIT_ORDER_LSB_FIRST,
  MCU_SPI_BIT_ORDER_MSB_FIRST,
} mcu_spi_bit_order_e;

typedef enum {
  MCU_SPI_CLOCK_ORDER_MODE0,  // CLKPOL=0, CLKPHA=0
  MCU_SPI_CLOCK_ORDER_MODE1,  // CLKPOL=0, CLKPHA=1
  MCU_SPI_CLOCK_ORDER_MODE2,  // CLKPOL=1, CLKPHA=0
  MCU_SPI_CLOCK_ORDER_MODE3,  // CLKPOL=1, CLKPHA=1
} mcu_spi_clock_mode_e;

typedef struct {
  void* port;
  mcu_gpio_config_t miso;
  mcu_gpio_config_t mosi;
  mcu_gpio_config_t clk;
  mcu_gpio_config_t cs;
  uint32_t bitrate;
  uint32_t frame_len;
  uint32_t dummy_tx_value;
  mcu_spi_bit_order_e bit_order;
  mcu_spi_clock_mode_e clock_mode;
  bool auto_cs;
  bool master;
} mcu_spi_config_t;

typedef enum {
  MCU_SPI_PERIPHERAL_EUSART,
  MCU_SPI_PERIPHERAL_USART,
} mcu_spi_peripheral_type_t;

struct mcu_spi_state;
typedef void (*mcu_spi_callback_t)(struct mcu_spi_state* state, mcu_err_t transfer_status,
                                   uint32_t items_transferred);

typedef struct mcu_spi_state {
  uint32_t usart_clock;
  uint32_t tx_dma_signal;
  uint32_t rx_dma_signal;
  union {
    void* eusart_port;
    void* usart_port;
  } peripheral;
  mcu_spi_peripheral_type_t peripheral_type;
  mcu_spi_config_t* config;
  uint32_t tx_dma_channel;
  uint32_t rx_dma_channel;
  mcu_err_t transfer_status;
  volatile enum { MCU_SPI_STATE_IDLE = 0, MCU_SPI_STATE_TRANSFER = 1 } state;
  uint32_t transfer_count;
  mcu_spi_callback_t callback;
  uint32_t remaining;

  /* RTOS */
  rtos_mutex_t access;
  rtos_semaphore_t cb_complete;
} mcu_spi_state_t;

mcu_err_t mcu_spi_init(mcu_spi_state_t* state, mcu_spi_config_t* config);
mcu_err_t mcu_spi_master_transfer_b(mcu_spi_state_t* state, const void* tx_buffer, void* rx_buffer,
                                    uint32_t count);
