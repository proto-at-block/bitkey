#include "screen_about.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "scroll_arc_indicator.h"
#include "top_back.h"
#include "ui.h"

#include <string.h>

#define SCREEN_BRIGHTNESS 100

#define HEADER_HEIGHT              88
#define SCROLL_CONTAINER_SIDE_PAD  24
#define SCROLL_CONTAINER_TOP_PAD   52
#define SCROLL_BOTTOM_PADDING      100
#define SECTION_SPACING            48
#define CONTENT_SPACING            4
#define INFO_ITEM_SPACING          50
#define INFO_TO_REGULATORY_SPACING INFO_ITEM_SPACING
#define SECTION_BODY_SPACING       10
#define LABEL_VALUE_OFFSET         16
#define LOGO_TOP_PAD               8
#define INFO_MAX_WIDTH             400
#define TEXT_MAX_WIDTH             340

#define COLOR_REGULATORY_COUNTRY 0xADADAD
#define COLOR_LABEL_NAME         0xADADAD
#define COLOR_LABEL_VALUE        0xFFFFFF
#define COLOR_BODY_TEXT          0xFFFFFF
#define COLOR_LOGO_TINT          0xADADAD

#define FONT_REGULATORY_COUNTRY (&cash_sans_mono_regular_26)
#define FONT_LABEL_NAME         (&cash_sans_mono_regular_28)
#define FONT_LABEL_VALUE        (&cash_sans_mono_regular_26)
#define FONT_BODY               (&cash_sans_mono_regular_28)

extern const lv_img_dsc_t rcm_logo;

static lv_obj_t* screen = NULL;
static lv_obj_t* scroll_container = NULL;
static scroll_arc_indicator_t scroll_indicator;
static top_back_t back_button;
static lv_obj_t* firmware_value_label = NULL;
static lv_obj_t* serial_value_label = NULL;

