/**
 * @brief Power off screen.
 *
 * @details The power off screen is shown when the device is being powered
 * off but USB remains plugged. On any gesture, the device will reset,
 * otherwise it will wait on this screen until USB is un-plugged to power
 * off.
 */

#pragma once

#include "lvgl/lvgl.h"

/**
 * @brief Initializes the power off screen.
 *
 * @param ctx Pointer to the LVGL context.
 *
 * @return LVGL screen object.
 */
lv_obj_t* screen_power_off_init(void* ctx);

/**
 * @brief Tears down the power off screen.
 */
void screen_power_off_destroy(void);

/**
 * @brief Called on each screen tick to update the screen.
 *
 * @param ctx Pointer to the LVGL context.
 */
void screen_power_off_update(void* ctx);
