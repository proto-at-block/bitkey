/**
 * @file nfc_dots_animation.c
 * @brief NFC dots widget implementation
 *
 * Uses exact dot coordinates from NFC.svg. Pattern is centered on the display.
 * 7 rings (4 original + 3 intermediate) with radiate-only animation.
 */

#include "nfc_dots_animation.h"

#include "assert.h"

#include <stdlib.h>
#include <string.h>

#define COLOR_ACTIVE 0xFFFFFF

// SVG dimensions (from NFC.svg viewBox)
#define SVG_WIDTH  223
#define SVG_HEIGHT 297

#define DISPLAY_SIZE 466

// Offset to center the SVG pattern on the display
#define OFFSET_X ((DISPLAY_SIZE - SVG_WIDTH) / 2)
#define OFFSET_Y ((DISPLAY_SIZE - SVG_HEIGHT) / 2)

// Animation constants.
#define NUM_RINGS      7
#define NFC_BLEND_MAX  255
#define ANIM_UPDATE_MS 50  // Redraw interval

// Radiate pulse: dramatic inner-to-outer ring glow.
#define RADIATE_CYCLE_MS     1200
#define RADIATE_GAP_RINGS    2
#define RADIATE_TOTAL_RINGS  (NUM_RINGS + RADIATE_GAP_RINGS)
#define RADIATE_PERIOD_Q8    (RADIATE_TOTAL_RINGS * NFC_BLEND_MAX)
#define RADIATE_PEAK_COLOR_T 220

// Dot coordinates from NFC.svg plus computed intermediate rings.
static const struct {
  uint16_t x;
  uint16_t y;
} dot_positions[NFC_DOTS_COUNT] = {
  // Top arc - Ring 0 (innermost, r=58)
  {82, 98},
  {96, 92},
  {111, 90},
  {126, 92},
  {140, 98},
  // Top arc - Ring 1 (r=86)
  {56, 82},
  {68, 73},
  {82, 67},
  {96, 63},
  {111, 62},
  {126, 63},
  {141, 67},
  {154, 73},
  {167, 82},
  // Top arc - Ring 2 (r=115)
  {30, 67},
  {41, 57},
  {54, 48},
  {67, 42},
  {81, 37},
  {96, 34},
  {111, 33},
  {126, 34},
  {141, 37},
  {155, 42},
  {169, 48},
  {181, 57},
  {193, 67},
  // Top arc - Ring 3 (outermost, r=144)
  {4, 52},
  {15, 41},
  {26, 32},
  {39, 23},
  {53, 16},
  {67, 11},
  {81, 7},
  {96, 5},
  {111, 4},
  {126, 5},
  {141, 7},
  {156, 11},
  {170, 16},
  {183, 23},
  {196, 32},
  {208, 41},
  {218, 52},
  // Bottom arc - Ring 0 (innermost)
  {140, 198},
  {126, 204},
  {111, 206},
  {96, 204},
  {82, 198},
  // Bottom arc - Ring 1
  {167, 214},
  {154, 223},
  {141, 229},
  {126, 233},
  {111, 235},
  {96, 233},
  {82, 229},
  {68, 223},
  {56, 214},
  // Bottom arc - Ring 2
  {193, 230},
  {181, 240},
  {169, 248},
  {155, 255},
  {141, 260},
  {126, 263},
  {111, 264},
  {96, 263},
  {81, 260},
  {67, 255},
  {54, 248},
  {41, 240},
  {30, 230},
  // Bottom arc - Ring 3 (outermost)
  {218, 245},
  {208, 255},
  {196, 265},
  {183, 273},
  {170, 280},
  {156, 285},
  {141, 289},
  {126, 292},
  {111, 292},
  {96, 292},
  {81, 289},
  {67, 285},
  {53, 280},
  {39, 273},
  {26, 265},
  {15, 255},
  {4, 245},
  // Top arc - Ring A (between ring 0 and 1, r=72)
  {70, 89},
  {82, 82},
  {96, 77},
  {111, 76},
  {126, 77},
  {140, 82},
  {152, 89},
  // Bottom arc - Ring A
  {152, 207},
  {140, 214},
  {126, 219},
  {111, 220},
  {96, 219},
  {82, 214},
  {70, 207},
  // Top arc - Ring B (between ring 1 and 2, r=100)
  {43, 74},
  {55, 65},
  {68, 58},
  {82, 52},
  {96, 49},
  {111, 48},
  {126, 49},
  {140, 52},
  {154, 58},
  {167, 65},
  {179, 74},
  // Bottom arc - Ring B
  {179, 222},
  {167, 231},
  {154, 238},
  {140, 244},
  {126, 247},
  {111, 248},
  {96, 247},
  {82, 244},
  {68, 238},
  {55, 231},
  {43, 222},
  // Top arc - Ring C (between ring 2 and 3, r=130)
  {17, 58},
  {28, 48},
  {40, 39},
  {53, 32},
  {67, 26},
  {81, 21},
  {96, 19},
  {111, 18},
  {126, 19},
  {141, 21},
  {155, 26},
  {169, 32},
  {182, 39},
  {194, 48},
  {205, 58},
  // Bottom arc - Ring C
  {205, 238},
  {194, 248},
  {182, 257},
  {169, 264},
  {155, 270},
  {141, 275},
  {126, 277},
  {111, 278},
  {96, 277},
  {81, 275},
  {67, 270},
  {53, 264},
  {40, 257},
  {28, 248},
  {17, 238},
};

