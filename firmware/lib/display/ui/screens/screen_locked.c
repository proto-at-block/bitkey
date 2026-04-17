#include "screen_locked.h"

#include "assert.h"
#include "display.pb.h"
#include "dot_ring.h"
#include "langpack.h"
#include "ui.h"

#include <stdio.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Battery state thresholds
#define BATTERY_LOW_THRESHOLD_PERCENT 10
#define BATTERY_FULL_PERCENT          100

// Layout configuration
#define BATTERY_Y_OFFSET     (-30)
#define BATTERY_ICON_SPACING 5  // Space between battery icon and percentage text

// Error overlay layout
#define ERROR_ICON_Y_OFFSET  (-30)
#define ERROR_LABEL_Y_OFFSET 30

// Colors
#define COLOR_BATTERY_FULL_CHARGING \
  lv_color_make(0xD1, 0xFB, 0x96)                                   // Lime green (matches dot ring)
#define COLOR_BATTERY_CHARGING     lv_color_make(0xFF, 0xFF, 0xFF)  // White
#define COLOR_BATTERY_LOW          lv_color_make(0xF8, 0x47, 0x52)  // Red (#F84752)
#define COLOR_BATTERY_PERCENT_TEXT lv_color_make(0xAD, 0xAD, 0xAD)  // Grey

// Fonts
#define FONT_BATTERY (&cash_sans_mono_regular_26)
#define FONT_ERROR   (&cash_sans_mono_regular_30)

// External image declarations
extern const lv_img_dsc_t locked;
extern const lv_img_dsc_t unlocked;
extern const lv_img_dsc_t exclamation_circle;
extern const lv_img_dsc_t battery_10;
extern const lv_img_dsc_t battery_charging;

static lv_obj_t* screen = NULL;
static lv_obj_t* lock_icon = NULL;
static lv_obj_t* unlocked_icon = NULL;
static lv_obj_t* battery_container = NULL;
static lv_obj_t* battery_icon = NULL;
static lv_obj_t* battery_percent_label = NULL;
static dot_ring_t charging_ring = {0};
static bool charging_ring_visible = false;
static lv_obj_t* error_icon = NULL;
static lv_obj_t* error_label = NULL;

static bool is_low_battery(uint8_t percent) {
  return percent <= BATTERY_LOW_THRESHOLD_PERCENT;
}

static bool is_fully_charged(uint8_t percent) {
  return percent == BATTERY_FULL_PERCENT;
}

static bool should_show_battery_icon(uint8_t percent) {
  return is_low_battery(percent);
}

static lv_color_t get_battery_indicator_color(uint8_t percent, bool is_charging) {
  if (is_charging && is_fully_charged(percent)) {
    return COLOR_BATTERY_FULL_CHARGING;
  } else if (is_charging) {
    return COLOR_BATTERY_CHARGING;
  }

  return is_low_battery(percent) ? COLOR_BATTERY_LOW : COLOR_BATTERY_PERCENT_TEXT;
}

static void update_battery_indicator(uint8_t battery_percent, bool is_charging) {
  lv_color_t indicator_color = get_battery_indicator_color(battery_percent, is_charging);

  if (battery_icon) {
    if (is_charging) {
      lv_img_set_src(battery_icon, &battery_charging);
      lv_img_set_zoom(battery_icon, LV_SCALE_NONE);
      lv_obj_set_style_img_recolor(battery_icon, indicator_color, 0);
      lv_obj_set_style_img_recolor_opa(battery_icon, LV_OPA_100, 0);
      lv_obj_clear_flag(battery_icon, LV_OBJ_FLAG_HIDDEN);
    } else if (should_show_battery_icon(battery_percent)) {
      lv_img_set_src(battery_icon, &battery_10);
      lv_img_set_zoom(battery_icon, LV_SCALE_NONE);
      lv_obj_set_style_img_recolor(battery_icon, indicator_color, 0);
      lv_obj_set_style_img_recolor_opa(battery_icon, LV_OPA_100, 0);
      lv_obj_clear_flag(battery_icon, LV_OBJ_FLAG_HIDDEN);
    } else {
      lv_obj_add_flag(battery_icon, LV_OBJ_FLAG_HIDDEN);
    }
  }

  if (battery_percent_label) {
    char percent_text[8];
    snprintf(percent_text, sizeof(percent_text), "%d%%", battery_percent);
    lv_label_set_text(battery_percent_label, percent_text);

    lv_obj_set_style_text_color(battery_percent_label, indicator_color, 0);
  }
}

