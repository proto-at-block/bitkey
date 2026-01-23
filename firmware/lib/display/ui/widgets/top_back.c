#include "top_back.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"

// Button configuration
#define PILL_WIDTH    60
#define PILL_HEIGHT   36
#define PILL_RADIUS   12
#define TOP_MARGIN    20
#define PILL_BG_COLOR 0x555555   // Grey
#define PILL_BG_OPA   LV_OPA_80  // Semi-transparent grey

// External image declaration
extern const lv_img_dsc_t back_arrow;

// Default back button click handler
static void default_back_button_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK, 0);
  }
}

void top_back_create(lv_obj_t* parent, top_back_t* button, lv_event_cb_t custom_handler) {
  ASSERT(parent != NULL);
  ASSERT(button != NULL);

  // Create pill-shaped container with grey background
  button->container = lv_obj_create(parent);
  lv_obj_set_size(button->container, PILL_WIDTH, PILL_HEIGHT);
  lv_obj_set_style_radius(button->container, PILL_RADIUS, 0);
  lv_obj_set_style_bg_color(button->container, lv_color_hex(PILL_BG_COLOR), 0);
  lv_obj_set_style_bg_opa(button->container, PILL_BG_OPA, 0);
  lv_obj_set_style_border_width(button->container, 0, 0);
  lv_obj_set_style_shadow_opa(button->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(button->container, 0, 0);
  lv_obj_clear_flag(button->container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(button->container, LV_OBJ_FLAG_CLICKABLE);

  // Position at top center for round screen
  lv_obj_align(button->container, LV_ALIGN_TOP_MID, 0, TOP_MARGIN);

  // Add click event handler (custom or default)
  lv_event_cb_t handler = custom_handler ? custom_handler : default_back_button_click_handler;
  lv_obj_add_event_cb(button->container, handler, LV_EVENT_CLICKED, NULL);

  // Create icon image centered in pill
  button->icon_img = lv_img_create(button->container);
  lv_img_set_src(button->icon_img, &back_arrow);
  lv_obj_center(button->icon_img);

  button->is_initialized = true;
}

void top_back_destroy(top_back_t* button) {
  if (!button || !button->is_initialized) {
    return;
  }

  if (button->container) {
    lv_obj_del(button->container);
  }

  button->container = NULL;
  button->icon_img = NULL;
  button->is_initialized = false;
}