// Ring ranges: dot index ranges for top and bottom arcs (inner to outer).
static const struct {
  uint8_t top_start;
  uint8_t top_count;
  uint8_t bot_start;
  uint8_t bot_count;
} ring_ranges[NUM_RINGS] = {
  {0, 5, 44, 5},    {88, 7, 95, 7},     {5, 9, 49, 9},    {102, 11, 113, 11},
  {14, 13, 58, 13}, {124, 15, 139, 15}, {27, 17, 71, 17},
};

// Interpolate between two colors. t=0 → from, t=255 → to.
static lv_color_t lerp_color(lv_color_t from, lv_color_t to, uint8_t t) {
  lv_color_t result;
  result.red = (uint8_t)(from.red + ((int16_t)to.red - from.red) * t / NFC_BLEND_MAX);
  result.green = (uint8_t)(from.green + ((int16_t)to.green - from.green) * t / NFC_BLEND_MAX);
  result.blue = (uint8_t)(from.blue + ((int16_t)to.blue - from.blue) * t / NFC_BLEND_MAX);
  return result;
}

// Resize a dot, re-center it at its SVG coordinate, and update its color.
static void set_dot_state(nfc_dots_animation_t* anim, uint8_t dot_idx, lv_coord_t size,
                          uint8_t color_t) {
  if (!anim->dots[dot_idx]) {
    return;
  }
  lv_coord_t x = (lv_coord_t)dot_positions[dot_idx].x + OFFSET_X;
  lv_coord_t y = (lv_coord_t)dot_positions[dot_idx].y + OFFSET_Y;
  lv_obj_set_size(anim->dots[dot_idx], size, size);
  lv_obj_set_pos(anim->dots[dot_idx], x - size / 2, y - size / 2);
  lv_obj_set_style_bg_color(anim->dots[dot_idx],
                            lerp_color(anim->resting_color, anim->highlight_color, color_t), 0);
}

static void accumulate_dot_state(lv_coord_t* dot_sizes, uint8_t* dot_color_t, uint8_t dot_idx,
                                 lv_coord_t sample_size, uint8_t sample_color_t, uint8_t weight_t) {
  if (weight_t == 0) {
    return;
  }

  lv_coord_t candidate_size =
    NFC_DOT_SIZE_RESTING +
    (lv_coord_t)(((sample_size - NFC_DOT_SIZE_RESTING) * weight_t + (NFC_BLEND_MAX / 2)) /
                 NFC_BLEND_MAX);
  uint8_t candidate_color_t =
    (uint8_t)(((uint16_t)sample_color_t * weight_t + (NFC_BLEND_MAX / 2)) / NFC_BLEND_MAX);

  if (candidate_size > dot_sizes[dot_idx]) {
    dot_sizes[dot_idx] = candidate_size;
  }
  if (candidate_color_t > dot_color_t[dot_idx]) {
    dot_color_t[dot_idx] = candidate_color_t;
  }
}

// Spoke-based dot selection: fixed angular positions (dx from pattern center)
// that form consistent radial lines across all rings. Inner rings naturally
// activate fewer spokes since their arcs are narrower.
#define NUM_SPOKES         9
#define PATTERN_CENTER_X   111
#define SPOKE_MATCH_THRESH 30  // max dx distance to claim a dot
#define MAX_ARC_DOTS       17  // largest arc (ring 3)

// Spoke dx positions derived from outermost ring's every-other-dot x-offsets.
static const int16_t spoke_dx[NUM_SPOKES] = {-107, -85, -58, -30, 0, 30, 59, 85, 107};
// Process order: center spoke first, then symmetric pairs outward.
// Gives center spokes priority during dedup so outer rings don't steal inner matches.
static const uint8_t spoke_order[NUM_SPOKES] = {4, 3, 5, 2, 6, 1, 7, 0, 8};

