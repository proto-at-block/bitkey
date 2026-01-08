#include "screen_locked.h"

#include "assert.h"
#include "display.pb.h"
#include "ui.h"

#include <stdio.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define BATTERY_Y_OFFSET     (-30)
#define BATTERY_ICON_SPACING 5  // Space between battery icon and percentage text

// Colors
#define COLOR_BATTERY_FULL_CHARGING lv_color_make(0x00, 0xFF, 0x00)  // Bright green
#define COLOR_BATTERY_CHARGING      lv_color_make(0x00, 0xAD, 0x00)  // Muted green
#define COLOR_BATTERY_LOW           lv_color_make(0xAD, 0x00, 0x00)  // Muted red
#define COLOR_BATTERY_MEDIUM_LOW    lv_color_make(0xAD, 0x6B, 0x00)  // Muted orange
#define COLOR_BATTERY_NORMAL        lv_color_make(0xAD, 0xAD, 0xAD)  // Grey
#define COLOR_BATTERY_PERCENT_TEXT  lv_color_make(0xAD, 0xAD, 0xAD)  // Grey

// Fonts
#define FONT_BATTERY (&cash_sans_mono_regular_20)

// External image declarations
extern const lv_img_dsc_t lock;
extern const lv_img_dsc_t battery_10;
extern const lv_img_dsc_t battery_25;
extern const lv_img_dsc_t battery_50;
extern const lv_img_dsc_t battery_75;
extern const lv_img_dsc_t battery_100;
extern const lv_img_dsc_t battery_charging;

static lv_obj_t* screen = NULL;
static lv_obj_t* lock_icon = NULL;
static lv_obj_t* battery_container = NULL;
static lv_obj_t* battery_icon = NULL;
static lv_obj_t* battery_percent_label = NULL;

static const lv_img_dsc_t* get_battery_icon(uint8_t percent, bool is_charging) {
  if (is_charging) {
    return &battery_charging;
  }

  if (percent <= 10) {
    return &battery_10;
  } else if (percent <= 25) {
    return &battery_25;
  } else if (percent <= 50) {
    return &battery_50;
  } else if (percent <= 75) {
    return &battery_75;
  } else {
    return &battery_100;
  }
}

static lv_color_t get_battery_color(uint8_t percent, bool is_charging) {
  if (is_charging && percent == 100) {
    return COLOR_BATTERY_FULL_CHARGING;
  } else if (is_charging) {
    return COLOR_BATTERY_CHARGING;
  }

  if (percent <= 10) {
    return COLOR_BATTERY_LOW;
  } else if (percent <= 30) {
    return COLOR_BATTERY_MEDIUM_LOW;
  } else {
    return COLOR_BATTERY_NORMAL;
  }
}

lv_obj_t* screen_locked_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  uint8_t battery_percent = 100;
  bool is_charging = false;

  // Extract battery info from params if available
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_locked_tag) {
    battery_percent = show_screen->params.locked.battery_percent;
    is_charging = show_screen->params.locked.is_charging;
  }

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create lock icon
  lock_icon = lv_img_create(screen);
  lv_img_set_src(lock_icon, &lock);
  lv_obj_align(lock_icon, LV_ALIGN_CENTER, 0, 0);

  // Create battery container to keep icon and percentage centered together
  battery_container = lv_obj_create(screen);
  lv_obj_remove_style_all(battery_container);
  lv_obj_set_size(battery_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_layout(battery_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(battery_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(battery_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(battery_container, BATTERY_ICON_SPACING, 0);
  lv_obj_align(battery_container, LV_ALIGN_BOTTOM_MID, 0, BATTERY_Y_OFFSET);

  // Create battery icon in container
  battery_icon = lv_img_create(battery_container);
  const lv_img_dsc_t* battery_img = get_battery_icon(battery_percent, is_charging);
  lv_img_set_src(battery_icon, battery_img);

  // Apply color to battery icon
  lv_color_t battery_color = get_battery_color(battery_percent, is_charging);
  lv_obj_set_style_img_recolor(battery_icon, battery_color, 0);
  lv_obj_set_style_img_recolor_opa(battery_icon, LV_OPA_100, 0);

  // Create battery percentage label in container
  battery_percent_label = lv_label_create(battery_container);
  char percent_text[8];
  snprintf(percent_text, sizeof(percent_text), "%d%%", battery_percent);
  lv_label_set_text(battery_percent_label, percent_text);
  lv_obj_set_style_text_color(battery_percent_label, COLOR_BATTERY_PERCENT_TEXT, 0);
  lv_obj_set_style_text_font(battery_percent_label, FONT_BATTERY, 0);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_locked_destroy(void) {
  if (!screen) {
    return;
  }

  lv_obj_del(screen);
  screen = NULL;
  lock_icon = NULL;
  battery_container = NULL;
  battery_icon = NULL;
  battery_percent_label = NULL;
}

void screen_locked_update(void* ctx) {
  if (!screen) {
    screen_locked_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_locked_tag) {
    return;
  }

  uint8_t battery_percent = show_screen->params.locked.battery_percent;
  bool is_charging = show_screen->params.locked.is_charging;

  // Update battery icon
  if (battery_icon) {
    const lv_img_dsc_t* battery_img = get_battery_icon(battery_percent, is_charging);
    lv_img_set_src(battery_icon, battery_img);

    // Update color
    lv_color_t battery_color = get_battery_color(battery_percent, is_charging);
    lv_obj_set_style_img_recolor(battery_icon, battery_color, 0);
    lv_obj_set_style_img_recolor_opa(battery_icon, LV_OPA_100, 0);
  }

  // Update battery percentage text
  if (battery_percent_label) {
    char percent_text[8];
    snprintf(percent_text, sizeof(percent_text), "%d%%", battery_percent);
    lv_label_set_text(battery_percent_label, percent_text);
    // Text always stays grey
    lv_obj_set_style_text_color(battery_percent_label, COLOR_BATTERY_PERCENT_TEXT, 0);
  }
}
