/* STM32U5 QSPI driver implementation using OCTOSPI peripheral */

#include "mcu_qspi.h"

#include "mcu.h"
#include "mcu_dma.h"
#include "mcu_dma_impl.h"
#include "mcu_gpio.h"
#include "mcu_nvic.h"
#include "mcu_nvic_impl.h"
#include "rtos_thread.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_dma.h"

#include <string.h>

/* Configuration */
#define QSPI_MAX_INSTANCES    2
#define QSPI_TIMEOUT_MS       1000 /* Default timeout for QSPI operations */
#define QSPI_ABORT_TIMEOUT_MS 100  /* Timeout for abort operations */
#define QSPI_BUSY_TIMEOUT_MS  100  /* Timeout for busy flag checks */

/* OCTOSPI functional modes */
#define OSPI_FUNCTIONAL_MODE_INDIRECT_WRITE ((uint32_t)0x00000000)
#define OSPI_FUNCTIONAL_MODE_INDIRECT_READ  ((uint32_t)OCTOSPI_CR_FMODE_0)

/* OCTOSPI line mode values (for CCR register) */
#define OSPI_LINE_MODE_NONE  0
#define OSPI_LINE_MODE_1LINE 1
#define OSPI_LINE_MODE_2LINE 2
#define OSPI_LINE_MODE_4LINE 3

typedef struct {
  OCTOSPI_TypeDef* instance;
  mcu_qspi_state_t* state;
} qspi_handle_t;

static qspi_handle_t qspi_handles[QSPI_MAX_INSTANCES];

static void gpio_init(mcu_qspi_config_t* config);
static mcu_err_t setup_command(OCTOSPI_TypeDef* ospi, mcu_qspi_command_t* cmd);
static mcu_err_t wait_for_flag(OCTOSPI_TypeDef* ospi, uint32_t flag, uint32_t timeout_ms);
static mcu_err_t octospi_transmit(OCTOSPI_TypeDef* ospi, const uint8_t* data, uint32_t len);
static mcu_err_t octospi_receive(OCTOSPI_TypeDef* ospi, uint8_t* data, uint32_t len);
static bool qspi_dma_callback(uint32_t channel, uint32_t flags, void* user_param);
static void qspi_cleanup_state(mcu_qspi_state_t* state, bool clear_dma_fields);
static mcu_err_t octospi_abort(OCTOSPI_TypeDef* ospi, uint32_t timeout_ms);

mcu_err_t mcu_qspi_init(mcu_qspi_state_t* state, mcu_qspi_config_t* config) {
  if (!state || !config) {
    return MCU_ERROR_PARAMETER;
  }

  state->config = config;
  state->state = MCU_QSPI_STATE_IDLE;
  state->transfer_status = MCU_ERROR_OK;
  state->dma_enabled = false;

  /* Initialize RTOS primitives */
  rtos_mutex_create(&state->access);
  rtos_semaphore_create(&state->transfer_complete);

  /* Allocate DMA channel */
  mcu_err_t dma_err = mcu_dma_allocate_channel(&state->dma_channel);
  if (dma_err != MCU_ERROR_OK) {
    rtos_mutex_destroy(&state->access);
    rtos_semaphore_destroy(&state->transfer_complete);
    return dma_err;
  }
  state->dma_enabled = true;

  /* Clock configuration: PLL2Q must be externally setup */

  /* Determine which OCTOSPI instance to use */
  qspi_handle_t* handle = NULL;
  OCTOSPI_TypeDef* ospi = (OCTOSPI_TypeDef*)config->port;

  if (ospi == OCTOSPI1) {
    handle = &qspi_handles[0];
    /* Enable OCTOSPI1 clock */
    RCC->AHB2ENR2 |= RCC_AHB2ENR2_OCTOSPI1EN;
  } else if (ospi == OCTOSPI2) {
    handle = &qspi_handles[1];
    /* Enable OCTOSPI2 clock */
    RCC->AHB2ENR2 |= RCC_AHB2ENR2_OCTOSPI2EN;
  } else {
    mcu_dma_channel_free(state->dma_channel);
    state->dma_enabled = false;
    rtos_mutex_destroy(&state->access);
    rtos_semaphore_destroy(&state->transfer_complete);
    return MCU_ERROR_PARAMETER;
  }

  handle->state = state;
  handle->instance = ospi;
  state->instance = handle;

  /* Configure GPIO pins */
  gpio_init(config);

  /* Reset OCTOSPI peripheral */
  ospi->CR = 0;

  /* Wait for peripheral to be disabled */
  uint32_t tickstart = rtos_thread_systime();
  while (ospi->SR & OCTOSPI_SR_BUSY) {
    if ((rtos_thread_systime() - tickstart) > QSPI_TIMEOUT_MS) {
      mcu_dma_channel_free(state->dma_channel);
      state->dma_enabled = false;
      rtos_mutex_destroy(&state->access);
      rtos_semaphore_destroy(&state->transfer_complete);
      return MCU_ERROR_UNKNOWN;
    }
    rtos_thread_sleep(1);
  }

  /* Configure DCR1 - Device Configuration Register 1 */
  uint32_t dcr1_value = 0;
  dcr1_value |=
    (((config->cs_high_time - 1) & 0x7) << OCTOSPI_DCR1_CSHT_Pos); /* CS high time in cycles */
  dcr1_value |= ((24 - 1) << OCTOSPI_DCR1_DEVSIZE_Pos);            /* Device size: 2^24 = 16MB */
  dcr1_value |= (0x1 << OCTOSPI_DCR1_MTYP_Pos);                    /* Memory type: Micron */
  /* CKMODE bit is 0 for Mode 0 (CPOL=0, CPHA=0) */
  dcr1_value |= OCTOSPI_DCR1_DLYBYP; /* Bypass delay block */
  ospi->DCR1 = dcr1_value;

  /* Configure DCR2 - Device Configuration Register 2 */
  ospi->DCR2 = 0;
  ospi->DCR2 |= (0 << OCTOSPI_DCR2_WRAPSIZE_Pos); /* No wrap mode */
  /* Prescaler will be set after busy wait */

  /* Configure DCR3 - Device Configuration Register 3 */
  ospi->DCR3 = 0;
  ospi->DCR3 |= (0 << OCTOSPI_DCR3_MAXTRAN_Pos); /* No maximum transfer limit */
  ospi->DCR3 |= (0 << OCTOSPI_DCR3_CSBOUND_Pos); /* No chip select boundary */

  /* Configure DCR4 - Device Configuration Register 4 */
  ospi->DCR4 = 0; /* No refresh needed for display */

  /* Configure FIFO threshold in Control Register */
  ospi->CR = 0;
  ospi->CR |=
    (((config->fifo_threshold - 1) & 0x1F) << OCTOSPI_CR_FTHRES_Pos); /* FIFO threshold (N-1) */

  /* Wait for busy flag to clear */
  tickstart = rtos_thread_systime();
  while (ospi->SR & OCTOSPI_SR_BUSY) {
    if ((rtos_thread_systime() - tickstart) > QSPI_TIMEOUT_MS) {
      mcu_dma_channel_free(state->dma_channel);
      state->dma_enabled = false;
      rtos_mutex_destroy(&state->access);
      rtos_semaphore_destroy(&state->transfer_complete);
      return MCU_ERROR_UNKNOWN;
    }
    rtos_thread_sleep(1);
  }

  /* Set clock prescaler (must be done after busy flag clears) */
  ospi->DCR2 |= (0 << OCTOSPI_DCR2_PRESCALER_Pos); /* No prescaling - divide by 1 */

  /* Configure TCR - Timing Configuration Register */
  ospi->TCR = 0;
  if (config->sample_shifting) {
    ospi->TCR |= OCTOSPI_TCR_SSHIFT; /* Delay data sampling by 1/2 cycle */
  }

  /* Enable peripheral */
  ospi->CR |= OCTOSPI_CR_EN;

  /* Wait for enable to take effect */
  while (!(ospi->CR & OCTOSPI_CR_EN)) {
  }

  /* Clear all status flags */
  ospi->FCR = 0xFFFFFFFF;

  /* Enable interrupts */
  IRQn_Type irq = (ospi == OCTOSPI1) ? OCTOSPI1_IRQn : OCTOSPI2_IRQn;
  mcu_nvic_set_priority(irq, MCU_NVIC_DEFAULT_IRQ_PRIORITY);
  mcu_nvic_enable_irq(irq);

  return MCU_ERROR_OK;
}

