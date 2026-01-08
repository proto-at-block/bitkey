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

#define EXTI_INVALID UINT32_MAX

static uint32_t gpio_to_exti(const mcu_gpio_config_t* gpio);
static void exti_isr(const uint32_t flag);

void exti_init(void) {
  if (initialised) {
    return;
  }

  mcu_nvic_enable_irq(GPIO_ODD_IRQn);
  mcu_nvic_enable_irq(GPIO_EVEN_IRQn);
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
    LOGW("repeated call to exti_enable for P%lu.%lu", config->gpio.port, config->gpio.pin);
    return;
  }

  bool pull_up = (config->gpio.mode == MCU_GPIO_MODE_INPUT_PULL ||
                  config->gpio.mode == MCU_GPIO_MODE_INPUT_PULL_FILTER);
  mcu_gpio_configure(&config->gpio, pull_up);

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
  for (uint32_t i = 0; i < MCU_GPIO_EXTI_MAX; i++) {
    if (exti_map[i] == gpio) {
      return i;
    }
  }

  return EXTI_INVALID;
}

static void exti_isr(const uint32_t flag) {
  /* Event group bits map directly to the EXTI interrupt number bits. */
  bool woken = false;
  rtos_event_group_set_bits_from_isr(&exti_events, flag, &woken);
  if (perf != NULL) {
    perf_count(perf);
  }
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
