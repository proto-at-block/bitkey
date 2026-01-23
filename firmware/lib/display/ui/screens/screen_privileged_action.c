#include "screen_privileged_action.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "widgets/address_display.h"
#include "widgets/hold_cancel.h"
#include "widgets/hold_ring.h"
#include "widgets/orbital_dots_animation.h"
#include "widgets/top_menu.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

// Screen configuration
#define MAX_PAGES 5

// Layout configuration
#define HEADER_HEIGHT              140
#define TITLE_MARGIN_TOP           0
#define CHECK_BUTTON_SIZE          80
#define CHECK_BUTTON_BOTTOM_MARGIN 40
#define PILL_BG_OPA                51

// Colors
#define COLOR_TITLE  0xADADAD
#define COLOR_CANCEL 0xF84752

// Fonts
#define FONT_TITLE       (&cash_sans_mono_regular_24)
#define FONT_SCAN        (&cash_sans_mono_regular_36)
#define FONT_AMOUNT_SATS (&cash_sans_mono_regular_48)
#define FONT_TEXT        (&cash_sans_mono_regular_28)

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

// Cached params for rendering
static fwpb_display_params_privileged_action cached_params;

// Hold ring widget for approve action
static hold_ring_t approve_ring;

// Scan page orbital dots animation
static orbital_dots_animation_t scan_page_orbital;

// Cancel modal
static hold_cancel_t cancel_modal;

// Address display widget (for multi-page addresses/hashes)
static address_display_t address_widget;

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
static int calculate_total_pages(const fwpb_display_params_privileged_action* params);
static void fade_anim_cb(void* obj, int32_t value);

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

static int calculate_total_pages(const fwpb_display_params_privileged_action* params) {
  int content_pages = 0;

  switch (params->which_action) {
    case fwpb_display_params_privileged_action_confirm_address_tag: {
      address_display_t temp_widget;
      address_display_init(&temp_widget, params->action.confirm_address.address);
      content_pages = address_display_get_page_count(&temp_widget);
      break;
    }
    case fwpb_display_params_privileged_action_confirm_string_tag:
      content_pages = 1;
      break;
    case fwpb_display_params_privileged_action_fwup_tag:
      // Hash page + version page
      content_pages = 2;
      break;
    case fwpb_display_params_privileged_action_confirm_action_tag:
      content_pages = 1;
      break;
    default:
      content_pages = 1;
      break;
  }

  return content_pages + 1;
}

static void check_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSED) {
    hold_ring_start(&approve_ring, HOLD_RING_COLOR_GREEN, on_approve_complete, NULL);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    // Stop if released before completion
    hold_ring_stop(&approve_ring);
  }
}

static void on_approve_complete(void* user_data) {
  (void)user_data;

  if (current_page_index < num_pages - 1) {
    // Navigate to next page
    scroll_to_page(current_page_index + 1, true);
  } else {
    // Final page - send approve action
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
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
  hold_cancel_hide(&cancel_modal);
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL, 0);
}

static void on_cancel_dismiss(void* user_data) {
  (void)user_data;
  hold_cancel_hide(&cancel_modal);
  update_step_indicator(current_page_index, num_pages);
}

static void scroll_to_page(int page_index, bool animate) {
  if (page_index < 0 || page_index >= num_pages) {
    return;
  }

  if (!page_containers[page_index]) {
    return;
  }

  // Free memory before scan page (orbital dots need memory)
  if (page_index == num_pages - 1 && current_page_index != num_pages - 1) {
    hold_ring_destroy(&approve_ring);
    address_display_destroy(&address_widget);
  }

  // Create page content if needed
  create_page_content(page_index);
  lv_obj_scroll_to_view(page_containers[page_index], animate ? LV_ANIM_ON : LV_ANIM_OFF);

  current_page_index = page_index;
  update_step_indicator(current_page_index, num_pages);
}

static void update_step_indicator(int current, int total) {
  if (!header_title) {
    return;
  }

  char text[32];
  snprintf(text, sizeof(text), "%d of %d", current + 1, total);
  lv_label_set_text(header_title, text);
}

