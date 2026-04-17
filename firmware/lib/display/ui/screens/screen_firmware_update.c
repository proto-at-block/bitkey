/**
 * Firmware update screen.
 *
 * Pages:
 * - PAGE_CONFIRMATION (0): user approval
 * - PAGE_IN_PROGRESS (2): update in progress
 */

#include "screen_firmware_update.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "log.h"
#include "lvgl/lvgl.h"
#include "ui.h"
#include "wallet.pb.h"
#include "widgets/approval_button.h"
#include "widgets/dot_ring.h"
#include "widgets/hold_cancel.h"
#include "widgets/loading_ring.h"
#include "widgets/top_menu.h"

#include <stdbool.h>
#include <stdio.h>
#include <string.h>

// Page values must match the firmware update flow controller.
typedef enum {
  PAGE_CONFIRMATION = 0,
  PAGE_IN_PROGRESS = 2,
  PAGE_SUCCESS = 3,
  PAGE_VERIFYING = 4,
  PAGE_FAILED = 5,
} fwup_page_t;

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Timing
#define HOLD_TO_CONFIRM_DURATION_MS 2000
#define IDLE_HINT_DELAY_MS          2000
#define STEP_DISPLAY_MS             6000
#define HOLD_DISPLAY_MS             1500
#define HEADER_FADE_DURATION_MS     160

// Layout
#define HEADER_HEIGHT           140
#define HEADER_PADDING_TOP      20
#define HEADER_PADDING_BOTTOM   20
#define TITLE_MARGIN_TOP        0
#define CONFIRMATION_TEXT_WIDTH 400
#define CONTENT_CENTER_OFFSET   24

// Colors
#define COLOR_RING APPROVAL_BUTTON_RING_COLOR

// Fonts
#define FONT_STEP         (&cash_sans_mono_regular_26)
#define FONT_CONFIRMATION (&cash_sans_mono_regular_28)
#define FONT_INFO         (&cash_sans_mono_regular_36)
#define FONT_VERSION      (&cash_sans_mono_regular_24)

// Success/failed page layout (matches money_movement confirmed page)
#define RESULT_ICON_Y  -28
#define RESULT_LABEL_Y 28

// Error color for the failed page (white icon, no tint needed)
#define COLOR_ERROR 0xFFFFFF

extern const lv_img_dsc_t check;
extern const lv_img_dsc_t exclamation_circle;

static lv_obj_t* screen = NULL;
static lv_obj_t* header = NULL;
static lv_obj_t* header_title = NULL;
static fwup_page_t current_page = PAGE_CONFIRMATION;

// Confirmation page elements
static lv_obj_t* approve_button = NULL;
static lv_obj_t* confirmation_message = NULL;
static top_menu_t menu_button = {0};
static dot_ring_t approve_ring = {0};
static hold_cancel_t cancel_modal = {0};
static bool hold_completed = false;

typedef enum {
  HEADER_PROMPT_MODE_STEP = 0,
  HEADER_PROMPT_MODE_HOLD_TO_CONFIRM = 1,
  HEADER_PROMPT_MODE_KEEP_HOLDING = 2,
} header_prompt_mode_t;

static lv_timer_t* header_hint_timer = NULL;
static header_prompt_mode_t header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static header_prompt_mode_t pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static bool check_button_held = false;

// In progress page elements
static lv_obj_t* progress_message = NULL;
static loading_ring_t progress_ring = {0};

// Forward declarations
static void approve_button_event_handler(lv_event_t* e);
static void menu_button_custom_handler(lv_event_t* e);
static void update_step_indicator_default(void);
static void update_step_indicator_keep_holding(void);
static void stop_header_hint_cycle(void);
static void restart_header_hint_cycle(void);
static void header_hint_timer_cb(lv_timer_t* timer);
static void set_header_prompt_mode(header_prompt_mode_t mode, bool animate);
static void header_text_opa_anim_cb(void* var, int32_t value);
static void header_fade_out_ready_cb(lv_anim_t* anim);

