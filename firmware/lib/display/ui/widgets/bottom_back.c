#include "bottom_back.h"

#include "assert.h"

// Button configuration
#define CIRCLE_DIAMETER     64
#define CIRCLE_BORDER_WIDTH 3
#define CIRCLE_BORDER_COLOR 0xFFFFFF
#define BOTTOM_MARGIN       20

// External image declaration
extern const lv_img_dsc_t cycle_back;

void bottom_back_create(lv_obj_t* parent, bottom_back_t* button) {
  ASSERT(parent != NULL);
  ASSERT(button != NULL);

  // Create container
  button->container = lv_obj_create(parent);
  lv_obj_set_size(button->container, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
  lv_obj_align(button->container, LV_ALIGN_BOTTOM_MID, 0, -BOTTOM_MARGIN);
  lv_obj_set_style_bg_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_shadow_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(button->container, 0, 0);
  lv_obj_clear_flag(button->container, LV_OBJ_FLAG_SCROLLABLE);

  // Create circle with white border
  button->circle = lv_obj_create(button->container);
  lv_obj_set_size(button->circle, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
  lv_obj_center(button->circle);
  lv_obj_set_style_radius(button->circle, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_opa(button->circle, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(button->circle, CIRCLE_BORDER_WIDTH, 0);
  lv_obj_set_style_border_color(button->circle, lv_color_hex(CIRCLE_BORDER_COLOR), 0);
  lv_obj_set_style_border_opa(button->circle, LV_OPA_COVER, 0);
  lv_obj_set_style_pad_all(button->circle, 0, 0);
  lv_obj_clear_flag(button->circle, LV_OBJ_FLAG_SCROLLABLE);

  // Create icon image centered in circle
  button->icon_img = lv_img_create(button->container);
  lv_img_set_src(button->icon_img, &cycle_back);
  lv_obj_center(button->icon_img);

  button->is_initialized = true;
}

void bottom_back_destroy(bottom_back_t* button) {
  if (!button || !button->is_initialized) {
    return;
  }

  if (button->container) {
    lv_obj_del(button->container);
  }

  button->container = NULL;
  button->circle = NULL;
  button->icon_img = NULL;
  button->is_initialized = false;
}
