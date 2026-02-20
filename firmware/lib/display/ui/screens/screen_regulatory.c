#include "screen_regulatory.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "top_back.h"
#include "ui.h"

#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define HEADER_HEIGHT         120
#define SCROLL_CONTAINER_PAD  24
#define SCROLL_BOTTOM_PADDING 100
#define CONTENT_WIDTH         400
#define SECTION_SPACING       40
#define LOGO_SPACING          30
#define NOTE_LINE_SPACE       8
#define TEXT_MAX_WIDTH        380

// Arc scroll indicator configuration (matching page_indicator style)
#define ARC_BACKGROUND_WIDTH    40  // Total arc span in degrees
#define ARC_WIDTH               12
#define ARC_MIN_INDICATOR_WIDTH 10  // Minimum indicator width in degrees
#define ARC_EDGE_GAP            8   // Gap between outer edge of arc and screen edge
#define ARC_CENTER_ANGLE        0   // Center position: 0° is right side
#define ARC_START_ANGLE         (360 - ARC_BACKGROUND_WIDTH / 2)
#define ARC_BG_COLOR            0x404040  // Grey for background
#define ARC_FG_COLOR            0xFFFFFF  // White for position indicator
#define ARC_RADIUS              (233 - ARC_WIDTH / 2 - ARC_EDGE_GAP)

// Colors
#define COLOR_LOGO_TINT 0xADADAD
#define COLOR_SECTION   0x888888

// Fonts
#define FONT_FCC_ID  (&cash_sans_mono_regular_24)
#define FONT_SECTION (&cash_sans_mono_regular_24)
#define FONT_NOTE    (&cash_sans_mono_regular_24)

// External image declarations
extern const lv_img_dsc_t fcc_logo;
extern const lv_img_dsc_t ce_logo;
extern const lv_img_dsc_t eac_logo;

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* scroll_arc_bg = NULL;
static lv_obj_t* scroll_arc_fg = NULL;
static top_back_t back_button;

// Forward declarations
static void create_scroll_content(void);
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

static void create_scroll_content(void) {
  // Certification logos section
  lv_obj_t* logos_container = lv_obj_create(scroll_container);
  if (!logos_container) {
    return;
  }
  lv_obj_set_size(logos_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(logos_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(logos_container, 0, 0);
  lv_obj_set_style_pad_all(logos_container, 0, 0);
  lv_obj_set_layout(logos_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(logos_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(logos_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(logos_container, LOGO_SPACING, 0);

  lv_obj_t* fcc_img = lv_img_create(logos_container);
  if (fcc_img) {
    lv_img_set_src(fcc_img, &fcc_logo);
    lv_obj_set_style_img_recolor(fcc_img, lv_color_hex(COLOR_LOGO_TINT), 0);
    lv_obj_set_style_img_recolor_opa(fcc_img, LV_OPA_COVER, 0);
  }

  lv_obj_t* ce_img = lv_img_create(logos_container);
  if (ce_img) {
    lv_img_set_src(ce_img, &ce_logo);
    lv_obj_set_style_img_recolor(ce_img, lv_color_hex(COLOR_LOGO_TINT), 0);
    lv_obj_set_style_img_recolor_opa(ce_img, LV_OPA_COVER, 0);
  }

  lv_obj_t* eac_img = lv_img_create(logos_container);
  if (eac_img) {
    lv_img_set_src(eac_img, &eac_logo);
    lv_obj_set_style_img_recolor(eac_img, lv_color_hex(COLOR_LOGO_TINT), 0);
    lv_obj_set_style_img_recolor_opa(eac_img, LV_OPA_COVER, 0);
  }

  // FCC ID
  lv_obj_t* fcc_id = lv_label_create(scroll_container);
  if (fcc_id) {
    lv_label_set_text(fcc_id, langpack_get_string(LANGPACK_ID_REGULATORY_FCC_ID));
    lv_obj_set_style_text_font(fcc_id, FONT_FCC_ID, 0);
    lv_obj_set_style_text_color(fcc_id, lv_color_white(), 0);
    lv_obj_set_style_text_align(fcc_id, LV_TEXT_ALIGN_CENTER, 0);
  }

  // FCC Notes 1 section title
  lv_obj_t* note1_title = lv_label_create(scroll_container);
  if (note1_title) {
    lv_label_set_text_fmt(note1_title, langpack_get_string(LANGPACK_ID_REGULATORY_FCC_NOTES_FMT),
                          1);
    lv_obj_set_style_text_font(note1_title, FONT_SECTION, 0);
    lv_obj_set_style_text_color(note1_title, lv_color_hex(COLOR_SECTION), 0);
    lv_obj_set_style_text_align(note1_title, LV_TEXT_ALIGN_CENTER, 0);
  }

  // FCC Notes 1 content
  lv_obj_t* note1 = lv_label_create(scroll_container);
  if (note1) {
    lv_label_set_text(note1, langpack_get_string(LANGPACK_ID_REGULATORY_NOTE1));
    lv_obj_set_width(note1, TEXT_MAX_WIDTH);
    lv_label_set_long_mode(note1, LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_font(note1, FONT_NOTE, 0);
    lv_obj_set_style_text_color(note1, lv_color_white(), 0);
    lv_obj_set_style_text_align(note1, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_set_style_text_line_space(note1, NOTE_LINE_SPACE, 0);
  }

  // FCC Notes 2 section title
  lv_obj_t* note2_title = lv_label_create(scroll_container);
  if (note2_title) {
    lv_label_set_text_fmt(note2_title, langpack_get_string(LANGPACK_ID_REGULATORY_FCC_NOTES_FMT),
                          2);
    lv_obj_set_style_text_font(note2_title, FONT_SECTION, 0);
    lv_obj_set_style_text_color(note2_title, lv_color_hex(COLOR_SECTION), 0);
    lv_obj_set_style_text_align(note2_title, LV_TEXT_ALIGN_CENTER, 0);
  }

  // FCC Notes 2 content
  lv_obj_t* note2 = lv_label_create(scroll_container);
  if (note2) {
    lv_label_set_text(note2, langpack_get_string(LANGPACK_ID_REGULATORY_NOTE2));
    lv_obj_set_width(note2, TEXT_MAX_WIDTH);
    lv_label_set_long_mode(note2, LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_font(note2, FONT_NOTE, 0);
    lv_obj_set_style_text_color(note2, lv_color_white(), 0);
    lv_obj_set_style_text_align(note2, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_set_style_text_line_space(note2, NOTE_LINE_SPACE, 0);
  }
}

void screen_regulatory_update(void* ctx) {
  if (!screen) {
    screen_regulatory_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_regulatory_tag) {
    (void)show_screen;
  }
}

lv_obj_t* screen_regulatory_init(void* ctx) {
  (void)ctx;

  ASSERT(screen == NULL);
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
  create_scroll_content();

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

void screen_regulatory_destroy(void) {
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
}
