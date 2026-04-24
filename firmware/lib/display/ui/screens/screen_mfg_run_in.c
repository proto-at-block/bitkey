// Run-in test screen - multi-state test flow with hold ring confirmation

#include "screen_mfg_run_in.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl.h"
#include "ui.h"
#include "widgets/dot_ring.h"
#include "widgets/mfg_burnin_checker.h"
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

// Timing
#define HOLD_TO_CONFIRM_DURATION_MS 2000
#define IDLE_HINT_DELAY_MS          2000
#define STEP_DISPLAY_MS             6000
#define HOLD_DISPLAY_MS             1500
#define HEADER_FADE_DURATION_MS     160

// Layout configuration
#define CHECK_BUTTON_SIZE          80  // Check button size (circular)
#define CHECK_BUTTON_BOTTOM_MARGIN 40  // Bottom margin for check button
#define CHECK_ICON_ZOOM            LV_SCALE_NONE
#define PILL_BG_COLOR              0x404040
#define PILL_BG_OPA                LV_OPA_70
#define HEADER_PROMPT_Y            64

// Run-in test screen layout configuration
#define START_SCREEN_BG_COLOR      lv_color_make(64, 64, 64)  // Dark gray
#define START_SCREEN_CENTER_TEXT_Y -20                        // Center text Y offset
#define START_SCREEN_BATTERY_Y     30                         // Battery status Y offset
#define START_SCREEN_COLOR_GREEN   lv_color_make(0, 255, 0)   // Charging color
#define START_SCREEN_COLOR_RED     lv_color_make(255, 0, 0)   // Not charging color

#define STATUS_BG_COLOR_YELLOW lv_color_make(200, 200, 0)  // In progress
#define STATUS_BG_COLOR_BLUE   lv_color_make(0, 100, 200)  // Complete (neutral)
#define STATUS_TITLE_Y_OFFSET  10                          // Title Y offset from top

// Fonts
#define FONT_STANDARD  (&cash_sans_mono_regular_22)
#define FONT_COUNTDOWN (&cash_sans_mono_regular_36)
#define FONT_PROMPT    (&cash_sans_mono_regular_26)

// Colors
#define COLOR_RING 0xD1FB96

// External image declarations
extern const lv_img_dsc_t check;

// Screen state
static lv_obj_t* screen = NULL;
static fwpb_display_mfg_test_mode current_test_mode =
  fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;

// General UI elements
static lv_obj_t* status_label = NULL;
static lv_obj_t* header_title = NULL;

// Widgets
static lv_obj_t* check_button = NULL;
static dot_ring_t approve_ring = {0};
static top_back_t back_button = {0};
static top_back_t status_back_button = {0};
static mfg_starfield_fps_t fps_widget = {0};
static lv_timer_t* header_hint_timer = NULL;
static bool check_button_held = false;
static bool hold_completed = false;

typedef enum {
  HEADER_PROMPT_MODE_STEP = 0,
  HEADER_PROMPT_MODE_HOLD_TO_CONFIRM = 1,
  HEADER_PROMPT_MODE_KEEP_HOLDING = 2,
} header_prompt_mode_t;

static header_prompt_mode_t header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static header_prompt_mode_t pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;

static void stop_header_hint_cycle(void);
static void restart_header_hint_cycle(void);
static void header_hint_timer_cb(lv_timer_t* timer);
static void set_header_prompt_mode(header_prompt_mode_t mode, bool animate);
static void header_text_opa_anim_cb(void* var, int32_t value);
static void header_fade_out_ready_cb(lv_anim_t* anim);

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
  hold_completed = true;
  check_button_held = false;
  stop_header_hint_cycle();
  dot_ring_hide(&approve_ring);
  dot_ring_reset(&approve_ring);
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
}

static void update_runin_start_screen_status(lv_obj_t* label, uint8_t battery_pct, bool plugged_in,
                                             bool charging) {
  if (!label) {
    return;
  }

  char status_text[64];
  if (!plugged_in) {
    lv_obj_set_style_text_color(label, START_SCREEN_COLOR_RED, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Not Charging - Plug in USB!)",
             (unsigned long)battery_pct);
  } else if (charging) {
    lv_obj_set_style_text_color(label, START_SCREEN_COLOR_GREEN, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Charging)",
             (unsigned long)battery_pct);
  } else {
    lv_obj_set_style_text_color(label, START_SCREEN_COLOR_GREEN, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(USB Connected)",
             (unsigned long)battery_pct);
  }

  lv_label_set_text(label, status_text);
}

