#include "exti.h"

#include "assert.h"
#include "log.h"
#include "mcu_nvic.h"
#include "perf.h"
#include "rtos.h"

static bool PERIPHERALS_DATA initialised = false;
static rtos_event_group_t PERIPHERALS_DATA exti_events = {0};
static perf_counter_t* perf;
static const mcu_gpio_config_t* PERIPHERALS_DATA exti_map[MCU_GPIO_EXTI_MAX] = {0};

static uint32_t gpio_to_exti(const mcu_gpio_config_t* gpio);
static void exti_isr(const uint32_t flag);

void exti_enable(const exti_config_t* config) {
  ASSERT(config != NULL);

  if (!initialised) {
    mcu_nvic_enable_irq(GPIO_ODD_IRQn);
    mcu_nvic_enable_irq(GPIO_EVEN_IRQn);
    rtos_event_group_create(&exti_events);
    perf = perf_create(PERF_COUNT, exti_events);
    initialised = true;
  }

  // Ignore re-initlaising the same exti
  if (exti_map[gpio_to_exti(&config->gpio)] == &config->gpio) {
    LOGW("repeated call to exti_enable for P%lu.%lu", config->gpio.port, config->gpio.pin);
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
      break;
  }

  uint32_t exti_num = 0;
  if (mcu_gpio_int_enable(&config->gpio, rising, falling, &exti_num)) {
    // Add the exti gpio to the int num table
    exti_map[exti_num] = &config->gpio;
  } else {
    LOGE("failed to add exti for gpio P%lu.%lu", config->gpio.port, config->gpio.pin);
  }
}

bool exti_pending(const exti_config_t* config) {
  ASSERT(config != NULL);

  const uint32_t bits = rtos_event_group_get_bits(&exti_events);
  return bits & (1 << gpio_to_exti(&config->gpio));
}

void exti_clear(const exti_config_t* config) {
  ASSERT(config != NULL);

  const uint32_t exti_bits = (1 << gpio_to_exti(&config->gpio));
  rtos_event_group_clear_bits(&exti_events, exti_bits);
}

bool exti_wait(const exti_config_t* config, const uint32_t timeout_ms, bool clear) {
  ASSERT(config != NULL);

  const uint32_t exti_bits = (1 << gpio_to_exti(&config->gpio));
  return rtos_event_group_wait_bits(&exti_events, exti_bits, clear, true, timeout_ms) == exti_bits;
}

static uint32_t gpio_to_exti(const mcu_gpio_config_t* gpio) {
  for (uint32_t i = 0; i < MCU_GPIO_EXTI_MAX; i++) {
    if (exti_map[i] == gpio) {
      return i;
    }
  }

  return 0;
}

static void exti_isr(const uint32_t flag) {
  /* Event group bits map directly to the pin GPIO bits. */
  bool woken = false;
  rtos_event_group_set_bits_from_isr(&exti_events, flag, &woken);
  perf_count(perf);
}

void GPIO_ODD_IRQHandler(void) {
  const uint32_t flags = mcu_gpio_int_get_enabled_odd();
  mcu_gpio_int_clear(flags);
  exti_isr(flags);
}

void GPIO_EVEN_IRQHandler(void) {
  const uint32_t flags = mcu_gpio_int_get_enabled_even();
  mcu_gpio_int_clear(flags);
  exti_isr(flags);
}
