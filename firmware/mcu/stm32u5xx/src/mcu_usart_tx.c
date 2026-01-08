#include "mcu_usart_tx.h"

#include "mcu.h"
#include "mcu_dma.h"
#include "mcu_dma_impl.h"
#include "mcu_usart.h"
#include "rtos.h"
#include "rtos_thread.h"
// stm32 ll
#include "stm32u5xx.h"
#include "stm32u5xx_ll_dma.h"
#include "stm32u5xx_ll_usart.h"

#include <stdbool.h>
#include <stddef.h>
#include <string.h>

static void mcu_usart_tx_destroy(mcu_usart_tx_state_t* state);
static void tx_dma_start(mcu_usart_tx_state_t* state);
static void tx_dma_schedule(mcu_usart_tx_state_t* state);
static bool tx_dma_callback(uint32_t channel, uint32_t flags, void* user_param);
static bool usart_tx_ringbuf_lock(void);
static bool usart_tx_ringbuf_unlock(void);

static bool usart_tx_ringbuf_lock(void) {
  rtos_thread_enter_critical();
  return true;
}

static bool usart_tx_ringbuf_unlock(void) {
  rtos_thread_exit_critical();
  return true;
}

void mcu_usart_tx_init_dma(mcu_usart_tx_state_t* state, mcu_usart_config_t* config) {
  ASSERT(state != NULL);
  state->usart = (USART_TypeDef*)config->usart.base;

  /* Initialize RTOS primitives */
  rtos_mutex_create(&state->access);
  rtos_mutex_create(&state->rb_access);

  /* Initialize ring buffer */
  ringbuf_api_t api = {
    .lock = usart_tx_ringbuf_lock,
    .unlock = usart_tx_ringbuf_unlock,
  };
  state->ringbuf.api = api;
  ringbuf_init(&state->ringbuf, state->buffer, MCU_USART_TX_BUFFER_LEN);

  /* Allocate DMA channel */
  mcu_err_t err = mcu_dma_allocate_channel(&state->dma_channel);
  if (err != MCU_ERROR_OK) {
    mcu_usart_tx_destroy(state);
    return;
  }

  /* Determine DMA request line based on USART instance */
  if (state->usart == USART1) {
    state->dma_request = MCU_DMA_SIGNAL_USART1_TX;
  } else if (state->usart == USART2) {
    state->dma_request = MCU_DMA_SIGNAL_USART2_TX;
  } else if (state->usart == USART3) {
    state->dma_request = MCU_DMA_SIGNAL_USART3_TX;
  } else if (state->usart == (USART_TypeDef*)UART4) {
    state->dma_request = MCU_DMA_SIGNAL_UART4_TX;
  } else if (state->usart == (USART_TypeDef*)UART5) {
    state->dma_request = MCU_DMA_SIGNAL_UART5_TX;
  } else if (state->usart == (USART_TypeDef*)LPUART1) {
    state->dma_request = MCU_DMA_SIGNAL_LPUART1_TX;
  } else {
    mcu_dma_channel_free(state->dma_channel);
    mcu_usart_tx_destroy(state);
    return; /* Unsupported USART */
  }

  state->initialized = true;
  state->xfer_len = 0; /* No transfer active */
}

uint32_t mcu_usart_tx_write(mcu_usart_tx_state_t* state, const uint8_t* data, uint32_t len) {
  ASSERT(state != NULL);
  if (!state->initialized || (len == 0) || (data == NULL)) {
    return 0;
  }

  uint32_t written = 0;

  rtos_mutex_lock(&state->access);

  /* Write data to TX ring buffer
   * ringbuf_push_buf returns the last index written (0-based)
   * Returns 0 for both "wrote 1 byte" and "wrote nothing"
   * So we need to check if we actually tried to write something
   */
  uint32_t result = ringbuf_push_buf(&state->ringbuf, (uint8_t*)data, len);

  /* Check if write was successful */
  if (len == 1 && result == 0) {
    /* Successfully wrote 1 byte */
    written = 1;
  } else if (len > 1 && result == (len - 1)) {
    /* Successfully wrote all bytes */
    written = len;
  } else {
    /* Nothing was written (buffer full) */
    written = 0;
    rtos_mutex_unlock(&state->access);
    return 0;
  }

  /* Try to start transfer if not already active - must be in critical section */
  rtos_thread_enter_critical();
  tx_dma_schedule(state);
  rtos_thread_exit_critical();

  rtos_mutex_unlock(&state->access);

  return written; /* Return actual bytes written to buffer */
}

