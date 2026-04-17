#pragma once
#include "screens.h"

#include <stdbool.h>

lv_obj_t* screen_game_init(void* ctx);
void screen_game_destroy(void);
void screen_game_update(void* ctx);
