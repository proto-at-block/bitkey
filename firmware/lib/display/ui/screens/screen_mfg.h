#pragma once
#include "screens.h"

#include <stdint.h>

lv_obj_t* screen_mfg_init(void* ctx);
void screen_mfg_destroy(void);
void screen_mfg_update(void* ctx);
uint16_t screen_mfg_get_touch_boxes_remaining(void);
