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
#include <stdint.h>

// Top menu widget - displays ellipsis icon in a pill button at top center
typedef struct {
  lv_obj_t* container;  // Pill-shaped container
  lv_obj_t* icon;       // Ellipsis icon image
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
 * @brief Sets the top menu opacity immediately.
 *
 * @param widget Widget state structure.
 * @param opacity Target opacity.
 */
void top_menu_set_opacity(top_menu_t* widget, lv_opa_t opacity);

/**
 * @brief Animates the top menu opacity.
 *
 * @param widget Widget state structure.
 * @param opacity Target opacity.
 * @param duration_ms Animation duration in milliseconds.
 */
void top_menu_fade_to_opacity(top_menu_t* widget, lv_opa_t opacity, uint32_t duration_ms);

/**
 * @brief Destroys a top menu widget.
 *
 * @param widget Widget state structure.
 */
void top_menu_destroy(top_menu_t* widget);

/** @} */
