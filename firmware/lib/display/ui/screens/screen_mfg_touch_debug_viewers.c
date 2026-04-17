/**
 * @file screen_mfg_touch_debug_viewers.c
 * @brief Touch debug viewer modes: Touch Viewer V2, Cap Viewer, Self Cap, Calibrate, Power Mode.
 */

#include "screen_mfg_touch_debug_internal.h"

#ifdef MFGTEST

#include "lvgl/lvgl.h"
#include "printf.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

// ============================================================================
// Local defines
// ============================================================================
#define CROSSHAIR_COLOR     lv_color_hex(0xFF0000)
#define V2_TRAIL_BUFFER_MAX 500  // Maximum configurable buffer size

// ============================================================================
// Static variables — Touch Viewer V2
// ============================================================================
static lv_point_precise_t* v2_trail_points = NULL;  // Dynamically allocated on viewer entry
static int v2_trail_point_count = 0;
static lv_obj_t* v2_trail_line = NULL;
static lv_obj_t* v2_coord_label = NULL;
static lv_obj_t* v2_crosshair_h = NULL;
static lv_obj_t* v2_crosshair_v = NULL;
static lv_obj_t* v2_tap_dot = NULL;
static bool v2_is_touching = false;

// V2 runtime-configurable parameters (set via settings screen)
static int v2_buffer_size = 100;      // Effective buffer size (default 100)
static int v2_overflow_divisor = 50;  // Drop 1/N of buffer when full (default 1/50 = 2 points)
static int v2_decimation_dist = 2;    // Min pixels to move before adding point (default 2)

// V2 settings screen objects
static lv_obj_t* v2_settings_buffer_label = NULL;
static lv_obj_t* v2_settings_divisor_label = NULL;
static lv_obj_t* v2_settings_decim_label = NULL;

// ============================================================================
// Static variables — Cap Viewer (shared with Calibrate Test)
// ============================================================================
// Dynamically allocated when cap viewer or calibrate test is entered.
typedef struct {
  lv_obj_t* status_label;
  lv_obj_t* data_labels[TOUCH_MFGTEST_MCAP_ROWS][TOUCH_MFGTEST_MCAP_COLS];
  uint8_t data[TOUCH_MFGTEST_MCAP_SIZE];
  bool read_success;
} cap_viewer_state_t;
static cap_viewer_state_t* cap_state = NULL;
static lv_timer_t* cap_read_timer = NULL;

// ============================================================================
// Static variables — Calibrate Test
// ============================================================================
static lv_obj_t* calibrate_start_btn = NULL;
static lv_obj_t* calibrate_countdown_label = NULL;
static lv_obj_t* calibrate_restart_btn = NULL;
static lv_timer_t* calibrate_countdown_timer = NULL;
static int calibrate_countdown_value = 3;
static bool calibrate_in_cap_view = false;

// ============================================================================
// Static variables — Self Cap Viewer
// ============================================================================
// Dynamically allocated when self-cap viewer is entered.
typedef struct {
  lv_obj_t* labels[4][TOUCH_MFGTEST_SCAP_COLS];
  lv_obj_t* row_labels[4];
  lv_obj_t* status_label;
  uint8_t waterproof_data[TOUCH_MFGTEST_SCAP_SIZE];
  uint8_t normal_data[TOUCH_MFGTEST_SCAP_SIZE];
  bool read_success;
} self_cap_state_t;
static self_cap_state_t* self_cap = NULL;
static lv_timer_t* self_cap_read_timer = NULL;

// ============================================================================
// Static variables — Power Mode
// ============================================================================
static lv_obj_t* power_mode_value_label = NULL;
static lv_obj_t* power_mode_elapsed_label = NULL;
static lv_timer_t* power_mode_timer = NULL;
static uint32_t power_mode_start_tick = 0;
static uint8_t power_mode_last_value = 0xFF;
static bool power_mode_i2c_suspended = false;

// ============================================================================
// Forward declarations
// ============================================================================
static void show_touch_viewer_v2(void);
static void touch_viewer_v2_event_handler(lv_event_t* e);
static void cap_read_and_display(void);
static void cap_read_timer_cb(lv_timer_t* timer);
static void self_cap_read_and_display(void);
static void self_cap_read_timer_cb(lv_timer_t* timer);
static void calibrate_start_btn_handler(lv_event_t* e);
static void calibrate_countdown_timer_cb(lv_timer_t* timer);
static void calibrate_restart_btn_handler(lv_event_t* e);
static void show_calibrate_cap_view(void);

// ============================================================================
// Touch Viewer v2 Settings Screen
// ============================================================================

// Button handlers for v2 settings adjustments
static void v2_settings_buffer_minus(lv_event_t* e) {
  (void)e;
  if (v2_buffer_size > 50) {
    v2_buffer_size -= 50;
    if (v2_buffer_size > V2_TRAIL_BUFFER_MAX)
      v2_buffer_size = V2_TRAIL_BUFFER_MAX;
    char buf[16];
    snprintf(buf, sizeof(buf), "%d", v2_buffer_size);
    if (v2_settings_buffer_label)
      lv_label_set_text(v2_settings_buffer_label, buf);
  }
}

static void v2_settings_buffer_plus(lv_event_t* e) {
  (void)e;
  if (v2_buffer_size < V2_TRAIL_BUFFER_MAX) {
    v2_buffer_size += 50;
    if (v2_buffer_size > V2_TRAIL_BUFFER_MAX)
      v2_buffer_size = V2_TRAIL_BUFFER_MAX;
    char buf[16];
    snprintf(buf, sizeof(buf), "%d", v2_buffer_size);
    if (v2_settings_buffer_label)
      lv_label_set_text(v2_settings_buffer_label, buf);
  }
}

static void v2_settings_divisor_minus(lv_event_t* e) {
  (void)e;
  if (v2_overflow_divisor > 2) {
    v2_overflow_divisor -= 2;
    char buf[16];
    snprintf(buf, sizeof(buf), "1/%d", v2_overflow_divisor);
    if (v2_settings_divisor_label)
      lv_label_set_text(v2_settings_divisor_label, buf);
  }
}

static void v2_settings_divisor_plus(lv_event_t* e) {
  (void)e;
  if (v2_overflow_divisor < 50) {
    v2_overflow_divisor += 2;
    char buf[16];
    snprintf(buf, sizeof(buf), "1/%d", v2_overflow_divisor);
    if (v2_settings_divisor_label)
      lv_label_set_text(v2_settings_divisor_label, buf);
  }
}

static void v2_settings_decim_minus(lv_event_t* e) {
  (void)e;
  if (v2_decimation_dist > 0) {
    v2_decimation_dist--;
    char buf[16];
    snprintf(buf, sizeof(buf), "%dpx", v2_decimation_dist);
    if (v2_settings_decim_label)
      lv_label_set_text(v2_settings_decim_label, buf);
  }
}

static void v2_settings_decim_plus(lv_event_t* e) {
  (void)e;
  if (v2_decimation_dist < 20) {
    v2_decimation_dist++;
    char buf[16];
    snprintf(buf, sizeof(buf), "%dpx", v2_decimation_dist);
    if (v2_settings_decim_label)
      lv_label_set_text(v2_settings_decim_label, buf);
  }
}

static void v2_settings_start_btn_handler(lv_event_t* e) {
  (void)e;
  // Proceed to the actual touch viewer v2
  show_touch_viewer_v2();
}

