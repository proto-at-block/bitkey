#include "mcu_usart.h"

#include "assert.h"
#include "mcu_dma.h"
#include "mcu_gpio.h"
#include "mcu_nvic.h"
#include "mcu_nvic_impl.h"
#include "mcu_usart_impl.h"
#include "mcu_usart_rx.h"
#include "mcu_usart_tx.h"
#include "platform.h"
// stm32 ll
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_dma.h"
#include "stm32u5xx_ll_gpio.h"
#include "stm32u5xx_ll_rcc.h"
#include "stm32u5xx_ll_usart.h"
#include "stm32u5xx_ll_utils.h"

#include <stdbool.h>
#include <stddef.h>
#include <string.h>

typedef struct {
  void* usart_base;
  bool initialized;
  mcu_usart_tx_state_t tx_state;
  mcu_usart_rx_state_t rx_state;
} uart_instance_t;

static uart_instance_t _uart_instances[PLATFORM_CFG_MCU_UART_CNT] = {0};

static uart_instance_t* get_free_uart_instance(void);
static uart_instance_t* get_uart_instance(void* usart);
static void mcu_usart_irq_handler(void* usart);
static IRQn_Type get_usart_irqn(USART_TypeDef* usart);
static void usart_enable_clock(USART_TypeDef* usart);
static uint32_t usart_get_kernel_clk_hz(USART_TypeDef* usart);

void mcu_usart_init(mcu_usart_config_t* config) {
  if (config == NULL || config->usart.base == NULL) {
    return;
  }

  uart_instance_t* instance = get_uart_instance(config->usart.base);
  if (instance != NULL) {
    return; /* Already initialized */
  }

  instance = get_free_uart_instance();
  if (instance == NULL) {
    return; /* No free slots */
  }

  instance->usart_base = config->usart.base;

  /* Configure GPIO pins */
  mcu_gpio_configure(&config->tx, false);
  mcu_gpio_configure(&config->rx, false);

  USART_TypeDef* usart = (USART_TypeDef*)config->usart.base;

  /* Enable peripheral clock */
  usart_enable_clock(usart);

  /* Disable USART before reconfig */
  LL_USART_Disable(usart);
  LL_USART_DisableDirectionRx(usart);
  LL_USART_DisableDirectionTx(usart);

  /* Core async configuration: 8-N-1, no flow control, TX+RX, oversampling x16 */
  LL_USART_SetDataWidth(usart, LL_USART_DATAWIDTH_8B);
  LL_USART_SetParity(usart, LL_USART_PARITY_NONE);
  LL_USART_SetStopBitsLength(usart, LL_USART_STOPBITS_1);
  LL_USART_SetHWFlowCtrl(usart, LL_USART_HWCONTROL_NONE);
  LL_USART_SetOverSampling(usart, LL_USART_OVERSAMPLING_16);

  /* U5 has a programmable prescaler */
  LL_USART_SetPrescaler(usart, LL_USART_PRESCALER_DIV1);

  /* Baudrate */
  uint32_t kernel_hz = usart_get_kernel_clk_hz(usart);
  LL_USART_SetBaudRate(usart, kernel_hz, LL_USART_PRESCALER_DIV1, LL_USART_OVERSAMPLING_16,
                       config->baudrate);

  /* Enable USART */
  LL_USART_Enable(usart);

  /* Enable transmitter and receiver */
  LL_USART_SetTransferDirection(usart, LL_USART_DIRECTION_TX_RX);

  /* Initialize DMA system */
  mcu_dma_init(MCU_DMA_IRQ_PRIORITY);

  /* Initialize DMA for TX and RX */
  mcu_usart_tx_init_dma(&instance->tx_state, config);
  mcu_usart_rx_init_dma(&instance->rx_state, config);

  /* Enable UART DMA requests */
  LL_USART_EnableDMAReq_RX(usart);
  LL_USART_EnableDMAReq_TX(usart);

  /* Enable UART IDLE line interrupt */
  LL_USART_ClearFlag_IDLE(usart);
  LL_USART_EnableIT_IDLE(usart);

  /* Enable USART interrupts */
  IRQn_Type irqn = get_usart_irqn(usart);
  if (irqn != (IRQn_Type)-1) {
    mcu_nvic_set_priority(irqn, MCU_NVIC_DEFAULT_IRQ_PRIORITY);
    mcu_nvic_enable_irq(irqn);
  }

  instance->initialized = true;
}

