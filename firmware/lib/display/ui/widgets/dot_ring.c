/**
 * @file dot_ring.c
 * @brief Reusable dot ring widget implementation
 */

#include "dot_ring.h"

#include "assert.h"
#include "log.h"

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
#define DOT_ACTIVATE_DURATION_MS      100
#define DOT_HOLD_ACTIVATE_DURATION_MS 60
#define DOT_HOLD_FAST_START_DOT_COUNT 13
#define DOT_RELEASE_MIN_DURATION_MS   120
#define DOT_RELEASE_DURATION_PERCENT  80
#define DOT_RING_SHARED_STORAGE_SLOTS 4

struct dot_ring_storage {
  bool in_use;
  lv_obj_t* dots[DOT_RING_MAX_DOTS];
  lv_coord_t dot_centers_x[DOT_RING_MAX_DOTS];
  lv_coord_t dot_centers_y[DOT_RING_MAX_DOTS];
  bool dot_active[DOT_RING_MAX_DOTS];
  dot_ring_anim_ctx_t dot_contexts[DOT_RING_MAX_DOTS];
};

static dot_ring_storage_t dot_ring_storage_pool[DOT_RING_SHARED_STORAGE_SLOTS];

// Forward declarations
static void progress_anim_cb(void* var, int32_t value);
static void progress_anim_complete_cb(lv_anim_t* a);
static void activate_dot(dot_ring_t* ring, int dot_index);
static void dot_activate_anim_cb(void* var, int32_t value);
static uint32_t get_color_hex(dot_ring_color_t color);
static int get_dot_activation_order(int dot_index, int total_dots, dot_ring_fill_dir_t fill_dir);
static void set_dot_visual_state(dot_ring_t* ring, int dot_index, bool should_be_active);
static void set_active_dot_count(dot_ring_t* ring, uint16_t active_count, bool force_visual_state);
static void clear_progress_anim_state(dot_ring_t* ring);
static void stop_dot_activation_anims(dot_ring_t* ring);
static void cancel_progress_anim(dot_ring_t* ring);
static bool should_complete_forward_fill_on_release(const dot_ring_t* ring);
static void complete_forward_fill_now(dot_ring_t* ring);
static bool should_use_fast_start_activation(const dot_ring_t* ring, int dot_index);
static void dot_ring_animate_fill_internal(dot_ring_t* ring, uint8_t target_percent,
                                           uint32_t duration_ms, dot_ring_color_t color,
                                           dot_ring_fill_dir_t fill_dir,
                                           dot_ring_complete_cb_t complete_cb, void* user_data,
                                           bool resume_from_current);
static dot_ring_storage_t* get_storage(const dot_ring_t* ring);
static dot_ring_storage_t* acquire_storage(void);
static void release_storage(dot_ring_t* ring);

static dot_ring_storage_t* get_storage(const dot_ring_t* ring) {
  if (!ring || !ring->storage) {
    return NULL;
  }

  return ring->storage;
}

static dot_ring_storage_t* acquire_storage(void) {
  for (size_t i = 0; i < DOT_RING_SHARED_STORAGE_SLOTS; i++) {
    if (!dot_ring_storage_pool[i].in_use) {
      memset(&dot_ring_storage_pool[i], 0, sizeof(dot_ring_storage_pool[i]));
      dot_ring_storage_pool[i].in_use = true;
      return &dot_ring_storage_pool[i];
    }
  }

  return NULL;
}

static void release_storage(dot_ring_t* ring) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!storage) {
    return;
  }

  memset(storage, 0, sizeof(*storage));
  ring->storage = NULL;
}

