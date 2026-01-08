#include "coproc.h"

#include "assert.h"
#include "attributes.h"
#include "mcu_gpio.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

coproc_cfg_t _coproc_cfg UI_TASK_DATA = {0};

void coproc_init(const coproc_cfg_t* cfg) {
  ASSERT(cfg != NULL);
  memcpy(&_coproc_cfg, cfg, sizeof(*cfg));

  if (_coproc_cfg.power.en.gpio != NULL) {
    /* Do not power on (assumes active high). */
    mcu_gpio_configure(_coproc_cfg.power.en.gpio, false);
  }

  if (_coproc_cfg.power.reset.gpio != NULL) {
    /* Hold in reset (assumes active low). */
    mcu_gpio_configure(_coproc_cfg.power.reset.gpio, false);
  }

  if (_coproc_cfg.power.boot.gpio != NULL) {
    mcu_gpio_configure(_coproc_cfg.power.boot.gpio, false);
  }
}

coproc_status_t coproc_get_status(void) {
  if (_coproc_cfg.power.boot.gpio == NULL) {
    return COPROC_STATUS_NOT_AVAIL;
  }

  uint32_t status = mcu_gpio_read(_coproc_cfg.power.boot.gpio);
  return (status == 0 ? COPROC_STATUS_OFF : COPROC_STATUS_RUNNING);
}
