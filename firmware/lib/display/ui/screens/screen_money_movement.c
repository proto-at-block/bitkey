#include "screen_money_movement.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "ui.h"
#include "widgets/address_display.h"
#include "widgets/approval_button.h"
#include "widgets/dot_ring.h"
#include "widgets/hold_cancel.h"
#include "widgets/nfc_dots_animation.h"
#include "widgets/scroll_arc_indicator.h"
#include "widgets/top_back.h"
#include "widgets/top_menu.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

// Screen configuration
#define MAX_PAGES 5

// Layout configuration - Header
#define HEADER_HEIGHT         140
#define HEADER_PADDING_TOP    20
#define HEADER_PADDING_BOTTOM 20
#define TITLE_MARGIN_TOP      0

// Layout configuration - Receive flow
#define RECEIVE_HEADER_HEIGHT            88
#define RECEIVE_SCROLL_CONTAINER_PAD     24
#define RECEIVE_SCROLL_CONTAINER_TOP_PAD 52
#define RECEIVE_SCROLL_BOTTOM_PAD        100
#define RECEIVE_SECTION_SPACING          48
#define RECEIVE_SUBSECTION_SPACING       12
#define RECEIVE_TEXT_MAX_WIDTH           340
#define RECEIVE_SCREEN_BRIGHTNESS        100

// Layout configuration - Content
#define AMOUNT_SECTION_SPACING 16
#define LABEL_TO_VALUE_SPACING 4
#define CONTENT_CENTER_NUDGE_Y 8

// Colors
#define COLOR_USD            0xADADAD
#define COLOR_RING           APPROVAL_BUTTON_RING_COLOR
#define COLOR_SECTION_HEADER 0x808080

// Confirmed page configuration
#define HOLD_TO_CONFIRM_DURATION_MS 2000
#define CONFIRMED_DELAY_MS          2500
#define CONFIRMED_CHECKMARK_Y       -28
#define CONFIRMED_LABEL_Y           28

// Header prompt cycle configuration
#define STEP_DISPLAY_MS         6000
#define HOLD_DISPLAY_MS         1500
#define HEADER_FADE_DURATION_MS 160

// Scan page configuration (matches screen_scan.c)
#define SCAN_TEXT_CONTAINER_PADDING 12
#define SCAN_SCREEN_BRIGHTNESS      100

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_26)
#define FONT_SCAN    (&cash_sans_mono_regular_36)
#define FONT_ADDRESS (&cash_sans_mono_regular_28)
#define FONT_FEE     (&cash_sans_mono_regular_26)
#define FONT_VALUE   (&cash_sans_mono_regular_36)

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
static top_back_t back_button;

// Receive flow scroll indicator
static scroll_arc_indicator_t receive_scroll_indicator;

// Cached params for gesture handling
static fwpb_display_params_money_movement cached_params;

// Dot ring widget for approve action
static dot_ring_t approve_ring;

// Scan page NFC dots animation
static nfc_dots_animation_t scan_page_nfc_dots;

// Cancel modal
static hold_cancel_t cancel_modal;

// Address display widget (for multi-page addresses)
static address_display_t address_widget;

// Confirmed page elements
static lv_obj_t* confirmed_checkmark = NULL;
static lv_obj_t* confirmed_label = NULL;
static lv_obj_t* check_button_obj = NULL;
static lv_timer_t* confirmed_timer = NULL;
static bool showing_confirmed_page = false;
static int confirmed_next_page_index = -1;

typedef enum {
  HEADER_PROMPT_MODE_STEP = 0,
  HEADER_PROMPT_MODE_HOLD_TO_CONFIRM = 1,
  HEADER_PROMPT_MODE_KEEP_HOLDING = 2,
} header_prompt_mode_t;

