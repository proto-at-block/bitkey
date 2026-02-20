#include "top_menu.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"

#include <stdbool.h>
#include <string.h>

// Widget configuration
#define PILL_WIDTH  60
#define PILL_HEIGHT 36
#define PILL_RADIUS 32
#define TOP_MARGIN  32
#define PILL_BG_OPA LV_OPA_80  // Semi-transparent grey

// Colors
#define COLOR_PILL 0x555555  // Grey

// External image declaration
extern const lv_img_dsc_t ellipsis_horizontal;

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
  lv_obj_set_ext_click_area(widget->container, 40);  // Extend touch target beyond visible pill

  // Position at top center
  lv_obj_align(widget->container, LV_ALIGN_TOP_MID, 0, TOP_MARGIN);

  // Create ellipsis icon centered in pill
  widget->icon = lv_img_create(widget->container);
  lv_img_set_src(widget->icon, &ellipsis_horizontal);
  lv_obj_center(widget->icon);

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
  widget->icon = NULL;
  widget->is_initialized = false;
}
