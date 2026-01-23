#pragma once

#include "lvgl.h"

/**
 * @brief Create burn-in grid pattern widget
 *
 * Creates a grid of red, green, blue, and black lines to test display uniformity.
 * Grid lines are spaced 20 pixels apart in both horizontal and vertical directions.
 *
 * @param parent Parent LVGL object (typically the screen)
 */
void mfg_burnin_grid_create(lv_obj_t* parent);