mcu_err_t mcu_qspi_command(mcu_qspi_state_t* state, mcu_qspi_command_t* cmd, const uint8_t* tx_data,
                           uint8_t* rx_data) {
  if (!state || !cmd) {
    return MCU_ERROR_PARAMETER;
  }

  if (cmd->data_length > 0 && !tx_data && !rx_data) {
    return MCU_ERROR_PARAMETER;
  }

  if (tx_data && rx_data) {
    return MCU_ERROR_PARAMETER;
  }

  rtos_mutex_lock(&state->access);

  if (state->state != MCU_QSPI_STATE_IDLE) {
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_SPI_BUSY;
  }

  qspi_handle_t* handle = (qspi_handle_t*)state->instance;
  OCTOSPI_TypeDef* ospi = handle->instance;

  if (wait_for_flag(ospi, OCTOSPI_SR_BUSY, QSPI_TIMEOUT_MS) != MCU_ERROR_OK) {
    /* Peripheral timed out while busy - attempt abort and reset state */
    mcu_err_t abort_result = octospi_abort(ospi, QSPI_ABORT_TIMEOUT_MS);
    if (abort_result == MCU_ERROR_OK) {
      qspi_cleanup_state(state, false);
    }
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_UNKNOWN;
  }

  /* Setup the command (functional mode is set inside setup_command) */
  mcu_err_t result = setup_command(ospi, cmd);
  if (result != MCU_ERROR_OK) {
    rtos_mutex_unlock(&state->access);
    return result;
  }

  /* Execute data phase if present */
  if (cmd->data_length > 0) {
    if (tx_data) {
      /* Transmit mode - setting FMODE triggers transfer */
      MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, OSPI_FUNCTIONAL_MODE_INDIRECT_WRITE);
      result = octospi_transmit(ospi, tx_data, cmd->data_length);
    } else {
      /* Receive mode - save registers before configuring */
      uint32_t addr_reg = ospi->AR;
      uint32_t ir_reg = ospi->IR;

      /* Set to indirect read mode */
      MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, OSPI_FUNCTIONAL_MODE_INDIRECT_READ);

      /* Trigger transfer by re-writing address or instruction register */
      if ((ospi->CCR & OCTOSPI_CCR_ADMODE) != 0) {
        WRITE_REG(ospi->AR, addr_reg); /* Has address - write AR to trigger */
      } else if ((ospi->CCR & OCTOSPI_CCR_IMODE) != 0) {
        WRITE_REG(ospi->IR, ir_reg); /* No address - write IR to trigger */
      }

      result = octospi_receive(ospi, rx_data, cmd->data_length);
    }
  } else {
    /* Command only - wait for completion */
    result = wait_for_flag(ospi, OCTOSPI_SR_TCF, QSPI_TIMEOUT_MS);
    if (result == MCU_ERROR_OK) {
      ospi->FCR = OCTOSPI_FCR_CTCF; /* Clear transfer complete flag */
    } else {
      /* Abort if transfer is stuck */
      octospi_abort(ospi, QSPI_ABORT_TIMEOUT_MS);
    }
  }

  rtos_mutex_unlock(&state->access);
  return result;
}

