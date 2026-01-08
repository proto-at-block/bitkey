/**
 * @file bottom_menu.h
 *
 * @brief Implements a menu button widget.
 *
 * @{
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>

// Bottom menu button widget - displays ellipsis icon in a circle at bottom center
typedef struct {
  lv_obj_t* container;

  /**
   * @brief Circle highlighting the currently selected bottom menu button
   * widget.
   */
  lv_obj_t* circle;

  /**
   * @brief Default menu navigation item (chevron).
   */
  lv_obj_t* default_icon_img;

  /**
   * @brief Icon to display in the bottom widget (overriding the default).
   */
  lv_obj_t* icon_img;

  /**
   * @brief `true` if the widget has already been created.
   */
  bool is_initialized;
} bottom_menu_t;

/**
 * @brief Initializes a menu button widget.
 *
 * @param parent      Parent LVL object to bind the menu button widget to.
 * @param button      Menu button widget state.
 * @param no_default  `True` if an icon image is supplied, meaning the default
 *                    should not be used.
 */
void bottom_menu_create(lv_obj_t* parent, bottom_menu_t* button, bool no_default);

void bottom_menu_set_highlight(bottom_menu_t* button, bool highlighted);
void bottom_menu_destroy(bottom_menu_t* button);

/** @} */
