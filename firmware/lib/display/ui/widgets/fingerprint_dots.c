/**
 * @file fingerprint_dots.c
 * @brief Fingerprint enrollment dots widget implementation
 */

#include "fingerprint_dots.h"

#include "assert.h"

#include <string.h>

// Colors
#define COLOR_ACTIVE   0xFFFFFF  // White (active/mid dots)
#define COLOR_INACTIVE 0x555555  // Grey (inactive dots)

// Opacity levels (INACTIVE_OPA matches scan screen's orbital dots)
#define INACTIVE_OPA LV_OPA_COVER
#define MID_OPA      LV_OPA_80

// Animation timing for individual dot activation
#define DOT_ACTIVATE_DURATION_MS 100

// Within the active range, every Nth dot goes to full, the rest start at mid
// e.g., MID_PATTERN_SKIP = 3 means 1 in 3 goes full, 2 in 3 start at mid
#define MID_PATTERN_SKIP 3

// SVG dimensions for scaling (scaled 25% larger: 128*1.25=160, 138*1.25=172.5≈173)
#define SVG_WIDTH  160
#define SVG_HEIGHT 173

// Forward declarations
static void dot_activate_anim_cb(void* var, int32_t value);
static void dot_mid_anim_cb(void* var, int32_t value);
static void dot_mid_to_full_anim_cb(void* var, int32_t value);
static void activate_dot(fingerprint_dots_t* dots, int dot_index);
static void activate_dot_mid(fingerprint_dots_t* dots, int dot_index);
static void activate_dot_mid_to_full(fingerprint_dots_t* dots, int dot_index);

// Dot coordinates extracted from fingerprint_enroll.svg, scaled 25% larger
// Original coords multiplied by 1.25
// Format: {x, y} where x,y are the center coordinates in scaled SVG space
static const struct {
  uint8_t x;
  uint8_t y;
} dot_positions[FINGERPRINT_DOTS_COUNT] = {
  {5, 43},    {5, 55},    {5, 68},    {5, 80},    {5, 93},    {5, 105},   {5, 118},   {5, 130},
  {5, 143},   {18, 30},   {30, 18},   {30, 55},   {30, 68},   {30, 80},   {30, 93},   {30, 105},
  {30, 118},  {30, 130},  {30, 143},  {30, 155},  {30, 168},  {43, 5},    {43, 43},   {55, 5},
  {55, 30},   {55, 68},   {55, 80},   {55, 93},   {55, 105},  {55, 118},  {55, 130},  {55, 143},
  {55, 155},  {55, 168},  {68, 5},    {68, 30},   {68, 55},   {80, 5},    {80, 30},   {80, 55},
  {80, 80},   {80, 93},   {80, 105},  {80, 118},  {80, 130},  {80, 143},  {80, 155},  {80, 168},
  {93, 5},    {93, 30},   {93, 55},   {105, 5},   {105, 30},  {105, 68},  {105, 80},  {105, 93},
  {105, 105}, {105, 118}, {105, 130}, {105, 143}, {105, 155}, {105, 168}, {118, 5},   {118, 43},
  {130, 18},  {130, 55},  {130, 68},  {130, 80},  {130, 93},  {130, 105}, {130, 118}, {130, 130},
  {130, 143}, {130, 155}, {130, 168}, {143, 30},  {155, 43},  {155, 55},  {155, 68},  {155, 80},
  {155, 93},  {155, 105}, {155, 118}, {155, 130}, {155, 143},
};

// Activation order: sorted by distance from center (inside-out fill pattern)
static const uint8_t activation_order[FINGERPRINT_DOTS_COUNT] = {
  42, 41, 43, 55, 56, 27, 28, 69, 70, 14, 40, 44, 54, 57, 26, 29, 68, 71, 13, 15, 81, 4,
  39, 45, 53, 58, 25, 30, 67, 72, 36, 50, 12, 16, 80, 82, 3,  5,  38, 46, 52, 60, 24, 31,
  66, 74, 35, 49, 11, 17, 79, 83, 2,  6,  18, 32, 59, 73, 37, 47, 51, 61, 23, 33, 65, 75,
  34, 48, 22, 64, 10, 19, 76, 78, 84, 1,  7,  9,  21, 63, 20, 62, 0,  8,  77,
};