static bool should_cycle_header_hint(void) {
  if (!header_title || lv_obj_has_flag(header_title, LV_OBJ_FLAG_HIDDEN)) {
    return false;
  }

  if (current_test_mode != fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN) {
    return false;
  }

  if (check_button_held || hold_completed) {
    return false;
  }

  return true;
}

static void set_check_button_icon_color(lv_obj_t* icon, lv_color_t color) {
  if (!icon || !lv_obj_is_valid(icon)) {
    return;
  }

  lv_obj_set_style_img_recolor(icon, color, 0);
  lv_obj_set_style_img_recolor_opa(icon, LV_OPA_COVER, 0);
}

static lv_opa_t header_prompt_target_opa(header_prompt_mode_t mode) {
  switch (mode) {
    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      return LV_OPA_COVER;
    case HEADER_PROMPT_MODE_HOLD_TO_CONFIRM:
      return LV_OPA_COVER;
    case HEADER_PROMPT_MODE_STEP:
    default:
      return LV_OPA_50;
  }
}

static void apply_header_prompt_mode(header_prompt_mode_t mode) {
  if (!header_title) {
    return;
  }

  switch (mode) {
    case HEADER_PROMPT_MODE_HOLD_TO_CONFIRM:
      lv_label_set_text(header_title,
                        langpack_get_string(LANGPACK_ID_CONFIRMATION_HOLD_TO_CONFIRM));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
      if (check_button) {
        lv_obj_t* icon = lv_obj_get_child(check_button, 0);
        if (icon) {
          lv_obj_set_style_img_recolor(icon, lv_color_hex(COLOR_RING), 0);
          lv_obj_set_style_img_recolor_opa(icon, LV_OPA_COVER, 0);
        }
      }
      break;

    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      lv_label_set_text(header_title, langpack_get_string(LANGPACK_ID_CONFIRMATION_KEEP_HOLDING));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
      break;

    case HEADER_PROMPT_MODE_STEP:
    default:
      lv_label_set_text(header_title, "");
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
      if (check_button) {
        lv_obj_t* icon = lv_obj_get_child(check_button, 0);
        if (icon) {
          set_check_button_icon_color(icon, lv_color_white());
        }
      }
      break;
  }
}

static void header_text_opa_anim_cb(void* var, int32_t value) {
  lv_obj_t* obj = (lv_obj_t*)var;
  if (!obj || !lv_obj_is_valid(obj)) {
    return;
  }
  lv_obj_set_style_text_opa(obj, (lv_opa_t)value, 0);
}

static void header_fade_out_ready_cb(lv_anim_t* anim) {
  (void)anim;

  if (!header_title || !lv_obj_is_valid(header_title)) {
    return;
  }

  header_prompt_mode = pending_header_prompt_mode;
  apply_header_prompt_mode(header_prompt_mode);
  lv_obj_set_style_text_opa(header_title, LV_OPA_TRANSP, 0);

  lv_anim_t fade_in;
  lv_anim_init(&fade_in);
  lv_anim_set_var(&fade_in, header_title);
  lv_anim_set_exec_cb(&fade_in, header_text_opa_anim_cb);
  lv_anim_set_time(&fade_in, HEADER_FADE_DURATION_MS);
  lv_anim_set_values(&fade_in, LV_OPA_TRANSP, header_prompt_target_opa(header_prompt_mode));
  lv_anim_set_path_cb(&fade_in, lv_anim_path_ease_in_out);
  lv_anim_start(&fade_in);
}

static void set_header_prompt_mode(header_prompt_mode_t mode, bool animate) {
  if (!header_title || !lv_obj_is_valid(header_title)) {
    return;
  }

  pending_header_prompt_mode = mode;
  lv_anim_del(header_title, header_text_opa_anim_cb);

  if (!animate) {
    header_prompt_mode = mode;
    apply_header_prompt_mode(mode);
    return;
  }

  lv_opa_t current_opa = lv_obj_get_style_text_opa(header_title, 0);
  if (current_opa == LV_OPA_TRANSP) {
    header_fade_out_ready_cb(NULL);
    return;
  }

  lv_anim_t fade_out;
  lv_anim_init(&fade_out);
  lv_anim_set_var(&fade_out, header_title);
  lv_anim_set_exec_cb(&fade_out, header_text_opa_anim_cb);
  lv_anim_set_time(&fade_out, HEADER_FADE_DURATION_MS);
  lv_anim_set_values(&fade_out, current_opa, LV_OPA_TRANSP);
  lv_anim_set_path_cb(&fade_out, lv_anim_path_ease_in_out);
  lv_anim_set_ready_cb(&fade_out, header_fade_out_ready_cb);
  lv_anim_start(&fade_out);
}

static void stop_header_hint_cycle(void) {
  if (header_hint_timer) {
    lv_timer_del(header_hint_timer);
    header_hint_timer = NULL;
  }
}

