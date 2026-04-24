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
#define PILL_BG_OPA LV_OPA_70

// Colors
#define COLOR_PILL 0x404040

// External image declaration
extern const lv_img_dsc_t ellipsis_horizontal;

static bool top_menu_is_ready(const top_menu_t* widget) {
  return widget && widget->is_initialized && widget->container &&
         lv_obj_is_valid(widget->container);
}

static void top_menu_opa_anim_cb(void* var, int32_t value) {
  lv_obj_t* container = (lv_obj_t*)var;
  if (!container || !lv_obj_is_valid(container)) {
    return;
  }

  lv_obj_set_style_opa(container, (lv_opa_t)value, 0);
}

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

  widget->container = NULL;
  widget->icon = NULL;
  widget->is_initialized = false;

  // Create pill-shaped container
  widget->container = lv_obj_create(parent);
  if (!widget->container) {
    return;
  }
  lv_obj_set_size(widget->container, PILL_WIDTH, PILL_HEIGHT);
  lv_obj_set_style_radius(widget->container, PILL_RADIUS, 0);
  lv_obj_set_style_bg_color(widget->container, lv_color_hex(COLOR_PILL), 0);
  lv_obj_set_style_bg_opa(widget->container, PILL_BG_OPA, 0);
  lv_obj_set_style_opa(widget->container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(widget->container, 0, 0);
  lv_obj_set_style_shadow_opa(widget->container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(widget->container, 0, 0);
  lv_obj_clear_flag(widget->container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(widget->container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(widget->container, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_set_ext_click_area(widget->container, 40);  // Extend touch target beyond visible pill

  // Position at top center
  lv_obj_align(widget->container, LV_ALIGN_TOP_MID, 0, TOP_MARGIN);

  // Create ellipsis icon centered in pill
  widget->icon = lv_img_create(widget->container);
  if (!widget->icon) {
    lv_obj_del(widget->container);
    widget->container = NULL;
    return;
  }
  lv_img_set_src(widget->icon, &ellipsis_horizontal);
  lv_obj_set_style_img_recolor(widget->icon, lv_color_white(), 0);
  lv_obj_set_style_img_recolor_opa(widget->icon, LV_OPA_COVER, 0);
  lv_obj_center(widget->icon);

  // Add click event handler to the container (custom or default)
  lv_event_cb_t handler = custom_handler ? custom_handler : menu_button_click_handler;
  lv_obj_add_event_cb(widget->container, handler, LV_EVENT_CLICKED, NULL);

  widget->is_initialized = true;
}

void top_menu_set_opacity(top_menu_t* widget, lv_opa_t opacity) {
  if (!top_menu_is_ready(widget)) {
    return;
  }

  lv_anim_del(widget->container, top_menu_opa_anim_cb);
  lv_obj_set_style_opa(widget->container, opacity, 0);
}

void top_menu_fade_to_opacity(top_menu_t* widget, lv_opa_t opacity, uint32_t duration_ms) {
  if (!top_menu_is_ready(widget)) {
    return;
  }

  lv_anim_del(widget->container, top_menu_opa_anim_cb);

  if (duration_ms == 0) {
    lv_obj_set_style_opa(widget->container, opacity, 0);
    return;
  }

  lv_opa_t current_opa = lv_obj_get_style_opa(widget->container, 0);
  if (current_opa == opacity) {
    return;
  }

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, widget->container);
  lv_anim_set_exec_cb(&anim, top_menu_opa_anim_cb);
  lv_anim_set_values(&anim, current_opa, opacity);
  lv_anim_set_time(&anim, duration_ms);
  lv_anim_set_path_cb(&anim, lv_anim_path_linear);
  lv_anim_start(&anim);
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
