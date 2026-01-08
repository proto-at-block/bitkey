#include "mcu_spi.h"

#include "mcu_dma.h"
#include "mcu_dma_impl.h"

#include "em_cmu.h"
#include "em_core.h"
#include "em_eusart.h"
#include "em_ldma.h"

#include <string.h>

/* Performance counters */
static struct {
  perf_counter_t* transfers;
  perf_counter_t* errors;
} perf SHARED_TASK_DATA;

static const uint32_t fifo_clear_timeout_ms = 10;

static mcu_err_t mcu_spi_init_eusart(mcu_spi_state_t* state, mcu_spi_config_t* config);
static mcu_err_t mcu_spi_init_usart(mcu_spi_state_t* state, mcu_spi_config_t* config);
static void mcu_spi_gpio_init(mcu_spi_state_t* handle, bool enable);
static mcu_err_t transfer_api_blocking_prologue(mcu_spi_state_t* state, void* buffer,
                                                uint32_t count);
static void start_dma_transfer(mcu_spi_state_t* state, const void* tx_buffer, void* rx_buffer,
                               uint32_t count, mcu_spi_callback_t callback);
static void blocking_complete_cb(mcu_spi_state_t* state, mcu_err_t transfer_status,
                                 uint32_t items_transferred);
static void clear_eusart_fifos(void* eusart);
static bool rx_dma_complete(uint32_t channel, uint32_t sequence_num, void* user_param);

mcu_err_t mcu_spi_init(mcu_spi_state_t* state, mcu_spi_config_t* config) {
  perf.transfers = perf_create(PERF_ELAPSED, spi_transfers);
  perf.errors = perf_create(PERF_COUNT, spi_errors);

  if (EUSART_NUM((EUSART_TypeDef*)config->port) != -1) {
    return mcu_spi_init_eusart(state, config);
  }

  return mcu_spi_init_usart(state, config);
}

mcu_err_t mcu_spi_master_transfer_b(mcu_spi_state_t* state, const void* tx_buffer, void* rx_buffer,
                                    uint32_t count) {
  return mcu_spi_master_transfer_async(state, tx_buffer, rx_buffer, count, NULL);
}

mcu_err_t mcu_spi_master_transfer_async(mcu_spi_state_t* state, const void* tx_buffer,
                                        void* rx_buffer, uint32_t count,
                                        mcu_spi_callback_t callback) {
  mcu_err_t result;

  perf_begin(perf.transfers);

  if (state->config->master == false) {
    perf_cancel(perf.transfers);
    perf_count(perf.errors);
    return MCU_ERROR_SPI_MODE;
  }

  rtos_mutex_lock(&state->access);

  if ((result = transfer_api_blocking_prologue(state, (void*)tx_buffer, count)) != MCU_ERROR_OK) {
    rtos_mutex_unlock(&state->access);
    perf_cancel(perf.transfers);
    perf_count(perf.errors);
    return result;
  }

  if (rx_buffer == NULL) {
    rtos_mutex_unlock(&state->access);
    perf_cancel(perf.transfers);
    perf_count(perf.errors);
    return MCU_ERROR_PARAMETER;
  }

  /* choose which callback to hand off to DMA */
  mcu_spi_callback_t cb = callback ? callback : blocking_complete_cb;

  start_dma_transfer(state, tx_buffer, rx_buffer, count, cb);

  if (callback) {
    /* non-blocking: return immediately */
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_OK;
  }

  /* blocking: wait for our semaphore to be given by blocking_complete_cb */
  rtos_semaphore_take(&state->cb_complete, RTOS_SEMAPHORE_TIMEOUT_MAX);
  rtos_mutex_unlock(&state->access);

  if (state->transfer_status != MCU_ERROR_OK) {
    perf_count(perf.errors);
  }

  perf_end(perf.transfers);

  return state->transfer_status;
}

