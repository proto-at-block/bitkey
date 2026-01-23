#pragma once

#include "screens.h"

/**
 * @brief Initialize the run-in test screen
 * @param ctx Display show_screen context
 * @return LVGL screen object
 */
lv_obj_t* screen_mfg_run_in_init(void* ctx);

/**
 * @brief Destroy the run-in test screen and cleanup resources
 */
void screen_mfg_run_in_destroy(void);

/**
 * @brief Update the run-in test screen with new data
 * @param ctx Display show_screen context
 */
void screen_mfg_run_in_update(void* ctx);
