#include "screen_menu.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "lvgl/lvgl.h"

#include <stdio.h>
#include <string.h>

// Define menu item constants for easier reference
#define MENU_ITEM_BACK         fwpb_display_menu_item_DISPLAY_MENU_ITEM_BACK
#define MENU_ITEM_FINGERPRINTS fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS
#define MENU_ITEM_BRIGHTNESS   fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS
#define MENU_ITEM_ABOUT        fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT
#define MENU_ITEM_REGULATORY   fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY
#define MENU_ITEM_LOCK_DEVICE  fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE
#define MENU_ITEM_POWER_OFF    fwpb_display_menu_item_DISPLAY_MENU_ITEM_POWER_OFF
#ifdef MFGTEST
#define MENU_ITEM_TOUCH_TEST fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST
#define MENU_ITEM_COUNT      8
#else
#define MENU_ITEM_COUNT 7
#endif

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define MENU_START_Y         50
#define ITEM_HEIGHT          60
#define ITEM_SPACING         15
#define ICON_SIZE            32
#define ICON_TEXT_GAP        16
#define PILL_RADIUS          30
#define PILL_PADDING_H       20
#define PILL_PADDING_V       10
#define VISIBLE_ITEMS        5
#define SELECTION_ANCHOR_ROW 2

// Bounce animation configuration
#define BOUNCE_DURATION_MS     150  // Duration of bounce animation
#define BOUNCE_PLAYBACK_MS     150  // Duration of bounce-back
#define BOUNCE_DISTANCE_PIXELS 15   // How far to bounce

// Fade effect configuration
#define FADE_OPACITY LV_OPA_50

// Colors
#define PILL_BORDER_WIDTH 2

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_22)
#define FONT_ITEM  (&cash_sans_mono_regular_20)

// Menu item data
typedef struct {
  langpack_string_id_t label_id;
  const lv_img_dsc_t* icon;
} menu_item_data_t;

// External image declarations
extern const lv_img_dsc_t cycle_back;
extern const lv_img_dsc_t brightness;
extern const lv_img_dsc_t fingerprint;
extern const lv_img_dsc_t info_circle;
extern const lv_img_dsc_t paper_ribbon;
extern const lv_img_dsc_t lock;
extern const lv_img_dsc_t power;

static const menu_item_data_t menu_items[MENU_ITEM_COUNT] = {
  [MENU_ITEM_BACK] = {LANGPACK_ID_MENU_BACK, &cycle_back},
  [MENU_ITEM_FINGERPRINTS] = {LANGPACK_ID_MENU_FINGERPRINTS, &fingerprint},
  [MENU_ITEM_BRIGHTNESS] = {LANGPACK_ID_MENU_BRIGHTNESS, &brightness},
  [MENU_ITEM_ABOUT] = {LANGPACK_ID_MENU_ABOUT, &info_circle},
  [MENU_ITEM_REGULATORY] = {LANGPACK_ID_MENU_REGULATORY, &paper_ribbon},
  [MENU_ITEM_LOCK_DEVICE] = {LANGPACK_ID_MENU_LOCK, &lock},
  [MENU_ITEM_POWER_OFF] = {LANGPACK_ID_MENU_OFF, &power},
#ifdef MFGTEST
  [MENU_ITEM_TOUCH_TEST] = {LANGPACK_ID_MENU_TOUCH_TEST, &fingerprint},
#endif
};

static lv_obj_t* screen = NULL;
static lv_obj_t* item_containers[MENU_ITEM_COUNT];
static lv_obj_t* item_pills[MENU_ITEM_COUNT];
static lv_obj_t* item_icons[MENU_ITEM_COUNT];
static lv_obj_t* item_labels[MENU_ITEM_COUNT];
static lv_anim_t bounce_anim;
static int8_t bounce_offset = 0;

