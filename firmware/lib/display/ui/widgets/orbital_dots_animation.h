/**
 * @file orbital_dots_animation.h
 * @brief Orbital dots animation widget for scan screen
 *
 * Creates an animated background of dots arranged in concentric circles with
 * 6 highlighted dots that orbit on the outer tracks.
 */

#pragma once
#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/** Maximum number of background dots */
#define ORBITAL_DOTS_NUM_BACKGROUND 350

/** Number of animated orbital dots */
#define ORBITAL_DOTS_NUM_ANIMATED 6

/**
 * @brief Orbital dots animation state structure
 *
 * Manages background dots in concentric circles and animated orbital dots
 * that travel on circular paths.
 */
typedef struct {
  lv_obj_t* parent;  ///< Parent LVGL object

  lv_obj_t* bg_dots[ORBITAL_DOTS_NUM_BACKGROUND];  ///< Static background dots

  lv_obj_t* orbital_dots[ORBITAL_DOTS_NUM_ANIMATED];  ///< Animated orbital dots
  float orbital_angles[ORBITAL_DOTS_NUM_ANIMATED];    ///< Current angle for each dot (degrees)
  float orbital_speeds[ORBITAL_DOTS_NUM_ANIMATED];    ///< Speed for each dot (degrees/frame)
  uint16_t orbital_radii[ORBITAL_DOTS_NUM_ANIMATED];  ///< Radius for each dot (pixels)

  lv_timer_t* update_timer;  ///< Animation update timer

  bool is_initialized;  ///< True if widget has been created
  bool is_animating;    ///< True if animation is running
} orbital_dots_animation_t;

/**
 * @brief Create orbital dots animation widget
 *
 * Creates background dots in concentric circles and orbital dots that will
 * animate on the outer tracks. Background dots and orbital dots start hidden.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param animation Animation state structure (must be zero-initialized)
 * @return Parent object, or NULL on error
 */
lv_obj_t* orbital_dots_animation_create(lv_obj_t* parent, orbital_dots_animation_t* animation);

/**
 * @brief Start the orbital dots animation
 *
 * Shows background dots and orbital dots, then begins the orbital animation.
 * Should be called after screen fade-in completes.
 *
 * @param animation Animation state structure
 */
void orbital_dots_animation_start(orbital_dots_animation_t* animation);

/**
 * @brief Stop the orbital dots animation
 *
 * Stops the animation timer. Dots remain visible but stop moving.
 *
 * @param animation Animation state structure
 */
void orbital_dots_animation_stop(orbital_dots_animation_t* animation);

/**
 * @brief Destroy the orbital dots animation widget
 *
 * Stops animation and cleans up all resources. The animation structure
 * will be reset to zero.
 *
 * @param animation Animation state structure
 */
void orbital_dots_animation_destroy(orbital_dots_animation_t* animation);
