/**
 * @file mfg_starfield_fps.c
 * @brief Starfield FPS animation widget implementation
 */

#include "mfg_starfield_fps.h"

#include "assert.h"
#include "secure_rng.h"
#include "ui.h"

#include <stddef.h>
#include <stdio.h>

// Star configuration
#define STAR_LAYERS       3
#define STAR_MIN_SIZE     3   // Smallest star size (layer 0)
#define STAR_MIN_SPEED    1   // Slowest star speed (layer 0)
#define STAR_SPAWN_OFFSET 16  // Random offset when spawning at right edge
#define STAR_OFFSCREEN_X  -4  // X position considered off-screen left

// Opacity values for star layers
#define STAR_OPA_LAYER_0        140  // Far stars are dimmer
#define STAR_OPA_LAYER_1        190  // Medium distance stars
#define STAR_OPA_LAYER_2        255  // Near stars are brightest
#define STAR_OPA_MIN            60   // Minimum opacity during twinkle
#define STAR_OPA_TWINKLE_OFFSET 70   // Offset for twinkle calculation

// Timer intervals
#define FPS_UPDATE_INTERVAL_MS     250   // How often to update FPS display
#define STAR_ANIMATION_INTERVAL_MS 16    // Animation frame rate (~60 FPS)
#define PHASE_DURATION_MS          7500  // 7.5 seconds per test phase

// Random color generation ranges
#define STAR_COLOR_BASE  180  // Base value for star RGB components
#define STAR_COLOR_RANGE 76   // Random range added to base (180-255)

// Twinkle animation
#define TWINKLE_PHASE_MASK 0x3FF  // Mask for initial phase randomization
#define TWINKLE_SHIFT      3      // Bit shift for twinkle calculation
#define TWINKLE_RANGE_MASK 0x3F   // Mask for twinkle range
#define TWINKLE_BASE       40     // Base twinkle offset
#define TWINKLE_SPEED_MULT 2      // Multiplier for twinkle speed per layer

// FPS label positioning
#define FPS_LABEL_Y_OFFSET 80

// Font
#define FONT_FPS_LABEL (&cash_sans_mono_regular_20)

static inline uint16_t ui_rand(void) {
  return crypto_rand_short();
}

static inline int16_t rand_range(int16_t a, int16_t b) {
  if (b <= a) {
    return a;
  }
  return a + (int16_t)(ui_rand() % (uint16_t)(b - a + 1));
}

static inline lv_color_t get_star_color(void) {
  uint8_t r = (uint8_t)(STAR_COLOR_BASE + (ui_rand() % STAR_COLOR_RANGE));
  uint8_t g = (uint8_t)(STAR_COLOR_BASE + (ui_rand() % STAR_COLOR_RANGE));
  uint8_t b = (uint8_t)(STAR_COLOR_BASE + (ui_rand() % STAR_COLOR_RANGE));
  return lv_color_make(r, g, b);
}

static inline uint8_t star_size(uint8_t layer) {
  return STAR_MIN_SIZE + layer;
}

static inline uint8_t star_speed(uint8_t layer) {
  return STAR_MIN_SPEED + layer;
}

static void toggle_test_phase(lv_timer_t* timer) {
  mfg_starfield_fps_t* widget = (mfg_starfield_fps_t*)lv_timer_get_user_data(timer);
  if (!widget || !widget->star_layer) {
    return;
  }

  widget->full_invalidate_mode = !widget->full_invalidate_mode;
}

