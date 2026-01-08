#include "screen_fingerprint_remove.h"

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
#define TITLE_Y_OFFSET        50
#define BACK_BUTTON_Y_OFFSET  40
#define BACK_BUTTON_X_PADDING 30
#define BACK_BUTTON_SIZE      44
#define REMOVE_BUTTON_Y       240
#define REMOVE_BUTTON_WIDTH   180
#define REMOVE_BUTTON_HEIGHT  50
#define PILL_BORDER_WIDTH     2
#define PILL_RADIUS           25
#define PILL_Y_SPACING        80
#define MESSAGE_Y_OFFSET      200

// Colors
#define COLOR_BACK_BUTTON   0xFFFFFF
#define COLOR_REMOVE_BUTTON 0xFFFFFF
#define COLOR_REMOVE_TEXT   0xF84752
#define COLOR_WHITE         0xFFFFFF
#define COLOR_BLACK         0x000000

// Fonts
#define FONT_LABEL   (&cash_sans_mono_regular_24)
#define FONT_TITLE   (&cash_sans_mono_regular_24)
#define FONT_BUTTON  (&cash_sans_mono_regular_20)
#define FONT_MESSAGE (&cash_sans_mono_regular_34)

// External image declarations
extern const lv_img_dsc_t cycle_back;
extern const lv_img_dsc_t fingerprint;

static lv_obj_t* screen = NULL;
static lv_obj_t* back_button = NULL;
static lv_obj_t* remove_button = NULL;
static lv_obj_t* remove_pill = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* yes_pill = NULL;
static lv_obj_t* back_pill = NULL;
static lv_obj_t* message_container = NULL;
static uint8_t current_page = 0;

