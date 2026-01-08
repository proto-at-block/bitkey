#pragma once

#include "screens.h"

lv_obj_t* screen_locked_init(void* ctx);
void screen_locked_destroy(void);
void screen_locked_update(void* ctx);
