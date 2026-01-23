/**
 * @file hold_ring.c
 * @brief Hold-to-confirm ring animation widget implementation
 */

#include "hold_ring.h"

#include "assert.h"

#include <math.h>
#include <string.h>

// Ring configuration
#define RING_DOT_RADIUS_SMALL     3     // Initial dot radius
#define RING_DOT_RADIUS_LARGE     6     // Expanded dot radius when active
#define RING_INSET                8     // Distance from screen edge
#define RING_DOT_GROW_DURATION_MS 150   // Duration for dot grow animation
#define HOLD_DURATION_MS          2000  // Total hold duration

// Colors
#define COLOR_GREEN 0xD1FB96  // Lime green (approve)
#define COLOR_RED   0xF84752  // Red (cancel)

// Forward declarations
static void ring_main_anim_cb(void* var, int32_t value);
static void ring_anim_complete_cb(lv_anim_t* a);
static void start_dot_grow_animation(hold_ring_t* ring, int dot_index);
static void dot_grow_anim_cb(void* var, int32_t value);

void hold_ring_create(lv_obj_t* parent, hold_ring_t* ring) {
  ASSERT(parent != NULL);
  ASSERT(ring != NULL);

  ring->parent = parent;
  ring->is_active = false;
  ring->current_value = 0;

  // Calculate ring dimensions (ellipse around screen)
  lv_coord_t center_x = LV_HOR_RES / 2;
  lv_coord_t center_y = LV_VER_RES / 2;
  lv_coord_t radius_x = (LV_HOR_RES / 2) - RING_INSET - RING_DOT_RADIUS_LARGE;
  lv_coord_t radius_y = (LV_VER_RES / 2) - RING_INSET - RING_DOT_RADIUS_LARGE;

  // Create ring dots positioned around screen edge
  for (int i = 0; i < HOLD_RING_DOT_COUNT; i++) {
    // Calculate angle for this dot (starting from bottom, going clockwise)
    float angle = (M_PI / 2.0f) + (2.0f * M_PI * i / HOLD_RING_DOT_COUNT);
    lv_coord_t dot_x = center_x + (lv_coord_t)(radius_x * cosf(angle));
    lv_coord_t dot_y = center_y + (lv_coord_t)(radius_y * sinf(angle));

    // Store center positions for animation repositioning
    ring->dot_centers_x[i] = dot_x;
    ring->dot_centers_y[i] = dot_y;

    // Create dot object
    ring->ring_dots[i] = lv_obj_create(parent);
    lv_obj_set_size(ring->ring_dots[i], RING_DOT_RADIUS_SMALL * 2, RING_DOT_RADIUS_SMALL * 2);
    lv_obj_set_pos(ring->ring_dots[i], dot_x - RING_DOT_RADIUS_SMALL,
                   dot_y - RING_DOT_RADIUS_SMALL);
    lv_obj_set_style_radius(ring->ring_dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(ring->ring_dots[i], lv_color_white(), 0);
    lv_obj_set_style_bg_opa(ring->ring_dots[i], LV_OPA_50, 0);
    lv_obj_set_style_border_opa(ring->ring_dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(ring->ring_dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(ring->ring_dots[i], LV_OBJ_FLAG_HIDDEN);

    ring->dot_active[i] = false;
  }

  ring->is_initialized = true;
}

void hold_ring_start(hold_ring_t* ring, hold_ring_color_t color,
                     hold_ring_complete_cb_t complete_cb, void* user_data) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // First, stop any existing animations to ensure clean state
  lv_anim_del(&ring->main_anim, ring_main_anim_cb);
  for (int i = 0; i < HOLD_RING_DOT_COUNT; i++) {
    lv_anim_del(&ring->dot_contexts[i], dot_grow_anim_cb);
  }

  ring->color = color;
  ring->complete_cb = complete_cb;
  ring->user_data = user_data;
  ring->is_active = true;
  ring->current_value = 0;

  // Reset and show all dots in initial state
  for (int i = 0; i < HOLD_RING_DOT_COUNT; i++) {
    ring->dot_active[i] = false;

    // Reset to small size
    lv_obj_set_size(ring->ring_dots[i], RING_DOT_RADIUS_SMALL * 2, RING_DOT_RADIUS_SMALL * 2);

    // Reset position to center on dot center
    lv_obj_set_pos(ring->ring_dots[i], ring->dot_centers_x[i] - RING_DOT_RADIUS_SMALL,
                   ring->dot_centers_y[i] - RING_DOT_RADIUS_SMALL);

    // Reset to white, 50% opacity
    lv_obj_set_style_bg_color(ring->ring_dots[i], lv_color_white(), 0);
    lv_obj_set_style_bg_opa(ring->ring_dots[i], LV_OPA_50, 0);

    // Show the dot
    lv_obj_clear_flag(ring->ring_dots[i], LV_OBJ_FLAG_HIDDEN);
  }

  // Start main progress animation
  lv_anim_init(&ring->main_anim);
  lv_anim_set_var(&ring->main_anim, ring);
  lv_anim_set_user_data(&ring->main_anim, ring);
  lv_anim_set_values(&ring->main_anim, 0, HOLD_RING_DOT_COUNT);
  lv_anim_set_duration(&ring->main_anim, HOLD_DURATION_MS);
  lv_anim_set_exec_cb(&ring->main_anim, ring_main_anim_cb);
  lv_anim_set_ready_cb(&ring->main_anim, ring_anim_complete_cb);
  lv_anim_set_path_cb(&ring->main_anim, lv_anim_path_linear);
  lv_anim_start(&ring->main_anim);
}

void hold_ring_stop(hold_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Stop main animation
  lv_anim_del(&ring->main_anim, ring_main_anim_cb);

  // Stop all dot animations and reset
  for (int i = 0; i < HOLD_RING_DOT_COUNT; i++) {
    // Delete animation using the context pointer (which is the anim var)
    lv_anim_del(&ring->dot_contexts[i], dot_grow_anim_cb);

    // Reset to initial state
    lv_obj_set_size(ring->ring_dots[i], RING_DOT_RADIUS_SMALL * 2, RING_DOT_RADIUS_SMALL * 2);
    lv_obj_set_pos(ring->ring_dots[i], ring->dot_centers_x[i] - RING_DOT_RADIUS_SMALL,
                   ring->dot_centers_y[i] - RING_DOT_RADIUS_SMALL);
    lv_obj_set_style_bg_color(ring->ring_dots[i], lv_color_white(), 0);
    lv_obj_set_style_bg_opa(ring->ring_dots[i], LV_OPA_50, 0);
    lv_obj_add_flag(ring->ring_dots[i], LV_OBJ_FLAG_HIDDEN);

    ring->dot_active[i] = false;
  }

  ring->is_active = false;
  ring->current_value = 0;
}

void hold_ring_destroy(hold_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Stop any active animation
  hold_ring_stop(ring);

  // Delete all dot objects
  for (int i = 0; i < HOLD_RING_DOT_COUNT; i++) {
    if (ring->ring_dots[i]) {
      lv_obj_del(ring->ring_dots[i]);
      ring->ring_dots[i] = NULL;
    }
  }

  // Reset structure
  memset(ring, 0, sizeof(hold_ring_t));
}

// ========================================================================
// Animation Callbacks
// ========================================================================

static void ring_main_anim_cb(void* var, int32_t value) {
  hold_ring_t* ring = (hold_ring_t*)var;

  ring->current_value = value;

  // Activate dots progressively from bottom, spreading up both sides
  // Dots are arranged starting from bottom (index 0) going clockwise
  // Activation order: 0, 1, RING_DOT_COUNT-1, 2, RING_DOT_COUNT-2, etc.
  for (int i = 0; i < HOLD_RING_DOT_COUNT; i++) {
    // Calculate activation order for this dot
    int dot_activation_order;
    if (i == 0) {
      dot_activation_order = 0;  // Bottom dot activates first
    } else if (i <= HOLD_RING_DOT_COUNT / 2) {
      // Right side: activation order = i * 2 - 1
      dot_activation_order = i * 2 - 1;
    } else {
      // Left side: activation order = (RING_DOT_COUNT - i) * 2
      dot_activation_order = (HOLD_RING_DOT_COUNT - i) * 2;
    }

    bool should_be_active = value > dot_activation_order;

    // Only trigger animation when dot transitions from inactive to active
    if (should_be_active && !ring->dot_active[i]) {
      ring->dot_active[i] = true;
      start_dot_grow_animation(ring, i);
    }
  }
}

static void ring_anim_complete_cb(lv_anim_t* a) {
  hold_ring_t* ring = (hold_ring_t*)lv_anim_get_user_data(a);

  // Only call completion callback if ring is still active (not stopped)
  if (ring && ring->is_active && ring->complete_cb) {
    ring->complete_cb(ring->user_data);
  }
}

static void start_dot_grow_animation(hold_ring_t* ring, int dot_index) {
  if (dot_index < 0 || dot_index >= HOLD_RING_DOT_COUNT) {
    return;
  }

  lv_obj_t* dot = ring->ring_dots[dot_index];
  if (!dot) {
    return;
  }

  // Store context for callback in the ring's own context array
  ring->dot_contexts[dot_index].ring = ring;
  ring->dot_contexts[dot_index].dot_index = dot_index;

  lv_anim_t anim;
  lv_anim_init(&anim);
  // Pass the context as the var - callback will dereference it
  lv_anim_set_var(&anim, &ring->dot_contexts[dot_index]);
  lv_anim_set_values(&anim, RING_DOT_RADIUS_SMALL, RING_DOT_RADIUS_LARGE);
  lv_anim_set_duration(&anim, RING_DOT_GROW_DURATION_MS);
  lv_anim_set_exec_cb(&anim, dot_grow_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);
}

static void dot_grow_anim_cb(void* var, int32_t value) {
  // var is now the context pointer
  dot_anim_ctx_t* ctx = (dot_anim_ctx_t*)var;
  if (!ctx || !ctx->ring) {
    return;
  }

  hold_ring_t* ring = (hold_ring_t*)ctx->ring;
  int dot_index = ctx->dot_index;

  lv_obj_t* dot = ring->ring_dots[dot_index];
  if (!dot) {
    return;
  }

  lv_coord_t radius = (lv_coord_t)value;

  // Resize dot
  lv_obj_set_size(dot, radius * 2, radius * 2);

  // Reposition to keep centered
  lv_coord_t new_x = ring->dot_centers_x[dot_index] - radius;
  lv_coord_t new_y = ring->dot_centers_y[dot_index] - radius;
  lv_obj_set_pos(dot, new_x, new_y);

  // Calculate animation progress as integer (0 to 255)
  int progress =
    ((value - RING_DOT_RADIUS_SMALL) * 255) / (RING_DOT_RADIUS_LARGE - RING_DOT_RADIUS_SMALL);
  if (progress < 0)
    progress = 0;
  if (progress > 255)
    progress = 255;

  // Select target color based on ring color
  uint32_t target_color = (ring->color == HOLD_RING_COLOR_GREEN) ? COLOR_GREEN : COLOR_RED;

  // Interpolate color from white (255,255,255) to target color
  // Using integer arithmetic to match original implementation
  uint8_t end_r = (target_color >> 16) & 0xFF;
  uint8_t end_g = (target_color >> 8) & 0xFF;
  uint8_t end_b = target_color & 0xFF;

  // Interpolate: start + (end - start) * progress / 255
  uint8_t r = (uint8_t)(255 + ((int)end_r - 255) * progress / 255);
  uint8_t g = (uint8_t)(255 + ((int)end_g - 255) * progress / 255);
  uint8_t b = (uint8_t)(255 + ((int)end_b - 255) * progress / 255);

  lv_obj_set_style_bg_color(dot, lv_color_make(r, g, b), 0);

  // Interpolate opacity from 50% to 100%
  lv_opa_t opa = (lv_opa_t)(LV_OPA_50 + ((LV_OPA_COVER - LV_OPA_50) * progress) / 255);
  lv_obj_set_style_bg_opa(dot, opa, 0);
}
