#include "screen_money_movement.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "ui.h"
#include "widgets/address_display.h"
#include "widgets/dot_ring.h"
#include "widgets/hold_cancel.h"
#include "widgets/orbital_dots_animation.h"
#include "widgets/top_back.h"
#include "widgets/top_menu.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

// Screen configuration
#define MAX_PAGES 5

// Layout configuration - Header
#define HEADER_HEIGHT           140
#define HEADER_PADDING_TOP      20
#define HEADER_PADDING_BOTTOM   20
#define HEADER_BUTTON_PADDING_H 24
#define HEADER_BUTTON_PADDING_V 16
#define TITLE_MARGIN_TOP        0
#define PILL_RADIUS             100
#define HEADER_ICON_SIZE        32

// Layout configuration - Content
#define CONTENT_START_Y            HEADER_HEIGHT
#define CHECK_BUTTON_SIZE          100
#define CHECK_BUTTON_BOTTOM_MARGIN 32
#define AMOUNT_TO_FEE_SPACING      8
// The header has empty space below the step indicator. To visually center content
// between the step indicator and check button, we shift content up by this amount.
#define CONTENT_CENTER_OFFSET 24

// Colors
#define COLOR_TITLE  0xADADAD
#define COLOR_USD    0xADADAD
#define COLOR_CANCEL 0xF84752
#define COLOR_RING   0xD1FB96
#define PILL_BG_OPA  51

// Confirmed page configuration
#define CONFIRMED_DELAY_MS 3000
#define CONFIRMED_LABEL_Y  60

// Scan page configuration (matches screen_scan.c)
#define SCAN_TEXT_CONTAINER_PADDING 12
#define SCAN_SCREEN_BRIGHTNESS      100

// Fonts
#define FONT_TITLE       (&cash_sans_mono_regular_26)
#define FONT_SCAN        (&cash_sans_mono_regular_36)
#define FONT_ADDRESS     (&cash_sans_mono_regular_28)
#define FONT_AMOUNT_SATS (&cash_sans_mono_regular_48)
#define FONT_AMOUNT_BTC  (&cash_sans_mono_regular_26)
#define FONT_FEE         (&cash_sans_mono_regular_26)
#define FONT_BUTTON      (&cash_sans_mono_regular_26)

// External image declarations
extern const lv_img_dsc_t check;

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* header = NULL;
static lv_obj_t* header_title = NULL;
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* page_containers[MAX_PAGES] = {0};
static int num_pages = 0;
static int current_page_index = 0;

// Top menu button (in header)
static top_menu_t menu_button;

// Cached params for gesture handling
static fwpb_display_params_money_movement cached_params;

// Dot ring widget for approve action
static dot_ring_t approve_ring;

// Scan page orbital dots animation
static orbital_dots_animation_t scan_page_orbital;

// Cancel modal
static hold_cancel_t cancel_modal;

// Address display widget (for multi-page addresses)
static address_display_t address_widget;

// Confirmed page elements
static lv_obj_t* confirmed_checkmark = NULL;
static lv_obj_t* confirmed_label = NULL;
static lv_timer_t* confirmed_timer = NULL;
static bool showing_confirmed_page = false;

// Forward declarations
static void check_button_event_handler(lv_event_t* e);
static void menu_button_custom_handler(lv_event_t* e);
static void on_approve_complete(void* user_data);
static void on_cancel_complete(void* user_data);
static void on_cancel_dismiss(void* user_data);
static void scroll_to_page(int page_index, bool animate);
static void update_step_indicator(int current, int total);
static void create_page_content(int page_index);
static lv_obj_t* create_check_button(lv_obj_t* parent);
static void show_confirmed_page(void);
static void hide_confirmed_page(void);
static void confirmed_timer_cb(lv_timer_t* timer);

