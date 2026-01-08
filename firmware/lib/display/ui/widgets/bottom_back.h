#pragma once

#include "lvgl.h"

#include <stdbool.h>

// Bottom back button widget - displays cycle_back icon in a circle at bottom center
typedef struct {
  lv_obj_t* container;
  lv_obj_t* circle;
  lv_obj_t* icon_img;
  bool is_initialized;
} bottom_back_t;

void bottom_back_create(lv_obj_t* parent, bottom_back_t* button);
void bottom_back_destroy(bottom_back_t* button);