// Apply a radiate pulse to dots in an arc, selecting by angular spoke position.
// Each spoke picks the nearest unactivated dot (by x-position) and alternates
// big/small by spoke distance from center.
static void accumulate_radiate_arc(uint8_t start, uint8_t count, uint8_t weight_t,
                                   lv_coord_t* dot_sizes, uint8_t* dot_color_t) {
  if (count == 0 || weight_t == 0) {
    return;
  }

  bool activated[MAX_ARC_DOTS];
  memset(activated, false, count * sizeof(bool));

  for (uint8_t si = 0; si < NUM_SPOKES; si++) {
    uint8_t s = spoke_order[si];
    int16_t target = spoke_dx[s];

    // Find nearest unactivated dot by dx from pattern center.
    uint8_t best_i = 0;
    uint16_t best_d = UINT16_MAX;
    for (uint8_t i = 0; i < count; i++) {
      if (activated[i]) {
        continue;
      }
      int16_t dx = (int16_t)dot_positions[start + i].x - PATTERN_CENTER_X;
      uint16_t d = (uint16_t)abs(dx - target);
      if (d < best_d) {
        best_d = d;
        best_i = i;
      }
    }
    if (best_d > SPOKE_MATCH_THRESH) {
      continue;
    }
    activated[best_i] = true;

    // Size alternation: even distance from center spoke = big, odd = small.
    uint8_t dist_from_center = (s >= NUM_SPOKES / 2) ? (s - NUM_SPOKES / 2) : (NUM_SPOKES / 2 - s);
    lv_coord_t size = (dist_from_center % 2 == 0) ? NFC_DOT_SIZE_ACTIVE : NFC_DOT_SIZE_FAR;

    // Boost trail for center spokes: small dots get extra boost so more
    // appear in the trailing wake without doubling up the large highlights.
    int16_t dx = (int16_t)dot_positions[start + best_i].x - PATTERN_CENTER_X;
    uint16_t abs_dx = (uint16_t)abs(dx);
    uint8_t boost_amount = (dist_from_center % 2 != 0) ? 70 : 40;
    uint16_t boosted = (uint16_t)weight_t + (uint16_t)((107 - abs_dx) * boost_amount / 107);
    uint8_t final_wt = (boosted > NFC_BLEND_MAX) ? NFC_BLEND_MAX : (uint8_t)boosted;

    // Scale color intensity with dot size so smaller dots are dimmer.
    uint8_t color_t = (uint8_t)((uint16_t)RADIATE_PEAK_COLOR_T * size / NFC_DOT_SIZE_ACTIVE);
    accumulate_dot_state(dot_sizes, dot_color_t, (uint8_t)(start + best_i), size, color_t,
                         final_wt);
  }

  // Ghost trail: the unactivated dot between each highlighted dot and the
  // arc center gets a subtle 30% color glow at resting size, extending the tail.
  uint8_t center = count / 2;
  uint8_t ghost_color_t = (uint8_t)(RADIATE_PEAK_COLOR_T * 30 / 100);
  for (uint8_t i = 0; i < count; i++) {
    if (!activated[i]) {
      continue;
    }
    // Neighbor toward arc center (trailing side).
    int8_t toward_center = (i < center) ? 1 : (i > center) ? -1 : 0;
    if (toward_center == 0) {
      continue;
    }
    uint8_t neighbor = (uint8_t)(i + toward_center);
    if (neighbor < count && !activated[neighbor]) {
      accumulate_dot_state(dot_sizes, dot_color_t, (uint8_t)(start + neighbor),
                           NFC_DOT_SIZE_RESTING, ghost_color_t, weight_t);
    }
  }
}

static void accumulate_radiate_ring(uint8_t ring, uint8_t weight_t, lv_coord_t* dot_sizes,
                                    uint8_t* dot_color_t) {
  if (weight_t == 0) {
    return;
  }
  accumulate_radiate_arc(ring_ranges[ring].top_start, ring_ranges[ring].top_count, weight_t,
                         dot_sizes, dot_color_t);
  accumulate_radiate_arc(ring_ranges[ring].bot_start, ring_ranges[ring].bot_count, weight_t,
                         dot_sizes, dot_color_t);
}