static void restart_header_hint_cycle(void) {
  stop_header_hint_cycle();

  if (!should_cycle_header_hint()) {
    return;
  }

  header_hint_timer = lv_timer_create(header_hint_timer_cb, IDLE_HINT_DELAY_MS, NULL);
  if (header_hint_timer) {
    lv_timer_set_repeat_count(header_hint_timer, -1);
  }
}

static void header_hint_timer_cb(lv_timer_t* timer) {
  if (!should_cycle_header_hint()) {
    stop_header_hint_cycle();
    return;
  }

  header_prompt_mode_t next_mode = (header_prompt_mode == HEADER_PROMPT_MODE_STEP)
                                     ? HEADER_PROMPT_MODE_HOLD_TO_CONFIRM
                                     : HEADER_PROMPT_MODE_STEP;
  set_header_prompt_mode(next_mode, true);
  lv_timer_set_period(
    timer, next_mode == HEADER_PROMPT_MODE_HOLD_TO_CONFIRM ? HOLD_DISPLAY_MS : STEP_DISPLAY_MS);
}

// Check button event handler
static void check_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  lv_obj_t* button = lv_event_get_target(e);

  if (code == LV_EVENT_PRESSED) {
    hold_completed = false;
    check_button_held = true;
    stop_header_hint_cycle();
    set_header_prompt_mode(HEADER_PROMPT_MODE_KEEP_HOLDING, false);

    if (button) {
      lv_obj_set_style_bg_color(button, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_bg_opa(button, LV_OPA_COVER, 0);

      lv_obj_t* check_icon = lv_obj_get_child(button, 0);
      if (check_icon) {
        set_check_button_icon_color(check_icon, lv_color_black());
      }
    }

    if (back_button.is_initialized && back_button.container) {
      lv_obj_add_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
    }

    dot_ring_show_with_fade_in(&approve_ring, 400);
    dot_ring_animate_fill_from_current(&approve_ring, 100, HOLD_TO_CONFIRM_DURATION_MS,
                                       DOT_RING_COLOR_GREEN, DOT_RING_FILL_SPLIT,
                                       on_approve_complete, NULL);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    if (hold_completed) {
      return;
    }

    if (dot_ring_animate_release(&approve_ring, HOLD_TO_CONFIRM_DURATION_MS)) {
      return;
    }

    check_button_held = false;

    if (button) {
      lv_obj_set_style_bg_color(button, lv_color_hex(PILL_BG_COLOR), 0);
      lv_obj_set_style_bg_opa(button, PILL_BG_OPA, 0);

      lv_obj_t* check_icon = lv_obj_get_child(button, 0);
      if (check_icon) {
        set_check_button_icon_color(check_icon, lv_color_white());
      }
    }

    if (back_button.is_initialized && back_button.container) {
      lv_obj_clear_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
    }

    set_header_prompt_mode(HEADER_PROMPT_MODE_STEP, false);
    restart_header_hint_cycle();
  }
}