static void create_scan_page(lv_obj_t* parent) {
  if (!parent) {
    return;
  }

  // Hide header title to free memory for orbital dots
  if (header_title) {
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  }

  // Create orbital dots on screen (not parent) for full-screen centering
  memset(&scan_page_orbital, 0, sizeof(orbital_dots_animation_t));
  lv_obj_t* orbital_parent = orbital_dots_animation_create(screen, &scan_page_orbital);

  if (orbital_parent) {
    for (int i = 0; i < ORBITAL_DOTS_NUM_BACKGROUND; i++) {
      if (scan_page_orbital.bg_dots[i]) {
        lv_obj_clear_flag(scan_page_orbital.bg_dots[i], LV_OBJ_FLAG_HIDDEN);
      }
    }
    orbital_dots_animation_start(&scan_page_orbital);
  }

  // Text container on screen (not parent) to be visible above orbital dots
  lv_obj_t* text_container = lv_obj_create(screen);
  if (!text_container) {
    return;
  }
  lv_obj_set_style_bg_color(text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_left(text_container, 24, 0);
  lv_obj_set_style_pad_right(text_container, 24, 0);
  lv_obj_set_style_pad_top(text_container, 16, 0);
  lv_obj_set_style_pad_bottom(text_container, 16, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_move_foreground(text_container);

  // Label
  lv_obj_t* scan_label = lv_label_create(text_container);
  if (!scan_label) {
    return;
  }
  lv_label_set_text(scan_label, langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_SCAN_TO_FINISH));
  lv_obj_set_style_text_align(scan_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_color(scan_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(scan_label, FONT_SCAN, 0);
  lv_obj_set_width(scan_label, 400);
  lv_label_set_long_mode(scan_label, LV_LABEL_LONG_WRAP);
  lv_obj_center(scan_label);

  lv_obj_update_layout(text_container);
  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Fade in animation
  lv_obj_set_style_opa(text_container, LV_OPA_TRANSP, 0);
  lv_anim_t fade_anim;
  lv_anim_init(&fade_anim);
  lv_anim_set_var(&fade_anim, text_container);
  lv_anim_set_values(&fade_anim, LV_OPA_TRANSP, LV_OPA_COVER);
  lv_anim_set_time(&fade_anim, 500);
  lv_anim_set_delay(&fade_anim, 300);
  lv_anim_set_exec_cb(&fade_anim, fade_anim_cb);
  lv_anim_start(&fade_anim);

  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_move_foreground(menu_button.container);
  }
}

static void fade_anim_cb(void* obj, int32_t value) {
  lv_obj_set_style_opa((lv_obj_t*)obj, (lv_opa_t)value, 0);
}

static void create_fwup_hash_page(lv_obj_t* parent,
                                  const fwpb_display_params_privileged_action* params) {
  if (params->which_action != fwpb_display_params_privileged_action_fwup_tag) {
    return;
  }

  // Title
  lv_obj_t* title = lv_label_create(parent);
  if (title) {
    lv_obj_set_style_text_color(title, lv_color_hex(COLOR_TITLE), 0);
    lv_obj_set_style_text_font(title, FONT_TITLE, 0);
    lv_obj_align(title, LV_ALIGN_CENTER, 0, -100);
    lv_label_set_text(title, langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_HASH));
  }

  // Hash value (using address display widget)
  address_display_create_page(parent, &address_widget, 0);
  create_check_button(parent);
}

static void create_fwup_version_page(lv_obj_t* parent,
                                     const fwpb_display_params_privileged_action* params) {
  if (params->which_action != fwpb_display_params_privileged_action_fwup_tag) {
    return;
  }

  // Title
  lv_obj_t* title = lv_label_create(parent);
  if (title) {
    lv_obj_set_style_text_color(title, lv_color_hex(COLOR_TITLE), 0);
    lv_obj_set_style_text_font(title, FONT_TITLE, 0);
    lv_obj_align(title, LV_ALIGN_CENTER, 0, -100);
    lv_label_set_text(title, langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_VERSION));
  }

  // Version text
  lv_obj_t* version = lv_label_create(parent);
  if (version) {
    lv_obj_set_style_text_color(version, lv_color_white(), 0);
    lv_obj_set_style_text_font(version, FONT_AMOUNT_SATS, 0);
    lv_obj_align(version, LV_ALIGN_CENTER, 0, -20);
    lv_label_set_text(version, params->action.fwup.version);
  }

  create_check_button(parent);
}

