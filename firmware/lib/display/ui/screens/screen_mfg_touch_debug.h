/**
 * @file screen_mfg_touch_debug.h
 * @brief Touch Debug main menu screen
 *
 * Main menu for touch debugging with options:
 * 1. Robot Test
 * 2. Touch Viewer
 * 3. Capacitance Viewer
 * 4. Disable Host Touch
 */

#pragma once

#include "screens.h"

lv_obj_t* screen_touch_debug_init(void* ctx);
void screen_touch_debug_update(void* ctx);
void screen_touch_debug_destroy(void);
