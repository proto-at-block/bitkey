#include "top_menu.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"

#include <stdbool.h>
#include <string.h>

// Widget configuration
#define PILL_WIDTH   60
#define PILL_HEIGHT  36
#define PILL_RADIUS  12
#define TOP_MARGIN   20
#define DOT_DIAMETER 5
#define DOT_SPACING  10         // Space between dot centers
#define PILL_BG_OPA  LV_OPA_80  // Semi-transparent grey

// Colors
#define COLOR_DOT  0xFFFFFF  // White
#define COLOR_PILL 0x555555  // Grey

// Click event handler for the menu button
static void menu_button_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    // Send MENU action using display_action system
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU, 0);
  }
}

void top_menu_create(lv_obj_t* parent, top_menu_t* widget, lv_event_cb_t custom_handler) {
  ASSERT(parent != NULL);
  ASSERT(widget != NULL);

  // Create pill-shaped container
  widget->container = lv_obj_create(parent);
  lv_obj_set_size(widget->container, PILL_WIDTH, PILL_HEIGHT);
  lv_obj_set_style_radius(widget->container, PILL_RADIUS, 0);
  lv_obj_set_style_bg_color(widget->container, lv_color_hex(COLOR_PILL), 0);
  lv_obj_set_style_bg_opa(widget->container, PILL_BG_OPA, 0);
  lv_obj_set_style_border_width(widget->container, 0, 0);
  lv_obj_set_style_shadow_opa(widget->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(widget->container, 0, 0);
  lv_obj_clear_flag(widget->container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(widget->container, LV_OBJ_FLAG_CLICKABLE);

  // Position at top center
  lv_obj_align(widget->container, LV_ALIGN_TOP_MID, 0, TOP_MARGIN);

  // Create three dots inside the pill, evenly spaced
  int32_t pill_center_y = PILL_HEIGHT / 2;

  // Calculate positions for three dots centered in the pill
  int32_t dot_y = pill_center_y;
  int32_t dot1_x = (PILL_WIDTH / 2) - DOT_SPACING;  // Left dot
  int32_t dot2_x = PILL_WIDTH / 2;                  // Center dot
  int32_t dot3_x = (PILL_WIDTH / 2) + DOT_SPACING;  // Right dot

  for (int i = 0; i < 3; i++) {
    widget->dots[i] = lv_obj_create(widget->container);
    lv_obj_set_size(widget->dots[i], DOT_DIAMETER, DOT_DIAMETER);
    lv_obj_set_style_radius(widget->dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(widget->dots[i], lv_color_hex(COLOR_DOT), 0);
    lv_obj_set_style_bg_opa(widget->dots[i], LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(widget->dots[i], 0, 0);
    lv_obj_set_style_shadow_opa(widget->dots[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(widget->dots[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_clear_flag(widget->dots[i], LV_OBJ_FLAG_SCROLLABLE);

    // Position each dot
    int32_t dot_x = (i == 0) ? dot1_x : (i == 1) ? dot2_x : dot3_x;
    lv_obj_set_pos(widget->dots[i], dot_x - (DOT_DIAMETER / 2), dot_y - (DOT_DIAMETER / 2));
  }

  // Add click event handler to the container (custom or default)
  lv_event_cb_t handler = custom_handler ? custom_handler : menu_button_click_handler;
  lv_obj_add_event_cb(widget->container, handler, LV_EVENT_CLICKED, NULL);

  widget->is_initialized = true;
}

void top_menu_destroy(top_menu_t* widget) {
  if (!widget || !widget->is_initialized) {
    return;
  }

  if (widget->container) {
    lv_obj_del(widget->container);
  }

  widget->container = NULL;
  widget->dots[0] = NULL;
  widget->dots[1] = NULL;
  widget->dots[2] = NULL;
  widget->is_initialized = false;
}
