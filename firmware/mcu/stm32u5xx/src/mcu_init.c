#include "mcu.h"
#include "mcu_aes.h"
#include "mcu_clock.h"
#include "mcu_clock_impl.h"
#include "mcu_gpio.h"
#include "mcu_hash.h"
#include "mcu_i2c.h"
#include "mcu_pka.h"
#include "mcu_rng.h"
#include "mcu_tamper.h"
#include "stm32u5xx_ll_pwr.h"

void mcu_init(void) {
  // Disable UCPD dead battery pull-ups to avoid GPIO conflicts
  LL_PWR_DisableUCPDDeadBattery();

  // Initialize system clock to 160MHz using HSI
  mcu_clock_init(MCU_CLOCK_HSI_16MHZ_CORE_160MHZ);

  // Configure PLL2 for peripherals (OCTOSPI)
  mcu_aux_clock_init(MCU_AUX_CLOCK_HSE_16MHZ_AUX_48MHZ);

  // Configure tamper.
  mcu_tamper_init();

  // Enable GPIOs.
  mcu_gpio_init();

  // Initialize I2C RTOS primitives.
  mcu_i2c_init();

  // Enable the RNG.
  mcu_rng_init();

  // Enable hardware crypto modules.
  mcu_hash_init();
  mcu_pka_init();
  mcu_aes_init();
}
