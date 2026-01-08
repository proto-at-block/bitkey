#include "screen_money_movement.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "widgets/bottom_menu.h"
#include "widgets/page_indicator.h"

#include <stdio.h>
#include <string.h>

// Screen configuration - Address display
#define MAX_CHARS_PER_SCREEN 60
#define CHARS_PER_GROUP      4
#define GROUPS_PER_LINE      3
#define MAX_LINES            5
#define MAX_LABELS           80

// Layout configuration
#define TITLE_Y_OFFSET         50
#define ADDRESS_START_Y        -80
#define CHAR_WIDTH             24
#define BULLET_WIDTH           20
#define LINE_HEIGHT            36
#define AMOUNT_Y_OFFSET        -60
#define AMOUNT_USD_Y_OFFSET    -10
#define FEE_LABEL_Y_OFFSET     60
#define FEE_BTC_Y_OFFSET       80
#define FEE_USD_Y_OFFSET       105
#define VERIFY_BUTTON_Y_OFFSET -30
#define CANCEL_BUTTON_Y_OFFSET 40
#define BUTTON_SPACING         30
#define VERIFY_BUTTON_WIDTH    220
#define CANCEL_BUTTON_WIDTH    140
#define BUTTON_HEIGHT          60
#define BUTTON_RADIUS          30
#define BUTTON_BORDER_WIDTH    3

// Colors
#define COLOR_TITLE  0xADADAD
#define COLOR_USD    0xADADAD
#define COLOR_VERIFY 0x1CB843
#define COLOR_CANCEL 0xF84752

// Text content
#define TEXT_ELLIPSIS "..."

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_22)
#define FONT_ADDRESS (&cash_sans_mono_regular_24)
#define FONT_AMOUNT  (&cash_sans_mono_regular_34)
#define FONT_USD     (&cash_sans_mono_regular_28)
#define FONT_FEE     (&cash_sans_mono_regular_28)
#define FONT_BUTTON  (&cash_sans_mono_regular_24)

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static page_indicator_t page_indicator;

// Address page elements
static lv_obj_t* char_labels[MAX_LABELS] = {0};  // Holds all UI elements (chars, bullets, ellipsis)
static int label_count = 0;

// Amount page elements
static lv_obj_t* amount_btc_label = NULL;
static lv_obj_t* amount_usd_label = NULL;
static lv_obj_t* fee_btc_label = NULL;
static lv_obj_t* fee_usd_label = NULL;

// Confirm page elements
static lv_obj_t* verify_button = NULL;
static lv_obj_t* cancel_button = NULL;
static lv_obj_t* button_selector = NULL;
static bottom_menu_t menu_button;

// Helper function to clear all address labels
static void clear_address_labels(void) {
  for (int i = 0; i < label_count; i++) {
    if (char_labels[i]) {
      lv_obj_del(char_labels[i]);
      char_labels[i] = NULL;
    }
  }
  label_count = 0;
}

