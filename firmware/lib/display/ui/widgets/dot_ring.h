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
#define DOT_RING_DOT_SIZE_INACTIVE 4  // Inactive dot diameter in pixels
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
  DOT_RING_FILL_CLOCKWISE_TOP = 2,  // Fill from top, going clockwise (for success confirmation)
} dot_ring_fill_dir_t;

typedef enum {
  DOT_RING_ANIM_NONE = 0,
  DOT_RING_ANIM_FORWARD = 1,
  DOT_RING_ANIM_REVERSE = 2,
} dot_ring_anim_t;

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

typedef struct dot_ring_storage dot_ring_storage_t;

/**
 * @brief Dot ring widget state
 */
typedef struct {
  lv_obj_t* parent;             // Parent object
  dot_ring_storage_t* storage;  // Shared backing storage for dot objects/state

  uint16_t dot_count;     // Actual number of dots in the ring
  uint16_t active_count;  // Number of currently active dots
  uint16_t target_count;  // Target number of active dots for current animation
  uint32_t anim_start_tick_ms;
  uint32_t anim_duration_ms;

  lv_anim_t fill_anim;                // Fill progress animation
  bool is_animating;                  // True if fill animation is running
  bool hide_when_animation_complete;  // Hide the ring when the current animation completes
  bool suppress_ready_cb;             // Ignore the ready callback for a cancelled animation
  dot_ring_anim_t anim_type;          // Current animation direction/state

  dot_ring_color_t active_color;       // Color for active dots
  dot_ring_fill_dir_t fill_dir;        // Fill direction
  dot_ring_complete_cb_t complete_cb;  // Completion callback
  void* user_data;                     // User data for callback

  bool is_initialized;  // True if widget created
  bool is_visible;      // True if dots are visible

  uint32_t fade_duration_ms;  // Fade duration for show/hide (0 = no fade)
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
 * @brief Show the dot ring with a fade-in animation
 *
 * Same as dot_ring_show but fades the dots in from transparent over the given
 * duration. Animates each dot's main style_opa; leaves bg_opa (which the dot
 * activation animation owns) untouched. The duration is stored on the ring so
 * the symmetric fade-out runs automatically when the internal release flow
 * would have hidden the ring.
 *
 * @param ring Ring widget structure
 * @param duration_ms Fade duration in milliseconds (0 = no fade)
 */
void dot_ring_show_with_fade_in(dot_ring_t* ring, uint32_t duration_ms);

/**
 * @brief Hide the dot ring with a fade-out animation
 *
 * Animates each dot's main style_opa from its current value down to
 * transparent, then sets the HIDDEN flag. Safe to call repeatedly; in-flight
 * fade animations are cancelled and restarted.
 *
 * @param ring Ring widget structure
 * @param duration_ms Fade-out duration in milliseconds (0 = instant hide)
 */
void dot_ring_hide_with_fade_out(dot_ring_t* ring, uint32_t duration_ms);

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
 * @brief Set a single dot to a custom active/inactive visual state
 *
 * Applies the standard active/inactive sizing and positioning while allowing
 * the caller to provide custom colors/opacities for screen-specific effects.
 *
 * @param ring Ring widget structure
 * @param dot_index Dot index to update
 * @param should_be_active Whether the dot should render active or inactive
 * @param active_color Color to use when active
 * @param active_opa Opacity to use when active
 * @param inactive_color Color to use when inactive
 * @param inactive_opa Opacity to use when inactive
 */
void dot_ring_set_dot_state(dot_ring_t* ring, uint16_t dot_index, bool should_be_active,
                            lv_color_t active_color, lv_opa_t active_opa, lv_color_t inactive_color,
                            lv_opa_t inactive_opa);

/**
 * @brief Animate fill from zero to target percentage
 *
 * Animates dots progressively from zero to target. Split-fill hold rings use a
 * slightly cheaper fast-start so the first visible progress appears sooner
 * without shortening the overall hold threshold.
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
 * @brief Animate fill from the current state to target percentage
 *
 * Resumes the fill from the ring's current active count. This is intended for
 * hold interactions that should continue from partially rewound progress when
 * the user presses again.
 *
 * @param ring Ring widget structure
 * @param target_percent Target percentage (0-100)
 * @param duration_ms Animation duration in milliseconds
 * @param color Color for active dots
 * @param fill_dir Fill direction (clockwise or split from bottom)
 * @param complete_cb Callback invoked when animation completes (can be NULL)
 * @param user_data User data passed to callback
 */
void dot_ring_animate_fill_from_current(dot_ring_t* ring, uint8_t target_percent,
                                        uint32_t duration_ms, dot_ring_color_t color,
                                        dot_ring_fill_dir_t fill_dir,
                                        dot_ring_complete_cb_t complete_cb, void* user_data);

/**
 * @brief Animate the ring back to the start and hide it
 *
 * Cancels any active forward fill, preserves the current visual progress, then
 * rewinds the ring back to zero. The reverse animation duration is scaled to
 * the current progress and shortened slightly so releasing halfway through the
 * hold rewinds a bit faster than the forward fill.
 *
 * @param ring Ring widget structure
 * @param full_duration_ms Full hold duration in milliseconds
 * @return true if the forward hold had already reached its threshold and the
 *         completion callback was fired instead of starting a rewind
 */
bool dot_ring_animate_release(dot_ring_t* ring, uint32_t full_duration_ms);

/**
 * @brief Animate the ring back to the start but keep inactive dots visible
 *
 * Cancels any active forward fill, preserves the current visual progress, then
 * rewinds the ring back to zero while leaving the inactive dots on screen.
 *
 * @param ring Ring widget structure
 * @param full_duration_ms Full hold duration in milliseconds
 * @return true if the forward hold had already reached its threshold and the
 *         completion callback was fired instead of starting a rewind
 */
bool dot_ring_animate_release_to_inactive(dot_ring_t* ring, uint32_t full_duration_ms);

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
