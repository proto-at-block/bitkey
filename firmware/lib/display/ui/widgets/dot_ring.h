/**
 * @file dot_ring.h
 * @brief Reusable dot ring widget for percentage/progress display
 *
 * Creates a ring of evenly-spaced dots around the screen edge that can be
 * used to display percentages (e.g. battery charge) or animated progress
 * (e.g. hold-to-confirm). Dots can transition from inactive to active state
 * with configurable colors and animation.
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/** Dot ring configuration */
#define DOT_RING_DOT_SIZE_INACTIVE 2  // Inactive dot diameter in pixels
#define DOT_RING_DOT_SIZE_ACTIVE   8  // Active dot diameter in pixels
#define DOT_RING_DOT_SPACING       2  // Gap between dots in pixels
#define DOT_RING_EDGE_INSET        6  // Distance from screen edge in pixels

/** Maximum number of dots (calculated based on circumference) */
#define DOT_RING_MAX_DOTS 120

/**
 * @brief Dot ring color presets
 */
typedef enum {
  DOT_RING_COLOR_GREEN = 0,  // Lime green (#D1FB96)
  DOT_RING_COLOR_RED = 1,    // Red (#F84752)
  DOT_RING_COLOR_WHITE = 2,  // White
} dot_ring_color_t;

/**
 * @brief Dot ring fill direction
 */
typedef enum {
  DOT_RING_FILL_CLOCKWISE = 0,  // Fill from bottom, going clockwise (for battery/percentage)
  DOT_RING_FILL_SPLIT = 1,      // Fill from bottom, spreading up both sides (for hold-to-confirm)
} dot_ring_fill_dir_t;

/**
 * @brief Callback invoked when animated fill completes
 *
 * @param user_data User data passed to dot_ring_animate_fill()
 */
typedef void (*dot_ring_complete_cb_t)(void* user_data);

/**
 * @brief Context for dot animation callbacks
 */
typedef struct {
  void* ring;     // Pointer to dot_ring_t
  int dot_index;  // Index of this dot
} dot_ring_anim_ctx_t;

/**
 * @brief Dot ring widget state
 */
typedef struct {
  lv_obj_t* parent;                                     // Parent object
  lv_obj_t* dots[DOT_RING_MAX_DOTS];                    // Dot objects
  lv_coord_t dot_centers_x[DOT_RING_MAX_DOTS];          // Dot center X positions
  lv_coord_t dot_centers_y[DOT_RING_MAX_DOTS];          // Dot center Y positions
  bool dot_active[DOT_RING_MAX_DOTS];                   // Activation state per dot
  dot_ring_anim_ctx_t dot_contexts[DOT_RING_MAX_DOTS];  // Per-dot animation contexts

  uint16_t dot_count;     // Actual number of dots in the ring
  uint16_t active_count;  // Number of currently active dots

  lv_anim_t fill_anim;  // Fill progress animation
  bool is_animating;    // True if fill animation is running

  dot_ring_color_t active_color;       // Color for active dots
  dot_ring_fill_dir_t fill_dir;        // Fill direction
  dot_ring_complete_cb_t complete_cb;  // Completion callback
  void* user_data;                     // User data for callback

  bool is_initialized;  // True if widget created
  bool is_visible;      // True if dots are visible
} dot_ring_t;

/**
 * @brief Create dot ring widget
 *
 * Creates ring of dots around the screen edge, initially hidden.
 * Dots are positioned starting from the bottom center.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param ring Ring widget structure (caller-allocated, will be initialized)
 */
void dot_ring_create(lv_obj_t* parent, dot_ring_t* ring);

/**
 * @brief Show the dot ring
 *
 * Makes all dots visible in their inactive (dimmed) state.
 *
 * @param ring Ring widget structure
 */
void dot_ring_show(dot_ring_t* ring);

/**
 * @brief Hide the dot ring
 *
 * Hides all dots.
 *
 * @param ring Ring widget structure
 */
void dot_ring_hide(dot_ring_t* ring);

/**
 * @brief Set static percentage (no animation)
 *
 * Immediately sets dots to show the given percentage. Active dots
 * use the specified color, inactive dots remain dimmed.
 *
 * @param ring Ring widget structure
 * @param percent Percentage to display (0-100)
 * @param color Color for active dots
 * @param fill_dir Fill direction (clockwise or split from bottom)
 */
void dot_ring_set_percent(dot_ring_t* ring, uint8_t percent, dot_ring_color_t color,
                          dot_ring_fill_dir_t fill_dir);

/**
 * @brief Animate fill from current state to target percentage
 *
 * Animates dots progressively from current active count to target.
 *
 * @param ring Ring widget structure
 * @param target_percent Target percentage (0-100)
 * @param duration_ms Animation duration in milliseconds
 * @param color Color for active dots
 * @param fill_dir Fill direction (clockwise or split from bottom)
 * @param complete_cb Callback invoked when animation completes (can be NULL)
 * @param user_data User data passed to callback
 */
void dot_ring_animate_fill(dot_ring_t* ring, uint8_t target_percent, uint32_t duration_ms,
                           dot_ring_color_t color, dot_ring_fill_dir_t fill_dir,
                           dot_ring_complete_cb_t complete_cb, void* user_data);

/**
 * @brief Stop any running fill animation
 *
 * Stops animation and resets all dots to inactive state.
 *
 * @param ring Ring widget structure
 */
void dot_ring_stop(dot_ring_t* ring);

/**
 * @brief Reset all dots to inactive state
 *
 * Sets all dots to dimmed/inactive appearance without hiding them.
 *
 * @param ring Ring widget structure
 */
void dot_ring_reset(dot_ring_t* ring);

/**
 * @brief Destroy dot ring widget
 *
 * Stops animation and cleans up all resources.
 *
 * @param ring Ring widget structure
 */
void dot_ring_destroy(dot_ring_t* ring);
