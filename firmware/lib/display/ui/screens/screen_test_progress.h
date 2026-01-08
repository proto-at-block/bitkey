#pragma once

#include "lvgl/lvgl.h"

lv_obj_t* screen_test_progress_init(void* ctx);
void screen_test_progress_update(void* ctx);
void screen_test_progress_destroy(void);
