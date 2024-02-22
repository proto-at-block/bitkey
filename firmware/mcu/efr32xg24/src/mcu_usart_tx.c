#include "attributes.h"
#include "mcu.h"
#include "mcu_dma.h"
#include "mcu_usart.h"
#include "rtos.h"

#include "em_cmu.h"
#include "em_ldma.h"
#include "em_usart.h"

#include <stddef.h>
#include <string.h>

typedef struct {
  /* Circular Buffer */
  uint8_t buffer[MCU_USART_TX_BUFFER_LEN];
  uint32_t rd;       /* Next byte to read out of the buffer */
  uint32_t wr;       /* Next buffer write address */
  uint32_t xfer_len; /* Number of bytes to DMA from buffer to TXDATA */

  /* EFR32 LDMA */
  struct {
    uint32_t channel;          /* Channel to use for LDMA transfers */
    bool busy;                 /* True when an LDMA transfer is in progress */
    LDMA_Descriptor_t desc;    /* LDMA  descriptor */
    LDMA_TransferCfg_t config; /* LDMA config */
  } ldma;

  /* RTOS */
  rtos_mutex_t access;
} mcu_usart_tx_state_t;

static mcu_usart_tx_state_t PERIPHERALS_DATA _state = {0};

static uint32_t _tx_n_free(void);
static bool _tx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param);

void mcu_usart_tx_init_dma(mcu_usart_config_t* UNUSED(config)) {
  rtos_mutex_create(&_state.access);

  mcu_dma_init(MCU_DMA_IRQ_PRIORITY);

  // TODO: check return values
  mcu_dma_allocate_channel(&_state.ldma.channel, _tx_ldma_isr);

  /* Setting up TX LDMA */
  _state.ldma.config =
    (LDMA_TransferCfg_t)LDMA_TRANSFER_CFG_PERIPHERAL(ldmaPeripheralSignal_USART0_TXBL);
}

SYSCALL uint32_t mcu_usart_tx_write(void* usart, const uint8_t* data, uint32_t len) {
  (void)usart;

  /* Return if there is not data to write */
  if (len == 0) {
    return 0;
  }

  rtos_mutex_lock(&_state.access);
  int was_priv = rtos_thread_is_privileged();
  if (!was_priv) {
    rtos_thread_raise_privilege();
  }
  rtos_thread_enter_critical();

  /* Check if the write would overflow the buffer, is so only write up to the
   * end of the buffer */
  const uint32_t available = _tx_n_free();
  if (len > available) {
    len = available;
  }

  const uint32_t old_wr = _state.wr;
  _state.wr = (_state.wr + len) % MCU_USART_TX_BUFFER_LEN;

  /* Handle buffer wrapping */
  if (old_wr + len <= MCU_USART_TX_BUFFER_LEN) {
    memcpy(&(_state.buffer[old_wr]), data, len);
  } else {
    memcpy(&(_state.buffer[old_wr]), &data[0], MCU_USART_TX_BUFFER_LEN - old_wr);
    memcpy(&(_state.buffer[0]), &data[MCU_USART_TX_BUFFER_LEN - old_wr],
           len - (MCU_USART_TX_BUFFER_LEN - old_wr));
  }

  /* Check if there is an active LDMA transfer or the LDMA ISR is waiting to be
   * serviced */
  // TODO: Can the LDMA registers be used in place of ldma_busy?
  const bool pending = (uint32_t)LDMA_IntGet() & (1 << _state.ldma.channel);
  if (!pending && !_state.ldma.busy) {
    tx_ldma_schedule();
  }

  rtos_thread_exit_critical();
  if (!was_priv) {
    rtos_thread_reset_privilege();
  }
  rtos_mutex_unlock(&_state.access);

  return len;
}

void tx_ldma_schedule(void) {
  /* Save the transfer length to increment the read index after the LDMA transfer
   * is complete */
  if (_state.rd < _state.wr) {
    /* LDMA up until the write pointer */
    _state.xfer_len = _state.wr - _state.rd;
  } else {
    /* LDMA up until the end of the buffer */
    _state.xfer_len = MCU_USART_TX_BUFFER_LEN - _state.rd;
  }

  /* Set the starting memory address and number of bytes for the LDMA transfer */
  _state.ldma.desc = (LDMA_Descriptor_t)LDMA_DESCRIPTOR_SINGLE_M2P_BYTE(
    &(_state.buffer[_state.rd]), &(USART0->TXDATA), _state.xfer_len);

  // TODO: Clear the transfer complete flag??
  LDMA_IntClear(1 << _state.ldma.channel);

  /* Start the LDMA transfer */
  _state.ldma.busy = true;
  LDMA_StartTransfer(_state.ldma.channel, &_state.ldma.config, &_state.ldma.desc);
}

static bool _tx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param) {
  (void)channel;
  (void)sequence_num;
  (void)user_param;

  _state.rd = (_state.rd + _state.xfer_len) % MCU_USART_TX_BUFFER_LEN;
  _state.ldma.busy = false;

  /* Buffer not empty */
  if (_state.wr != _state.rd) {
    tx_ldma_schedule();
  }

  return true;
}

static uint32_t _tx_n_free(void) {
  /* Return number of bytes free in the buffer with handling for wrapping */
  if (_state.wr >= _state.rd)
    return MCU_USART_TX_BUFFER_LEN - 1 - (_state.wr - _state.rd);
  else
    return (_state.rd - _state.wr) - 1;
}
