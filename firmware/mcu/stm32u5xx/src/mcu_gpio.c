#include "mcu_gpio.h"

#include "mcu.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_exti.h"
#include "stm32u5xx_ll_gpio.h"
#include "stm32u5xx_ll_system.h"

#include <stdint.h>

// Lookup table for LL GPIO pin constants
static const uint32_t ll_gpio_pins[] = {
  LL_GPIO_PIN_0,  LL_GPIO_PIN_1,  LL_GPIO_PIN_2,  LL_GPIO_PIN_3,  LL_GPIO_PIN_4,  LL_GPIO_PIN_5,
  LL_GPIO_PIN_6,  LL_GPIO_PIN_7,  LL_GPIO_PIN_8,  LL_GPIO_PIN_9,  LL_GPIO_PIN_10, LL_GPIO_PIN_11,
  LL_GPIO_PIN_12, LL_GPIO_PIN_13, LL_GPIO_PIN_14, LL_GPIO_PIN_15,
};

// Masks for even/odd EXTI lines
#define EXTI_EVEN_LINES_MASK 0x5555U  // Lines 0,2,4,6,8,10,12,14
#define EXTI_ODD_LINES_MASK  0xAAAAU  // Lines 1,3,5,7,9,11,13,15

static uint16_t exti_enabled = 0;

// Enable clocks for all GPIO ports
void mcu_gpio_init(void) {
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOA);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOB);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOC);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOD);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOE);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOF);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOG);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOH);
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_GPIOI);

  // Enable SYSCFG clock early required for EXTI configuration
  LL_APB3_GRP1_EnableClock(LL_APB3_GRP1_PERIPH_SYSCFG);
}

void mcu_gpio_set(const mcu_gpio_config_t* gpio) {
  gpio->port->BSRR = (1U << gpio->pin);
}

void mcu_gpio_clear(const mcu_gpio_config_t* gpio) {
  gpio->port->BSRR = (1U << (gpio->pin + GPIO_BSRR_BR0_Pos));
}

uint32_t mcu_gpio_read(const mcu_gpio_config_t* gpio) {
  uint32_t pin_mask = (1U << gpio->pin);
  return (gpio->port->IDR & pin_mask) == pin_mask;
}

void mcu_gpio_output_set(const mcu_gpio_config_t* gpio, const bool set) {
  if (set) {
    mcu_gpio_set(gpio);
  } else {
    mcu_gpio_clear(gpio);
  }
}

// Configure GPIO pin with specified settings
// WARNING: Non-reentrant - do not call from different priority contexts
void mcu_gpio_configure(const mcu_gpio_config_t* gpio, const bool output_set) {
  // Set GPIO mode
  uint32_t pin_pos = gpio->pin * 2;
  uint32_t moder = gpio->port->MODER;
  moder &= ~(0x3U << pin_pos);
  moder |= (gpio->mode << pin_pos);
  gpio->port->MODER = moder;

  if (gpio->mode == MCU_GPIO_MODE_OUTPUT) {
    // Configure output speed
    gpio->port->OSPEEDR &= ~(0x3U << pin_pos);
    gpio->port->OSPEEDR |= (gpio->speed << pin_pos);

    // Set push-pull output (clear open-drain bit)
    gpio->port->OTYPER &= ~(1U << gpio->pin);
  }

  else if (gpio->mode == MCU_GPIO_MODE_ALTERNATE) {
    // Configure output speed for alternate function
    gpio->port->OSPEEDR &= ~(0x3U << pin_pos);
    gpio->port->OSPEEDR |= (gpio->speed << pin_pos);

    // Set alternate function (AFR[0] for pins 0-7, AFR[1] for pins 8-15)
    uint32_t afr_index = gpio->pin >> 3;
    uint32_t afr_pos = (gpio->pin & 0x7U) * 4;
    gpio->port->AFR[afr_index] &= ~(0xFU << afr_pos);
    gpio->port->AFR[afr_index] |= (gpio->af << afr_pos);
  }

  if (gpio->low_voltage) {
    gpio->port->HSLVR |= (1U << gpio->pin);
  } else {
    gpio->port->HSLVR &= ~(1U << gpio->pin);
  }

  // Configure pull-up/pull-down (except for analog mode)
  if (gpio->mode != MCU_GPIO_MODE_ANALOG) {
    gpio->port->PUPDR &= ~(0x3U << pin_pos);
    gpio->port->PUPDR |= (gpio->pupd << pin_pos);
  }

  if (gpio->mode == MCU_GPIO_MODE_OUTPUT) {
    if (output_set) {
      mcu_gpio_set(gpio);
    } else {
      mcu_gpio_clear(gpio);
    }
  }
}

