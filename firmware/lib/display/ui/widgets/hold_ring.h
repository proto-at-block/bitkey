/**
 * @file hold_ring.h
 * @brief Hold-to-confirm ring animation widget
 *
 * Creates a ring of dots around the screen edge that progressively light up
 * when the user holds a button. Supports green (approve) and red (cancel) colors.
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/** Number of dots in the ring */
#define HOLD_RING_DOT_COUNT 40

/**
 * @brief Ring color options
 */
typedef enum {
  HOLD_RING_COLOR_GREEN = 0,  // Approve/confirm (lime green)
  HOLD_RING_COLOR_RED = 1,    // Cancel/reject (red)
} hold_ring_color_t;

/**
 * @brief Callback invoked when hold animation completes
 *
 * @param user_data User data passed to hold_ring_start()
 */
typedef void (*hold_ring_complete_cb_t)(void* user_data);

/**
 * @brief Context for dot animation callbacks
 */
typedef struct {
  void* ring;     // Pointer to hold_ring_t (void* to avoid circular dependency)
  int dot_index;  // Index of this dot
} dot_anim_ctx_t;

/**
 * @brief Hold ring widget state
 */
typedef struct {
  lv_obj_t* parent;                                  // Parent object
  lv_obj_t* ring_dots[HOLD_RING_DOT_COUNT];          // Ring dot objects
  lv_coord_t dot_centers_x[HOLD_RING_DOT_COUNT];     // Dot center X positions
  lv_coord_t dot_centers_y[HOLD_RING_DOT_COUNT];     // Dot center Y positions
  bool dot_active[HOLD_RING_DOT_COUNT];              // Activation state per dot
  dot_anim_ctx_t dot_contexts[HOLD_RING_DOT_COUNT];  // Per-dot animation contexts

  lv_anim_t main_anim;  // Main progress animation
  int current_value;    // Current animation value (0 to DOT_COUNT)
  bool is_active;       // True if hold animation is running

  hold_ring_color_t color;              // Current ring color
  hold_ring_complete_cb_t complete_cb;  // Completion callback
  void* user_data;                      // User data for callback

  bool is_initialized;  // True if widget created
} hold_ring_t;

/**
 * @brief Create hold ring widget
 *
 * Creates ring dots around screen edge, initially hidden. Dots start at the
 * bottom and progress clockwise.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param ring Ring widget structure (must be zero-initialized)
 */
void hold_ring_create(lv_obj_t* parent, hold_ring_t* ring);

/**
 * @brief Start hold animation
 *
 * Shows ring dots and starts progressive lighting animation.
 *
 * @param ring Ring widget structure
 * @param color Ring color (green or red)
 * @param complete_cb Callback invoked when animation completes (can be NULL)
 * @param user_data User data passed to callback
 */
void hold_ring_start(hold_ring_t* ring, hold_ring_color_t color,
                     hold_ring_complete_cb_t complete_cb, void* user_data);

/**
 * @brief Stop hold animation
 *
 * Stops animation and resets ring dots to initial hidden state.
 *
 * @param ring Ring widget structure
 */
void hold_ring_stop(hold_ring_t* ring);

/**
 * @brief Destroy hold ring widget
 *
 * Stops animation and cleans up all resources.
 *
 * @param ring Ring widget structure
 */
void hold_ring_destroy(hold_ring_t* ring);
