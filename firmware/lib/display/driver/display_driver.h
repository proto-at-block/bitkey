#pragma once

#include <gfx.h>
#include <mcu_gpio.h>
#include <stdint.h>

#define DISPLAY_MAX_NUM_PWR_PINS (4u)

typedef struct {
  gfx_config_t gfx_config;    //<! Graphics library configuration.
  uint32_t pwr_on_delay;      //<! Number of milliseconds to wait for the display to power on.
  uint32_t pwr_off_delay;     //<! Number of milliseconds to wait for the display to power off.
  uint32_t update_period_ms;  //<! Display update/refresh period in milliseconds.
  union {
    struct {
      mcu_gpio_config_t* const pwr_1v8_en;   //<! Enable/disable the 1V8 power rail.
      mcu_gpio_config_t* const pwr_3v3_en;   //<! Enable/disable the 3V3 power rail.
      mcu_gpio_config_t* const pwr_vbat_en;  //<! Enable/disable power to the display from VBAT.
      mcu_gpio_config_t* const pwr_avdd_en;  //<! Enable/disable power to the display from AVDD.
    } pwr;
    mcu_gpio_config_t* const
      pwr_pins[DISPLAY_MAX_NUM_PWR_PINS];  //<! GPIOs used to power on the display.
  };
} display_config_t;

/**
 * @brief Initializes the display.
 *
 * @note This method will power on the display.
 */
void display_init(void);

/**
 * @brief Triggers a refresh of the display.
 */
void display_update(void);

/**
 * @brief Powers on the display.
 *
 * @note This method will block until the display has powered on.
 */
void display_power_on(void);

/**
 * @brief Powers off the display.
 *
 * @note This method will block until the display has powered off.
 */
void display_power_off(void);

/**
 * @brief Sets the display rotation at runtime.
 *
 * @param rotate_180  If true, rotate display 180 degrees.
 */
void display_set_rotation(bool rotate_180);
