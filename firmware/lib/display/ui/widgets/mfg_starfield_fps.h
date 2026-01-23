/**
 * @file mfg_starfield_fps.h
 * @brief Starfield FPS animation widget for manufacturing tests
 *
 * Creates an animated starfield with FPS counter to test display performance.
 * Features 3 depth layers with parallax scrolling and twinkle effects.
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/** Number of stars in the animation */
#define STARFIELD_FPS_STAR_COUNT 15

/**
 * @brief Starfield FPS animation state structure
 */
typedef struct {
  lv_obj_t* parent;                           // Parent LVGL object
  lv_obj_t* star_layer;                       // Container for star objects
  lv_obj_t* fps_label;                        // FPS counter label
  lv_obj_t* stars[STARFIELD_FPS_STAR_COUNT];  // Star objects

  lv_timer_t* fps_timer;    // FPS update timer
  lv_timer_t* star_timer;   // Star animation timer
  lv_timer_t* phase_timer;  // Test phase toggle timer

  uint8_t star_layer_idx[STARFIELD_FPS_STAR_COUNT];  // Depth layer per star (0-2)
  uint16_t twinkle_phase[STARFIELD_FPS_STAR_COUNT];  // Twinkle animation phase per star

  bool full_invalidate_mode;  // True = full screen invalidate, false = partial updates
  bool is_initialized;        // True if widget has been created
} mfg_starfield_fps_t;

/**
 * @brief Create starfield FPS animation widget
 *
 * Creates star layer, spawns stars at random positions, and starts animation timers.
 * Stars will begin animating immediately.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param widget Widget state structure (must be zero-initialized)
 */
void mfg_starfield_fps_create(lv_obj_t* parent, mfg_starfield_fps_t* widget);

/**
 * @brief Destroy starfield FPS animation widget
 *
 * Stops all timers and cleans up resources. The widget structure will be reset.
 *
 * @param widget Widget state structure
 */
void mfg_starfield_fps_destroy(mfg_starfield_fps_t* widget);