static mcu_err_t mcu_spi_init_eusart(mcu_spi_state_t* state, mcu_spi_config_t* config) {
  EUSART_SpiAdvancedInit_TypeDef eusartAdvancedSpiInit = EUSART_SPI_ADVANCED_INIT_DEFAULT;
  EUSART_SpiInit_TypeDef eusartSpiInit = EUSART_SPI_MASTER_INIT_DEFAULT_HF;
  int8_t spiPortNum = -1;

  eusartSpiInit.advancedSettings = &eusartAdvancedSpiInit;

  if (state == NULL) {
    return MCU_ERROR_ILLEGAL_HANDLE;
  }

  if (config == NULL) {
    return MCU_ERROR_PARAMETER;
  }

  memset(state, 0, sizeof(mcu_spi_state_t));

  if (config->port == EUSART0) {
    state->usart_clock = cmuClock_EUSART0;
    state->tx_dma_signal = MCU_DMA_SIGNAL_EUSART0_TXBL;
    state->rx_dma_signal = MCU_DMA_SIGNAL_EUSART0_RXDATAV;
    spiPortNum = 0;
  } else if (config->port == EUSART1) {
    state->usart_clock = cmuClock_EUSART1;
    state->tx_dma_signal = MCU_DMA_SIGNAL_EUSART1_TXBL;
    state->rx_dma_signal = MCU_DMA_SIGNAL_EUSART1_RXDATAV;
    spiPortNum = 1;
  } else {
    return MCU_ERROR_PARAMETER;
  }

  state->peripheral.eusart_port = config->port;
  state->peripheral_type = MCU_SPI_PERIPHERAL_EUSART;
  state->config = config;

  if (config->bit_order == MCU_SPI_BIT_ORDER_MSB_FIRST) {
    eusartAdvancedSpiInit.msbFirst = true;
  }

  if (config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE0) {
    eusartSpiInit.clockMode = eusartClockMode0;
  } else if (config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE1) {
    eusartSpiInit.clockMode = eusartClockMode1;
  } else if (config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE2) {
    eusartSpiInit.clockMode = eusartClockMode2;
  } else if (config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE3) {
    eusartSpiInit.clockMode = eusartClockMode3;
  } else {
    return MCU_ERROR_PARAMETER;
  }

  if (config->master) {
    eusartSpiInit.bitRate = config->bitrate;
  } else {
    if (config->bitrate >= 5000000) {
      // If baud-rate is more than 5MHz, a value of 4 is required
      eusartSpiInit.advancedSettings->setupWindow = 4;
    } else {
      // If baud-rate is less than 5MHz, a value of 5 is required
      eusartSpiInit.advancedSettings->setupWindow = 5;
    }
    eusartSpiInit.master = false;
    eusartSpiInit.bitRate = 1000000;
  }

  CMU_ClockEnable(cmuClock_GPIO, true);
  CMU_ClockEnable(state->usart_clock, true);

  if ((config->frame_len < 7U) || (config->frame_len > 16U)) {
    return MCU_ERROR_PARAMETER;
  }

  const uint32_t databits = config->frame_len - 7U + EUSART_FRAMECFG_DATABITS_SEVEN;
  eusartSpiInit.databits = (EUSART_Databits_TypeDef)databits;

  if ((config->master) && config->auto_cs) {
    eusartAdvancedSpiInit.autoCsEnable = true;
  }

  EUSART_SpiInit(config->port, &eusartSpiInit);

  // SPI 4 wire mode
  if (config->auto_cs) {
    GPIO->EUSARTROUTE[spiPortNum].ROUTEEN = GPIO_EUSART_ROUTEEN_TXPEN | GPIO_EUSART_ROUTEEN_RXPEN |
                                            GPIO_EUSART_ROUTEEN_SCLKPEN | GPIO_EUSART_ROUTEEN_CSPEN;
  } else {
    GPIO->EUSARTROUTE[spiPortNum].ROUTEEN =
      GPIO_EUSART_ROUTEEN_TXPEN | GPIO_EUSART_ROUTEEN_RXPEN | GPIO_EUSART_ROUTEEN_SCLKPEN;
  }

  GPIO->EUSARTROUTE[spiPortNum].TXROUTE =
    ((uint32_t)config->mosi.port << _GPIO_EUSART_TXROUTE_PORT_SHIFT) |
    ((uint32_t)config->mosi.pin << _GPIO_EUSART_TXROUTE_PIN_SHIFT);
  GPIO->EUSARTROUTE[spiPortNum].RXROUTE =
    ((uint32_t)config->miso.port << _GPIO_EUSART_RXROUTE_PORT_SHIFT) |
    ((uint32_t)config->miso.pin << _GPIO_EUSART_RXROUTE_PIN_SHIFT);
  GPIO->EUSARTROUTE[spiPortNum].SCLKROUTE =
    ((uint32_t)config->clk.port << _GPIO_EUSART_SCLKROUTE_PORT_SHIFT) |
    ((uint32_t)config->clk.pin << _GPIO_EUSART_SCLKROUTE_PIN_SHIFT);

  if (config->auto_cs) {
    // SPI 4 wire mode, Chip Select controlled by the peripheral
    GPIO->EUSARTROUTE[spiPortNum].CSROUTE =
      ((uint32_t)config->cs.port << _GPIO_EUSART_CSROUTE_PORT_SHIFT) |
      ((uint32_t)config->cs.pin << _GPIO_EUSART_CSROUTE_PIN_SHIFT);
  }

  mcu_spi_gpio_init(state, true);

  mcu_err_t err = mcu_dma_init(MCU_DMA_IRQ_PRIORITY);
  if ((err != MCU_ERROR_OK) && (err != MCU_ERROR_ALREADY_INITIALISED)) {
    return err;
  }

  // Allocate RX channel first to get lower channel number (higher priority).
  if (mcu_dma_allocate_channel(&state->rx_dma_channel) != MCU_ERROR_OK) {
    return MCU_ERROR_DMA_ALLOC;
  }
  if (mcu_dma_allocate_channel(&state->tx_dma_channel) != MCU_ERROR_OK) {
    return MCU_ERROR_DMA_ALLOC;
  }

  rtos_mutex_create(&state->access);
  rtos_semaphore_create(&state->cb_complete);

  return MCU_ERROR_OK;
}

