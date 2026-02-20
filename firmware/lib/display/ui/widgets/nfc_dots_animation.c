/**
 * @file nfc_dots_animation.c
 * @brief NFC dots widget implementation
 *
 * Uses exact dot coordinates from NFC.svg. Pattern is centered on the display.
 */

#include "nfc_dots_animation.h"

#include "assert.h"

#include <string.h>

#define COLOR_ACTIVE 0xFFFFFF

// SVG dimensions (from NFC.svg viewBox)
#define SVG_WIDTH  223
#define SVG_HEIGHT 297

#define DISPLAY_SIZE 466

// Offset to center the SVG pattern on the display
#define OFFSET_X ((DISPLAY_SIZE - SVG_WIDTH) / 2)
#define OFFSET_Y ((DISPLAY_SIZE - SVG_HEIGHT) / 2)

// Dot coordinates from NFC.svg
static const struct {
  uint16_t x;
  uint16_t y;
} dot_positions[NFC_DOTS_COUNT] = {
  // Top arc - Ring 0 (innermost)
  {82, 98},
  {96, 92},
  {111, 90},
  {126, 92},
  {140, 98},
  // Top arc - Ring 1
  {56, 82},
  {68, 73},
  {82, 67},
  {96, 63},
  {111, 62},
  {126, 63},
  {141, 67},
  {154, 73},
  {167, 82},
  // Top arc - Ring 2
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
  // Top arc - Ring 3 (outermost)
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
};

lv_obj_t* nfc_dots_animation_create(lv_obj_t* parent, nfc_dots_animation_t* animation) {
  if (!animation || animation->is_initialized) {
    return NULL;
  }

  memset(animation, 0, sizeof(nfc_dots_animation_t));
  animation->parent = parent;

  animation->container = lv_obj_create(parent);
  lv_obj_set_size(animation->container, DISPLAY_SIZE, DISPLAY_SIZE);
  lv_obj_center(animation->container);
  lv_obj_set_style_bg_opa(animation->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(animation->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(animation->container, 0, 0);
  lv_obj_clear_flag(animation->container, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);

  for (int i = 0; i < NFC_DOTS_COUNT; i++) {
    lv_coord_t x = (lv_coord_t)dot_positions[i].x + OFFSET_X;
    lv_coord_t y = (lv_coord_t)dot_positions[i].y + OFFSET_Y;

    animation->dots[i] = lv_obj_create(animation->container);
    lv_obj_set_size(animation->dots[i], NFC_DOT_SIZE_ACTIVE, NFC_DOT_SIZE_ACTIVE);
    lv_obj_set_pos(animation->dots[i], x - (NFC_DOT_SIZE_ACTIVE / 2),
                   y - (NFC_DOT_SIZE_ACTIVE / 2));
    lv_obj_set_style_radius(animation->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(animation->dots[i], lv_color_hex(COLOR_ACTIVE), 0);
    lv_obj_set_style_bg_opa(animation->dots[i], LV_OPA_COVER, 0);
    lv_obj_set_style_border_opa(animation->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(animation->dots[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(animation->dots[i], LV_OBJ_FLAG_HIDDEN);
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