// Create address display with optional continuation markers for multi-page addresses
static void create_address_page(lv_obj_t* parent, const char* address, int page_num,
                                int total_pages) {
  if (!address || !parent) {
    return;
  }

  clear_address_labels();

  const int addr_len = strlen(address);
  int chars_per_page = MAX_CHARS_PER_SCREEN;

  // For multi-page addresses, pages overlap by 4 chars for continuity
  const int start_offset = page_num * (chars_per_page - 4);
  int char_index = start_offset;

  // Continuation markers show connection between pages
  const bool show_start_ellipsis = (page_num > 0);
  const bool show_end_ellipsis = (page_num < total_pages - 1);

  // Adjust for continuation
  if (show_end_ellipsis) {
    chars_per_page -= 4;  // Reserve space for "..." at end
  }

  int line = 0;
  int group_in_line = 0;

  // Calculate starting positions
  int total_width =
    (GROUPS_PER_LINE * CHARS_PER_GROUP * CHAR_WIDTH) + ((GROUPS_PER_LINE - 1) * BULLET_WIDTH);
  int start_x = -total_width / 2;

  // Show starting ellipsis if continuing
  if (show_start_ellipsis) {
    lv_obj_t* ellipsis = lv_label_create(parent);
    lv_label_set_text(ellipsis, TEXT_ELLIPSIS);
    lv_obj_set_style_text_color(ellipsis, lv_color_white(), 0);
    lv_obj_set_style_text_font(ellipsis, FONT_ADDRESS, 0);
    // Center the ellipsis in the first group position
    lv_obj_align(ellipsis, LV_ALIGN_CENTER, start_x + (CHARS_PER_GROUP * CHAR_WIDTH / 2),
                 ADDRESS_START_Y);
    char_labels[label_count++] = ellipsis;

    // The ellipsis replaces the first group position
    group_in_line = 1;  // Start after ellipsis group
  }

  // Display address characters
  // Note: We check char_index (actual chars) not label_count (which includes bullets)
  while (char_index < addr_len && char_index - start_offset < MAX_CHARS_PER_SCREEN) {
    // Check if we need to stop for end ellipsis
    if (show_end_ellipsis && (char_index >= start_offset + chars_per_page)) {
      break;
    }

    int y_pos = ADDRESS_START_Y + (line * LINE_HEIGHT);
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));

    // Create group of 4 characters
    int chars_in_group = 0;
    for (int i = 0; i < CHARS_PER_GROUP && char_index < addr_len; i++) {
      // Stop if we need space for end ellipsis
      if (show_end_ellipsis && char_index >= start_offset + chars_per_page) {
        break;
      }

      char char_str[2] = {address[char_index], '\0'};

      lv_obj_t* label = lv_label_create(parent);
      lv_label_set_text(label, char_str);
      lv_obj_set_style_text_color(label, lv_color_white(), 0);
      lv_obj_set_style_text_font(label, FONT_ADDRESS, 0);
      lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
      lv_obj_set_width(label, CHAR_WIDTH);
      lv_obj_align(label, LV_ALIGN_CENTER, x_pos + (i * CHAR_WIDTH) + (CHAR_WIDTH / 2), y_pos);

      char_labels[label_count++] = label;
      char_index++;
      chars_in_group++;
    }

    // Add bullet separator only if:
    // 1. We have a complete group (4 chars)
    // 2. There are more characters to display
    // 3. We're not at the end of the line
    // 4. We're not about to show ellipsis
    if (chars_in_group == CHARS_PER_GROUP && char_index < addr_len &&
        group_in_line < GROUPS_PER_LINE - 1 &&
        !(show_end_ellipsis && char_index >= start_offset + chars_per_page)) {
      lv_obj_t* bullet = lv_label_create(parent);
      lv_label_set_text(bullet, "•");
      lv_obj_set_style_text_color(bullet, lv_color_white(), 0);
      lv_obj_set_style_text_font(bullet, FONT_ADDRESS, 0);
      lv_obj_set_style_text_align(bullet, LV_TEXT_ALIGN_CENTER, 0);
      lv_obj_set_width(bullet, BULLET_WIDTH);
      lv_obj_align(bullet, LV_ALIGN_CENTER,
                   x_pos + (CHARS_PER_GROUP * CHAR_WIDTH) + (BULLET_WIDTH / 2), y_pos);

      char_labels[label_count++] = bullet;
    }

    group_in_line++;

    // Move to next line
    if (group_in_line >= GROUPS_PER_LINE) {
      group_in_line = 0;
      line++;
      if (line >= MAX_LINES) {
        break;
      }
    }
  }

  // Show ending ellipsis if more pages follow
  if (show_end_ellipsis) {
    // Position ellipsis at the end of current content
    int y_pos = ADDRESS_START_Y + (line * LINE_HEIGHT);
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));

    lv_obj_t* ellipsis = lv_label_create(parent);
    lv_label_set_text(ellipsis, TEXT_ELLIPSIS);
    lv_obj_set_style_text_color(ellipsis, lv_color_white(), 0);
    lv_obj_set_style_text_font(ellipsis, FONT_ADDRESS, 0);
    lv_obj_align(ellipsis, LV_ALIGN_CENTER, x_pos + (CHAR_WIDTH * 2), y_pos);
    char_labels[label_count++] = ellipsis;
  }
}