static void bounce_anim_cb(void* var, int32_t value) {
  (void)var;
  int8_t new_offset = (int8_t)value;
  int8_t delta = new_offset - bounce_offset;
  bounce_offset = new_offset;

  // Apply bounce delta to all visible items
  for (uint8_t i = 0; i < MENU_ITEM_COUNT; i++) {
    if (item_containers[i] && !lv_obj_has_flag(item_containers[i], LV_OBJ_FLAG_HIDDEN)) {
      lv_coord_t current_y = lv_obj_get_y(item_containers[i]);
      lv_obj_set_y(item_containers[i], current_y + delta);
    }
  }
}

static void trigger_bounce_animation(bool at_top) {
  // Stop any existing animation and reset offset
  lv_anim_del(&bounce_anim, bounce_anim_cb);

  // Reset any existing offset first
  if (bounce_offset != 0) {
    for (uint8_t i = 0; i < MENU_ITEM_COUNT; i++) {
      if (item_containers[i] && !lv_obj_has_flag(item_containers[i], LV_OBJ_FLAG_HIDDEN)) {
        lv_coord_t current_y = lv_obj_get_y(item_containers[i]);
        lv_obj_set_y(item_containers[i], current_y - bounce_offset);
      }
    }
    bounce_offset = 0;
  }

  // Configure bounce animation
  lv_anim_init(&bounce_anim);
  lv_anim_set_var(&bounce_anim, NULL);
  lv_anim_set_exec_cb(&bounce_anim, bounce_anim_cb);
  lv_anim_set_time(&bounce_anim, BOUNCE_DURATION_MS);
  lv_anim_set_path_cb(&bounce_anim, lv_anim_path_ease_out);

  // Bounce direction based on boundary hit
  if (at_top) {
    lv_anim_set_values(&bounce_anim, 0, BOUNCE_DISTANCE_PIXELS);  // Bounce down
  } else {
    lv_anim_set_values(&bounce_anim, 0, -BOUNCE_DISTANCE_PIXELS);  // Bounce up
  }

  lv_anim_set_playback_time(&bounce_anim, BOUNCE_PLAYBACK_MS);
  lv_anim_start(&bounce_anim);
}

static void menu_update_selection_display(const fwpb_display_params_menu* params) {
  if (!params || !screen) {
    return;
  }

  // Calculate scroll offset based on selected item position
  // Scrolling starts when moving down past SELECTION_ANCHOR_ROW (item 2)
  // Scrolling stops when moving up to item 1 or below
  int8_t scroll_offset = 0;
  if (params->selected_item >= SELECTION_ANCHOR_ROW) {
    scroll_offset = params->selected_item - SELECTION_ANCHOR_ROW;
    // Don't scroll past the end
    if (scroll_offset > (MENU_ITEM_COUNT - VISIBLE_ITEMS)) {
      scroll_offset = MENU_ITEM_COUNT - VISIBLE_ITEMS;
    }
  }

  // Update positions and visibility
  for (uint8_t i = 0; i < MENU_ITEM_COUNT; i++) {
    if (!item_pills[i] || !item_containers[i]) {
      continue;
    }

    // Calculate display position relative to scroll offset
    int8_t display_position = i - scroll_offset;

    // Position and show/hide based on whether item is in visible range
    if (display_position >= 0 && display_position < VISIBLE_ITEMS) {
      lv_obj_set_pos(item_containers[i], 0,
                     MENU_START_Y + display_position * (ITEM_HEIGHT + ITEM_SPACING));
      lv_obj_clear_flag(item_containers[i], LV_OBJ_FLAG_HIDDEN);

      // Apply fade effect to first and last visible items when scrolled
      lv_opa_t opacity = LV_OPA_COVER;
      if (display_position == 0 && scroll_offset > 0) {
        // First visible item when there are items above
        opacity = FADE_OPACITY;
      } else if (display_position == VISIBLE_ITEMS - 1 &&
                 scroll_offset < (MENU_ITEM_COUNT - VISIBLE_ITEMS)) {
        // Last visible item when there are items below
        opacity = FADE_OPACITY;
      }
      lv_obj_set_style_opa(item_containers[i], opacity, 0);
    } else {
      lv_obj_add_flag(item_containers[i], LV_OBJ_FLAG_HIDDEN);
    }

    // Show pill border only for selected item
    if (i == params->selected_item) {
      lv_obj_set_style_border_width(item_pills[i], PILL_BORDER_WIDTH, 0);
    } else {
      lv_obj_set_style_border_width(item_pills[i], 0, 0);
    }
  }
}

