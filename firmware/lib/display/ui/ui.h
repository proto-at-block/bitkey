#pragma once

#include "display.pb.h"
#include "screens.h"

#include <stdbool.h>
#include <stdint.h>

typedef void (*ui_brightness_callback_t)(uint8_t level);
typedef uint32_t (*ui_fps_callback_t)(void);

// Display API
void ui_init(ui_brightness_callback_t brightness_callback, ui_fps_callback_t fps_callback,
             ui_fps_callback_t effective_fps_callback);
fwpb_display_result ui_execute_command(const fwpb_display_command* cmd);
void ui_set_local_brightness(uint8_t percent);
uint32_t ui_get_fps(void);
uint32_t ui_get_effective_fps(void);
pb_size_t ui_get_current_params_tag(void);
