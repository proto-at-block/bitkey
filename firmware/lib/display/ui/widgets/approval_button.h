#pragma once

#include "lvgl.h"

#define APPROVAL_BUTTON_SIZE           80
#define APPROVAL_BUTTON_BOTTOM_MARGIN  32
#define APPROVAL_BUTTON_BG_COLOR       0x404040
#define APPROVAL_BUTTON_BG_OPA         LV_OPA_70
#define APPROVAL_BUTTON_RING_COLOR     0xD1FB96
#define APPROVAL_BUTTON_EXT_CLICK_AREA 100
#define APPROVAL_BUTTON_ICON_ZOOM      LV_SCALE_NONE

lv_obj_t* approval_button_create(lv_obj_t* parent, lv_event_cb_t event_cb);
lv_obj_t* approval_button_create_with_label(lv_obj_t* parent, const char* text,
                                            lv_event_cb_t event_cb);
void approval_button_set_hold_state(lv_obj_t* button);
void approval_button_set_idle_state(lv_obj_t* button);
void approval_button_set_icon_highlight(lv_obj_t* button, lv_color_t color);
void approval_button_clear_icon_highlight(lv_obj_t* button);