void mcu_gpio_set_mode(const mcu_gpio_config_t* gpio, const mcu_gpio_mode_e mode,
                       const bool output_set) {
  if (output_set) {
    mcu_gpio_set(gpio);
  } else {
    mcu_gpio_clear(gpio);
  }

  LL_GPIO_SetPinMode(gpio->port, ll_gpio_pins[gpio->pin], mode);

  if (output_set) {
    mcu_gpio_set(gpio);
  } else {
    mcu_gpio_clear(gpio);
  }
}

// Configure external interrupt for GPIO pin
bool mcu_gpio_int_enable(const mcu_gpio_config_t* gpio, const bool rising, const bool falling,
                         uint32_t* int_num) {
  // STM32U5 EXTI: Each pin number (0-15) has dedicated line
  // Only one port can connect to each EXTI line
  if (gpio->pin >= 16) {
    return false;
  }

  // Check if EXTI line already in use
  if (exti_enabled & (1 << gpio->pin)) {
    return false;
  }

  // Track enabled interrupt
  exti_enabled |= (1 << gpio->pin);
  if (int_num) {
    *int_num = gpio->pin;
  }

  // Configure GPIO as input
  uint32_t pin_pos = gpio->pin * 2;
  uint32_t moder = gpio->port->MODER;
  moder &= ~(0x3U << pin_pos);
  moder |= (MCU_GPIO_MODE_INPUT << pin_pos);
  gpio->port->MODER = moder;

  // Set pull configuration
  gpio->port->PUPDR &= ~(0x3U << pin_pos);
  gpio->port->PUPDR |= (gpio->pupd << pin_pos);

  // Calculate port index: each GPIO port is offset by 0x400 from GPIOA
  // Port index = (port_address - GPIOA) / 0x400 = A:0, B:1, C:2, etc.
  uint32_t port_index = ((uint32_t)gpio->port - (uint32_t)GPIOA) >> 10;  // Divide by 0x400
  if (port_index > 8) {  // Valid ports are A-I (0-8)
    return false;
  }

  // EXTICR[0-3] maps pins: [0]=0-3, [1]=4-7, [2]=8-11, [3]=12-15
  // Each pin gets 8 bits in the register
  uint32_t reg_idx = gpio->pin >> 2;          // Which EXTICR register (0-3)
  uint32_t shift = (gpio->pin & 0x03U) * 8U;  // Bit position within register (0, 8, 16, 24)
  EXTI->EXTICR[reg_idx] = (EXTI->EXTICR[reg_idx] & ~(0xFFU << shift)) | (port_index << shift);

  // Clear pending interrupts
  uint32_t line_mask = (1U << gpio->pin);
  EXTI->RPR1 = line_mask;
  EXTI->FPR1 = line_mask;

  // Configure edge triggers
  if (rising) {
    EXTI->RTSR1 |= line_mask;
  } else {
    EXTI->RTSR1 &= ~line_mask;
  }

  if (falling) {
    EXTI->FTSR1 |= line_mask;
  } else {
    EXTI->FTSR1 &= ~line_mask;
  }

  // Enable interrupt
  EXTI->IMR1 |= line_mask;

  return true;
}

// Clear EXTI pending flags
void mcu_gpio_int_clear(const uint32_t flags) {
  EXTI->RPR1 = flags & 0xFFFF;
  EXTI->FPR1 = flags & 0xFFFF;
}

// Get pending interrupts for odd-numbered pins
uint32_t mcu_gpio_int_get_enabled_odd(void) {
  uint32_t pending = (EXTI->RPR1 | EXTI->FPR1) & 0xFFFF;
  return pending & EXTI_ODD_LINES_MASK;
}

// Get pending interrupts for even-numbered pins
uint32_t mcu_gpio_int_get_enabled_even(void) {
  uint32_t pending = (EXTI->RPR1 | EXTI->FPR1) & 0xFFFF;
  return pending & EXTI_EVEN_LINES_MASK;
}
