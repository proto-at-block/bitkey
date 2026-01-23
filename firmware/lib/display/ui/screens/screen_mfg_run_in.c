// Run-in test screen - multi-state test flow with hold ring confirmation

#include "screen_mfg_run_in.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "lvgl.h"
#include "ui.h"
#include "widgets/hold_ring.h"
#include "widgets/mfg_burnin_grid.h"
#include "widgets/mfg_starfield_fps.h"
#include "widgets/top_back.h"

#include <math.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define CHECK_BUTTON_SIZE          80  // Check button size (circular)
#define CHECK_BUTTON_BOTTOM_MARGIN 40  // Bottom margin for check button
#define PILL_BG_OPA                51  // Pill background opacity (20%)

// Run-in test screen layout configuration
#define START_SCREEN_BG_COLOR      lv_color_make(64, 64, 64)  // Dark gray
#define START_SCREEN_CENTER_TEXT_Y -20                        // Center text Y offset
#define START_SCREEN_BATTERY_Y     30                         // Battery status Y offset
#define START_SCREEN_COLOR_GREEN   lv_color_make(0, 255, 0)   // Charging color
#define START_SCREEN_COLOR_RED     lv_color_make(255, 0, 0)   // Not charging color

#define STATUS_BG_COLOR_YELLOW lv_color_make(200, 200, 0)  // In progress, no failures
#define STATUS_BG_COLOR_ORANGE lv_color_make(255, 140, 0)  // In progress, with failures
#define STATUS_BG_COLOR_GREEN  lv_color_make(0, 200, 0)    // Complete, passed
#define STATUS_BG_COLOR_RED    lv_color_make(200, 0, 0)    // Complete, failed
#define STATUS_TITLE_Y_OFFSET  10                          // Title Y offset from top

// Fonts
#define FONT_STANDARD  (&cash_sans_mono_regular_22)
#define FONT_COUNTDOWN (&cash_sans_mono_regular_36)

// External image declarations
extern const lv_img_dsc_t check;

// Screen state
static lv_obj_t* screen = NULL;
static fwpb_display_mfg_test_mode current_test_mode =
  fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;

// General UI elements
static lv_obj_t* status_label = NULL;

// Widgets
static lv_obj_t* check_button = NULL;
static hold_ring_t approve_ring = {0};
static top_back_t back_button = {0};
static top_back_t status_back_button = {0};
static mfg_starfield_fps_t fps_widget = {0};

// Helper function to format time as H:MM:SS
static void format_time(char* buf, size_t buf_size, uint32_t ms) {
  uint32_t total_sec = ms / 1000;
  uint32_t hours = total_sec / 3600;
  uint32_t minutes = (total_sec % 3600) / 60;
  uint32_t seconds = total_sec % 60;
  snprintf(buf, buf_size, "%lu:%02lu:%02lu", (unsigned long)hours, (unsigned long)minutes,
           (unsigned long)seconds);
}

// Hold ring completion callback - send approve action to Core
static void on_approve_complete(void* user_data) {
  (void)user_data;
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
}

// Check button event handler
static void check_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSED) {
    hold_ring_start(&approve_ring, HOLD_RING_COLOR_GREEN, on_approve_complete, NULL);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    hold_ring_stop(&approve_ring);
  }
}