static void update_fps_label(lv_timer_t* timer) {
  mfg_starfield_fps_t* widget = (mfg_starfield_fps_t*)lv_timer_get_user_data(timer);
  if (!widget || !widget->fps_label) {
    return;
  }

  uint32_t flush_rate = ui_get_fps();
  uint32_t effective_fps = ui_get_effective_fps();
  char fps_text[128];

  if (widget->full_invalidate_mode) {
    if (effective_fps > 0) {
      snprintf(fps_text, sizeof(fps_text), "Full Screen Updates\n%lu full fps\n%u stars",
               (unsigned long)effective_fps, STARFIELD_FPS_STAR_COUNT);
    } else {
      snprintf(fps_text, sizeof(fps_text), "Full Screen Updates\n-- full fps\n%u stars",
               STARFIELD_FPS_STAR_COUNT);
    }
  } else {
    if (flush_rate > 0) {
      snprintf(fps_text, sizeof(fps_text), "Partial Updates\n%lu flushes/sec\n%u stars",
               (unsigned long)flush_rate, STARFIELD_FPS_STAR_COUNT);
    } else {
      snprintf(fps_text, sizeof(fps_text), "Partial Updates\n-- flushes/sec\n%u stars",
               STARFIELD_FPS_STAR_COUNT);
    }
  }

  lv_label_set_text(widget->fps_label, fps_text);
}

static void spawn_star(mfg_starfield_fps_t* widget, uint16_t i, lv_coord_t w, lv_coord_t h,
                       bool at_right_edge) {
  widget->star_layer_idx[i] = (uint8_t)(ui_rand() % STAR_LAYERS);
  widget->twinkle_phase[i] = (uint16_t)(ui_rand() & TWINKLE_PHASE_MASK);

  lv_coord_t y = (h > 0) ? (lv_coord_t)rand_range(0, (int16_t)(h - 1)) : 0;
  lv_coord_t x = at_right_edge ? (lv_coord_t)(w + rand_range(0, STAR_SPAWN_OFFSET))
                               : (w > 0 ? (lv_coord_t)rand_range(0, (int16_t)(w - 1)) : 0);

  if (!widget->stars[i]) {
    widget->stars[i] = lv_obj_create(widget->star_layer);
    if (!widget->stars[i]) {
      return;
    }
    lv_obj_remove_style_all(widget->stars[i]);
    lv_obj_set_style_border_width(widget->stars[i], 0, 0);
    lv_obj_set_style_radius(widget->stars[i], 0, 0);
    lv_obj_add_flag(widget->stars[i], LV_OBJ_FLAG_IGNORE_LAYOUT);
  }

  lv_obj_set_style_bg_color(widget->stars[i], get_star_color(), 0);
  uint8_t size = star_size(widget->star_layer_idx[i]);
  lv_obj_set_size(widget->stars[i], size, size);

  // Far stars are dimmer
  uint8_t base_opa = (widget->star_layer_idx[i] == 0)   ? STAR_OPA_LAYER_0
                     : (widget->star_layer_idx[i] == 1) ? STAR_OPA_LAYER_1
                                                        : STAR_OPA_LAYER_2;
  lv_obj_set_style_bg_opa(widget->stars[i], base_opa, 0);

  lv_obj_set_pos(widget->stars[i], x, y);
}

static void animate_stars(lv_timer_t* timer) {
  mfg_starfield_fps_t* widget = (mfg_starfield_fps_t*)lv_timer_get_user_data(timer);
  if (!widget || !widget->parent || !widget->star_layer) {
    return;
  }

  lv_coord_t w = lv_obj_get_width(widget->star_layer);
  lv_coord_t h = lv_obj_get_height(widget->star_layer);

  for (uint16_t i = 0; i < STARFIELD_FPS_STAR_COUNT; i++) {
    if (!widget->stars[i]) {
      continue;
    }

    // Move star left based on its depth layer
    uint8_t speed = star_speed(widget->star_layer_idx[i]);
    lv_coord_t x = lv_obj_get_x(widget->stars[i]) - speed;

    if (x < STAR_OFFSCREEN_X) {
      spawn_star(widget, i, w, h, true);
    } else {
      lv_obj_set_x(widget->stars[i], x);
    }

    // Animate twinkle effect
    widget->twinkle_phase[i] += (uint16_t)((widget->star_layer_idx[i] + 1) * TWINKLE_SPEED_MULT);
    uint8_t twinkle =
      (uint8_t)(TWINKLE_BASE + ((widget->twinkle_phase[i] >> TWINKLE_SHIFT) & TWINKLE_RANGE_MASK));
    uint8_t base_opa = (widget->star_layer_idx[i] == 0)   ? STAR_OPA_LAYER_0
                       : (widget->star_layer_idx[i] == 1) ? STAR_OPA_LAYER_1
                                                          : STAR_OPA_LAYER_2;
    int opacity = base_opa + twinkle - STAR_OPA_TWINKLE_OFFSET;
    if (opacity < STAR_OPA_MIN) {
      opacity = STAR_OPA_MIN;
    }
    if (opacity > 255) {
      opacity = 255;
    }
    lv_obj_set_style_bg_opa(widget->stars[i], (lv_opa_t)opacity, 0);
  }

  if (widget->full_invalidate_mode) {
    lv_obj_invalidate(widget->parent);
  }
}