// Helper to create check button (reusable across address/amount pages)
static lv_obj_t* create_check_button(lv_obj_t* parent) {
  lv_obj_t* check_button = lv_obj_create(parent);
  if (!check_button) {
    return NULL;
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

  // Check icon
  lv_obj_t* check_icon = lv_img_create(check_button);
  if (check_icon) {
    lv_img_set_src(check_icon, &check);
    lv_obj_center(check_icon);
  }

  return check_button;
}

static void create_amount_page(lv_obj_t* parent, const fwpb_display_params_money_movement* params) {
  // Create a content container to hold amount and fee elements
  // This will be centered in the available space between top and check button
  lv_obj_t* content_container = lv_obj_create(parent);
  if (!content_container) {
    return;
  }
  lv_obj_set_size(content_container, LV_PCT(100), LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(content_container, 0, 0);
  lv_obj_clear_flag(content_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_set_layout(content_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(content_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(content_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(content_container, AMOUNT_TO_FEE_SPACING, 0);

  // "AMOUNT" title
  lv_obj_t* amount_title = lv_label_create(content_container);
  if (!amount_title) {
    return;
  }
  lv_obj_set_style_text_color(amount_title, lv_color_hex(COLOR_USD), 0);
  lv_obj_set_style_text_font(amount_title, FONT_TITLE, 0);
  lv_label_set_text(amount_title, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_AMOUNT));

  // Amount row: sats value + BTC suffix
  lv_obj_t* amount_row = lv_obj_create(content_container);
  if (!amount_row) {
    return;
  }
  lv_obj_set_size(amount_row, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(amount_row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(amount_row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(amount_row, 0, 0);
  lv_obj_set_layout(amount_row, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(amount_row, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(amount_row, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_END, LV_FLEX_ALIGN_END);
  lv_obj_set_style_pad_column(amount_row, 4, 0);
  lv_obj_clear_flag(amount_row, LV_OBJ_FLAG_SCROLLABLE);

  // Sats value
  lv_obj_t* sats_label = lv_label_create(amount_row);
  if (!sats_label) {
    return;
  }
  lv_obj_set_style_text_color(sats_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(sats_label, FONT_AMOUNT_SATS, 0);
  lv_label_set_text(sats_label, params->amount_sats);

  // BTC suffix
  lv_obj_t* btc_suffix = lv_label_create(amount_row);
  if (!btc_suffix) {
    return;
  }
  lv_obj_set_style_text_color(btc_suffix, lv_color_white(), 0);
  lv_obj_set_style_text_font(btc_suffix, FONT_AMOUNT_BTC, 0);
  lv_obj_set_flex_grow(btc_suffix, 0);
  lv_obj_set_style_align(btc_suffix, LV_ALIGN_BOTTOM_LEFT, 0);
  lv_label_set_text(btc_suffix, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_BTC_SUFFIX));

  // Fee row: "FEE" label + fee sats
  lv_obj_t* fee_row = lv_obj_create(content_container);
  if (!fee_row) {
    return;
  }
  lv_obj_set_size(fee_row, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(fee_row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(fee_row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(fee_row, 0, 0);
  lv_obj_set_layout(fee_row, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(fee_row, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(fee_row, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(fee_row, 8, 0);
  lv_obj_clear_flag(fee_row, LV_OBJ_FLAG_SCROLLABLE);

  // Fee label
  lv_obj_t* fee_title = lv_label_create(fee_row);
  if (!fee_title) {
    return;
  }
  lv_obj_set_style_text_color(fee_title, lv_color_hex(COLOR_USD), 0);
  lv_obj_set_style_text_font(fee_title, FONT_FEE, 0);
  lv_label_set_text(fee_title, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_FEE));

  // Fee value
  lv_obj_t* fee_value = lv_label_create(fee_row);
  if (!fee_value) {
    return;
  }
  lv_obj_set_style_text_color(fee_value, lv_color_white(), 0);
  lv_obj_set_style_text_font(fee_value, FONT_FEE, 0);

  char fee_btc_text[64];
  snprintf(fee_btc_text, sizeof(fee_btc_text), "%s%s", params->fee_sats,
           langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_BTC_SUFFIX));
  lv_label_set_text(fee_value, fee_btc_text);

  // Calculate available space and center the content container
  // Available space: from y=0 of page container to top of check button
  // Apply CONTENT_CENTER_OFFSET to shift up and visually center between step indicator and check
  // button
  lv_obj_update_layout(content_container);
  lv_coord_t parent_height = lv_obj_get_height(parent);
  lv_coord_t check_button_top = parent_height - CHECK_BUTTON_BOTTOM_MARGIN - CHECK_BUTTON_SIZE;
  lv_coord_t content_height = lv_obj_get_height(content_container);
  lv_coord_t center_y = (check_button_top - content_height) / 2 - CONTENT_CENTER_OFFSET;
  lv_obj_align(content_container, LV_ALIGN_TOP_MID, 0, center_y);

  // Check button
  create_check_button(parent);
}

// Create scan page with same styling as screen_scan.c
static void create_scan_page(lv_obj_t* parent) {
  (void)parent;  // Scan page content is created on screen, not parent

  // Hide header on scan page
  if (header) {
    lv_obj_set_style_bg_opa(header, LV_OPA_TRANSP, 0);
  }
  if (header_title) {
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  }

  // Initialize and create orbital dots animation (matches screen_scan.c)
  memset(&scan_page_orbital, 0, sizeof(orbital_dots_animation_t));
  orbital_dots_animation_create(screen, &scan_page_orbital);

  // Title with black background box (matches screen_scan.c styling)
  lv_obj_t* text_container = lv_obj_create(screen);
  if (!text_container) {
    return;
  }
  lv_obj_set_style_bg_color(text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_all(text_container, SCAN_TEXT_CONTAINER_PADDING, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* title_label = lv_label_create(text_container);
  if (!title_label) {
    return;
  }
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_SCAN_TAP));
  lv_obj_set_style_text_color(title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(title_label, FONT_SCAN, 0);
  lv_obj_center(title_label);

  // Size container to fit the text
  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Set brightness to full (matches screen_scan.c)
  ui_set_local_brightness(SCAN_SCREEN_BRIGHTNESS);

  // Start orbital dots animation
  orbital_dots_animation_start(&scan_page_orbital);

  // Move menu button to foreground
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_move_foreground(menu_button.container);
  }
}

static void update_step_indicator(int current, int total) {
  if (!header_title) {
    return;
  }

  char step_text[16];
  snprintf(step_text, sizeof(step_text), "%d of %d", current + 1, total);
  lv_label_set_text(header_title, step_text);
}

static void scroll_to_page(int page_index, bool animate) {
  if (!scroll_container || page_index < 0 || page_index >= num_pages) {
    return;
  }

  current_page_index = page_index;
  lv_coord_t scroll_x = page_index * LV_HOR_RES;

  if (animate) {
    lv_obj_scroll_to_x(scroll_container, scroll_x, LV_ANIM_ON);
  } else {
    lv_obj_scroll_to_x(scroll_container, scroll_x, LV_ANIM_OFF);
  }

  update_step_indicator(current_page_index, num_pages);
}

static void on_approve_complete(void* user_data) {
  (void)user_data;

  // Check if we're on the last address page (page before scan page)
  // Scan page is always the last page (num_pages - 1)
  bool is_on_last_address_page = (current_page_index == num_pages - 2);

  if (is_on_last_address_page) {
    // On last address page - show confirmed page, then navigate to scan
    // The ring is already filled from the hold animation - just keep it visible
    show_confirmed_page();
  } else {
    // Not on last address page - stop and hide ring, navigate to next page
    dot_ring_stop(&approve_ring);
    dot_ring_hide(&approve_ring);
    int next_page = current_page_index + 1;
    if (lv_obj_get_child_cnt(page_containers[next_page]) == 0) {
      create_page_content(next_page);
    }
    scroll_to_page(next_page, true);
  }
}

static void menu_button_custom_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    hold_cancel_show(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL);
  }
}

static void on_cancel_complete(void* user_data) {
  (void)user_data;
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL, 0);
}

static void on_cancel_dismiss(void* user_data) {
  (void)user_data;
  hold_cancel_hide(&cancel_modal);
  update_step_indicator(current_page_index, num_pages);
}

static void show_confirmed_page(void) {
  showing_confirmed_page = true;

  // Hide header and step indicator
  if (header) {
    lv_obj_set_style_bg_opa(header, LV_OPA_TRANSP, 0);
  }
  if (header_title) {
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  }

  // Hide scroll container (address/amount pages)
  if (scroll_container) {
    lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_HIDDEN);
  }

  // Hide menu button during confirmed page
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
  }

  // The dot_ring is already visible and filled from the hold animation
  // Just ensure it stays visible (it should already be at 100% green)

  // Create checkmark icon centered on screen
  confirmed_checkmark = lv_img_create(screen);
  if (confirmed_checkmark) {
    lv_img_set_src(confirmed_checkmark, &check);
    lv_obj_set_style_img_recolor(confirmed_checkmark, lv_color_hex(COLOR_RING), 0);
    lv_obj_set_style_img_recolor_opa(confirmed_checkmark, LV_OPA_COVER, 0);
    lv_obj_align(confirmed_checkmark, LV_ALIGN_CENTER, 0, -20);
  }

  // Create "CONFIRMED" label below checkmark
  confirmed_label = lv_label_create(screen);
  if (confirmed_label) {
    lv_label_set_text(confirmed_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_CONFIRMED));
    lv_obj_set_style_text_color(confirmed_label, lv_color_hex(COLOR_RING), 0);
    lv_obj_set_style_text_font(confirmed_label, FONT_TITLE, 0);
    lv_obj_align(confirmed_label, LV_ALIGN_CENTER, 0, CONFIRMED_LABEL_Y);
  }

  // Start timer to transition to scan page after delay
  confirmed_timer = lv_timer_create(confirmed_timer_cb, CONFIRMED_DELAY_MS, NULL);
  if (confirmed_timer) {
    lv_timer_set_repeat_count(confirmed_timer, 1);
  }
}

static void hide_confirmed_page(void) {
  // Hide the dot ring
  dot_ring_hide(&approve_ring);

  // Delete confirmed page elements
  if (confirmed_checkmark) {
    lv_obj_del(confirmed_checkmark);
    confirmed_checkmark = NULL;
  }
  if (confirmed_label) {
    lv_obj_del(confirmed_label);
    confirmed_label = NULL;
  }
  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }
}

static void confirmed_timer_cb(lv_timer_t* timer) {
  (void)timer;
  confirmed_timer = NULL;

  // Hide confirmed page elements
  hide_confirmed_page();

  // Free memory before scan page (orbital dots need memory)
  dot_ring_destroy(&approve_ring);
  address_display_destroy(&address_widget);

  // Navigate to scan page (final page)
  int scan_page = num_pages - 1;
  if (lv_obj_get_child_cnt(page_containers[scan_page]) == 0) {
    create_page_content(scan_page);
  }

  // Show scroll container again for scan page
  if (scroll_container) {
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_HIDDEN);
  }

  // Show menu button again (was hidden during confirmed page)
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    lv_obj_move_foreground(menu_button.container);
  }

  scroll_to_page(scan_page, false);
}

static void check_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  lv_obj_t* check_button = lv_event_get_target(e);

  if (code == LV_EVENT_PRESSED) {
    // Change step text to "HOLD" with green color
    if (header_title) {
      lv_label_set_text(header_title, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_HOLD));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
    }

    // Change check button background to green and icon to black
    if (check_button) {
      lv_obj_set_style_bg_color(check_button, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_bg_opa(check_button, LV_OPA_COVER, 0);
      // Recolor the check icon to black
      lv_obj_t* check_icon = lv_obj_get_child(check_button, 0);
      if (check_icon) {
        lv_obj_set_style_img_recolor(check_icon, lv_color_black(), 0);
        lv_obj_set_style_img_recolor_opa(check_icon, LV_OPA_COVER, 0);
      }
    }

    // Hide menu button while holding
    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }

    dot_ring_show(&approve_ring);
    dot_ring_animate_fill(&approve_ring, 100, 2000, DOT_RING_COLOR_GREEN, DOT_RING_FILL_SPLIT,
                          on_approve_complete, NULL);

  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    // Ignore release if we're showing the confirmed page (animation completed successfully)
    if (showing_confirmed_page) {
      return;
    }

    // Stop animation and restore step indicator
    dot_ring_stop(&approve_ring);
    dot_ring_hide(&approve_ring);
    update_step_indicator(current_page_index, num_pages);
    if (header_title) {
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
    }

    // Restore check button background to white and icon to original
    if (check_button) {
      lv_obj_set_style_bg_color(check_button, lv_color_white(), 0);
      lv_obj_set_style_bg_opa(check_button, PILL_BG_OPA, 0);
      // Restore check icon color
      lv_obj_t* check_icon = lv_obj_get_child(check_button, 0);
      if (check_icon) {
        lv_obj_set_style_img_recolor_opa(check_icon, LV_OPA_TRANSP, 0);
      }
    }

    // Show menu button again
    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }
  }
}

lv_obj_t* screen_money_movement_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_money_movement* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_money_movement_tag) {
    params = &show_screen->params.money_movement;
  }

  // Create screen
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Create header
  header = lv_obj_create(screen);
  if (!header) {
    return NULL;
  }
  lv_obj_set_size(header, LV_PCT(100), HEADER_HEIGHT);
  lv_obj_set_pos(header, 0, 0);
  lv_obj_set_style_bg_color(header, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(header, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(header, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(header, 0, 0);
  lv_obj_set_style_pad_top(header, HEADER_PADDING_TOP, 0);
  lv_obj_set_style_pad_bottom(header, HEADER_PADDING_BOTTOM, 0);
  lv_obj_clear_flag(header, LV_OBJ_FLAG_SCROLLABLE);

  // Step indicator label (page counter)
  header_title = lv_label_create(header);
  if (!header_title) {
    screen_money_movement_destroy();
    return NULL;
  }
  lv_obj_align(header_title, LV_ALIGN_TOP_MID, 0, HEADER_PADDING_TOP + 44 + TITLE_MARGIN_TOP);
  lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
  lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
  lv_obj_set_style_text_font(header_title, FONT_TITLE, 0);

  // Create scroll container for pages
  scroll_container = lv_obj_create(screen);
  if (!scroll_container) {
    return NULL;
  }
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES - HEADER_HEIGHT);
  lv_obj_set_pos(scroll_container, 0, HEADER_HEIGHT);
  lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(scroll_container, 0, 0);
  lv_obj_set_scroll_dir(scroll_container, LV_DIR_NONE);
  lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
  lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);

  // Create widgets
  memset(&approve_ring, 0, sizeof(dot_ring_t));
  dot_ring_create(screen, &approve_ring);

  memset(&cancel_modal, 0, sizeof(hold_cancel_t));
  hold_cancel_create(screen, &cancel_modal);

  if (params) {
    // Cache params
    memcpy(&cached_params, params, sizeof(cached_params));

    // Initialize address widget
    address_display_init(&address_widget, params->address);
    int total_address_pages = address_display_get_page_count(&address_widget);

    // Calculate total pages: Amount (if send) -> Address page(s) -> Scan
    int total_pages = total_address_pages + 1;  // +1 for scan page
    if (!params->is_receive_flow) {
      total_pages += 1;  // Add amount page for send flow
    }
    num_pages = total_pages;

    // Create page containers
    for (int i = 0; i < total_pages && i < MAX_PAGES; i++) {
      page_containers[i] = lv_obj_create(scroll_container);
      if (!page_containers[i]) {
        return NULL;
      }
      lv_obj_set_size(page_containers[i], LV_HOR_RES, LV_VER_RES - HEADER_HEIGHT);
      lv_obj_set_style_bg_opa(page_containers[i], LV_OPA_TRANSP, 0);
      lv_obj_set_style_border_opa(page_containers[i], LV_OPA_TRANSP, 0);
      lv_obj_set_style_pad_all(page_containers[i], 0, 0);
      lv_obj_clear_flag(page_containers[i], LV_OBJ_FLAG_SCROLLABLE);
    }

    // Create first page content (lazy load others)
    create_page_content(0);
    scroll_to_page(0, false);
  }

  // Create menu button as child of screen (not header) so it's 20px from top like scan screen
  memset(&menu_button, 0, sizeof(top_menu_t));
  top_menu_create(screen, &menu_button, menu_button_custom_handler);

  return screen;
}

static void create_page_content(int page_index) {
  if (page_index < 0 || page_index >= num_pages || !page_containers[page_index]) {
    return;
  }

  int total_address_pages = address_display_get_page_count(&address_widget);
  int amount_page_count = cached_params.is_receive_flow ? 0 : 1;
  int first_address_page = amount_page_count;
  int first_scan_page = amount_page_count + total_address_pages;

  if (page_index < first_address_page) {
    // Amount page
    create_amount_page(page_containers[page_index], &cached_params);
  } else if (page_index < first_scan_page) {
    // Address page
    int addr_page_num = page_index - first_address_page;
    address_display_create_page(page_containers[page_index], &address_widget, addr_page_num);
    create_check_button(page_containers[page_index]);
  } else {
    // Scan page (final page)
    create_scan_page(page_containers[page_index]);
  }
}

void screen_money_movement_destroy(void) {
  if (!screen) {
    return;
  }

  // Clean up confirmed page timer first (before deleting screen)
  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }

  // Stop animations/timers before deleting screen
  // orbital_dots has its own timer that must be stopped
  orbital_dots_animation_stop(&scan_page_orbital);

  // Stop dot_ring animations (they use LVGL animations, not timers)
  dot_ring_stop(&approve_ring);

  // Stop hold_cancel's internal dot_ring animation
  if (cancel_modal.is_showing) {
    dot_ring_stop(&cancel_modal.ring);
  }

  // Destroy top_menu widget (has no timers/animations, just clears state)
  if (menu_button.is_initialized) {
    top_menu_destroy(&menu_button);
  }

  // Delete screen - this automatically deletes all children.
  // Do NOT call address_display_destroy(), dot_ring_destroy(), hold_cancel_destroy(),
  // or orbital_dots_animation_destroy() here - they would call lv_obj_del() on
  // lv_obj_t children that were already deleted when the screen was deleted.
  lv_obj_del(screen);
  screen = NULL;
  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  confirmed_checkmark = NULL;
  confirmed_label = NULL;
  showing_confirmed_page = false;

  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;

  // Clear all widget state (objects already deleted with screen)
  memset(&menu_button, 0, sizeof(top_menu_t));
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&address_widget, 0, sizeof(address_widget));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_page_orbital, 0, sizeof(scan_page_orbital));
}

// Handles screen re-entry safely by stopping all animations before recreation.
void screen_money_movement_update(void* ctx) {
  // First-time init
  if (!screen) {
    screen_money_movement_init(ctx);
    return;
  }

  // Validate parameters
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_money_movement_tag) {
    return;
  }

  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }
  orbital_dots_animation_stop(&scan_page_orbital);
  dot_ring_stop(&approve_ring);
  if (cancel_modal.is_showing) {
    dot_ring_stop(&cancel_modal.ring);
  }

  // Preserve old screen for async deletion
  lv_obj_t* old_screen = screen;

  // Clear all screen state
  screen = NULL;
  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  confirmed_checkmark = NULL;
  confirmed_label = NULL;
  showing_confirmed_page = false;
  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;

  // Clear widget state
  memset(&menu_button, 0, sizeof(menu_button));
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&address_widget, 0, sizeof(address_widget));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_page_orbital, 0, sizeof(scan_page_orbital));

  // Create and display new screen
  lv_obj_t* new_screen = screen_money_movement_init(ctx);
  if (new_screen) {
    lv_scr_load(new_screen);

    // Delete old screen asynchronously to avoid callback conflicts
    if (old_screen) {
      lv_obj_del_async(old_screen);
    }
  }
}
