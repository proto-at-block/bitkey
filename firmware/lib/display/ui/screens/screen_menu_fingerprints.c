#include "screen_menu_fingerprints.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "hold_cancel.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "top_back.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100
#define TITLE_Y_OFFSET    90
#define ICON_SIZE         64
#define ITEM_LABEL_Y      120
#define MAX_FINGERPRINTS  3

// Carousel layout configuration
#define ITEM_WIDTH           220
#define OPACITY_SELECTED     LV_OPA_COVER
#define OPACITY_DIMMED       LV_OPA_40
#define ANIM_DURATION        300
#define SCROLL_ANIM_TIME     150
#define ICON_CIRCLE_SIZE     80
#define ICON_CIRCLE_COLOR    0x404040  // Gray
#define COLOR_GREEN          0xD1FB96  // Green for pulse animation
#define GREEN_PULSE_DURATION 200
#define GREEN_PULSE_CYCLES   2

// Colors
#define COLOR_TITLE      0xADADAD
#define COLOR_ITEM_TEXT  0xFFFFFF
#define COLOR_EMPTY_TEXT 0x808080

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_24)
#define FONT_ITEM  (&cash_sans_mono_regular_28)

// External image declaration
extern const lv_img_dsc_t fingerprint;

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* item_containers[MAX_FINGERPRINTS] = {NULL};
static lv_obj_t* item_icon_circles[MAX_FINGERPRINTS] = {NULL};  // Circle backgrounds
static lv_obj_t* item_icons[MAX_FINGERPRINTS] = {NULL};
static lv_obj_t* item_labels[MAX_FINGERPRINTS] = {NULL};
static top_back_t back_button = {0};
static int current_item = 0;

// Animation context for green pulse
static lv_obj_t* pulse_target_obj = NULL;
static lv_color_t pulse_original_color = {0};
static int pulse_slot_index = 0;
static bool pulse_active = false;
static uint8_t pulse_cycle_count = 0;

// hold_cancel widget for deletion
static hold_cancel_t cancel_modal;
static uint8_t cached_fingerprint_index = 0;
static bool enrolled_cache[MAX_FINGERPRINTS] = {false};

// Forward declarations
static void fingerprint_item_click_handler(lv_event_t* e);
static void scroll_to_item(int index, bool animate);
static void scroll_event_handler(lv_event_t* e);
static void update_item_styles_by_position(void);
static void start_green_pulse(int slot_index);
static void green_pulse_anim_cb(void* var, int32_t value);
static void green_pulse_complete_cb(lv_anim_t* anim);
static void on_cancel_complete(void* user_data);
static void on_cancel_dismiss(void* user_data);

static void update_item_styles_by_position(void) {
  if (!scroll_container) {
    return;
  }

  lv_coord_t scroll_x = lv_obj_get_scroll_x(scroll_container);
  lv_coord_t screen_center = LV_HOR_RES / 2;
  lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;

  for (int i = 0; i < MAX_FINGERPRINTS; i++) {
    lv_coord_t item_x = side_padding + (i * ITEM_WIDTH);
    lv_coord_t item_center = item_x + (ITEM_WIDTH / 2) - scroll_x;
    lv_coord_t distance =
      (item_center > screen_center) ? (item_center - screen_center) : (screen_center - item_center);

    // Calculate opacity: full at center, fades to 40% away
    lv_opa_t opacity;
    if (distance < ITEM_WIDTH / 2) {
      uint32_t opacity_range = OPACITY_SELECTED - OPACITY_DIMMED;
      uint32_t fade = (distance * opacity_range) / (ITEM_WIDTH / 2);
      opacity = OPACITY_SELECTED - fade;
    } else {
      opacity = OPACITY_DIMMED;
    }

    lv_obj_set_style_opa(item_icon_circles[i], opacity, 0);
    lv_obj_set_style_opa(item_icons[i], opacity, 0);
    lv_obj_set_style_text_opa(item_labels[i], opacity, 0);
  }
}

static void scroll_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SCROLL) {
    update_item_styles_by_position();
  } else if (code == LV_EVENT_SCROLL_END) {
    lv_coord_t scroll_x = lv_obj_get_scroll_x(scroll_container);
    lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;
    lv_coord_t screen_center = LV_HOR_RES / 2;

    int centered_item = 0;
    lv_coord_t min_distance = LV_COORD_MAX;

    for (int i = 0; i < MAX_FINGERPRINTS; i++) {
      lv_coord_t item_x = side_padding + (i * ITEM_WIDTH);
      lv_coord_t item_center = item_x + (ITEM_WIDTH / 2) - scroll_x;
      lv_coord_t distance = (item_center > screen_center) ? (item_center - screen_center)
                                                          : (screen_center - item_center);

      if (distance < min_distance) {
        min_distance = distance;
        centered_item = i;
      }
    }

    current_item = centered_item;
    update_item_styles_by_position();
  }
}

