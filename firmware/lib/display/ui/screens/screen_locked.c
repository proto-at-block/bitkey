#include "screen_locked.h"

#include "assert.h"
#include "display.pb.h"
#include "dot_ring.h"
#include "ui.h"

#include <stdio.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define BATTERY_Y_OFFSET     (-30)
#define BATTERY_ICON_SPACING 5  // Space between battery icon and percentage text

// Colors
#define COLOR_BATTERY_FULL_CHARGING \
  lv_color_make(0xD1, 0xFB, 0x96)                                   // Lime green (matches dot ring)
#define COLOR_BATTERY_CHARGING     lv_color_make(0xFF, 0xFF, 0xFF)  // White
#define COLOR_BATTERY_LOW          lv_color_make(0xF8, 0x47, 0x52)  // Red (#F84752)
#define COLOR_BATTERY_MEDIUM_LOW   lv_color_make(0xAD, 0x6B, 0x00)  // Muted orange
#define COLOR_BATTERY_NORMAL       lv_color_make(0xAD, 0xAD, 0xAD)  // Grey
#define COLOR_BATTERY_PERCENT_TEXT lv_color_make(0xAD, 0xAD, 0xAD)  // Grey

// Fonts
#define FONT_BATTERY (&cash_sans_mono_regular_26)

// External image declarations
extern const lv_img_dsc_t locked;
extern const lv_img_dsc_t unlocked;
extern const lv_img_dsc_t battery_10;
extern const lv_img_dsc_t battery_25;
extern const lv_img_dsc_t battery_50;
extern const lv_img_dsc_t battery_75;
extern const lv_img_dsc_t battery_100;
extern const lv_img_dsc_t battery_charging;