void dot_ring_create(lv_obj_t* parent, dot_ring_t* ring) {
  ASSERT(parent != NULL);
  ASSERT(ring != NULL);

  if (ring->is_initialized) {
    dot_ring_destroy(ring);
  }

  memset(ring, 0, sizeof(dot_ring_t));
  ring->parent = parent;
  ring->storage = acquire_storage();
  if (!ring->storage) {
    LOGE("Failed to acquire shared dot_ring storage");
    return;
  }

  dot_ring_storage_t* storage = ring->storage;

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
    storage->dot_centers_x[i] = dot_x;
    storage->dot_centers_y[i] = dot_y;

    // Create dot object - start with inactive (small) size
    storage->dots[i] = lv_obj_create(parent);
    if (!storage->dots[i]) {
      for (uint16_t j = 0; j < i; j++) {
        if (storage->dots[j]) {
          lv_obj_del(storage->dots[j]);
          storage->dots[j] = NULL;
        }
      }
      release_storage(ring);
      memset(ring, 0, sizeof(dot_ring_t));
      return;
    }

    lv_obj_set_size(storage->dots[i], DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
    lv_obj_set_pos(storage->dots[i], dot_x - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                   dot_y - (DOT_RING_DOT_SIZE_INACTIVE / 2));
    lv_obj_set_style_radius(storage->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(storage->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
    lv_obj_set_style_bg_opa(storage->dots[i], INACTIVE_OPA, 0);
    lv_obj_set_style_border_opa(storage->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(storage->dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(storage->dots[i], LV_OBJ_FLAG_HIDDEN);

    storage->dot_active[i] = false;
  }

  ring->is_initialized = true;
  ring->is_visible = false;
}

void dot_ring_show(dot_ring_t* ring) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !ring->is_initialized || !storage) {
    return;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (storage->dots[i]) {
      lv_obj_clear_flag(storage->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  ring->is_visible = true;
}

void dot_ring_hide(dot_ring_t* ring) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !ring->is_initialized || !storage) {
    return;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (storage->dots[i]) {
      lv_obj_add_flag(storage->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  ring->is_visible = false;
}

void dot_ring_set_percent(dot_ring_t* ring, uint8_t percent, dot_ring_color_t color,
                          dot_ring_fill_dir_t fill_dir) {
  if (!ring || !ring->is_initialized || !ring->storage) {
    return;
  }

  ring->complete_cb = NULL;
  ring->user_data = NULL;
  ring->hide_when_animation_complete = false;
  cancel_progress_anim(ring);
  stop_dot_activation_anims(ring);

  // Clamp percent
  if (percent > 100) {
    percent = 100;
  }

  ring->active_color = color;
  ring->fill_dir = fill_dir;
  ring->hide_when_animation_complete = false;

  // Calculate number of dots to activate
  uint16_t target_active = (uint16_t)((ring->dot_count * percent) / 100);
  ring->target_count = target_active;
  set_active_dot_count(ring, target_active, true);
}

void dot_ring_set_dot_state(dot_ring_t* ring, uint16_t dot_index, bool should_be_active,
                            lv_color_t active_color, lv_opa_t active_opa, lv_color_t inactive_color,
                            lv_opa_t inactive_opa) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !ring->is_initialized || !storage || dot_index >= ring->dot_count) {
    return;
  }

  lv_obj_t* dot = storage->dots[dot_index];
  if (!dot) {
    return;
  }

  bool was_active = storage->dot_active[dot_index];
  storage->dot_active[dot_index] = should_be_active;
  if (was_active != should_be_active) {
    if (should_be_active && ring->active_count < ring->dot_count) {
      ring->active_count++;
    } else if (ring->active_count > 0) {
      ring->active_count--;
    }
  }

  if (should_be_active) {
    lv_obj_set_size(dot, DOT_RING_DOT_SIZE_ACTIVE, DOT_RING_DOT_SIZE_ACTIVE);
    lv_obj_set_pos(dot, storage->dot_centers_x[dot_index] - (DOT_RING_DOT_SIZE_ACTIVE / 2),
                   storage->dot_centers_y[dot_index] - (DOT_RING_DOT_SIZE_ACTIVE / 2));
    lv_obj_set_style_bg_color(dot, active_color, 0);
    lv_obj_set_style_bg_opa(dot, active_opa, 0);
    return;
  }

  lv_obj_set_size(dot, DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
  lv_obj_set_pos(dot, storage->dot_centers_x[dot_index] - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                 storage->dot_centers_y[dot_index] - (DOT_RING_DOT_SIZE_INACTIVE / 2));
  lv_obj_set_style_bg_color(dot, inactive_color, 0);
  lv_obj_set_style_bg_opa(dot, inactive_opa, 0);
}

void dot_ring_animate_fill(dot_ring_t* ring, uint8_t target_percent, uint32_t duration_ms,
                           dot_ring_color_t color, dot_ring_fill_dir_t fill_dir,
                           dot_ring_complete_cb_t complete_cb, void* user_data) {
  dot_ring_animate_fill_internal(ring, target_percent, duration_ms, color, fill_dir, complete_cb,
                                 user_data, false);
}

void dot_ring_animate_fill_from_current(dot_ring_t* ring, uint8_t target_percent,
                                        uint32_t duration_ms, dot_ring_color_t color,
                                        dot_ring_fill_dir_t fill_dir,
                                        dot_ring_complete_cb_t complete_cb, void* user_data) {
  dot_ring_animate_fill_internal(ring, target_percent, duration_ms, color, fill_dir, complete_cb,
                                 user_data, true);
}

static void dot_ring_animate_fill_internal(dot_ring_t* ring, uint8_t target_percent,
                                           uint32_t duration_ms, dot_ring_color_t color,
                                           dot_ring_fill_dir_t fill_dir,
                                           dot_ring_complete_cb_t complete_cb, void* user_data,
                                           bool resume_from_current) {
  if (!ring || !ring->is_initialized || !ring->storage) {
    return;
  }

  // Stop any existing animation
  ring->complete_cb = NULL;
  ring->user_data = NULL;
  ring->hide_when_animation_complete = false;
  cancel_progress_anim(ring);
  stop_dot_activation_anims(ring);

  // Clamp percent
  if (target_percent > 100) {
    target_percent = 100;
  }

  ring->active_color = color;
  ring->fill_dir = fill_dir;
  ring->complete_cb = complete_cb;
  ring->user_data = user_data;

  // Calculate target dot count
  uint16_t target_dots = (uint16_t)((ring->dot_count * target_percent) / 100);
  ring->target_count = target_dots;

  uint16_t start_dots = resume_from_current ? ring->active_count : 0;
  if (start_dots > target_dots) {
    start_dots = target_dots;
  }

  set_active_dot_count(ring, start_dots, true);

  if (start_dots >= target_dots) {
    ring->complete_cb = NULL;
    ring->user_data = NULL;
    clear_progress_anim_state(ring);

    if (complete_cb) {
      complete_cb(user_data);
    }
    return;
  }

  uint32_t remaining_duration_ms = duration_ms;
  if (target_dots > 0) {
    remaining_duration_ms = (duration_ms * (target_dots - start_dots)) / target_dots;
  }
  if (remaining_duration_ms == 0) {
    remaining_duration_ms = 1;
  }

  ring->is_animating = true;
  ring->hide_when_animation_complete = false;
  ring->anim_type = DOT_RING_ANIM_FORWARD;
  ring->anim_start_tick_ms = lv_tick_get();
  ring->anim_duration_ms = remaining_duration_ms;

  // Start fill animation
  lv_anim_init(&ring->fill_anim);
  lv_anim_set_var(&ring->fill_anim, ring);
  lv_anim_set_user_data(&ring->fill_anim, ring);
  lv_anim_set_values(&ring->fill_anim, start_dots, target_dots);
  lv_anim_set_duration(&ring->fill_anim, remaining_duration_ms);
  lv_anim_set_exec_cb(&ring->fill_anim, progress_anim_cb);
  lv_anim_set_ready_cb(&ring->fill_anim, progress_anim_complete_cb);
  lv_anim_set_path_cb(&ring->fill_anim, lv_anim_path_custom_bezier3);
  lv_anim_set_bezier3_param(&ring->fill_anim, LV_BEZIER_VAL_FLOAT(0), LV_BEZIER_VAL_FLOAT(0),
                            LV_BEZIER_VAL_FLOAT(0.4), LV_BEZIER_VAL_FLOAT(1));
  lv_anim_start(&ring->fill_anim);
}

bool dot_ring_animate_release(dot_ring_t* ring, uint32_t full_duration_ms) {
  if (!ring || !ring->is_initialized || !ring->storage) {
    return false;
  }

  if (should_complete_forward_fill_on_release(ring)) {
    complete_forward_fill_now(ring);
    return true;
  }

  uint16_t current_count = ring->active_count;

  ring->complete_cb = NULL;
  ring->user_data = NULL;
  ring->hide_when_animation_complete = false;
  cancel_progress_anim(ring);
  stop_dot_activation_anims(ring);

  if (ring->dot_count == 0 || current_count == 0 || full_duration_ms == 0) {
    set_active_dot_count(ring, 0, true);
    ring->target_count = 0;
    dot_ring_hide(ring);
    return false;
  }

  uint32_t duration_ms = (full_duration_ms * current_count) / ring->dot_count;
  duration_ms = (duration_ms * DOT_RELEASE_DURATION_PERCENT) / 100;
  if (duration_ms < DOT_RELEASE_MIN_DURATION_MS) {
    duration_ms = DOT_RELEASE_MIN_DURATION_MS;
  }

  ring->target_count = 0;
  ring->is_animating = true;
  ring->hide_when_animation_complete = true;
  ring->anim_type = DOT_RING_ANIM_REVERSE;
  ring->anim_start_tick_ms = lv_tick_get();
  ring->anim_duration_ms = duration_ms;

  lv_anim_init(&ring->fill_anim);
  lv_anim_set_var(&ring->fill_anim, ring);
  lv_anim_set_user_data(&ring->fill_anim, ring);
  lv_anim_set_values(&ring->fill_anim, current_count, 0);
  lv_anim_set_duration(&ring->fill_anim, duration_ms);
  lv_anim_set_exec_cb(&ring->fill_anim, progress_anim_cb);
  lv_anim_set_ready_cb(&ring->fill_anim, progress_anim_complete_cb);
  lv_anim_set_path_cb(&ring->fill_anim, lv_anim_path_custom_bezier3);
  lv_anim_set_bezier3_param(&ring->fill_anim, LV_BEZIER_VAL_FLOAT(0.6), LV_BEZIER_VAL_FLOAT(0),
                            LV_BEZIER_VAL_FLOAT(1), LV_BEZIER_VAL_FLOAT(0.8));
  lv_anim_start(&ring->fill_anim);

  return false;
}

void dot_ring_stop(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized || !ring->storage) {
    return;
  }

  // Clear the callback BEFORE stopping animation to prevent it from firing
  // when the animation is deleted (lv_anim_del may trigger ready callback)
  ring->complete_cb = NULL;
  ring->user_data = NULL;
  ring->hide_when_animation_complete = false;

  // Stop fill animation
  cancel_progress_anim(ring);

  // Stop all dot animations and reset to inactive (small, grey)
  stop_dot_activation_anims(ring);

  ring->target_count = 0;
  set_active_dot_count(ring, 0, true);
}

void dot_ring_reset(dot_ring_t* ring) {
  if (!ring || !ring->is_initialized || !ring->storage) {
    return;
  }

  // Stop any animations
  dot_ring_stop(ring);
}

void dot_ring_destroy(dot_ring_t* ring) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !ring->is_initialized || !storage) {
    return;
  }

  // Stop any active animation
  dot_ring_stop(ring);

  // Delete all dot objects
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    if (storage->dots[i]) {
      lv_obj_del(storage->dots[i]);
      storage->dots[i] = NULL;
    }
  }

  release_storage(ring);

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
 * @brief Get activation order for a dot (clockwise fill starting at top)
 *
 * Dots activate from the top center, going clockwise around the ring.
 *
 * @param dot_index The dot's index in the ring (0 = bottom center)
 * @param total_dots Total number of dots in the ring
 * @return Activation order (0 = first to activate)
 */
static int get_dot_activation_order_clockwise_top(int dot_index, int total_dots) {
  int top_index = total_dots / 2;
  int rotated_index = dot_index - top_index;

  if (rotated_index < 0) {
    rotated_index += total_dots;
  }

  return rotated_index;
}

/**
 * @brief Get activation order for a dot based on fill direction
 */
static int get_dot_activation_order(int dot_index, int total_dots, dot_ring_fill_dir_t fill_dir) {
  switch (fill_dir) {
    case DOT_RING_FILL_CLOCKWISE:
      return get_dot_activation_order_clockwise(dot_index, total_dots);
    case DOT_RING_FILL_CLOCKWISE_TOP:
      return get_dot_activation_order_clockwise_top(dot_index, total_dots);
    case DOT_RING_FILL_SPLIT:
    default:
      return get_dot_activation_order_split(dot_index, total_dots);
  }
}

// ========================================================================
// Animation Callbacks
// ========================================================================

static void progress_anim_cb(void* var, int32_t value) {
  dot_ring_t* ring = (dot_ring_t*)var;
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !storage) {
    return;
  }

  uint16_t previous_count = ring->active_count;

  int32_t clamped_value = value;
  if (clamped_value < 0) {
    clamped_value = 0;
  }

  if (clamped_value > ring->dot_count) {
    clamped_value = ring->dot_count;
  }

  uint16_t next_count = (uint16_t)clamped_value;
  if (next_count == previous_count) {
    return;
  }

  ring->active_count = next_count;

  if (next_count < previous_count) {
    set_active_dot_count(ring, next_count, false);
    return;
  }

  // Activate dots progressively based on activation order.
  for (uint16_t i = 0; i < ring->dot_count; i++) {
    int activation_order = get_dot_activation_order(i, ring->dot_count, ring->fill_dir);
    bool should_be_active = activation_order < clamped_value;

    if (should_be_active && !storage->dot_active[i]) {
      storage->dot_active[i] = true;
      activate_dot(ring, i);
    }
  }
}

static void progress_anim_complete_cb(lv_anim_t* a) {
  dot_ring_t* ring = (dot_ring_t*)lv_anim_get_user_data(a);

  if (ring) {
    if (ring->suppress_ready_cb) {
      ring->suppress_ready_cb = false;
      clear_progress_anim_state(ring);
      return;
    }

    set_active_dot_count(ring, ring->target_count, true);
    clear_progress_anim_state(ring);
    dot_ring_complete_cb_t complete_cb = ring->complete_cb;
    void* user_data = ring->user_data;
    bool should_hide = ring->hide_when_animation_complete && ring->target_count == 0;

    ring->complete_cb = NULL;
    ring->user_data = NULL;
    ring->hide_when_animation_complete = false;

    if (should_hide) {
      dot_ring_hide(ring);
    }

    if (complete_cb) {
      complete_cb(user_data);
    }
  }
}

static void activate_dot(dot_ring_t* ring, int dot_index) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!storage) {
    return;
  }

  if (dot_index < 0 || dot_index >= (int)ring->dot_count) {
    return;
  }

  lv_obj_t* dot = storage->dots[dot_index];
  if (!dot) {
    return;
  }

  if (should_use_fast_start_activation(ring, dot_index)) {
    set_dot_visual_state(ring, dot_index, true);
    return;
  }

  uint32_t activation_duration_ms = DOT_ACTIVATE_DURATION_MS;
  if (ring->fill_dir == DOT_RING_FILL_SPLIT && ring->anim_type == DOT_RING_ANIM_FORWARD) {
    activation_duration_ms = DOT_HOLD_ACTIVATE_DURATION_MS;
  }

  // Store context for callback
  storage->dot_contexts[dot_index].ring = ring;
  storage->dot_contexts[dot_index].dot_index = dot_index;

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &storage->dot_contexts[dot_index]);
  lv_anim_set_values(&anim, 0, 255);
  lv_anim_set_duration(&anim, activation_duration_ms);
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
  dot_ring_storage_t* storage = get_storage(ring);
  if (!storage) {
    return;
  }

  int dot_index = ctx->dot_index;

  lv_obj_t* dot = storage->dots[dot_index];
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
  lv_obj_set_pos(dot, storage->dot_centers_x[dot_index] - (size / 2),
                 storage->dot_centers_y[dot_index] - (size / 2));

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