mcu_err_t mcu_qspi_command_async(mcu_qspi_state_t* state, mcu_qspi_command_t* cmd,
                                 const uint8_t* tx_data, uint8_t* rx_data,
                                 mcu_qspi_callback_t callback) {
  if (!state || !cmd) {
    return MCU_ERROR_PARAMETER;
  }

  if (cmd->data_length > 0 && !tx_data && !rx_data) {
    return MCU_ERROR_PARAMETER;
  }

  if (tx_data && rx_data) {
    return MCU_ERROR_PARAMETER;
  }

  rtos_mutex_lock(&state->access);
  if (state->state != MCU_QSPI_STATE_IDLE) {
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_SPI_BUSY;
  }

  /* Setup async state */
  state->state = MCU_QSPI_STATE_BUSY;
  state->callback = callback;
  state->transfer_count = cmd->data_length;
  state->transfer_status = MCU_ERROR_OK;
  state->tx_buffer = (uint8_t*)tx_data;
  state->rx_buffer = rx_data;
  state->transfer_index = 0;

  qspi_handle_t* handle = (qspi_handle_t*)state->instance;
  OCTOSPI_TypeDef* ospi = handle->instance;

  if (wait_for_flag(ospi, OCTOSPI_SR_BUSY, QSPI_TIMEOUT_MS) != MCU_ERROR_OK) {
    /* Peripheral timed out while busy - attempt abort and reset state */
    mcu_err_t abort_result = octospi_abort(ospi, QSPI_ABORT_TIMEOUT_MS);
    if (abort_result == MCU_ERROR_OK) {
      qspi_cleanup_state(state, false);
    }
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_UNKNOWN;
  }

  /* Clear interrupt flags */
  ospi->FCR = OCTOSPI_FCR_CTEF | OCTOSPI_FCR_CTCF;

  mcu_err_t result = setup_command(ospi, cmd);
  if (result != MCU_ERROR_OK) {
    qspi_cleanup_state(state, false);
    rtos_mutex_unlock(&state->access);
    return result;
  }

  /* Configure async transfer */
  if (cmd->data_length > 0) {
    if (tx_data) {
      /* TX mode */
      MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, OSPI_FUNCTIONAL_MODE_INDIRECT_WRITE);

      SET_BIT(ospi->CR, OCTOSPI_CR_TCIE | OCTOSPI_CR_TEIE | OCTOSPI_CR_FTIE);
    } else {
      /* RX mode */
      uint32_t addr_reg = ospi->AR;
      uint32_t ir_reg = ospi->IR;

      MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, OSPI_FUNCTIONAL_MODE_INDIRECT_READ);

      SET_BIT(ospi->CR, OCTOSPI_CR_TCIE | OCTOSPI_CR_TEIE | OCTOSPI_CR_FTIE);

      /* Trigger transfer */
      if ((ospi->CCR & OCTOSPI_CCR_ADMODE) != 0) {
        WRITE_REG(ospi->AR, addr_reg);
      } else if ((ospi->CCR & OCTOSPI_CCR_IMODE) != 0) {
        WRITE_REG(ospi->IR, ir_reg);
      }
    }
  } else {
    /* Command only - complete immediately */
    state->state = MCU_QSPI_STATE_IDLE;
    if (callback) {
      rtos_mutex_unlock(&state->access);
      callback(state, MCU_ERROR_OK, 0);
    } else {
      rtos_semaphore_give(&state->transfer_complete);
      rtos_mutex_unlock(&state->access);
    }
    return MCU_ERROR_OK;
  }

  rtos_mutex_unlock(&state->access);

  if (!callback) {
    rtos_semaphore_take(&state->transfer_complete, RTOS_SEMAPHORE_TIMEOUT_MAX);
    return state->transfer_status;
  }

  return MCU_ERROR_OK;
}

