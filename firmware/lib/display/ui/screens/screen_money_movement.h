#pragma once

#include "screens.h"

#include <stdint.h>

lv_obj_t* screen_money_movement_init(void* ctx);
void screen_money_movement_destroy(void);
void screen_money_movement_update(void* ctx);

#if LV_USE_SNAPSHOT
void screen_money_movement_snapshot_show_confirmed(void);
void screen_money_movement_snapshot_show_cancel_followup(void);
void screen_money_movement_snapshot_start_hold_progress(uint8_t percent);
void screen_money_movement_snapshot_start_hold_reverse(uint8_t percent);
void screen_money_movement_snapshot_start_cancel_reverse(uint8_t percent);
#endif
