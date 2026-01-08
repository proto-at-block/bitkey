#include "screen_menu_fingerprints.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration
#define MENU_START_Y     50
#define ITEM_HEIGHT      60
#define ITEM_SPACING     15
#define ICON_SIZE        32
#define ICON_TEXT_GAP    16
#define PILL_RADIUS      30
#define PILL_PADDING_H   20
#define PILL_PADDING_V   10
#define MAX_FINGERPRINTS 3

// Bounce animation configuration
#define BOUNCE_DURATION_MS     150
#define BOUNCE_PLAYBACK_MS     150
#define BOUNCE_DISTANCE_PIXELS 15

// Highlight animation configuration
#define HIGHLIGHT_DURATION_MS 600

// Menu item indexing (item 0 is back button, items 1-3 are fingerprints)
#define ITEM_BACK          0
#define ITEM_COUNT         (MAX_FINGERPRINTS + 1)
#define FP_TO_ITEM(fp_idx) ((fp_idx) + 1)
#define ITEM_TO_FP(item)   ((item)-1)
#define IS_BACK_ITEM(item) ((item) == ITEM_BACK)
#define IS_FP_ITEM(item)   ((item) >= 1 && (item) <= MAX_FINGERPRINTS)

// Colors
#define PILL_BORDER_WIDTH 2
#define COLOR_ITEM_TEXT   0xFFFFFF
#define COLOR_EMPTY_TEXT  0x808080
#define COLOR_HIGHLIGHT   0x00FF00

// Fonts
#define FONT_ITEM (&cash_sans_mono_regular_20)

// External image declarations
extern const lv_img_dsc_t cycle_back;
extern const lv_img_dsc_t fingerprint;

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* item_containers[ITEM_COUNT];
static lv_obj_t* item_pills[ITEM_COUNT];
static lv_obj_t* item_icons[ITEM_COUNT];
static lv_obj_t* item_labels[ITEM_COUNT];

// Animations
static lv_anim_t bounce_anim;
static int8_t bounce_offset = 0;
static lv_anim_t highlight_anim;
static uint8_t highlight_index = 0xFF;  // Which fingerprint is being highlighted (0xFF = none)

static void bounce_anim_cb(void* var, int32_t value) {
  (void)var;
  int8_t new_offset = (int8_t)value;
  int8_t delta = new_offset - bounce_offset;
  bounce_offset = new_offset;

  // Apply bounce delta to all items
  for (uint8_t i = 0; i < ITEM_COUNT; i++) {
    if (item_containers[i] && !lv_obj_has_flag(item_containers[i], LV_OBJ_FLAG_HIDDEN)) {
      lv_coord_t current_y = lv_obj_get_y(item_containers[i]);
      lv_obj_set_y(item_containers[i], current_y + delta);
    }
  }
}

static void highlight_anim_exec_cb(void* var, int32_t value) {
  (void)var;

  if (highlight_index == 0xFF || highlight_index >= MAX_FINGERPRINTS) {
    return;
  }

  uint8_t item = FP_TO_ITEM(highlight_index);

  // value goes 0->255->0 with playback
  lv_color_t color = lv_color_mix(lv_color_hex(COLOR_HIGHLIGHT), lv_color_white(), value);

  if (item_labels[item]) {
    lv_obj_set_style_text_color(item_labels[item], color, 0);
  }
  if (item_icons[item]) {
    lv_obj_set_style_img_recolor(item_icons[item], color, 0);
  }
}

static void highlight_anim_complete_cb(lv_anim_t* anim) {
  (void)anim;

  // Restore white color
  if (highlight_index != 0xFF && highlight_index < MAX_FINGERPRINTS) {
    uint8_t item = FP_TO_ITEM(highlight_index);
    if (item_labels[item]) {
      lv_obj_set_style_text_color(item_labels[item], lv_color_white(), 0);
    }
    if (item_icons[item]) {
      lv_obj_set_style_img_recolor(item_icons[item], lv_color_white(), 0);
    }
  }

  highlight_index = 0xFF;
}

static void trigger_highlight_animation(uint8_t fp_idx) {
  lv_anim_del(&highlight_anim, NULL);

  highlight_index = fp_idx;

  // Animate color value: white (0) -> green (255) -> white (0)
  lv_anim_init(&highlight_anim);
  lv_anim_set_var(&highlight_anim, NULL);
  lv_anim_set_exec_cb(&highlight_anim, highlight_anim_exec_cb);
  lv_anim_set_values(&highlight_anim, 0, 255);
  lv_anim_set_time(&highlight_anim, HIGHLIGHT_DURATION_MS / 2);
  lv_anim_set_playback_time(&highlight_anim, HIGHLIGHT_DURATION_MS / 2);
  lv_anim_set_ready_cb(&highlight_anim, highlight_anim_complete_cb);
  lv_anim_start(&highlight_anim);
}

