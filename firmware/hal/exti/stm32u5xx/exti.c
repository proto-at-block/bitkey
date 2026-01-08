#include "exti.h"

#include "FreeRTOS.h"
#include "assert.h"
#include "log.h"
#include "mcu.h"
#include "mcu_nvic.h"
#include "perf.h"
#include "rtos.h"
#include "stm32u585xx.h"

static bool PERIPHERALS_DATA initialised = false;
static rtos_event_group_t PERIPHERALS_DATA exti_events = {0};
static perf_counter_t* perf = NULL;
static const mcu_gpio_config_t* PERIPHERALS_DATA exti_map[MCU_GPIO_EXTI_MAX] = {0};

#define EXTI_INVALID UINT32_MAX

static uint32_t gpio_to_exti(const mcu_gpio_config_t* gpio);
static void exti_isr(const uint32_t flag);

void exti_init(void) {
  if (initialised) {
    return;
  }

  rtos_event_group_create(&exti_events);
  perf = perf_create(PERF_COUNT, exti_events);
  initialised = true;
}

void exti_enable(const exti_config_t* config) {
  ASSERT(config != NULL);
  ASSERT(initialised);

  // Ignore re-initialising the same exti
  uint32_t existing_exti = gpio_to_exti(&config->gpio);
  if (existing_exti != EXTI_INVALID) {
    LOGW("repeated call to exti_enable for pin %lu", config->gpio.pin);
    return;
  }

  mcu_gpio_configure(&config->gpio, false);

  bool rising = false;
  bool falling = false;
  switch (config->trigger) {
    case EXTI_TRIGGER_RISING:
      rising = true;
      break;
    case EXTI_TRIGGER_FALLING:
      falling = true;
      break;
    case EXTI_TRIGGER_BOTH:
      rising = true;
      falling = true;
      break;
    default:
      ASSERT(false);
      break;
  }

  uint32_t exti_num = 0;
  if (mcu_gpio_int_enable(&config->gpio, rising, falling, &exti_num)) {
    // Add the exti gpio to the int num table
    exti_map[exti_num] = &config->gpio;

    // Enable the specific EXTI IRQ for this line
    const mcu_irqn_t exti_irqs[] = {
      EXTI0_IRQn,  EXTI1_IRQn,  EXTI2_IRQn,  EXTI3_IRQn,  EXTI4_IRQn,  EXTI5_IRQn,
      EXTI6_IRQn,  EXTI7_IRQn,  EXTI8_IRQn,  EXTI9_IRQn,  EXTI10_IRQn, EXTI11_IRQn,
      EXTI12_IRQn, EXTI13_IRQn, EXTI14_IRQn, EXTI15_IRQn,
    };

    if (exti_num < (sizeof(exti_irqs) / sizeof(exti_irqs[0]))) {
      const uint32_t priority = configLIBRARY_MAX_SYSCALL_INTERRUPT_PRIORITY;
      mcu_nvic_set_priority(exti_irqs[exti_num], priority);
      mcu_nvic_enable_irq(exti_irqs[exti_num]);
    }
  } else {
    // LOGE("failed to add exti for pin %lu", config->gpio.pin); // W-14261
  }
}

bool exti_pending(const exti_config_t* config) {
  ASSERT(config != NULL);
  ASSERT(initialised);

  uint32_t exti_num = gpio_to_exti(&config->gpio);
  ASSERT(exti_num != EXTI_INVALID);

  const uint32_t bits = rtos_event_group_get_bits(&exti_events);
  return bits & (1 << exti_num);
}

void exti_clear(const exti_config_t* config) {
  ASSERT(config != NULL);
  ASSERT(initialised);

  uint32_t exti_num = gpio_to_exti(&config->gpio);
  ASSERT(exti_num != EXTI_INVALID);

  const uint32_t exti_bits = (1 << exti_num);
  rtos_event_group_clear_bits(&exti_events, exti_bits);
}

bool exti_wait(const exti_config_t* config, const uint32_t timeout_ms, bool clear) {
  ASSERT(config != NULL);
  ASSERT(initialised);

  uint32_t exti_num = gpio_to_exti(&config->gpio);
  ASSERT(exti_num != EXTI_INVALID);

  const uint32_t exti_bits = (1 << exti_num);
  const uint32_t set_bits =
    rtos_event_group_wait_bits(&exti_events, exti_bits, clear, true, timeout_ms);
  return ((set_bits & exti_bits) == exti_bits);
}

void exti_signal(const exti_config_t* config) {
  ASSERT(config != NULL);
  ASSERT(initialised);

  uint32_t exti_num = gpio_to_exti(&config->gpio);
  ASSERT(exti_num != EXTI_INVALID);

  const uint32_t exti_bits = (1 << exti_num);
  rtos_event_group_set_bits(&exti_events, exti_bits);
}

static uint32_t gpio_to_exti(const mcu_gpio_config_t* gpio) {
  // STM32: EXTI line maps directly to GPIO pin number
  if (gpio->pin < MCU_GPIO_EXTI_MAX && exti_map[gpio->pin] != NULL) {
    return gpio->pin;
  }
  return EXTI_INVALID;
}

static void exti_isr(const uint32_t flag) {
  /* Event group bits map directly to the EXTI line number (which equals pin number on STM32). */
  bool woken = false;
  rtos_event_group_set_bits_from_isr(&exti_events, flag, &woken);
  if (perf != NULL) {
    perf_count(perf);
  }
}

static void exti_line_handler(uint32_t line) {
  uint32_t line_mask = (1 << line);

  // Check if interrupt is pending on STM32U5 (check both rising and falling pending registers)
  if ((EXTI->RPR1 | EXTI->FPR1) & line_mask) {
    mcu_gpio_int_clear(line_mask);
    exti_isr(line_mask);
  }
}

void EXTI0_IRQHandler(void) {
  exti_line_handler(0);
}
void EXTI1_IRQHandler(void) {
  exti_line_handler(1);
}
void EXTI2_IRQHandler(void) {
  exti_line_handler(2);
}
void EXTI3_IRQHandler(void) {
  exti_line_handler(3);
}
void EXTI4_IRQHandler(void) {
  exti_line_handler(4);
}
void EXTI5_IRQHandler(void) {
  exti_line_handler(5);
}
void EXTI6_IRQHandler(void) {
  exti_line_handler(6);
}
void EXTI7_IRQHandler(void) {
  exti_line_handler(7);
}
void EXTI8_IRQHandler(void) {
  exti_line_handler(8);
}
void EXTI9_IRQHandler(void) {
  exti_line_handler(9);
}
void EXTI10_IRQHandler(void) {
  exti_line_handler(10);
}
void EXTI11_IRQHandler(void) {
  exti_line_handler(11);
}
void EXTI12_IRQHandler(void) {
  exti_line_handler(12);
}
void EXTI13_IRQHandler(void) {
  exti_line_handler(13);
}
void EXTI14_IRQHandler(void) {
  exti_line_handler(14);
}
void EXTI15_IRQHandler(void) {
  exti_line_handler(15);
}