static mcu_err_t mcu_spi_init_usart(mcu_spi_state_t* state, mcu_spi_config_t* config) {
  (void)state;
  (void)config;
  return MCU_ERROR_NOT_IMPLEMENTED;
}

static void mcu_spi_gpio_init(mcu_spi_state_t* state, bool enable) {
  if (enable) {
    if (state->config->master) {
      mcu_gpio_set_mode(&state->config->mosi, MCU_GPIO_MODE_PUSH_PULL, false);
      mcu_gpio_set_mode(&state->config->miso, MCU_GPIO_MODE_INPUT, false);

      if ((state->config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE0) ||
          (state->config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE1)) {
        mcu_gpio_set_mode(&state->config->clk, MCU_GPIO_MODE_PUSH_PULL, false);
      } else {
        mcu_gpio_set_mode(&state->config->clk, MCU_GPIO_MODE_PUSH_PULL, true);
      }

      if (state->config->auto_cs) {
        mcu_gpio_set_mode(&state->config->cs, MCU_GPIO_MODE_PUSH_PULL, true);
      }
    } else {
      mcu_gpio_set_mode(&state->config->mosi, MCU_GPIO_MODE_INPUT, false);
      mcu_gpio_set_mode(&state->config->miso, MCU_GPIO_MODE_PUSH_PULL, false);

      if ((state->config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE0) ||
          (state->config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE1)) {
        mcu_gpio_set_mode(&state->config->clk, MCU_GPIO_MODE_INPUT_PULL, false);
      } else {
        mcu_gpio_set_mode(&state->config->clk, MCU_GPIO_MODE_INPUT_PULL, true);
      }

      if (state->config->auto_cs) {
        mcu_gpio_set_mode(&state->config->cs, MCU_GPIO_MODE_INPUT_PULL, true);
      }
    }
  } else {
    mcu_gpio_set_mode(&state->config->mosi, MCU_GPIO_MODE_INPUT_PULL, false);
    mcu_gpio_set_mode(&state->config->miso, MCU_GPIO_MODE_INPUT_PULL, false);

    if ((state->config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE0) ||
        (state->config->clock_mode == MCU_SPI_CLOCK_ORDER_MODE1)) {
      mcu_gpio_set_mode(&state->config->clk, MCU_GPIO_MODE_INPUT_PULL, false);
    } else {
      mcu_gpio_set_mode(&state->config->clk, MCU_GPIO_MODE_INPUT_PULL, true);
    }

    if (state->config->auto_cs) {
      mcu_gpio_set_mode(&state->config->cs, MCU_GPIO_MODE_DISABLED, false);
    }
  }
}