static void hold_ring_complete_handler(void* user_data) {
  (void)user_data;
  hold_completed = true;
  check_button_held = false;
  stop_header_hint_cycle();
  dot_ring_hide(&approve_ring);
  dot_ring_reset(&approve_ring);
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
}

static void on_cancel_complete(void* user_data) {
  (void)user_data;
  // User confirmed cancel - send cancel action
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL, 0);
}

static void on_cancel_dismiss(void* user_data) {
  (void)user_data;
  // User dismissed cancel modal - just hide it
  hold_cancel_hide(&cancel_modal);
  if (current_page == PAGE_CONFIRMATION) {
    update_step_indicator_default();
  }
}

static void menu_button_custom_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    hold_cancel_options_t options = {
      .followup_title = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_ON_YOUR_PHONE),
      .followup_text = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCEL_UPDATE_IN_APP),
    };

    stop_header_hint_cycle();
    // Show cancel confirmation modal
    hold_cancel_show_with_options(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL,
                                  &options);
  }
}

// Helper functions to create and destroy pages
static void create_confirmation_page(const fwpb_display_params_firmware_update* params) {
  // Cancel modal
  hold_cancel_create(screen, &cancel_modal);

  header = lv_obj_create(screen);
  lv_obj_set_size(header, LV_PCT(100), HEADER_HEIGHT);
  lv_obj_set_pos(header, 0, 0);
  lv_obj_set_style_bg_color(header, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(header, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(header, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(header, 0, 0);
  lv_obj_set_style_pad_top(header, HEADER_PADDING_TOP, 0);
  lv_obj_set_style_pad_bottom(header, HEADER_PADDING_BOTTOM, 0);
  lv_obj_clear_flag(header, LV_OBJ_FLAG_SCROLLABLE);

  header_title = lv_label_create(header);
  lv_obj_align(header_title, LV_ALIGN_TOP_MID, 0, HEADER_PADDING_TOP + 44 + TITLE_MARGIN_TOP);
  lv_obj_set_style_text_font(header_title, FONT_STEP, 0);
  update_step_indicator_default();

  const char* version_text = langpack_get_string(LANGPACK_ID_FIRMWARE_UPDATE_VERSION_UNKNOWN);
  if (params && params->version[0] != '\0') {
    version_text = params->version;
  }

  char update_text[128];
  snprintf(update_text, sizeof(update_text), "Update to %s", version_text);

  confirmation_message = lv_label_create(screen);
  lv_label_set_text(confirmation_message, update_text);
  lv_obj_set_style_text_color(confirmation_message, lv_color_white(), 0);
  lv_obj_set_style_text_font(confirmation_message, FONT_CONFIRMATION, 0);
  lv_obj_set_style_text_align(confirmation_message, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_width(confirmation_message, CONFIRMATION_TEXT_WIDTH);
  lv_label_set_long_mode(confirmation_message, LV_LABEL_LONG_WRAP);

  // Vertically center between the header content area and check button.
  lv_obj_update_layout(confirmation_message);
  lv_coord_t message_height = lv_obj_get_height(confirmation_message);
  lv_coord_t content_height = LV_VER_RES - HEADER_HEIGHT;
  lv_coord_t check_button_top =
    content_height - APPROVAL_BUTTON_BOTTOM_MARGIN - APPROVAL_BUTTON_SIZE;
  lv_coord_t message_top =
    HEADER_HEIGHT + ((check_button_top - message_height) / 2) - CONTENT_CENTER_OFFSET;
  lv_obj_align(confirmation_message, LV_ALIGN_TOP_MID, 0, message_top);

  // Create menu after header/content so it stays visible on top.
  top_menu_create(screen, &menu_button, menu_button_custom_handler);

  // Approve ring
  dot_ring_create(screen, &approve_ring);

  // Approve button
  approve_button = approval_button_create(screen, approve_button_event_handler);
}

static bool should_cycle_header_hint(void) {
  if (!header_title || lv_obj_has_flag(header_title, LV_OBJ_FLAG_HIDDEN)) {
    return false;
  }

  if (current_page != PAGE_CONFIRMATION || check_button_held || hold_completed) {
    return false;
  }

  return true;
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
      if (approve_button) {
        approval_button_set_icon_highlight(approve_button, lv_color_hex(COLOR_RING));
      }
      break;

    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      lv_label_set_text(header_title, langpack_get_string(LANGPACK_ID_CONFIRMATION_KEEP_HOLDING));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
      break;

    case HEADER_PROMPT_MODE_STEP:
    default:
      lv_label_set_text(header_title,
                        langpack_get_string(LANGPACK_ID_CONFIRMATION_HOLD_TO_CONFIRM));
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
      if (approve_button) {
        approval_button_clear_icon_highlight(approve_button);
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

  header_hint_timer = lv_timer_create(header_hint_timer_cb, HOLD_DISPLAY_MS, NULL);
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

  // Both modes display the same text, so crossfade opacity/color directly
  // without fading to transparent (which would cause the text to disappear).
  lv_opa_t current_opa = lv_obj_get_style_text_opa(header_title, 0);
  lv_opa_t target_opa = header_prompt_target_opa(next_mode);

  pending_header_prompt_mode = next_mode;
  header_prompt_mode = next_mode;
  lv_anim_del(header_title, header_text_opa_anim_cb);
  apply_header_prompt_mode(next_mode);
  lv_obj_set_style_text_opa(header_title, current_opa, 0);

  lv_anim_t crossfade;
  lv_anim_init(&crossfade);
  lv_anim_set_var(&crossfade, header_title);
  lv_anim_set_exec_cb(&crossfade, header_text_opa_anim_cb);
  lv_anim_set_time(&crossfade, HEADER_FADE_DURATION_MS);
  lv_anim_set_values(&crossfade, current_opa, target_opa);
  lv_anim_set_path_cb(&crossfade, lv_anim_path_ease_in_out);
  lv_anim_start(&crossfade);

  lv_timer_set_period(
    timer, next_mode == HEADER_PROMPT_MODE_HOLD_TO_CONFIRM ? HOLD_DISPLAY_MS : STEP_DISPLAY_MS);
}

static void update_step_indicator_default(void) {
  if (!header_title) {
    return;
  }

  set_header_prompt_mode(HEADER_PROMPT_MODE_HOLD_TO_CONFIRM, false);
  restart_header_hint_cycle();
}

static void update_step_indicator_keep_holding(void) {
  stop_header_hint_cycle();
  set_header_prompt_mode(HEADER_PROMPT_MODE_KEEP_HOLDING, false);
}

static void create_progress_page(const fwpb_display_params_firmware_update* params,
                                 langpack_string_id_t status_string_id) {
  if (!screen || !lv_obj_is_valid(screen)) {
    return;
  }

  // Menu button
  top_menu_create(screen, &menu_button, menu_button_custom_handler);

  // Cancel modal
  hold_cancel_create(screen, &cancel_modal);

  // Loading ring animation around screen edge
  loading_ring_create(screen, &progress_ring);

  lv_obj_t* progress_container = lv_obj_create(screen);
  if (!progress_container) {
    loading_ring_destroy(&progress_ring);
    return;
  }
  lv_obj_set_size(progress_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(progress_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(progress_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(progress_container, 0, 0);
  lv_obj_set_layout(progress_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(progress_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(progress_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(progress_container, 12, 0);
  lv_obj_clear_flag(progress_container, LV_OBJ_FLAG_SCROLLABLE);

  progress_message = lv_label_create(progress_container);
  if (!progress_message) {
    loading_ring_destroy(&progress_ring);
    return;
  }

  lv_obj_set_style_text_color(progress_message, lv_color_white(), 0);
  lv_obj_set_style_text_font(progress_message, FONT_INFO, 0);
  lv_obj_set_style_text_align(progress_message, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_width(progress_message, 400);
  lv_label_set_long_mode(progress_message, LV_LABEL_LONG_WRAP);
  lv_label_set_text(progress_message, langpack_get_string(status_string_id));

  if (params && params->version[0] != '\0') {
    lv_obj_t* version_label = lv_label_create(progress_container);
    lv_obj_set_style_text_color(version_label, lv_color_hex(0xADADAD), 0);
    lv_obj_set_style_text_font(version_label, FONT_VERSION, 0);
    lv_obj_set_style_text_align(version_label, LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_text(version_label, params->version);
  }

  lv_obj_update_layout(progress_container);
  lv_obj_align(progress_container, LV_ALIGN_CENTER, 0, 0);

  loading_ring_start(&progress_ring);
}

static void create_success_page(void) {
  if (!screen || !lv_obj_is_valid(screen)) {
    return;
  }

  // Checkmark icon centered on screen, recolored to the green ring color.
  // Matches the "CONFIRMED" page from money_movement.
  lv_obj_t* checkmark = lv_img_create(screen);
  if (checkmark) {
    lv_img_set_src(checkmark, &check);
    lv_obj_set_style_img_recolor(checkmark, lv_color_hex(COLOR_RING), 0);
    lv_obj_set_style_img_recolor_opa(checkmark, LV_OPA_COVER, 0);
    lv_obj_align(checkmark, LV_ALIGN_CENTER, 0, RESULT_ICON_Y);
  }

  lv_obj_t* label = lv_label_create(screen);
  if (label) {
    lv_label_set_text(label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_CONFIRMED));
    lv_obj_set_style_text_color(label, lv_color_hex(COLOR_RING), 0);
    lv_obj_set_style_text_font(label, FONT_STEP, 0);
    lv_obj_align(label, LV_ALIGN_CENTER, 0, RESULT_LABEL_Y);
  }
}

static void create_failed_page(void) {
  if (!screen || !lv_obj_is_valid(screen)) {
    return;
  }

  lv_obj_t* icon = lv_img_create(screen);
  if (icon) {
    lv_img_set_src(icon, &exclamation_circle);
    lv_obj_set_style_img_recolor(icon, lv_color_white(), 0);
    lv_obj_set_style_img_recolor_opa(icon, LV_OPA_COVER, 0);
    lv_obj_align(icon, LV_ALIGN_CENTER, 0, RESULT_ICON_Y);
  }

  lv_obj_t* label = lv_label_create(screen);
  if (label) {
    lv_label_set_text(label, langpack_get_string(LANGPACK_ID_FIRMWARE_UPDATE_FAILED));
    lv_obj_set_style_text_color(label, lv_color_hex(COLOR_ERROR), 0);
    lv_obj_set_style_text_font(label, FONT_STEP, 0);
    lv_obj_align(label, LV_ALIGN_CENTER, 0, RESULT_LABEL_Y);
  }
}

static void approve_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  lv_obj_t* check_button = lv_event_get_target(e);

  if (code == LV_EVENT_PRESSED) {
    hold_completed = false;
    check_button_held = true;
    update_step_indicator_keep_holding();

    if (check_button) {
      approval_button_set_hold_state(check_button);
    }

    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }

    dot_ring_show(&approve_ring);
    dot_ring_animate_fill_from_current(&approve_ring, 100, HOLD_TO_CONFIRM_DURATION_MS,
                                       DOT_RING_COLOR_GREEN, DOT_RING_FILL_SPLIT,
                                       hold_ring_complete_handler, NULL);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    if (hold_completed) {
      return;
    }

    if (dot_ring_animate_release(&approve_ring, HOLD_TO_CONFIRM_DURATION_MS)) {
      return;
    }

    check_button_held = false;

    // User released - rewind the ring while restoring the default prompt.
    update_step_indicator_default();

    if (check_button) {
      approval_button_set_idle_state(check_button);
    }

    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }
  }
}

lv_obj_t* screen_firmware_update_init(void* ctx) {
  ASSERT(screen == NULL);

  if (!ctx) {
    return NULL;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_firmware_update* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_firmware_update_tag) {
    params = &show_screen->params.firmware_update;
  }

  // Initialize all widgets
  memset(&menu_button, 0, sizeof(menu_button));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&progress_ring, 0, sizeof(progress_ring));
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  check_button_held = false;
  hold_completed = false;

  // Create screen
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Create appropriate page based on params
  uint32_t page = params ? params->page : (uint32_t)PAGE_CONFIRMATION;
  switch (page) {
    case PAGE_CONFIRMATION:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
    case PAGE_IN_PROGRESS:
      current_page = PAGE_IN_PROGRESS;
      create_progress_page(params, LANGPACK_ID_FIRMWARE_UPDATE_IN_PROGRESS);
      break;
    case PAGE_SUCCESS:
      current_page = PAGE_SUCCESS;
      create_success_page();
      break;
    case PAGE_VERIFYING:
      current_page = PAGE_VERIFYING;
      create_progress_page(params, LANGPACK_ID_FIRMWARE_UPDATE_VERIFYING);
      break;
    case PAGE_FAILED:
      current_page = PAGE_FAILED;
      create_failed_page();
      break;
    default:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
  }

  ui_set_local_brightness(SCREEN_BRIGHTNESS);
  return screen;
}

static void screen_firmware_update_clean_widgets(void) {
  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }

  // Clean up widgets (they manage their own LVGL objects)
  if (menu_button.is_initialized) {
    top_menu_destroy(&menu_button);
  }
  memset(&menu_button, 0, sizeof(menu_button));

  if (approve_ring.is_initialized) {
    dot_ring_destroy(&approve_ring);
  }
  memset(&approve_ring, 0, sizeof(approve_ring));

  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }
  memset(&cancel_modal, 0, sizeof(cancel_modal));

  if (progress_ring.is_initialized) {
    loading_ring_destroy(&progress_ring);
  }
  memset(&progress_ring, 0, sizeof(progress_ring));

  // Reset all child pointers
  header = NULL;
  header_title = NULL;
  approve_button = NULL;
  confirmation_message = NULL;
  progress_message = NULL;
  current_page = PAGE_CONFIRMATION;
  hold_completed = false;
  check_button_held = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
}

void screen_firmware_update_destroy(void) {
  if (!screen) {
    return;
  }

  screen_firmware_update_clean_widgets();

  // Delete screen and all children
  lv_obj_del(screen);
  screen = NULL;
}

void screen_firmware_update_update(void* ctx) {
  if (!screen) {
    return;
  }

  // Clean widgets and children, but don't delete the screen itself
  // (screen is still the active LVGL screen during update)
  screen_firmware_update_clean_widgets();
  lv_obj_clean(screen);

  // Re-initialize content without creating a new screen
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_firmware_update* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_firmware_update_tag) {
    params = &show_screen->params.firmware_update;
  }

  // Initialize all widgets
  memset(&menu_button, 0, sizeof(menu_button));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&progress_ring, 0, sizeof(progress_ring));

  // Create appropriate page based on params
  uint32_t page = params ? params->page : (uint32_t)PAGE_CONFIRMATION;
  switch (page) {
    case PAGE_CONFIRMATION:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
    case PAGE_IN_PROGRESS:
      current_page = PAGE_IN_PROGRESS;
      create_progress_page(params, LANGPACK_ID_FIRMWARE_UPDATE_IN_PROGRESS);
      break;
    case PAGE_SUCCESS:
      current_page = PAGE_SUCCESS;
      create_success_page();
      break;
    case PAGE_VERIFYING:
      current_page = PAGE_VERIFYING;
      create_progress_page(params, LANGPACK_ID_FIRMWARE_UPDATE_VERIFYING);
      break;
    case PAGE_FAILED:
      current_page = PAGE_FAILED;
      create_failed_page();
      break;
    default:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
  }
}