mcu_err_t mcu_qspi_command_async_dma(mcu_qspi_state_t* state, mcu_qspi_command_t* cmd,
                                     const uint8_t* tx_data, uint8_t* rx_data,
                                     mcu_qspi_callback_t callback) {
  if (!state || !cmd) {
    return MCU_ERROR_PARAMETER;
  }

  if (cmd->data_length > 0 && !tx_data && !rx_data) {
    return MCU_ERROR_PARAMETER;
  }

  if (tx_data && rx_data) {
    return MCU_ERROR_PARAMETER;
  }

  if (!state->dma_enabled) {
    return MCU_ERROR_PARAMETER;
  }

  rtos_mutex_lock(&state->access);
  if (state->state != MCU_QSPI_STATE_IDLE) {
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_SPI_BUSY;
  }

  /* Setup async state */
  state->state = MCU_QSPI_STATE_BUSY;
  state->callback = callback;
  state->transfer_count = cmd->data_length;
  state->transfer_status = MCU_ERROR_OK;
  state->tx_buffer = (uint8_t*)tx_data;
  state->rx_buffer = rx_data;
  state->transfer_index = 0;

  qspi_handle_t* handle = (qspi_handle_t*)state->instance;
  OCTOSPI_TypeDef* ospi = handle->instance;

  if (wait_for_flag(ospi, OCTOSPI_SR_BUSY, QSPI_TIMEOUT_MS) != MCU_ERROR_OK) {
    /* Peripheral timed out while busy - attempt abort and reset state */
    mcu_err_t abort_result = octospi_abort(ospi, QSPI_ABORT_TIMEOUT_MS);
    if (abort_result == MCU_ERROR_OK) {
      qspi_cleanup_state(state, true);
    }
    rtos_mutex_unlock(&state->access);
    return MCU_ERROR_UNKNOWN;
  }

  /* Clear interrupt flags */
  ospi->FCR = OCTOSPI_FCR_CTEF | OCTOSPI_FCR_CTCF;

  mcu_err_t result = setup_command(ospi, cmd);
  if (result != MCU_ERROR_OK) {
    qspi_cleanup_state(state, true);
    rtos_mutex_unlock(&state->access);
    return result;
  }

  /* Configure DMA transfer if data phase exists */
  if (cmd->data_length > 0) {
    /* Setup transfer chaining state for large transfers */
    if (cmd->data_length > MCU_DMA_MAX_XFER_COUNT) {
      state->total_transfer_size = cmd->data_length;
      state->bytes_transferred = 0;
      /* Calculate number of remaining chunks after the current one (avoid overflow) */
      state->chunks_remaining = (cmd->data_length - 1) / MCU_DMA_MAX_XFER_COUNT;
      state->current_chunk_size = MCU_DMA_MAX_XFER_COUNT;
    } else {
      /* Single chunk transfer */
      state->total_transfer_size = cmd->data_length;
      state->bytes_transferred = 0;
      state->chunks_remaining = 0;
      state->current_chunk_size = cmd->data_length;
    }

    /* Determine QSPI DMA request signal */
    mcu_dma_signal_t dma_request =
      (ospi == OCTOSPI1) ? MCU_DMA_SIGNAL_OCTOSPI1 : MCU_DMA_SIGNAL_OCTOSPI2;

    /* Configure DMA for first chunk */
    mcu_dma_config_t dma_config = {
      .mode = MCU_DMA_MODE_BASIC,
      .request = dma_request,
      .src_width = MCU_DMA_SIZE_1_BYTE,
      .dst_width = MCU_DMA_SIZE_1_BYTE,
      .priority = MCU_DMA_REQ_PRIORITY_HIGH,
      .callback = qspi_dma_callback,
      .user_param = state,
      .xfer_node = NULL,
    };

    if (tx_data) {
      /* TX mode: Memory to QSPI data register */
      dma_config.direction = MCU_DMA_DIR_M2P;
      dma_config.src_addr = (void*)tx_data;
      dma_config.dst_addr = (void*)&ospi->DR;
      dma_config.src_increment = true;
      dma_config.dst_increment = false;
      dma_config.length = state->current_chunk_size;

      MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, OSPI_FUNCTIONAL_MODE_INDIRECT_WRITE);
    } else {
      /* RX mode: QSPI data register to Memory */
      uint32_t addr_reg = ospi->AR;
      uint32_t ir_reg = ospi->IR;

      dma_config.direction = MCU_DMA_DIR_P2M;
      dma_config.src_addr = (void*)&ospi->DR;
      dma_config.dst_addr = (void*)rx_data;
      dma_config.src_increment = false;
      dma_config.dst_increment = true;
      dma_config.length = state->current_chunk_size;

      MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, OSPI_FUNCTIONAL_MODE_INDIRECT_READ);

      /* Trigger transfer by re-writing address or instruction register */
      if ((ospi->CCR & OCTOSPI_CCR_ADMODE) != 0) {
        WRITE_REG(ospi->AR, addr_reg);
      } else if ((ospi->CCR & OCTOSPI_CCR_IMODE) != 0) {
        WRITE_REG(ospi->IR, ir_reg);
      }
    }

    /* Configure and start DMA */
    result = mcu_dma_channel_configure(state->dma_channel, &dma_config);
    if (result != MCU_ERROR_OK) {
      qspi_cleanup_state(state, true);
      rtos_mutex_unlock(&state->access);
      return result;
    }

    /* Enable OCTOSPI DMA and transfer error interrupt */
    SET_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_TEIE);

    /* Start DMA transfer */
    result = mcu_dma_channel_start(state->dma_channel);
    if (result != MCU_ERROR_OK) {
      /* Stop DMA channel if it was configured but failed to start */
      (void)mcu_dma_channel_stop(state->dma_channel);
      CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_TEIE);
      qspi_cleanup_state(state, true);
      rtos_mutex_unlock(&state->access);
      return result;
    }
  } else {
    /* Command only - complete immediately */
    state->state = MCU_QSPI_STATE_IDLE;
    if (callback) {
      rtos_mutex_unlock(&state->access);
      callback(state, MCU_ERROR_OK, 0);
    } else {
      rtos_semaphore_give(&state->transfer_complete);
      rtos_mutex_unlock(&state->access);
    }
    return MCU_ERROR_OK;
  }

  rtos_mutex_unlock(&state->access);

  if (!callback) {
    rtos_semaphore_take(&state->transfer_complete, RTOS_SEMAPHORE_TIMEOUT_MAX);
    return state->transfer_status;
  }

  return MCU_ERROR_OK;
}

mcu_err_t mcu_qspi_abort(mcu_qspi_state_t* state) {
  if (!state) {
    return MCU_ERROR_PARAMETER;
  }

  qspi_handle_t* handle = (qspi_handle_t*)state->instance;
  OCTOSPI_TypeDef* ospi = handle->instance;

  /* Stop DMA if enabled */
  if (state->dma_enabled) {
    mcu_dma_channel_stop(state->dma_channel);
    CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN);
  }

  mcu_err_t result = octospi_abort(ospi, QSPI_BUSY_TIMEOUT_MS);
  if (result == MCU_ERROR_OK) {
    state->state = MCU_QSPI_STATE_IDLE;
  }

  return result;
}

