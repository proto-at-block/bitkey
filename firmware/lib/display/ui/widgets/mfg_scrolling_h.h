#pragma once

#include "lvgl.h"

#include <stdbool.h>

/**
 * @brief Scrolling H test widget state
 */
typedef struct {
  lv_obj_t* container;
  lv_obj_t* label1;
  lv_obj_t* label2;
  lv_timer_t* timer;
  int32_t offset;
  bool is_initialized;
} mfg_scrolling_h_t;

/**
 * @brief Create scrolling H test widget
 *
 * Creates a horizontal scrolling grid of 'H' characters to test display response time.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param widget Widget state structure (must be zero-initialized)
 */
void mfg_scrolling_h_create(lv_obj_t* parent, mfg_scrolling_h_t* widget);

/**
 * @brief Destroy scrolling H test widget
 *
 * Stops animation and cleans up resources.
 *
 * @param widget Widget state structure
 */
void mfg_scrolling_h_destroy(mfg_scrolling_h_t* widget);
