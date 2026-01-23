// Manufacturing test screen dispatcher

#include "screen_mfg.h"

#include "assert.h"
#include "display.pb.h"
#include "lvgl.h"
#include "screen_mfg_run_in.h"
#include "screen_mfg_touch_test.h"
#include "ui.h"
#include "widgets/mfg_scrolling_h.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Track which screen type is currently active
static enum {
  SCREEN_TYPE_RUN_IN,      // Delegated to screen_mfg_run_in.c
  SCREEN_TYPE_TOUCH_TEST,  // Delegated to screen_mfg_touch_test.c
  SCREEN_TYPE_SIMPLE,      // Handled locally (color bars, scrolling H, custom color)
} current_screen_type = SCREEN_TYPE_SIMPLE;

// Screen state
static lv_obj_t* screen = NULL;
static fwpb_display_mfg_test_mode current_test_mode =
  fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;
static uint32_t current_custom_rgb = 0;

// Widgets
static mfg_scrolling_h_t scrolling_h_widget = {0};

static void setup_color_bars(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_black(), 0);

  lv_coord_t w = lv_obj_get_width(scr);
  lv_coord_t h = lv_obj_get_height(scr);
  lv_coord_t bar_width = w / 8;

  // Define the 8 EBU color bars
  lv_color_t colors[8] = {
    lv_color_make(255, 255, 255),  // White
    lv_color_make(255, 255, 0),    // Yellow
    lv_color_make(0, 255, 255),    // Cyan
    lv_color_make(0, 255, 0),      // Green
    lv_color_make(255, 0, 255),    // Magenta
    lv_color_make(255, 0, 0),      // Red
    lv_color_make(0, 0, 255),      // Blue
    lv_color_make(0, 0, 0),        // Black
  };

  for (int i = 0; i < 8; i++) {
    lv_obj_t* bar = lv_obj_create(scr);
    if (!bar) {
      return;
    }
    lv_obj_remove_style_all(bar);
    lv_obj_set_style_bg_color(bar, colors[i], 0);
    lv_obj_set_style_bg_opa(bar, LV_OPA_COVER, 0);
    lv_obj_set_size(bar, bar_width, h);
    lv_obj_set_pos(bar, i * bar_width, 0);
    lv_obj_add_flag(bar, LV_OBJ_FLAG_IGNORE_LAYOUT);
  }
}

static bool is_run_in_mode(fwpb_display_mfg_test_mode test_mode) {
  return (test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN ||
          test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COUNTDOWN ||
          test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS ||
          test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION ||
          test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_NFC_TEST ||
          test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BURNIN_GRID ||
          test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BUTTON_BYPASS_WARNING);
}

lv_obj_t* screen_mfg_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  fwpb_display_mfg_test_mode test_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;
  uint32_t brightness_percent = 0;

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_mfg_tag) {
    test_mode = show_screen->params.mfg.test_mode;
    brightness_percent = show_screen->params.mfg.brightness_percent;
  }

  // Delegate to screen_mfg_run_in.c
  if (is_run_in_mode(test_mode)) {
    current_screen_type = SCREEN_TYPE_RUN_IN;
    return screen_mfg_run_in_init(ctx);
  }

  // Delegate to screen_mfg_touch_test.c
  if (test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_TOUCH_TEST_BOXES) {
    current_screen_type = SCREEN_TYPE_TOUCH_TEST;
    return screen_mfg_touch_test_init(ctx);
  }

  // Simple test modes - handle locally
  current_screen_type = SCREEN_TYPE_SIMPLE;
  current_test_mode = test_mode;

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);

  // Set brightness
  if (brightness_percent > 0 && brightness_percent <= 100) {
    ui_set_local_brightness((uint8_t)brightness_percent);
  } else {
    ui_set_local_brightness(SCREEN_BRIGHTNESS);
  }

  // Render based on mode
  switch (test_mode) {
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COLOR_BARS:
      setup_color_bars(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_SCROLLING_H:
      mfg_scrolling_h_create(screen, &scrolling_h_widget);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR:
      current_custom_rgb = show_screen ? show_screen->params.mfg.custom_rgb : 0;
      lv_obj_set_style_bg_color(screen, lv_color_hex(current_custom_rgb), 0);
      break;
    default:
      lv_obj_set_style_bg_color(screen, lv_color_hex(0xFF0000), 0);
      break;
  }

  return screen;
}

void screen_mfg_destroy(void) {
  if (current_screen_type == SCREEN_TYPE_RUN_IN) {
    screen_mfg_run_in_destroy();
    return;
  }

  if (current_screen_type == SCREEN_TYPE_TOUCH_TEST) {
    screen_mfg_touch_test_destroy();
    return;
  }

  // Clean up simple test modes
  if (!screen) {
    return;
  }

  mfg_scrolling_h_destroy(&scrolling_h_widget);

  lv_obj_del(screen);
  screen = NULL;
}

uint16_t screen_mfg_get_touch_boxes_remaining(void) {
  if (current_screen_type == SCREEN_TYPE_TOUCH_TEST) {
    return screen_mfg_touch_test_get_boxes_remaining();
  }
  return 0;
}

void screen_mfg_update(void* ctx) {
  if (current_screen_type == SCREEN_TYPE_RUN_IN) {
    screen_mfg_run_in_update(ctx);
    return;
  }

  if (current_screen_type == SCREEN_TYPE_TOUCH_TEST) {
    return;
  }

  // Simple test modes
  if (!screen) {
    lv_obj_t* new_screen = screen_mfg_init(ctx);
    if (new_screen) {
      lv_scr_load(new_screen);
    }
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_mfg_tag) {
    return;
  }

  const fwpb_display_params_mfg* params = &show_screen->params.mfg;

  // Apply brightness if specified
  if (params->brightness_percent > 0 && params->brightness_percent <= 100) {
    ui_set_local_brightness((uint8_t)params->brightness_percent);
  }

  // Determine what screen type the new mode requires
  bool new_is_run_in = is_run_in_mode(params->test_mode);
  bool new_is_touch_test =
    (params->test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_TOUCH_TEST_BOXES);

  // Check if we need to switch screen types (SIMPLE ↔ RUN_IN ↔ TOUCH_TEST)
  bool type_changed =
    (new_is_run_in && current_screen_type != SCREEN_TYPE_RUN_IN) ||
    (new_is_touch_test && current_screen_type != SCREEN_TYPE_TOUCH_TEST) ||
    (!new_is_run_in && !new_is_touch_test && current_screen_type != SCREEN_TYPE_SIMPLE);

  // For SIMPLE screens, also check if mode or RGB changed
  bool simple_mode_changed =
    (current_screen_type == SCREEN_TYPE_SIMPLE && params->test_mode != current_test_mode);
  bool simple_rgb_changed =
    (current_screen_type == SCREEN_TYPE_SIMPLE &&
     params->test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR &&
     params->custom_rgb != current_custom_rgb);

  if (type_changed || simple_mode_changed || simple_rgb_changed) {
    screen_mfg_destroy();
    lv_obj_t* new_screen = screen_mfg_init(ctx);
    if (new_screen) {
      lv_scr_load(new_screen);
    }
    return;
  }

  // Note: Brightness changes are applied above without recreating the screen
}