static void gpio_init(mcu_qspi_config_t* config) {
  mcu_gpio_configure(&config->clk, true);
  mcu_gpio_configure(&config->cs, false);
  mcu_gpio_configure(&config->io0, false);
  mcu_gpio_configure(&config->io1, false);

  /* Initialize IO2 and IO3 for Quad mode */
  if (config->mode == MCU_QSPI_MODE_QUAD) {
    mcu_gpio_configure(&config->io2, false);
    mcu_gpio_configure(&config->io3, false);
  }
}

static void qspi_cleanup_state(mcu_qspi_state_t* state, bool clear_dma_fields) {
  state->state = MCU_QSPI_STATE_IDLE;
  state->tx_buffer = NULL;
  state->rx_buffer = NULL;
  state->transfer_index = 0;
  state->callback = NULL;

  if (clear_dma_fields) {
    state->total_transfer_size = 0;
    state->chunks_remaining = 0;
    state->bytes_transferred = 0;
  }
}

static mcu_err_t octospi_abort(OCTOSPI_TypeDef* ospi, uint32_t timeout_ms) {
  ospi->CR |= OCTOSPI_CR_ABORT;

  uint32_t abort_start = rtos_thread_systime();
  while (ospi->CR & OCTOSPI_CR_ABORT) {
    if ((rtos_thread_systime() - abort_start) > timeout_ms) {
      return MCU_ERROR_UNKNOWN;
    }
    rtos_thread_sleep(1);
  }

  return MCU_ERROR_OK;
}

static uint32_t map_instr_mode(mcu_qspi_mode_e m) {
  switch (m) {
    case MCU_QSPI_MODE_SPI:
      return OSPI_LINE_MODE_1LINE;
    case MCU_QSPI_MODE_DUAL:
      return OSPI_LINE_MODE_2LINE;
    case MCU_QSPI_MODE_QUAD:
      return OSPI_LINE_MODE_4LINE;
    default:
      return OSPI_LINE_MODE_NONE;
  }
}

static uint32_t map_addr_mode(mcu_qspi_mode_e m) {
  switch (m) {
    case MCU_QSPI_MODE_SPI:
      return OSPI_LINE_MODE_1LINE;
    case MCU_QSPI_MODE_DUAL:
      return OSPI_LINE_MODE_2LINE;
    case MCU_QSPI_MODE_QUAD:
      return OSPI_LINE_MODE_4LINE;
    default:
      return OSPI_LINE_MODE_NONE;
  }
}

static uint32_t map_data_mode(mcu_qspi_mode_e m) {
  switch (m) {
    case MCU_QSPI_MODE_SPI:
      return OSPI_LINE_MODE_1LINE;
    case MCU_QSPI_MODE_DUAL:
      return OSPI_LINE_MODE_2LINE;
    case MCU_QSPI_MODE_QUAD:
      return OSPI_LINE_MODE_4LINE;
    default:
      return OSPI_LINE_MODE_NONE;
  }
}

static uint32_t get_address_size_bits(uint8_t size) {
  switch (size) {
    case 1:
      return 0; /* 8 bits */
    case 2:
      return 1; /* 16 bits */
    case 3:
      return 2; /* 24 bits */
    case 4:
      return 3; /* 32 bits */
    default:
      return 2; /* Default to 24 bits */
  }
}

