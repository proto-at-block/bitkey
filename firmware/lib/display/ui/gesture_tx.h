#pragma once

#include "lvgl/lvgl.h"

/**
 * @brief Manually attach gesture handlers to a screen.
 *
 * Gestures detected on the screen will be forwarded to Core via the
 * display_send_queue_msg() function.
 *
 * @param screen The LVGL screen object to attach handlers to
 */
void gesture_tx_attach_to_screen(lv_obj_t* screen);
