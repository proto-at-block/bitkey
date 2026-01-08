#include "screen_regulatory.h"

#include "assert.h"
#include "bottom_back.h"
#include "display.pb.h"
#include "langpack.h"
#include "page_indicator.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100
#define TOTAL_CERT_PAGES  3

// Layout configuration
#define CONTENT_WIDTH_PCT     100
#define CONTENT_HEIGHT_PCT    80
#define CONTENT_Y_OFFSET      10
#define CONTENT_PADDING       10
#define SCREEN_TITLE_Y_OFFSET 50
#define FCC_ID_Y_OFFSET       40
#define LOGOS_Y_OFFSET        (-40)
#define LOGO_SPACING          30
#define TITLE_Y_OFFSET        80
#define NOTE_Y_OFFSET         0
#define NOTE_LINE_SPACE       8

// Colors
#define COLOR_TITLE     0xADADAD
#define COLOR_LOGO_TINT 0xADADAD

// Fonts
#define FONT_SCREEN_TITLE (&cash_sans_mono_regular_24)
#define FONT_FCC_ID       (&cash_sans_mono_regular_24)
#define FONT_TITLE        (&cash_sans_mono_regular_24)
#define FONT_NOTE         (&cash_sans_mono_regular_24)

// External image declarations
extern const lv_img_dsc_t fcc_logo;
extern const lv_img_dsc_t ce_logo;
extern const lv_img_dsc_t eac_logo;

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* content_container = NULL;
static page_indicator_t page_indicator;
static bottom_back_t back_button;

// Forward declarations
static void create_page_content(int page);
static void create_certification_logos_page(void);
static void create_fcc_notes_page(int page_num);