// Create amount page display
static void create_amount_page(lv_obj_t* parent, const fwpb_display_params_money_movement* params) {
  // BTC amount
  if (!amount_btc_label) {
    amount_btc_label = lv_label_create(parent);
    lv_obj_set_style_text_color(amount_btc_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(amount_btc_label, FONT_AMOUNT, 0);
    lv_obj_align(amount_btc_label, LV_ALIGN_CENTER, 0, AMOUNT_Y_OFFSET);
  }

  char btc_text[64];
  snprintf(btc_text, sizeof(btc_text), "%s%s", params->amount_sats,
           langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_BTC_SUFFIX));
  lv_label_set_text(amount_btc_label, btc_text);
  lv_obj_clear_flag(amount_btc_label, LV_OBJ_FLAG_HIDDEN);

  // USD amount
  if (!amount_usd_label) {
    amount_usd_label = lv_label_create(parent);
    lv_obj_set_style_text_color(amount_usd_label, lv_color_hex(COLOR_USD), 0);
    lv_obj_set_style_text_font(amount_usd_label, FONT_USD, 0);
    lv_obj_align(amount_usd_label, LV_ALIGN_CENTER, 0, AMOUNT_USD_Y_OFFSET);
  }
  lv_label_set_text(amount_usd_label, params->amount_usd);
  lv_obj_clear_flag(amount_usd_label, LV_OBJ_FLAG_HIDDEN);

  // BTC fee with "BTC Fee" suffix
  if (!fee_btc_label) {
    fee_btc_label = lv_label_create(parent);
    lv_obj_set_style_text_color(fee_btc_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(fee_btc_label, FONT_FEE, 0);
    lv_obj_align(fee_btc_label, LV_ALIGN_CENTER, 0, FEE_LABEL_Y_OFFSET);
  }

  char fee_btc_text[64];
  snprintf(fee_btc_text, sizeof(fee_btc_text), "%s%s", params->fee_sats,
           langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_BTC_FEE_SUFFIX));
  lv_label_set_text(fee_btc_label, fee_btc_text);
  lv_obj_clear_flag(fee_btc_label, LV_OBJ_FLAG_HIDDEN);

  // USD fee
  if (!fee_usd_label) {
    fee_usd_label = lv_label_create(parent);
    lv_obj_set_style_text_color(fee_usd_label, lv_color_hex(COLOR_USD), 0);
    lv_obj_set_style_text_font(fee_usd_label, FONT_FEE, 0);
    lv_obj_align(fee_usd_label, LV_ALIGN_CENTER, 0, FEE_USD_Y_OFFSET);
  }
  lv_label_set_text(fee_usd_label, params->fee_usd);
  lv_obj_clear_flag(fee_usd_label, LV_OBJ_FLAG_HIDDEN);
}

// Create confirm page with buttons
static void create_confirm_page(lv_obj_t* parent, uint8_t button_selection) {
  // Create verify button (top)
  if (!verify_button) {
    verify_button = lv_obj_create(parent);
    lv_obj_set_size(verify_button, VERIFY_BUTTON_WIDTH, BUTTON_HEIGHT);
    lv_obj_set_style_radius(verify_button, BUTTON_RADIUS, 0);
    lv_obj_set_style_bg_opa(verify_button, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_color(verify_button, lv_color_white(), 0);
    lv_obj_set_style_border_width(verify_button, 0, 0);  // No border by default
    lv_obj_align(verify_button, LV_ALIGN_CENTER, 0, VERIFY_BUTTON_Y_OFFSET);

    lv_obj_t* verify_label = lv_label_create(verify_button);
    lv_label_set_text(verify_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_VERIFY));
    lv_obj_set_style_text_color(verify_label, lv_color_hex(COLOR_VERIFY), 0);
    lv_obj_set_style_text_font(verify_label, FONT_BUTTON, 0);
    lv_obj_center(verify_label);
  }

  // Create cancel button (bottom)
  if (!cancel_button) {
    cancel_button = lv_obj_create(parent);
    lv_obj_set_size(cancel_button, CANCEL_BUTTON_WIDTH, BUTTON_HEIGHT);
    lv_obj_set_style_radius(cancel_button, BUTTON_RADIUS, 0);
    lv_obj_set_style_bg_opa(cancel_button, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_color(cancel_button, lv_color_white(), 0);
    lv_obj_set_style_border_width(cancel_button, 0, 0);  // No border by default
    lv_obj_align(cancel_button, LV_ALIGN_CENTER, 0, CANCEL_BUTTON_Y_OFFSET);

    lv_obj_t* cancel_label = lv_label_create(cancel_button);
    lv_label_set_text(cancel_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_CANCEL));
    lv_obj_set_style_text_color(cancel_label, lv_color_hex(COLOR_CANCEL), 0);
    lv_obj_set_style_text_font(cancel_label, FONT_BUTTON, 0);
    lv_obj_center(cancel_label);
  }

  // Update selection indicator - white pill outline moves between options
  // Navigation: Verify & Sign (0) ↕ Cancel (1) ↕ Menu (2)
  if (button_selection == 0) {
    lv_obj_set_style_border_width(verify_button, BUTTON_BORDER_WIDTH, 0);
    lv_obj_set_style_border_width(cancel_button, 0, 0);
    bottom_menu_set_highlight(&menu_button, false);
  } else if (button_selection == 1) {
    lv_obj_set_style_border_width(verify_button, 0, 0);
    lv_obj_set_style_border_width(cancel_button, BUTTON_BORDER_WIDTH, 0);
    bottom_menu_set_highlight(&menu_button, false);
  } else if (button_selection == 2) {
    lv_obj_set_style_border_width(verify_button, 0, 0);
    lv_obj_set_style_border_width(cancel_button, 0, 0);
    bottom_menu_set_highlight(&menu_button, true);
  }

  lv_obj_clear_flag(verify_button, LV_OBJ_FLAG_HIDDEN);
  lv_obj_clear_flag(cancel_button, LV_OBJ_FLAG_HIDDEN);

  // Show menu button on confirm page
  if (menu_button.container) {
    lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
  }
}