static lv_timer_t* header_hint_timer = NULL;
static header_prompt_mode_t header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static header_prompt_mode_t pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static bool header_cycle_enabled = false;
static bool check_button_held = false;
static char step_indicator_text[32] = {0};

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
static void create_self_send_info_page(lv_obj_t* parent);
static void create_receive_content(void);
static lv_obj_t* create_receive_section_header(const char* text);
static lv_obj_t* create_receive_body_text(const char* text);
static lv_obj_t* create_receive_address_block(void);
static void refresh_confirmation_timeout(void);
static void show_confirmed_page(int next_page_index);
static void hide_confirmed_page(void);
static void trim_hidden_content_for_signing_handoff(void);
static void confirmed_timer_cb(lv_timer_t* timer);
static void stop_header_hint_cycle(void);
static void restart_header_hint_cycle(void);
static void header_hint_timer_cb(lv_timer_t* timer);
static void set_header_prompt_mode(header_prompt_mode_t mode, bool animate);
static void header_text_opa_anim_cb(void* var, int32_t value);
static void header_fade_out_ready_cb(lv_anim_t* anim);

// Helper to create check button (reusable across address/amount pages)
static lv_obj_t* create_check_button(lv_obj_t* parent) {
  check_button_obj = approval_button_create(parent, check_button_event_handler);
  return check_button_obj;
}

