#include "bottom_menu.h"

#include "arithmetic.h"
#include "assert.h"

#include <stdbool.h>

// Button configuration
#define CIRCLE_DIAMETER     64
#define CIRCLE_BORDER_WIDTH 3
#define CIRCLE_BORDER_COLOR 0xFFFFFF
#define BOTTOM_MARGIN       20

// External image declaration
extern const lv_img_dsc_t ellipsis_horizontal;

static const lv_point_precise_t points[] = {{16, 32}, {32, 48}, {48, 32}};

static const lv_style_const_prop_t style_props[] = {
  LV_STYLE_CONST_LINE_WIDTH(1),
  LV_STYLE_CONST_LINE_COLOR((lv_color_t)LV_COLOR_MAKE(0xF0, 0xF0, 0xF0)),
  LV_STYLE_CONST_LINE_ROUNDED(false),
  LV_STYLE_CONST_PROPS_END,
};

LV_STYLE_CONST_INIT(style_line, style_props);

void bottom_menu_create(lv_obj_t* parent, bottom_menu_t* button, bool no_default) {
  ASSERT(parent != NULL);
  ASSERT(button != NULL);

  // Create container.
  button->container = lv_obj_create(parent);
  lv_obj_set_size(button->container, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
  lv_obj_set_style_bg_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_shadow_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(button->container, 0, 0);
  lv_obj_clear_flag(button->container, LV_OBJ_FLAG_SCROLLABLE);

  if (no_default) {
    lv_obj_align(button->container, LV_ALIGN_BOTTOM_MID, 0, -20);

    button->default_icon_img = lv_line_create(button->container);
    lv_line_set_points(button->default_icon_img, points, ARRAY_SIZE(points));
    lv_obj_add_style(button->default_icon_img, &style_line, 0);

    button->circle = NULL;
  } else {
    lv_obj_align(button->container, LV_ALIGN_BOTTOM_MID, 0, -BOTTOM_MARGIN);

    button->default_icon_img = NULL;
    button->circle = NULL;

    // Create icon image centered in circle.
    button->icon_img = lv_img_create(button->container);
    lv_img_set_src(button->icon_img, &ellipsis_horizontal);
    lv_obj_center(button->icon_img);
  }

  button->is_initialized = true;
}

void bottom_menu_set_highlight(bottom_menu_t* button, bool highlighted) {
  if (!button || !button->is_initialized) {
    return;
  }

  if (button->default_icon_img == NULL) {
    // When the default icon image is not used, create / show a circle when
    // an item is highlighted.
    if (highlighted) {
      if (button->circle == NULL) {
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

        // Move icon to front.
        lv_obj_move_foreground(button->icon_img);
      }
    } else if (button->circle != NULL) {
      lv_obj_del(button->circle);
      button->circle = NULL;
    }
  }
}

void bottom_menu_destroy(bottom_menu_t* button) {
  if (!button || !button->is_initialized) {
    return;
  }

  if (button->container) {
    lv_obj_del(button->container);
  }

  button->container = NULL;
  button->default_icon_img = NULL;
  button->circle = NULL;
  button->icon_img = NULL;
  button->is_initialized = false;
}
