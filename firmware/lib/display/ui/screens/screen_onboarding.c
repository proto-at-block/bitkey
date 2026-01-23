#include "screen_onboarding.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "ui.h"

#include <string.h>

// Layout configuration
#define FADE_DURATION_MS    1000
#define BRIGHTNESS_START    0
#define BRIGHTNESS_END      100
#define TITLE_Y_OFFSET      50
#define TEXT_Y_OFFSET       150
#define TEXT_WIDTH          400
#define BUTTON_Y_OFFSET     310
#define BUTTON_RADIUS       25
#define PAGE_COUNT          3
#define BUTTON_BORDER_WIDTH 2
#define BUTTON_PADDING_X    30
#define BUTTON_PADDING_Y    16

// Colors
#define COLOR_LOGO 0xADADAD

// Fonts
#define FONT_TITLE  (&cash_sans_mono_regular_24)
#define FONT_TEXT   (&cash_sans_mono_regular_34)
#define FONT_BUTTON (&cash_sans_mono_regular_30)

// External image declarations
extern const lv_img_dsc_t bitkey_logo_key;

// Forward declarations
static void brightness_anim_cb(void* var, int32_t value);
static void update_page_display(uint8_t page);
static void page_swipe_handler(lv_event_t* e);
static void continue_button_click_handler(lv_event_t* e);

static lv_obj_t* screen = NULL;
static lv_obj_t* logo_img = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* text_label = NULL;
static lv_obj_t* button_container = NULL;
static lv_obj_t* button_label = NULL;
static lv_anim_t brightness_anim;
static uint8_t current_page = 0;  // Current page (0-2)

static void brightness_anim_cb(void* var, int32_t value) {
  (void)var;
  ui_set_local_brightness((uint8_t)value);
}

static void page_swipe_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_GESTURE) {
    lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_get_act());

    if (dir == LV_DIR_LEFT && current_page < PAGE_COUNT - 1) {
      current_page++;
      update_page_display(current_page);
    } else if (dir == LV_DIR_RIGHT && current_page > 0) {
      current_page--;
      update_page_display(current_page);
    }
  }
}

static void continue_button_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT, 0);
  }
}

static void update_page_display(uint8_t page) {
  if (!screen) {
    return;
  }

  if (page == 0) {
    if (logo_img) {
      lv_obj_clear_flag(logo_img, LV_OBJ_FLAG_HIDDEN);
    }
    if (title_label) {
      lv_obj_add_flag(title_label, LV_OBJ_FLAG_HIDDEN);
    }
    if (text_label) {
      lv_obj_add_flag(text_label, LV_OBJ_FLAG_HIDDEN);
    }
    if (button_container) {
      lv_obj_add_flag(button_container, LV_OBJ_FLAG_HIDDEN);
    }
  } else {
    if (logo_img) {
      lv_obj_add_flag(logo_img, LV_OBJ_FLAG_HIDDEN);
    }
    if (title_label) {
      lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
    }
    if (text_label) {
      lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      const char* text;
      switch (page) {
        case 1:
          text = langpack_get_string(LANGPACK_ID_ONBOARDING_WELCOME);
          break;
        case 2:
          text = langpack_get_string(LANGPACK_ID_ONBOARDING_FINGERPRINT);
          break;
        default:
          text = "";
          break;
      }
      lv_label_set_text(text_label, text);
      lv_obj_align(text_label, LV_ALIGN_TOP_MID, 0, TEXT_Y_OFFSET);
    }

    // Button only on last page
    if (page == 2 && button_container) {
      lv_obj_clear_flag(button_container, LV_OBJ_FLAG_HIDDEN);
      lv_label_set_text(button_label, langpack_get_string(LANGPACK_ID_ONBOARDING_GET_STARTED));
    } else if (button_container) {
      lv_obj_add_flag(button_container, LV_OBJ_FLAG_HIDDEN);
    }
  }
}

lv_obj_t* screen_onboarding_init(void* ctx) {
  (void)ctx;
  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_add_event_cb(screen, page_swipe_handler, LV_EVENT_GESTURE, NULL);

  // Logo
  logo_img = lv_img_create(screen);
  if (!logo_img) {
    return NULL;
  }
  lv_img_set_src(logo_img, &bitkey_logo_key);
  lv_obj_set_style_img_recolor(logo_img, lv_color_hex(COLOR_LOGO), 0);
  lv_obj_set_style_img_recolor_opa(logo_img, LV_OPA_COVER, 0);
  lv_obj_align(logo_img, LV_ALIGN_CENTER, 0, 0);

  // Title
  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_ONBOARDING_TITLE));
  lv_obj_set_style_text_color(title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Text label
  text_label = lv_label_create(screen);
  if (!text_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(text_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(text_label, FONT_TEXT, 0);
  lv_obj_set_style_text_align(text_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_line_space(text_label, 8, 0);
  lv_obj_set_width(text_label, TEXT_WIDTH);
  lv_label_set_long_mode(text_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(text_label, LV_ALIGN_TOP_MID, 0, TEXT_Y_OFFSET);

  // Button label
  button_label = lv_label_create(screen);
  if (!button_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(button_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(button_label, FONT_BUTTON, 0);

  // Button container
  button_container = lv_obj_create(screen);
  if (!button_container) {
    return NULL;
  }
  lv_obj_set_size(button_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_pad_left(button_container, BUTTON_PADDING_X, 0);
  lv_obj_set_style_pad_right(button_container, BUTTON_PADDING_X, 0);
  lv_obj_set_style_pad_top(button_container, BUTTON_PADDING_Y, 0);
  lv_obj_set_style_pad_bottom(button_container, BUTTON_PADDING_Y, 0);
  lv_obj_align(button_container, LV_ALIGN_TOP_MID, 0, BUTTON_Y_OFFSET);
  lv_obj_set_style_radius(button_container, BUTTON_RADIUS, 0);
  lv_obj_set_style_bg_opa(button_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_color(button_container, lv_color_white(), 0);
  lv_obj_set_style_border_width(button_container, BUTTON_BORDER_WIDTH, 0);
  lv_obj_set_style_border_opa(button_container, LV_OPA_COVER, 0);
  lv_obj_clear_flag(button_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(button_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_flag(button_container, LV_OBJ_FLAG_EVENT_BUBBLE);

  lv_obj_set_parent(button_label, button_container);
  lv_obj_center(button_label);
  lv_obj_update_layout(button_container);

  lv_obj_add_event_cb(button_container, continue_button_click_handler, LV_EVENT_CLICKED, NULL);

  current_page = 0;
  update_page_display(current_page);

  // Fade-in animation
  ui_set_local_brightness(BRIGHTNESS_START);
  lv_anim_init(&brightness_anim);
  lv_anim_set_var(&brightness_anim, NULL);
  lv_anim_set_values(&brightness_anim, BRIGHTNESS_START, BRIGHTNESS_END);
  lv_anim_set_time(&brightness_anim, FADE_DURATION_MS);
  lv_anim_set_exec_cb(&brightness_anim, brightness_anim_cb);
  lv_anim_start(&brightness_anim);

  return screen;
}

void screen_onboarding_destroy(void) {
  if (!screen) {
    return;
  }

  lv_anim_del(&brightness_anim, brightness_anim_cb);

  lv_obj_del(screen);
  screen = NULL;
  logo_img = NULL;
  title_label = NULL;
  text_label = NULL;
  button_container = NULL;
  button_label = NULL;
  current_page = 0;
}

void screen_onboarding_update(void* ctx) {
  (void)ctx;

  if (!screen) {
    screen_onboarding_init(ctx);
    return;
  }
}