static lv_obj_t* create_content_group(lv_obj_t* parent, lv_coord_t row_spacing) {
  lv_obj_t* container = lv_obj_create(parent);
  if (!container) {
    return NULL;
  }
  lv_obj_set_size(container, LV_PCT(100), LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(container, 0, 0);
  lv_obj_set_style_pad_all(container, 0, 0);
  lv_obj_clear_flag(container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_clear_flag(container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_set_layout(container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(container, row_spacing, 0);
  return container;
}

static lv_obj_t* create_label_header_with_margin(lv_obj_t* parent, const char* text,
                                                 lv_coord_t margin_top) {
  lv_obj_t* label = lv_label_create(parent);
  if (!label) {
    return NULL;
  }
  lv_label_set_text(label, text);
  lv_obj_set_style_text_font(label, FONT_LABEL_NAME, 0);
  lv_obj_set_style_text_color(label, lv_color_hex(COLOR_LABEL_NAME), 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_margin_top(label, margin_top, 0);
  lv_obj_set_style_margin_bottom(label, SECTION_BODY_SPACING - CONTENT_SPACING, 0);
  return label;
}

static lv_obj_t* create_regulatory_country_header(lv_obj_t* parent, const char* text) {
  lv_obj_t* label = lv_label_create(parent);
  if (!label) {
    return NULL;
  }
  lv_label_set_text(label, text);
  lv_obj_set_style_text_font(label, FONT_REGULATORY_COUNTRY, 0);
  lv_obj_set_style_text_color(label, lv_color_hex(COLOR_REGULATORY_COUNTRY), 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_margin_top(label, SECTION_SPACING - CONTENT_SPACING, 0);
  lv_obj_set_style_margin_bottom(label, SECTION_BODY_SPACING - CONTENT_SPACING, 0);
  return label;
}

static lv_obj_t* create_body_text(lv_obj_t* parent, const char* text) {
  lv_obj_t* label = lv_label_create(parent);
  if (!label) {
    return NULL;
  }
  lv_label_set_text(label, text);
  lv_obj_set_width(label, TEXT_MAX_WIDTH);
  lv_label_set_long_mode(label, LV_LABEL_LONG_WRAP);
  lv_obj_set_style_text_font(label, FONT_BODY, 0);
  lv_obj_set_style_text_color(label, lv_color_hex(COLOR_BODY_TEXT), 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_line_space(label, 8, 0);
  return label;
}

static lv_obj_t* create_logo(lv_obj_t* parent, const lv_img_dsc_t* src) {
  lv_obj_t* img = lv_img_create(parent);
  if (!img) {
    return NULL;
  }
  lv_img_set_src(img, src);
  lv_obj_set_style_img_recolor(img, lv_color_hex(COLOR_LOGO_TINT), 0);
  lv_obj_set_style_img_recolor_opa(img, LV_OPA_COVER, 0);
  lv_obj_set_style_margin_top(img, LOGO_TOP_PAD, 0);
  return img;
}

static lv_obj_t* create_info_item(lv_obj_t* parent, const char* name, const char* value) {
  lv_obj_t* container = lv_obj_create(parent);
  if (!container) {
    return NULL;
  }
  lv_obj_set_size(container, INFO_MAX_WIDTH, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(container, 0, 0);
  lv_obj_set_style_pad_all(container, 0, 0);
  lv_obj_clear_flag(container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_clear_flag(container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_set_layout(container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(container, LABEL_VALUE_OFFSET, 0);

  lv_obj_t* label_name = lv_label_create(container);
  if (label_name) {
    lv_label_set_text(label_name, name);
    lv_obj_set_width(label_name, LV_PCT(100));
    lv_obj_set_style_text_color(label_name, lv_color_hex(COLOR_LABEL_NAME), 0);
    lv_obj_set_style_text_font(label_name, FONT_LABEL_NAME, 0);
    lv_obj_set_style_text_align(label_name, LV_TEXT_ALIGN_CENTER, 0);
  }

  lv_obj_t* label_val = lv_label_create(container);
  if (label_val) {
    lv_label_set_text(label_val, value ? value : "");
    lv_obj_set_width(label_val, LV_PCT(100));
    lv_label_set_long_mode(label_val, LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_color(label_val, lv_color_hex(COLOR_LABEL_VALUE), 0);
    lv_obj_set_style_text_font(label_val, FONT_LABEL_VALUE, 0);
    lv_obj_set_style_text_align(label_val, LV_TEXT_ALIGN_CENTER, 0);
  }

  return label_val;
}

static void create_regulatory_content(lv_obj_t* parent) {
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_COMPANY));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_PRODUCT));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_MODEL));

  create_regulatory_country_header(parent, langpack_get_string(LANGPACK_ID_REGULATORY_US_HEADER));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_US_ADDRESS));
  lv_obj_t* fcc_id = create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_FCC_ID));
  if (fcc_id) {
    lv_obj_set_style_margin_top(fcc_id, SECTION_SPACING - CONTENT_SPACING, 0);
  }

  create_regulatory_country_header(parent, langpack_get_string(LANGPACK_ID_REGULATORY_CA_HEADER));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_CA_ICES));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_CA_IC));

  create_regulatory_country_header(parent, langpack_get_string(LANGPACK_ID_REGULATORY_EU_HEADER));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_EU_ADDRESS));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_EU_RATING));

  create_regulatory_country_header(parent, langpack_get_string(LANGPACK_ID_REGULATORY_AU_HEADER));
  create_logo(parent, &rcm_logo);

  create_regulatory_country_header(parent, langpack_get_string(LANGPACK_ID_REGULATORY_NZ_HEADER));
  create_body_text(parent, langpack_get_string(LANGPACK_ID_REGULATORY_NZ_SUPPLIER));
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

  scroll_container = lv_obj_create(screen);
  if (!scroll_container) {
    return NULL;
  }
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES - HEADER_HEIGHT);
  lv_obj_set_pos(scroll_container, 0, HEADER_HEIGHT);
  lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_left(scroll_container, SCROLL_CONTAINER_SIDE_PAD, 0);
  lv_obj_set_style_pad_right(scroll_container, SCROLL_CONTAINER_SIDE_PAD, 0);
  lv_obj_set_style_pad_top(scroll_container, SCROLL_CONTAINER_TOP_PAD, 0);
  lv_obj_set_style_pad_bottom(scroll_container, SCROLL_BOTTOM_PADDING, 0);

  lv_obj_set_scroll_dir(scroll_container, LV_DIR_VER);
  lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
  lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_SCROLL_MOMENTUM);
  lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_SCROLL_ELASTIC);

  lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(scroll_container, 0, 0);

  lv_obj_t* about_group = create_content_group(scroll_container, INFO_ITEM_SPACING);
  if (about_group) {
    firmware_value_label =
      create_info_item(about_group, langpack_get_string(LANGPACK_ID_ABOUT_FIRMWARE_LABEL),
                       params ? params->firmware_version : "");
    serial_value_label =
      create_info_item(about_group, langpack_get_string(LANGPACK_ID_ABOUT_SERIAL_LABEL),
                       params ? params->serial_number : "");
  }

  lv_obj_t* regulatory_group = create_content_group(scroll_container, CONTENT_SPACING);
  if (regulatory_group) {
    lv_obj_set_style_margin_top(regulatory_group, INFO_TO_REGULATORY_SPACING, 0);
    create_label_header_with_margin(regulatory_group,
                                    langpack_get_string(LANGPACK_ID_ABOUT_REGULATORY_HEADER), 0);
    create_regulatory_content(regulatory_group);
  }

  scroll_arc_indicator_create(screen, scroll_container, &scroll_indicator);

  lv_obj_update_layout(scroll_container);
  scroll_arc_indicator_update(&scroll_indicator);

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

  scroll_arc_indicator_destroy(&scroll_indicator);
  top_back_destroy(&back_button);
  memset(&back_button, 0, sizeof(back_button));
  lv_obj_del(screen);
  screen = NULL;
  scroll_container = NULL;
  firmware_value_label = NULL;
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
      if (serial_value_label) {
        lv_label_set_text(serial_value_label, params->serial_number);
      }

      lv_obj_update_layout(scroll_container);
      scroll_arc_indicator_update(&scroll_indicator);
    }
  }
}
