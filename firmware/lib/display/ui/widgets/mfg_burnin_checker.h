#pragma once

#include "lvgl.h"

/**
 * @brief Create burn-in checkerboard pattern widget
 *
 * Creates an 8x8 checkerboard pattern with 59x59 pixel squares for display testing.
 *
 * @param parent Parent LVGL object (typically the screen)
 */
void mfg_burnin_checker_create(lv_obj_t* parent);
