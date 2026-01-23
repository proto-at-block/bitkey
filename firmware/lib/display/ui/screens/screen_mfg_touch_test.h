#pragma once

#include "screens.h"

#include <stdint.h>

/**
 * @brief Initialize the touch test screen
 * @param ctx Display show_screen context
 * @return LVGL screen object
 */
lv_obj_t* screen_mfg_touch_test_init(void* ctx);

/**
 * @brief Destroy the touch test screen and cleanup resources
 */
void screen_mfg_touch_test_destroy(void);

/**
 * @brief Get the number of remaining touch test boxes
 * @return Number of boxes that haven't been cleared yet
 */
uint16_t screen_mfg_touch_test_get_boxes_remaining(void);
