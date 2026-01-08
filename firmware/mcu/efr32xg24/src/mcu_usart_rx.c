#include "mcu_usart_rx.h"

#include "mcu.h"
#include "mcu_dma.h"
#include "mcu_dma_impl.h"
#include "mcu_usart.h"
#include "mcu_usart_rx.h"
#include "platform.h"
#include "rtos.h"

#include "em_cmu.h"
#include "em_device.h"
#include "em_eusart.h"
#include "em_ldma.h"
#include "em_usart.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

typedef struct {
  /* Base address of USART the state belongs to. */
  void* usart_base;

  /* USART is an EUSART instance */
  bool eusart;

  /* USART IRQN */
  uint32_t irqn;

  /* Circular Buffer */
  uint8_t buffer[MCU_USART_RX_BUFFER_LEN];
  uint32_t rd; /* Next byte to read out of the buffer */
  uint32_t wr; /* Next buffer write address */

  /* EFR32 LDMA */
  struct {
    uint32_t channel;         /* Channel to use for LDMA transfers */
    uint32_t signal;          /* LDMA signal */
    uint8_t active_link_desc; /* Currently active LDMA link descriptor */
  } ldma;

  /* RTOS */
  rtos_mutex_t access;
  rtos_semaphore_t timeout;
} mcu_usart_rx_state_t;

static mcu_usart_rx_state_t _rx_states[PLATFORM_CFG_MCU_UART_CNT] = {0};

/* RX LDMA */
static bool _mcu_usart_rx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param);
static mcu_usart_rx_state_t* _mcu_usart_rx_get_state(void* usart_base);
static mcu_usart_rx_state_t* _mcu_usart_rx_allocate_state(void* usart_base);
static void _mcu_usart_rx_timeout_handler(USART_TypeDef* eusart);
static void _mcu_eusart_rx_timeout_handler(EUSART_TypeDef* eusart);
static void _mcu_usart_rx_timeout_isr(void* usart_base);