void fingerprint_dots_create(lv_obj_t* parent, fingerprint_dots_t* dots) {
  ASSERT(parent != NULL);
  ASSERT(dots != NULL);

  memset(dots, 0, sizeof(fingerprint_dots_t));
  dots->parent = parent;

  dots->offset_x = (LV_HOR_RES - SVG_WIDTH) / 2;
  dots->offset_y = (LV_VER_RES - SVG_HEIGHT) / 2;

  dots->container = lv_obj_create(parent);
  lv_obj_set_size(dots->container, SVG_WIDTH, SVG_HEIGHT);
  lv_obj_center(dots->container);
  lv_obj_set_style_bg_opa(dots->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(dots->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(dots->container, 0, 0);
  lv_obj_clear_flag(dots->container, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);

  for (uint16_t i = 0; i < FINGERPRINT_DOTS_COUNT; i++) {
    dots->dots[i] = lv_obj_create(dots->container);

    lv_coord_t x = dot_positions[i].x;
    lv_coord_t y = dot_positions[i].y;

    lv_obj_set_size(dots->dots[i], FINGERPRINT_DOT_SIZE_INACTIVE, FINGERPRINT_DOT_SIZE_INACTIVE);
    lv_obj_set_pos(dots->dots[i], x - (FINGERPRINT_DOT_SIZE_INACTIVE / 2),
                   y - (FINGERPRINT_DOT_SIZE_INACTIVE / 2));

    lv_obj_set_style_radius(dots->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(dots->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
    lv_obj_set_style_bg_opa(dots->dots[i], INACTIVE_OPA, 0);
    lv_obj_set_style_border_opa(dots->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(dots->dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(dots->dots[i], LV_OBJ_FLAG_HIDDEN);

    dots->dot_state[i] = FINGERPRINT_DOT_STATE_INACTIVE;
    dots->anim_ctx[i].dots = dots;
    dots->anim_ctx[i].dot_index = i;
  }

  dots->is_initialized = true;
  dots->is_visible = false;
}

void fingerprint_dots_show(fingerprint_dots_t* dots) {
  if (!dots || !dots->is_initialized) {
    return;
  }

  for (uint16_t i = 0; i < FINGERPRINT_DOTS_COUNT; i++) {
    if (dots->dots[i]) {
      lv_obj_clear_flag(dots->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  dots->is_visible = true;
}

void fingerprint_dots_hide(fingerprint_dots_t* dots) {
  if (!dots || !dots->is_initialized) {
    return;
  }

  for (uint16_t i = 0; i < FINGERPRINT_DOTS_COUNT; i++) {
    if (dots->dots[i]) {
      lv_obj_add_flag(dots->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  dots->is_visible = false;
}

void fingerprint_dots_set_percent(fingerprint_dots_t* dots, uint8_t percent) {
  if (!dots || !dots->is_initialized) {
    return;
  }

  if (percent > 100) {
    percent = 100;
  }

  uint16_t target_active = (uint16_t)((FINGERPRINT_DOTS_COUNT * percent) / 100);
  dots->active_count = target_active;

  for (uint16_t order = 0; order < FINGERPRINT_DOTS_COUNT; order++) {
    uint8_t dot_idx = activation_order[order];
    if (dot_idx >= FINGERPRINT_DOTS_COUNT || !dots->dots[dot_idx]) {
      continue;
    }

    fingerprint_dot_state_t current_state = dots->dot_state[dot_idx];
    fingerprint_dot_state_t target_state;

    if (order < target_active) {
      if (percent == 100) {
        target_state = FINGERPRINT_DOT_STATE_ACTIVE;
      } else if (current_state == FINGERPRINT_DOT_STATE_INACTIVE) {
        if ((order % MID_PATTERN_SKIP) == 0) {
          target_state = FINGERPRINT_DOT_STATE_ACTIVE;
        } else {
          target_state = FINGERPRINT_DOT_STATE_MID;
        }
      } else if (current_state == FINGERPRINT_DOT_STATE_MID) {
        target_state = FINGERPRINT_DOT_STATE_ACTIVE;
      } else {
        target_state = FINGERPRINT_DOT_STATE_ACTIVE;
      }
    } else {
      target_state = FINGERPRINT_DOT_STATE_INACTIVE;
    }

    if (target_state != current_state) {
      lv_anim_del(&dots->anim_ctx[dot_idx], dot_activate_anim_cb);
      lv_anim_del(&dots->anim_ctx[dot_idx], dot_mid_anim_cb);
      lv_anim_del(&dots->anim_ctx[dot_idx], dot_mid_to_full_anim_cb);

      if (target_state == FINGERPRINT_DOT_STATE_ACTIVE) {
        dots->dot_state[dot_idx] = FINGERPRINT_DOT_STATE_ACTIVE;
        if (current_state == FINGERPRINT_DOT_STATE_MID) {
          activate_dot_mid_to_full(dots, dot_idx);
        } else {
          activate_dot(dots, dot_idx);
        }
      } else if (target_state == FINGERPRINT_DOT_STATE_MID) {
        dots->dot_state[dot_idx] = FINGERPRINT_DOT_STATE_MID;
        activate_dot_mid(dots, dot_idx);
      } else {
        dots->dot_state[dot_idx] = FINGERPRINT_DOT_STATE_INACTIVE;

        lv_coord_t x = dot_positions[dot_idx].x;
        lv_coord_t y = dot_positions[dot_idx].y;

        lv_obj_set_size(dots->dots[dot_idx], FINGERPRINT_DOT_SIZE_INACTIVE,
                        FINGERPRINT_DOT_SIZE_INACTIVE);
        lv_obj_set_pos(dots->dots[dot_idx], x - (FINGERPRINT_DOT_SIZE_INACTIVE / 2),
                       y - (FINGERPRINT_DOT_SIZE_INACTIVE / 2));
        lv_obj_set_style_bg_color(dots->dots[dot_idx], lv_color_hex(COLOR_INACTIVE), 0);
        lv_obj_set_style_bg_opa(dots->dots[dot_idx], INACTIVE_OPA, 0);
      }
    }
  }
}

void fingerprint_dots_reset(fingerprint_dots_t* dots) {
  if (!dots || !dots->is_initialized) {
    return;
  }

  for (uint16_t i = 0; i < FINGERPRINT_DOTS_COUNT; i++) {
    lv_anim_del(&dots->anim_ctx[i], dot_activate_anim_cb);
    lv_anim_del(&dots->anim_ctx[i], dot_mid_anim_cb);
    lv_anim_del(&dots->anim_ctx[i], dot_mid_to_full_anim_cb);

    if (dots->dots[i]) {
      lv_coord_t x = dot_positions[i].x;
      lv_coord_t y = dot_positions[i].y;

      lv_obj_set_size(dots->dots[i], FINGERPRINT_DOT_SIZE_INACTIVE, FINGERPRINT_DOT_SIZE_INACTIVE);
      lv_obj_set_pos(dots->dots[i], x - (FINGERPRINT_DOT_SIZE_INACTIVE / 2),
                     y - (FINGERPRINT_DOT_SIZE_INACTIVE / 2));
      lv_obj_set_style_bg_color(dots->dots[i], lv_color_hex(COLOR_INACTIVE), 0);
      lv_obj_set_style_bg_opa(dots->dots[i], INACTIVE_OPA, 0);
    }
    dots->dot_state[i] = FINGERPRINT_DOT_STATE_INACTIVE;
  }

  dots->active_count = 0;
}

void fingerprint_dots_destroy(fingerprint_dots_t* dots) {
  if (!dots || !dots->is_initialized) {
    return;
  }

  for (uint16_t i = 0; i < FINGERPRINT_DOTS_COUNT; i++) {
    lv_anim_del(&dots->anim_ctx[i], dot_activate_anim_cb);
    lv_anim_del(&dots->anim_ctx[i], dot_mid_anim_cb);
    lv_anim_del(&dots->anim_ctx[i], dot_mid_to_full_anim_cb);
  }

  if (dots->container) {
    lv_obj_del(dots->container);
  }

  memset(dots, 0, sizeof(fingerprint_dots_t));
}

static void activate_dot(fingerprint_dots_t* dots, int dot_index) {
  if (dot_index < 0 || dot_index >= FINGERPRINT_DOTS_COUNT) {
    return;
  }

  lv_obj_t* dot = dots->dots[dot_index];
  if (!dot) {
    return;
  }

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &dots->anim_ctx[dot_index]);
  lv_anim_set_values(&anim, 0, 255);
  lv_anim_set_duration(&anim, DOT_ACTIVATE_DURATION_MS);
  lv_anim_set_exec_cb(&anim, dot_activate_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);
}

static void dot_activate_anim_cb(void* var, int32_t value) {
  fingerprint_dot_anim_ctx_t* ctx = (fingerprint_dot_anim_ctx_t*)var;
  if (!ctx || !ctx->dots) {
    return;
  }

  fingerprint_dots_t* dots = (fingerprint_dots_t*)ctx->dots;
  int dot_index = ctx->dot_index;

  if (dot_index < 0 || dot_index >= FINGERPRINT_DOTS_COUNT) {
    return;
  }

  lv_obj_t* dot = dots->dots[dot_index];
  if (!dot) {
    return;
  }

  lv_coord_t size =
    (lv_coord_t)(FINGERPRINT_DOT_SIZE_INACTIVE +
                 ((FINGERPRINT_DOT_SIZE_ACTIVE - FINGERPRINT_DOT_SIZE_INACTIVE) * value) / 255);
  lv_obj_set_size(dot, size, size);

  lv_coord_t x = dot_positions[dot_index].x;
  lv_coord_t y = dot_positions[dot_index].y;
  lv_obj_set_pos(dot, x - (size / 2), y - (size / 2));

  uint8_t start_r = (COLOR_INACTIVE >> 16) & 0xFF;
  uint8_t start_g = (COLOR_INACTIVE >> 8) & 0xFF;
  uint8_t start_b = COLOR_INACTIVE & 0xFF;

  uint8_t end_r = (COLOR_ACTIVE >> 16) & 0xFF;
  uint8_t end_g = (COLOR_ACTIVE >> 8) & 0xFF;
  uint8_t end_b = COLOR_ACTIVE & 0xFF;

  uint8_t r = (uint8_t)(start_r + ((int)end_r - (int)start_r) * value / 255);
  uint8_t g = (uint8_t)(start_g + ((int)end_g - (int)start_g) * value / 255);
  uint8_t b = (uint8_t)(start_b + ((int)end_b - (int)start_b) * value / 255);

  lv_obj_set_style_bg_color(dot, lv_color_make(r, g, b), 0);

  lv_opa_t opa = (lv_opa_t)(INACTIVE_OPA + ((LV_OPA_COVER - INACTIVE_OPA) * value) / 255);
  lv_obj_set_style_bg_opa(dot, opa, 0);
}

static void activate_dot_mid(fingerprint_dots_t* dots, int dot_index) {
  if (dot_index < 0 || dot_index >= FINGERPRINT_DOTS_COUNT) {
    return;
  }

  lv_obj_t* dot = dots->dots[dot_index];
  if (!dot) {
    return;
  }

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &dots->anim_ctx[dot_index]);
  lv_anim_set_values(&anim, 0, 255);
  lv_anim_set_duration(&anim, DOT_ACTIVATE_DURATION_MS);
  lv_anim_set_exec_cb(&anim, dot_mid_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);
}

static void dot_mid_anim_cb(void* var, int32_t value) {
  fingerprint_dot_anim_ctx_t* ctx = (fingerprint_dot_anim_ctx_t*)var;
  if (!ctx || !ctx->dots) {
    return;
  }

  fingerprint_dots_t* dots = (fingerprint_dots_t*)ctx->dots;
  int dot_index = ctx->dot_index;

  if (dot_index < 0 || dot_index >= FINGERPRINT_DOTS_COUNT) {
    return;
  }

  lv_obj_t* dot = dots->dots[dot_index];
  if (!dot) {
    return;
  }

  lv_coord_t size =
    (lv_coord_t)(FINGERPRINT_DOT_SIZE_INACTIVE +
                 ((FINGERPRINT_DOT_SIZE_MID - FINGERPRINT_DOT_SIZE_INACTIVE) * value) / 255);
  lv_obj_set_size(dot, size, size);

  lv_coord_t x = dot_positions[dot_index].x;
  lv_coord_t y = dot_positions[dot_index].y;
  lv_obj_set_pos(dot, x - (size / 2), y - (size / 2));

  uint8_t start_r = (COLOR_INACTIVE >> 16) & 0xFF;
  uint8_t start_g = (COLOR_INACTIVE >> 8) & 0xFF;
  uint8_t start_b = COLOR_INACTIVE & 0xFF;

  uint8_t end_r = (COLOR_ACTIVE >> 16) & 0xFF;
  uint8_t end_g = (COLOR_ACTIVE >> 8) & 0xFF;
  uint8_t end_b = COLOR_ACTIVE & 0xFF;

  uint8_t r = (uint8_t)(start_r + ((int)end_r - (int)start_r) * value / 255);
  uint8_t g = (uint8_t)(start_g + ((int)end_g - (int)start_g) * value / 255);
  uint8_t b = (uint8_t)(start_b + ((int)end_b - (int)start_b) * value / 255);

  lv_obj_set_style_bg_color(dot, lv_color_make(r, g, b), 0);

  lv_opa_t opa = (lv_opa_t)(INACTIVE_OPA + ((MID_OPA - INACTIVE_OPA) * value) / 255);
  lv_obj_set_style_bg_opa(dot, opa, 0);
}

static void activate_dot_mid_to_full(fingerprint_dots_t* dots, int dot_index) {
  if (dot_index < 0 || dot_index >= FINGERPRINT_DOTS_COUNT) {
    return;
  }

  lv_obj_t* dot = dots->dots[dot_index];
  if (!dot) {
    return;
  }

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &dots->anim_ctx[dot_index]);
  lv_anim_set_values(&anim, 0, 255);
  lv_anim_set_duration(&anim, DOT_ACTIVATE_DURATION_MS);
  lv_anim_set_exec_cb(&anim, dot_mid_to_full_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);
}

static void dot_mid_to_full_anim_cb(void* var, int32_t value) {
  fingerprint_dot_anim_ctx_t* ctx = (fingerprint_dot_anim_ctx_t*)var;
  if (!ctx || !ctx->dots) {
    return;
  }

  fingerprint_dots_t* dots = (fingerprint_dots_t*)ctx->dots;
  int dot_index = ctx->dot_index;

  if (dot_index < 0 || dot_index >= FINGERPRINT_DOTS_COUNT) {
    return;
  }

  lv_obj_t* dot = dots->dots[dot_index];
  if (!dot) {
    return;
  }

  lv_coord_t size =
    (lv_coord_t)(FINGERPRINT_DOT_SIZE_MID +
                 ((FINGERPRINT_DOT_SIZE_ACTIVE - FINGERPRINT_DOT_SIZE_MID) * value) / 255);
  lv_obj_set_size(dot, size, size);

  lv_coord_t x = dot_positions[dot_index].x;
  lv_coord_t y = dot_positions[dot_index].y;
  lv_obj_set_pos(dot, x - (size / 2), y - (size / 2));

  lv_obj_set_style_bg_color(dot, lv_color_hex(COLOR_ACTIVE), 0);

  lv_opa_t opa = (lv_opa_t)(MID_OPA + ((LV_OPA_COVER - MID_OPA) * value) / 255);
  lv_obj_set_style_bg_opa(dot, opa, 0);
}