static mcu_err_t transfer_api_blocking_prologue(mcu_spi_state_t* state, void* buffer,
                                                uint32_t count) {
  if (state == NULL) {
    return MCU_ERROR_ILLEGAL_HANDLE;
  }

  if ((buffer == NULL) || (count == 0) || (count > MCU_DMA_MAX_XFER_COUNT)) {
    return MCU_ERROR_PARAMETER;
  }

  rtos_thread_enter_critical();

  if (state->state != MCU_SPI_STATE_IDLE) {
    rtos_thread_exit_critical();
    return MCU_ERROR_SPI_BUSY;
  }

  state->state = MCU_SPI_STATE_TRANSFER;
  rtos_thread_exit_critical();

  return MCU_ERROR_OK;
}

static void start_dma_transfer(mcu_spi_state_t* state, const void* tx_buffer, void* rx_buffer,
                               uint32_t count, mcu_spi_callback_t callback) {
  void *rx_port, *tx_port;
  rx_port = tx_port = 0;
  mcu_dma_data_size_t size;

  state->transfer_count = count;
  state->callback = callback;

  if (state->peripheral_type == MCU_SPI_PERIPHERAL_USART) {
    // Not implemented
    return;
  } else if (state->peripheral_type == MCU_SPI_PERIPHERAL_EUSART) {
    clear_eusart_fifos(state->peripheral.eusart_port);

    EUSART_TypeDef* eusart = (EUSART_TypeDef*)state->peripheral.eusart_port;
    rx_port = (void*)&(eusart->RXDATA);
    tx_port = (void*)&(eusart->TXDATA);
  } else {
    return;
  }

  if (state->config->frame_len > 8) {
    size = MCU_DMA_SIZE_2_BYTES;
  } else {
    size = MCU_DMA_SIZE_1_BYTE;
  }

  // Start receive DMA.
  mcu_dma_peripheral_memory(state->rx_dma_channel, state->rx_dma_signal, rx_buffer, rx_port, true,
                            count, size, rx_dma_complete, state);

  // Start transmit DMA.
  mcu_dma_memory_peripheral(state->tx_dma_channel, state->tx_dma_signal, tx_port, (void*)tx_buffer,
                            true, count, size, NULL, NULL);
}

static void blocking_complete_cb(mcu_spi_state_t* state, mcu_err_t transfer_status,
                                 uint32_t items_transferred) {
  (void)items_transferred;

  state->transfer_status = transfer_status;
  rtos_semaphore_give_from_isr(&state->cb_complete);
}

static void clear_eusart_fifos(void* eusart) {
  EUSART_TypeDef* _eusart = (EUSART_TypeDef*)eusart;
  _eusart->CMD = EUSART_CMD_CLEARTX;

  uint32_t start = rtos_thread_systime();
  while ((_eusart->STATUS & EUSART_STATUS_CLEARTXBUSY) != 0U &&
         !RTOS_DEADLINE(start, fifo_clear_timeout_ms)) {
  }

  // Read data until FIFO is emptied
  // but taking care not to underflow the receiver
  start = rtos_thread_systime();
  while ((_eusart->STATUS & EUSART_STATUS_RXFL) && !RTOS_DEADLINE(start, fifo_clear_timeout_ms)) {
    _eusart->RXDATA;
  }

  // TODO: return an error when deadlines are exceeded
}

static bool rx_dma_complete(uint32_t channel, uint32_t sequence_num, void* user_param) {
  (void)channel;
  (void)sequence_num;

  rtos_thread_enter_critical();

  mcu_spi_state_t* state = (mcu_spi_state_t*)user_param;

  state->transfer_status = MCU_ERROR_OK;
  state->state = MCU_SPI_STATE_IDLE;
  state->remaining = 0;

  if (state->callback != NULL) {
    state->callback(state, MCU_ERROR_OK, state->transfer_count);
  }

  rtos_thread_exit_critical();

  return true;
}
