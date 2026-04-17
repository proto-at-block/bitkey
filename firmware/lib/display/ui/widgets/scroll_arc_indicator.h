/**
 * @file scroll_arc_indicator.h
 * @brief Reusable edge arc scroll indicator for vertically scrollable screens
 */

#pragma once

#include "lvgl.h"

#include <stdbool.h>

typedef struct {
  lv_obj_t* scroll_target;
  lv_obj_t* background_arc;
  lv_obj_t* indicator_arc;
  bool is_initialized;
} scroll_arc_indicator_t;

void scroll_arc_indicator_create(lv_obj_t* parent, lv_obj_t* scroll_target,
                                 scroll_arc_indicator_t* indicator);
void scroll_arc_indicator_update(scroll_arc_indicator_t* indicator);
void scroll_arc_indicator_destroy(scroll_arc_indicator_t* indicator);