// Helper to create a row with label, value, and +/- buttons
static void create_settings_row(lv_obj_t* parent, int32_t y, const char* label_text,
                                const char* initial_value, lv_obj_t** value_label_out,
                                lv_event_cb_t minus_cb, lv_event_cb_t plus_cb) {
  int32_t row_x = 60;
  int32_t btn_size = 40;
  int32_t value_width = 80;

  // Label
  lv_obj_t* label = lv_label_create(parent);
  lv_label_set_text(label, label_text);
  lv_obj_set_style_text_color(label, lv_color_hex(0xAAAAAA), 0);
  lv_obj_set_style_text_font(label, &cash_sans_mono_regular_20, 0);
  lv_obj_set_pos(label, row_x, y + 8);

  // Minus button
  lv_obj_t* minus_btn = lv_btn_create(parent);
  lv_obj_set_size(minus_btn, btn_size, btn_size);
  lv_obj_set_pos(minus_btn, row_x + 90, y);
  lv_obj_set_style_bg_color(minus_btn, lv_color_hex(0x404040), 0);
  lv_obj_set_style_bg_color(minus_btn, lv_color_hex(0x606060), LV_STATE_PRESSED);
  lv_obj_set_style_radius(minus_btn, 6, 0);
  lv_obj_set_style_border_width(minus_btn, 0, 0);
  lv_obj_t* minus_label = lv_label_create(minus_btn);
  lv_label_set_text(minus_label, "-");
  lv_obj_set_style_text_color(minus_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(minus_label, &cash_sans_mono_regular_24, 0);
  lv_obj_center(minus_label);
  lv_obj_clear_flag(minus_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(minus_btn, minus_cb, LV_EVENT_CLICKED, NULL);

  // Value label
  lv_obj_t* value_lbl = lv_label_create(parent);
  lv_label_set_text(value_lbl, initial_value);
  lv_obj_set_style_text_color(value_lbl, lv_color_hex(0x00FFFF), 0);
  lv_obj_set_style_text_font(value_lbl, &cash_sans_mono_regular_20, 0);
  lv_obj_set_pos(value_lbl, row_x + 90 + btn_size + 10, y + 8);
  lv_obj_set_size(value_lbl, value_width, 30);
  lv_obj_set_style_text_align(value_lbl, LV_TEXT_ALIGN_CENTER, 0);
  *value_label_out = value_lbl;

  // Plus button
  lv_obj_t* plus_btn = lv_btn_create(parent);
  lv_obj_set_size(plus_btn, btn_size, btn_size);
  lv_obj_set_pos(plus_btn, row_x + 90 + btn_size + value_width + 20, y);
  lv_obj_set_style_bg_color(plus_btn, lv_color_hex(0x404040), 0);
  lv_obj_set_style_bg_color(plus_btn, lv_color_hex(0x606060), LV_STATE_PRESSED);
  lv_obj_set_style_radius(plus_btn, 6, 0);
  lv_obj_set_style_border_width(plus_btn, 0, 0);
  lv_obj_t* plus_label = lv_label_create(plus_btn);
  lv_label_set_text(plus_label, "+");
  lv_obj_set_style_text_color(plus_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(plus_label, &cash_sans_mono_regular_24, 0);
  lv_obj_center(plus_label);
  lv_obj_clear_flag(plus_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(plus_btn, plus_cb, LV_EVENT_CLICKED, NULL);
}

void td_show_touch_viewer_v2_settings(void) {
  td_clear_screen_content();
  td_current_mode = MODE_TOUCH_VIEWER_V2_SETTINGS;

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Touch Viewer v2");
  lv_obj_set_style_text_color(td_title_label, lv_color_hex(0x00FF00), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 40);

  // Subtitle
  lv_obj_t* subtitle = lv_label_create(td_screen);
  lv_label_set_text(subtitle, "Configure Parameters");
  lv_obj_set_style_text_color(subtitle, lv_color_hex(0x888888), 0);
  lv_obj_set_style_text_font(subtitle, &cash_sans_mono_regular_20, 0);
  lv_obj_align(subtitle, LV_ALIGN_TOP_MID, 0, 65);

  // Settings rows
  int32_t row_start_y = 100;
  int32_t row_spacing = 55;

  // Buffer size row
  char buf_val[16];
  snprintf(buf_val, sizeof(buf_val), "%d", v2_buffer_size);
  create_settings_row(td_screen, row_start_y, "Buffer:", buf_val, &v2_settings_buffer_label,
                      v2_settings_buffer_minus, v2_settings_buffer_plus);

  // Divisor row
  char div_val[16];
  snprintf(div_val, sizeof(div_val), "1/%d", v2_overflow_divisor);
  create_settings_row(td_screen, row_start_y + row_spacing, "Drop:", div_val,
                      &v2_settings_divisor_label, v2_settings_divisor_minus,
                      v2_settings_divisor_plus);

  // Decimation row
  char dec_val[16];
  snprintf(dec_val, sizeof(dec_val), "%dpx", v2_decimation_dist);
  create_settings_row(td_screen, row_start_y + row_spacing * 2, "Decim:", dec_val,
                      &v2_settings_decim_label, v2_settings_decim_minus, v2_settings_decim_plus);

  // Start button
  lv_obj_t* start_btn = lv_btn_create(td_screen);
  lv_obj_set_size(start_btn, 140, 50);
  lv_obj_align(start_btn, LV_ALIGN_BOTTOM_MID, 0, -50);
  lv_obj_set_style_bg_color(start_btn, lv_color_hex(0x006600), 0);
  lv_obj_set_style_bg_color(start_btn, lv_color_hex(0x008800), LV_STATE_PRESSED);
  lv_obj_set_style_radius(start_btn, 8, 0);
  lv_obj_set_style_border_width(start_btn, 0, 0);

  lv_obj_t* start_label = lv_label_create(start_btn);
  lv_label_set_text(start_label, "START");
  lv_obj_set_style_text_color(start_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(start_label, &cash_sans_mono_regular_24, 0);
  lv_obj_center(start_label);

  lv_obj_clear_flag(start_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(start_btn, v2_settings_start_btn_handler, LV_EVENT_CLICKED, NULL);

  printf("Touch Viewer v2 Settings: buffer=%d, divisor=1/%d, decimation=%dpx\r\n", v2_buffer_size,
         v2_overflow_divisor, v2_decimation_dist);
}

// ============================================================================
// Touch Viewer v2 Implementation (optimized with small buffer)
// ============================================================================

// Touch Viewer v2 event handler - uses small 50-point buffer for performance
static void touch_viewer_v2_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  lv_indev_t* indev = lv_indev_get_act();
  if (indev == NULL) {
    return;
  }

  lv_point_t point;
  lv_indev_get_point(indev, &point);

  // Get timestamp and gesture direction for logging
  uint32_t touch_ts = lv_indev_get_data_timestamp(indev);
  lv_dir_t gesture_dir = lv_indev_get_gesture_dir(indev);

  switch (code) {
    case LV_EVENT_PRESSED:
      // New touch - clear buffer (but keep any existing drawn lines on screen)
      v2_trail_point_count = 0;
      v2_is_touching = true;

      // Hide previous tap dot
      if (v2_tap_dot != NULL) {
        lv_obj_add_flag(v2_tap_dot, LV_OBJ_FLAG_HIDDEN);
      }

      // Hide trail line (will show when we have 2+ points)
      if (v2_trail_line != NULL) {
        lv_obj_add_flag(v2_trail_line, LV_OBJ_FLAG_HIDDEN);
      }

      // Update crosshairs position
      if (v2_crosshair_h != NULL) {
        lv_obj_set_pos(v2_crosshair_h, 0, point.y);
        lv_obj_clear_flag(v2_crosshair_h, LV_OBJ_FLAG_HIDDEN);
      }
      if (v2_crosshair_v != NULL) {
        lv_obj_set_pos(v2_crosshair_v, point.x, 0);
        lv_obj_clear_flag(v2_crosshair_v, LV_OBJ_FLAG_HIDDEN);
      }

      // Add first point
      if (v2_trail_points != NULL && v2_trail_point_count < v2_buffer_size) {
        v2_trail_points[v2_trail_point_count].x = point.x;
        v2_trail_points[v2_trail_point_count].y = point.y;
        v2_trail_point_count++;
      }

      // Update coordinates
      if (v2_coord_label != NULL) {
        char buf[32];
        snprintf(buf, sizeof(buf), "X:%3ld Y:%3ld", (long)point.x, (long)point.y);
        lv_label_set_text(v2_coord_label, buf);
      }

      // Log to UART
      printf("X=%ld, Y=%ld, e=%d g=%d ts=%lu\r\n", (long)point.x, (long)point.y, (int)code,
             (int)gesture_dir, (unsigned long)touch_ts);
      break;

    case LV_EVENT_PRESSING:
      // Update crosshairs position
      if (v2_crosshair_h != NULL) {
        lv_obj_set_pos(v2_crosshair_h, 0, point.y);
      }
      if (v2_crosshair_v != NULL) {
        lv_obj_set_pos(v2_crosshair_v, point.x, 0);
      }

      // Decimation: Only add point if moved >= v2_decimation_dist pixels
      if (v2_trail_point_count > 0 && v2_decimation_dist > 0) {
        int32_t last_x = (int32_t)v2_trail_points[v2_trail_point_count - 1].x;
        int32_t last_y = (int32_t)v2_trail_points[v2_trail_point_count - 1].y;
        int32_t dx = point.x - last_x;
        int32_t dy = point.y - last_y;
        if (dx < 0)
          dx = -dx;  // abs
        if (dy < 0)
          dy = -dy;  // abs

        // Skip if moved less than decimation distance
        if (dx < v2_decimation_dist && dy < v2_decimation_dist) {
          // Still update coordinates display and log
          if (v2_coord_label != NULL) {
            char buf[32];
            snprintf(buf, sizeof(buf), "X:%3ld Y:%3ld", (long)point.x, (long)point.y);
            lv_label_set_text(v2_coord_label, buf);
          }
          printf("X=%ld, Y=%ld, e=%d g=%d ts=%lu\r\n", (long)point.x, (long)point.y, (int)code,
                 (int)gesture_dir, (unsigned long)touch_ts);
          break;  // Skip adding this point to buffer
        }
      }

      // If buffer is full, shift to drop oldest points based on divisor
      if (v2_trail_point_count >= v2_buffer_size) {
        int shift_amount = v2_buffer_size / v2_overflow_divisor;
        if (shift_amount < 1)
          shift_amount = 1;  // Always drop at least 1
        memmove(v2_trail_points, &v2_trail_points[shift_amount],
                (v2_buffer_size - shift_amount) * sizeof(lv_point_precise_t));
        v2_trail_point_count = v2_buffer_size - shift_amount;
      }

      // Add new point
      v2_trail_points[v2_trail_point_count].x = point.x;
      v2_trail_points[v2_trail_point_count].y = point.y;
      v2_trail_point_count++;

      // Update trail line if we have 2+ points
      if (v2_trail_line != NULL && v2_trail_point_count >= 2) {
        lv_line_set_points(v2_trail_line, v2_trail_points, v2_trail_point_count);
        lv_obj_clear_flag(v2_trail_line, LV_OBJ_FLAG_HIDDEN);
      }

      // Update coordinates
      if (v2_coord_label != NULL) {
        char buf[32];
        snprintf(buf, sizeof(buf), "X:%3ld Y:%3ld", (long)point.x, (long)point.y);
        lv_label_set_text(v2_coord_label, buf);
      }

      // Log to UART
      printf("X=%ld, Y=%ld, e=%d g=%d ts=%lu\r\n", (long)point.x, (long)point.y, (int)code,
             (int)gesture_dir, (unsigned long)touch_ts);
      break;

    case LV_EVENT_RELEASED:
      v2_is_touching = false;

      // Hide crosshairs when touch released
      if (v2_crosshair_h != NULL) {
        lv_obj_add_flag(v2_crosshair_h, LV_OBJ_FLAG_HIDDEN);
      }
      if (v2_crosshair_v != NULL) {
        lv_obj_add_flag(v2_crosshair_v, LV_OBJ_FLAG_HIDDEN);
      }

      // Show tap dot at release position
      if (v2_tap_dot != NULL) {
        lv_obj_set_pos(v2_tap_dot, point.x - TAP_DOT_RADIUS, point.y - TAP_DOT_RADIUS);
        lv_obj_clear_flag(v2_tap_dot, LV_OBJ_FLAG_HIDDEN);
      }

      // Update coordinates
      if (v2_coord_label != NULL) {
        char buf[32];
        snprintf(buf, sizeof(buf), "X:%3ld Y:%3ld", (long)point.x, (long)point.y);
        lv_label_set_text(v2_coord_label, buf);
      }

      // Log to UART
      printf("X=%ld, Y=%ld, e=%d g=%d ts=%lu\r\n", (long)point.x, (long)point.y, (int)code,
             (int)gesture_dir, (unsigned long)touch_ts);
      break;

    default:
      break;
  }
}

static void show_touch_viewer_v2(void) {
  td_clear_screen_content();
  td_current_mode = MODE_TOUCH_VIEWER_V2;

  // Allocate trail buffer (freed in td_viewers_reset_state)
  v2_trail_points = lv_malloc(v2_buffer_size * sizeof(lv_point_precise_t));
  if (!v2_trail_points) {
    printf("Touch Viewer v2: failed to allocate trail buffer\r\n");
    td_show_menu();
    return;
  }

  // Black background
  lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);
  lv_obj_set_style_pad_all(td_screen, 0, 0);

  // Title - indicate this is v2
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Touch Viewer v2");
  lv_obj_set_style_text_color(td_title_label, lv_color_hex(0x00FF00), 0);  // Green to distinguish
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 35);

  // Info label showing current parameters
  lv_obj_t* info_label = lv_label_create(td_screen);
  char info_buf[48];
  snprintf(info_buf, sizeof(info_buf), "buf:%d, drop:1/%d, dec:%d", v2_buffer_size,
           v2_overflow_divisor, v2_decimation_dist);
  lv_label_set_text(info_label, info_buf);
  lv_obj_set_style_text_color(info_label, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(info_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(info_label, LV_ALIGN_TOP_MID, 0, 58);

  // Log current parameters to UART
  printf("Touch Viewer v2: buffer=%d, divisor=%d (drop %d), decimation=%dpx\r\n", v2_buffer_size,
         v2_overflow_divisor, v2_buffer_size / v2_overflow_divisor, v2_decimation_dist);

  // Coordinate label
  v2_coord_label = lv_label_create(td_screen);
  lv_label_set_text(v2_coord_label, "X:--- Y:---");
  lv_obj_set_style_text_color(v2_coord_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(v2_coord_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(v2_coord_label, LV_ALIGN_BOTTOM_MID, 0, -10);

  // Trail line (will be drawn as points are added)
  v2_trail_line = lv_line_create(td_screen);
  lv_obj_set_style_line_color(v2_trail_line, TRAIL_COLOR, 0);
  lv_obj_set_style_line_width(v2_trail_line, 2, 0);
  lv_obj_set_style_line_rounded(v2_trail_line, true, 0);
  lv_obj_add_flag(v2_trail_line, LV_OBJ_FLAG_HIDDEN);
  lv_obj_clear_flag(v2_trail_line, LV_OBJ_FLAG_CLICKABLE);

  // Tap dot (shown on tap, persists until next touch)
  v2_tap_dot = lv_obj_create(td_screen);
  lv_obj_set_size(v2_tap_dot, TAP_DOT_RADIUS * 2, TAP_DOT_RADIUS * 2);
  lv_obj_set_style_bg_color(v2_tap_dot, TAP_DOT_COLOR, 0);
  lv_obj_set_style_bg_opa(v2_tap_dot, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(v2_tap_dot, 0, 0);
  lv_obj_set_style_radius(v2_tap_dot, LV_RADIUS_CIRCLE, 0);
  lv_obj_add_flag(v2_tap_dot, LV_OBJ_FLAG_HIDDEN);
  lv_obj_clear_flag(v2_tap_dot, LV_OBJ_FLAG_CLICKABLE);

  // Horizontal crosshair line (red, only visible when touching)
  v2_crosshair_h = lv_obj_create(td_screen);
  lv_obj_set_size(v2_crosshair_h, LV_HOR_RES, 1);
  lv_obj_set_style_bg_color(v2_crosshair_h, CROSSHAIR_COLOR, 0);
  lv_obj_set_style_bg_opa(v2_crosshair_h, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(v2_crosshair_h, 0, 0);
  lv_obj_add_flag(v2_crosshair_h, LV_OBJ_FLAG_HIDDEN);
  lv_obj_add_flag(v2_crosshair_h, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(v2_crosshair_h, LV_OBJ_FLAG_CLICKABLE);

  // Vertical crosshair line (red, only visible when touching)
  v2_crosshair_v = lv_obj_create(td_screen);
  lv_obj_set_size(v2_crosshair_v, 1, LV_VER_RES);
  lv_obj_set_style_bg_color(v2_crosshair_v, CROSSHAIR_COLOR, 0);
  lv_obj_set_style_bg_opa(v2_crosshair_v, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(v2_crosshair_v, 0, 0);
  lv_obj_add_flag(v2_crosshair_v, LV_OBJ_FLAG_HIDDEN);
  lv_obj_add_flag(v2_crosshair_v, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(v2_crosshair_v, LV_OBJ_FLAG_CLICKABLE);

  // Reset trail
  v2_trail_point_count = 0;
  v2_is_touching = false;

  // Add touch event handlers to screen
  lv_obj_add_flag(td_screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(td_screen, touch_viewer_v2_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(td_screen, touch_viewer_v2_event_handler, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(td_screen, touch_viewer_v2_event_handler, LV_EVENT_RELEASED, NULL);
}

// Read capacitance data from touch IC and update display
static void cap_read_and_display(void) {
  // Clear buffer
  memset(cap_state->data, 0, sizeof(cap_state->data));

  // Read mutual capacitance data from register 0x40
  // The data is 128 bytes (8 TX x 8 RX x 2 bytes per value)
  cap_state->read_success =
    touch_mfgtest_read_buf(TOUCH_MFGTEST_MCAP_DIFF_REG, cap_state->data, TOUCH_MFGTEST_MCAP_SIZE);

  // Update status label
  if (cap_state->status_label != NULL) {
    if (cap_state->read_success) {
      lv_label_set_text(cap_state->status_label, "Read OK - Mutual Cap (0x40)");
      lv_obj_set_style_text_color(cap_state->status_label, lv_color_hex(0x00FF00), 0);
    } else {
      lv_label_set_text(cap_state->status_label, "Read FAILED");
      lv_obj_set_style_text_color(cap_state->status_label, lv_color_hex(0xFF0000), 0);
    }
  }

  // Update data labels
  if (cap_state->read_success) {
    for (int row = 0; row < TOUCH_MFGTEST_MCAP_ROWS; row++) {
      for (int col = 0; col < TOUCH_MFGTEST_MCAP_COLS; col++) {
        if (cap_state->data_labels[row][col] != NULL) {
          // Each value is 2 bytes BIG-ENDIAN, SIGNED (per FocalTech reference code)
          // Format: (short)((data[i] << 8) + data[i + 1])
          int idx = (row * TOUCH_MFGTEST_MCAP_COLS + col) * 2;
          int16_t value = (int16_t)((cap_state->data[idx] << 8) | cap_state->data[idx + 1]);
          char buf[8];
          // Format value - truncate with "k" if >= 1000 or <= -1000
          if (value >= 1000) {
            snprintf(buf, sizeof(buf), "%d.%dk", value / 1000, (value % 1000) / 100);
          } else if (value <= -1000) {
            int abs_val = -value;
            snprintf(buf, sizeof(buf), "-%d.%dk", abs_val / 1000, (abs_val % 1000) / 100);
          } else {
            snprintf(buf, sizeof(buf), "%4d", (int)value);
          }
          lv_label_set_text(cap_state->data_labels[row][col], buf);

          // Color code based on thresholds:
          // Bright red: <= -1000 (truncated)
          // Dark red: < -100 and > -1000
          // Yellow: -100 to +100
          // Dark green: > 100 and < 1000
          // Bright green: >= 1000 (truncated)
          if (value <= -1000) {
            lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0xFF6666),
                                        0);  // Bright red
          } else if (value < -100) {
            lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0x993333),
                                        0);  // Dark red
          } else if (value >= 1000) {
            lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0x00FF00),
                                        0);  // Bright green
          } else if (value > 100) {
            lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0x009900),
                                        0);  // Dark green
          } else {
            lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0xFFFF00),
                                        0);  // Yellow
          }
        }
      }
    }
  }

  // Log to UART
  printf("Cap read %s\r\n", cap_state->read_success ? "OK" : "FAILED");
  if (cap_state->read_success) {
    printf("Mutual Cap Diff Data (8x8) - Signed Big-Endian:\r\n");
    for (int row = 0; row < TOUCH_MFGTEST_MCAP_ROWS; row++) {
      printf("TX%d: ", row + 1);
      for (int col = 0; col < TOUCH_MFGTEST_MCAP_COLS; col++) {
        int idx = (row * TOUCH_MFGTEST_MCAP_COLS + col) * 2;
        int16_t value = (int16_t)((cap_state->data[idx] << 8) | cap_state->data[idx + 1]);
        printf("%6d ", (int)value);
      }
      printf("\r\n");
    }
  }
}

// Timer callback for periodic capacitance reads
static void cap_read_timer_cb(lv_timer_t* timer) {
  (void)timer;
  if (td_current_mode == MODE_CAP_VIEWER) {
    cap_read_and_display();
  }
}

void td_show_cap_viewer(void) {
  td_clear_screen_content();
  td_current_mode = MODE_CAP_VIEWER;
  touch_set_fwup_in_progress(true);  // Pause ESD checks during cap reads

  cap_state = lv_malloc(sizeof(cap_viewer_state_t));
  if (!cap_state) {
    printf("Cap Viewer: alloc failed\r\n");
    td_show_menu();
    return;
  }
  memset(cap_state, 0, sizeof(cap_viewer_state_t));

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // For circular display (~240x400), center content to avoid cut-off corners
  // Title near top center (safe zone on circular display)
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Cap 0x40");
  lv_obj_set_style_text_color(td_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 35);

  // Status label
  cap_state->status_label = lv_label_create(td_screen);
  lv_label_set_text(cap_state->status_label, "Reading...");
  lv_obj_set_style_text_color(cap_state->status_label, lv_color_hex(0xFFFF00), 0);
  lv_obj_set_style_text_font(cap_state->status_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(cap_state->status_label, LV_ALIGN_TOP_MID, 0, 68);

  // Create 8x8 grid centered on circular display
  // Cell width increased by 1.25x (40 * 1.25 = 50)
  // Grid shifted left by 15 pixels
  int32_t cell_w = 50;  // 40 * 1.25 = 50
  int32_t cell_h = 26;
  int32_t start_x = (LV_HOR_RES - (8 * cell_w)) / 2 - 15;  // Shift left 15px
  int32_t start_y = 115;                                   // Below title/status

  // Data cells - centered grid
  for (int row = 0; row < TOUCH_MFGTEST_MCAP_ROWS; row++) {
    for (int col = 0; col < TOUCH_MFGTEST_MCAP_COLS; col++) {
      cap_state->data_labels[row][col] = lv_label_create(td_screen);
      lv_label_set_text(cap_state->data_labels[row][col], "---");
      lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0x00FFFF), 0);
      lv_obj_set_style_text_font(cap_state->data_labels[row][col], &cash_sans_mono_regular_20, 0);
      lv_obj_set_pos(cap_state->data_labels[row][col], start_x + col * cell_w,
                     start_y + row * cell_h);
    }
  }

  // Info label at bottom (in safe zone for circular display)
  lv_obj_t* info_label = lv_label_create(td_screen);
  lv_label_set_text(info_label, "10s refresh");
  lv_obj_set_style_text_color(info_label, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(info_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(info_label, LV_ALIGN_BOTTOM_MID, 0, -35);

  // Do initial read
  cap_read_and_display();

  // Start timer for periodic reads (every 10 seconds)
  cap_read_timer = lv_timer_create(cap_read_timer_cb, CAP_READ_INTERVAL_MS, NULL);
}

// ============================================================================
// Calibrate Test Implementation
// ============================================================================

// Timer callback for calibrate test cap reads (reuses cap_read_and_display)
static void calibrate_cap_timer_cb(lv_timer_t* timer) {
  (void)timer;
  if (td_current_mode == MODE_CALIBRATE_TEST && calibrate_in_cap_view) {
    cap_read_and_display();
  }
}

// Show the cap viewer portion of calibrate test (after calibration completes)
static void show_calibrate_cap_view(void) {
  // Clear current content but keep screen
  lv_obj_clean(td_screen);

  // Allocate cap state for the cap view phase
  if (!cap_state) {
    cap_state = lv_malloc(sizeof(cap_viewer_state_t));
    if (!cap_state) {
      printf("Calibrate cap view: alloc failed\r\n");
      td_show_menu();
      return;
    }
    memset(cap_state, 0, sizeof(cap_viewer_state_t));
  }

  // Reset pointers that were cleared
  td_title_label = NULL;
  calibrate_start_btn = NULL;
  calibrate_countdown_label = NULL;
  cap_state->status_label = NULL;
  for (int r = 0; r < TOUCH_MFGTEST_MCAP_ROWS; r++) {
    for (int c = 0; c < TOUCH_MFGTEST_MCAP_COLS; c++) {
      cap_state->data_labels[r][c] = NULL;
    }
  }

  calibrate_in_cap_view = true;

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title - indicate this is post-calibration
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Calibrated");
  lv_obj_set_style_text_color(td_title_label, lv_color_hex(0x00FF00), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 35);

  // Status label
  cap_state->status_label = lv_label_create(td_screen);
  lv_label_set_text(cap_state->status_label, "Reading...");
  lv_obj_set_style_text_color(cap_state->status_label, lv_color_hex(0xFFFF00), 0);
  lv_obj_set_style_text_font(cap_state->status_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(cap_state->status_label, LV_ALIGN_TOP_MID, 0, 68);

  // Create 8x8 grid - same layout as Cap Viewer
  int32_t cell_w = 50;
  int32_t cell_h = 26;
  int32_t start_x = (LV_HOR_RES - (8 * cell_w)) / 2 - 15;
  int32_t start_y = 115;

  for (int row = 0; row < TOUCH_MFGTEST_MCAP_ROWS; row++) {
    for (int col = 0; col < TOUCH_MFGTEST_MCAP_COLS; col++) {
      cap_state->data_labels[row][col] = lv_label_create(td_screen);
      lv_label_set_text(cap_state->data_labels[row][col], "---");
      lv_obj_set_style_text_color(cap_state->data_labels[row][col], lv_color_hex(0x00FFFF), 0);
      lv_obj_set_style_text_font(cap_state->data_labels[row][col], &cash_sans_mono_regular_20, 0);
      lv_obj_set_pos(cap_state->data_labels[row][col], start_x + col * cell_w,
                     start_y + row * cell_h);
    }
  }

  // Small restart button below the cap table
  calibrate_restart_btn = lv_btn_create(td_screen);
  lv_obj_set_size(calibrate_restart_btn, 100, 40);
  lv_obj_align(calibrate_restart_btn, LV_ALIGN_BOTTOM_MID, 0, -30);
  lv_obj_set_style_bg_color(calibrate_restart_btn, lv_color_hex(0x404040), 0);
  lv_obj_set_style_bg_color(calibrate_restart_btn, lv_color_hex(0x606060), LV_STATE_PRESSED);
  lv_obj_set_style_radius(calibrate_restart_btn, 6, 0);
  lv_obj_set_style_border_width(calibrate_restart_btn, 0, 0);

  lv_obj_t* restart_label = lv_label_create(calibrate_restart_btn);
  lv_label_set_text(restart_label, "Restart");
  lv_obj_set_style_text_color(restart_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(restart_label, &cash_sans_mono_regular_20, 0);
  lv_obj_center(restart_label);

  lv_obj_clear_flag(calibrate_restart_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(calibrate_restart_btn, calibrate_restart_btn_handler, LV_EVENT_CLICKED, NULL);

  // Do initial read
  cap_read_and_display();

  // Start timer for periodic reads (every 10 seconds)
  cap_read_timer = lv_timer_create(calibrate_cap_timer_cb, CAP_READ_INTERVAL_MS, NULL);

  printf("Calibrate Test: Now showing cap data (10s refresh)\r\n");
}

// Timer callback for countdown
static void calibrate_countdown_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (td_current_mode != MODE_CALIBRATE_TEST) {
    // Mode changed, stop timer
    if (calibrate_countdown_timer != NULL) {
      lv_timer_del(calibrate_countdown_timer);
      calibrate_countdown_timer = NULL;
    }
    return;
  }

  calibrate_countdown_value--;

  if (calibrate_countdown_value > 0) {
    // Update countdown display
    char buf[16];
    snprintf(buf, sizeof(buf), "%d...", calibrate_countdown_value);
    if (calibrate_countdown_label != NULL) {
      lv_label_set_text(calibrate_countdown_label, buf);
    }
    printf("Calibrate Test: %d...\r\n", calibrate_countdown_value);
  } else {
    // Countdown complete - stop timer
    if (calibrate_countdown_timer != NULL) {
      lv_timer_del(calibrate_countdown_timer);
      calibrate_countdown_timer = NULL;
    }

    // Update label to show calibrating
    if (calibrate_countdown_label != NULL) {
      lv_label_set_text(calibrate_countdown_label, "Calibrating...");
      lv_obj_set_style_text_color(calibrate_countdown_label, lv_color_hex(0xFFFF00), 0);
    }

    printf("Calibrate Test: Performing touch_hw_reset()...\r\n");

    // Perform hardware reset (calibration)
    touch_hw_reset();

    printf("Calibrate Test: Reset complete!\r\n");

    // Transition to cap viewer mode
    show_calibrate_cap_view();
  }
}

// Button handler for "Start Calibration" button
static void calibrate_start_btn_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  printf("Calibrate Test: Starting 3 second countdown...\r\n");

  // Hide the start button
  if (calibrate_start_btn != NULL) {
    lv_obj_add_flag(calibrate_start_btn, LV_OBJ_FLAG_HIDDEN);
  }

  // Show countdown label
  if (calibrate_countdown_label != NULL) {
    lv_label_set_text(calibrate_countdown_label, "3...");
    lv_obj_set_style_text_color(calibrate_countdown_label, lv_color_white(), 0);
    lv_obj_clear_flag(calibrate_countdown_label, LV_OBJ_FLAG_HIDDEN);
  }

  // Reset countdown value
  calibrate_countdown_value = 3;

  // Start countdown timer (fires every 1 second)
  calibrate_countdown_timer = lv_timer_create(calibrate_countdown_timer_cb, 1000, NULL);
}

// Button handler for "Restart" button (in cap view mode)
static void calibrate_restart_btn_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  printf("Calibrate Test: Restarting...\r\n");

  // Go back to initial calibrate test screen
  td_show_calibrate_test();
}

// Show the initial Calibrate Test screen with "Start Calibration" button
void td_show_calibrate_test(void) {
  td_clear_screen_content();
  td_current_mode = MODE_CALIBRATE_TEST;
  calibrate_in_cap_view = false;
  touch_set_fwup_in_progress(true);  // Pause ESD checks during calibration

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Calibrate Test");
  lv_obj_set_style_text_color(td_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_24, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 45);

  // Description
  lv_obj_t* desc_label = lv_label_create(td_screen);
  lv_label_set_text(desc_label, "Press button to start\n3 second countdown\nthen touch_hw_reset()");
  lv_obj_set_style_text_color(desc_label, lv_color_hex(0xAAAAAA), 0);
  lv_obj_set_style_text_font(desc_label, &cash_sans_mono_regular_20, 0);
  lv_obj_set_style_text_align(desc_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(desc_label, LV_ALIGN_TOP_MID, 0, 85);

  // Countdown label (hidden initially)
  calibrate_countdown_label = lv_label_create(td_screen);
  lv_label_set_text(calibrate_countdown_label, "");
  lv_obj_set_style_text_color(calibrate_countdown_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(calibrate_countdown_label, &cash_sans_mono_regular_48, 0);
  lv_obj_align(calibrate_countdown_label, LV_ALIGN_CENTER, 0, 0);
  lv_obj_add_flag(calibrate_countdown_label, LV_OBJ_FLAG_HIDDEN);

  // Start Calibration button
  calibrate_start_btn = lv_btn_create(td_screen);
  lv_obj_set_size(calibrate_start_btn, 225, 60);
  lv_obj_align(calibrate_start_btn, LV_ALIGN_CENTER, 0, 40);
  lv_obj_set_style_bg_color(calibrate_start_btn, lv_color_hex(0x006600), 0);
  lv_obj_set_style_bg_color(calibrate_start_btn, lv_color_hex(0x008800), LV_STATE_PRESSED);
  lv_obj_set_style_radius(calibrate_start_btn, 10, 0);
  lv_obj_set_style_border_width(calibrate_start_btn, 0, 0);

  lv_obj_t* btn_label = lv_label_create(calibrate_start_btn);
  lv_label_set_text(btn_label, "Start Calibration");
  lv_obj_set_style_text_color(btn_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(btn_label, &cash_sans_mono_regular_20, 0);
  lv_obj_center(btn_label);

  lv_obj_clear_flag(calibrate_start_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(calibrate_start_btn, calibrate_start_btn_handler, LV_EVENT_CLICKED, NULL);

  // Info label at bottom
  lv_obj_t* info_label = lv_label_create(td_screen);
  lv_label_set_text(info_label, "After reset, cap data shown");
  lv_obj_set_style_text_color(info_label, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(info_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(info_label, LV_ALIGN_BOTTOM_MID, 0, -65);
}

// ============================================================================
// Self Cap Viewer Implementation
// ============================================================================

// Helper to apply color coding to a self-cap value label (same thresholds as mutual cap)
static void apply_cap_color(lv_obj_t* label, int16_t value) {
  if (value <= -1000) {
    lv_obj_set_style_text_color(label, lv_color_hex(0xFF6666), 0);  // Bright red
  } else if (value < -100) {
    lv_obj_set_style_text_color(label, lv_color_hex(0x993333), 0);  // Dark red
  } else if (value >= 1000) {
    lv_obj_set_style_text_color(label, lv_color_hex(0x00FF00), 0);  // Bright green
  } else if (value > 100) {
    lv_obj_set_style_text_color(label, lv_color_hex(0x009900), 0);  // Dark green
  } else {
    lv_obj_set_style_text_color(label, lv_color_hex(0xFFFF00), 0);  // Yellow
  }
}

// Helper to format a cap value (same as mutual cap)
static void format_cap_value(int16_t value, char* buf, size_t buf_size) {
  if (value >= 1000) {
    snprintf(buf, buf_size, "%d.%dk", value / 1000, (value % 1000) / 100);
  } else if (value <= -1000) {
    int abs_val = -value;
    snprintf(buf, buf_size, "-%d.%dk", abs_val / 1000, (abs_val % 1000) / 100);
  } else {
    snprintf(buf, buf_size, "%4d", (int)value);
  }
}

// Read self-cap data and update display
static void self_cap_read_and_display(void) {
  // Clear buffers
  memset(self_cap->waterproof_data, 0, sizeof(self_cap->waterproof_data));
  memset(self_cap->normal_data, 0, sizeof(self_cap->normal_data));

  // Read waterproof self-cap data from register 0x42
  bool wp_success = touch_mfgtest_read_buf(TOUCH_MFGTEST_SCAP_WP_DIFF_REG,
                                           self_cap->waterproof_data, TOUCH_MFGTEST_SCAP_SIZE);

  // Read normal self-cap data from register 0x44
  bool norm_success = touch_mfgtest_read_buf(TOUCH_MFGTEST_SCAP_NORM_DIFF_REG,
                                             self_cap->normal_data, TOUCH_MFGTEST_SCAP_SIZE);

  self_cap->read_success = wp_success && norm_success;

  // Update status label
  if (self_cap->status_label != NULL) {
    if (self_cap->read_success) {
      lv_label_set_text(self_cap->status_label, "Read OK");
      lv_obj_set_style_text_color(self_cap->status_label, lv_color_hex(0x00FF00), 0);
    } else {
      char status[32];
      snprintf(status, sizeof(status), "FAIL: WP=%s N=%s", wp_success ? "OK" : "ERR",
               norm_success ? "OK" : "ERR");
      lv_label_set_text(self_cap->status_label, status);
      lv_obj_set_style_text_color(self_cap->status_label, lv_color_hex(0xFF0000), 0);
    }
  }

  // Update data labels
  // Row 0: Waterproof RX (first 8 values from 0x42)
  // Row 1: Waterproof TX (next 8 values from 0x42)
  // Row 2: Normal RX (first 8 values from 0x44)
  // Row 3: Normal TX (next 8 values from 0x44)

  for (int col = 0; col < TOUCH_MFGTEST_SCAP_COLS; col++) {
    char buf[8];

    // Waterproof RX (row 0)
    if (self_cap->labels[0][col] != NULL && wp_success) {
      int idx = col * 2;
      int16_t value =
        (int16_t)((self_cap->waterproof_data[idx] << 8) | self_cap->waterproof_data[idx + 1]);
      format_cap_value(value, buf, sizeof(buf));
      lv_label_set_text(self_cap->labels[0][col], buf);
      apply_cap_color(self_cap->labels[0][col], value);
    }

    // Waterproof TX (row 1)
    if (self_cap->labels[1][col] != NULL && wp_success) {
      int idx = (TOUCH_MFGTEST_SCAP_COLS + col) * 2;  // Second row of data
      int16_t value =
        (int16_t)((self_cap->waterproof_data[idx] << 8) | self_cap->waterproof_data[idx + 1]);
      format_cap_value(value, buf, sizeof(buf));
      lv_label_set_text(self_cap->labels[1][col], buf);
      apply_cap_color(self_cap->labels[1][col], value);
    }

    // Normal RX (row 2)
    if (self_cap->labels[2][col] != NULL && norm_success) {
      int idx = col * 2;
      int16_t value = (int16_t)((self_cap->normal_data[idx] << 8) | self_cap->normal_data[idx + 1]);
      format_cap_value(value, buf, sizeof(buf));
      lv_label_set_text(self_cap->labels[2][col], buf);
      apply_cap_color(self_cap->labels[2][col], value);
    }

    // Normal TX (row 3)
    if (self_cap->labels[3][col] != NULL && norm_success) {
      int idx = (TOUCH_MFGTEST_SCAP_COLS + col) * 2;  // Second row of data
      int16_t value = (int16_t)((self_cap->normal_data[idx] << 8) | self_cap->normal_data[idx + 1]);
      format_cap_value(value, buf, sizeof(buf));
      lv_label_set_text(self_cap->labels[3][col], buf);
      apply_cap_color(self_cap->labels[3][col], value);
    }
  }

  // Log to UART
  printf("Self-Cap read: WP=%s, Norm=%s\r\n", wp_success ? "OK" : "FAIL",
         norm_success ? "OK" : "FAIL");
  if (wp_success) {
    printf("Waterproof Self-Cap (0x42):\r\n");
    printf("  RX: ");
    for (int col = 0; col < TOUCH_MFGTEST_SCAP_COLS; col++) {
      int idx = col * 2;
      int16_t value =
        (int16_t)((self_cap->waterproof_data[idx] << 8) | self_cap->waterproof_data[idx + 1]);
      printf("%6d ", (int)value);
    }
    printf("\r\n  TX: ");
    for (int col = 0; col < TOUCH_MFGTEST_SCAP_COLS; col++) {
      int idx = (TOUCH_MFGTEST_SCAP_COLS + col) * 2;
      int16_t value =
        (int16_t)((self_cap->waterproof_data[idx] << 8) | self_cap->waterproof_data[idx + 1]);
      printf("%6d ", (int)value);
    }
    printf("\r\n");
  }
  if (norm_success) {
    printf("Normal Self-Cap (0x44):\r\n");
    printf("  RX: ");
    for (int col = 0; col < TOUCH_MFGTEST_SCAP_COLS; col++) {
      int idx = col * 2;
      int16_t value = (int16_t)((self_cap->normal_data[idx] << 8) | self_cap->normal_data[idx + 1]);
      printf("%6d ", (int)value);
    }
    printf("\r\n  TX: ");
    for (int col = 0; col < TOUCH_MFGTEST_SCAP_COLS; col++) {
      int idx = (TOUCH_MFGTEST_SCAP_COLS + col) * 2;
      int16_t value = (int16_t)((self_cap->normal_data[idx] << 8) | self_cap->normal_data[idx + 1]);
      printf("%6d ", (int)value);
    }
    printf("\r\n");
  }
}

// Timer callback for periodic self-cap reads
static void self_cap_read_timer_cb(lv_timer_t* timer) {
  (void)timer;
  if (td_current_mode == MODE_SELF_CAP_VIEWER) {
    self_cap_read_and_display();
  }
}

void td_show_self_cap_viewer(void) {
  td_clear_screen_content();
  td_current_mode = MODE_SELF_CAP_VIEWER;
  touch_set_fwup_in_progress(true);  // Pause ESD checks during self-cap reads

  self_cap = lv_malloc(sizeof(self_cap_state_t));
  if (!self_cap) {
    printf("Self Cap Viewer: alloc failed\r\n");
    td_show_menu();
    return;
  }
  memset(self_cap, 0, sizeof(self_cap_state_t));

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Self Cap");
  lv_obj_set_style_text_color(td_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 43);

  // Status label
  self_cap->status_label = lv_label_create(td_screen);
  lv_label_set_text(self_cap->status_label, "Reading...");
  lv_obj_set_style_text_color(self_cap->status_label, lv_color_hex(0xFFFF00), 0);
  lv_obj_set_style_text_font(self_cap->status_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(self_cap->status_label, LV_ALIGN_TOP_MID, 0, 66);

  // Layout for 4 rows of 8 values each - moved down 50px for circular display
  int32_t cell_w = 50;
  int32_t cell_h = 26;
  int32_t label_w = 30;  // Width for row labels (reduced from 55 to keep RX/TX visible)
  int32_t grid_width = 8 * cell_w + label_w;
  int32_t start_x = (LV_HOR_RES - grid_width) / 2 - 10;
  int32_t start_y = 158;     // Moved down for circular display
  int32_t section_gap = 30;  // Gap between waterproof and normal sections

  // Row labels
  const char* row_names[4] = {"RX", "TX", "RX", "TX"};

  for (int row = 0; row < 4; row++) {
    // Calculate Y position with gap between sections
    int32_t row_y = start_y + row * cell_h;
    if (row >= 2) {
      row_y += section_gap;  // Add gap before Normal section
    }

    // Row label
    self_cap->row_labels[row] = lv_label_create(td_screen);
    lv_label_set_text(self_cap->row_labels[row], row_names[row]);
    lv_obj_set_style_text_color(self_cap->row_labels[row], lv_color_hex(0x888888), 0);
    lv_obj_set_style_text_font(self_cap->row_labels[row], &cash_sans_mono_regular_20, 0);
    lv_obj_set_pos(self_cap->row_labels[row], start_x + 5, row_y);

    // Data cells
    for (int col = 0; col < TOUCH_MFGTEST_SCAP_COLS; col++) {
      self_cap->labels[row][col] = lv_label_create(td_screen);
      lv_label_set_text(self_cap->labels[row][col], "---");
      lv_obj_set_style_text_color(self_cap->labels[row][col], lv_color_hex(0x00FFFF), 0);
      lv_obj_set_style_text_font(self_cap->labels[row][col], &cash_sans_mono_regular_20, 0);
      lv_obj_set_pos(self_cap->labels[row][col], start_x + label_w + col * cell_w, row_y);
    }
  }

  // Section labels - centered
  lv_obj_t* wp_label = lv_label_create(td_screen);
  lv_label_set_text(wp_label, "Waterproof (0x42)");
  lv_obj_set_style_text_color(wp_label, lv_color_hex(0x6666FF), 0);
  lv_obj_set_style_text_font(wp_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(wp_label, LV_ALIGN_TOP_MID, 0, start_y - 25);  // Centered above waterproof data

  lv_obj_t* norm_label = lv_label_create(td_screen);
  lv_label_set_text(norm_label, "Normal (0x44)");
  lv_obj_set_style_text_color(norm_label, lv_color_hex(0x66FF66), 0);
  lv_obj_set_style_text_font(norm_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(norm_label, LV_ALIGN_TOP_MID, 0,
               start_y + 2 * cell_h + section_gap - 25);  // Centered above normal data

  // Info label at bottom
  lv_obj_t* info_label = lv_label_create(td_screen);
  lv_label_set_text(info_label, "10s refresh");
  lv_obj_set_style_text_color(info_label, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(info_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(info_label, LV_ALIGN_BOTTOM_MID, 0, -35);

  // Do initial read
  self_cap_read_and_display();

  // Start timer for periodic reads (every 10 seconds)
  self_cap_read_timer = lv_timer_create(self_cap_read_timer_cb, CAP_READ_INTERVAL_MS, NULL);
}

// ============================================================================
// Power Mode Viewer Implementation
// ============================================================================

// Timer callback for periodic power mode reads
static void power_mode_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (td_current_mode != MODE_POWER_MODE) {
    return;
  }

  // Read power mode register
  uint8_t power_mode = 0xFF;
  bool read_ok = touch_mfgtest_read_buf(TOUCH_MFGTEST_POWER_MODE_REG, &power_mode, 1);

  // Calculate elapsed time since screen opened
  uint32_t elapsed_ms = lv_tick_get() - power_mode_start_tick;
  uint32_t elapsed_sec = elapsed_ms / 1000;
  uint32_t elapsed_ms_frac = (elapsed_ms % 1000) / 100;  // Tenths of a second

  // Update value label
  if (power_mode_value_label != NULL) {
    char buf[48];
    if (read_ok) {
      const char* mode_name = "UNKNOWN";
      uint32_t color = 0xFFFFFF;

      switch (power_mode) {
        case TOUCH_MFGTEST_POWER_ACTIVE:
          mode_name = "ACTIVE";
          color = 0x00FF00;  // Green
          break;
        case TOUCH_MFGTEST_POWER_MONITOR:
          mode_name = "MONITOR";
          color = 0xFFFF00;  // Yellow
          break;
        case TOUCH_MFGTEST_POWER_SLEEP:
          mode_name = "SLEEP";
          color = 0xFF6600;  // Orange
          break;
        default:
          mode_name = "???";
          color = 0xFF0000;  // Red
          break;
      }

      snprintf(buf, sizeof(buf), "0x%02X = %s", power_mode, mode_name);
      lv_label_set_text(power_mode_value_label, buf);
      lv_obj_set_style_text_color(power_mode_value_label, lv_color_hex(color), 0);

      // Log if value changed
      if (power_mode != power_mode_last_value) {
        printf("Power Mode changed: 0x%02X -> 0x%02X (%s) at %lu.%lus\r\n", power_mode_last_value,
               power_mode, mode_name, (unsigned long)elapsed_sec, (unsigned long)elapsed_ms_frac);
        power_mode_last_value = power_mode;
      }
    } else {
      lv_label_set_text(power_mode_value_label, "READ ERROR");
      lv_obj_set_style_text_color(power_mode_value_label, lv_color_hex(0xFF0000), 0);
    }
  }

  // Update elapsed time label
  if (power_mode_elapsed_label != NULL) {
    char buf[32];
    snprintf(buf, sizeof(buf), "Elapsed: %lu.%lus", (unsigned long)elapsed_sec,
             (unsigned long)elapsed_ms_frac);
    lv_label_set_text(power_mode_elapsed_label, buf);
  }
}

// Show the Power Mode viewer screen
void td_show_power_mode(void) {
  td_clear_screen_content();
  td_current_mode = MODE_POWER_MODE;

  // Suspend host I2C polling (coordinate reads, ESD checks, etc.)
  // This isolates our 0xA5 reads as the ONLY I2C traffic
  touch_set_host_i2c_suspended(true);
  power_mode_i2c_suspended = true;
  printf("Power Mode: Host I2C suspended (only 0xA5 reads active)\r\n");

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Power Mode");
  lv_obj_set_style_text_color(td_title_label, lv_color_hex(0x00FFFF), 0);  // Cyan
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_24, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 45);

  // Register info - note that host I2C is suspended
  lv_obj_t* reg_label = lv_label_create(td_screen);
  lv_label_set_text(reg_label, "Reg 0xA5 (host suspended)");
  lv_obj_set_style_text_color(reg_label, lv_color_hex(0xFF6600),
                              0);  // Orange to indicate suspended
  lv_obj_set_style_text_font(reg_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(reg_label, LV_ALIGN_TOP_MID, 0, 78);

  // Power mode value (large, centered)
  power_mode_value_label = lv_label_create(td_screen);
  lv_label_set_text(power_mode_value_label, "Reading...");
  lv_obj_set_style_text_color(power_mode_value_label, lv_color_hex(0xFFFFFF), 0);
  lv_obj_set_style_text_font(power_mode_value_label, &cash_sans_mono_regular_28, 0);
  lv_obj_align(power_mode_value_label, LV_ALIGN_CENTER, 0, -20);

  // Elapsed time label
  power_mode_elapsed_label = lv_label_create(td_screen);
  lv_label_set_text(power_mode_elapsed_label, "Elapsed: 0.0s");
  lv_obj_set_style_text_color(power_mode_elapsed_label, lv_color_hex(0x00FFFF), 0);
  lv_obj_set_style_text_font(power_mode_elapsed_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(power_mode_elapsed_label, LV_ALIGN_CENTER, 0, 20);

  // Legend at bottom
  lv_obj_t* legend_label = lv_label_create(td_screen);
  lv_label_set_text(legend_label, "0x00=Active\n0x01=Monitor\n0x03=Sleep");
  lv_obj_set_style_text_color(legend_label, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(legend_label, &cash_sans_mono_regular_20, 0);
  lv_obj_set_style_text_align(legend_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(legend_label, LV_ALIGN_BOTTOM_MID, 0, -75);

  // Refresh rate info
  lv_obj_t* refresh_label = lv_label_create(td_screen);
  lv_label_set_text(refresh_label, "500ms refresh");
  lv_obj_set_style_text_color(refresh_label, lv_color_hex(0x666666), 0);
  lv_obj_set_style_text_font(refresh_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(refresh_label, LV_ALIGN_BOTTOM_MID, 0, -35);

  // Initialize state
  power_mode_start_tick = lv_tick_get();
  power_mode_last_value = 0xFF;

  // Do initial read immediately
  power_mode_timer_cb(NULL);

  // Start timer for periodic reads (every 500ms for responsive updates)
  power_mode_timer = lv_timer_create(power_mode_timer_cb, 500, NULL);

  printf("Power Mode viewer opened\r\n");
}

// ============================================================================
// Reset and cleanup functions
// ============================================================================

void td_viewers_reset_state(void) {
  // V2 viewer — free dynamically allocated trail buffer
  if (v2_trail_points != NULL) {
    lv_free(v2_trail_points);
    v2_trail_points = NULL;
  }
  v2_trail_point_count = 0;
  v2_trail_line = NULL;
  v2_coord_label = NULL;
  v2_crosshair_h = NULL;
  v2_crosshair_v = NULL;
  v2_tap_dot = NULL;
  v2_is_touching = false;
  v2_settings_buffer_label = NULL;
  v2_settings_divisor_label = NULL;
  v2_settings_decim_label = NULL;

  // Cap viewer — free dynamically allocated state
  if (cap_state != NULL) {
    lv_free(cap_state);
    cap_state = NULL;
  }

  // Calibrate test
  calibrate_start_btn = NULL;
  calibrate_countdown_label = NULL;
  calibrate_restart_btn = NULL;
  calibrate_countdown_value = 3;
  calibrate_in_cap_view = false;

  // Self-cap viewer — free dynamically allocated state
  if (self_cap != NULL) {
    lv_free(self_cap);
    self_cap = NULL;
  }

  // Power mode
  power_mode_value_label = NULL;
  power_mode_elapsed_label = NULL;
  power_mode_start_tick = 0;
  power_mode_last_value = 0xFF;
}

void td_viewers_cleanup_timers(void) {
  // Cap read timer (used by both Cap Viewer and Calibrate Test)
  if (cap_read_timer != NULL) {
    lv_timer_del(cap_read_timer);
    cap_read_timer = NULL;
  }

  // Self-cap read timer
  if (self_cap_read_timer != NULL) {
    lv_timer_del(self_cap_read_timer);
    self_cap_read_timer = NULL;
  }

  // Calibrate countdown timer
  if (calibrate_countdown_timer != NULL) {
    lv_timer_del(calibrate_countdown_timer);
    calibrate_countdown_timer = NULL;
  }

  // Power mode timer
  if (power_mode_timer != NULL) {
    lv_timer_del(power_mode_timer);
    power_mode_timer = NULL;
  }

  // Restore host I2C if we suspended it
  if (power_mode_i2c_suspended) {
    touch_set_host_i2c_suspended(false);
    power_mode_i2c_suspended = false;
  }
}

#endif /* MFGTEST */