static void set_dot_visual_state(dot_ring_t* ring, int dot_index, bool should_be_active) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!storage) {
    return;
  }

  lv_obj_t* dot = storage->dots[dot_index];
  if (!dot) {
    return;
  }

  storage->dot_active[dot_index] = should_be_active;

  if (should_be_active) {
    uint32_t active_color = get_color_hex(ring->active_color);
    lv_obj_set_size(dot, DOT_RING_DOT_SIZE_ACTIVE, DOT_RING_DOT_SIZE_ACTIVE);
    lv_obj_set_pos(dot, storage->dot_centers_x[dot_index] - (DOT_RING_DOT_SIZE_ACTIVE / 2),
                   storage->dot_centers_y[dot_index] - (DOT_RING_DOT_SIZE_ACTIVE / 2));
    lv_obj_set_style_bg_color(dot, lv_color_hex(active_color), 0);
    lv_obj_set_style_bg_opa(dot, LV_OPA_COVER, 0);
  } else {
    lv_obj_set_size(dot, DOT_RING_DOT_SIZE_INACTIVE, DOT_RING_DOT_SIZE_INACTIVE);
    lv_obj_set_pos(dot, storage->dot_centers_x[dot_index] - (DOT_RING_DOT_SIZE_INACTIVE / 2),
                   storage->dot_centers_y[dot_index] - (DOT_RING_DOT_SIZE_INACTIVE / 2));
    lv_obj_set_style_bg_color(dot, lv_color_hex(COLOR_INACTIVE), 0);
    lv_obj_set_style_bg_opa(dot, INACTIVE_OPA, 0);
  }
}

