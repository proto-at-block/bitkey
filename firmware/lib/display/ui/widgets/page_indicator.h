#pragma once

#include "lvgl.h"

#include <stdbool.h>

#define PAGE_INDICATOR_MAX_PAGES 6

// Page indicator for multi-page screens
typedef struct {
  lv_obj_t* background_arc;
  lv_obj_t* foreground_arc;
  lv_anim_t position_anim;
  int total_pages;
  int current_page;
  bool is_initialized;
} page_indicator_t;

void page_indicator_create(lv_obj_t* parent, page_indicator_t* indicator, int total_pages);
void page_indicator_update(page_indicator_t* indicator, int current_page);
void page_indicator_destroy(page_indicator_t* indicator);
