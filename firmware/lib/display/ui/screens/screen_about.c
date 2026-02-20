#include "screen_about.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "top_back.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define HEADER_HEIGHT         120
#define SCROLL_CONTAINER_PAD  24
#define SCROLL_BOTTOM_PADDING 100
#define SECTION_SPACING       40
#define LABEL_VALUE_OFFSET    8

// Arc scroll indicator configuration (matching page_indicator style)
#define ARC_BACKGROUND_WIDTH    40  // Total arc span in degrees
#define ARC_WIDTH               12
#define ARC_MIN_INDICATOR_WIDTH 10  // Minimum indicator width in degrees
#define ARC_EDGE_GAP            8   // Gap between outer edge of arc and screen edge
#define ARC_START_ANGLE         (360 - ARC_BACKGROUND_WIDTH / 2)
#define ARC_BG_COLOR            0x404040  // Grey for background
#define ARC_FG_COLOR            0xFFFFFF  // White for position indicator
#define ARC_RADIUS              (233 - ARC_WIDTH / 2 - ARC_EDGE_GAP)

// Colors
#define COLOR_LABEL_NAME  0x808080
#define COLOR_LABEL_VALUE 0xFFFFFF

// Fonts
#define FONT_LABEL_NAME  (&cash_sans_mono_regular_26)
#define FONT_LABEL_VALUE (&cash_sans_mono_regular_30)

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* scroll_arc_bg = NULL;
static lv_obj_t* scroll_arc_fg = NULL;
static top_back_t back_button;
static lv_obj_t* firmware_value_label = NULL;
static lv_obj_t* hardware_value_label = NULL;
static lv_obj_t* serial_value_label = NULL;

// Forward declarations
static void create_scroll_content(const fwpb_display_params_about* params);
static void create_scroll_arc_indicator(void);
static void scroll_event_handler(lv_event_t* e);
static void update_scroll_indicator(void);