static mcu_err_t setup_command(OCTOSPI_TypeDef* ospi, mcu_qspi_command_t* cmd) {
  /* Wait for any ongoing operation to complete */
  uint32_t tickstart = rtos_thread_systime();
  while (ospi->SR & OCTOSPI_SR_BUSY) {
    if ((rtos_thread_systime() - tickstart) > QSPI_BUSY_TIMEOUT_MS) {
      return MCU_ERROR_UNKNOWN;
    }
    rtos_thread_sleep(1);
  }

  /* Clear all flags */
  ospi->FCR = 0xFFFFFFFF;

  /* Re-initialize the value of the functional mode (HAL: OSPI_ConfigCmd line 3051) */
  MODIFY_REG(ospi->CR, OCTOSPI_CR_FMODE, 0U);

  /* Configure the CCR register with DQS and SIOO modes (HAL: line 3082) */
  /* DQS disabled (bit 28), SIOO disabled (bit 0) - instruction sent for every command */
  ospi->CCR = 0; /* Start with clean CCR */

  /* Configure alternate bytes if present (HAL: line 3084-3092) */
  if (cmd->alternate_bytes_size > 0) {
    /* Write alternate bytes value */
    ospi->ABR = cmd->alternate_bytes;

    /* Configure alternate bytes parameters in CCR */
    MODIFY_REG(ospi->CCR, (OCTOSPI_CCR_ABMODE | OCTOSPI_CCR_ABDTR | OCTOSPI_CCR_ABSIZE),
               ((map_addr_mode(cmd->alternate_mode) << OCTOSPI_CCR_ABMODE_Pos) |
                (get_address_size_bits(cmd->alternate_bytes_size) << OCTOSPI_CCR_ABSIZE_Pos)));
  }

  /* Configure the TCR register with the number of dummy cycles (HAL: line 3095) */
  MODIFY_REG(ospi->TCR, OCTOSPI_TCR_DCYC, (cmd->dummy_cycles << OCTOSPI_TCR_DCYC_Pos));

  /* Configure DLR if data phase exists (HAL: line 3097-3104) */
  if (cmd->data_length > 0) {
    ospi->DLR = cmd->data_length - 1;
  }

  /* Build and write CCR based on command configuration (HAL: line 3106+) */

  if (cmd->instruction_enable) {
    if (cmd->address_size > 0) {
      if (cmd->data_length > 0) {
        /* Command with instruction, address and data (HAL: line 3112-3120) */
        MODIFY_REG(ospi->CCR,
                   (OCTOSPI_CCR_IMODE | OCTOSPI_CCR_IDTR | OCTOSPI_CCR_ISIZE | OCTOSPI_CCR_ADMODE |
                    OCTOSPI_CCR_ADDTR | OCTOSPI_CCR_ADSIZE | OCTOSPI_CCR_DMODE | OCTOSPI_CCR_DDTR),
                   ((map_instr_mode(cmd->instruction_mode) << OCTOSPI_CCR_IMODE_Pos) |
                    (0 << OCTOSPI_CCR_ISIZE_Pos) |
                    (map_addr_mode(cmd->address_mode) << OCTOSPI_CCR_ADMODE_Pos) |
                    (get_address_size_bits(cmd->address_size) << OCTOSPI_CCR_ADSIZE_Pos) |
                    (map_data_mode(cmd->data_mode) << OCTOSPI_CCR_DMODE_Pos)));
      } else {
        /* Command with instruction and address only (HAL: line 3124-3130) */
        MODIFY_REG(ospi->CCR,
                   (OCTOSPI_CCR_IMODE | OCTOSPI_CCR_IDTR | OCTOSPI_CCR_ISIZE | OCTOSPI_CCR_ADMODE |
                    OCTOSPI_CCR_ADDTR | OCTOSPI_CCR_ADSIZE),
                   ((map_instr_mode(cmd->instruction_mode) << OCTOSPI_CCR_IMODE_Pos) |
                    (0 << OCTOSPI_CCR_ISIZE_Pos) |
                    (map_addr_mode(cmd->address_mode) << OCTOSPI_CCR_ADMODE_Pos) |
                    (get_address_size_bits(cmd->address_size) << OCTOSPI_CCR_ADSIZE_Pos)));
      }

      /* Configure the IR register with the instruction value (HAL: line 3141) */
      ospi->IR = cmd->instruction;

      /* Configure the AR register with the address value (HAL: line 3144) */
      ospi->AR = cmd->address;
    } else {
      if (cmd->data_length > 0) {
        /* Instruction + data */
        MODIFY_REG(ospi->CCR,
                   (OCTOSPI_CCR_IMODE | OCTOSPI_CCR_IDTR | OCTOSPI_CCR_ISIZE | OCTOSPI_CCR_DMODE |
                    OCTOSPI_CCR_DDTR),
                   ((map_instr_mode(cmd->instruction_mode) << OCTOSPI_CCR_IMODE_Pos) |
                    (0 << OCTOSPI_CCR_ISIZE_Pos) |
                    (map_data_mode(cmd->data_mode) << OCTOSPI_CCR_DMODE_Pos)));
      } else {
        /* Instruction only */
        MODIFY_REG(ospi->CCR, (OCTOSPI_CCR_IMODE | OCTOSPI_CCR_IDTR | OCTOSPI_CCR_ISIZE),
                   ((map_instr_mode(cmd->instruction_mode) << OCTOSPI_CCR_IMODE_Pos) |
                    (0 << OCTOSPI_CCR_ISIZE_Pos)));
      }

      ospi->IR = cmd->instruction;
    }
  } else {
    if (cmd->address_size > 0) {
      if (cmd->data_length > 0) {
        /* Address + data */
        MODIFY_REG(ospi->CCR,
                   (OCTOSPI_CCR_ADMODE | OCTOSPI_CCR_ADDTR | OCTOSPI_CCR_ADSIZE |
                    OCTOSPI_CCR_DMODE | OCTOSPI_CCR_DDTR),
                   ((map_addr_mode(cmd->address_mode) << OCTOSPI_CCR_ADMODE_Pos) |
                    (get_address_size_bits(cmd->address_size) << OCTOSPI_CCR_ADSIZE_Pos) |
                    (map_data_mode(cmd->data_mode) << OCTOSPI_CCR_DMODE_Pos)));
      } else {
        /* Address only */
        MODIFY_REG(ospi->CCR, (OCTOSPI_CCR_ADMODE | OCTOSPI_CCR_ADDTR | OCTOSPI_CCR_ADSIZE),
                   ((map_addr_mode(cmd->address_mode) << OCTOSPI_CCR_ADMODE_Pos) |
                    (get_address_size_bits(cmd->address_size) << OCTOSPI_CCR_ADSIZE_Pos)));
      }

      ospi->AR = cmd->address;
    } else {
      /* Invalid: no instruction, no address */
      return MCU_ERROR_PARAMETER;
    }
  }

  return MCU_ERROR_OK;
}

static mcu_err_t wait_for_flag(OCTOSPI_TypeDef* ospi, uint32_t flag, uint32_t timeout_ms) {
  uint32_t tickstart = rtos_thread_systime();

  if (flag == OCTOSPI_SR_BUSY) {
    while (ospi->SR & OCTOSPI_SR_BUSY) {
      if ((rtos_thread_systime() - tickstart) > timeout_ms) {
        return MCU_ERROR_UNKNOWN;
      }
      rtos_thread_sleep(1);
    }
  } else if (flag == OCTOSPI_SR_TCF) {
    while (!(ospi->SR & OCTOSPI_SR_TCF)) {
      if ((rtos_thread_systime() - tickstart) > timeout_ms) {
        return MCU_ERROR_UNKNOWN;
      }
      rtos_thread_sleep(1);
    }
  } else {
    while (!(ospi->SR & flag)) {
      if ((rtos_thread_systime() - tickstart) > timeout_ms) {
        return MCU_ERROR_UNKNOWN;
      }
      rtos_thread_sleep(1);
    }
  }

  return MCU_ERROR_OK;
}