static lv_obj_t* screen = NULL;
static lv_obj_t* lock_icon = NULL;
static lv_obj_t* unlocked_icon = NULL;
static lv_obj_t* battery_container = NULL;
static lv_obj_t* battery_icon = NULL;
static lv_obj_t* battery_percent_label = NULL;
static dot_ring_t charging_ring = {0};
static bool charging_ring_visible = false;

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
  bool show_unlocked = false;

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_locked_tag) {
    battery_percent = show_screen->params.locked.battery_percent;
    is_charging = show_screen->params.locked.is_charging;
    show_unlocked = show_screen->params.locked.show_unlocked;
  }

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Locked icon
  lock_icon = lv_img_create(screen);
  if (!lock_icon) {
    return NULL;
  }
  lv_img_set_src(lock_icon, &locked);
  lv_obj_align(lock_icon, LV_ALIGN_CENTER, 0, 0);
  if (show_unlocked) {
    lv_obj_add_flag(lock_icon, LV_OBJ_FLAG_HIDDEN);
  }

  // Unlocked icon
  unlocked_icon = lv_img_create(screen);
  if (!unlocked_icon) {
    return NULL;
  }
  lv_img_set_src(unlocked_icon, &unlocked);
  lv_obj_align(unlocked_icon, LV_ALIGN_CENTER, 0, 0);
  if (!show_unlocked) {
    lv_obj_add_flag(unlocked_icon, LV_OBJ_FLAG_HIDDEN);
  }

  // Battery container: icon + percentage
  battery_container = lv_obj_create(screen);
  if (!battery_container) {
    return NULL;
  }
  lv_obj_remove_style_all(battery_container);
  lv_obj_set_size(battery_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_layout(battery_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(battery_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(battery_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(battery_container, BATTERY_ICON_SPACING, 0);
  lv_obj_align(battery_container, LV_ALIGN_BOTTOM_MID, 0, BATTERY_Y_OFFSET);

  // Battery icon
  battery_icon = lv_img_create(battery_container);
  if (!battery_icon) {
    return NULL;
  }
  const lv_img_dsc_t* battery_img = get_battery_icon(battery_percent, is_charging);
  lv_img_set_src(battery_icon, battery_img);

  lv_color_t battery_color = get_battery_color(battery_percent, is_charging);
  lv_obj_set_style_img_recolor(battery_icon, battery_color, 0);
  lv_obj_set_style_img_recolor_opa(battery_icon, LV_OPA_100, 0);

  // Battery percentage label
  battery_percent_label = lv_label_create(battery_container);
  if (!battery_percent_label) {
    return NULL;
  }
  char percent_text[8];
  snprintf(percent_text, sizeof(percent_text), "%d%%", battery_percent);
  lv_label_set_text(battery_percent_label, percent_text);
  // Text color: use battery color when charging or low, otherwise grey
  lv_color_t text_color = COLOR_BATTERY_PERCENT_TEXT;
  if (is_charging) {
    text_color = battery_color;
  } else if (battery_percent <= 10) {
    text_color = COLOR_BATTERY_LOW;
  }
  lv_obj_set_style_text_color(battery_percent_label, text_color, 0);
  lv_obj_set_style_text_font(battery_percent_label, FONT_BATTERY, 0);

  // Set lock icon color based on charging state and battery level
  lv_color_t icon_color = lv_color_white();  // Default
  if (is_charging && battery_percent == 100) {
    icon_color = COLOR_BATTERY_FULL_CHARGING;  // Lime green at 100%
  } else if (is_charging) {
    icon_color = lv_color_white();  // White while charging
  } else if (battery_percent <= 10) {
    icon_color = COLOR_BATTERY_LOW;  // Red when low
  }

  if (is_charging || battery_percent <= 10) {
    lv_obj_set_style_img_recolor(lock_icon, icon_color, 0);
    lv_obj_set_style_img_recolor_opa(lock_icon, LV_OPA_100, 0);
    lv_obj_set_style_img_recolor(unlocked_icon, icon_color, 0);
    lv_obj_set_style_img_recolor_opa(unlocked_icon, LV_OPA_100, 0);
  }

  // Create charging ring (initially hidden)
  dot_ring_create(screen, &charging_ring);
  charging_ring_visible = false;

  // Show charging ring with current percentage if charging
  if (is_charging) {
    dot_ring_show(&charging_ring);
    dot_ring_color_t ring_color =
      (battery_percent == 100) ? DOT_RING_COLOR_GREEN : DOT_RING_COLOR_WHITE;
    dot_ring_set_percent(&charging_ring, battery_percent, ring_color, DOT_RING_FILL_CLOCKWISE);
    charging_ring_visible = true;
  } else if (battery_percent <= 10) {
    // Show red ring when battery is critically low (not charging)
    dot_ring_show(&charging_ring);
    dot_ring_set_percent(&charging_ring, battery_percent, DOT_RING_COLOR_RED,
                         DOT_RING_FILL_CLOCKWISE);
    charging_ring_visible = true;
  }

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_locked_destroy(void) {
  if (!screen) {
    return;
  }

  // Destroy charging ring before deleting screen
  dot_ring_destroy(&charging_ring);
  charging_ring_visible = false;

  lv_obj_del(screen);
  screen = NULL;
  lock_icon = NULL;
  unlocked_icon = NULL;
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
  bool show_unlocked = show_screen->params.locked.show_unlocked;

  // Toggle lock/unlock icon
  if (show_unlocked) {
    if (lock_icon) {
      lv_obj_add_flag(lock_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (unlocked_icon) {
      lv_obj_clear_flag(unlocked_icon, LV_OBJ_FLAG_HIDDEN);
    }
  } else {
    if (lock_icon) {
      lv_obj_clear_flag(lock_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (unlocked_icon) {
      lv_obj_add_flag(unlocked_icon, LV_OBJ_FLAG_HIDDEN);
    }
  }

  // Update battery
  if (battery_icon) {
    const lv_img_dsc_t* battery_img = get_battery_icon(battery_percent, is_charging);
    lv_img_set_src(battery_icon, battery_img);

    lv_color_t battery_color = get_battery_color(battery_percent, is_charging);
    lv_obj_set_style_img_recolor(battery_icon, battery_color, 0);
    lv_obj_set_style_img_recolor_opa(battery_icon, LV_OPA_100, 0);
  }

  if (battery_percent_label) {
    char percent_text[8];
    snprintf(percent_text, sizeof(percent_text), "%d%%", battery_percent);
    lv_label_set_text(battery_percent_label, percent_text);
    lv_color_t battery_color = get_battery_color(battery_percent, is_charging);
    // Text color: use battery color when charging or low, otherwise grey
    lv_color_t text_color = COLOR_BATTERY_PERCENT_TEXT;
    if (is_charging) {
      text_color = battery_color;
    } else if (battery_percent <= 10) {
      text_color = COLOR_BATTERY_LOW;
    }
    lv_obj_set_style_text_color(battery_percent_label, text_color, 0);
  }

  // Update lock icon color based on charging state and battery level
  lv_color_t icon_color = lv_color_white();  // Default
  if (is_charging && battery_percent == 100) {
    icon_color = COLOR_BATTERY_FULL_CHARGING;  // Lime green at 100%
  } else if (is_charging) {
    icon_color = lv_color_white();  // White while charging
  } else if (battery_percent <= 10) {
    icon_color = COLOR_BATTERY_LOW;  // Red when low
  }

  if (is_charging || battery_percent <= 10) {
    if (lock_icon) {
      lv_obj_set_style_img_recolor(lock_icon, icon_color, 0);
      lv_obj_set_style_img_recolor_opa(lock_icon, LV_OPA_100, 0);
    }
    if (unlocked_icon) {
      lv_obj_set_style_img_recolor(unlocked_icon, icon_color, 0);
      lv_obj_set_style_img_recolor_opa(unlocked_icon, LV_OPA_100, 0);
    }
  } else {
    // Reset icon color when not charging and not low battery
    if (lock_icon) {
      lv_obj_set_style_img_recolor_opa(lock_icon, LV_OPA_0, 0);
    }
    if (unlocked_icon) {
      lv_obj_set_style_img_recolor_opa(unlocked_icon, LV_OPA_0, 0);
    }
  }

  // Update charging ring visibility and percentage
  if (is_charging) {
    if (!charging_ring_visible) {
      dot_ring_show(&charging_ring);
      charging_ring_visible = true;
    }
    dot_ring_color_t ring_color =
      (battery_percent == 100) ? DOT_RING_COLOR_GREEN : DOT_RING_COLOR_WHITE;
    dot_ring_set_percent(&charging_ring, battery_percent, ring_color, DOT_RING_FILL_CLOCKWISE);
  } else if (battery_percent <= 10) {
    // Show red ring when battery is critically low (not charging)
    if (!charging_ring_visible) {
      dot_ring_show(&charging_ring);
      charging_ring_visible = true;
    }
    dot_ring_set_percent(&charging_ring, battery_percent, DOT_RING_COLOR_RED,
                         DOT_RING_FILL_CLOCKWISE);
  } else {
    if (charging_ring_visible) {
      dot_ring_hide(&charging_ring);
      dot_ring_reset(&charging_ring);
      charging_ring_visible = false;
    }
  }
}