static void tx_dma_schedule(mcu_usart_tx_state_t* state) {
  /* Check if transfer is currently inactive by examining state->xfer_len
   * Following the golden example pattern - this is atomic when called
   * with interrupts disabled or from ISR context
   */
  if (state->xfer_len == 0) {
    /* Get size of contiguous data - mutex will detect ISR context */
    state->xfer_len = ringbuf_size_contiguous(&state->ringbuf);
    if (state->xfer_len > 0) {
      /* Limit transfer size if needed */
      if (state->xfer_len > 1024) {
        state->xfer_len = 1024;
      }

      tx_dma_start(state);
    }
  }
}

static void mcu_usart_tx_destroy(mcu_usart_tx_state_t* state) {
  rtos_mutex_destroy(&state->access);
  rtos_mutex_destroy(&state->rb_access);
}

static void tx_dma_start(mcu_usart_tx_state_t* state) {
  /* Get pointer to tail for reading */
  uint8_t* xfer_ptr = ringbuf_tail_ptr(&state->ringbuf);
  if (xfer_ptr == NULL || state->xfer_len == 0) {
    state->xfer_len = 0;
    return;
  }

  /* Configure DMA for TX operation using mcu_dma module */
  mcu_dma_config_t dma_config = {
    .src_addr = xfer_ptr,
    .dst_addr = (void*)LL_USART_DMA_GetRegAddr(state->usart, LL_USART_DMA_REG_DATA_TRANSMIT),
    .length = state->xfer_len,
    .direction = MCU_DMA_DIR_M2P,
    .src_width = MCU_DMA_SIZE_1_BYTE,
    .dst_width = MCU_DMA_SIZE_1_BYTE,
    .src_increment = true,
    .dst_increment = false,
    .request = state->dma_request,
    .mode = MCU_DMA_MODE_BASIC,
    .priority = MCU_DMA_REQ_PRIORITY_MEDIUM,
    .callback = tx_dma_callback,
    .user_param = (void*)state,
  };

  mcu_err_t err = mcu_dma_channel_configure(state->dma_channel, &dma_config);
  if (err != MCU_ERROR_OK) {
    state->xfer_len = 0;
    return;
  }

  /* Start DMA channel */
  err = mcu_dma_channel_start(state->dma_channel);
  if (err != MCU_ERROR_OK) {
    state->xfer_len = 0;
  }
}

static bool tx_dma_callback(uint32_t channel, uint32_t flags, void* user_param) {
  (void)channel;

  mcu_usart_tx_state_t* state = user_param;
  ASSERT(state != NULL);
  uint32_t xfer_len = state->xfer_len;

  /* Check for transfer complete */
  if (flags & MCU_DMA_FLAG_TRANSFER_COMPLETE) {
    /* Stop DMA channel */
    mcu_dma_channel_stop(state->dma_channel);

    /* Advance ring buffer tail pointer */
    ringbuf_advance(&state->ringbuf, xfer_len);

    /* Clear transfer length to indicate inactive */
    state->xfer_len = 0;

    /* Schedule next transfer if more data available */
    tx_dma_schedule(state);
  } else if (flags & MCU_DMA_FLAG_TRANSFER_ERROR) {
    /* Reset transfer state on error */
    state->xfer_len = 0;
    mcu_dma_channel_stop(state->dma_channel);
  }

  return true;
}
