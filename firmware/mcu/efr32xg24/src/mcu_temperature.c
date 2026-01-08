#include "mcu_temperature.h"

#include "em_cmu.h"
#include "em_core.h"
#include "em_emu.h"

// Temperature callback state
static struct {
  mcu_temperature_callback_t callback;
  volatile float averaged_temp;
  bool initialized;
} temp_state = {0};

bool mcu_temperature_init_monitoring(int8_t high_threshold, int8_t low_threshold,
                                     mcu_temperature_callback_t callback) {
  // EMU temperature sensor is always enabled on EFR32
  // Wait for first temperature measurement to be ready
  int timeout = 1000;
  while (!EMU_TemperatureReady()) {
    if (--timeout == 0) {
      return false;
    }
  }

  // Store callback
  temp_state.callback = callback;
  temp_state.initialized = true;

  // Unlock EMU to configure temperature monitoring
  EMU_Unlock();

  // Configure temperature thresholds (convert Celsius to Kelvin)
  // EMU expects Kelvin values for thresholds
  uint32_t temp_high = (uint32_t)(high_threshold + 273);
  uint32_t temp_low = (uint32_t)(low_threshold + 273);

  // Clamp to valid range (9-bit values: 0-511)
  if (temp_high > 0x1FF) {
    temp_high = 0x1FF;
  }
  if (temp_low > 0x1FF) {
    temp_low = 0x1FF;
  }

  EMU->TEMPLIMITS =
    (temp_high << _EMU_TEMPLIMITS_TEMPHIGH_SHIFT) | (temp_low << _EMU_TEMPLIMITS_TEMPLOW_SHIFT);

  // Clear any pending temperature interrupts
  EMU->IF_CLR = EMU_IF_TEMPAVG | EMU_IF_TEMPHIGH | EMU_IF_TEMPLOW;

  // Enable all temperature interrupts
  EMU->IEN_SET = EMU_IEN_TEMPAVG | EMU_IEN_TEMPHIGH | EMU_IEN_TEMPLOW;

  // Enable EMU interrupt in NVIC
  NVIC_ClearPendingIRQ(EMU_IRQn);
  NVIC_EnableIRQ(EMU_IRQn);

  return true;
}

float mcu_temperature_get_celsius_instant(void) {
  return EMU_TemperatureGet();
}

float mcu_temperature_get_celsius_averaged(void) {
  return temp_state.averaged_temp;
}

void mcu_temperature_trigger_averaging(void) {
  EMU_TemperatureAvgRequest(emuTempAvgNum_16);
}

void EMU_IRQHandler(void) {
  uint32_t flags = EMU->IF;

  // Clear all triggered interrupts immediately
  EMU->IF_CLR = flags;

  // Temperature average ready
  if (flags & EMU_IF_TEMPAVG) {
    temp_state.averaged_temp = EMU_TemperatureAvgGet();
    if (temp_state.callback) {
      temp_state.callback(MCU_TEMP_EVENT_AVG);
    }
  }

  // High temperature threshold crossed
  if ((flags & EMU_IF_TEMPHIGH) && temp_state.callback) {
    temp_state.callback(MCU_TEMP_EVENT_HIGH);
  }

  // Low temperature threshold crossed
  if ((flags & EMU_IF_TEMPLOW) && temp_state.callback) {
    temp_state.callback(MCU_TEMP_EVENT_LOW);
  }
}
