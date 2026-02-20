/**
 * Firmware Update Screen - Two-Tap Confirmation Flow
 *
 * Three-page state machine:
 * - PAGE_CONFIRMATION (0): User holds ring to approve
 * - PAGE_SCANNING (1): Waiting for phone tap after approval
 * - PAGE_IN_PROGRESS (2): Firmware update in progress
 *
 */

#include "screen_firmware_update.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "log.h"
#include "lvgl/lvgl.h"
#include "top_back.h"
#include "ui.h"
#include "wallet.pb.h"
#include "widgets/dot_ring.h"
#include "widgets/hold_cancel.h"
#include "widgets/orbital_dots_animation.h"

#include <stdio.h>
#include <string.h>

// Page states
typedef enum {
  PAGE_CONFIRMATION = 0,
  PAGE_SCANNING = 1,
  PAGE_IN_PROGRESS = 2,
} fwup_page_t;

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Timing
#define HOLD_TO_CONFIRM_DURATION_MS 2000

// Layout
#define TITLE_Y_OFFSET     -120
#define INFO_Y_OFFSET_BASE -60
#define INFO_LINE_SPACING  30
#define HOLD_RING_Y_OFFSET 60

// Colors
#define COLOR_WHITE 0xFFFFFF
#define COLOR_TITLE 0xADADAD

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_24)
#define FONT_INFO  (&cash_sans_mono_regular_20)

static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static fwup_page_t current_page = PAGE_CONFIRMATION;

// Confirmation page elements
static lv_obj_t* approve_button = NULL;
static top_back_t back_button = {0};
static dot_ring_t approve_ring = {0};
static hold_cancel_t cancel_modal = {0};

// Scanning page elements
static lv_obj_t* scanning_container = NULL;
static lv_obj_t* scanning_message = NULL;
static lv_obj_t* scanning_orbital_parent = NULL;
static orbital_dots_animation_t scanning_dots = {0};

// In progress page elements
static lv_obj_t* progress_message = NULL;

// Forward declarations
static void approve_button_event_handler(lv_event_t* e);
static void back_button_custom_handler(lv_event_t* e);

static void hold_ring_complete_handler(void* user_data) {
  (void)user_data;
  // User held ring to approve - send approve action
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
}

static void back_button_custom_handler(lv_event_t* e) {
  (void)e;
  // Show cancel confirmation modal
  hold_cancel_show(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL);
}

