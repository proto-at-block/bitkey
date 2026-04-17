/**
 * @file screen_mfg_touch_debug.c
 * @brief Touch Debug screen — shared core, menu, and simple modes (mfgtest only).
 *
 * Simple modes kept here: Robot Test, Tap Latency, Disable Touch.
 * Viewer modes: screen_mfg_touch_debug_viewers.c
 * Diagnostic modes: screen_mfg_touch_debug_diag.c
 */

#include "screen_mfg_touch_debug.h"

#include "screen_mfg_touch_debug_internal.h"

#ifdef MFGTEST

#include "assert.h"
#include "display_action.h"
#include "lvgl/lvgl.h"
#include "printf.h"
#include "top_back.h"
#include "touch.h"
#include "touch_mfgtest.h"
#include "ui.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

// =============================================================================
// Menu configuration
// =============================================================================
#define MENU_ITEM_SPACING   10
#define MENU_START_Y        100
#define BUTTON_WIDTH        200
#define BUTTON_HEIGHT       82
#define MENU_BOTTOM_PADDING 80

#define CROSSHAIR_COLOR lv_color_hex(0xFF0000)

typedef enum {
  MENU_ITEM_TOUCH_VIEWER_V2 = 0,
  MENU_ITEM_ROBOT_TEST,
  MENU_ITEM_DISPLAY_TEST,
  MENU_ITEM_TAP_LATENCY,
  MENU_ITEM_CALIBRATE_TEST,
  MENU_ITEM_CAP_VIEWER,
  MENU_ITEM_SELF_CAP_VIEWER,
  MENU_ITEM_FORCE_FWUP,
  MENU_ITEM_COLLECT_DATA,
  MENU_ITEM_DISABLE_TOUCH,
  MENU_ITEM_POWER_MODE,
  MENU_ITEM_COUNT
} touch_debug_menu_item_t;

static const char* menu_labels[MENU_ITEM_COUNT] = {
  "Touch Viewer", "Robot Test",      "Display Test", "Tap Latency",   "Calibrate Test", "Mut Cap",
  "Self Cap",     "Force FW Update", "Collect Data", "Disable Touch", "Power Mode",
};

// =============================================================================
// Shared screen state (externed in screen_mfg_touch_debug_internal.h)
// =============================================================================
lv_obj_t* td_screen = NULL;
lv_obj_t* td_title_label = NULL;
screen_mode_t td_current_mode = MODE_MENU;
uint8_t td_touch_fw_version = 0;
bool td_touch_fw_version_read = false;

// =============================================================================
// Local state — menu + simple modes
// =============================================================================
static lv_obj_t* menu_buttons[MENU_ITEM_COUNT] = {NULL};
static lv_obj_t* fw_version_label = NULL;
static top_back_t back_button = {0};

// Touch firmware ID info (read once, displayed in menu)
static uint8_t touch_vendor_id = 0;
static uint8_t touch_module_id = 0;

// Robot test objects
static lv_obj_t* crosshair_h = NULL;
static lv_obj_t* crosshair_v = NULL;
static lv_obj_t* coord_label = NULL;
static lv_obj_t* dot_top = NULL;
static lv_obj_t* dot_bottom = NULL;
static lv_obj_t* dot_left = NULL;
static lv_obj_t* dot_right = NULL;
static lv_obj_t* dot_center = NULL;
static bool is_touching = false;

// Disable touch state
static bool touch_host_suspended = false;
static lv_obj_t* disable_status_label = NULL;
static lv_obj_t* disable_btn = NULL;
static lv_obj_t* placeholder_label = NULL;

// Tap latency state
static bool tap_latency_is_white = false;
static lv_timer_t* tap_latency_reset_timer = NULL;
#define TAP_LATENCY_RESET_MS 20000

// Forward declarations — local modes
static void show_robot_test(void);
static void show_disable_touch(void);
static void show_tap_latency(void);
static void robot_test_event_handler(lv_event_t* e);
static void tap_latency_event_handler(lv_event_t* e);
static void tap_latency_reset_timer_cb(lv_timer_t* timer);
static void disable_touch_btn_handler(lv_event_t* e);
static void update_disable_touch_ui(void);
static void read_touch_fw_version(void);
static void menu_item_click_handler(lv_event_t* e);