static void create_certification_logos_page(void) {
  // Create a horizontal container for the three logos
  lv_obj_t* logos_container = lv_obj_create(content_container);
  lv_obj_set_size(logos_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(logos_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(logos_container, 0, 0);
  lv_obj_set_style_pad_all(logos_container, 0, 0);
  lv_obj_set_layout(logos_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(logos_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(logos_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(logos_container, LOGO_SPACING, 0);
  lv_obj_align(logos_container, LV_ALIGN_CENTER, 0, LOGOS_Y_OFFSET);

  // Add FCC logo
  lv_obj_t* fcc_img = lv_img_create(logos_container);
  lv_img_set_src(fcc_img, &fcc_logo);
  lv_obj_set_style_img_recolor(fcc_img, lv_color_hex(COLOR_LOGO_TINT), 0);
  lv_obj_set_style_img_recolor_opa(fcc_img, LV_OPA_COVER, 0);

  // Add CE logo
  lv_obj_t* ce_img = lv_img_create(logos_container);
  lv_img_set_src(ce_img, &ce_logo);
  lv_obj_set_style_img_recolor(ce_img, lv_color_hex(COLOR_LOGO_TINT), 0);
  lv_obj_set_style_img_recolor_opa(ce_img, LV_OPA_COVER, 0);

  // Add EAC logo
  lv_obj_t* eac_img = lv_img_create(logos_container);
  lv_img_set_src(eac_img, &eac_logo);
  lv_obj_set_style_img_recolor(eac_img, lv_color_hex(COLOR_LOGO_TINT), 0);
  lv_obj_set_style_img_recolor_opa(eac_img, LV_OPA_COVER, 0);

  // Add FCC ID below the logos
  lv_obj_t* fcc_id = lv_label_create(content_container);
  lv_label_set_text(fcc_id, langpack_get_string(LANGPACK_ID_REGULATORY_FCC_ID));
  lv_obj_set_style_text_font(fcc_id, FONT_FCC_ID, 0);
  lv_obj_set_style_text_color(fcc_id, lv_color_white(), 0);
  lv_obj_align(fcc_id, LV_ALIGN_CENTER, 0, FCC_ID_Y_OFFSET);
}

static void create_fcc_notes_page(int page_num) {
  // Create title
  lv_obj_t* title = lv_label_create(content_container);
  lv_label_set_text_fmt(title, langpack_get_string(LANGPACK_ID_REGULATORY_FCC_NOTES_FMT), page_num);
  lv_obj_set_style_text_font(title, FONT_TITLE, 0);
  lv_obj_set_style_text_color(title, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_align(title, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Create note text
  lv_obj_t* note = lv_label_create(content_container);

  if (page_num == 1) {
    lv_label_set_text(note, langpack_get_string(LANGPACK_ID_REGULATORY_NOTE1));
    lv_obj_align(note, LV_ALIGN_CENTER, 0, NOTE_Y_OFFSET);
  } else {
    lv_label_set_text(note, langpack_get_string(LANGPACK_ID_REGULATORY_NOTE2));
    lv_obj_align(note, LV_ALIGN_CENTER, 0, NOTE_Y_OFFSET);
  }

  lv_obj_set_style_text_font(note, FONT_NOTE, 0);
  lv_obj_set_style_text_color(note, lv_color_white(), 0);
  lv_obj_set_style_text_align(note, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_line_space(note, NOTE_LINE_SPACE, 0);
}

static void create_page_content(int page) {
  // Clear existing page content
  lv_obj_clean(content_container);

  switch (page) {
    case 0: {
      create_certification_logos_page();
      break;
    }
    case 1: {
      create_fcc_notes_page(1);
      break;
    }
    case 2: {
      create_fcc_notes_page(2);
      break;
    }
  }
}

void screen_regulatory_update(void* ctx) {
  if (!screen) {
    screen_regulatory_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_regulatory_tag) {
    const fwpb_display_params_regulatory* params = &show_screen->params.regulatory;
    // Update page content if valid page
    if (params->page >= 0 && params->page < TOTAL_CERT_PAGES) {
      create_page_content(params->page);
      // Update page indicator
      page_indicator_update(&page_indicator, params->page);
    }
  }
}

lv_obj_t* screen_regulatory_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_regulatory* params = NULL;
  int initial_page = 0;

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_regulatory_tag) {
    params = &show_screen->params.regulatory;
    if (params->page >= 0 && params->page < TOTAL_CERT_PAGES) {
      initial_page = params->page;
    }
  }

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create "REGULATORY" title at top
  lv_obj_t* screen_title = lv_label_create(screen);
  lv_label_set_text(screen_title, langpack_get_string(LANGPACK_ID_REGULATORY_TITLE));
  lv_obj_set_style_text_font(screen_title, FONT_SCREEN_TITLE, 0);
  lv_obj_set_style_text_color(screen_title, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_align(screen_title, LV_ALIGN_TOP_MID, 0, SCREEN_TITLE_Y_OFFSET);

  // Initialize page indicator
  memset(&page_indicator, 0, sizeof(page_indicator_t));

  // Create page indicator
  page_indicator_create(screen, &page_indicator, TOTAL_CERT_PAGES);

  // Create bottom back button
  memset(&back_button, 0, sizeof(bottom_back_t));
  bottom_back_create(screen, &back_button);

  // Create content container (single level - no nested page container)
  content_container = lv_obj_create(screen);
  lv_obj_set_size(content_container, lv_pct(CONTENT_WIDTH_PCT), lv_pct(CONTENT_HEIGHT_PCT));
  lv_obj_align(content_container, LV_ALIGN_CENTER, 0, CONTENT_Y_OFFSET);
  lv_obj_set_style_bg_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(content_container, 0, 0);
  lv_obj_set_style_pad_all(content_container, CONTENT_PADDING, 0);

  // Create initial page content
  create_page_content(initial_page);

  // Update page indicator
  page_indicator_update(&page_indicator, initial_page);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_regulatory_destroy(void) {
  if (!screen) {
    return;
  }

  page_indicator_destroy(&page_indicator);
  memset(&page_indicator, 0, sizeof(page_indicator));
  bottom_back_destroy(&back_button);
  memset(&back_button, 0, sizeof(back_button));
  lv_obj_del(screen);
  screen = NULL;
  content_container = NULL;
}
