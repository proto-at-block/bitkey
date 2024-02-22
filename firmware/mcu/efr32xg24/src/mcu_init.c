#include "mcu.h"
#include "mcu_gpio.h"
#include "sl_device_init_clocks.h"
#include "sl_device_init_dcdc.h"
#include "sl_device_init_emu.h"
#include "sl_device_init_nvic.h"

#include "em_cmu.h"

void mcu_init(void) {
  sl_device_init_nvic();
  sl_device_init_clocks();
  sl_device_init_emu();

  mcu_gpio_init();
}