static void radiate_timer_cb(lv_timer_t* timer) {
  nfc_dots_animation_t* anim = (nfc_dots_animation_t*)lv_timer_get_user_data(timer);
  lv_coord_t dot_sizes[NFC_DOTS_COUNT];
  uint8_t dot_color_t[NFC_DOTS_COUNT];

  // Reset all dots to resting.
  for (uint8_t i = 0; i < NFC_DOTS_COUNT; i++) {
    dot_sizes[i] = NFC_DOT_SIZE_RESTING;
    dot_color_t[i] = 0;
  }

  // Radiate pulse: dramatic glow that ripples from inner to outer rings.
  uint16_t rp = anim->radiate_phase;
  for (uint8_t r = 0; r < NUM_RINGS; r++) {
    int32_t dist = abs((int32_t)rp - (int32_t)(r * NFC_BLEND_MAX));
    if (dist >= 2 * NFC_BLEND_MAX) {
      continue;
    }
    // Smooth falloff over 2 rings: full at center, zero at 2 rings away.
    uint8_t proximity = (uint8_t)(NFC_BLEND_MAX - dist / 2);
    accumulate_radiate_ring(r, proximity, dot_sizes, dot_color_t);
  }
  uint16_t advance = (uint16_t)((uint32_t)RADIATE_PERIOD_Q8 * ANIM_UPDATE_MS / RADIATE_CYCLE_MS);
  anim->radiate_phase = (uint16_t)((rp + advance) % RADIATE_PERIOD_Q8);

  for (uint8_t i = 0; i < NFC_DOTS_COUNT; i++) {
    set_dot_state(anim, i, dot_sizes[i], dot_color_t[i]);
  }
}

lv_obj_t* nfc_dots_animation_create(lv_obj_t* parent, nfc_dots_animation_t* animation) {
  if (!animation || animation->is_initialized) {
    return NULL;
  }

  // Preserve highlight_color (set by caller before create) across the memset.
  lv_color_t saved_color = animation->highlight_color;
  memset(animation, 0, sizeof(nfc_dots_animation_t));
  animation->highlight_color = saved_color;

  // Resting color is fixed dark grey. A zero highlight color selects the
  // default white pulse.
  animation->resting_color = lv_color_hex(0x313131);
  bool has_custom = (saved_color.red != 0) || (saved_color.green != 0) || (saved_color.blue != 0);
  if (!has_custom) {
    animation->highlight_color = lv_color_hex(COLOR_ACTIVE);
  }

  animation->container = lv_obj_create(parent);
  lv_obj_set_size(animation->container, DISPLAY_SIZE, DISPLAY_SIZE);
  lv_obj_center(animation->container);
  lv_obj_set_style_bg_opa(animation->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(animation->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(animation->container, 0, 0);
  lv_obj_clear_flag(animation->container, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);

  for (int i = 0; i < NFC_DOTS_COUNT; i++) {
    animation->dots[i] = lv_obj_create(animation->container);
    lv_obj_set_style_radius(animation->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_opa(animation->dots[i], LV_OPA_COVER, 0);
    lv_obj_set_style_border_opa(animation->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(animation->dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(animation->dots[i], LV_OBJ_FLAG_HIDDEN);
    set_dot_state(animation, (uint8_t)i, NFC_DOT_SIZE_RESTING, 0);
  }

  animation->is_initialized = true;
  return parent;
}

void nfc_dots_animation_start(nfc_dots_animation_t* animation) {
  if (!animation || !animation->is_initialized || animation->is_animating) {
    return;
  }

  for (int i = 0; i < NFC_DOTS_COUNT; i++) {
    if (animation->dots[i]) {
      lv_obj_clear_flag(animation->dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  animation->update_timer = lv_timer_create(radiate_timer_cb, ANIM_UPDATE_MS, animation);
  if (!animation->update_timer) {
    return;
  }
  animation->is_animating = true;
}

void nfc_dots_animation_stop(nfc_dots_animation_t* animation) {
  if (!animation || !animation->is_initialized || !animation->is_animating) {
    return;
  }

  if (animation->update_timer) {
    lv_timer_del(animation->update_timer);
    animation->update_timer = NULL;
  }

  for (int i = 0; i < NFC_DOTS_COUNT; i++) {
    set_dot_state(animation, (uint8_t)i, NFC_DOT_SIZE_RESTING, 0);
  }

  animation->is_animating = false;
}

void nfc_dots_animation_destroy(nfc_dots_animation_t* animation) {
  if (!animation || !animation->is_initialized) {
    return;
  }

  nfc_dots_animation_stop(animation);

  if (animation->container) {
    lv_obj_del(animation->container);
  }

  memset(animation, 0, sizeof(nfc_dots_animation_t));
}
