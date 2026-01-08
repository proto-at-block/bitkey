#include "temperature.h"

#include "log.h"
#include "mcu_temperature.h"
#include "rtos.h"
#include "rtos_mpu.h"
#include "sysevent.h"

// High/low temperature thresholds in °C
#define TEMP_HIGH_THRESHOLD 60
#define TEMP_LOW_THRESHOLD  1

// Timeout for temperature averaging operation
#define TEMP_AVERAGING_TIMEOUT_MS 20

// Internal event flag for averaging completion
#define TEMP_EVENT_AVG (1 << 2)

static struct {
  bool initialized;
  rtos_event_group_t events;
} temp_state = {0};

static void temperature_callback(mcu_temperature_event_t event) {
  switch (event) {
    case MCU_TEMP_EVENT_HIGH:
      sysevent_set_from_isr(SYSEVENT_TEMP_HIGH);
      break;
    case MCU_TEMP_EVENT_LOW:
      sysevent_set_from_isr(SYSEVENT_TEMP_LOW);
      break;
    case MCU_TEMP_EVENT_AVG: {
      bool woken = false;
      rtos_event_group_set_bits_from_isr(&temp_state.events, TEMP_EVENT_AVG, &woken);
      break;
    }
  }
}

bool temperature_init(void) {
  if (temp_state.initialized) {
    return true;
  }

  // Create event group for ISR-to-task communication
  rtos_event_group_create(&temp_state.events);

  // Initialize MCU temperature sensor with callback
  if (!mcu_temperature_init_monitoring(TEMP_HIGH_THRESHOLD, TEMP_LOW_THRESHOLD,
                                       temperature_callback)) {
    LOGE("Failed to initialize MCU temperature sensor");
    rtos_event_group_destroy(&temp_state.events);
    return false;
  }

  temp_state.initialized = true;
  return true;
}

float temperature_get_averaged(void) {
  if (!temp_state.initialized) {
    return 0.0f;
  }

  // Clear any stale AVG event before triggering
  rtos_event_group_clear_bits(&temp_state.events, TEMP_EVENT_AVG);

  // Trigger averaging
  mcu_temperature_trigger_averaging();

  // Wait for averaging to complete
  uint32_t events = rtos_event_group_wait_bits(&temp_state.events, TEMP_EVENT_AVG, true, false,
                                               pdMS_TO_TICKS(TEMP_AVERAGING_TIMEOUT_MS));

  if (!(events & TEMP_EVENT_AVG)) {
    LOGE("Temperature averaging timeout");
    return 0.0f;
  }

  return mcu_temperature_get_celsius_averaged();
}