static mcu_err_t octospi_transmit(OCTOSPI_TypeDef* ospi, const uint8_t* data, uint32_t len) {
  uint32_t tickstart = rtos_thread_systime();

  /* Write data to FIFO */
  for (uint32_t i = 0; i < len; i++) {
    while (!(ospi->SR & OCTOSPI_SR_FTF)) {
      if ((rtos_thread_systime() - tickstart) > QSPI_TIMEOUT_MS) {
        return MCU_ERROR_UNKNOWN;
      }
    }

    *((volatile uint8_t*)&ospi->DR) = data[i];
  }

  /* Wait for completion */
  if (wait_for_flag(ospi, OCTOSPI_SR_TCF, QSPI_TIMEOUT_MS) != MCU_ERROR_OK) {
    return MCU_ERROR_UNKNOWN;
  }

  ospi->FCR = OCTOSPI_FCR_CTCF;

  return MCU_ERROR_OK;
}

static mcu_err_t octospi_receive(OCTOSPI_TypeDef* ospi, uint8_t* data, uint32_t len) {
  uint32_t tickstart = rtos_thread_systime();

  /* Read data from FIFO */
  for (uint32_t i = 0; i < len; i++) {
    while (!(ospi->SR & (OCTOSPI_SR_FTF | OCTOSPI_SR_TCF))) {
      if ((rtos_thread_systime() - tickstart) > QSPI_TIMEOUT_MS) {
        return MCU_ERROR_UNKNOWN;
      }
    }

    data[i] = *((volatile uint8_t*)&ospi->DR);
  }

  /* Wait for completion */
  if (wait_for_flag(ospi, OCTOSPI_SR_TCF, QSPI_TIMEOUT_MS) != MCU_ERROR_OK) {
    return MCU_ERROR_UNKNOWN;
  }

  ospi->FCR = OCTOSPI_FCR_CTCF;

  return MCU_ERROR_OK;
}

static void octospi_irq_handler(uint32_t handle_index) {
  if (handle_index >= QSPI_MAX_INSTANCES) {
    return;
  }

  qspi_handle_t* handle = &qspi_handles[handle_index];
  if (!handle->instance || !handle->state) {
    return;
  }

  OCTOSPI_TypeDef* ospi = handle->instance;
  mcu_qspi_state_t* state = handle->state;

  uint32_t sr = ospi->SR;
  uint32_t cr = ospi->CR;

  /* FIFO threshold interrupt - handle data transfer */
  if ((sr & OCTOSPI_SR_FTF) && (cr & OCTOSPI_CR_FTIE)) {
    if (state->state == MCU_QSPI_STATE_BUSY) {
      if (state->tx_buffer) {
        /* Write multiple bytes while FIFO has space */
        do {
          if (state->transfer_index >= state->transfer_count) {
            break;
          }
          *((volatile uint8_t*)&ospi->DR) = state->tx_buffer[state->transfer_index];
          state->transfer_index++;
        } while (ospi->SR & OCTOSPI_SR_FTF);

        if (state->transfer_index >= state->transfer_count) {
          CLEAR_BIT(ospi->CR, OCTOSPI_CR_FTIE); /* All data sent - disable FIFO interrupt */
        }

      } else if (state->rx_buffer) {
        /* Read multiple bytes while FIFO has data */
        do {
          if (state->transfer_index >= state->transfer_count) {
            break;
          }
          state->rx_buffer[state->transfer_index] = *((volatile uint8_t*)&ospi->DR);
          state->transfer_index++;
        } while ((ospi->SR & OCTOSPI_SR_FTF) || (ospi->SR & OCTOSPI_SR_TCF));

        if (state->transfer_index >= state->transfer_count) {
          CLEAR_BIT(ospi->CR, OCTOSPI_CR_FTIE); /* All data received - disable FIFO interrupt */
        }
      }
    }
  }

  /* Transfer complete interrupt */
  else if ((sr & OCTOSPI_SR_TCF) && (cr & OCTOSPI_CR_TCIE)) {
    /* Check if this is DMA transfer completion */
    if (ospi->CR & OCTOSPI_CR_DMAEN) {
      /* DMA transfer complete - clear flags and cleanup */
      ospi->FCR = OCTOSPI_FCR_CTCF;
      CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_TCIE | OCTOSPI_CR_TEIE);

      state->transfer_status = MCU_ERROR_OK;

      /* Save callback before cleanup to avoid race condition */
      mcu_qspi_callback_t callback = state->callback;
      uint32_t transfer_count = state->transfer_count;

      qspi_cleanup_state(state, true);

      if (callback) {
        callback(state, MCU_ERROR_OK, transfer_count);
      } else {
        rtos_semaphore_give_from_isr(&state->transfer_complete);
      }
    } else {
      /* Non-DMA transfer (interrupt-driven) */
      if (state->state == MCU_QSPI_STATE_BUSY && state->rx_buffer) {
        /* Read remaining FIFO data */
        if ((state->transfer_index < state->transfer_count) && ((sr & OCTOSPI_SR_FLEVEL) != 0)) {
          state->rx_buffer[state->transfer_index++] = *((volatile uint8_t*)&ospi->DR);
        }

        if (state->transfer_index < state->transfer_count) {
          return;
        }
      }

      ospi->FCR = OCTOSPI_FCR_CTCF;

      ospi->CR &= ~(OCTOSPI_CR_FTIE | OCTOSPI_CR_TCIE | OCTOSPI_CR_TEIE);

      state->transfer_status = MCU_ERROR_OK;

      /* Save callback before cleanup to avoid race condition */
      mcu_qspi_callback_t callback = state->callback;
      uint32_t transfer_count = state->transfer_count;

      qspi_cleanup_state(state, false);

      if (callback) {
        callback(state, MCU_ERROR_OK, transfer_count);
      } else {
        rtos_semaphore_give_from_isr(&state->transfer_complete);
      }
    }
  }

  /* Transfer error interrupt */
  if (sr & OCTOSPI_SR_TEF) {
    ospi->FCR = OCTOSPI_FCR_CTEF;

    CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_FTIE | OCTOSPI_CR_TCIE | OCTOSPI_CR_TEIE);

    state->transfer_status = MCU_ERROR_UNKNOWN;

    /* Save callback before cleanup to avoid race condition */
    mcu_qspi_callback_t callback = state->callback;

    qspi_cleanup_state(state, true);

    if (callback) {
      callback(state, MCU_ERROR_UNKNOWN, 0);
    } else {
      rtos_semaphore_give_from_isr(&state->transfer_complete);
    }
  }
}

