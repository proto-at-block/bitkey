#pragma once

#include "display.pb.h"
#include "lvgl/lvgl.h"

typedef lv_obj_t* (*screen_init_fn)(void* ctx);
typedef void (*screen_update_fn)(void* ctx);
typedef void (*screen_destroy_fn)(void);

typedef struct {
  screen_init_fn init;
  screen_update_fn update;
  screen_destroy_fn destroy;
} screen_t;

const screen_t* screen_get_by_params_tag(pb_size_t params_tag);