static void setup_runin_start_screen(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  lv_obj_set_style_bg_color(scr, START_SCREEN_BG_COLOR, 0);

  // Top back button
  top_back_create(scr, &back_button, NULL);

  // Center text
  lv_obj_t* center_label = lv_label_create(scr);
  if (!center_label) {
    return;
  }
  lv_obj_set_style_text_color(center_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(center_label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(center_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(center_label, "Start Run-In App?");
  lv_obj_align(center_label, LV_ALIGN_CENTER, 0, START_SCREEN_CENTER_TEXT_Y);

  // Battery status below center
  uint8_t battery_pct = show_screen->params.mfg.battery_percent;
  bool charging = show_screen->params.mfg.is_charging;

  status_label = lv_label_create(scr);
  if (!status_label) {
    return;
  }
  lv_obj_set_style_text_font(status_label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);

  char status_text[64];
  if (charging) {
    lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_GREEN, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Charging)",
             (unsigned long)battery_pct);
  } else {
    lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_RED, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Not Charging - Plug in USB!)",
             (unsigned long)battery_pct);
  }
  lv_label_set_text(status_label, status_text);
  lv_obj_align(status_label, LV_ALIGN_CENTER, 0, START_SCREEN_BATTERY_Y);

  // Check button at bottom center
  check_button = lv_obj_create(scr);
  if (!check_button) {
    return;
  }
  lv_obj_set_size(check_button, CHECK_BUTTON_SIZE, CHECK_BUTTON_SIZE);
  lv_obj_align(check_button, LV_ALIGN_BOTTOM_MID, 0, -CHECK_BUTTON_BOTTOM_MARGIN);
  lv_obj_set_style_radius(check_button, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(check_button, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(check_button, PILL_BG_OPA, 0);
  lv_obj_set_style_border_opa(check_button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(check_button, 0, 0);
  lv_obj_clear_flag(check_button, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(check_button, LV_OBJ_FLAG_CLICKABLE);

  lv_obj_add_event_cb(check_button, check_button_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(check_button, check_button_event_handler, LV_EVENT_RELEASED, NULL);
  lv_obj_add_event_cb(check_button, check_button_event_handler, LV_EVENT_PRESS_LOST, NULL);

  // Check icon inside button
  lv_obj_t* check_icon = lv_img_create(check_button);
  if (check_icon) {
    lv_img_set_src(check_icon, &check);
    lv_obj_center(check_icon);
  }

  // Create hold ring widget
  hold_ring_create(scr, &approve_ring);
}

static void setup_runin_countdown(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  lv_obj_set_style_bg_color(scr, lv_color_black(), 0);

  char countdown_text[8];
  snprintf(countdown_text, sizeof(countdown_text), "%lu",
           (unsigned long)show_screen->params.mfg.countdown_value);

  status_label = lv_label_create(scr);
  if (!status_label) {
    return;
  }
  lv_obj_set_style_text_color(status_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(status_label, FONT_COUNTDOWN, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(status_label, countdown_text);
  lv_obj_center(status_label);
}

static void setup_runin_status_screen(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  // Dynamic background color based on test state
  lv_color_t bg_color;
  const char* result_text;

  if (show_screen->params.mfg.test_complete) {
    // Test complete: green = pass, red = fail
    if (show_screen->params.mfg.has_failures) {
      bg_color = STATUS_BG_COLOR_RED;
      result_text = "FAIL";
    } else {
      bg_color = STATUS_BG_COLOR_GREEN;
      result_text = "PASS";
    }
  } else {
    // Test in progress: yellow or orange
    if (show_screen->params.mfg.has_failures) {
      bg_color = STATUS_BG_COLOR_ORANGE;
    } else {
      bg_color = STATUS_BG_COLOR_YELLOW;
    }
    result_text = "In Progress";
  }

  lv_obj_set_style_bg_color(scr, bg_color, 0);

  // Top back button
  top_back_create(scr, &status_back_button, NULL);

  // Title
  lv_obj_t* title = lv_label_create(scr);
  if (!title) {
    return;
  }
  lv_obj_set_style_text_color(title, lv_color_black(), 0);
  lv_obj_set_style_text_font(title, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(title, "STATUS");
  lv_obj_align(title, LV_ALIGN_TOP_MID, 0, STATUS_TITLE_Y_OFFSET);

  // Status details
  char elapsed_str[32];
  char phase_remaining_str[32];
  format_time(elapsed_str, sizeof(elapsed_str), show_screen->params.mfg.elapsed_ms);
  format_time(phase_remaining_str, sizeof(phase_remaining_str),
              show_screen->params.mfg.phase_time_remaining_ms);

  // Get phase name based on power_phase enum value
  const char* phase_name;
  switch (show_screen->params.mfg.power_phase) {
    case 0:
      phase_name = "CHARGE 1";
      break;
    case 1:
      phase_name = "DISCHARGE";
      break;
    case 2:
      phase_name = "CHARGE 2";
      break;
    case 3:
      phase_name = "COMPLETE";
      break;
    default:
      phase_name = "UNKNOWN";
      break;
  }

  // Get phase number (1-based) and holding indicator
  uint32_t phase_num = show_screen->params.mfg.power_phase + 1;
  if (phase_num > 3) {
    phase_num = 3;  // Cap at 3 for complete phase
  }
  const char* holding_text = show_screen->params.mfg.target_reached ? " [HOLD]" : "";

  char status_text[256];
  uint32_t total_failures = show_screen->params.mfg.captouch_events +
                            show_screen->params.mfg.display_touch_events +
                            show_screen->params.mfg.fingerprint_events;

  if (total_failures == 0) {
    snprintf(status_text, sizeof(status_text),
             "Result: %s\nPhase %lu/3: %s%s\nRemain: %s\nTime: %s\nLoops: %lu\nSOC: %lu%%",
             result_text, (unsigned long)phase_num, phase_name, holding_text, phase_remaining_str,
             elapsed_str, (unsigned long)show_screen->params.mfg.loop_count,
             (unsigned long)show_screen->params.mfg.battery_percent);
  } else {
    snprintf(status_text, sizeof(status_text),
             "Result: %s\nPhase %lu/3: %s%s\nRemain: %s\nLoops: %lu\nCap:%lu FP:%lu T:%lu",
             result_text, (unsigned long)phase_num, phase_name, holding_text, phase_remaining_str,
             (unsigned long)show_screen->params.mfg.loop_count,
             (unsigned long)show_screen->params.mfg.captouch_events,
             (unsigned long)show_screen->params.mfg.fingerprint_events,
             (unsigned long)show_screen->params.mfg.display_touch_events);
  }

  status_label = lv_label_create(scr);
  if (!status_label) {
    return;
  }
  lv_obj_set_style_text_color(status_label, lv_color_black(), 0);
  lv_obj_set_style_text_font(status_label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(status_label, status_text);
  lv_obj_center(status_label);
}

static void setup_nfc_test_screen(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_white(), 0);

  lv_obj_t* label = lv_label_create(scr);
  if (!label) {
    return;
  }
  lv_obj_set_style_text_color(label, lv_color_black(), 0);
  lv_obj_set_style_text_font(label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(label, "NFC TAP");
  lv_obj_center(label);
}

static void setup_button_bypass_warning(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_make(255, 165, 0), 0);

  // Title
  lv_obj_t* title = lv_label_create(scr);
  if (!title) {
    return;
  }
  lv_obj_set_style_text_color(title, lv_color_black(), 0);
  lv_obj_set_style_text_font(title, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(title, "BUTTON TEST MODE");
  lv_obj_align(title, LV_ALIGN_CENTER, 0, -30);

  // Subtitle
  lv_obj_t* subtitle = lv_label_create(scr);
  if (!subtitle) {
    return;
  }
  lv_obj_set_style_text_color(subtitle, lv_color_black(), 0);
  lv_obj_set_style_text_font(subtitle, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(subtitle, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(subtitle, "UI Disabled");
  lv_obj_align(subtitle, LV_ALIGN_CENTER, 0, 10);
}

lv_obj_t* screen_mfg_run_in_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  fwpb_display_mfg_test_mode test_mode =
    fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN;

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_mfg_tag) {
    test_mode = show_screen->params.mfg.test_mode;
  }

  // Track current mode
  current_test_mode = test_mode;

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);

  // Set brightness
  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  // Render based on mode
  switch (test_mode) {
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN:
      setup_runin_start_screen(screen, show_screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COUNTDOWN:
      setup_runin_countdown(screen, show_screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS:
      setup_runin_status_screen(screen, show_screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION:
      mfg_starfield_fps_create(screen, &fps_widget);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_NFC_TEST:
      setup_nfc_test_screen(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BURNIN_GRID:
      mfg_burnin_grid_create(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BUTTON_BYPASS_WARNING:
      setup_button_bypass_warning(screen);
      break;
    // Color tests - just set solid background color
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR:
      if (show_screen) {
        lv_obj_set_style_bg_color(screen, lv_color_hex(show_screen->params.mfg.custom_rgb), 0);
      } else {
        lv_obj_set_style_bg_color(screen, lv_color_hex(0xFF0000), 0);
      }
      break;
    default:
      lv_obj_set_style_bg_color(screen, lv_color_hex(0xFF0000), 0);
      break;
  }

  return screen;
}

void screen_mfg_run_in_destroy(void) {
  if (!screen) {
    return;
  }

  // Cleanup widgets
  mfg_starfield_fps_destroy(&fps_widget);
  hold_ring_destroy(&approve_ring);
  top_back_destroy(&back_button);
  top_back_destroy(&status_back_button);
  check_button = NULL;

  lv_obj_del(screen);
  screen = NULL;
  status_label = NULL;
}

void screen_mfg_run_in_update(void* ctx) {
  if (!screen) {
    lv_obj_t* new_screen = screen_mfg_run_in_init(ctx);
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

  // If test mode changed, recreate the screen
  if (params->test_mode != current_test_mode) {
    screen_mfg_run_in_destroy();
    lv_obj_t* new_screen = screen_mfg_run_in_init(ctx);
    if (new_screen) {
      lv_scr_load(new_screen);
    }
    return;
  }

  // Same test mode - update dynamic screens
  switch (current_test_mode) {
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN:
      // Update battery status on start screen
      if (status_label) {
        char status_text[64];
        if (params->is_charging) {
          lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_GREEN, 0);
          snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Charging)",
                   (unsigned long)params->battery_percent);
        } else {
          lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_RED, 0);
          snprintf(status_text, sizeof(status_text),
                   "Battery: %lu%%\n(Not Charging - Plug in USB!)",
                   (unsigned long)params->battery_percent);
        }
        lv_label_set_text(status_label, status_text);
      }
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COUNTDOWN:
      // Update countdown value
      if (status_label) {
        char countdown_text[8];
        snprintf(countdown_text, sizeof(countdown_text), "%lu",
                 (unsigned long)params->countdown_value);
        lv_label_set_text(status_label, countdown_text);
      }
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS:
      // STATUS screen updates frequently - don't recreate, just update the label
      if (status_label) {
        // Regenerate status text with updated values
        char elapsed_str[32];
        char phase_remaining_str[32];
        format_time(elapsed_str, sizeof(elapsed_str), params->elapsed_ms);
        format_time(phase_remaining_str, sizeof(phase_remaining_str),
                    params->phase_time_remaining_ms);

        const char* phase_name;
        switch (params->power_phase) {
          case 0:
            phase_name = "CHARGE 1";
            break;
          case 1:
            phase_name = "DISCHARGE";
            break;
          case 2:
            phase_name = "CHARGE 2";
            break;
          case 3:
            phase_name = "COMPLETE";
            break;
          default:
            phase_name = "UNKNOWN";
            break;
        }

        uint32_t phase_num = params->power_phase + 1;
        if (phase_num > 3) {
          phase_num = 3;
        }
        const char* holding_text = params->target_reached ? " [HOLD]" : "";
        const char* result_text =
          params->test_complete ? (params->has_failures ? "FAIL" : "PASS") : "In Progress";

        char status_text[256];
        uint32_t total_failures =
          params->captouch_events + params->display_touch_events + params->fingerprint_events;

        if (total_failures == 0) {
          snprintf(status_text, sizeof(status_text),
                   "Result: %s\nPhase %lu/3: %s%s\nRemain: %s\nTime: %s\nLoops: %lu\nSOC: %lu%%",
                   result_text, (unsigned long)phase_num, phase_name, holding_text,
                   phase_remaining_str, elapsed_str, (unsigned long)params->loop_count,
                   (unsigned long)params->battery_percent);
        } else {
          snprintf(
            status_text, sizeof(status_text),
            "Result: %s\nPhase %lu/3: %s%s\nRemain: %s\nLoops: %lu\nCap:%lu FP:%lu T:%lu",
            result_text, (unsigned long)phase_num, phase_name, holding_text, phase_remaining_str,
            (unsigned long)params->loop_count, (unsigned long)params->captouch_events,
            (unsigned long)params->fingerprint_events, (unsigned long)params->display_touch_events);
        }

        lv_label_set_text(status_label, status_text);
      }
      break;

    default:
      // Other modes don't need updating
      break;
  }
}