// Hide all page elements
static void hide_all_elements(void) {
  // Hide address elements
  clear_address_labels();

  // Hide amount elements
  if (amount_btc_label) {
    lv_obj_add_flag(amount_btc_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (amount_usd_label) {
    lv_obj_add_flag(amount_usd_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (fee_btc_label) {
    lv_obj_add_flag(fee_btc_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (fee_usd_label) {
    lv_obj_add_flag(fee_usd_label, LV_OBJ_FLAG_HIDDEN);
  }

  // Hide confirm elements
  if (verify_button) {
    lv_obj_add_flag(verify_button, LV_OBJ_FLAG_HIDDEN);
  }
  if (cancel_button) {
    lv_obj_add_flag(cancel_button, LV_OBJ_FLAG_HIDDEN);
  }
  if (menu_button.container) {
    lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
  }
}

lv_obj_t* screen_money_movement_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_money_movement* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_money_movement_tag) {
    params = &show_screen->params.money_movement;
  }

  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Create title label
  title_label = lv_label_create(screen);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Initialize page indicator (will be configured based on address length)
  memset(&page_indicator, 0, sizeof(page_indicator_t));

  // Create menu button (initially hidden, no circle)
  memset(&menu_button, 0, sizeof(bottom_menu_t));
  bottom_menu_create(screen, &menu_button, false);  // false = no circle initially
  lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);

  // Show initial content if params provided
  if (params) {
    screen_money_movement_update(ctx);
  }

  return screen;
}

void screen_money_movement_destroy(void) {
  if (!screen) {
    return;
  }

  page_indicator_destroy(&page_indicator);
  memset(&page_indicator, 0, sizeof(page_indicator));

  // Clean up menu button
  if (menu_button.container) {
    bottom_menu_destroy(&menu_button);
  }

  lv_obj_del(screen);
  screen = NULL;
  title_label = NULL;

  // Reset all static variables
  label_count = 0;
  memset(char_labels, 0, sizeof(char_labels));
  amount_btc_label = NULL;
  amount_usd_label = NULL;
  fee_btc_label = NULL;
  fee_usd_label = NULL;
  verify_button = NULL;
  cancel_button = NULL;
  button_selector = NULL;
  memset(&menu_button, 0, sizeof(bottom_menu_t));
}

void screen_money_movement_update(void* ctx) {
  if (!screen) {
    screen_money_movement_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_money_movement_tag) {
    return;
  }

  const fwpb_display_params_money_movement* params = &show_screen->params.money_movement;

  hide_all_elements();

  // Initialize or recreate page indicator if total pages changed
  if (params->total_pages > 0) {
    if (!page_indicator.is_initialized) {
      page_indicator_create(screen, &page_indicator, params->total_pages);
    } else if ((uint32_t)page_indicator.total_pages != params->total_pages) {
      // Total pages changed, recreate the indicator
      page_indicator_destroy(&page_indicator);
      page_indicator_create(screen, &page_indicator, params->total_pages);
    }
  }

  if (params->step ==
      fwpb_display_params_money_movement_display_params_money_movement_step_ADDRESS) {
    // Address page(s) - always show ADDRESS title
    lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_ADDRESS));

    // Calculate number of address pages needed for display
    int addr_len = strlen(params->address);
    int chars_per_page = MAX_CHARS_PER_SCREEN;
    int total_address_pages;

    if (addr_len <= chars_per_page) {
      // Fits on single page - no ellipsis needed
      total_address_pages = 1;
    } else {
      // Multi-page - account for continuation overlap
      total_address_pages = (addr_len + chars_per_page - 5) / (chars_per_page - 4);
      if (total_address_pages < 1) {
        total_address_pages = 1;
      }
    }

    // Create address display
    const uint32_t current_address_page = params->current_address_page;
    create_address_page(screen, params->address, current_address_page, total_address_pages);

    // Update page indicator with current position from controller
    if (page_indicator.is_initialized) {
      page_indicator_update(&page_indicator, params->current_page_index);
    }

  } else if (params->step ==
             fwpb_display_params_money_movement_display_params_money_movement_step_AMOUNT) {
    // Amount page (only shown in send flow)
    if (!params->is_receive_flow) {
      lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_AMOUNT));
      create_amount_page(screen, params);
    }

    // Update page indicator with current position from controller
    if (page_indicator.is_initialized) {
      page_indicator_update(&page_indicator, params->current_page_index);
    }

  } else if (params->step ==
             fwpb_display_params_money_movement_display_params_money_movement_step_CONFIRM) {
    // Confirm page
    lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_CONFIRM));

    // Pass button selection directly to create_confirm_page
    create_confirm_page(screen, params->button_selection);

    // Update page indicator with current position from controller (last page)
    if (page_indicator.is_initialized) {
      page_indicator_update(&page_indicator, params->current_page_index);
    }
  }
}