// Helper functions to create and destroy pages
static void create_confirmation_page(const fwpb_display_params_firmware_update* params) {
  if (!params) {
    return;
  }

  // Back button
  top_back_create(screen, &back_button, back_button_custom_handler);

  // Cancel modal
  hold_cancel_create(screen, &cancel_modal);

  // Approve ring
  dot_ring_create(screen, &approve_ring);

  // Approve button
  approve_button = lv_obj_create(screen);
  lv_obj_set_size(approve_button, 80, 80);
  lv_obj_align(approve_button, LV_ALIGN_CENTER, 0, HOLD_RING_Y_OFFSET);
  lv_obj_set_style_bg_opa(approve_button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_color(approve_button, lv_color_white(), 0);
  lv_obj_set_style_border_width(approve_button, 3, 0);
  lv_obj_set_style_border_opa(approve_button, LV_OPA_COVER, 0);
  lv_obj_set_style_radius(approve_button, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_pad_all(approve_button, 0, 0);
  lv_obj_clear_flag(approve_button, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(approve_button, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(approve_button, approve_button_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(approve_button, approve_button_event_handler, LV_EVENT_RELEASED, NULL);
  lv_obj_add_event_cb(approve_button, approve_button_event_handler, LV_EVENT_PRESS_LOST, NULL);

  // Check icon
  extern const lv_img_dsc_t check;
  lv_obj_t* check_icon = lv_img_create(approve_button);
  lv_img_set_src(check_icon, &check);
  lv_obj_center(check_icon);
}

static void create_scanning_page(void) {
  // Create orbital dots animation
  scanning_orbital_parent = orbital_dots_animation_create(screen, &scanning_dots);
  if (!scanning_orbital_parent) {
    LOGE("Failed to create orbital animation");
    return;
  }
  orbital_dots_animation_start(&scanning_dots);

  // Create text container with proper styling (like money_movement)
  scanning_container = lv_obj_create(screen);
  if (!scanning_container) {
    LOGE("Failed to create scanning container");
    return;
  }

  lv_obj_set_style_bg_color(scanning_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(scanning_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(scanning_container, 0, 0);
  lv_obj_set_style_pad_left(scanning_container, 24, 0);
  lv_obj_set_style_pad_right(scanning_container, 24, 0);
  lv_obj_set_style_pad_top(scanning_container, 16, 0);
  lv_obj_set_style_pad_bottom(scanning_container, 16, 0);
  lv_obj_clear_flag(scanning_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(scanning_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_move_foreground(scanning_container);

  // Label inside container
  scanning_message = lv_label_create(scanning_container);
  if (!scanning_message) {
    LOGE("Failed to create scanning message label");
    return;
  }

  lv_label_set_text(scanning_message, langpack_get_string(LANGPACK_ID_SCAN_TAP));
  lv_obj_set_style_text_align(scanning_message, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_color(scanning_message, lv_color_white(), 0);
  lv_obj_set_style_text_font(scanning_message, &cash_sans_mono_regular_36, 0);
  lv_obj_set_width(scanning_message, 400);
  lv_label_set_long_mode(scanning_message, LV_LABEL_LONG_WRAP);
  lv_obj_center(scanning_message);

  lv_obj_update_layout(scanning_container);
  lv_obj_set_size(scanning_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(scanning_container, LV_ALIGN_CENTER, 0, 0);
}

static void create_in_progress_page(void) {
  if (!screen || !lv_obj_is_valid(screen)) {
    return;
  }

  progress_message = lv_label_create(screen);
  if (!progress_message) {
    return;
  }

  lv_obj_set_style_text_color(progress_message, lv_color_white(), 0);
  lv_obj_set_style_text_font(progress_message, FONT_INFO, 0);
  lv_obj_align(progress_message, LV_ALIGN_CENTER, 0, 0);
  lv_obj_move_foreground(progress_message);
  lv_label_set_text(progress_message, langpack_get_string(LANGPACK_ID_FIRMWARE_UPDATE_IN_PROGRESS));
}

static void approve_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSED) {
    // User pressed - show ring and start hold animation
    dot_ring_show(&approve_ring);
    dot_ring_animate_fill(&approve_ring, 100, HOLD_TO_CONFIRM_DURATION_MS, DOT_RING_COLOR_GREEN,
                          DOT_RING_FILL_SPLIT, hold_ring_complete_handler, NULL);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    // User released - stop animation and hide ring
    dot_ring_stop(&approve_ring);
    dot_ring_hide(&approve_ring);
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
  memset(&back_button, 0, sizeof(back_button));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scanning_dots, 0, sizeof(scanning_dots));

  // Create screen
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Title
  title_label = lv_label_create(screen);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_CENTER, 0, TITLE_Y_OFFSET);
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FIRMWARE_UPDATE_TITLE));

  // Create appropriate page based on params
  uint32_t page = params ? params->page : (uint32_t)PAGE_CONFIRMATION;
  switch (page) {
    case PAGE_CONFIRMATION:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
    case PAGE_SCANNING:
      current_page = PAGE_SCANNING;
      create_scanning_page();
      break;
    case PAGE_IN_PROGRESS:
      current_page = PAGE_IN_PROGRESS;
      create_in_progress_page();
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
  // Clean up widgets (they manage their own LVGL objects)
  if (back_button.is_initialized) {
    top_back_destroy(&back_button);
  }
  memset(&back_button, 0, sizeof(back_button));

  if (approve_ring.is_initialized) {
    dot_ring_destroy(&approve_ring);
  }
  memset(&approve_ring, 0, sizeof(approve_ring));

  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }
  memset(&cancel_modal, 0, sizeof(cancel_modal));

  if (scanning_dots.is_initialized) {
    orbital_dots_animation_destroy(&scanning_dots);
  }
  memset(&scanning_dots, 0, sizeof(scanning_dots));

  // Reset all child pointers
  title_label = NULL;
  approve_button = NULL;
  scanning_container = NULL;
  scanning_message = NULL;
  scanning_orbital_parent = NULL;
  progress_message = NULL;
  current_page = PAGE_CONFIRMATION;
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
  memset(&back_button, 0, sizeof(back_button));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scanning_dots, 0, sizeof(scanning_dots));

  // Title
  title_label = lv_label_create(screen);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_CENTER, 0, TITLE_Y_OFFSET);
  lv_label_set_text(title_label, "FIRMWARE UPDATE");

  // Create appropriate page based on params
  uint32_t page = params ? params->page : (uint32_t)PAGE_CONFIRMATION;
  switch (page) {
    case PAGE_CONFIRMATION:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
    case PAGE_SCANNING:
      current_page = PAGE_SCANNING;
      create_scanning_page();
      break;
    case PAGE_IN_PROGRESS:
      current_page = PAGE_IN_PROGRESS;
      create_in_progress_page();
      break;
    default:
      current_page = PAGE_CONFIRMATION;
      create_confirmation_page(params);
      break;
  }
}