// Format a sats string as BTC into out_buf (e.g. "150000" → "0.00150000 BTC").
// Zero-pads to 9+ chars and inserts '.' 8 positions from the right.
static void format_sats_as_btc(const char* sats_str, size_t sats_max_len, char* out_buf,
                               size_t out_size, const char* btc_suffix) {
  size_t sats_len = strnlen(sats_str, sats_max_len);

  // Zero-pad to at least 9 chars (so we have at least "0.XXXXXXXX")
  char padded[32];
  memset(padded, '0', sizeof(padded));
  if (sats_len < 9) {
    size_t pad_count = 9 - sats_len;
    memcpy(padded + pad_count, sats_str, sats_len);
    padded[9] = '\0';
  } else {
    memcpy(padded, sats_str, sats_len);
    padded[sats_len] = '\0';
  }

  // Insert decimal point 8 positions from end
  size_t padded_len = strnlen(padded, sizeof(padded));
  size_t decimal_pos = padded_len - 8;
  char formatted[48];
  memcpy(formatted, padded, decimal_pos);
  formatted[decimal_pos] = '.';
  memcpy(formatted + decimal_pos + 1, padded + decimal_pos, 8);
  formatted[decimal_pos + 9] = '\0';

  snprintf(out_buf, out_size, "%s%s", formatted, btc_suffix);
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
  lv_obj_set_style_pad_row(content_container, AMOUNT_SECTION_SPACING, 0);

  // Format amounts based on btc_display_unit preference.
  // btc_display_unit == 0: satoshi → "₿150,000" (BIP 177 prefix)
  // btc_display_unit == 1: bitcoin → "0.00150000 BTC" (suffix)
  char amount_text[64];
  char fee_text[64];

  if (params->btc_display_unit == fwpb_display_btc_unit_DISPLAY_BTC_UNIT_BITCOIN) {
    const char* btc_suffix = langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_BTC_SUFFIX);
    format_sats_as_btc(params->amount_sats, sizeof(params->amount_sats), amount_text,
                       sizeof(amount_text), btc_suffix);
    format_sats_as_btc(params->fee_sats, sizeof(params->fee_sats), fee_text, sizeof(fee_text),
                       btc_suffix);
  } else {
    // Satoshi display (default): ₿ prefix + raw sats (BIP 177)
    const char* sats_prefix = langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_SATS_PREFIX);
    snprintf(amount_text, sizeof(amount_text), "%s%s", sats_prefix, params->amount_sats);
    snprintf(fee_text, sizeof(fee_text), "%s%s", sats_prefix, params->fee_sats);
  }

  // Amount section: label above value
  lv_obj_t* amount_section = lv_obj_create(content_container);
  if (!amount_section) {
    return;
  }
  lv_obj_set_size(amount_section, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(amount_section, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(amount_section, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(amount_section, 0, 0);
  lv_obj_set_layout(amount_section, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(amount_section, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(amount_section, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(amount_section, LABEL_TO_VALUE_SPACING, 0);
  lv_obj_clear_flag(amount_section, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* amount_title = lv_label_create(amount_section);
  if (!amount_title) {
    return;
  }
  lv_obj_set_style_text_color(amount_title, lv_color_hex(COLOR_USD), 0);
  lv_obj_set_style_text_font(amount_title, FONT_FEE, 0);
  lv_label_set_text(amount_title, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_AMOUNT));

  lv_obj_t* amount_value = lv_label_create(amount_section);
  if (!amount_value) {
    return;
  }
  lv_obj_set_style_text_color(amount_value, lv_color_white(), 0);
  lv_obj_set_style_text_font(amount_value, FONT_VALUE, 0);
  lv_label_set_text(amount_value, amount_text);

  // Fee section: label above value
  lv_obj_t* fee_section = lv_obj_create(content_container);
  if (!fee_section) {
    return;
  }
  lv_obj_set_size(fee_section, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(fee_section, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(fee_section, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(fee_section, 0, 0);
  lv_obj_set_layout(fee_section, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(fee_section, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(fee_section, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(fee_section, LABEL_TO_VALUE_SPACING, 0);
  lv_obj_clear_flag(fee_section, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* fee_title = lv_label_create(fee_section);
  if (!fee_title) {
    return;
  }
  lv_obj_set_style_text_color(fee_title, lv_color_hex(COLOR_USD), 0);
  lv_obj_set_style_text_font(fee_title, FONT_FEE, 0);
  lv_label_set_text(fee_title, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_FEE));

  lv_obj_t* fee_value = lv_label_create(fee_section);
  if (!fee_value) {
    return;
  }
  lv_obj_set_style_text_color(fee_value, lv_color_white(), 0);
  lv_obj_set_style_text_font(fee_value, FONT_VALUE, 0);
  lv_label_set_text(fee_value, fee_text);

  // Center content between the step/menu area and the check button.
  // parent is the page container (starts at HEADER_HEIGHT), so convert the
  // step area bottom (screen coords) into this local coordinate space.
  lv_obj_update_layout(content_container);
  lv_coord_t parent_height = lv_obj_get_height(parent);
  lv_coord_t check_button_top =
    parent_height - APPROVAL_BUTTON_BOTTOM_MARGIN - APPROVAL_BUTTON_SIZE;
  lv_coord_t content_height = lv_obj_get_height(content_container);
  lv_coord_t step_area_bottom_screen =
    HEADER_PADDING_TOP + 44 + lv_font_get_line_height(FONT_TITLE) + TITLE_MARGIN_TOP;
  lv_coord_t step_area_bottom_local = step_area_bottom_screen - HEADER_HEIGHT;
  lv_coord_t center_y =
    ((check_button_top + step_area_bottom_local - content_height) / 2) + CONTENT_CENTER_NUDGE_Y;
  if (center_y < 0) {
    center_y = 0;
  }
  lv_obj_align(content_container, LV_ALIGN_TOP_MID, 0, center_y);

  // Check button
  create_check_button(parent);
}

// Self-send info page (page 1): "Self-send funds to your wallet"
static void create_self_send_info_page(lv_obj_t* parent) {
  lv_obj_t* info_label = lv_label_create(parent);
  if (!info_label) {
    return;
  }
  lv_label_set_text(info_label, langpack_get_string(LANGPACK_ID_SELF_SEND_INFO));
  lv_obj_set_style_text_color(info_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(info_label, FONT_ADDRESS, 0);
  lv_obj_set_style_text_align(info_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_width(info_label, LV_PCT(80));

  // Center vertically in available space
  lv_obj_update_layout(info_label);
  lv_coord_t parent_height = lv_obj_get_height(parent);
  lv_coord_t check_button_top =
    parent_height - APPROVAL_BUTTON_BOTTOM_MARGIN - APPROVAL_BUTTON_SIZE;
  lv_coord_t label_height = lv_obj_get_height(info_label);
  lv_coord_t step_area_bottom_screen =
    HEADER_PADDING_TOP + 44 + lv_font_get_line_height(FONT_TITLE) + TITLE_MARGIN_TOP;
  lv_coord_t step_area_bottom_local = step_area_bottom_screen - HEADER_HEIGHT;
  lv_coord_t center_y =
    ((check_button_top + step_area_bottom_local - label_height) / 2) + CONTENT_CENTER_NUDGE_Y;
  if (center_y < 0) {
    center_y = 0;
  }
  lv_obj_align(info_label, LV_ALIGN_TOP_MID, 0, center_y);

  create_check_button(parent);
}

static lv_obj_t* create_receive_section_header(const char* text) {
  lv_obj_t* label = lv_label_create(scroll_container);
  if (!label) {
    return NULL;
  }

  lv_label_set_text(label, text);
  lv_obj_set_style_text_font(label, FONT_ADDRESS, 0);
  lv_obj_set_style_text_color(label, lv_color_hex(COLOR_SECTION_HEADER), 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_margin_top(label, RECEIVE_SECTION_SPACING - RECEIVE_SUBSECTION_SPACING, 0);
  return label;
}

static lv_obj_t* create_receive_body_text(const char* text) {
  lv_obj_t* label = lv_label_create(scroll_container);
  if (!label) {
    return NULL;
  }

  lv_label_set_text(label, text);
  lv_obj_set_width(label, RECEIVE_TEXT_MAX_WIDTH);
  lv_label_set_long_mode(label, LV_LABEL_LONG_WRAP);
  lv_obj_set_style_text_font(label, FONT_ADDRESS, 0);
  lv_obj_set_style_text_color(label, lv_color_white(), 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_line_space(label, 8, 0);
  return label;
}

static lv_obj_t* create_receive_address_block(void) {
  lv_obj_t* container = lv_obj_create(scroll_container);
  if (!container) {
    return NULL;
  }

  lv_coord_t address_height = address_display_get_full_height(&address_widget);
  if (address_height <= 0) {
    address_height = lv_font_get_line_height(FONT_ADDRESS);
  }
  lv_obj_set_size(container, LV_PCT(100), address_height);
  lv_obj_set_style_bg_opa(container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(container, 0, 0);
  lv_obj_clear_flag(container, LV_OBJ_FLAG_SCROLLABLE);

  address_display_create_full(container, &address_widget);
  return container;
}

static void create_receive_content(void) {
  lv_obj_t* address_header =
    create_receive_section_header(langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_ADDRESS));
  if (address_header) {
    lv_obj_set_style_margin_top(address_header, 0, 0);
  }

  create_receive_address_block();
  create_receive_section_header(langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_HOW_TO_VERIFY));
  create_receive_body_text(langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_VERIFY_INSTRUCTIONS));
}

static bool should_cycle_header_hint(void) {
  if (!header_title || lv_obj_has_flag(header_title, LV_OBJ_FLAG_HIDDEN)) {
    return false;
  }

  if (!header_cycle_enabled || showing_confirmed_page || check_button_held) {
    return false;
  }

  // Receive flow is a passive scroll screen and never shows hold prompts.
  if (cached_params.flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE) {
    return false;
  }

  return (current_page_index < (num_pages - 1));
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
      if (check_button_obj) {
        approval_button_set_icon_highlight(check_button_obj, lv_color_hex(COLOR_RING));
      }
      break;

    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      lv_label_set_text(header_title, langpack_get_string(LANGPACK_ID_CONFIRMATION_KEEP_HOLDING));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
      break;

    case HEADER_PROMPT_MODE_STEP:
    default:
      lv_label_set_text(header_title, step_indicator_text);
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
      if (check_button_obj) {
        approval_button_clear_icon_highlight(check_button_obj);
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

  uint32_t initial_period =
    (header_prompt_mode == HEADER_PROMPT_MODE_STEP) ? STEP_DISPLAY_MS : HOLD_DISPLAY_MS;
  header_hint_timer = lv_timer_create(header_hint_timer_cb, initial_period, NULL);
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

  // Initialize and create NFC dots animation (matches screen_scan.c).
  memset(&scan_page_nfc_dots, 0, sizeof(scan_page_nfc_dots));
  scan_page_nfc_dots.highlight_color = lv_color_hex(COLOR_RING);
  nfc_dots_animation_create(screen, &scan_page_nfc_dots);

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
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_SCAN_CONFIRM));
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_RING), 0);
  lv_obj_set_style_text_font(title_label, FONT_SCAN, 0);
  lv_obj_center(title_label);

  // Size container to fit the text
  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Set brightness to full (matches screen_scan.c)
  ui_set_local_brightness(SCAN_SCREEN_BRIGHTNESS);

  // Start NFC dots animation
  nfc_dots_animation_start(&scan_page_nfc_dots);

  // Move menu button to foreground
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_move_foreground(menu_button.container);
  }
}

static void update_step_indicator(int current, int total) {
  if (!header_title) {
    return;
  }

  // total includes the scan page; only content pages should be counted.
  int content_pages = total - 1;
  if (content_pages <= 0 || current >= content_pages) {
    header_cycle_enabled = false;
    stop_header_hint_cycle();
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
    return;
  }

  lv_obj_clear_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  snprintf(step_indicator_text, sizeof(step_indicator_text), "%d OF %d", current + 1,
           content_pages);
  header_cycle_enabled = true;

  set_header_prompt_mode(HEADER_PROMPT_MODE_STEP, false);

  if (should_cycle_header_hint()) {
    restart_header_hint_cycle();
  } else {
    stop_header_hint_cycle();
  }
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

static void refresh_confirmation_timeout(void) {
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_PAGE_CONFIRMED, 0);
}

static void on_approve_complete(void* user_data) {
  (void)user_data;

  int next_page = current_page_index + 1;

  // Show confirmed interstitial after every successful hold.
  show_confirmed_page(next_page);
}

static hold_cancel_options_t cancel_followup_options(void) {
  return (hold_cancel_options_t){
    .followup_title = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_ON_YOUR_PHONE),
    .followup_text = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCEL_TRANSACTION_IN_APP),
  };
}

static void menu_button_custom_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    hold_cancel_options_t options = cancel_followup_options();

    stop_header_hint_cycle();
    hold_cancel_show_with_options(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL,
                                  &options);
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

static void show_confirmed_page(int next_page_index) {
  showing_confirmed_page = true;
  check_button_held = false;
  stop_header_hint_cycle();
  confirmed_next_page_index = next_page_index;

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
    lv_obj_align(confirmed_checkmark, LV_ALIGN_CENTER, 0, CONFIRMED_CHECKMARK_Y);
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
  showing_confirmed_page = false;
  confirmed_next_page_index = -1;

  // Hide the dot ring
  dot_ring_hide(&approve_ring);
  dot_ring_reset(&approve_ring);

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

static void trim_hidden_content_for_signing_handoff(void) {
  // The signing screen is initialized before this screen is destroyed, so free
  // the hidden money-movement widgets first to lower the peak LVGL heap usage.
  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }

  dot_ring_destroy(&approve_ring);

  if (scroll_container) {
    lv_obj_del(scroll_container);
    scroll_container = NULL;
  }

  if (header) {
    lv_obj_del(header);
    header = NULL;
    header_title = NULL;
  }

  if (menu_button.is_initialized) {
    top_menu_destroy(&menu_button);
  }

  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }

  check_button_obj = NULL;
  memset(&address_widget, 0, sizeof(address_widget));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
}

static void confirmed_timer_cb(lv_timer_t* timer) {
  (void)timer;
  confirmed_timer = NULL;
  int next_page = confirmed_next_page_index;
  bool moving_to_scan_page = (next_page == num_pages - 1);

  if (moving_to_scan_page) {
    // Keep the confirmed interstitial on screen, but shed the hidden widgets
    // before queueing APPROVE so the signing screen is built with a lower
    // peak LVGL heap footprint.
    trim_hidden_content_for_signing_handoff();
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
    return;
  }

  // Hide confirmed page elements
  hide_confirmed_page();

  // Show scroll container again after confirmed page.
  if (scroll_container) {
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_HIDDEN);
  }

  // Show menu button again (was hidden during confirmed page).
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    lv_obj_move_foreground(menu_button.container);
  }

  // Restore header elements for regular content pages.
  if (header) {
    lv_obj_set_style_bg_opa(header, LV_OPA_COVER, 0);
  }
  if (header_title) {
    lv_obj_clear_flag(header_title, LV_OBJ_FLAG_HIDDEN);
    lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
    lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
  }

  // Refresh again here so the newly visible page gets the full timeout budget.
  refresh_confirmation_timeout();

  if (next_page >= 0 && next_page < num_pages) {
    if (lv_obj_get_child_cnt(page_containers[next_page]) == 0) {
      create_page_content(next_page);
    }
    scroll_to_page(next_page, false);
  }
}

static void check_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  lv_obj_t* check_button = lv_event_get_target(e);

  if (code == LV_EVENT_PRESSED) {
    check_button_held = true;
    stop_header_hint_cycle();
    if (header_title) {
      lv_obj_clear_flag(header_title, LV_OBJ_FLAG_HIDDEN);
    }
    set_header_prompt_mode(HEADER_PROMPT_MODE_KEEP_HOLDING, false);

    // Refresh as soon as the user commits to the hold so a nearly expired
    // confirmation cannot time out mid-gesture.
    refresh_confirmation_timeout();

    // Change check button background to green and icon to black
    if (check_button) {
      approval_button_set_hold_state(check_button);
    }

    // Hide menu button while holding
    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }

    dot_ring_show(&approve_ring);
    dot_ring_animate_fill_from_current(&approve_ring, 100, HOLD_TO_CONFIRM_DURATION_MS,
                                       DOT_RING_COLOR_GREEN, DOT_RING_FILL_SPLIT,
                                       on_approve_complete, NULL);

  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    // Ignore release if we're showing the confirmed page (animation completed successfully)
    if (showing_confirmed_page) {
      return;
    }

    if (dot_ring_animate_release(&approve_ring, HOLD_TO_CONFIRM_DURATION_MS)) {
      return;
    }

    check_button_held = false;

    // Rewind the ring and restore the idle hint cycle immediately.
    update_step_indicator(current_page_index, num_pages);

    // Restore check button background to white and icon to original
    if (check_button) {
      approval_button_set_idle_state(check_button);
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
  bool is_receive = params && params->flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE;
  lv_coord_t header_height = is_receive ? RECEIVE_HEADER_HEIGHT : HEADER_HEIGHT;

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  header = lv_obj_create(screen);
  if (!header) {
    return NULL;
  }
  lv_obj_set_size(header, LV_PCT(100), header_height);
  lv_obj_set_pos(header, 0, 0);
  lv_obj_set_style_bg_color(header, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(header, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(header, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(header, 0, 0);
  lv_obj_clear_flag(header, LV_OBJ_FLAG_SCROLLABLE);
  if (is_receive) {
    lv_obj_clear_flag(header, LV_OBJ_FLAG_CLICKABLE);
  } else {
    lv_obj_set_style_pad_top(header, HEADER_PADDING_TOP, 0);
    lv_obj_set_style_pad_bottom(header, HEADER_PADDING_BOTTOM, 0);

    header_title = lv_label_create(header);
    if (!header_title) {
      screen_money_movement_destroy();
      return NULL;
    }
    lv_obj_align(header_title, LV_ALIGN_TOP_MID, 0, HEADER_PADDING_TOP + 44 + TITLE_MARGIN_TOP);
    lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
    lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
    lv_obj_set_style_text_font(header_title, FONT_TITLE, 0);
  }

  scroll_container = lv_obj_create(screen);
  if (!scroll_container) {
    return NULL;
  }
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES - header_height);
  lv_obj_set_pos(scroll_container, 0, header_height);
  lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
  if (is_receive) {
    lv_obj_set_style_pad_all(scroll_container, RECEIVE_SCROLL_CONTAINER_PAD, 0);
    lv_obj_set_style_pad_top(scroll_container, RECEIVE_SCROLL_CONTAINER_TOP_PAD, 0);
    lv_obj_set_style_pad_bottom(scroll_container, RECEIVE_SCROLL_BOTTOM_PAD, 0);
    lv_obj_set_scroll_dir(scroll_container, LV_DIR_VER);
    lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
    lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_SCROLL_MOMENTUM);
    lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_SCROLL_ELASTIC);
    lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
    lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_COLUMN);
    lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                          LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_row(scroll_container, RECEIVE_SUBSECTION_SPACING, 0);
  } else {
    lv_obj_set_style_pad_all(scroll_container, 0, 0);
    lv_obj_set_scroll_dir(scroll_container, LV_DIR_NONE);
    lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
    lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                          LV_FLEX_ALIGN_CENTER);

    memset(&approve_ring, 0, sizeof(dot_ring_t));
    dot_ring_create(screen, &approve_ring);

    memset(&cancel_modal, 0, sizeof(hold_cancel_t));
    hold_cancel_create(screen, &cancel_modal);
  }

  if (params) {
    memcpy(&cached_params, params, sizeof(cached_params));

    bool is_self_send = params->flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND;
    if (is_self_send) {
      num_pages = 3;
    } else if (is_receive) {
      address_display_init(&address_widget, params->address);
      num_pages = 1;
    } else {
      address_display_init(&address_widget, params->address);
      address_display_set_bottom_reserved(&address_widget,
                                          APPROVAL_BUTTON_SIZE + APPROVAL_BUTTON_BOTTOM_MARGIN);
      num_pages = address_display_get_page_count(&address_widget) + 2;
    }

    if (is_receive) {
      create_receive_content();
      scroll_arc_indicator_create(screen, scroll_container, &receive_scroll_indicator);
      lv_obj_update_layout(scroll_container);
      scroll_arc_indicator_update(&receive_scroll_indicator);
    } else {
      for (int i = 0; i < num_pages && i < MAX_PAGES; i++) {
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

      create_page_content(0);
      scroll_to_page(0, false);
    }
  }

  if (is_receive) {
    memset(&back_button, 0, sizeof(top_back_t));
    top_back_create(screen, &back_button, NULL);
    ui_set_local_brightness(RECEIVE_SCREEN_BRIGHTNESS);
  } else {
    memset(&menu_button, 0, sizeof(top_menu_t));
    top_menu_create(screen, &menu_button, menu_button_custom_handler);
  }

  return screen;
}

static void create_page_content(int page_index) {
  if (page_index < 0 || page_index >= num_pages || !page_containers[page_index]) {
    return;
  }

  bool is_self_send = cached_params.flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND;
  if (is_self_send) {
    if (page_index == 0) {
      create_self_send_info_page(page_containers[page_index]);
    } else if (page_index == 1) {
      create_amount_page(page_containers[page_index], &cached_params);
    } else {
      create_scan_page(page_containers[page_index]);
    }
    return;
  }

  int total_address_pages = address_display_get_page_count(&address_widget);
  int first_amount_page = total_address_pages;
  int first_scan_page = total_address_pages + 1;

  if (page_index < first_amount_page) {
    address_display_create_page(page_containers[page_index], &address_widget, page_index);
    create_check_button(page_containers[page_index]);
  } else if (page_index < first_scan_page) {
    create_amount_page(page_containers[page_index], &cached_params);
  } else {
    create_scan_page(page_containers[page_index]);
  }
}

void screen_money_movement_destroy(void) {
  if (!screen) {
    return;
  }

  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }
  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }

  nfc_dots_animation_stop(&scan_page_nfc_dots);
  if (approve_ring.is_initialized) {
    dot_ring_destroy(&approve_ring);
  }
  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }

  if (back_button.is_initialized) {
    top_back_destroy(&back_button);
  }
  if (menu_button.is_initialized) {
    top_menu_destroy(&menu_button);
  }

  scroll_arc_indicator_destroy(&receive_scroll_indicator);
  lv_obj_del(screen);
  screen = NULL;
  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  confirmed_checkmark = NULL;
  confirmed_label = NULL;
  showing_confirmed_page = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  header_cycle_enabled = false;
  check_button_held = false;
  memset(step_indicator_text, 0, sizeof(step_indicator_text));
  confirmed_next_page_index = -1;

  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;

  memset(&menu_button, 0, sizeof(menu_button));
  memset(&back_button, 0, sizeof(back_button));
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&address_widget, 0, sizeof(address_widget));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_page_nfc_dots, 0, sizeof(scan_page_nfc_dots));
}