void screen_menu_update(void* ctx) {
  if (!screen) {
    screen_menu_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_menu_tag) {
    return;
  }

  const fwpb_display_params_menu* params = &show_screen->params.menu;

  // Check for boundary hits and trigger bounce animation
  if (params->hit_top) {
    trigger_bounce_animation(true);
  } else if (params->hit_bottom) {
    trigger_bounce_animation(false);
  }

  menu_update_selection_display(params);
}

lv_obj_t* screen_menu_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_menu* params = NULL;

  static fwpb_display_params_menu default_params = {
    .selected_item = 0,
  };

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_tag) {
    params = &show_screen->params.menu;
  } else {
    params = &default_params;
  }

  // Create the screen
  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Create menu items
  for (uint8_t i = 0; i < MENU_ITEM_COUNT; i++) {
    lv_coord_t y_pos = MENU_START_Y + (i * (ITEM_HEIGHT + ITEM_SPACING));

    // Create container for this item
    item_containers[i] = lv_obj_create(screen);
    lv_obj_set_size(item_containers[i], LV_SIZE_CONTENT, ITEM_HEIGHT);
    lv_obj_set_pos(item_containers[i], 0, y_pos);
    lv_obj_align(item_containers[i], LV_ALIGN_TOP_MID, 0, y_pos);
    lv_obj_set_style_bg_opa(item_containers[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(item_containers[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(item_containers[i], 0, 0);
    lv_obj_clear_flag(item_containers[i], LV_OBJ_FLAG_SCROLLABLE);

    // Create pill background (oval)
    item_pills[i] = lv_obj_create(item_containers[i]);
    lv_obj_set_size(item_pills[i], LV_SIZE_CONTENT, ITEM_HEIGHT);
    lv_obj_center(item_pills[i]);
    lv_obj_set_style_radius(item_pills[i], PILL_RADIUS, 0);
    lv_obj_set_style_bg_opa(item_pills[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_color(item_pills[i], lv_color_white(), 0);
    lv_obj_set_style_border_width(item_pills[i], 0, 0);  // Initially hidden
    lv_obj_set_style_border_opa(item_pills[i], LV_OPA_COVER, 0);
    lv_obj_set_style_pad_hor(item_pills[i], PILL_PADDING_H, 0);
    lv_obj_set_style_pad_ver(item_pills[i], PILL_PADDING_V, 0);
    lv_obj_set_layout(item_pills[i], LV_LAYOUT_FLEX);
    lv_obj_set_flex_flow(item_pills[i], LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(item_pills[i], LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                          LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_column(item_pills[i], ICON_TEXT_GAP, 0);
    lv_obj_clear_flag(item_pills[i], LV_OBJ_FLAG_SCROLLABLE);

    // Create icon
    item_icons[i] = lv_img_create(item_pills[i]);
    lv_img_set_src(item_icons[i], menu_items[i].icon);

    // Create label
    item_labels[i] = lv_label_create(item_pills[i]);
    lv_label_set_text(item_labels[i], langpack_get_string(menu_items[i].label_id));
    lv_obj_set_style_text_color(item_labels[i], lv_color_white(), 0);
    lv_obj_set_style_text_font(item_labels[i], FONT_ITEM, 0);
  }

  // Update initial selection display
  menu_update_selection_display(params);

  return screen;
}

void screen_menu_destroy(void) {
  if (!screen) {
    return;
  }

  // Stop any ongoing bounce animation
  lv_anim_del(&bounce_anim, bounce_anim_cb);
  bounce_offset = 0;

  lv_obj_del(screen);
  // Clear all static references
  screen = NULL;
  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    item_containers[i] = NULL;
    item_pills[i] = NULL;
    item_icons[i] = NULL;
    item_labels[i] = NULL;
  }
}
