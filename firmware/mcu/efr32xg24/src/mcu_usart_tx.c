#include "mcu_usart_tx.h"

#include "attributes.h"
#include "mcu.h"
#include "mcu_dma.h"
#include "mcu_dma_impl.h"
#include "mcu_usart.h"
#include "mcu_usart_tx.h"
#include "platform.h"
#include "rtos.h"

#include "em_cmu.h"
#include "em_ldma.h"
#include "em_usart.h"

#include <stddef.h>
#include <string.h>

typedef struct {
  /* Base address of USART the state belongs to. */
  void* usart_base;

  /* Circular Buffer */
  uint8_t buffer[MCU_USART_TX_BUFFER_LEN];
  uint32_t rd;       /* Next byte to read out of the buffer */
  uint32_t wr;       /* Next buffer write address */
  uint32_t xfer_len; /* Number of bytes to DMA from buffer to TXDATA */

  /* EFR32 LDMA */
  struct {
    uint32_t channel; /* Channel to use for LDMA transfers */
    uint32_t signal;  /* LDMA signal */
    bool busy;        /* True when an LDMA transfer is in progress */
  } ldma;

  /* RTOS */
  rtos_mutex_t access;
} mcu_usart_tx_state_t;

static mcu_usart_tx_state_t PERIPHERALS_DATA _tx_states[PLATFORM_CFG_MCU_UART_CNT] = {0};

static uint32_t _tx_n_free(mcu_usart_tx_state_t* state);
static bool _tx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param);
static mcu_usart_tx_state_t* _mcu_usart_tx_get_state(void* usart_base);
static mcu_usart_tx_state_t* _mcu_usart_tx_allocate_state(void* usart_base);
static void mcu_usart_tx_ldma_schedule(mcu_usart_tx_state_t* state);

mcu_err_t mcu_usart_tx_init_dma(mcu_usart_config_t* config) {
  if ((config == NULL) || (config->usart.base == NULL)) {
    return MCU_ERROR_PARAMETER;
  }

  mcu_usart_tx_state_t* state = _mcu_usart_tx_get_state(config->usart.base);
  if (state == NULL) {
    state = _mcu_usart_tx_allocate_state(config->usart.base);
    if (state == NULL) {
      return MCU_ERROR_OUT_OF_MEM;
    }
  }

  state->usart_base = config->usart.base;
  if (state->usart_base == (void*)USART0) {
    state->ldma.signal = MCU_DMA_SIGNAL_USART0_TXBL;
  } else if (state->usart_base == (void*)EUSART0) {
    state->ldma.signal = MCU_DMA_SIGNAL_EUSART0_TXBL;
  } else if (state->usart_base == (void*)EUSART1) {
    state->ldma.signal = MCU_DMA_SIGNAL_EUSART1_TXBL;
  } else {
    return MCU_ERROR_PARAMETER;
  }

  rtos_mutex_create(&state->access);

  mcu_err_t err = mcu_dma_init(MCU_DMA_IRQ_PRIORITY);
  if ((err != MCU_ERROR_OK) && (err != MCU_ERROR_ALREADY_INITIALISED)) {
    return err;
  }

  err = mcu_dma_allocate_channel(&state->ldma.channel);
  if (err != MCU_ERROR_OK) {
    return err;
  }

  err = mcu_dma_channel_configure(state->ldma.channel, state);
  if (err != MCU_ERROR_OK) {
    return err;
  }

  return MCU_ERROR_OK;
}