// =============================================================================
// Shared functions
// =============================================================================

static void reset_screen_state(void) {
  td_screen = NULL;
  td_title_label = NULL;
  fw_version_label = NULL;
  memset(&back_button, 0, sizeof(top_back_t));
  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    menu_buttons[i] = NULL;
  }
  // Robot test
  crosshair_h = NULL;
  crosshair_v = NULL;
  coord_label = NULL;
  dot_top = NULL;
  dot_bottom = NULL;
  dot_left = NULL;
  dot_right = NULL;
  dot_center = NULL;
  // Disable touch
  placeholder_label = NULL;
  disable_status_label = NULL;
  disable_btn = NULL;
  // Tap latency
  tap_latency_is_white = false;
  tap_latency_reset_timer = NULL;

  // Reset other files' state
  td_viewers_reset_state();
  td_diag_reset_state();

  td_current_mode = MODE_MENU;
  is_touching = false;
}

void td_screen_delete_handler(lv_event_t* e) {
  (void)e;
  printf("[TouchDebug] Screen auto-deleted, resetting state\r\n");
  reset_screen_state();
}

static lv_obj_t* create_reference_dot(lv_obj_t* parent, int32_t x, int32_t y) {
  lv_obj_t* dot = lv_obj_create(parent);
  if (!dot) {
    return NULL;
  }
  lv_obj_set_size(dot, 1, 1);
  lv_obj_set_pos(dot, x, y);
  lv_obj_set_style_bg_color(dot, lv_color_hex(0x00FF00), 0);
  lv_obj_set_style_bg_opa(dot, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(dot, 0, 0);
  lv_obj_add_flag(dot, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(dot, LV_OBJ_FLAG_CLICKABLE);
  return dot;
}

void td_clear_screen_content(void) {
  // Cleanup timers in other files
  td_viewers_cleanup_timers();
  td_diag_cleanup_timers();

  // Cleanup tap latency timer
  if (tap_latency_reset_timer != NULL) {
    lv_timer_del(tap_latency_reset_timer);
    tap_latency_reset_timer = NULL;
  }

  // Re-enable ESD checks if leaving a screen that paused them
  if (td_current_mode == MODE_CAP_VIEWER || td_current_mode == MODE_SELF_CAP_VIEWER ||
      td_current_mode == MODE_CALIBRATE_TEST) {
    touch_set_fwup_in_progress(false);
  }

  // Cleanup display test before lv_obj_clean destroys its objects
  if (td_current_mode == MODE_DISPLAY_TEST) {
    td_diag_cleanup_mode();
  }

  // Delete all children except the screen itself
  lv_obj_clean(td_screen);

  // Reset local pointers
  td_title_label = NULL;
  fw_version_label = NULL;
  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    menu_buttons[i] = NULL;
  }
  crosshair_h = NULL;
  crosshair_v = NULL;
  coord_label = NULL;
  dot_top = NULL;
  dot_bottom = NULL;
  dot_left = NULL;
  dot_right = NULL;
  dot_center = NULL;
  placeholder_label = NULL;
  disable_status_label = NULL;
  disable_btn = NULL;
  is_touching = false;
  tap_latency_is_white = false;

  // Reset other files' state
  td_viewers_reset_state();
  td_diag_reset_state();
}

// =============================================================================
// Menu
// =============================================================================

static void back_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    // Send BACK action — menu flow handles this by returning to scan,
    // but we override in menu.c to reload the menu screen for touch debug
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK, 0);
  }
}

