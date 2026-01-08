#include "mcu_usart_rx.h"

#include "mcu.h"
#include "mcu_dma.h"
#include "mcu_dma_impl.h"
#include "mcu_usart.h"
#include "rtos.h"
#include "rtos_thread.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_dma.h"
#include "stm32u5xx_ll_usart.h"

#include <stdbool.h>
#include <stddef.h>
#include <string.h>

#define RX_EVENT_BIT0 (1 << 0)

static void mcu_usart_rx_destroy(mcu_usart_rx_state_t* state);
static uint32_t mcu_usart_rx_available_internal(mcu_usart_rx_state_t* state);
static void usart_rx_check(mcu_usart_rx_state_t* state);
static bool rx_dma_callback(uint32_t channel, uint32_t flags, void* user_param);
static bool mcu_usart_rx_start_transfer(mcu_usart_rx_state_t* state);

void mcu_usart_rx_init_dma(mcu_usart_rx_state_t* state, mcu_usart_config_t* config) {
  /* Buffer is empty on init */
  state->rd = state->wr = 0;
  state->usart = (USART_TypeDef*)config->usart.base;

  rtos_mutex_create(&state->access);
  rtos_semaphore_create(&state->timeout);
  rtos_event_group_create(&state->events);

  /* Allocate DMA channel */
  mcu_err_t err = mcu_dma_allocate_channel(&state->dma.channel_num);
  if (err != MCU_ERROR_OK) {
    mcu_usart_rx_destroy(state);
    return;
  }

  if (mcu_usart_rx_start_transfer(state)) {
    state->initialized = true;
  } else {
    mcu_dma_channel_free(state->dma.channel_num);
    mcu_usart_rx_destroy(state);
  }
}

uint32_t mcu_usart_rx_read(mcu_usart_rx_state_t* state, uint8_t* data, uint32_t len,
                           uint32_t timeout_ms) {
  ASSERT(state != NULL);
  if (!state->initialized) {
    return 0;
  }

  uint32_t n_available;

  rtos_mutex_lock(&state->access);

  do {
    rtos_thread_enter_critical();
    n_available = mcu_usart_rx_available_internal(state);
    rtos_thread_exit_critical();

  } while ((n_available < len) && (rtos_semaphore_take(&state->timeout, timeout_ms)));

  /* Limit len to available bytes */
  uint32_t n = (len > n_available) ? n_available : len;

  if (n == 0) {
    rtos_mutex_unlock(&state->access);
    return n;
  }

  /* Handle buffer wrapping */
  if (state->rd + n < MCU_USART_RX_BUFFER_LEN) {
    memcpy(data, &(state->buffer[state->rd]), n);
  } else {
    memcpy(&data[0], &(state->buffer[state->rd]), MCU_USART_RX_BUFFER_LEN - state->rd);
    memcpy(&data[MCU_USART_RX_BUFFER_LEN - state->rd], &(state->buffer[0]),
           n - MCU_USART_RX_BUFFER_LEN + state->rd);
  }

  rtos_thread_enter_critical();
  state->rd = (state->rd + n) % MCU_USART_RX_BUFFER_LEN;
  rtos_thread_exit_critical();

  rtos_mutex_unlock(&state->access);

  return n;
}

uint32_t mcu_usart_rx_available(mcu_usart_rx_state_t* state) {
  ASSERT(state != NULL);
  if (!state->initialized) {
    return 0;
  }

  rtos_thread_enter_critical();
  uint32_t available = mcu_usart_rx_available_internal(state);
  rtos_thread_exit_critical();

  return available;
}

static void mcu_usart_rx_destroy(mcu_usart_rx_state_t* state) {
  rtos_mutex_destroy(&state->access);
  rtos_semaphore_destroy(&state->timeout);
  rtos_event_group_destroy(&state->events);
}

