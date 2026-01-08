#include "mcu_usart.h"

#include "assert.h"
#include "mcu_dma.h"
#include "mcu_gpio.h"
#include "mcu_usart_rx.h"
#include "mcu_usart_tx.h"

#include "em_cmu.h"
#include "em_eusart.h"
#include "em_ldma.h"
#include "em_usart.h"

#include <stddef.h>
#include <string.h>

/* Structure rationale:
 *  mcu_usart.c     - main entry point for hal_* layer
 *  mcu_usart_tx.c  - transmit handling, called by mcu_usart functions, thread-safe
 *  mcu_usart_rx.c  - receive handling, called by mcu_usart functions, thread-safe
 */

void mcu_usart_init(mcu_usart_config_t* config) {
  // Configure TX and RX GPIOs
  mcu_gpio_init();
  mcu_gpio_set_mode(&config->tx, MCU_GPIO_MODE_PUSH_PULL, true);
  mcu_gpio_set_mode(&config->rx, MCU_GPIO_MODE_INPUT, false);

  CMU_ClockEnable(config->clock, true);
  if (config->usart.base == EUSART0) {
    // EUSART is a special case with its own separate clock tree
    // gated. To use ESUART for high speed communication, we must set either
    // the HFRCO or EM01GRPCCLK.
    CMU_ClockSelectSet(config->clock, cmuSelect_EM01GRPCCLK);
  }

  const bool is_eusart = (config->usart.base == EUSART0) || (config->usart.base == EUSART1);
  const bool is_usart = (config->usart.base == USART0);

  if (is_usart) {
    // Configure USART for basic sync operation
    const USART_InitAsync_TypeDef init = {
      .enable = usartDisable,
      .baudrate = config->baudrate,
      .databits = usartDatabits8,
      .parity = usartNoParity,
      .stopbits = usartStopbits1,
    };
    USART_InitAsync(config->usart.base, &init);

    // Enable pins at correct USART/USART location
    GPIO->USARTROUTE[config->usart.index].ROUTEEN =
      GPIO_USART_ROUTEEN_TXPEN | GPIO_USART_ROUTEEN_RXPEN;
    GPIO->USARTROUTE[config->usart.index].TXROUTE =
      (config->tx.port << _GPIO_USART_TXROUTE_PORT_SHIFT) |
      (config->tx.pin << _GPIO_USART_TXROUTE_PIN_SHIFT);
    GPIO->USARTROUTE[config->usart.index].RXROUTE =
      (config->rx.port << _GPIO_USART_RXROUTE_PORT_SHIFT) |
      (config->rx.pin << _GPIO_USART_RXROUTE_PIN_SHIFT);

    // Finally enable it
    USART_Enable(config->usart.base, usartEnable);
  } else if (is_eusart) {
    // Configure EUSART for basic operation
    const EUSART_UartInit_TypeDef init = {
      .enable = eusartDisable,
      .refFreq = 0,
      .baudrate = config->baudrate,
      .oversampling = eusartOVS16,
      .databits = eusartDataBits8,
      .parity = eusartNoParity,
      .stopbits = eusartStopbits1,
      .majorityVote = eusartMajorityVoteDisable,
      .loopbackEnable = eusartLoopbackDisable,
      .advancedSettings = NULL,
    };

    // Note: Assuming we are using a high frequency clock
    EUSART_UartInitHf(config->usart.base, &init);

    // Configure RX timeout BEFORE enabling (can only modify CFG1 when disabled)
    if (config->rx_irq_timeout) {
      EUSART_TypeDef* eusart = config->usart.base;
      eusart->CFG1 =
        (eusart->CFG1 & ~_EUSART_CFG1_RXTIMEOUT_MASK) | EUSART_CFG1_RXTIMEOUT_SEVENFRAMES;
    }

    // Enable pins at correct EUSART/EUSART location
    GPIO->EUSARTROUTE[config->usart.index].ROUTEEN =
      GPIO_EUSART_ROUTEEN_TXPEN | GPIO_EUSART_ROUTEEN_RXPEN;
    GPIO->EUSARTROUTE[config->usart.index].TXROUTE =
      (config->tx.port << _GPIO_EUSART_TXROUTE_PORT_SHIFT) |
      (config->tx.pin << _GPIO_EUSART_TXROUTE_PIN_SHIFT);
    GPIO->EUSARTROUTE[config->usart.index].RXROUTE =
      (config->rx.port << _GPIO_EUSART_RXROUTE_PORT_SHIFT) |
      (config->rx.pin << _GPIO_EUSART_RXROUTE_PIN_SHIFT);

    EUSART_Enable(config->usart.base, eusartEnable);
  } else {
    ASSERT(false);
  }

  mcu_err_t err = mcu_dma_init(MCU_DMA_IRQ_PRIORITY);
  ASSERT((err == MCU_ERROR_OK) || (err == MCU_ERROR_ALREADY_INITIALISED));

  err = mcu_usart_tx_init_dma(config);
  ASSERT(err == MCU_ERROR_OK);

  err = mcu_usart_rx_init_dma(config);
  ASSERT(err == MCU_ERROR_OK);

  NVIC_ClearPendingIRQ(LDMA_IRQn);
  NVIC_EnableIRQ(LDMA_IRQn);
}

uint32_t mcu_usart_read_timeout(mcu_usart_config_t* config, uint8_t* data, uint32_t len,
                                uint32_t timeout_ms) {
  if (config == NULL) {
    return 0;
  }
  return mcu_usart_rx_read(config, data, len, timeout_ms);
}

uint32_t mcu_usart_write(mcu_usart_config_t* config, const uint8_t* data, uint32_t len) {
  if (config == NULL) {
    return 0;
  }
  return mcu_usart_tx_write(config, data, len);
}