static void fingerprint_item_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  uint32_t fp_index = (uint32_t)(uintptr_t)lv_event_get_user_data(e);

  if (fp_index < MAX_FINGERPRINTS) {
    // If not centered, scroll to center first
    if (fp_index != (uint32_t)current_item) {
      scroll_to_item(fp_index, true);
      return;
    }

    // Already centered - trigger action
    if (enrolled_cache[fp_index]) {
      // Check if this is the last enrolled fingerprint
      uint8_t enrolled_count = 0;
      for (uint8_t i = 0; i < MAX_FINGERPRINTS; i++) {
        if (enrolled_cache[i]) {
          enrolled_count++;
        }
      }

      if (enrolled_count <= 1) {
        // Last fingerprint - can't delete
        return;
      }

      // Show delete modal
      cached_fingerprint_index = (uint8_t)fp_index;
      hold_cancel_show_with_text(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL,
                                 "Remove", "Removed");
    } else {
      // Empty slot - start enrollment
      display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT, fp_index);
    }
  }
}

static void scroll_to_item(int index, bool animate) {
  if (!scroll_container || index < 0 || index >= MAX_FINGERPRINTS) {
    return;
  }

  current_item = index;
  lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;
  lv_coord_t item_x = side_padding + (index * ITEM_WIDTH);
  lv_coord_t scroll_x = item_x + (ITEM_WIDTH / 2) - (LV_HOR_RES / 2);

  lv_obj_scroll_to_x(scroll_container, scroll_x, animate ? LV_ANIM_ON : LV_ANIM_OFF);
  update_item_styles_by_position();
}

static void green_pulse_complete_cb(lv_anim_t* anim) {
  (void)anim;

  if (!pulse_target_obj) {
    return;
  }

  pulse_cycle_count++;

  if (pulse_cycle_count < GREEN_PULSE_CYCLES) {
    lv_anim_t next_anim;
    lv_anim_init(&next_anim);
    lv_anim_set_var(&next_anim, NULL);
    lv_anim_set_exec_cb(&next_anim, green_pulse_anim_cb);
    lv_anim_set_values(&next_anim, 0, 100);
    lv_anim_set_duration(&next_anim, GREEN_PULSE_DURATION);
    lv_anim_set_path_cb(&next_anim, lv_anim_path_ease_in_out);
    lv_anim_set_completed_cb(&next_anim, green_pulse_complete_cb);
    lv_anim_start(&next_anim);
  } else {
    // Restore original color
    lv_obj_set_style_bg_color(pulse_target_obj, pulse_original_color, 0);
    pulse_active = false;
    pulse_target_obj = NULL;
    pulse_cycle_count = 0;
  }
}

static void green_pulse_anim_cb(void* var, int32_t value) {
  (void)var;

  if (!pulse_target_obj) {
    return;
  }

  // Fade gray -> green -> gray
  if (value <= 50) {
    lv_obj_set_style_bg_color(pulse_target_obj, lv_color_hex(COLOR_GREEN), 0);
  } else {
    lv_obj_set_style_bg_color(pulse_target_obj, pulse_original_color, 0);
  }
}

static void start_green_pulse(int slot_index) {
  if (pulse_active || slot_index < 0 || slot_index >= MAX_FINGERPRINTS) {
    return;
  }

  if (!item_icon_circles[slot_index]) {
    return;
  }

  pulse_active = true;
  pulse_cycle_count = 0;
  pulse_target_obj = item_icon_circles[slot_index];
  pulse_original_color = lv_color_hex(ICON_CIRCLE_COLOR);
  pulse_slot_index = slot_index;

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, NULL);
  lv_anim_set_exec_cb(&anim, green_pulse_anim_cb);
  lv_anim_set_values(&anim, 0, 100);
  lv_anim_set_duration(&anim, GREEN_PULSE_DURATION);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_in_out);
  lv_anim_set_completed_cb(&anim, green_pulse_complete_cb);
  lv_anim_start(&anim);
}

static void on_cancel_complete(void* user_data) {
  (void)user_data;
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_DELETE_FINGERPRINT,
                      cached_fingerprint_index);
}

