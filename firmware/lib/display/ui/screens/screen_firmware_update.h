#pragma once

#include "lvgl/lvgl.h"

lv_obj_t* screen_firmware_update_init(void* ctx);
void screen_firmware_update_destroy(void);
void screen_firmware_update_update(void* params);