// Handles screen re-entry safely by stopping all animations before recreation.
void screen_money_movement_update(void* ctx) {
  if (!screen) {
    screen_money_movement_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_money_movement_tag) {
    return;
  }

  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }
  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }
  nfc_dots_animation_stop(&scan_page_nfc_dots);
  if (approve_ring.is_initialized) {
    dot_ring_destroy(&approve_ring);
  }
  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }
  scroll_arc_indicator_destroy(&receive_scroll_indicator);

  lv_obj_t* old_screen = screen;

  screen = NULL;
  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  confirmed_checkmark = NULL;
  confirmed_label = NULL;
  showing_confirmed_page = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  header_cycle_enabled = false;
  check_button_held = false;
  memset(step_indicator_text, 0, sizeof(step_indicator_text));
  confirmed_next_page_index = -1;
  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;

  memset(&menu_button, 0, sizeof(menu_button));
  memset(&back_button, 0, sizeof(back_button));
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&address_widget, 0, sizeof(address_widget));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_page_nfc_dots, 0, sizeof(scan_page_nfc_dots));

  lv_obj_t* new_screen = screen_money_movement_init(ctx);
  if (new_screen) {
    lv_scr_load(new_screen);
    if (old_screen) {
      lv_obj_del_async(old_screen);
    }
  }
}