void td_show_menu(void) {
  td_clear_screen_content();
  td_current_mode = MODE_MENU;

  lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);

  // Read touch firmware version on first menu display
  if (!td_touch_fw_version_read) {
    read_touch_fw_version();
  }

  // Create title label with firmware version info (moved down for circular display)
  td_title_label = lv_label_create(td_screen);
  if (td_touch_fw_version_read) {
    char title_buf[64];
    snprintf(title_buf, sizeof(title_buf), "Touch Debug FW:0x%02X", td_touch_fw_version);
    lv_label_set_text(td_title_label, title_buf);
  } else {
    lv_label_set_text(td_title_label, "Touch Debug (FW:ERR)");
  }
  lv_obj_set_style_text_color(td_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 75);

  // Back button to return to system menu
  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(td_screen, &back_button, back_button_handler);
  if (back_button.container) {
    lv_obj_move_foreground(back_button.container);
  }

  // "Scroll for more" hint to the right of buttons area
  lv_obj_t* scroll_hint = lv_label_create(td_screen);
  lv_label_set_text(scroll_hint, "scroll\nfor\nmore");
  lv_obj_set_style_text_color(scroll_hint, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(scroll_hint, &cash_sans_mono_regular_20, 0);
  lv_obj_set_style_text_align(scroll_hint, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(scroll_hint, LV_ALIGN_RIGHT_MID, -5, 0);

  // Create a scrollable container for menu buttons
  int32_t menu_start = MENU_START_Y + 15;
  lv_obj_t* menu_container = lv_obj_create(td_screen);
  lv_obj_set_size(menu_container, LV_HOR_RES - 60, LV_VER_RES - menu_start);
  lv_obj_set_pos(menu_container, 0, menu_start);
  lv_obj_set_style_bg_opa(menu_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(menu_container, 0, 0);
  lv_obj_set_style_pad_all(menu_container, 0, 0);
  lv_obj_set_scroll_dir(menu_container, LV_DIR_VER);
  lv_obj_set_scrollbar_mode(menu_container, LV_SCROLLBAR_MODE_AUTO);

  // Create menu buttons inside the scrollable container
  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    menu_buttons[i] = lv_btn_create(menu_container);
    lv_obj_set_size(menu_buttons[i], BUTTON_WIDTH, BUTTON_HEIGHT);
    lv_obj_set_pos(menu_buttons[i], ((LV_HOR_RES - 60) - BUTTON_WIDTH) / 2,
                   i * (BUTTON_HEIGHT + MENU_ITEM_SPACING));

    lv_obj_set_style_bg_color(menu_buttons[i], lv_color_hex(0x404040), 0);
    lv_obj_set_style_bg_color(menu_buttons[i], lv_color_hex(0x606060), LV_STATE_PRESSED);
    lv_obj_set_style_radius(menu_buttons[i], 8, 0);
    lv_obj_set_style_border_width(menu_buttons[i], 0, 0);
    lv_obj_clear_flag(menu_buttons[i], LV_OBJ_FLAG_PRESS_LOCK);

    lv_obj_t* label = lv_label_create(menu_buttons[i]);
    lv_label_set_text(label, menu_labels[i]);
    lv_obj_set_style_text_color(label, lv_color_white(), 0);
    lv_obj_set_style_text_font(label, &cash_sans_mono_regular_20, 0);
    lv_obj_center(label);

    lv_obj_add_event_cb(menu_buttons[i], menu_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);
  }

  // Spacer at bottom for scrolling
  lv_obj_t* spacer = lv_obj_create(menu_container);
  lv_obj_set_size(spacer, 1, MENU_BOTTOM_PADDING);
  lv_obj_set_pos(spacer, 0, MENU_ITEM_COUNT * (BUTTON_HEIGHT + MENU_ITEM_SPACING));
  lv_obj_set_style_bg_opa(spacer, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(spacer, 0, 0);
  lv_obj_clear_flag(spacer, LV_OBJ_FLAG_CLICKABLE);
}

static void menu_item_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  uint32_t item_idx = (uint32_t)(uintptr_t)lv_event_get_user_data(e);

  switch (item_idx) {
    case MENU_ITEM_ROBOT_TEST:
      show_robot_test();
      break;
    case MENU_ITEM_TOUCH_VIEWER_V2:
      td_show_touch_viewer_v2_settings();
      break;
    case MENU_ITEM_CAP_VIEWER:
      td_show_cap_viewer();
      break;
    case MENU_ITEM_SELF_CAP_VIEWER:
      td_show_self_cap_viewer();
      break;
    case MENU_ITEM_CALIBRATE_TEST:
      td_show_calibrate_test();
      break;
    case MENU_ITEM_FORCE_FWUP:
      td_show_force_fwup();
      break;
    case MENU_ITEM_POWER_MODE:
      td_show_power_mode();
      break;
    case MENU_ITEM_TAP_LATENCY:
      show_tap_latency();
      break;
    case MENU_ITEM_COLLECT_DATA:
      td_show_collect_data();
      break;
    case MENU_ITEM_DISABLE_TOUCH:
      show_disable_touch();
      break;
    case MENU_ITEM_DISPLAY_TEST:
      td_show_display_test();
      break;
    default:
      break;
  }
}

// =============================================================================
// Touch Firmware Version
// =============================================================================

static void read_touch_fw_version(void) {
  uint8_t data[1] = {0};
  bool success = true;

  // Suppress touch task I2C traffic while we read registers from the display task
  touch_set_fwup_in_progress(true);

  // Read firmware version (0xA6)
  if (touch_mfgtest_read_buf(TOUCH_MFGTEST_FW_VERSION_REG, data, 1)) {
    td_touch_fw_version = data[0];
    printf("Touch FW Version (0xA6): 0x%02X (%d)\r\n", td_touch_fw_version, td_touch_fw_version);
  } else {
    success = false;
    printf("Touch FW Version: READ FAILED\r\n");
  }

  // Read vendor/panel ID (0xA8) - often contains D-revision info
  if (touch_mfgtest_read_buf(TOUCH_MFGTEST_VENDOR_ID_REG, data, 1)) {
    touch_vendor_id = data[0];
    printf("Touch Vendor ID (0xA8): 0x%02X (%d)\r\n", touch_vendor_id, touch_vendor_id);
  } else {
    printf("Touch Vendor ID: READ FAILED\r\n");
  }

  // Read module/IC version (0xA9)
  if (touch_mfgtest_read_buf(TOUCH_MFGTEST_MODULE_ID_REG, data, 1)) {
    touch_module_id = data[0];
    printf("Touch Module ID (0xA9): 0x%02X (%d)\r\n", touch_module_id, touch_module_id);
  } else {
    printf("Touch Module ID: READ FAILED\r\n");
  }

  td_touch_fw_version_read = success;

  touch_set_fwup_in_progress(false);
}

// =============================================================================
// Robot Test
// =============================================================================

static void show_robot_test(void) {
  td_clear_screen_content();
  td_current_mode = MODE_ROBOT_TEST;

  // Dark gray background like original robo touch mode
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x404040), 0);
  lv_obj_set_style_pad_all(td_screen, 0, 0);

  // Title - matches original robo touch mode style
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Robot Test");
  lv_obj_set_style_text_color(td_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 35);

  // Coordinate label (shown while touching)
  coord_label = lv_label_create(td_screen);
  lv_label_set_text(coord_label, "");
  lv_obj_set_style_text_color(coord_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(coord_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(coord_label, LV_ALIGN_TOP_MID, 0, 60);
  lv_obj_add_flag(coord_label, LV_OBJ_FLAG_HIDDEN);

  // Horizontal crosshair line - full screen width, 1px height
  crosshair_h = lv_obj_create(td_screen);
  lv_obj_set_size(crosshair_h, LV_HOR_RES, 1);
  lv_obj_set_style_bg_color(crosshair_h, CROSSHAIR_COLOR, 0);
  lv_obj_set_style_bg_opa(crosshair_h, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(crosshair_h, 0, 0);
  lv_obj_add_flag(crosshair_h, LV_OBJ_FLAG_HIDDEN);
  lv_obj_add_flag(crosshair_h, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(crosshair_h, LV_OBJ_FLAG_CLICKABLE);

  // Vertical crosshair line - 1px width, full screen height
  crosshair_v = lv_obj_create(td_screen);
  lv_obj_set_size(crosshair_v, 1, LV_VER_RES);
  lv_obj_set_style_bg_color(crosshair_v, CROSSHAIR_COLOR, 0);
  lv_obj_set_style_bg_opa(crosshair_v, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(crosshair_v, 0, 0);
  lv_obj_add_flag(crosshair_v, LV_OBJ_FLAG_HIDDEN);
  lv_obj_add_flag(crosshair_v, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(crosshair_v, LV_OBJ_FLAG_CLICKABLE);

  // Create 5 single-pixel reference dots at edges (matches original)
  int32_t screen_w = LV_HOR_RES;
  int32_t screen_h = LV_VER_RES;

  dot_top = create_reference_dot(td_screen, screen_w / 2, 2);
  dot_bottom = create_reference_dot(td_screen, screen_w / 2, screen_h - 3);
  dot_left = create_reference_dot(td_screen, 0, screen_h / 2);
  dot_right = create_reference_dot(td_screen, screen_w - 9, screen_h / 2);
  dot_center = create_reference_dot(td_screen, screen_w / 2, screen_h / 2);

  // Add touch event handlers to screen
  lv_obj_add_flag(td_screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(td_screen, robot_test_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(td_screen, robot_test_event_handler, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(td_screen, robot_test_event_handler, LV_EVENT_RELEASED, NULL);
}

static void robot_test_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  lv_indev_t* indev = lv_indev_get_act();
  if (indev == NULL) {
    return;
  }

  lv_point_t point;
  lv_indev_get_point(indev, &point);

  // Get timestamp and gesture direction for logging (matches original format)
  uint32_t touch_ts = lv_indev_get_data_timestamp(indev);
  lv_dir_t gesture_dir = lv_indev_get_gesture_dir(indev);

  switch (code) {
    case LV_EVENT_PRESSED:
    case LV_EVENT_PRESSING:
      // Show and update crosshairs
      if (crosshair_h != NULL) {
        lv_obj_set_pos(crosshair_h, 0, point.y);
        lv_obj_clear_flag(crosshair_h, LV_OBJ_FLAG_HIDDEN);
      }
      if (crosshair_v != NULL) {
        lv_obj_set_pos(crosshair_v, point.x, 0);
        lv_obj_clear_flag(crosshair_v, LV_OBJ_FLAG_HIDDEN);
      }

      // Show coordinates
      if (coord_label != NULL) {
        char buf[32];
        snprintf(buf, sizeof(buf), "x:%ld y:%ld", (long)point.x, (long)point.y);
        lv_label_set_text(coord_label, buf);
        lv_obj_clear_flag(coord_label, LV_OBJ_FLAG_HIDDEN);
      }

      // Log to UART (matches original format: X=, Y=, e=, g=, ts=)
      printf("X=%ld, Y=%ld, e=%d g=%d ts=%lu\r\n", (long)point.x, (long)point.y, (int)code,
             (int)gesture_dir, (unsigned long)touch_ts);
      break;

    case LV_EVENT_RELEASED:
      // Hide crosshairs
      if (crosshair_h != NULL) {
        lv_obj_add_flag(crosshair_h, LV_OBJ_FLAG_HIDDEN);
      }
      if (crosshair_v != NULL) {
        lv_obj_add_flag(crosshair_v, LV_OBJ_FLAG_HIDDEN);
      }
      // Hide coordinates
      if (coord_label != NULL) {
        lv_obj_add_flag(coord_label, LV_OBJ_FLAG_HIDDEN);
      }

      // Log release event
      printf("X=%ld, Y=%ld, e=%d g=%d ts=%lu\r\n", (long)point.x, (long)point.y, (int)code,
             (int)gesture_dir, (unsigned long)touch_ts);
      break;

    default:
      break;
  }
}

// =============================================================================
// Disable Touch
// =============================================================================

static void update_disable_touch_ui(void) {
  if (disable_status_label != NULL) {
    if (touch_host_suspended) {
      lv_label_set_text(disable_status_label, "I2C SUSPENDED");
      lv_obj_set_style_text_color(disable_status_label, lv_color_hex(0xFF0000), 0);
    } else {
      lv_label_set_text(disable_status_label, "I2C ACTIVE");
      lv_obj_set_style_text_color(disable_status_label, lv_color_hex(0x00FF00), 0);
    }
  }

  if (disable_btn != NULL) {
    lv_obj_t* btn_label = lv_obj_get_child(disable_btn, 0);
    if (btn_label != NULL) {
      if (touch_host_suspended) {
        lv_label_set_text(btn_label, "ENABLE");
        lv_obj_set_style_bg_color(disable_btn, lv_color_hex(0x006600), 0);
      } else {
        lv_label_set_text(btn_label, "DISABLE");
        lv_obj_set_style_bg_color(disable_btn, lv_color_hex(0x660000), 0);
      }
    }
  }
}

static void disable_touch_btn_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  touch_host_suspended = !touch_host_suspended;
  touch_set_host_i2c_suspended(touch_host_suspended);
  printf("Touch I2C %s\r\n", touch_host_suspended ? "SUSPENDED" : "ACTIVE");
  update_disable_touch_ui();
}

static void show_disable_touch(void) {
  td_clear_screen_content();
  td_current_mode = MODE_PLACEHOLDER;

  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Disable Touch");
  lv_obj_set_style_text_color(td_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_24, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 45);

  lv_obj_t* desc_label = lv_label_create(td_screen);
  lv_label_set_text(desc_label, "Stop ALL host I2C\nfor IDC-studio debug\n\nTouch won't work!");
  lv_obj_set_style_text_color(desc_label, lv_color_hex(0xAAAAAA), 0);
  lv_obj_set_style_text_font(desc_label, &cash_sans_mono_regular_20, 0);
  lv_obj_set_style_text_align(desc_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(desc_label, LV_ALIGN_TOP_MID, 0, 75);

  disable_status_label = lv_label_create(td_screen);
  lv_obj_set_style_text_font(disable_status_label, &cash_sans_mono_regular_28, 0);
  lv_obj_align(disable_status_label, LV_ALIGN_CENTER, 0, -20);

  disable_btn = lv_btn_create(td_screen);
  lv_obj_set_size(disable_btn, 160, 60);
  lv_obj_align(disable_btn, LV_ALIGN_CENTER, 0, 50);
  lv_obj_set_style_radius(disable_btn, 10, 0);
  lv_obj_set_style_border_width(disable_btn, 0, 0);

  lv_obj_t* btn_label = lv_label_create(disable_btn);
  lv_obj_set_style_text_color(btn_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(btn_label, &cash_sans_mono_regular_24, 0);
  lv_obj_center(btn_label);

  lv_obj_clear_flag(disable_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(disable_btn, disable_touch_btn_handler, LV_EVENT_CLICKED, NULL);

  lv_obj_t* warn_label = lv_label_create(td_screen);
  lv_label_set_text(warn_label, "Reset device to restore");
  lv_obj_set_style_text_color(warn_label, lv_color_hex(0xFF6600), 0);
  lv_obj_set_style_text_font(warn_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(warn_label, LV_ALIGN_BOTTOM_MID, 0, -40);

  update_disable_touch_ui();
}

// =============================================================================
// Tap Latency Test
// =============================================================================

static void tap_latency_reset_timer_cb(lv_timer_t* timer) {
  (void)timer;
  if (td_current_mode != MODE_TAP_LATENCY) {
    return;
  }
  if (tap_latency_is_white) {
    lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);
    tap_latency_is_white = false;
    printf("TapLatency: AUTO-RESET -> BLACK (20s timeout)\r\n");
  }
  if (tap_latency_reset_timer != NULL) {
    lv_timer_del(tap_latency_reset_timer);
    tap_latency_reset_timer = NULL;
  }
}

static void tap_latency_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  lv_indev_t* indev = lv_indev_get_act();
  if (indev == NULL) {
    return;
  }

  lv_point_t point;
  lv_indev_get_point(indev, &point);
  uint32_t touch_ts = lv_tick_get();

  switch (code) {
    case LV_EVENT_PRESSED:
      if (tap_latency_is_white) {
        lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);
        tap_latency_is_white = false;
        if (tap_latency_reset_timer != NULL) {
          lv_timer_del(tap_latency_reset_timer);
          tap_latency_reset_timer = NULL;
        }
        printf("TapLatency: PRESSED  -> BLACK X=%ld Y=%ld ts=%lu\r\n", (long)point.x, (long)point.y,
               (unsigned long)touch_ts);
      } else {
        printf("TapLatency: PRESSED  (already black) X=%ld Y=%ld ts=%lu\r\n", (long)point.x,
               (long)point.y, (unsigned long)touch_ts);
      }
      break;

    case LV_EVENT_PRESSING:
      printf("TapLatency: PRESSING X=%ld Y=%ld ts=%lu\r\n", (long)point.x, (long)point.y,
             (unsigned long)touch_ts);
      break;

    case LV_EVENT_RELEASED:
      lv_obj_set_style_bg_color(td_screen, lv_color_white(), 0);
      tap_latency_is_white = true;
      printf("TapLatency: RELEASED -> WHITE X=%ld Y=%ld ts=%lu\r\n", (long)point.x, (long)point.y,
             (unsigned long)touch_ts);
      if (tap_latency_reset_timer != NULL) {
        lv_timer_del(tap_latency_reset_timer);
      }
      tap_latency_reset_timer =
        lv_timer_create(tap_latency_reset_timer_cb, TAP_LATENCY_RESET_MS, NULL);
      lv_timer_set_repeat_count(tap_latency_reset_timer, 1);
      break;

    default:
      break;
  }
}

static void show_tap_latency(void) {
  td_clear_screen_content();
  td_current_mode = MODE_TAP_LATENCY;

  lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);
  lv_obj_set_style_pad_all(td_screen, 0, 0);
  tap_latency_is_white = false;
  tap_latency_reset_timer = NULL;

  lv_obj_t* hint_label = lv_label_create(td_screen);
  lv_label_set_text(hint_label, "Auto-resets after 20s");
  lv_obj_set_style_text_color(hint_label, lv_color_hex(0x333333), 0);
  lv_obj_set_style_text_font(hint_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(hint_label, LV_ALIGN_TOP_MID, 0, 40);

  lv_obj_add_flag(td_screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(td_screen, tap_latency_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(td_screen, tap_latency_event_handler, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(td_screen, tap_latency_event_handler, LV_EVENT_RELEASED, NULL);

  printf("Tap Latency test screen opened\r\n");
  printf("  - Tap: screen turns WHITE on release\r\n");
  printf("  - Tap again: screen turns BLACK on press\r\n");
  printf("  - Auto-resets to BLACK after 20s\r\n");
}

// =============================================================================
// Screen Lifecycle
// =============================================================================

lv_obj_t* screen_touch_debug_init(void* ctx) {
  (void)ctx;
  ASSERT(td_screen == NULL);

  td_screen = lv_obj_create(NULL);
  if (!td_screen) {
    return NULL;
  }
  lv_obj_set_style_bg_opa(td_screen, LV_OPA_COVER, 0);

  lv_obj_add_event_cb(td_screen, td_screen_delete_handler, LV_EVENT_DELETE, NULL);

  td_current_mode = MODE_MENU;
  td_show_menu();

  return td_screen;
}

void screen_touch_debug_update(void* ctx) {
  (void)ctx;
}

void screen_touch_debug_destroy(void) {
  // Re-enable touch I2C if it was suspended
  if (touch_host_suspended) {
    touch_host_suspended = false;
    touch_set_host_i2c_suspended(false);
    printf("Touch I2C re-enabled on screen destroy\r\n");
  }

  // Cleanup timers in all files
  td_viewers_cleanup_timers();
  td_diag_cleanup_timers();

  if (tap_latency_reset_timer != NULL) {
    lv_timer_del(tap_latency_reset_timer);
    tap_latency_reset_timer = NULL;
  }

  // Cleanup display test before deleting screen objects
  if (td_current_mode == MODE_DISPLAY_TEST) {
    td_diag_cleanup_mode();
  }

  if (td_screen != NULL) {
    lv_obj_del(td_screen);
    reset_screen_state();
  }
}

#else /* !MFGTEST */

/* Stubs for non-mfgtest builds — screen is never registered, but symbols must exist. */
lv_obj_t* screen_touch_debug_init(void* ctx) {
  (void)ctx;
  return NULL;
}
void screen_touch_debug_update(void* ctx) {
  (void)ctx;
}
void screen_touch_debug_destroy(void) {}

#endif /* MFGTEST */