uint32_t mcu_usart_read_timeout(mcu_usart_config_t* config, uint8_t* data, uint32_t len,
                                uint32_t timeout_ms) {
  if ((config == NULL) || (data == NULL) || (len == 0)) {
    return 0;
  }

  uart_instance_t* instance = get_uart_instance(config->usart.base);
  if (instance == NULL) {
    return 0;
  }

  /* Use DMA-based read */
  return mcu_usart_rx_read(&instance->rx_state, data, len, timeout_ms);
}

uint32_t mcu_usart_write(mcu_usart_config_t* config, const uint8_t* data, uint32_t len) {
  if (config == NULL) {
    return 0;
  }

  uart_instance_t* instance = get_uart_instance(config->usart.base);
  if (instance == NULL) {
    return 0;
  }

  return mcu_usart_tx_write(&instance->tx_state, data, len);
}

/* USART IRQ handlers - these override the weak symbols in mcu_startup.c */
void USART1_IRQHandler(void) {
  mcu_usart_irq_handler(USART1);
}

void USART2_IRQHandler(void) {
  mcu_usart_irq_handler(USART2);
}

void USART3_IRQHandler(void) {
  mcu_usart_irq_handler(USART3);
}

void UART4_IRQHandler(void) {
  mcu_usart_irq_handler(UART4);
}

void UART5_IRQHandler(void) {
  mcu_usart_irq_handler(UART5);
}

void LPUART1_IRQHandler(void) {
  mcu_usart_irq_handler(LPUART1);
}

static uart_instance_t* get_free_uart_instance(void) {
  for (uint8_t i = 0; i < PLATFORM_CFG_MCU_UART_CNT; i++) {
    if (!_uart_instances[i].initialized) {
      return &_uart_instances[i];
    }
  }
  return NULL;
}

static uart_instance_t* get_uart_instance(void* usart) {
  for (uint8_t i = 0; i < PLATFORM_CFG_MCU_UART_CNT; i++) {
    if (_uart_instances[i].initialized && (_uart_instances[i].usart_base == usart)) {
      return &_uart_instances[i];
    }
  }
  return NULL;
}

static void usart_enable_clock(USART_TypeDef* usart) {
#if defined(USART1)
  if (usart == USART1) {
    MCU_USART1_CLOCK_ENABLE();
    return;
  }
#endif
#if defined(USART2)
  if (usart == USART2) {
    MCU_USART2_CLOCK_ENABLE();
    return;
  }
#endif
#if defined(USART3)
  if (usart == USART3) {
    MCU_USART3_CLOCK_ENABLE();
    return;
  }
#endif
#if defined(UART4)
  if (usart == UART4) {
    MCU_UART4_CLOCK_ENABLE();
    return;
  }
#endif
#if defined(UART5)
  if (usart == UART5) {
    MCU_UART5_CLOCK_ENABLE();
    return;
  }
#endif
#if defined(LPUART1)
  if (usart == LPUART1) {
    MCU_LPUART1_CLOCK_ENABLE();
    return;
  }
#endif
}

static uint32_t usart_get_kernel_clk_hz(USART_TypeDef* usart) {
  /* Get the USART kernel clock frequency */
  LL_RCC_ClocksTypeDef clocks;
  LL_RCC_GetSystemClocksFreq(&clocks);

#if defined(USART1)
  if (usart == USART1) {
    /* USART1 is on APB2 */
    return clocks.PCLK2_Frequency;
  }
#endif
#if defined(USART2) || defined(USART3) || defined(UART4) || defined(UART5)
  if ((usart == USART2) || (usart == USART3) || (usart == UART4) || (usart == UART5)) {
    /* USART2/3/UART4/5 are on APB1 */
    return clocks.PCLK1_Frequency;
  }
#endif
#if defined(LPUART1)
  if (usart == LPUART1) {
    /* LPUART1 is on APB3 */
    return clocks.PCLK3_Frequency;
  }
#endif

  ASSERT(false);
  return 0;
}

static void mcu_usart_irq_handler(void* usart) {
  uart_instance_t* instance = get_uart_instance(usart);
  if (instance != NULL) {
    mcu_usart_rx_idle_irq_handler(&instance->rx_state);
  }
}

static IRQn_Type get_usart_irqn(USART_TypeDef* usart) {
#if defined(USART1)
  if (usart == USART1)
    return USART1_IRQn;
#endif
#if defined(USART2)
  if (usart == USART2)
    return USART2_IRQn;
#endif
#if defined(USART3)
  if (usart == USART3)
    return USART3_IRQn;
#endif
#if defined(UART4)
  if (usart == UART4)
    return UART4_IRQn;
#endif
#if defined(UART5)
  if (usart == UART5)
    return UART5_IRQn;
#endif
#if defined(LPUART1)
  if (usart == LPUART1)
    return LPUART1_IRQn;
#endif
  return (IRQn_Type)-1;
}
