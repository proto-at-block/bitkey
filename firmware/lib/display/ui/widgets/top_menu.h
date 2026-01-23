/**
 * @file top_menu.h
 *
 * @brief Implements a top menu widget.
 *
 * @{
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>

// Top menu widget - displays three dots in a pill button at top center
typedef struct {
  lv_obj_t* container;  // Pill-shaped container
  lv_obj_t* dots[3];    // Three dot objects
  bool is_initialized;
} top_menu_t;

/**
 * @brief Initializes a top menu widget.
 *
 * @param parent Parent LVGL object to bind the widget to.
 * @param widget Widget state structure.
 * @param custom_handler Optional custom click handler (NULL for default MENU action).
 */
void top_menu_create(lv_obj_t* parent, top_menu_t* widget, lv_event_cb_t custom_handler);

/**
 * @brief Destroys a top menu widget.
 *
 * @param widget Widget state structure.
 */
void top_menu_destroy(top_menu_t* widget);

/** @} */