static void setup_runin_start_screen(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  lv_obj_set_style_bg_color(scr, START_SCREEN_BG_COLOR, 0);
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  check_button_held = false;
  hold_completed = false;

  // Top back button
  top_back_create(scr, &back_button, NULL);

  header_title = lv_label_create(scr);
  if (!header_title) {
    return;
  }
  lv_obj_align(header_title, LV_ALIGN_TOP_MID, 0, HEADER_PROMPT_Y);
  lv_obj_set_style_text_font(header_title, FONT_PROMPT, 0);
  lv_obj_set_style_text_align(header_title, LV_TEXT_ALIGN_CENTER, 0);
  set_header_prompt_mode(HEADER_PROMPT_MODE_STEP, false);
  restart_header_hint_cycle();

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
  update_runin_start_screen_status(status_label, battery_pct, show_screen->params.mfg.plugged_in,
                                   charging);
  lv_obj_align(status_label, LV_ALIGN_CENTER, 0, START_SCREEN_BATTERY_Y);

  // Check button at bottom center
  check_button = lv_obj_create(scr);
  if (!check_button) {
    return;
  }
  lv_obj_set_size(check_button, CHECK_BUTTON_SIZE, CHECK_BUTTON_SIZE);
  lv_obj_align(check_button, LV_ALIGN_BOTTOM_MID, 0, -CHECK_BUTTON_BOTTOM_MARGIN);
  lv_obj_set_style_radius(check_button, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(check_button, lv_color_hex(PILL_BG_COLOR), 0);
  lv_obj_set_style_bg_opa(check_button, PILL_BG_OPA, 0);
  lv_obj_set_style_border_opa(check_button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(check_button, 0, 0);
  lv_obj_clear_flag(check_button, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(check_button, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(check_button, LV_OBJ_FLAG_PRESS_LOCK);

  lv_obj_add_event_cb(check_button, check_button_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(check_button, check_button_event_handler, LV_EVENT_RELEASED, NULL);
  lv_obj_add_event_cb(check_button, check_button_event_handler, LV_EVENT_PRESS_LOST, NULL);

  // Check icon inside button
  lv_obj_t* check_icon = lv_img_create(check_button);
  if (check_icon) {
    lv_img_set_src(check_icon, &check);
    set_check_button_icon_color(check_icon, lv_color_white());
    lv_img_set_zoom(check_icon, CHECK_ICON_ZOOM);
    lv_obj_center(check_icon);
  }

  // Create dot ring widget
  dot_ring_create(scr, &approve_ring);
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
  lv_color_t bg_color =
    show_screen->params.mfg.test_complete ? STATUS_BG_COLOR_BLUE : STATUS_BG_COLOR_YELLOW;

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

  char elapsed_str[32];
  format_time(elapsed_str, sizeof(elapsed_str), show_screen->params.mfg.elapsed_ms);

  const char* phase_name;
  switch (show_screen->params.mfg.power_phase) {
    case 0:
      phase_name = "CONVERGE";
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

  uint32_t phase_num = show_screen->params.mfg.power_phase + 1;
  if (phase_num > 3) {
    phase_num = 3;
  }

  const char* direction_text =
    show_screen->params.mfg.test_complete
      ? (show_screen->params.mfg.is_charging
           ? "Charging"
           : (show_screen->params.mfg.plugged_in ? "USB Connected" : "Not Charging"))
      : (show_screen->params.mfg.is_discharging
           ? "Discharging"
           : (show_screen->params.mfg.is_charging ? "Charging" : "Not Charging"));

  char status_text[256];
  snprintf(status_text, sizeof(status_text),
           "Phase %lu/3: %s\nDirection: %s\nTime: %s\nLoops: %lu\nBattery: %lu%%\nCap:%lu FP:%lu "
           "T:%lu",
           (unsigned long)phase_num, phase_name, direction_text, elapsed_str,
           (unsigned long)show_screen->params.mfg.loop_count,
           (unsigned long)show_screen->params.mfg.battery_percent,
           (unsigned long)show_screen->params.mfg.captouch_events,
           (unsigned long)show_screen->params.mfg.fingerprint_events,
           (unsigned long)show_screen->params.mfg.display_touch_events);

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
  header_title = NULL;
  check_button = NULL;
  check_button_held = false;
  hold_completed = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;

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
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BURNIN_CHECKER:
      mfg_burnin_checker_create(screen);
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
  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }
  mfg_starfield_fps_destroy(&fps_widget);
  dot_ring_destroy(&approve_ring);
  top_back_destroy(&back_button);
  top_back_destroy(&status_back_button);
  check_button = NULL;

  lv_obj_del(screen);
  screen = NULL;
  status_label = NULL;
  header_title = NULL;
  check_button_held = false;
  hold_completed = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
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
        update_runin_start_screen_status(status_label, params->battery_percent, params->plugged_in,
                                         params->is_charging);
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
      if (status_label) {
        lv_color_t bg_color = params->test_complete ? STATUS_BG_COLOR_BLUE : STATUS_BG_COLOR_YELLOW;
        lv_obj_set_style_bg_color(screen, bg_color, 0);

        char elapsed_str[32];
        format_time(elapsed_str, sizeof(elapsed_str), params->elapsed_ms);

        const char* phase_name;
        switch (params->power_phase) {
          case 0:
            phase_name = "CONVERGE";
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

        const char* direction_text =
          params->test_complete
            ? (params->is_charging ? "Charging"
                                   : (params->plugged_in ? "USB Connected" : "Not Charging"))
            : (params->is_discharging ? "Discharging"
                                      : (params->is_charging ? "Charging" : "Not Charging"));

        char status_text[256];
        snprintf(
          status_text, sizeof(status_text),
          "Phase %lu/3: %s\nDirection: %s\nTime: %s\nLoops: %lu\nBattery: %lu%%\nCap:%lu FP:%lu "
          "T:%lu",
          (unsigned long)phase_num, phase_name, direction_text, elapsed_str,
          (unsigned long)params->loop_count, (unsigned long)params->battery_percent,
          (unsigned long)params->captouch_events, (unsigned long)params->fingerprint_events,
          (unsigned long)params->display_touch_events);

        lv_label_set_text(status_label, status_text);
      }
      break;

    default:
      // Other modes don't need updating
      break;
  }
}