static void create_action_page(lv_obj_t* parent,
                               const fwpb_display_params_privileged_action* params) {
  if (params->which_action != fwpb_display_params_privileged_action_confirm_action_tag) {
    return;
  }

  // Title in red
  lv_obj_t* title = lv_label_create(parent);
  if (title) {
    lv_obj_set_style_text_color(title, lv_color_hex(COLOR_CANCEL), 0);
    lv_obj_set_style_text_font(title, FONT_TITLE, 0);
    lv_obj_align(title, LV_ALIGN_CENTER, 0, -100);
    lv_label_set_text(title, params->title);
  }

  // Warning text based on action type
  const char* warning = NULL;
  switch (params->action.confirm_action.action_type) {
    case fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_WIPE_DEVICE:
      warning = "This will erase all data";
      break;
    default:
      warning = "Confirm action";
      break;
  }

  if (warning) {
    lv_obj_t* warning_label = lv_label_create(parent);
    if (warning_label) {
      lv_obj_set_style_text_color(warning_label, lv_color_white(), 0);
      lv_obj_set_style_text_font(warning_label, FONT_TEXT, 0);
      lv_obj_align(warning_label, LV_ALIGN_CENTER, 0, -20);
      lv_label_set_text(warning_label, warning);
      lv_obj_set_width(warning_label, 400);
      lv_label_set_long_mode(warning_label, LV_LABEL_LONG_WRAP);
      lv_obj_set_style_text_align(warning_label, LV_TEXT_ALIGN_CENTER, 0);
    }
  }

  create_check_button(parent);
}