#if LV_USE_SNAPSHOT
static void snapshot_advance_lvgl_time(uint32_t elapsed_ms) {
  const uint32_t frame_step_ms = 16;
  uint32_t remaining_ms = elapsed_ms;

  while (remaining_ms > 0) {
    uint32_t delta_ms = remaining_ms > frame_step_ms ? frame_step_ms : remaining_ms;
    lv_tick_inc(delta_ms);
    lv_timer_handler();
    lv_refr_now(NULL);
    remaining_ms -= delta_ms;
  }
}

static uint32_t snapshot_hold_elapsed_ms(uint8_t percent, uint32_t full_duration_ms) {
  if (percent > 100) {
    percent = 100;
  }

  return (full_duration_ms * percent) / 100;
}

void screen_money_movement_snapshot_show_confirmed(void) {
  if (!screen || !approve_ring.is_initialized) {
    return;
  }

  // Match the real post-hold state before the confirmed interstitial appears.
  dot_ring_show(&approve_ring);
  dot_ring_set_percent(&approve_ring, 100, DOT_RING_COLOR_GREEN, DOT_RING_FILL_SPLIT);
  show_confirmed_page(current_page_index + 1);
}

void screen_money_movement_snapshot_show_cancel_followup(void) {
  if (!screen || !cancel_modal.is_initialized) {
    return;
  }

  hold_cancel_options_t options = cancel_followup_options();
  hold_cancel_snapshot_show_followup(&cancel_modal, &options);
}

