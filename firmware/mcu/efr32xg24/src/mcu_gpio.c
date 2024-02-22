#include "mcu_gpio.h"

#include "attributes.h"
#include "mcu.h"
#include "mcu_impl.h"

#include "em_cmu.h"
#include "em_device.h"

#include <stdint.h>

#define _GPIOINT_IF_EVEN_MASK ((_GPIO_IF_MASK)&0x55555555UL)
#define _GPIOINT_IF_ODD_MASK  ((_GPIO_IF_MASK)&0xAAAAAAAAUL)

static uint16_t PERIPHERALS_DATA exti_enabled = {0};

void mcu_gpio_init(void) {
  CMU_ClockEnable(cmuClock_GPIO, true);
}

void mcu_gpio_set(const mcu_gpio_config_t* gpio) {
  GPIO->P_SET[gpio->port].DOUT = 1UL << gpio->pin;
}

void mcu_gpio_clear(const mcu_gpio_config_t* gpio) {
  GPIO->P_CLR[gpio->port].DOUT = 1UL << gpio->pin;
}

uint32_t mcu_gpio_read(const mcu_gpio_config_t* gpio) {
  return reg_read_bit(&GPIO->P[gpio->port].DIN, gpio->pin);
}

void mcu_gpio_output_set(const mcu_gpio_config_t* gpio, const bool set) {
  if (set) {
    mcu_gpio_set(gpio);
  } else {
    mcu_gpio_clear(gpio);
  }
}

void mcu_gpio_configure(const mcu_gpio_config_t* gpio, const bool output_set) {
  mcu_gpio_set_mode(gpio, gpio->mode, output_set);
}

void mcu_gpio_set_mode(const mcu_gpio_config_t* gpio, mcu_gpio_mode_e mode, const bool output_set) {
  // Implementation based on `em_gpio.c`, including comments.

  /* If disabling a pin, do not modify DOUT to reduce the chance of */
  /* a glitch/spike (may not be sufficient precaution in all use cases). */
  if (mode != MCU_GPIO_MODE_DISABLED) {
    if (output_set) {
      mcu_gpio_set(gpio);
    } else {
      mcu_gpio_clear(gpio);
    }
  }

  /* There are two registers controlling the pins for each port. The MODEL
   * register controls pins 0-7 and MODEH controls pins 8-15. */
  if (gpio->pin < 8) {
    // Cast parameter [mode] to 32 bits to fix C99 Undefined Behavior (see SEI
    // CERT C INT34-C) Compiler assigned 8 bits for enum. Same thing for other
    // branch.
    reg_masked_write(&(GPIO->P[gpio->port].MODEL), 0xFu << (gpio->pin * 4),
                     (uint32_t)mode << (gpio->pin * 4));
  } else {
    reg_masked_write(&(GPIO->P[gpio->port].MODEH), 0xFu << ((gpio->pin - 8) * 4),
                     (uint32_t)mode << ((gpio->pin - 8) * 4));
  }

  if (mode == MCU_GPIO_MODE_DISABLED) {
    if (output_set) {
      mcu_gpio_set(gpio);
    } else {
      mcu_gpio_clear(gpio);
    }
  }
}

bool mcu_gpio_int_enable(const mcu_gpio_config_t* gpio, const bool rising, const bool falling,
                         uint32_t* int_num) {
  // On series 0 devices the pin number parameter is not used.
  // The pin number used on these devices is hardwired to the interrupt with the same number.
  // On series 1 devices, pin number can be selected freely within a group.
  // Interrupt numbers are divided into 4 groups (intNo / 4) and valid pin number within the
  // interrupt groups are: 0: pins 0-3 1: pins 4-7 2: pins 8-11 3: pins 12-15

  // Calculate the exti number based on the pin port/group
  // Pin must fall within a specific exti group, as above
  const uint32_t group = gpio->pin / MCU_GPIO_EXTI_GROUPS;
  const uint32_t group_count =
    __builtin_popcount((exti_enabled >> (MCU_GPIO_EXTI_GROUPS * group)) & 0xf);
  if (group_count == MCU_GPIO_EXTI_GROUPS) {
    return false;
  }

  // Track enabled interrupts
  const uint32_t exti_num = group_count + (MCU_GPIO_EXTI_GROUPS * group);
  exti_enabled |= (1 << exti_num);
  *int_num = exti_num;

  GPIO_ExtIntConfig(gpio->port, gpio->pin, exti_num, rising, falling, true /* enable */);

  return true;
}

void mcu_gpio_int_clear(const uint32_t flags) {
  GPIO_IntClear(flags);
}

uint32_t mcu_gpio_int_get_enabled_odd(void) {
  return GPIO_IntGetEnabled() & _GPIOINT_IF_ODD_MASK;
}

uint32_t mcu_gpio_int_get_enabled_even(void) {
  return GPIO_IntGetEnabled() & _GPIOINT_IF_EVEN_MASK;
}
