#pragma once

#include "display.pb.h"
#include "screens.h"

#include <stdbool.h>
#include <stdint.h>

// Brightness limits (shared with brightness slider widget)
#define BRIGHTNESS_MIN 15   // Minimum brightness percent
#define BRIGHTNESS_MAX 100  // Maximum brightness percent

typedef void (*ui_brightness_callback_t)(uint8_t level, bool verify);
typedef uint32_t (*ui_fps_callback_t)(void);
typedef void (*ui_rotation_callback_t)(bool rotate_180);

// Display API
void ui_init(ui_brightness_callback_t brightness_callback, ui_fps_callback_t fps_callback,
             ui_fps_callback_t effective_fps_callback);
void ui_set_rotation_callback(ui_rotation_callback_t rotation_callback);
fwpb_display_result ui_execute_command(const fwpb_display_command* cmd);
void ui_set_brightness(uint8_t percent, bool verify);
void ui_set_local_brightness(uint8_t percent);
uint32_t ui_get_fps(void);
uint32_t ui_get_effective_fps(void);
pb_size_t ui_get_current_params_tag(void);
