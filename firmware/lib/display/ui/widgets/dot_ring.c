/**
 * @file dot_ring.c
 * @brief Reusable dot ring widget implementation
 */

#include "dot_ring.h"

#include "assert.h"

#include <math.h>
#include <string.h>

// Colors
#define COLOR_GREEN    0xD1FB96  // Lime green
#define COLOR_RED      0xF84752  // Red
#define COLOR_WHITE    0xFFFFFF  // White
#define COLOR_INACTIVE 0x555555  // Grey (inactive dots)

// Inactive dot opacity
#define INACTIVE_OPA LV_OPA_50

// Animation timing for individual dot activation
#define DOT_ACTIVATE_DURATION_MS 100

// Forward declarations
static void fill_anim_cb(void* var, int32_t value);
static void fill_anim_complete_cb(lv_anim_t* a);
static void activate_dot(dot_ring_t* ring, int dot_index);
static void dot_activate_anim_cb(void* var, int32_t value);
static uint32_t get_color_hex(dot_ring_color_t color);
static int get_dot_activation_order(int dot_index, int total_dots, dot_ring_fill_dir_t fill_dir);

void dot_ring_create(lv_obj_t* parent, dot_ring_t* ring) {
  ASSERT(parent != NULL);
  ASSERT(ring != NULL);

  memset(ring, 0, sizeof(dot_ring_t));
  ring->parent = parent;

  // Calculate ring dimensions
  lv_coord_t center_x = LV_HOR_RES / 2;
  lv_coord_t center_y = LV_VER_RES / 2;

  // Radius from center to dot centers (inset from edge by active dot radius + edge inset)
  // Use active size for positioning so dots expand inward when activated
  lv_coord_t radius = (LV_HOR_RES / 2) - DOT_RING_EDGE_INSET - (DOT_RING_DOT_SIZE_ACTIVE / 2);

  // Calculate number of dots based on circumference and spacing
  // Use active dot size for spacing calculation
  float circumference = 2.0f * M_PI * radius;
  float arc_per_dot = DOT_RING_DOT_SIZE_ACTIVE + DOT_RING_DOT_SPACING;
  uint16_t num_dots = (uint16_t)(circumference / arc_per_dot);

  // Clamp to max
  if (num_dots > DOT_RING_MAX_DOTS) {
    num_dots = DOT_RING_MAX_DOTS;
  }

  ring->dot_count = num_dots;

  // Create dots positioned around screen edge
  // Starting from bottom center (angle = PI/2), going clockwise
  float angle_step = (2.0f * M_PI) / num_dots;

  for (uint16_t i = 0; i < num_dots; i++) {
    // Calculate angle for this dot (starting from bottom, going clockwise)
    float angle = (M_PI / 2.0f) + (angle_step * i);
    lv_coord_t dot_x = center_x + (lv_coord_t)(radius * cosf(angle));
    lv_coord_t dot_y = center_y + (lv_coord_t)(radius * sinf(angle));

    // Store center positions for repositioning during animation
    ring->dot_centers_x[i] = dot_x;
    ring->dot_centers_y[i] = dot_y;

    // Create dot object - start with inactive (small) size
    ring->dots[i] = lv_obj_create(parent);
    lv_obj_set_size(ring->dots[i], DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
    lv_obj_set_pos(ring->dots[i], dot_x - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                   dot_y - (DOT_RING_DOT_SIZE_INACTIVE / 2));
    lv_obj_set_style_radius(ring->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(ring->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
    lv_obj_set_style_bg_opa(ring->dots[i], INACTIVE_OPA, 0);
    lv_obj_set_style_border_opa(ring->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(ring->dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);

    ring->dot_active[i] = false;
  }

  ring->is_initialized = true;
  ring->is_visible = false;
}

void dot_ring_show(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_clear_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  ring->is_visible = true;
}

void dot_ring_hide(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_add_flag(ring->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  ring->is_visible = false;
}

void dot_ring_set_percent(dot_ring_t* ring, uint8_t percent, dot_ring_color_t color,
                          dot_ring_fill_dir_t fill_dir) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Clamp percent
  if (percent > 100) {
    percent = 100;
  }

  ring->active_color = color;
  ring->fill_dir = fill_dir;

  // Calculate number of dots to activate
  uint16_t target_active = (uint16_t)((ring->dot_count * percent) / 100);
  ring->active_count = target_active;

  // Set each dot's state based on activation order
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (!ring->dots[i]) {
      continue;
    }

    int activation_order = get_dot_activation_order(i, ring->dot_count, fill_dir);
    bool should_be_active = activation_order < (int)target_active;

    ring->dot_active[i] = should_be_active;

    if (should_be_active) {
      // Active: 8px dot with specified color
      uint32_t active_color = get_color_hex(color);
      lv_obj_set_size(ring->dots[i], DOT_RING_DOT_SIZE_ACTIVE, DOT_RING_DOT_SIZE_ACTIVE);
      lv_obj_set_pos(ring->dots[i], ring->dot_centers_x[i] - (DOT_RING_DOT_SIZE_ACTIVE / 2),
                     ring->dot_centers_y[i] - (DOT_RING_DOT_SIZE_ACTIVE / 2));
      lv_obj_set_style_bg_color(ring->dots[i], lv_color_hex(active_color), 0);
      lv_obj_set_style_bg_opa(ring->dots[i], LV_OPA_COVER, 0);
    } else {
      // Inactive: 2px grey dot
      lv_obj_set_size(ring->dots[i], DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
      lv_obj_set_pos(ring->dots[i], ring->dot_centers_x[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                     ring->dot_centers_y[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2));
      lv_obj_set_style_bg_color(ring->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
      lv_obj_set_style_bg_opa(ring->dots[i], INACTIVE_OPA, 0);
    }
  }
}

void dot_ring_animate_fill(dot_ring_t* ring, uint8_t target_percent, uint32_t duration_ms,
                           dot_ring_color_t color, dot_ring_fill_dir_t fill_dir,
                           dot_ring_complete_cb_t complete_cb, void* user_data) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Stop any existing animation
  lv_anim_del(&ring->fill_anim, fill_anim_cb);
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    lv_anim_del(&ring->dot_contexts[i], dot_activate_anim_cb);
  }

  // Clamp percent
  if (target_percent > 100) {
    target_percent = 100;
  }

  ring->active_color = color;
  ring->fill_dir = fill_dir;
  ring->complete_cb = complete_cb;
  ring->user_data = user_data;
  ring->is_animating = true;

  // Calculate target dot count
  uint16_t target_dots = (uint16_t)((ring->dot_count * target_percent) / 100);

  // Reset all dots to inactive (small, grey)
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    ring->dot_active[i] = false;
    if (ring->dots[i]) {
      lv_obj_set_size(ring->dots[i], DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
      lv_obj_set_pos(ring->dots[i], ring->dot_centers_x[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                     ring->dot_centers_y[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2));
      lv_obj_set_style_bg_color(ring->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
      lv_obj_set_style_bg_opa(ring->dots[i], INACTIVE_OPA, 0);
    }
  }

  ring->active_count = 0;

  // Start fill animation
  lv_anim_init(&ring->fill_anim);
  lv_anim_set_var(&ring->fill_anim, ring);
  lv_anim_set_user_data(&ring->fill_anim, ring);
  lv_anim_set_values(&ring->fill_anim, 0, target_dots);
  lv_anim_set_duration(&ring->fill_anim, duration_ms);
  lv_anim_set_exec_cb(&ring->fill_anim, fill_anim_cb);
  lv_anim_set_ready_cb(&ring->fill_anim, fill_anim_complete_cb);
  lv_anim_set_path_cb(&ring->fill_anim, lv_anim_path_linear);
  lv_anim_start(&ring->fill_anim);
}

void dot_ring_stop(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Clear the callback BEFORE stopping animation to prevent it from firing
  // when the animation is deleted (lv_anim_del may trigger ready callback)
  ring->complete_cb = NULL;
  ring->user_data = NULL;

  // Stop fill animation
  lv_anim_del(&ring->fill_anim, fill_anim_cb);

  // Stop all dot animations and reset to inactive (small, grey)
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    lv_anim_del(&ring->dot_contexts[i], dot_activate_anim_cb);

    if (ring->dots[i]) {
      lv_obj_set_size(ring->dots[i], DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
      lv_obj_set_pos(ring->dots[i], ring->dot_centers_x[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                     ring->dot_centers_y[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2));
      lv_obj_set_style_bg_color(ring->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
      lv_obj_set_style_bg_opa(ring->dots[i], INACTIVE_OPA, 0);
    }

    ring->dot_active[i] = false;
  }

  ring->is_animating = false;
  ring->active_count = 0;
}

void dot_ring_reset(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Stop any animations
  dot_ring_stop(ring);

  // Reset all dots to inactive appearance (small, grey) but keep visible if they were
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_set_size(ring->dots[i], DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
      lv_obj_set_pos(ring->dots[i], ring->dot_centers_x[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                     ring->dot_centers_y[i] - (DOT_RING_DOT_SIZE_INACTIVE / 2));
      lv_obj_set_style_bg_color(ring->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
      lv_obj_set_style_bg_opa(ring->dots[i], INACTIVE_OPA, 0);
    }
    ring->dot_active[i] = false;
  }

  ring->active_count = 0;
}

void dot_ring_destroy(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized) {
    return;
  }

  // Stop any active animation
  dot_ring_stop(ring);

  // Delete all dot objects
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (ring->dots[i]) {
      lv_obj_del(ring->dots[i]);
      ring->dots[i] = NULL;
    }
  }

  // Reset structure
  memset(ring, 0, sizeof(dot_ring_t));
}

// ========================================================================
// Helper Functions
// ========================================================================

static uint32_t get_color_hex(dot_ring_color_t color) {
  switch (color) {
    case DOT_RING_COLOR_GREEN:
      return COLOR_GREEN;
    case DOT_RING_COLOR_RED:
      return COLOR_RED;
    case DOT_RING_COLOR_WHITE:
    default:
      return COLOR_WHITE;
  }
}

/**
 * @brief Get activation order for a dot (split fill - both sides)
 *
 * Dots activate from bottom center, spreading up both sides simultaneously.
 * Bottom dot (index 0) activates first, then alternating sides.
 *
 * @param dot_index The dot's index in the ring (0 = bottom center)
 * @param total_dots Total number of dots in the ring
 * @return Activation order (0 = first to activate)
 */
static int get_dot_activation_order_split(int dot_index, int total_dots) {
  if (dot_index == 0) {
    return 0;  // Bottom dot activates first
  }

  // Right side dots (indices 1 to total/2) activate on odd orders
  // Left side dots (indices total-1 down to total/2+1) activate on even orders
  if (dot_index <= total_dots / 2) {
    // Right side: order = index * 2 - 1 (1->1, 2->3, 3->5, ...)
    return dot_index * 2 - 1;
  } else {
    // Left side: order = (total - index) * 2 (total-1->2, total-2->4, ...)
    return (total_dots - dot_index) * 2;
  }
}

/**
 * @brief Get activation order for a dot (clockwise fill)
 *
 * Dots activate from bottom center, going clockwise around the ring.
 * Bottom dot (index 0) activates first, then sequential.
 *
 * @param dot_index The dot's index in the ring (0 = bottom center)
 * @param total_dots Total number of dots in the ring (unused, for consistency)
 * @return Activation order (0 = first to activate)
 */
static int get_dot_activation_order_clockwise(int dot_index, int total_dots) {
  (void)total_dots;
  // Simple: activation order equals the dot index
  // Dots are already arranged starting from bottom going clockwise
  return dot_index;
}

/**
 * @brief Get activation order for a dot based on fill direction
 */
static int get_dot_activation_order(int dot_index, int total_dots, dot_ring_fill_dir_t fill_dir) {
  if (fill_dir == DOT_RING_FILL_CLOCKWISE) {
    return get_dot_activation_order_clockwise(dot_index, total_dots);
  } else {
    return get_dot_activation_order_split(dot_index, total_dots);
  }
}

// ========================================================================
// Animation Callbacks
// ========================================================================

static void fill_anim_cb(void* var, int32_t value) {
  dot_ring_t* ring = (dot_ring_t*)var;

  ring->active_count = (uint16_t)value;

  // Activate dots progressively based on activation order
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    int activation_order = get_dot_activation_order(i, ring->dot_count, ring->fill_dir);
    bool should_be_active = activation_order < value;

    // Only trigger animation when dot transitions from inactive to active
    if (should_be_active && !ring->dot_active[i]) {
      ring->dot_active[i] = true;
      activate_dot(ring, i);
    }
  }
}

static void fill_anim_complete_cb(lv_anim_t* a) {
  dot_ring_t* ring = (dot_ring_t*)lv_anim_get_user_data(a);

  if (ring) {
    ring->is_animating = false;

    if (ring->complete_cb) {
      ring->complete_cb(ring->user_data);
    }
  }
}

static void activate_dot(dot_ring_t* ring, int dot_index) {
  if (dot_index < 0 || dot_index >= (int)ring->dot_count) {
    return;
  }

  lv_obj_t* dot = ring->dots[dot_index];
  if (!dot) {
    return;
  }

  // Store context for callback
  ring->dot_contexts[dot_index].ring = ring;
  ring->dot_contexts[dot_index].dot_index = dot_index;

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &ring->dot_contexts[dot_index]);
  lv_anim_set_values(&anim, 0, 255);
  lv_anim_set_duration(&anim, DOT_ACTIVATE_DURATION_MS);
  lv_anim_set_exec_cb(&anim, dot_activate_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);
}

static void dot_activate_anim_cb(void* var, int32_t value) {
  dot_ring_anim_ctx_t* ctx = (dot_ring_anim_ctx_t*)var;
  if (!ctx || !ctx->ring) {
    return;
  }

  dot_ring_t* ring = (dot_ring_t*)ctx->ring;
  int dot_index = ctx->dot_index;

  lv_obj_t* dot = ring->dots[dot_index];
  if (!dot) {
    return;
  }

  int progress = value;  // 0 to 255

  // Interpolate size from 2px to 8px
  lv_coord_t size =
    (lv_coord_t)(DOT_RING_DOT_SIZE_INACTIVE +
                 ((DOT_RING_DOT_SIZE_ACTIVE - DOT_RING_DOT_SIZE_INACTIVE) * progress) / 255);
  lv_obj_set_size(dot, size, size);

  // Reposition to keep centered
  lv_obj_set_pos(dot, ring->dot_centers_x[dot_index] - (size / 2),
                 ring->dot_centers_y[dot_index] - (size / 2));

  // Interpolate color from inactive grey to active color
  uint32_t target_color = get_color_hex(ring->active_color);

  uint8_t start_r = (COLOR_INACTIVE >> 16) & 0xFF;  // 0x55
  uint8_t start_g = (COLOR_INACTIVE >> 8) & 0xFF;   // 0x55
  uint8_t start_b = COLOR_INACTIVE & 0xFF;          // 0x55

  uint8_t end_r = (target_color >> 16) & 0xFF;
  uint8_t end_g = (target_color >> 8) & 0xFF;
  uint8_t end_b = target_color & 0xFF;

  uint8_t r = (uint8_t)(start_r + ((int)end_r - (int)start_r) * progress / 255);
  uint8_t g = (uint8_t)(start_g + ((int)end_g - (int)start_g) * progress / 255);
  uint8_t b = (uint8_t)(start_b + ((int)end_b - (int)start_b) * progress / 255);

  lv_obj_set_style_bg_color(dot, lv_color_make(r, g, b), 0);

  // Interpolate opacity from 50% to 100%
  lv_opa_t opa = (lv_opa_t)(INACTIVE_OPA + ((LV_OPA_COVER - INACTIVE_OPA) * progress) / 255);
  lv_obj_set_style_bg_opa(dot, opa, 0);
}
