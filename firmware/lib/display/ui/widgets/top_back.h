#pragma once

#include "lvgl.h"

#include <stdbool.h>

// Top back button widget - displays back_arrow icon in a grey pill at top left
typedef struct {
  lv_obj_t* container;  // Pill-shaped container
  lv_obj_t* icon_img;
  bool is_initialized;
} top_back_t;

/**
 * @brief Create a back button widget
 * @param parent Parent LVGL object
 * @param button Back button structure to initialize
 * @param custom_handler Optional custom click handler. If NULL, sends DISPLAY_ACTION_BACK.
 *                       If provided, this handler will be called instead.
 */
void top_back_create(lv_obj_t* parent, top_back_t* button, lv_event_cb_t custom_handler);
void top_back_destroy(top_back_t* button);
