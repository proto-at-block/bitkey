#include "mcu_usart.h"

#include "mcu_dma.h"
#include "mcu_gpio.h"

#include "em_cmu.h"
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
  CMU_ClockEnable(cmuClock_GPIO, true);
  mcu_gpio_set_mode(&config->tx, MCU_GPIO_MODE_PUSH_PULL, true);
  mcu_gpio_set_mode(&config->rx, MCU_GPIO_MODE_INPUT, false);

  CMU_ClockEnable(config->clock, true);

  // Configure USART for basic sync operation
  USART_InitAsync_TypeDef init = {
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

  mcu_dma_init(MCU_DMA_IRQ_PRIORITY);
  mcu_usart_tx_init_dma(config);
  mcu_usart_rx_init_dma(config);
  NVIC_ClearPendingIRQ(LDMA_IRQn);
  NVIC_EnableIRQ(LDMA_IRQn);
}