static void create_string_page(lv_obj_t* parent,
                               const fwpb_display_params_privileged_action* params) {
  if (params->which_action != fwpb_display_params_privileged_action_confirm_string_tag) {
    return;
  }

  // Title
  lv_obj_t* title = lv_label_create(parent);
  if (title) {
    lv_obj_set_style_text_color(title, lv_color_hex(COLOR_TITLE), 0);
    lv_obj_set_style_text_font(title, FONT_TITLE, 0);
    lv_obj_align(title, LV_ALIGN_CENTER, 0, -100);
    lv_label_set_text(title, params->title);
  }

  // String value
  lv_obj_t* value_label = lv_label_create(parent);
  if (value_label) {
    lv_obj_set_style_text_color(value_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(value_label, FONT_TEXT, 0);
    lv_obj_align(value_label, LV_ALIGN_CENTER, 0, -20);
    lv_label_set_text(value_label, params->action.confirm_string.value);
    lv_obj_set_width(value_label, 400);
    lv_label_set_long_mode(value_label, LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_align(value_label, LV_TEXT_ALIGN_CENTER, 0);
  }

  create_check_button(parent);
}

static void create_page_content(int page_index) {
  if (page_index < 0 || page_index >= num_pages) {
    return;
  }

  lv_obj_t* parent = page_containers[page_index];
  if (!parent) {
    return;
  }

  // Final page is always scan page
  if (page_index == num_pages - 1) {
    create_scan_page(parent);
    return;
  }

  switch (cached_params.which_action) {
    case fwpb_display_params_privileged_action_confirm_address_tag:
      // All pages are address pages
      address_display_create_page(parent, &address_widget, page_index);
      create_check_button(parent);
      break;

    case fwpb_display_params_privileged_action_confirm_string_tag:
      create_string_page(parent, &cached_params);
      break;

    case fwpb_display_params_privileged_action_fwup_tag:
      if (page_index == 0) {
        create_fwup_hash_page(parent, &cached_params);
      } else {
        create_fwup_version_page(parent, &cached_params);
      }
      break;

    case fwpb_display_params_privileged_action_confirm_action_tag:
      create_action_page(parent, &cached_params);
      break;

    default: {
      // Unknown action type - show error
      lv_obj_t* error_label = lv_label_create(parent);
      if (error_label) {
        lv_obj_set_style_text_color(error_label, lv_color_hex(COLOR_CANCEL), 0);
        lv_obj_set_style_text_font(error_label, FONT_TITLE, 0);
        lv_obj_center(error_label);
        lv_label_set_text(error_label,
                          langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_UNKNOWN_ERROR));
      }
      break;
    }
  }
}

lv_obj_t* screen_privileged_action_init(void* ctx) {
  ASSERT(screen == NULL);

  if (!ctx) {
    return NULL;
  }

  fwpb_display_show_screen* params = (fwpb_display_show_screen*)ctx;

  // Cache params
  memcpy(&cached_params, &params->params.privileged_action,
         sizeof(fwpb_display_params_privileged_action));

  // Initialize address widget if needed
  memset(&address_widget, 0, sizeof(address_widget));
  switch (cached_params.which_action) {
    case fwpb_display_params_privileged_action_confirm_address_tag:
      address_display_init(&address_widget, cached_params.action.confirm_address.address);
      break;
    case fwpb_display_params_privileged_action_fwup_tag:
      // Address display widget for hash pagination
      address_display_init(&address_widget, cached_params.action.fwup.hash);
      break;
    default:
      break;
  }

  // Calculate total pages
  num_pages = calculate_total_pages(&cached_params);
  current_page_index = 0;

  // Create screen
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_size(screen, LV_HOR_RES, LV_VER_RES);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Create header
  header = lv_obj_create(screen);
  if (header) {
    lv_obj_set_size(header, LV_HOR_RES, HEADER_HEIGHT);
    lv_obj_align(header, LV_ALIGN_TOP_MID, 0, 0);
    lv_obj_set_style_bg_opa(header, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(header, LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(header, 0, 0);
    lv_obj_clear_flag(header, LV_OBJ_FLAG_SCROLLABLE);

    // Header title (page indicator)
    header_title = lv_label_create(header);
    if (header_title) {
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_font(header_title, FONT_TITLE, 0);
      lv_obj_align(header_title, LV_ALIGN_CENTER, 0, TITLE_MARGIN_TOP);
    }

    // Menu button
    top_menu_create(header, &menu_button, menu_button_custom_handler);
  }

  // Create scroll container
  scroll_container = lv_obj_create(screen);
  if (scroll_container) {
    lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES - HEADER_HEIGHT);
    lv_obj_set_pos(scroll_container, 0, HEADER_HEIGHT);
    lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(scroll_container, 0, 0);
    lv_obj_set_scroll_dir(scroll_container, LV_DIR_HOR);
    lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
    lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
    lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                          LV_FLEX_ALIGN_CENTER);
    lv_obj_set_scroll_snap_x(scroll_container, LV_SCROLL_SNAP_CENTER);
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_SCROLL_ELASTIC);

    // Create page containers
    for (int i = 0; i < num_pages; i++) {
      page_containers[i] = lv_obj_create(scroll_container);
      if (page_containers[i]) {
        lv_obj_set_size(page_containers[i], LV_HOR_RES, LV_VER_RES - HEADER_HEIGHT);
        lv_obj_set_style_bg_opa(page_containers[i], LV_OPA_TRANSP, 0);
        lv_obj_set_style_border_opa(page_containers[i], LV_OPA_TRANSP, 0);
        lv_obj_set_style_pad_all(page_containers[i], 0, 0);
        lv_obj_clear_flag(page_containers[i], LV_OBJ_FLAG_SCROLLABLE);
      }
    }
  }

  // Create widgets
  memset(&approve_ring, 0, sizeof(approve_ring));
  hold_ring_create(screen, &approve_ring);

  memset(&cancel_modal, 0, sizeof(cancel_modal));
  hold_cancel_create(screen, &cancel_modal);

  memset(&scan_page_orbital, 0, sizeof(scan_page_orbital));

  // Create first page content (lazy load others)
  create_page_content(0);
  update_step_indicator(0, num_pages);

  return screen;
}

void screen_privileged_action_destroy(void) {
  hold_ring_destroy(&approve_ring);
  hold_cancel_destroy(&cancel_modal);
  orbital_dots_animation_destroy(&scan_page_orbital);
  address_display_destroy(&address_widget);

  if (screen) {
    lv_obj_del(screen);
    screen = NULL;
  }

  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_page_orbital, 0, sizeof(scan_page_orbital));
  memset(&address_widget, 0, sizeof(address_widget));
}

void screen_privileged_action_update(void* ctx) {
  screen_privileged_action_destroy();
  screen_privileged_action_init(ctx);
}
