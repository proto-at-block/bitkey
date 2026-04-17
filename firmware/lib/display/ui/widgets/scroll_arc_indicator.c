/**
 * @file scroll_arc_indicator.c
 * @brief Reusable edge arc scroll indicator implementation
 */

#include "scroll_arc_indicator.h"

#include "assert.h"

#include <string.h>

#define SCROLL_ARC_BACKGROUND_WIDTH    40
#define SCROLL_ARC_WIDTH               12
#define SCROLL_ARC_MIN_INDICATOR_WIDTH 10
#define SCROLL_ARC_EDGE_GAP            8
#define SCROLL_ARC_START_ANGLE         (360 - SCROLL_ARC_BACKGROUND_WIDTH / 2)
#define SCROLL_ARC_BG_COLOR            0x404040
#define SCROLL_ARC_FG_COLOR            0xFFFFFF
#define SCROLL_ARC_RADIUS              (233 - SCROLL_ARC_WIDTH / 2 - SCROLL_ARC_EDGE_GAP)

static void scroll_arc_indicator_scroll_event_cb(lv_event_t* e);
static lv_obj_t* create_arc(lv_obj_t* parent);

void scroll_arc_indicator_create(lv_obj_t* parent, lv_obj_t* scroll_target,
                                 scroll_arc_indicator_t* indicator) {
  ASSERT(parent != NULL);
  ASSERT(scroll_target != NULL);
  ASSERT(indicator != NULL);

  memset(indicator, 0, sizeof(*indicator));
  indicator->scroll_target = scroll_target;

  indicator->background_arc = create_arc(parent);
  if (!indicator->background_arc) {
    return;
  }
  lv_arc_set_bg_angles(indicator->background_arc, SCROLL_ARC_START_ANGLE,
                       SCROLL_ARC_START_ANGLE + SCROLL_ARC_BACKGROUND_WIDTH);
  lv_arc_set_angles(indicator->background_arc, SCROLL_ARC_START_ANGLE,
                    SCROLL_ARC_START_ANGLE + SCROLL_ARC_BACKGROUND_WIDTH);
  lv_arc_set_range(indicator->background_arc, 0, 100);
  lv_arc_set_value(indicator->background_arc, 100);
  lv_obj_set_style_arc_color(indicator->background_arc, lv_color_hex(SCROLL_ARC_BG_COLOR),
                             LV_PART_INDICATOR);

  indicator->indicator_arc = create_arc(parent);
  if (!indicator->indicator_arc) {
    lv_obj_del(indicator->background_arc);
    indicator->background_arc = NULL;
    return;
  }
  lv_arc_set_bg_angles(indicator->indicator_arc, 0, 0);
  lv_arc_set_angles(indicator->indicator_arc, SCROLL_ARC_START_ANGLE,
                    SCROLL_ARC_START_ANGLE + SCROLL_ARC_BACKGROUND_WIDTH);
  lv_obj_set_style_arc_color(indicator->indicator_arc, lv_color_hex(SCROLL_ARC_FG_COLOR),
                             LV_PART_INDICATOR);

  lv_obj_add_event_cb(scroll_target, scroll_arc_indicator_scroll_event_cb, LV_EVENT_SCROLL,
                      indicator);
  indicator->is_initialized = true;
}

void scroll_arc_indicator_update(scroll_arc_indicator_t* indicator) {
  if (!indicator || !indicator->is_initialized || !indicator->scroll_target ||
      !indicator->indicator_arc) {
    return;
  }

  if (!lv_obj_is_valid(indicator->scroll_target) || !lv_obj_is_valid(indicator->indicator_arc)) {
    return;
  }

  lv_coord_t scroll_y = lv_obj_get_scroll_y(indicator->scroll_target);
  lv_coord_t scroll_max = lv_obj_get_scroll_bottom(indicator->scroll_target) + scroll_y;
  lv_coord_t viewport_height = lv_obj_get_height(indicator->scroll_target);
  lv_coord_t content_height = viewport_height + scroll_max;

  int16_t indicator_width = SCROLL_ARC_BACKGROUND_WIDTH;
  if (content_height > 0) {
    float visible_ratio = (float)viewport_height / (float)content_height;
    if (visible_ratio > 1.0f) {
      visible_ratio = 1.0f;
    }
    indicator_width = (int16_t)(visible_ratio * SCROLL_ARC_BACKGROUND_WIDTH);
    if (indicator_width < SCROLL_ARC_MIN_INDICATOR_WIDTH) {
      indicator_width = SCROLL_ARC_MIN_INDICATOR_WIDTH;
    }
  }

  float scroll_pct = 0.0f;
  if (scroll_max > 0) {
    scroll_pct = (float)scroll_y / (float)scroll_max;
    if (scroll_pct < 0.0f) {
      scroll_pct = 0.0f;
    }
    if (scroll_pct > 1.0f) {
      scroll_pct = 1.0f;
    }
  }

  int16_t available_range = SCROLL_ARC_BACKGROUND_WIDTH - indicator_width;
  int16_t offset = (int16_t)(scroll_pct * available_range);
  int16_t start_angle = SCROLL_ARC_START_ANGLE + offset;
  int16_t end_angle = start_angle + indicator_width;

  lv_arc_set_angles(indicator->indicator_arc, start_angle, end_angle);
}

void scroll_arc_indicator_destroy(scroll_arc_indicator_t* indicator) {
  if (!indicator) {
    return;
  }

  if (indicator->scroll_target && lv_obj_is_valid(indicator->scroll_target)) {
    lv_obj_remove_event_cb_with_user_data(indicator->scroll_target,
                                          scroll_arc_indicator_scroll_event_cb, indicator);
  }

  if (indicator->indicator_arc && lv_obj_is_valid(indicator->indicator_arc)) {
    lv_obj_del(indicator->indicator_arc);
  }
  if (indicator->background_arc && lv_obj_is_valid(indicator->background_arc)) {
    lv_obj_del(indicator->background_arc);
  }

  memset(indicator, 0, sizeof(*indicator));
}

static void scroll_arc_indicator_scroll_event_cb(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_SCROLL) {
    return;
  }

  scroll_arc_indicator_update(lv_event_get_user_data(e));
}

static lv_obj_t* create_arc(lv_obj_t* parent) {
  lv_obj_t* arc = lv_arc_create(parent);
  if (!arc) {
    return NULL;
  }

  lv_obj_set_size(arc, SCROLL_ARC_RADIUS * 2, SCROLL_ARC_RADIUS * 2);
  lv_obj_center(arc);
  lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
  lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_set_style_arc_opa(arc, LV_OPA_TRANSP, LV_PART_MAIN);
  lv_obj_set_style_arc_width(arc, SCROLL_ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(arc, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
  lv_obj_set_style_bg_opa(arc, LV_OPA_TRANSP, 0);

  return arc;
}
