#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Initialize the temperature monitoring system
 * @return true if initialization successful, false otherwise
 */
bool temperature_init(void);

/**
 * @brief Get averaged MCU die temperature
 * @note The EMU continuously samples temperature every 250ms and maintains a
 *       circular buffer. This function triggers hardware averaging of the last
 *       16 samples (covering the previous ~4 seconds)
 * @return Averaged temperature in degrees Celsius
 */
float temperature_get_averaged(void);
