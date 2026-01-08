#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Temperature event types
 */
typedef enum {
  MCU_TEMP_EVENT_HIGH,  // High threshold crossed
  MCU_TEMP_EVENT_LOW,   // Low threshold crossed
  MCU_TEMP_EVENT_AVG    // Average ready (every ~4 seconds)
} mcu_temperature_event_t;

/**
 * @brief Callback function type for temperature events
 * @param event Type of temperature event
 */
typedef void (*mcu_temperature_callback_t)(mcu_temperature_event_t event);

/**
 * @brief Initialize the MCU temperature sensor with threshold monitoring
 * @param high_threshold High temperature threshold in degrees Celsius
 * @param low_threshold Low temperature threshold in degrees Celsius
 * @param callback Callback function to invoke on threshold crossings
 * @return true if initialization successful, false otherwise
 */
bool mcu_temperature_init_monitoring(int8_t high_threshold, int8_t low_threshold,
                                     mcu_temperature_callback_t callback);

/**
 * @brief Get the instantaneous MCU die temperature
 * @return Temperature in degrees Celsius
 */
float mcu_temperature_get_celsius_instant(void);

/**
 * @brief Get the cached averaged MCU die temperature
 * @note Updated every ~4 seconds by the interrupt handler
 * @return Averaged temperature in degrees Celsius (0 if not yet available)
 */
float mcu_temperature_get_celsius_averaged(void);

/**
 * @brief Trigger temperature averaging
 * @note Starts a new 16-sample averaging cycle (~4 seconds)
 */
void mcu_temperature_trigger_averaging(void);