static void on_cancel_dismiss(void* user_data) {
  (void)user_data;
  hold_cancel_hide(&cancel_modal);
}

lv_obj_t* screen_menu_fingerprints_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_menu_fingerprints* params = NULL;

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_fingerprints_tag) {
    params = &show_screen->params.menu_fingerprints;
  }

  ASSERT(screen == NULL);

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  memset(&cancel_modal, 0, sizeof(cancel_modal));
  hold_cancel_create(screen, &cancel_modal);

  // Cache enrollment status
  if (params) {
    for (uint8_t i = 0; i < MAX_FINGERPRINTS; i++) {
      enrolled_cache[i] = params->enrolled[i];
    }
  }

  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(screen, &back_button, NULL);

  // Title
  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_MENU_TITLE));
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);

  // Scroll container
  scroll_container = lv_obj_create(screen);
  if (!scroll_container) {
    return NULL;
  }
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES);
  lv_obj_set_pos(scroll_container, 0, 0);
  lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(scroll_container, 0, 0);
  lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_CLICKABLE);

  lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;
  lv_obj_set_style_pad_left(scroll_container, side_padding, 0);
  lv_obj_set_style_pad_right(scroll_container, side_padding, 0);
  lv_obj_set_scroll_dir(scroll_container, LV_DIR_HOR);
  lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
  lv_obj_set_scroll_snap_x(scroll_container, LV_SCROLL_SNAP_CENTER);
  lv_obj_set_style_anim_time(scroll_container, SCROLL_ANIM_TIME, LV_PART_MAIN);
  lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);

  lv_obj_add_event_cb(scroll_container, scroll_event_handler, LV_EVENT_SCROLL, NULL);
  lv_obj_add_event_cb(scroll_container, scroll_event_handler, LV_EVENT_SCROLL_END, NULL);

  // Create fingerprint slots
  for (uint8_t i = 0; i < MAX_FINGERPRINTS; i++) {
    // Item container
    item_containers[i] = lv_obj_create(scroll_container);
    if (!item_containers[i]) {
      return NULL;
    }
    lv_obj_set_size(item_containers[i], ITEM_WIDTH, LV_VER_RES);
    lv_obj_set_style_bg_opa(item_containers[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(item_containers[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(item_containers[i], 0, 0);
    lv_obj_clear_flag(item_containers[i], LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_clear_flag(item_containers[i], LV_OBJ_FLAG_CLICKABLE);

    // Icon circle background
    item_icon_circles[i] = lv_obj_create(item_containers[i]);
    if (!item_icon_circles[i]) {
      return NULL;
    }
    lv_obj_set_size(item_icon_circles[i], ICON_CIRCLE_SIZE, ICON_CIRCLE_SIZE);
    lv_obj_set_style_radius(item_icon_circles[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(item_icon_circles[i], lv_color_hex(ICON_CIRCLE_COLOR), 0);
    lv_obj_set_style_bg_opa(item_icon_circles[i], LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(item_icon_circles[i], 0, 0);
    lv_obj_set_style_pad_all(item_icon_circles[i], 0, 0);
    lv_obj_align(item_icon_circles[i], LV_ALIGN_CENTER, 0, -20);
    lv_obj_add_flag(item_icon_circles[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(item_icon_circles[i], fingerprint_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);

    // Fingerprint icon
    item_icons[i] = lv_img_create(item_icon_circles[i]);
    if (!item_icons[i]) {
      return NULL;
    }
    lv_img_set_src(item_icons[i], &fingerprint);
    lv_obj_set_width(item_icons[i], ICON_SIZE);
    lv_obj_set_height(item_icons[i], ICON_SIZE);
    lv_obj_align(item_icons[i], LV_ALIGN_CENTER, 0, 0);
    lv_obj_add_flag(item_icons[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(item_icons[i], fingerprint_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);

    // Label
    item_labels[i] = lv_label_create(item_containers[i]);
    if (!item_labels[i]) {
      return NULL;
    }

    char label_text[64];
    if (params && params->enrolled[i]) {
      strncpy(label_text, params->labels[i], sizeof(label_text) - 1);
      label_text[sizeof(label_text) - 1] = '\0';
      lv_obj_set_style_text_color(item_labels[i], lv_color_white(), 0);
      lv_obj_set_style_img_recolor(item_icons[i], lv_color_white(), 0);
      lv_obj_set_style_img_recolor_opa(item_icons[i], LV_OPA_100, 0);
    } else {
      snprintf(label_text, sizeof(label_text),
               langpack_get_string(LANGPACK_ID_FINGERPRINT_MENU_ADD), i + 1);
      lv_obj_set_style_text_color(item_labels[i], lv_color_hex(COLOR_EMPTY_TEXT), 0);
      lv_obj_set_style_img_recolor(item_icons[i], lv_color_hex(COLOR_EMPTY_TEXT), 0);
      lv_obj_set_style_img_recolor_opa(item_icons[i], LV_OPA_100, 0);
    }

    lv_label_set_text(item_labels[i], label_text);
    lv_obj_set_style_text_font(item_labels[i], FONT_ITEM, 0);
    lv_obj_set_style_text_align(item_labels[i], LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_long_mode(item_labels[i], LV_LABEL_LONG_WRAP);
    lv_obj_set_width(item_labels[i], ITEM_WIDTH - 10);
    lv_obj_align(item_labels[i], LV_ALIGN_CENTER, 0, ITEM_LABEL_Y);
    lv_obj_add_flag(item_labels[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(item_labels[i], fingerprint_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);
  }

  // Scroll to initial position
  if (params && params->initial_slot < MAX_FINGERPRINTS) {
    current_item = params->initial_slot;
    scroll_to_item(current_item, false);
  } else {
    current_item = 0;
    scroll_to_item(0, false);
  }
  update_item_styles_by_position();

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_menu_fingerprints_destroy(void) {
  if (!screen) {
    return;
  }

  hold_cancel_destroy(&cancel_modal);
  top_back_destroy(&back_button);
  lv_obj_del(screen);

  screen = NULL;
  title_label = NULL;
  scroll_container = NULL;
  for (int i = 0; i < MAX_FINGERPRINTS; i++) {
    item_containers[i] = NULL;
    item_icon_circles[i] = NULL;
    item_icons[i] = NULL;
    item_labels[i] = NULL;
    enrolled_cache[i] = false;
  }
  current_item = 0;
  pulse_active = false;
  pulse_cycle_count = 0;
  cached_fingerprint_index = 0;
  pulse_target_obj = NULL;
}

void screen_menu_fingerprints_update(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;

  if (!screen) {
    screen_menu_fingerprints_init(ctx);
    return;
  }

  if (cancel_modal.is_showing) {
    hold_cancel_hide(&cancel_modal);
  }

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_fingerprints_tag) {
    const fwpb_display_params_menu_fingerprints* params = &show_screen->params.menu_fingerprints;

    for (uint8_t i = 0; i < MAX_FINGERPRINTS; i++) {
      enrolled_cache[i] = params->enrolled[i];
    }

    // Update fingerprint item labels and colors
    for (uint8_t i = 0; i < MAX_FINGERPRINTS; i++) {
      if (!item_labels[i]) {
        continue;
      }

      char label_text[64];
      if (params->enrolled[i]) {
        strncpy(label_text, params->labels[i], sizeof(label_text) - 1);
        label_text[sizeof(label_text) - 1] = '\0';
        lv_obj_set_style_text_color(item_labels[i], lv_color_white(), 0);
        if (item_icons[i]) {
          lv_obj_set_style_img_recolor(item_icons[i], lv_color_white(), 0);
          lv_obj_set_style_img_recolor_opa(item_icons[i], LV_OPA_100, 0);
        }
      } else {
        snprintf(label_text, sizeof(label_text),
                 langpack_get_string(LANGPACK_ID_FINGERPRINT_MENU_ADD), i + 1);
        lv_obj_set_style_text_color(item_labels[i], lv_color_hex(COLOR_EMPTY_TEXT), 0);
        if (item_icons[i]) {
          lv_obj_set_style_img_recolor(item_icons[i], lv_color_hex(COLOR_EMPTY_TEXT), 0);
          lv_obj_set_style_img_recolor_opa(item_icons[i], LV_OPA_100, 0);
        }
      }
      lv_label_set_text(item_labels[i], label_text);
    }

    // Handle authenticated fingerprint feedback
    if (params->show_authenticated && params->authenticated_index < MAX_FINGERPRINTS) {
      scroll_to_item(params->authenticated_index, true);
      start_green_pulse(params->authenticated_index);
    }
  }

  // Ensure back button is in front
  if (back_button.container && lv_obj_is_valid(back_button.container)) {
    lv_obj_move_foreground(back_button.container);
    lv_obj_clear_state(back_button.container, LV_STATE_DISABLED);
  }
}