void mfg_starfield_fps_create(lv_obj_t* parent, mfg_starfield_fps_t* widget) {
  ASSERT(parent != NULL);
  ASSERT(widget != NULL);
  ASSERT(!widget->is_initialized);

  widget->parent = parent;
  widget->full_invalidate_mode = false;

  // Set black background on parent
  lv_obj_set_style_bg_color(parent, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, 0);

  // Create star layer
  widget->star_layer = lv_obj_create(parent);
  if (!widget->star_layer) {
    return;
  }
  lv_obj_remove_style_all(widget->star_layer);
  lv_obj_set_size(widget->star_layer, LV_PCT(100), LV_PCT(100));
  lv_obj_add_flag(widget->star_layer, LV_OBJ_FLAG_IGNORE_LAYOUT);
  lv_obj_clear_flag(widget->star_layer, LV_OBJ_FLAG_CLICKABLE);

  // Spawn all stars
  lv_coord_t w = lv_obj_get_width(parent);
  lv_coord_t h = lv_obj_get_height(parent);
  for (uint16_t i = 0; i < STARFIELD_FPS_STAR_COUNT; i++) {
    spawn_star(widget, i, w, h, false);
  }

  // Create FPS label
  widget->fps_label = lv_label_create(parent);
  if (!widget->fps_label) {
    return;
  }
  lv_obj_set_style_text_color(widget->fps_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(widget->fps_label, FONT_FPS_LABEL, 0);
  lv_obj_set_style_text_align(widget->fps_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(widget->fps_label, LV_ALIGN_TOP_MID, 0, FPS_LABEL_Y_OFFSET);
  lv_label_set_text(widget->fps_label, "-- FPS");

  // Create timers with widget as user_data
  widget->fps_timer = lv_timer_create(update_fps_label, FPS_UPDATE_INTERVAL_MS, widget);
  widget->star_timer = lv_timer_create(animate_stars, STAR_ANIMATION_INTERVAL_MS, widget);
  widget->phase_timer = lv_timer_create(toggle_test_phase, PHASE_DURATION_MS, widget);

  // Initial FPS update
  update_fps_label(widget->fps_timer);

  widget->is_initialized = true;
}

void mfg_starfield_fps_destroy(mfg_starfield_fps_t* widget) {
  if (!widget || !widget->is_initialized) {
    return;
  }

  // Delete timers
  if (widget->fps_timer) {
    lv_timer_del(widget->fps_timer);
    widget->fps_timer = NULL;
  }
  if (widget->star_timer) {
    lv_timer_del(widget->star_timer);
    widget->star_timer = NULL;
  }
  if (widget->phase_timer) {
    lv_timer_del(widget->phase_timer);
    widget->phase_timer = NULL;
  }

  // Delete FPS label
  if (widget->fps_label) {
    lv_obj_del(widget->fps_label);
    widget->fps_label = NULL;
  }

  // Delete star layer (and all stars)
  if (widget->star_layer) {
    lv_obj_del(widget->star_layer);
    widget->star_layer = NULL;
    for (uint16_t i = 0; i < STARFIELD_FPS_STAR_COUNT; i++) {
      widget->stars[i] = NULL;
    }
  }

  // Reset state
  for (uint16_t i = 0; i < STARFIELD_FPS_STAR_COUNT; i++) {
    widget->star_layer_idx[i] = 0;
    widget->twinkle_phase[i] = 0;
  }
  widget->full_invalidate_mode = false;
  widget->parent = NULL;
  widget->is_initialized = false;
}
