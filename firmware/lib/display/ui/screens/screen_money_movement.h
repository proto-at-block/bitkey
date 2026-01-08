#pragma once

#include "screens.h"

#include <stdint.h>

lv_obj_t* screen_money_movement_init(void* ctx);
void screen_money_movement_destroy(void);
void screen_money_movement_update(void* ctx);