static void trigger_bounce_animation(bool at_top) {
  // Stop any existing animation and reset offset
  lv_anim_del(&bounce_anim, bounce_anim_cb);

  // Reset any existing offset first
  if (bounce_offset != 0) {
    for (uint8_t i = 0; i < ITEM_COUNT; i++) {
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

static void menu_fingerprints_update_selection_display(
  const fwpb_display_params_menu_fingerprints* params) {
  if (!params || !screen) {
    return;
  }

  // Update all items
  for (uint8_t item = 0; item < ITEM_COUNT; item++) {
    if (!item_pills[item]) {
      continue;
    }

    bool is_selected = (item == params->selected_item);

    if (is_selected) {
      // Selected state - show pill with white border and black background
      lv_obj_set_style_bg_opa(item_pills[item], LV_OPA_100, 0);
      lv_obj_set_style_bg_color(item_pills[item], lv_color_black(), 0);
      lv_obj_set_style_border_width(item_pills[item], PILL_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(item_pills[item], lv_color_white(), 0);

      if (item_labels[item]) {
        lv_obj_set_style_text_color(item_labels[item], lv_color_white(), 0);
      }
      if (item_icons[item]) {
        lv_obj_set_style_img_recolor(item_icons[item], lv_color_white(), 0);
        lv_obj_set_style_img_recolor_opa(item_icons[item], LV_OPA_100, 0);
      }
    } else {
      // Unselected state - no visible pill
      lv_obj_set_style_bg_opa(item_pills[item], LV_OPA_TRANSP, 0);
      lv_obj_set_style_border_width(item_pills[item], 0, 0);

      if (IS_FP_ITEM(item)) {
        uint8_t fp_idx = ITEM_TO_FP(item);
        bool is_enrolled = params->enrolled[fp_idx];

        if (item_labels[item]) {
          lv_color_t color = is_enrolled ? lv_color_white() : lv_color_hex(COLOR_EMPTY_TEXT);
          lv_obj_set_style_text_color(item_labels[item], color, 0);
        }
        if (item_icons[item]) {
          lv_color_t color = is_enrolled ? lv_color_white() : lv_color_hex(COLOR_EMPTY_TEXT);
          lv_obj_set_style_img_recolor(item_icons[item], color, 0);
          lv_obj_set_style_img_recolor_opa(item_icons[item], LV_OPA_100, 0);
        }
      } else {
        // Back button - always white when unselected
        if (item_icons[item]) {
          lv_obj_set_style_img_recolor(item_icons[item], lv_color_white(), 0);
          lv_obj_set_style_img_recolor_opa(item_icons[item], LV_OPA_100, 0);
        }
      }
    }
  }

  // Handle bounce animations
  if (params->hit_top) {
    trigger_bounce_animation(true);
  } else if (params->hit_bottom) {
    trigger_bounce_animation(false);
  }

  // Handle highlight animation - trigger green flash
  if (params->show_authenticated && params->authenticated_index < MAX_FINGERPRINTS) {
    trigger_highlight_animation(params->authenticated_index);
  }
}

lv_obj_t* screen_menu_fingerprints_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_menu_fingerprints* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_fingerprints_tag) {
    params = &show_screen->params.menu_fingerprints;
  }

  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Initialize arrays
  for (uint8_t i = 0; i < ITEM_COUNT; i++) {
    item_containers[i] = NULL;
    item_pills[i] = NULL;
    item_icons[i] = NULL;
    item_labels[i] = NULL;
  }

  // Create all menu items (back button + fingerprints)
  for (uint8_t item = 0; item < ITEM_COUNT; item++) {
    lv_coord_t y_pos = MENU_START_Y + (item * (ITEM_HEIGHT + ITEM_SPACING));

    item_containers[item] = lv_obj_create(screen);
    lv_obj_remove_style_all(item_containers[item]);
    lv_obj_set_size(item_containers[item], LV_PCT(100), ITEM_HEIGHT);
    lv_obj_set_pos(item_containers[item], 0, y_pos);

    item_pills[item] = lv_obj_create(item_containers[item]);
    lv_obj_remove_style_all(item_pills[item]);
    lv_obj_set_size(item_pills[item], LV_SIZE_CONTENT, ITEM_HEIGHT);
    lv_obj_set_style_radius(item_pills[item], PILL_RADIUS, 0);
    lv_obj_set_style_bg_opa(item_pills[item], LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(item_pills[item], 0, 0);
    lv_obj_set_style_pad_hor(item_pills[item], PILL_PADDING_H, 0);
    lv_obj_set_style_pad_ver(item_pills[item], PILL_PADDING_V, 0);
    lv_obj_center(item_pills[item]);

    lv_obj_t* content = lv_obj_create(item_pills[item]);
    lv_obj_remove_style_all(content);
    lv_obj_set_size(content, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
    lv_obj_set_layout(content, LV_LAYOUT_FLEX);
    lv_obj_set_flex_flow(content, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(content, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                          LV_FLEX_ALIGN_CENTER);
    lv_obj_center(content);

    if (IS_BACK_ITEM(item)) {
      // Back button
      item_icons[item] = lv_img_create(content);
      lv_img_set_src(item_icons[item], &cycle_back);
      lv_obj_set_style_img_recolor(item_icons[item], lv_color_white(), 0);
      lv_obj_set_style_img_recolor_opa(item_icons[item], LV_OPA_100, 0);
    } else {
      // Fingerprint slot
      uint8_t fp_idx = ITEM_TO_FP(item);

      lv_obj_set_style_pad_column(content, ICON_TEXT_GAP, 0);

      item_icons[item] = lv_img_create(content);
      lv_img_set_src(item_icons[item], &fingerprint);

      item_labels[item] = lv_label_create(content);
      char label_text[64];
      if (params && params->enrolled[fp_idx]) {
        strncpy(label_text, params->labels[fp_idx], sizeof(label_text) - 1);
        label_text[sizeof(label_text) - 1] = '\0';
        lv_obj_set_style_img_recolor(item_icons[item], lv_color_white(), 0);
        lv_obj_set_style_img_recolor_opa(item_icons[item], LV_OPA_100, 0);
        lv_obj_set_style_text_color(item_labels[item], lv_color_white(), 0);
      } else {
        snprintf(label_text, sizeof(label_text),
                 langpack_get_string(LANGPACK_ID_FINGERPRINT_MENU_ADD), fp_idx + 1);
        lv_obj_set_style_img_recolor(item_icons[item], lv_color_hex(COLOR_EMPTY_TEXT), 0);
        lv_obj_set_style_img_recolor_opa(item_icons[item], LV_OPA_100, 0);
        lv_obj_set_style_text_color(item_labels[item], lv_color_hex(COLOR_EMPTY_TEXT), 0);
      }
      lv_label_set_text(item_labels[item], label_text);
      lv_obj_set_style_text_font(item_labels[item], FONT_ITEM, 0);
    }
  }

  if (params) {
    menu_fingerprints_update_selection_display(params);
  }

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_menu_fingerprints_destroy(void) {
  if (!screen) {
    return;
  }

  // Stop any running animations
  lv_anim_del(&bounce_anim, bounce_anim_cb);
  lv_anim_del(&highlight_anim, NULL);

  lv_obj_del(screen);

  // Clear all static references
  screen = NULL;
  for (uint8_t i = 0; i < ITEM_COUNT; i++) {
    item_containers[i] = NULL;
    item_pills[i] = NULL;
    item_icons[i] = NULL;
    item_labels[i] = NULL;
  }

  bounce_offset = 0;
  highlight_index = 0xFF;
}

void screen_menu_fingerprints_update(void* ctx) {
  if (!screen) {
    screen_menu_fingerprints_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_menu_fingerprints_tag) {
    return;
  }

  const fwpb_display_params_menu_fingerprints* params = &show_screen->params.menu_fingerprints;

  // Update fingerprint labels based on enrollment status
  for (uint8_t fp_idx = 0; fp_idx < MAX_FINGERPRINTS; fp_idx++) {
    uint8_t item = FP_TO_ITEM(fp_idx);
    if (!item_labels[item]) {
      continue;
    }

    char label_text[64];
    if (params->enrolled[fp_idx]) {
      strncpy(label_text, params->labels[fp_idx], sizeof(label_text) - 1);
      label_text[sizeof(label_text) - 1] = '\0';
    } else {
      snprintf(label_text, sizeof(label_text),
               langpack_get_string(LANGPACK_ID_FINGERPRINT_MENU_ADD), fp_idx + 1);
    }
    lv_label_set_text(item_labels[item], label_text);
  }

  menu_fingerprints_update_selection_display(params);
}
