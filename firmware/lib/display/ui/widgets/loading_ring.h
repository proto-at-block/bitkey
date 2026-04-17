/**
 * @file loading_ring.h
 * @brief Reusable loading ring widget with rotating fading trail
 *
 * Creates a ring of dots around the screen edge that continuously rotates
 * to indicate an ongoing operation (e.g., firmware update in progress).
 * The ring covers ~70% of the circumference with a fading trail effect:
 * dots at the head are bright and dots toward the tail fade out.
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/** Dot ring configuration (matches dot_ring.h for visual consistency) */
#define LOADING_RING_DOT_SIZE    8  // Dot diameter in pixels
#define LOADING_RING_DOT_SPACING 2  // Gap between dots in pixels
#define LOADING_RING_EDGE_INSET  6  // Distance from screen edge in pixels

/** Maximum number of dots (calculated based on circumference) */
#define LOADING_RING_MAX_DOTS 120

/** Animation configuration */
#define LOADING_RING_UPDATE_MS      30  // Timer period (~33 FPS)
#define LOADING_RING_TRAIL_PERCENT  70  // Percentage of ring that is visible
#define LOADING_RING_ROTATION_SPEED 1   // Dots to advance per frame

/**
 * @brief Loading ring widget state
 */
typedef struct {
  lv_obj_t* parent;                                 // Parent LVGL object
  lv_obj_t* dots[LOADING_RING_MAX_DOTS];            // Dot objects
  lv_coord_t dot_centers_x[LOADING_RING_MAX_DOTS];  // Dot center X positions
  lv_coord_t dot_centers_y[LOADING_RING_MAX_DOTS];  // Dot center Y positions

  uint16_t dot_count;      // Actual number of dots in the ring
  uint16_t trail_length;   // Number of visible dots (~70% of total)
  uint16_t head_position;  // Current head dot index (advances each frame)

  uint8_t head_r;     // Head color red channel
  uint8_t head_g;     // Head color green channel
  uint8_t head_b;     // Head color blue channel
  uint8_t tail_r;     // Tail color red channel
  uint8_t tail_g;     // Tail color green channel
  uint8_t tail_b;     // Tail color blue channel
  lv_opa_t tail_opa;  // Tail opacity

  lv_timer_t* update_timer;  // Periodic animation timer
  bool is_initialized;       // True if widget created
  bool is_animating;         // True if animation is running
} loading_ring_t;

/**
 * @brief Create loading ring widget
 *
 * Creates ring of dots around the screen edge, initially hidden.
 * Dots are positioned starting from the bottom center, going clockwise.
 *
 * @param parent Parent LVGL object (typically the screen)
 * @param ring Ring widget structure (caller-allocated, will be initialized)
 */
void loading_ring_create(lv_obj_t* parent, loading_ring_t* ring);

/**
 * @brief Override the ring's active palette
 *
 * Updates the head and tail colors used by the rotating trail. If the ring is
 * already animating, the new palette is applied immediately.
 *
 * @param ring Ring widget structure
 * @param head_r Head color red channel
 * @param head_g Head color green channel
 * @param head_b Head color blue channel
 * @param tail_r Tail color red channel
 * @param tail_g Tail color green channel
 * @param tail_b Tail color blue channel
 * @param tail_opa Tail opacity
 */
void loading_ring_set_palette(loading_ring_t* ring, uint8_t head_r, uint8_t head_g, uint8_t head_b,
                              uint8_t tail_r, uint8_t tail_g, uint8_t tail_b, lv_opa_t tail_opa);

/**
 * @brief Start the loading ring animation
 *
 * Shows dots and begins the continuous rotation animation.
 *
 * @param ring Ring widget structure
 */
void loading_ring_start(loading_ring_t* ring);

/**
 * @brief Stop the loading ring animation
 *
 * Stops the rotation and hides all dots.
 *
 * @param ring Ring widget structure
 */
void loading_ring_stop(loading_ring_t* ring);

/**
 * @brief Destroy loading ring widget
 *
 * Stops animation and cleans up all resources.
 *
 * @param ring Ring widget structure
 */
void loading_ring_destroy(loading_ring_t* ring);
