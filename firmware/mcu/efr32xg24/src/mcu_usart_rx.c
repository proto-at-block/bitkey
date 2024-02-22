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
  uint8_t buffer[MCU_USART_RX_BUFFER_LEN];
  uint32_t rd; /* Next byte to read out of the buffer */
  uint32_t wr; /* Next buffer write address */

  /* EFR32 LDMA */
  struct {
    uint32_t channel;               /* Channel to use for LDMA transfers */
    uint32_t active_link_desc;      /* Currently active LDMA link descriptor */
    LDMA_Descriptor_t link_desc[2]; /* Table of LDMA link descriptors */
    LDMA_TransferCfg_t config;      /* LDMA config */
  } ldma;

  /* RTOS */
  rtos_mutex_t access;
  rtos_semaphore_t timeout;
} mcu_usart_rx_state_t;

static mcu_usart_rx_state_t _state = {0};

/* RX LDMA */
static bool _rx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param);

void mcu_usart_rx_init_dma(mcu_usart_config_t* config) {
  /* Buffer is empty on init */
  _state.rd = _state.wr = 0;

  rtos_mutex_create(&_state.access);
  rtos_semaphore_create(&_state.timeout);

  // Enable RX interrupts
  if (config->rx_irq_timeout) {
    // Set RX Timeout feature to generate an interrupt after 256 baud times
    USART0->TIMECMP1 = USART_TIMECMP1_TSTART_RXEOF | USART_TIMECMP1_TSTOP_RXACT | 0xFF;
    USART_IntEnable(USART0, USART_IEN_TCMP1);
    NVIC_ClearPendingIRQ(USART0_RX_IRQn);
    NVIC_EnableIRQ(USART0_RX_IRQn);
  }

  // TODO: handle failed dma allocation
  mcu_dma_allocate_channel(&_state.ldma.channel, _rx_ldma_isr);

  /* Setting up RX LDMA */
  _state.ldma.config =
    (LDMA_TransferCfg_t)LDMA_TRANSFER_CFG_PERIPHERAL(ldmaPeripheralSignal_USART0_RXDATAV);

  // Ping-pong buffers used to store RX data into circular buffer
  _state.ldma.link_desc[0] = (LDMA_Descriptor_t)LDMA_DESCRIPTOR_LINKREL_P2M_BYTE(
    &(USART0->RXDATA), _state.buffer, MCU_USART_RX_BUFFER_LEN / 2, 1);
  _state.ldma.link_desc[1] = (LDMA_Descriptor_t)LDMA_DESCRIPTOR_LINKREL_P2M_BYTE(
    &(USART0->RXDATA), _state.buffer + (MCU_USART_RX_BUFFER_LEN / 2), MCU_USART_RX_BUFFER_LEN / 2,
    -1);

  LDMA_StartTransfer(_state.ldma.channel, &_state.ldma.config, &(_state.ldma.link_desc[0]));
}

uint32_t mcu_usart_read_timeout(uint8_t* data, uint32_t len, uint32_t timeout_ms) {
  uint32_t n_available;

  rtos_mutex_lock(&_state.access);

  do {
    rtos_thread_enter_critical();
    n_available = mcu_usart_rx_available();
    rtos_thread_exit_critical();

  } while ((n_available < len) && (rtos_semaphore_take(&_state.timeout, timeout_ms)));

  /* Limit len to available bytes */
  uint32_t n = (len > n_available) ? n_available : len;

  if (n == 0) {
    rtos_mutex_unlock(&_state.access);
    return n;
  }

  /* Handle buffer wrapping */
  if (_state.rd + n < MCU_USART_RX_BUFFER_LEN) {
    memcpy(data, &(_state.buffer[_state.rd]), n);
  } else {
    memcpy(&data[0], &(_state.buffer[_state.rd]), MCU_USART_RX_BUFFER_LEN - _state.rd);
    memcpy(&data[MCU_USART_RX_BUFFER_LEN - _state.rd], &(_state.buffer[0]),
           n - MCU_USART_RX_BUFFER_LEN + _state.rd);
  }

  // TODO: RTOS Critical Enter
  rtos_thread_enter_critical();
  _state.rd = (_state.rd + n) % MCU_USART_RX_BUFFER_LEN;
  rtos_thread_exit_critical();

  rtos_mutex_unlock(&_state.access);

  return n;
}

uint32_t mcu_usart_rx_available(void) {
  uint32_t available = 0;
  if (_state.wr >= _state.rd) {
    available = (_state.wr - _state.rd);
  } else {
    available = (MCU_USART_RX_BUFFER_LEN - _state.rd) + _state.wr;
  }

  return available;
}

static bool _rx_ldma_isr(uint32_t channel, uint32_t sequence_num, void* user_param) {
  (void)channel;
  (void)sequence_num;
  (void)user_param;

  /* Update the write index */
  // TODO: Handle overflow
  if (_state.ldma.active_link_desc == 0) {
    _state.ldma.active_link_desc = 1;
    _state.wr = MCU_USART_RX_BUFFER_LEN / 2;
  } else {
    _state.ldma.active_link_desc = 0;
    _state.wr = 0;
  }

  rtos_semaphore_give_from_isr(&_state.timeout);

  return true;
}

static void rx_timeout_handler(USART_TypeDef* usart) {
  // Clear interrupt flags
  uint32_t flags = USART_IntGet(usart);
  USART_IntClear(usart, flags);

  // Update stop index based on the number of LDMA transfers that occurred
  if (flags & USART_IF_TCMP1) {
    const uint32_t xfer_count =
      ((LDMA->CH[_state.ldma.channel].CTRL & _LDMA_CH_CTRL_XFERCNT_MASK) >>
       _LDMA_CH_CTRL_XFERCNT_SHIFT);

    if (_state.ldma.active_link_desc == 0) {
      _state.wr = MCU_USART_RX_BUFFER_LEN / 2 - xfer_count - 1;
    } else {
      _state.wr = MCU_USART_RX_BUFFER_LEN - xfer_count - 1;
    }

    rtos_semaphore_give_from_isr(&_state.timeout);
  }
}

void USART0_RX_IRQHandler(void) {
  rx_timeout_handler(USART0);
}