SYSCALL uint32_t mcu_usart_tx_write(mcu_usart_config_t* config, const uint8_t* data, uint32_t len) {
  mcu_usart_tx_state_t* state = _mcu_usart_tx_get_state(config->usart.base);
  if (state == NULL) {
    return 0;
  }

  /* Return if there is not data to write */
  if (len == 0) {
    return 0;
  }

  rtos_mutex_lock(&state->access);
  int was_priv = rtos_thread_is_privileged();
  if (!was_priv) {
    rtos_thread_raise_privilege();
  }
  rtos_thread_enter_critical();

  /* Check if the write would overflow the buffer, is so only write up to the
   * end of the buffer */
  const uint32_t available = _tx_n_free(state);
  if (len > available) {
    len = available;
  }

  const uint32_t old_wr = state->wr;
  state->wr = (state->wr + len) % MCU_USART_TX_BUFFER_LEN;

  /* Handle buffer wrapping */
  if (old_wr + len <= MCU_USART_TX_BUFFER_LEN) {
    memcpy(&(state->buffer[old_wr]), data, len);
  } else {
    memcpy(&(state->buffer[old_wr]), &data[0], MCU_USART_TX_BUFFER_LEN - old_wr);
    memcpy(&(state->buffer[0]), &data[MCU_USART_TX_BUFFER_LEN - old_wr],
           len - (MCU_USART_TX_BUFFER_LEN - old_wr));
  }

  /* Check if there is an active LDMA transfer or the LDMA ISR is waiting to be
   * serviced */
  // TODO: Can the LDMA registers be used in place of ldma_busy?
  const bool pending = (uint32_t)LDMA_IntGet() & (1 << state->ldma.channel);
  if (!pending && !state->ldma.busy) {
    mcu_usart_tx_ldma_schedule(state);
  }

  rtos_thread_exit_critical();
  if (!was_priv) {
    rtos_thread_reset_privilege();
  }
  rtos_mutex_unlock(&state->access);

  return len;
}

void mcu_usart_tx_ldma_schedule(mcu_usart_tx_state_t* state) {
  ASSERT(state != NULL);

  /* Save the transfer length to increment the read index after the LDMA transfer
   * is complete */
  if (state->rd < state->wr) {
    /* LDMA up until the write pointer */
    state->xfer_len = state->wr - state->rd;
  } else {
    /* LDMA up until the end of the buffer */
    state->xfer_len = MCU_USART_TX_BUFFER_LEN - state->rd;
  }

  void* dst;
  if (state->usart_base == (void*)USART0) {
    dst = (void*)&(USART0->TXDATA);
  } else if (state->usart_base == (void*)EUSART0) {
    dst = (void*)&(EUSART0->TXDATA);
  } else if (state->usart_base == (void*)EUSART1) {
    dst = (void*)&(EUSART1->TXDATA);
  } else {
    ASSERT(false);
  }

  state->ldma.busy = true;
  const mcu_err_t err = mcu_dma_memory_peripheral(
    state->ldma.channel, state->ldma.signal, dst, &(state->buffer[state->rd]), true,
    state->xfer_len, MCU_DMA_SIZE_1_BYTE, _tx_ldma_isr, state);
  ASSERT(err == MCU_ERROR_OK);
}

static bool _tx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param) {
  (void)channel;
  (void)sequence_num;

  mcu_usart_tx_state_t* state = (mcu_usart_tx_state_t*)user_param;
  ASSERT(state != NULL);

  state->rd = (state->rd + state->xfer_len) % MCU_USART_TX_BUFFER_LEN;
  state->ldma.busy = false;

  /* Buffer not empty */
  if (state->wr != state->rd) {
    mcu_usart_tx_ldma_schedule(state);
  }

  return true;
}

static uint32_t _tx_n_free(mcu_usart_tx_state_t* state) {
  /* Return number of bytes free in the buffer with handling for wrapping */
  if (state->wr >= state->rd)
    return MCU_USART_TX_BUFFER_LEN - 1 - (state->wr - state->rd);
  else
    return (state->rd - state->wr) - 1;
}

static mcu_usart_tx_state_t* _mcu_usart_tx_get_state(void* usart_base) {
  for (uint8_t i = 0; i < sizeof(_tx_states) / sizeof(_tx_states[0]); i++) {
    if (_tx_states[i].usart_base == usart_base) {
      return &_tx_states[i];
    }
  }
  return NULL;
}

static mcu_usart_tx_state_t* _mcu_usart_tx_allocate_state(void* usart_base) {
  for (uint8_t i = 0; i < sizeof(_tx_states) / sizeof(_tx_states[0]); i++) {
    if (_tx_states[i].usart_base == NULL) {
      _tx_states[i].usart_base = usart_base;
      return &_tx_states[i];
    }
  }
  return NULL;
}
