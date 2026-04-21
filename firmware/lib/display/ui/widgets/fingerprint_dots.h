/**
 * @file fingerprint_dots.h
 * @brief Fingerprint enrollment dots widget
 *
 * Displays a fingerprint pattern made of dots that progressively activate
 * as the user provides fingerprint samples during enrollment.
 * Uses fingerprint-specific styling (inactive: 4px grey, mid: 6px white, active: 8px white).
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/** Number of dots in the fingerprint pattern */
#define FINGERPRINT_DOTS_COUNT 85

/** Dot size configuration */
#define FINGERPRINT_DOT_SIZE_INACTIVE 4
#define FINGERPRINT_DOT_SIZE_MID      6
#define FINGERPRINT_DOT_SIZE_ACTIVE   8

/**
 * @brief Context for dot animation callbacks
 */
typedef struct {
  void* dots;     // Pointer to fingerprint_dots_t
  int dot_index;  // Index of this dot
} fingerprint_dot_anim_ctx_t;

/**
 * @brief Fingerprint dots widget state
 */
/** Dot state enumeration */
typedef enum {
  FINGERPRINT_DOT_STATE_INACTIVE = 0,
  FINGERPRINT_DOT_STATE_MID,
  FINGERPRINT_DOT_STATE_ACTIVE,
} fingerprint_dot_state_t;

/**
 * @brief Fingerprint dots widget state
 */
typedef struct {
  lv_obj_t* container;                                        // Container for centering
  lv_obj_t* dots[FINGERPRINT_DOTS_COUNT];                     // Dot objects
  fingerprint_dot_state_t dot_state[FINGERPRINT_DOTS_COUNT];  // State per dot (inactive/mid/active)
  fingerprint_dot_anim_ctx_t anim_ctx[FINGERPRINT_DOTS_COUNT];  // Per-dot animation contexts
  bool is_initialized;                                          // True if widget created
} fingerprint_dots_t;

/**
 * @brief Create fingerprint dots widget
 *
 * Creates a fingerprint pattern of dots, initially hidden in inactive state.
 * The pattern is centered on the screen.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param dots Widget structure (caller-allocated, will be initialized)
 */
void fingerprint_dots_create(lv_obj_t* parent, fingerprint_dots_t* dots);

/**
 * @brief Show the fingerprint dots
 *
 * Makes all dots visible in their current state.
 *
 * @param dots Widget structure
 */
void fingerprint_dots_show(fingerprint_dots_t* dots);

/**
 * @brief Hide the fingerprint dots
 *
 * Hides all dots.
 *
 * @param dots Widget structure
 */
void fingerprint_dots_hide(fingerprint_dots_t* dots);

/**
 * @brief Set percentage of active dots with animation
 *
 * Sets dots to show the given percentage. Newly activated dots
 * animate from inactive (4px grey) to active (8px white).
 * Dots are activated in a spiral pattern from outside to center.
 *
 * @param dots Widget structure
 * @param percent Percentage of dots to activate (0-100)
 */
void fingerprint_dots_set_percent(fingerprint_dots_t* dots, uint8_t percent);

/**
 * @brief Destroy fingerprint dots widget
 *
 * Cleans up all resources.
 *
 * @param dots Widget structure
 */
void fingerprint_dots_destroy(fingerprint_dots_t* dots);
