#include "mfg_scrolling_h.h"

#include "assert.h"

#define FONT_SCROLLING_H (&cash_sans_mono_regular_36)

static void animate_scroll_h(lv_timer_t* timer) {
  mfg_scrolling_h_t* widget = (mfg_scrolling_h_t*)lv_timer_get_user_data(timer);
  if (!widget || !widget->label1 || !widget->label2 || !widget->container) {
    return;
  }

  // Scroll left by 3 pixels per frame
  widget->offset -= 3;

  // Get the width of one label
  lv_coord_t label_width = lv_obj_get_width(widget->label1);

  // When first label scrolls completely off screen, reset position
  if (widget->offset <= -label_width) {
    widget->offset = 0;
  }

  // Position both labels - second one follows first
  lv_obj_set_x(widget->label1, widget->offset);
  lv_obj_set_x(widget->label2, widget->offset + label_width);
}

void mfg_scrolling_h_create(lv_obj_t* parent, mfg_scrolling_h_t* widget) {
  ASSERT(parent != NULL);
  ASSERT(widget != NULL);
  ASSERT(!widget->is_initialized);

  // Set white background on parent
  lv_obj_set_style_bg_color(parent, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, 0);

  // Create scrolling container with clipping enabled
  widget->container = lv_obj_create(parent);
  if (!widget->container) {
    return;
  }
  lv_obj_remove_style_all(widget->container);
  lv_obj_set_size(widget->container, LV_PCT(100), LV_PCT(100));
  lv_obj_add_flag(widget->container, LV_OBJ_FLAG_IGNORE_LAYOUT);
  lv_obj_set_style_clip_corner(widget->container, true, 0);

  // Generate grid of H characters (15 rows × 22 columns)
  char h_text[360];
  int pos = 0;
  for (int row = 0; row < 15; row++) {
    for (int col = 0; col < 22; col++) {
      h_text[pos++] = 'H';
    }
    h_text[pos++] = '\n';
  }
  h_text[pos] = '\0';

  // Create two identical labels for seamless scrolling
  widget->label1 = lv_label_create(widget->container);
  if (!widget->label1) {
    return;
  }
  lv_obj_set_style_text_color(widget->label1, lv_color_black(), 0);
  lv_obj_set_style_text_font(widget->label1, FONT_SCROLLING_H, 0);
  lv_label_set_text(widget->label1, h_text);
  lv_obj_set_pos(widget->label1, 0, 0);

  widget->label2 = lv_label_create(widget->container);
  if (!widget->label2) {
    return;
  }
  lv_obj_set_style_text_color(widget->label2, lv_color_black(), 0);
  lv_obj_set_style_text_font(widget->label2, FONT_SCROLLING_H, 0);
  lv_label_set_text(widget->label2, h_text);

  // Position second label after the first
  lv_coord_t label_width = lv_obj_get_width(widget->label1);
  lv_obj_set_pos(widget->label2, label_width, 0);

  widget->offset = 0;

  // Create animation timer with widget as user_data
  widget->timer = lv_timer_create(animate_scroll_h, 33, widget);

  widget->is_initialized = true;
}

void mfg_scrolling_h_destroy(mfg_scrolling_h_t* widget) {
  if (!widget || !widget->is_initialized) {
    return;
  }

  if (widget->timer) {
    lv_timer_del(widget->timer);
    widget->timer = NULL;
  }
  if (widget->label1) {
    lv_obj_del(widget->label1);
    widget->label1 = NULL;
  }
  if (widget->label2) {
    lv_obj_del(widget->label2);
    widget->label2 = NULL;
  }
  if (widget->container) {
    lv_obj_del(widget->container);
    widget->container = NULL;
  }

  widget->offset = 0;
  widget->is_initialized = false;
}