static void create_scroll_arc_indicator(void) {
  // Background arc (grey track)
  scroll_arc_bg = lv_arc_create(screen);
  if (!scroll_arc_bg) {
    return;
  }
  lv_obj_set_size(scroll_arc_bg, ARC_RADIUS * 2, ARC_RADIUS * 2);
  lv_obj_center(scroll_arc_bg);

  // Remove knob and make non-clickable
  lv_obj_remove_style(scroll_arc_bg, NULL, LV_PART_KNOB);
  lv_obj_clear_flag(scroll_arc_bg, LV_OBJ_FLAG_CLICKABLE);

  // Set the arc angles
  lv_arc_set_bg_angles(scroll_arc_bg, ARC_START_ANGLE, ARC_START_ANGLE + ARC_BACKGROUND_WIDTH);
  lv_arc_set_angles(scroll_arc_bg, ARC_START_ANGLE, ARC_START_ANGLE + ARC_BACKGROUND_WIDTH);
  lv_arc_set_range(scroll_arc_bg, 0, 100);
  lv_arc_set_value(scroll_arc_bg, 100);

  // Hide the background track
  lv_obj_set_style_arc_opa(scroll_arc_bg, LV_OPA_TRANSP, LV_PART_MAIN);

  // Style the indicator arc (the grey background track)
  lv_obj_set_style_arc_width(scroll_arc_bg, ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(scroll_arc_bg, lv_color_hex(ARC_BG_COLOR), LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(scroll_arc_bg, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(scroll_arc_bg, true, LV_PART_INDICATOR);

  // Make background transparent
  lv_obj_set_style_bg_opa(scroll_arc_bg, LV_OPA_TRANSP, 0);

  // Foreground arc (white position indicator)
  scroll_arc_fg = lv_arc_create(screen);
  if (!scroll_arc_fg) {
    return;
  }
  lv_obj_set_size(scroll_arc_fg, ARC_RADIUS * 2, ARC_RADIUS * 2);
  lv_obj_center(scroll_arc_fg);

  // Remove knob and make non-clickable
  lv_obj_remove_style(scroll_arc_fg, NULL, LV_PART_KNOB);
  lv_obj_clear_flag(scroll_arc_fg, LV_OBJ_FLAG_CLICKABLE);

  // Hide background track
  lv_arc_set_bg_angles(scroll_arc_fg, 0, 0);
  lv_obj_set_style_arc_opa(scroll_arc_fg, LV_OPA_TRANSP, LV_PART_MAIN);

  // Set initial position at start (will be updated by update_scroll_indicator)
  lv_arc_set_angles(scroll_arc_fg, ARC_START_ANGLE, ARC_START_ANGLE + ARC_BACKGROUND_WIDTH);

  // Style the position indicator
  lv_obj_set_style_arc_width(scroll_arc_fg, ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(scroll_arc_fg, lv_color_hex(ARC_FG_COLOR), LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(scroll_arc_fg, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(scroll_arc_fg, true, LV_PART_INDICATOR);

  // Make background transparent
  lv_obj_set_style_bg_opa(scroll_arc_fg, LV_OPA_TRANSP, 0);
}

static void update_scroll_indicator(void) {
  if (!scroll_container || !scroll_arc_fg) {
    return;
  }

  // Get scroll position and max scroll range
  lv_coord_t scroll_y = lv_obj_get_scroll_y(scroll_container);
  lv_coord_t scroll_max = lv_obj_get_scroll_bottom(scroll_container) + scroll_y;

  // Get viewport and content heights to calculate indicator size
  lv_coord_t viewport_height = lv_obj_get_height(scroll_container);
  lv_coord_t content_height = viewport_height + scroll_max;

  // Calculate indicator width based on viewport/content ratio
  // Indicator width represents how much of the content is visible
  int16_t indicator_width = ARC_BACKGROUND_WIDTH;
  if (content_height > 0) {
    float visible_ratio = (float)viewport_height / (float)content_height;
    if (visible_ratio > 1.0f)
      visible_ratio = 1.0f;
    indicator_width = (int16_t)(visible_ratio * ARC_BACKGROUND_WIDTH);
    if (indicator_width < ARC_MIN_INDICATOR_WIDTH)
      indicator_width = ARC_MIN_INDICATOR_WIDTH;
  }

  // Calculate scroll percentage (0.0 to 1.0)
  float scroll_pct = 0.0f;
  if (scroll_max > 0) {
    scroll_pct = (float)scroll_y / (float)scroll_max;
    if (scroll_pct < 0.0f)
      scroll_pct = 0.0f;
    if (scroll_pct > 1.0f)
      scroll_pct = 1.0f;
  }

  // Map scroll percentage to arc angle position
  int16_t available_range = ARC_BACKGROUND_WIDTH - indicator_width;
  int16_t offset = (int16_t)(scroll_pct * available_range);

  int16_t start_angle = ARC_START_ANGLE + offset;
  int16_t end_angle = start_angle + indicator_width;

  lv_arc_set_angles(scroll_arc_fg, start_angle, end_angle);
}

static void scroll_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_SCROLL) {
    update_scroll_indicator();
  }
}

static lv_obj_t* create_info_item(const char* name, const char* value) {
  // Create container for the label pair
  lv_obj_t* container = lv_obj_create(scroll_container);
  if (!container) {
    return NULL;
  }
  lv_obj_set_size(container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(container, 0, 0);
  lv_obj_set_style_pad_all(container, 0, 0);
  lv_obj_set_layout(container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(container, LABEL_VALUE_OFFSET, 0);

  // Label name
  lv_obj_t* label_name = lv_label_create(container);
  if (label_name) {
    lv_label_set_text(label_name, name);
    lv_obj_set_style_text_color(label_name, lv_color_hex(COLOR_LABEL_NAME), 0);
    lv_obj_set_style_text_font(label_name, FONT_LABEL_NAME, 0);
    lv_obj_set_style_text_align(label_name, LV_TEXT_ALIGN_CENTER, 0);
  }

  // Label value
  lv_obj_t* label_val = lv_label_create(container);
  if (label_val) {
    lv_label_set_text(label_val, value ? value : "");
    lv_obj_set_style_text_color(label_val, lv_color_hex(COLOR_LABEL_VALUE), 0);
    lv_obj_set_style_text_font(label_val, FONT_LABEL_VALUE, 0);
    lv_obj_set_style_text_align(label_val, LV_TEXT_ALIGN_CENTER, 0);
  }

  return label_val;
}

static void create_scroll_content(const fwpb_display_params_about* params) {
  firmware_value_label = create_info_item(langpack_get_string(LANGPACK_ID_ABOUT_FIRMWARE_LABEL),
                                          params ? params->firmware_version : "");

  hardware_value_label = create_info_item(langpack_get_string(LANGPACK_ID_ABOUT_HARDWARE_LABEL),
                                          params ? params->hardware_version : "");

  serial_value_label = create_info_item(langpack_get_string(LANGPACK_ID_ABOUT_SERIAL_LABEL),
                                        params ? params->serial_number : "");
}

lv_obj_t* screen_about_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_about* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_about_tag) {
    params = &show_screen->params.about;
  }

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create vertical scroll container (below header area)
  scroll_container = lv_obj_create(screen);
  if (!scroll_container) {
    return NULL;
  }
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES - HEADER_HEIGHT);
  lv_obj_set_pos(scroll_container, 0, HEADER_HEIGHT);
  lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(scroll_container, SCROLL_CONTAINER_PAD, 0);
  lv_obj_set_style_pad_bottom(scroll_container, SCROLL_BOTTOM_PADDING, 0);

  // Configure vertical scrolling with touch input
  lv_obj_set_scroll_dir(scroll_container, LV_DIR_VER);
  lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);  // Use arc indicator instead
  lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_SCROLL_MOMENTUM);
  lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_SCROLL_ELASTIC);

  // Use flex column layout for vertical content
  lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(scroll_container, SECTION_SPACING, 0);

  // Create all content
  create_scroll_content(params);

  // Create arc scroll indicator (after content so we know scroll range)
  create_scroll_arc_indicator();

  // Force layout calculation and set initial indicator position and size
  lv_obj_update_layout(scroll_container);
  update_scroll_indicator();

  // Add scroll event handler to update arc position
  lv_obj_add_event_cb(scroll_container, scroll_event_handler, LV_EVENT_SCROLL, NULL);

  // Create header overlay (black background so content scrolls behind it)
  lv_obj_t* header = lv_obj_create(screen);
  if (header) {
    lv_obj_set_size(header, LV_PCT(100), HEADER_HEIGHT);
    lv_obj_set_pos(header, 0, 0);
    lv_obj_set_style_bg_color(header, lv_color_black(), 0);
    lv_obj_set_style_bg_opa(header, LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(header, 0, 0);
    lv_obj_set_style_pad_all(header, 0, 0);
    lv_obj_clear_flag(header, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_clear_flag(header, LV_OBJ_FLAG_CLICKABLE);
  }

  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(screen, &back_button, NULL);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_about_destroy(void) {
  if (!screen) {
    return;
  }

  top_back_destroy(&back_button);
  memset(&back_button, 0, sizeof(back_button));
  lv_obj_del(screen);
  screen = NULL;
  scroll_container = NULL;
  scroll_arc_bg = NULL;
  scroll_arc_fg = NULL;
  firmware_value_label = NULL;
  hardware_value_label = NULL;
  serial_value_label = NULL;
}

void screen_about_update(void* ctx) {
  if (!screen) {
    screen_about_init(ctx);
    return;
  }

  if (ctx) {
    const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
    if (show_screen->which_params == fwpb_display_show_screen_about_tag) {
      const fwpb_display_params_about* params = &show_screen->params.about;

      if (firmware_value_label) {
        lv_label_set_text(firmware_value_label, params->firmware_version);
      }
      if (hardware_value_label) {
        lv_label_set_text(hardware_value_label, params->hardware_version);
      }
      if (serial_value_label) {
        lv_label_set_text(serial_value_label, params->serial_number);
      }
    }
  }
}
