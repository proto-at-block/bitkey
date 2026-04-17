#include "approval_button.h"

extern const lv_img_dsc_t check;

static bool approval_button_is_valid(lv_obj_t* button) {
  return button && lv_obj_is_valid(button);
}

static lv_obj_t* approval_button_get_icon(lv_obj_t* button) {
  if (!approval_button_is_valid(button)) {
    return NULL;
  }

  lv_obj_t* icon = lv_obj_get_child(button, 0);
  if (!icon || !lv_obj_is_valid(icon)) {
    return NULL;
  }

  return icon;
}

static void approval_button_set_icon_color(lv_obj_t* icon, lv_color_t color) {
  if (!icon || !lv_obj_is_valid(icon)) {
    return;
  }

  lv_obj_set_style_img_recolor(icon, color, 0);
  lv_obj_set_style_img_recolor_opa(icon, LV_OPA_COVER, 0);
}

lv_obj_t* approval_button_create(lv_obj_t* parent, lv_event_cb_t event_cb) {
  lv_obj_t* button = lv_obj_create(parent);
  if (!button) {
    return NULL;
  }

  lv_obj_set_size(button, APPROVAL_BUTTON_SIZE, APPROVAL_BUTTON_SIZE);
  lv_obj_align(button, LV_ALIGN_BOTTOM_MID, 0, -APPROVAL_BUTTON_BOTTOM_MARGIN);
  lv_obj_set_ext_click_area(button, APPROVAL_BUTTON_EXT_CLICK_AREA);
  lv_obj_set_style_radius(button, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(button, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(button, APPROVAL_BUTTON_BG_OPA, 0);
  lv_obj_set_style_border_opa(button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(button, 0, 0);
  lv_obj_clear_flag(button, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(button, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(button, LV_OBJ_FLAG_PRESS_LOCK);

  if (event_cb) {
    lv_obj_add_event_cb(button, event_cb, LV_EVENT_PRESSED, NULL);
    lv_obj_add_event_cb(button, event_cb, LV_EVENT_RELEASED, NULL);
    lv_obj_add_event_cb(button, event_cb, LV_EVENT_PRESS_LOST, NULL);
  }

  lv_obj_t* icon = lv_img_create(button);
  if (icon) {
    lv_img_set_src(icon, &check);
    approval_button_set_icon_color(icon, lv_color_white());
    lv_obj_center(icon);
  }

  return button;
}

lv_obj_t* approval_button_create_with_label(lv_obj_t* parent, const char* text,
                                            lv_event_cb_t event_cb) {
  lv_obj_t* button = lv_obj_create(parent);
  if (!button) {
    return NULL;
  }

  lv_obj_set_size(button, APPROVAL_BUTTON_SIZE, APPROVAL_BUTTON_SIZE);
  lv_obj_align(button, LV_ALIGN_BOTTOM_MID, 0, -APPROVAL_BUTTON_BOTTOM_MARGIN);
  lv_obj_set_ext_click_area(button, APPROVAL_BUTTON_EXT_CLICK_AREA);
  lv_obj_set_style_radius(button, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(button, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(button, APPROVAL_BUTTON_BG_OPA, 0);
  lv_obj_set_style_border_opa(button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(button, 0, 0);
  lv_obj_clear_flag(button, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(button, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(button, LV_OBJ_FLAG_PRESS_LOCK);

  if (event_cb) {
    lv_obj_add_event_cb(button, event_cb, LV_EVENT_CLICKED, NULL);
  }

  lv_obj_t* label = lv_label_create(button);
  if (label) {
    lv_label_set_text(label, text);
    lv_obj_set_style_text_color(label, lv_color_white(), 0);
    extern const lv_font_t cash_sans_mono_regular_26;
    lv_obj_set_style_text_font(label, &cash_sans_mono_regular_26, 0);
    lv_obj_center(label);
  }

  return button;
}

void approval_button_set_hold_state(lv_obj_t* button) {
  if (!approval_button_is_valid(button)) {
    return;
  }

  lv_obj_set_style_bg_color(button, lv_color_hex(APPROVAL_BUTTON_RING_COLOR), 0);
  lv_obj_set_style_bg_opa(button, LV_OPA_COVER, 0);

  lv_obj_t* icon = approval_button_get_icon(button);
  if (!icon) {
    return;
  }

  approval_button_set_icon_color(icon, lv_color_black());
}

void approval_button_set_idle_state(lv_obj_t* button) {
  if (!approval_button_is_valid(button)) {
    return;
  }

  lv_obj_set_style_bg_color(button, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(button, APPROVAL_BUTTON_BG_OPA, 0);
  approval_button_clear_icon_highlight(button);
}

void approval_button_set_icon_highlight(lv_obj_t* button, lv_color_t color) {
  lv_obj_t* icon = approval_button_get_icon(button);
  if (!icon) {
    return;
  }

  approval_button_set_icon_color(icon, color);
}

void approval_button_clear_icon_highlight(lv_obj_t* button) {
  lv_obj_t* icon = approval_button_get_icon(button);
  if (!icon) {
    return;
  }

  approval_button_set_icon_color(icon, lv_color_white());
}