static void update_lock_icon_color(uint8_t battery_percent, bool is_charging) {
  (void)battery_percent;
  (void)is_charging;

  if (lock_icon) {
    lv_obj_set_style_img_recolor(lock_icon, lv_color_white(), 0);
    lv_obj_set_style_img_recolor_opa(lock_icon, LV_OPA_COVER, 0);
  }
  if (unlocked_icon) {
    lv_obj_set_style_img_recolor(unlocked_icon, lv_color_white(), 0);
    lv_obj_set_style_img_recolor_opa(unlocked_icon, LV_OPA_COVER, 0);
  }
}

static void update_charging_ring(uint8_t battery_percent, bool is_charging) {
  // Update charging ring visibility and percentage
  if (is_charging) {
    if (!charging_ring_visible) {
      dot_ring_show(&charging_ring);
      charging_ring_visible = true;
    }
    dot_ring_color_t ring_color =
      is_fully_charged(battery_percent) ? DOT_RING_COLOR_GREEN : DOT_RING_COLOR_WHITE;
    dot_ring_set_percent(&charging_ring, battery_percent, ring_color, DOT_RING_FILL_CLOCKWISE);
  } else if (is_low_battery(battery_percent)) {
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
  lv_img_set_src(battery_icon, &battery_10);

  // Battery percentage label
  battery_percent_label = lv_label_create(battery_container);
  if (!battery_percent_label) {
    return NULL;
  }
  char percent_text[8];
  snprintf(percent_text, sizeof(percent_text), "%d%%", battery_percent);
  lv_label_set_text(battery_percent_label, percent_text);
  lv_obj_set_style_text_font(battery_percent_label, FONT_BATTERY, 0);

  update_battery_indicator(battery_percent, is_charging);
  update_lock_icon_color(battery_percent, is_charging);

  // Create charging ring (initially hidden)
  dot_ring_create(screen, &charging_ring);
  charging_ring_visible = false;
  update_charging_ring(battery_percent, is_charging);

  // Error overlay: exclamation icon + "Try again" label (initially hidden)
  error_icon = lv_img_create(screen);
  if (!error_icon) {
    return NULL;
  }
  lv_img_set_src(error_icon, &exclamation_circle);
  lv_obj_set_style_img_recolor(error_icon, lv_color_white(), 0);
  lv_obj_set_style_img_recolor_opa(error_icon, LV_OPA_COVER, 0);
  lv_obj_align(error_icon, LV_ALIGN_CENTER, 0, ERROR_ICON_Y_OFFSET);
  lv_obj_add_flag(error_icon, LV_OBJ_FLAG_HIDDEN);

  error_label = lv_label_create(screen);
  if (!error_label) {
    return NULL;
  }
  lv_label_set_text(error_label, langpack_get_string(LANGPACK_ID_LOCKED_ERROR));
  lv_obj_set_style_text_color(error_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(error_label, FONT_ERROR, 0);
  lv_obj_set_style_text_align(error_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(error_label, LV_ALIGN_CENTER, 0, ERROR_LABEL_Y_OFFSET);
  lv_obj_add_flag(error_label, LV_OBJ_FLAG_HIDDEN);

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
  error_icon = NULL;
  error_label = NULL;
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
  bool show_error = show_screen->params.locked.show_error;

  // Handle error overlay
  if (show_error) {
    // Hide normal screen elements
    if (lock_icon) {
      lv_obj_add_flag(lock_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (unlocked_icon) {
      lv_obj_add_flag(unlocked_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (battery_container) {
      lv_obj_add_flag(battery_container, LV_OBJ_FLAG_HIDDEN);
    }
    if (charging_ring_visible) {
      dot_ring_hide(&charging_ring);
      charging_ring_visible = false;
    }

    // Show error elements
    if (error_icon) {
      lv_obj_clear_flag(error_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (error_label) {
      lv_obj_clear_flag(error_label, LV_OBJ_FLAG_HIDDEN);
    }
    return;
  }

  // Hide error elements when not in error state
  if (error_icon) {
    lv_obj_add_flag(error_icon, LV_OBJ_FLAG_HIDDEN);
  }
  if (error_label) {
    lv_obj_add_flag(error_label, LV_OBJ_FLAG_HIDDEN);
  }

  // Restore battery container visibility
  if (battery_container) {
    lv_obj_clear_flag(battery_container, LV_OBJ_FLAG_HIDDEN);
  }

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

  update_battery_indicator(battery_percent, is_charging);
  update_lock_icon_color(battery_percent, is_charging);
  update_charging_ring(battery_percent, is_charging);
}