static uint32_t mcu_usart_rx_available_internal(mcu_usart_rx_state_t* state) {
  /* Get the next address the DMA will write to */
  uint32_t remaining = 0;
  mcu_err_t err = mcu_dma_channel_get_remaining(state->dma.channel_num, &remaining);
  if (err != MCU_ERROR_OK) {
    return 0;
  }

  /* The index in the buffer is the difference between the end and start addresses */
  const size_t pos = sizeof(state->buffer) - remaining;

  /* Update write position */
  state->wr = pos;

  /* Calculate available bytes */
  uint32_t available;
  if (state->wr >= state->rd) {
    available = state->wr - state->rd;
  } else {
    available = (sizeof(state->buffer) - state->rd) + state->wr;
  }

  return available;
}

static bool rx_dma_callback(uint32_t channel, uint32_t flags, void* user_param) {
  (void)channel;
  mcu_usart_rx_state_t* state = (mcu_usart_rx_state_t*)user_param;
  ASSERT(state != NULL);

  /* Check for half-transfer or transfer complete */
  if (flags & (MCU_DMA_FLAG_HALF_TRANSFER | MCU_DMA_FLAG_TRANSFER_COMPLETE)) {
    usart_rx_check(state);
  }
  return true;
}

void mcu_usart_rx_idle_irq_handler(mcu_usart_rx_state_t* state) {
  ASSERT(state != NULL);
  USART_TypeDef* usart = state->usart;
  if (LL_USART_IsEnabledIT_IDLE(usart) && LL_USART_IsActiveFlag_IDLE(usart)) {
    LL_USART_ClearFlag_IDLE(usart);
    usart_rx_check(state);
  }
}

static void usart_rx_check(mcu_usart_rx_state_t* state) {
  /* Update available count and signal waiting threads */
  if (mcu_usart_rx_available_internal(state) > 0) {
    rtos_semaphore_give_from_isr(&state->timeout);
    bool woken;
    rtos_event_group_set_bits_from_isr(&state->events, RX_EVENT_BIT0, &woken);
    (void)woken;
  }
}

static bool mcu_usart_rx_start_transfer(mcu_usart_rx_state_t* state) {
  /* Determine DMA request line based on USART instance */
  uint32_t dma_request = 0;
  if (state->usart == USART1) {
    dma_request = MCU_DMA_SIGNAL_USART1_RX;
  } else if (state->usart == USART2) {
    dma_request = MCU_DMA_SIGNAL_USART2_RX;
  } else if (state->usart == USART3) {
    dma_request = MCU_DMA_SIGNAL_USART3_RX;
  } else if (state->usart == (USART_TypeDef*)UART4) {
    dma_request = MCU_DMA_SIGNAL_UART4_RX;
  } else if (state->usart == (USART_TypeDef*)UART5) {
    dma_request = MCU_DMA_SIGNAL_UART5_RX;
  } else if (state->usart == (USART_TypeDef*)LPUART1) {
    dma_request = MCU_DMA_SIGNAL_LPUART1_RX;
  } else {
    return false; /* Unsupported USART */
  }

  /* Configure DMA for circular transfers */
  mcu_dma_config_t dma_config = {
    .src_addr = (void*)LL_USART_DMA_GetRegAddr(state->usart, LL_USART_DMA_REG_DATA_RECEIVE),
    .dst_addr = state->buffer,
    .length = MCU_USART_RX_BUFFER_LEN,
    .direction = MCU_DMA_DIR_P2M,
    .src_width = MCU_DMA_SIZE_1_BYTE,
    .dst_width = MCU_DMA_SIZE_1_BYTE,
    .src_increment = false,
    .dst_increment = true,
    .request = dma_request,
    .mode = MCU_DMA_MODE_CIRCULAR,
    .priority = MCU_DMA_REQ_PRIORITY_HIGH,
    .callback = rx_dma_callback,
    .xfer_node = &state->dma.node,
    .user_param = (void*)state};

  mcu_err_t err = mcu_dma_channel_configure(state->dma.channel_num, &dma_config);
  if (err != MCU_ERROR_OK) {
    return false;
  }

  /* Start DMA channel */
  err = mcu_dma_channel_start(state->dma.channel_num);
  if (err != MCU_ERROR_OK) {
    return false;
  }
  return true;
}
