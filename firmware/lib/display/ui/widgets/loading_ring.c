/**
 * @file loading_ring.c
 * @brief Reusable loading ring widget implementation
 *
 * Continuously rotates a ~70% arc of dots around the screen edge.
 * Head dots are bright green, fading to dim toward the tail.
 */

#include "loading_ring.h"

#include "assert.h"

#include <math.h>
#include <string.h>

// Head color: bright lime green (matches dot_ring active green)
#define COLOR_HEAD_R 0xD1
#define COLOR_HEAD_G 0xFB
#define COLOR_HEAD_B 0x96

// Tail color: dim grey
#define COLOR_TAIL_R 0x33
#define COLOR_TAIL_G 0x33
#define COLOR_TAIL_B 0x33

// Opacity range
#define OPA_HEAD LV_OPA_COVER  // 255 - fully opaque at head
#define OPA_TAIL 38            // ~15% opacity at tail end

// Forward declarations
static void update_timer_cb(lv_timer_t* timer);
static void update_dot_appearances(loading_ring_t* ring);

void loading_ring_create(lv_obj_t* parent, loading_ring_t* ring) {
  ASSERT(parent != NULL);
  ASSERT(ring != NULL);

  memset(ring, 0, sizeof(loading_ring_t));
  ring->parent = parent;

  // Calculate ring dimensions (same as dot_ring for visual consistency)
  lv_coord_t center_x = LV_HOR_RES / 2;
  lv_coord_t center_y = LV_VER_RES / 2;
  lv_coord_t radius = (LV_HOR_RES / 2) - LOADING_RING_EDGE_INSET - (LOADING_RING_DOT_SIZE / 2);

  // Calculate number of dots based on circumference
  float circumference = 2.0f * (float)M_PI * radius;
  float arc_per_dot = LOADING_RING_DOT_SIZE + LOADING_RING_DOT_SPACING;
  uint16_t num_dots = (uint16_t)(circumference / arc_per_dot);

  if (num_dots == 0) {
    return;
  }
  if (num_dots > LOADING_RING_MAX_DOTS) {
    num_dots = LOADING_RING_MAX_DOTS;
  }

  ring->dot_count = num_dots;
  ring->trail_length = (uint16_t)((num_dots * LOADING_RING_TRAIL_PERCENT) / 100);
  ring->head_position = 0;
  ring->head_r = COLOR_HEAD_R;
  ring->head_g = COLOR_HEAD_G;
  ring->head_b = COLOR_HEAD_B;
  ring->tail_r = COLOR_TAIL_R;
  ring->tail_g = COLOR_TAIL_G;
  ring->tail_b = COLOR_TAIL_B;
  ring->tail_opa = OPA_TAIL;

  // Create dots positioned around screen edge
  // Starting from bottom center (angle = PI/2), going clockwise
  float angle_step = (2.0f * (float)M_PI) / num_dots;

  for (uint16_t i = 0; i < num_dots; i++) {
    float angle = ((float)M_PI / 2.0f) + (angle_step * (float)i);
    lv_coord_t dot_x = center_x + (lv_coord_t)(radius * cosf(angle));
    lv_coord_t dot_y = center_y + (lv_coord_t)(radius * sinf(angle));

    ring->dot_centers_x[i] = dot_x;
    ring->dot_centers_y[i] = dot_y;

    ring->dots[i] = lv_obj_create(parent);
    lv_obj_set_size(ring->dots[i], LOADING_RING_DOT_SIZE, LOADING_RING_DOT_SIZE);
    lv_obj_set_pos(ring->dots[i], dot_x - (LOADING_RING_DOT_SIZE / 2),
                   dot_y - (LOADING_RING_DOT_SIZE / 2));
    lv_obj_set_style_radius(ring->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_opa(ring->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(ring->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(ring->dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);
  }

  ring->is_initialized = true;
  ring->is_animating = false;
}

void loading_ring_set_palette(loading_ring_t* ring, uint8_t head_r, uint8_t head_g, uint8_t head_b,
                              uint8_t tail_r, uint8_t tail_g, uint8_t tail_b, lv_opa_t tail_opa) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  ring->head_r = head_r;
  ring->head_g = head_g;
  ring->head_b = head_b;
  ring->tail_r = tail_r;
  ring->tail_g = tail_g;
  ring->tail_b = tail_b;
  ring->tail_opa = tail_opa;

  if (ring->is_animating) {
    update_dot_appearances(ring);
  }
}

void loading_ring_start(loading_ring_t* ring) {
  if (!ring || !ring->is_initialized || ring->is_animating) {
    return;
  }

  // Show all dots (appearance set by update function)
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_clear_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  ring->head_position = 0;
  ring->is_animating = true;

  // Set initial appearances
  update_dot_appearances(ring);

  // Start periodic timer for animation
  ring->update_timer = lv_timer_create(update_timer_cb, LOADING_RING_UPDATE_MS, ring);
  if (!ring->update_timer) {
    ring->is_animating = false;
    for (uint16_t i = 0; i < ring->dot_count; i++) {
      if (ring->dots[i]) {
        lv_obj_add_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);
      }
    }
  }
}

void loading_ring_stop(loading_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Stop timer
  if (ring->update_timer) {
    lv_timer_del(ring->update_timer);
    ring->update_timer = NULL;
  }

  // Hide all dots
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_add_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  ring->is_animating = false;
}

void loading_ring_destroy(loading_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  loading_ring_stop(ring);

  // Delete all dot objects
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_del(ring->dots[i]);
      ring->dots[i] = NULL;
    }
  }

  memset(ring, 0, sizeof(loading_ring_t));
}

// ========================================================================
// Animation
// ========================================================================

/**
 * @brief Update all dot colors and opacities based on current head position
 *
 * Every trail dot's distance-to-head changes each tick, so every trail dot
 * needs a color/opacity update. Gap dots are set to transparent.
 */
static void update_dot_appearances(loading_ring_t* ring) {
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (!ring->dots[i]) {
      continue;
    }

    int distance;
    if (i <= ring->head_position) {
      distance = ring->head_position - i;
    } else {
      distance = ring->head_position + ring->dot_count - i;
    }

    if (distance < ring->trail_length) {
      int trail_max = ring->trail_length - 1;
      if (trail_max < 1) {
        trail_max = 1;
      }

      int factor = (distance * 255) / trail_max;

      uint8_t r = (uint8_t)(ring->head_r - ((ring->head_r - ring->tail_r) * factor) / 255);
      uint8_t g = (uint8_t)(ring->head_g - ((ring->head_g - ring->tail_g) * factor) / 255);
      uint8_t b = (uint8_t)(ring->head_b - ((ring->head_b - ring->tail_b) * factor) / 255);
      lv_opa_t opa = (lv_opa_t)(OPA_HEAD - ((OPA_HEAD - ring->tail_opa) * factor) / 255);

      lv_obj_set_style_bg_color(ring->dots[i], lv_color_make(r, g, b), 0);
      lv_obj_set_style_bg_opa(ring->dots[i], opa, 0);
    } else {
      lv_obj_set_style_bg_opa(ring->dots[i], LV_OPA_TRANSP, 0);
    }
  }
}

/**
 * @brief Timer callback - advances the ring rotation each frame
 */
static void update_timer_cb(lv_timer_t* timer) {
  loading_ring_t* ring = (loading_ring_t*)lv_timer_get_user_data(timer);
  if (!ring || !ring->is_animating || ring->dot_count == 0) {
    return;
  }

  ring->head_position =
    (uint16_t)((ring->head_position + LOADING_RING_ROTATION_SPEED) % ring->dot_count);

  update_dot_appearances(ring);
}