void screen_money_movement_snapshot_start_hold_progress(uint8_t percent) {
  if (!screen || !approve_ring.is_initialized || !check_button_obj) {
    return;
  }

  lv_obj_send_event(check_button_obj, LV_EVENT_PRESSED, NULL);
  snapshot_advance_lvgl_time(snapshot_hold_elapsed_ms(percent, HOLD_TO_CONFIRM_DURATION_MS));
}

void screen_money_movement_snapshot_start_hold_reverse(uint8_t percent) {
  if (!screen || !approve_ring.is_initialized || !check_button_obj) {
    return;
  }

  lv_obj_send_event(check_button_obj, LV_EVENT_PRESSED, NULL);
  snapshot_advance_lvgl_time(snapshot_hold_elapsed_ms(percent, HOLD_TO_CONFIRM_DURATION_MS));
  lv_obj_send_event(check_button_obj, LV_EVENT_RELEASED, NULL);
}

void screen_money_movement_snapshot_start_cancel_reverse(uint8_t percent) {
  if (!screen || !cancel_modal.is_initialized || !menu_button.container) {
    return;
  }

  lv_obj_send_event(menu_button.container, LV_EVENT_CLICKED, NULL);

  hold_cancel_options_t options = cancel_followup_options();
  hold_cancel_snapshot_start_release_reverse(&cancel_modal, &options, percent);
}
#endif