static void update_page_display(uint8_t page) {
  if (!screen) {
    return;
  }

  current_page = page;

  // Hide all elements first
  if (back_button) {
    lv_obj_add_flag(back_button, LV_OBJ_FLAG_HIDDEN);
  }
  if (title_label) {
    lv_obj_add_flag(title_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (remove_pill) {
    lv_obj_add_flag(remove_pill, LV_OBJ_FLAG_HIDDEN);
  }
  if (yes_pill) {
    lv_obj_add_flag(yes_pill, LV_OBJ_FLAG_HIDDEN);
  }
  if (back_pill) {
    lv_obj_add_flag(back_pill, LV_OBJ_FLAG_HIDDEN);
  }
  if (message_container) {
    lv_obj_add_flag(message_container, LV_OBJ_FLAG_HIDDEN);
  }

  switch (page) {
    case 0:  // Remove page
      if (back_button) {
        lv_obj_clear_flag(back_button, LV_OBJ_FLAG_HIDDEN);
      }
      if (remove_pill) {
        lv_obj_clear_flag(remove_pill, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case 1:  // Confirm page
      if (title_label) {
        lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
        lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_REMOVE_TITLE));
      }
      if (yes_pill) {
        lv_obj_clear_flag(yes_pill, LV_OBJ_FLAG_HIDDEN);
      }
      if (back_pill) {
        lv_obj_clear_flag(back_pill, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case 2:  // Removed page
      if (message_container) {
        lv_obj_clear_flag(message_container, LV_OBJ_FLAG_HIDDEN);
      }
      break;
  }
}

lv_obj_t* screen_fingerprint_remove_init(void* ctx) {
  ASSERT(screen == NULL);

  // We don't use params during init, only during update
  (void)ctx;

  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // === PAGE 0: Remove page elements ===
  // Create back button (top center) - circular style like menu
  back_button = lv_btn_create(screen);
  lv_obj_remove_style_all(back_button);
  lv_obj_set_size(back_button, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
  lv_obj_align(back_button, LV_ALIGN_TOP_MID, 0, BACK_BUTTON_Y_OFFSET);
  lv_obj_set_style_bg_opa(back_button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_radius(back_button, BACK_BUTTON_SIZE / 2, 0);

  lv_obj_t* back_icon = lv_img_create(back_button);
  lv_img_set_src(back_icon, &cycle_back);
  lv_obj_set_style_img_recolor(back_icon, lv_color_hex(COLOR_BACK_BUTTON), 0);
  lv_obj_set_style_img_recolor_opa(back_icon, LV_OPA_100, 0);
  lv_obj_center(back_icon);

  // Create remove button pill (center of screen)
  remove_pill = lv_obj_create(screen);
  lv_obj_remove_style_all(remove_pill);
  lv_obj_set_size(remove_pill, REMOVE_BUTTON_WIDTH, REMOVE_BUTTON_HEIGHT);
  lv_obj_align(remove_pill, LV_ALIGN_CENTER, 0, 0);
  lv_obj_set_style_bg_opa(remove_pill, LV_OPA_100, 0);
  lv_obj_set_style_bg_color(remove_pill, lv_color_black(), 0);
  lv_obj_set_style_radius(remove_pill, PILL_RADIUS, 0);

  // Create remove button inside the pill
  remove_button = lv_btn_create(remove_pill);
  lv_obj_remove_style_all(remove_button);
  lv_obj_set_size(remove_button, REMOVE_BUTTON_WIDTH, REMOVE_BUTTON_HEIGHT);
  lv_obj_center(remove_button);
  lv_obj_set_style_bg_opa(remove_button, LV_OPA_TRANSP, 0);

  // Create X icon and "Remove" text
  lv_obj_t* remove_container = lv_obj_create(remove_button);
  lv_obj_remove_style_all(remove_container);
  lv_obj_set_size(remove_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_center(remove_container);
  lv_obj_set_layout(remove_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(remove_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(remove_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(remove_container, 10, 0);

  lv_obj_t* x_label = lv_label_create(remove_container);
  lv_label_set_text(x_label, "X");
  lv_obj_set_style_text_font(x_label, FONT_BUTTON, 0);
  lv_obj_set_style_text_color(x_label, lv_color_hex(COLOR_REMOVE_TEXT), 0);

  lv_obj_t* remove_label = lv_label_create(remove_container);
  lv_label_set_text(remove_label, "Remove");
  lv_obj_set_style_text_font(remove_label, FONT_BUTTON, 0);
  lv_obj_set_style_text_color(remove_label, lv_color_white(), 0);

  // === PAGE 1: Confirm page elements ===
  // Create title label
  title_label = lv_label_create(screen);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, LV_PART_MAIN);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_WHITE), LV_PART_MAIN);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Create "Yes, remove" pill button
  yes_pill = lv_obj_create(screen);
  lv_obj_remove_style_all(yes_pill);
  lv_obj_set_size(yes_pill, REMOVE_BUTTON_WIDTH, REMOVE_BUTTON_HEIGHT);
  lv_obj_align(yes_pill, LV_ALIGN_CENTER, 0, -PILL_Y_SPACING / 2);
  lv_obj_set_style_bg_opa(yes_pill, LV_OPA_100, 0);
  lv_obj_set_style_bg_color(yes_pill, lv_color_black(), 0);
  lv_obj_set_style_radius(yes_pill, PILL_RADIUS, 0);

  lv_obj_t* yes_label = lv_label_create(yes_pill);
  lv_label_set_text(yes_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_REMOVE_CONFIRM));
  lv_obj_set_style_text_font(yes_label, FONT_BUTTON, LV_PART_MAIN);
  lv_obj_set_style_text_color(yes_label, lv_color_hex(COLOR_REMOVE_TEXT), LV_PART_MAIN);
  lv_obj_center(yes_label);

  // Create "Back" pill button
  back_pill = lv_obj_create(screen);
  lv_obj_remove_style_all(back_pill);
  lv_obj_set_size(back_pill, REMOVE_BUTTON_WIDTH, REMOVE_BUTTON_HEIGHT);
  lv_obj_align(back_pill, LV_ALIGN_CENTER, 0, PILL_Y_SPACING / 2);
  lv_obj_set_style_bg_opa(back_pill, LV_OPA_100, 0);
  lv_obj_set_style_bg_color(back_pill, lv_color_black(), 0);
  lv_obj_set_style_radius(back_pill, PILL_RADIUS, 0);

  // Add back icon and text to back pill
  lv_obj_t* back_pill_container = lv_obj_create(back_pill);
  lv_obj_remove_style_all(back_pill_container);
  lv_obj_set_size(back_pill_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_center(back_pill_container);
  lv_obj_set_layout(back_pill_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(back_pill_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(back_pill_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(back_pill_container, 10, 0);

  lv_obj_t* back_pill_icon = lv_img_create(back_pill_container);
  lv_img_set_src(back_pill_icon, &cycle_back);
  lv_obj_set_style_img_recolor(back_pill_icon, lv_color_hex(COLOR_WHITE), 0);
  lv_obj_set_style_img_recolor_opa(back_pill_icon, 255, 0);

  lv_obj_t* back_pill_label = lv_label_create(back_pill_container);
  lv_label_set_text(back_pill_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_REMOVE_BACK));
  lv_obj_set_style_text_font(back_pill_label, FONT_BUTTON, LV_PART_MAIN);
  lv_obj_set_style_text_color(back_pill_label, lv_color_hex(COLOR_WHITE), LV_PART_MAIN);

  // === PAGE 2: Removed page elements ===
  // Create message container
  message_container = lv_obj_create(screen);
  lv_obj_remove_style_all(message_container);
  lv_obj_set_size(message_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_layout(message_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(message_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(message_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_align(message_container, LV_ALIGN_CENTER, 0, 0);
  lv_obj_set_style_pad_column(message_container, 10, 0);

  // Create X mark (red)
  lv_obj_t* x_removed_label = lv_label_create(message_container);
  lv_label_set_text(x_removed_label, "X");
  lv_obj_set_style_text_font(x_removed_label, FONT_MESSAGE, LV_PART_MAIN);
  lv_obj_set_style_text_color(x_removed_label, lv_color_hex(COLOR_REMOVE_TEXT), LV_PART_MAIN);

  // Create "removed" text (white)
  lv_obj_t* removed_label = lv_label_create(message_container);
  lv_label_set_text(removed_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_REMOVE_REMOVED));
  lv_obj_set_style_text_font(removed_label, FONT_MESSAGE, LV_PART_MAIN);
  lv_obj_set_style_text_color(removed_label, lv_color_hex(COLOR_WHITE), LV_PART_MAIN);

  // Start with page 0
  update_page_display(0);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_fingerprint_remove_destroy(void) {
  if (!screen) {
    return;
  }

  lv_obj_del(screen);
  screen = NULL;
  back_button = NULL;
  remove_button = NULL;
  remove_pill = NULL;
  title_label = NULL;
  yes_pill = NULL;
  back_pill = NULL;
  message_container = NULL;
  current_page = 0;
}

void screen_fingerprint_remove_update(void* ctx) {
  if (!screen) {
    screen_fingerprint_remove_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen ||
      show_screen->which_params != fwpb_display_show_screen_fingerprint_remove_tag) {
    return;
  }

  const fwpb_display_params_fingerprint_remove* params = &show_screen->params.fingerprint_remove;

  // Update page display if changed
  if (params->page != current_page) {
    update_page_display(params->page);
  }

  // Handle button selection based on page
  if (params->page == 0) {
    // Page 0: Remove page
    if (params->selected_button == 0) {
      lv_obj_set_style_bg_opa(back_button, LV_OPA_TRANSP, 0);
      lv_obj_set_style_border_width(back_button, PILL_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(back_button, lv_color_hex(COLOR_BACK_BUTTON), 0);

      lv_obj_set_style_border_width(remove_pill, 0, 0);
    } else {
      lv_obj_set_style_border_width(remove_pill, PILL_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(remove_pill, lv_color_hex(COLOR_REMOVE_BUTTON), 0);

      lv_obj_set_style_border_width(back_button, 0, 0);
    }
  } else if (params->page == 1) {
    // Page 1: Confirm page
    if (params->selected_button == 0) {
      lv_obj_set_style_border_width(yes_pill, PILL_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(yes_pill, lv_color_hex(COLOR_WHITE), 0);

      lv_obj_set_style_border_width(back_pill, 0, 0);
    } else {
      lv_obj_set_style_border_width(back_pill, PILL_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(back_pill, lv_color_hex(COLOR_WHITE), 0);

      lv_obj_set_style_border_width(yes_pill, 0, 0);
    }
  }
}