void OCTOSPI1_IRQHandler(void) {
  octospi_irq_handler(0);
}

void OCTOSPI2_IRQHandler(void) {
  octospi_irq_handler(1);
}

static bool qspi_dma_callback(uint32_t channel, uint32_t flags, void* user_param) {
  mcu_qspi_state_t* state = (mcu_qspi_state_t*)user_param;

  if (!state) {
    return false;
  }

  qspi_handle_t* handle = (qspi_handle_t*)state->instance;
  OCTOSPI_TypeDef* ospi = handle->instance;

  /* Check for DMA errors */
  if (flags & MCU_DMA_FLAG_TRANSFER_ERROR) {
    /* Disable OCTOSPI DMA */
    CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_TEIE);

    state->transfer_status = MCU_ERROR_UNKNOWN;

    /* Save callback before cleanup to avoid race condition */
    mcu_qspi_callback_t callback = state->callback;

    qspi_cleanup_state(state, true);

    if (callback) {
      callback(state, MCU_ERROR_UNKNOWN, 0);
    } else {
      rtos_semaphore_give_from_isr(&state->transfer_complete);
    }

    return true;
  }

  /* Transfer complete */
  if (flags & MCU_DMA_FLAG_TRANSFER_COMPLETE) {
    state->bytes_transferred += state->current_chunk_size;

    /* Check if more chunks need to be transferred */
    if (state->chunks_remaining > 0) {
      state->chunks_remaining--;

      /* Calculate next chunk size */
      uint32_t remaining_bytes = state->total_transfer_size - state->bytes_transferred;
      state->current_chunk_size =
        (remaining_bytes > MCU_DMA_MAX_XFER_COUNT) ? MCU_DMA_MAX_XFER_COUNT : remaining_bytes;

      /* Configure DMA for next chunk */
      mcu_dma_config_t dma_config = {
        .mode = MCU_DMA_MODE_BASIC,
        .request = (ospi == OCTOSPI1) ? MCU_DMA_SIGNAL_OCTOSPI1 : MCU_DMA_SIGNAL_OCTOSPI2,
        .src_width = MCU_DMA_SIZE_1_BYTE,
        .dst_width = MCU_DMA_SIZE_1_BYTE,
        .priority = MCU_DMA_REQ_PRIORITY_HIGH,
        .callback = qspi_dma_callback,
        .user_param = state,
        .xfer_node = NULL,
      };

      if (state->tx_buffer) {
        /* TX mode: Memory to QSPI data register */
        dma_config.direction = MCU_DMA_DIR_M2P;
        dma_config.src_addr = (void*)(state->tx_buffer + state->bytes_transferred);
        dma_config.dst_addr = (void*)&ospi->DR;
        dma_config.src_increment = true;
        dma_config.dst_increment = false;
        dma_config.length = state->current_chunk_size;
      } else {
        /* RX mode: QSPI data register to Memory */
        dma_config.direction = MCU_DMA_DIR_P2M;
        dma_config.src_addr = (void*)&ospi->DR;
        dma_config.dst_addr = (void*)(state->rx_buffer + state->bytes_transferred);
        dma_config.src_increment = false;
        dma_config.dst_increment = true;
        dma_config.length = state->current_chunk_size;
      }

      /* Reconfigure and restart DMA for next chunk */
      mcu_err_t result = mcu_dma_channel_configure(channel, &dma_config);
      if (result == MCU_ERROR_OK) {
        result = mcu_dma_channel_start(channel);
      }

      if (result != MCU_ERROR_OK) {
        /* Error starting next chunk */
        CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_TEIE);
        state->transfer_status = result;

        /* Save callback before cleanup to avoid race condition */
        mcu_qspi_callback_t callback = state->callback;
        uint32_t bytes_transferred = state->bytes_transferred;

        qspi_cleanup_state(state, true);

        if (callback) {
          callback(state, result, bytes_transferred);
        } else {
          rtos_semaphore_give_from_isr(&state->transfer_complete);
        }
      }

      return true; /* More chunks to process */
    }

    /* All chunks complete - check if transfer already complete */
    if (ospi->SR & OCTOSPI_SR_TCF) {
      /* Transfer already complete */
      ospi->FCR = OCTOSPI_FCR_CTCF;
      CLEAR_BIT(ospi->CR, OCTOSPI_CR_DMAEN | OCTOSPI_CR_TCIE | OCTOSPI_CR_TEIE);

      state->transfer_status = MCU_ERROR_OK;

      /* Save callback before cleanup to avoid race condition */
      mcu_qspi_callback_t callback = state->callback;
      uint32_t transfer_count = state->transfer_count;

      qspi_cleanup_state(state, true);

      if (callback) {
        callback(state, MCU_ERROR_OK, transfer_count);
      } else {
        rtos_semaphore_give_from_isr(&state->transfer_complete);
      }
    } else {
      /* Transfer not complete yet - enable TC interrupt to wait for it */
      SET_BIT(ospi->CR, OCTOSPI_CR_TCIE);
    }
  }

  return true;
}
