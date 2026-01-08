#include "screen_about.h"

#include "assert.h"
#include "bottom_back.h"
#include "display.pb.h"
#include "langpack.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define CONTAINER_SIZE        240
#define SCREEN_TITLE_Y_OFFSET 50
#define LABEL_Y_START         10
#define LABEL_Y_SPACING       80
#define LABEL_VALUE_OFFSET    30

// Colors
#define COLOR_TITLE       0xADADAD
#define COLOR_LABEL_NAME  0x808080
#define COLOR_LABEL_VALUE 0xFFFFFF

// Fonts
#define FONT_SCREEN_TITLE (&cash_sans_mono_regular_24)
#define FONT_LABEL_NAME   (&cash_sans_mono_regular_20)
#define FONT_LABEL_VALUE  (&cash_sans_mono_regular_24)

static lv_obj_t* screen = NULL;
static bottom_back_t back_button;
static lv_obj_t* firmware_value_label = NULL;
static lv_obj_t* hardware_value_label = NULL;
static lv_obj_t* serial_value_label = NULL;

static lv_obj_t* create_info_label(lv_obj_t* parent, const char* name, const char* value,
                                   int y_offset) {
  lv_obj_t* label_name = lv_label_create(parent);
  lv_label_set_text(label_name, name);
  lv_obj_set_style_text_color(label_name, lv_color_hex(COLOR_LABEL_NAME), 0);
  lv_obj_set_style_text_font(label_name, FONT_LABEL_NAME, 0);
  lv_obj_align(label_name, LV_ALIGN_TOP_MID, 0, y_offset);

  lv_obj_t* label_val = lv_label_create(parent);
  lv_label_set_text(label_val, value ? value : "");
  lv_obj_set_style_text_color(label_val, lv_color_hex(COLOR_LABEL_VALUE), 0);
  lv_obj_set_style_text_font(label_val, FONT_LABEL_VALUE, 0);
  lv_obj_align(label_val, LV_ALIGN_TOP_MID, 0, y_offset + LABEL_VALUE_OFFSET);

  return label_val;
}

lv_obj_t* screen_about_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_about* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_about_tag) {
    params = &show_screen->params.about;
  }

  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  lv_obj_t* screen_title = lv_label_create(screen);
  lv_label_set_text(screen_title, langpack_get_string(LANGPACK_ID_ABOUT_TITLE));
  lv_obj_set_style_text_font(screen_title, FONT_SCREEN_TITLE, 0);
  lv_obj_set_style_text_color(screen_title, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_align(screen_title, LV_ALIGN_TOP_MID, 0, SCREEN_TITLE_Y_OFFSET);

  memset(&back_button, 0, sizeof(bottom_back_t));
  bottom_back_create(screen, &back_button);

  lv_obj_t* cont = lv_obj_create(screen);
  lv_obj_set_size(cont, CONTAINER_SIZE, CONTAINER_SIZE);
  lv_obj_center(cont);
  lv_obj_set_style_radius(cont, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(cont, lv_color_black(), 0);
  lv_obj_set_style_border_width(cont, 0, 0);

  firmware_value_label =
    create_info_label(cont, langpack_get_string(LANGPACK_ID_ABOUT_FIRMWARE_LABEL),
                      params ? params->firmware_version : "", LABEL_Y_START);
  hardware_value_label =
    create_info_label(cont, langpack_get_string(LANGPACK_ID_ABOUT_HARDWARE_LABEL),
                      params ? params->hardware_version : "", LABEL_Y_START + LABEL_Y_SPACING);
  serial_value_label =
    create_info_label(cont, langpack_get_string(LANGPACK_ID_ABOUT_SERIAL_LABEL),
                      params ? params->serial_number : "", LABEL_Y_START + LABEL_Y_SPACING * 2);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_about_destroy(void) {
  if (!screen) {
    return;
  }

  bottom_back_destroy(&back_button);
  lv_obj_del(screen);
  screen = NULL;
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