static void clear_progress_anim_state(dot_ring_t* ring) {
  if (!ring) {
    return;
  }

  ring->is_animating = false;
  ring->anim_type = DOT_RING_ANIM_NONE;
  ring->anim_start_tick_ms = 0;
  ring->anim_duration_ms = 0;
}

static void stop_dot_activation_anims(dot_ring_t* ring) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !storage) {
    return;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    lv_anim_del(&storage->dot_contexts[i], dot_activate_anim_cb);
  }
}

static void cancel_progress_anim(dot_ring_t* ring) {
  if (!ring) {
    return;
  }

  bool was_animating = ring->is_animating;
  ring->suppress_ready_cb = was_animating;
  lv_anim_del(ring, progress_anim_cb);
  ring->suppress_ready_cb = false;
  clear_progress_anim_state(ring);
}

static bool should_complete_forward_fill_on_release(const dot_ring_t* ring) {
  if (!ring || ring->anim_type != DOT_RING_ANIM_FORWARD || !ring->complete_cb) {
    return false;
  }

  if (ring->anim_duration_ms == 0) {
    return true;
  }

  return lv_tick_elaps(ring->anim_start_tick_ms) >= ring->anim_duration_ms;
}

static void complete_forward_fill_now(dot_ring_t* ring) {
  if (!ring) {
    return;
  }

  dot_ring_complete_cb_t complete_cb = ring->complete_cb;
  void* user_data = ring->user_data;

  ring->complete_cb = NULL;
  ring->user_data = NULL;
  ring->hide_when_animation_complete = false;

  cancel_progress_anim(ring);
  stop_dot_activation_anims(ring);
  set_active_dot_count(ring, ring->target_count, true);

  if (complete_cb) {
    complete_cb(user_data);
  }
}

static bool should_use_fast_start_activation(const dot_ring_t* ring, int dot_index) {
  if (!ring || ring->fill_dir != DOT_RING_FILL_SPLIT || ring->anim_type != DOT_RING_ANIM_FORWARD) {
    return false;
  }

  return get_dot_activation_order(dot_index, ring->dot_count, ring->fill_dir) <
         DOT_HOLD_FAST_START_DOT_COUNT;
}

static void set_active_dot_count(dot_ring_t* ring, uint16_t active_count, bool force_visual_state) {
  dot_ring_storage_t* storage = get_storage(ring);
  if (!ring || !storage) {
    return;
  }

  if (active_count > ring->dot_count) {
    active_count = ring->dot_count;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    int activation_order = get_dot_activation_order(i, ring->dot_count, ring->fill_dir);
    bool should_be_active = activation_order < (int)active_count;
    if (force_visual_state || storage->dot_active[i] != should_be_active) {
      set_dot_visual_state(ring, i, should_be_active);
    }
  }

  ring->active_count = active_count;
}