mcu_err_t mcu_usart_rx_init_dma(mcu_usart_config_t* config) {
  if ((config == NULL) || (config->usart.base == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  mcu_usart_rx_state_t* state = _mcu_usart_rx_get_state(config->usart.base);
  if (state == NULL) {
    state = _mcu_usart_rx_allocate_state(config->usart.base);
    if (state == NULL) {
      return MCU_ERROR_OUT_OF_MEM;
    }
  }

  /* Buffer is empty on init */
  state->rd = state->wr = 0;

  rtos_mutex_create(&state->access);
  rtos_semaphore_create(&state->timeout);

  if (state->usart_base == (void*)USART0) {
    state->ldma.signal = MCU_DMA_SIGNAL_USART0_RXDATAV;
    state->irqn = USART0_RX_IRQn;
    state->eusart = false;
  } else if (state->usart_base == (void*)EUSART0) {
    state->ldma.signal = MCU_DMA_SIGNAL_EUSART0_RXDATAV;
    state->irqn = EUSART0_RX_IRQn;
    state->eusart = true;
  } else if (state->usart_base == (void*)EUSART1) {
    state->ldma.signal = MCU_DMA_SIGNAL_EUSART1_RXDATAV;
    state->irqn = EUSART1_RX_IRQn;
    state->eusart = true;
  } else {
    /* Invalid USART instance. */
    return MCU_ERROR_PARAMETER;
  }

  /* Enable RX timeout interrupts */
  if (config->rx_irq_timeout) {
    if (state->eusart) {
      EUSART_TypeDef* eusart = state->usart_base;
      EUSART_IntEnable(eusart, EUSART_IEN_RXTO);
    } else {
      /* Set RX Timeout feature to generate an interrupt after 256 baud times */
      USART_TypeDef* usart = state->usart_base;
      usart->TIMECMP1 = USART_TIMECMP1_TSTART_RXEOF | USART_TIMECMP1_TSTOP_RXACT | 0xFF;
      USART_IntEnable(usart, USART_IEN_TCMP1);
    }

    NVIC_ClearPendingIRQ(state->irqn);
    NVIC_EnableIRQ(state->irqn);
  }

  if (mcu_dma_allocate_channel(&state->ldma.channel) != MCU_ERROR_OK) {
    return MCU_ERROR_DMA_ALLOC;
  }

  if (mcu_dma_channel_configure(state->ldma.channel, state) != MCU_ERROR_OK) {
    return MCU_ERROR_PARAMETER;
  }

  /* Determine source for LDMA transfers. */
  void* src;
  if (state->eusart) {
    EUSART_TypeDef* eusart = state->usart_base;
    src = (void*)&(eusart->RXDATA);
  } else {
    USART_TypeDef* usart = state->usart_base;
    src = (void*)&(usart->RXDATA);
  }

  /* Start a ping-pong transfer. We use half the buffer. */
  const mcu_err_t err = mcu_dma_peripheral_memory_ping_pong(
    state->ldma.channel, state->ldma.signal, state->buffer,
    state->buffer + (sizeof(state->buffer) / 2), (void*)src, true, sizeof(state->buffer) / 2,
    MCU_DMA_SIZE_1_BYTE, _mcu_usart_rx_ldma_isr, state);

  return err;
}

uint32_t mcu_usart_rx_read(mcu_usart_config_t* config, uint8_t* data, uint32_t len,
                           uint32_t timeout_ms) {
  uint32_t n_available;

  mcu_usart_rx_state_t* state = _mcu_usart_rx_get_state(config->usart.base);
  if (state == NULL) {
    return 0;
  }

  rtos_mutex_lock(&state->access);

  do {
    rtos_thread_enter_critical();
    n_available = mcu_usart_rx_available(config);
    rtos_thread_exit_critical();

  } while ((n_available < len) && (rtos_semaphore_take(&state->timeout, timeout_ms)));

  /* Limit len to available bytes */
  uint32_t n = (len > n_available) ? n_available : len;

  if (n == 0) {
    rtos_mutex_unlock(&state->access);
    return n;
  }

  /* Handle buffer wrapping */
  if (state->rd + n < sizeof(state->buffer)) {
    memcpy(data, &(state->buffer[state->rd]), n);
  } else {
    memcpy(&data[0], &(state->buffer[state->rd]), sizeof(state->buffer) - state->rd);
    memcpy(&data[sizeof(state->buffer) - state->rd], &(state->buffer[0]),
           n - sizeof(state->buffer) + state->rd);
  }

  rtos_thread_enter_critical();
  state->rd = (state->rd + n) % sizeof(state->buffer);
  rtos_thread_exit_critical();

  rtos_mutex_unlock(&state->access);

  return n;
}

uint32_t mcu_usart_rx_available(mcu_usart_config_t* config) {
  mcu_usart_rx_state_t* state = _mcu_usart_rx_get_state(config->usart.base);
  ASSERT(state != NULL);

  uint32_t available = 0;
  if (state->wr >= state->rd) {
    available = (state->wr - state->rd);
  } else {
    available = (sizeof(state->buffer) - state->rd) + state->wr;
  }

  return available;
}

static bool _mcu_usart_rx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param) {
  (void)channel;
  (void)sequence_num;

  mcu_usart_rx_state_t* state = (mcu_usart_rx_state_t*)user_param;
  ASSERT(state != NULL);

  /* Update the write index */
  // TODO: Handle overflow
  if (state->ldma.active_link_desc == 0) {
    state->ldma.active_link_desc = 1;
    state->wr = sizeof(state->buffer) / 2;
  } else {
    state->ldma.active_link_desc = 0;
    state->wr = 0;
  }

  rtos_semaphore_give_from_isr(&state->timeout);

  return true;
}

static void _mcu_usart_rx_timeout_isr(void* usart_base) {
  mcu_usart_rx_state_t* state = _mcu_usart_rx_get_state(usart_base);
  ASSERT(state != NULL);

  const uint32_t xfer_count = ((LDMA->CH[state->ldma.channel].CTRL & _LDMA_CH_CTRL_XFERCNT_MASK) >>
                               _LDMA_CH_CTRL_XFERCNT_SHIFT);

  /* Update stop index based on the number of LDMA transfers that occurred */
  if (state->ldma.active_link_desc == 0) {
    state->wr = sizeof(state->buffer) / 2 - xfer_count - 1;
  } else {
    state->wr = sizeof(state->buffer) - xfer_count - 1;
  }

  rtos_semaphore_give_from_isr(&state->timeout);
}

static void _mcu_usart_rx_timeout_handler(USART_TypeDef* usart) {
  /* Clear interrupt flags */
  uint32_t flags = USART_IntGet(usart);
  USART_IntClear(usart, flags);

  /* Check if RX timeout occurred. */
  if (flags & USART_IF_TCMP1) {
    _mcu_usart_rx_timeout_isr(usart);
  }
}

static void _mcu_eusart_rx_timeout_handler(EUSART_TypeDef* eusart) {
  /* Clear interrupt flags */
  uint32_t flags = EUSART_IntGet(eusart);
  EUSART_IntClear(eusart, flags);

  /* Check if RX timeout occurred. */
  if (flags & EUSART_IF_RXTO) {
    _mcu_usart_rx_timeout_isr(eusart);
  }
}

static mcu_usart_rx_state_t* _mcu_usart_rx_get_state(void* usart_base) {
  for (uint8_t i = 0; i < sizeof(_rx_states) / sizeof(_rx_states[0]); i++) {
    if (_rx_states[i].usart_base == usart_base) {
      return &_rx_states[i];
    }
  }
  return NULL;
}

static mcu_usart_rx_state_t* _mcu_usart_rx_allocate_state(void* usart_base) {
  for (uint8_t i = 0; i < sizeof(_rx_states) / sizeof(_rx_states[0]); i++) {
    if (_rx_states[i].usart_base == NULL) {
      _rx_states[i].usart_base = usart_base;
      return &_rx_states[i];
    }
  }
  return NULL;
}

void USART0_RX_IRQHandler(void) {
  _mcu_usart_rx_timeout_handler(USART0);
}

void EUSART0_RX_IRQHandler(void) {
  _mcu_eusart_rx_timeout_handler(EUSART0);
}

void EUSART1_RX_IRQHandler(void) {
  _mcu_eusart_rx_timeout_handler(EUSART1);
}
