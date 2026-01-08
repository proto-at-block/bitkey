#include "screen_onboarding.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "page_indicator.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Layout configuration
#define FADE_DURATION_MS        1000
#define BRIGHTNESS_START        0
#define BRIGHTNESS_END          100
#define TITLE_Y_OFFSET          50
#define TEXT_Y_OFFSET           140
#define TEXT_Y_OFFSET_NO_BUTTON 180
#define BUTTON_Y_OFFSET         280
#define BUTTON_WIDTH            200
#define BUTTON_HEIGHT           50
#define BUTTON_RADIUS           25
#define PAGE_COUNT              5
#define BUTTON_BORDER_WIDTH     2

// Colors
#define COLOR_LOGO 0xADADAD

// Fonts
#define FONT_TITLE  (&cash_sans_mono_regular_24)
#define FONT_TEXT   (&cash_sans_mono_regular_34)
#define FONT_BUTTON (&cash_sans_mono_regular_30)

extern const lv_img_dsc_t bitkey_logo_key;

static void brightness_anim_cb(void* var, int32_t value);
static void update_page_display(uint8_t page);

static lv_obj_t* screen = NULL;
static lv_obj_t* logo_img = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* text_label = NULL;
static lv_obj_t* button_container = NULL;
static lv_obj_t* button_label = NULL;
static page_indicator_t page_indicator;
static lv_anim_t brightness_anim;

static void brightness_anim_cb(void* var, int32_t value) {
  (void)var;
  ui_set_local_brightness((uint8_t)value);
}

static void update_page_display(uint8_t page) {
  if (!screen) {
    return;
  }

  // Page 0: Just logo
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
    if (page_indicator.is_initialized && page_indicator.background_arc) {
      lv_obj_add_flag(page_indicator.background_arc, LV_OBJ_FLAG_HIDDEN);
      lv_obj_add_flag(page_indicator.foreground_arc, LV_OBJ_FLAG_HIDDEN);
    }
  }
  // Pages 1-4: HELLO title + text + page indicator
  else {
    if (logo_img) {
      lv_obj_add_flag(logo_img, LV_OBJ_FLAG_HIDDEN);
    }
    if (title_label) {
      lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
    }
    if (text_label) {
      lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      const char* text = "";
      switch (page) {
        case 1:
          text = langpack_get_string(LANGPACK_ID_ONBOARDING_PAGE2);
          break;
        case 2:
          text = langpack_get_string(LANGPACK_ID_ONBOARDING_PAGE3);
          break;
        case 3:
          text = langpack_get_string(LANGPACK_ID_ONBOARDING_PAGE4);
          break;
        case 4:
          text = langpack_get_string(LANGPACK_ID_ONBOARDING_PAGE5);
          break;
      }
      lv_label_set_text(text_label, text);
    }
    if (page_indicator.is_initialized) {
      lv_obj_clear_flag(page_indicator.background_arc, LV_OBJ_FLAG_HIDDEN);
      lv_obj_clear_flag(page_indicator.foreground_arc, LV_OBJ_FLAG_HIDDEN);
      // Page indicator shows pages 1-4 as indices 0-3
      page_indicator_update(&page_indicator, page - 1);
    }

    // Button only on pages 3 and 4 (indices 3 and 4)
    if (page >= 3 && button_container) {
      lv_obj_clear_flag(button_container, LV_OBJ_FLAG_HIDDEN);
      const char* button_text = (page == 3) ? langpack_get_string(LANGPACK_ID_ONBOARDING_BUTTON4)
                                            : langpack_get_string(LANGPACK_ID_ONBOARDING_BUTTON5);
      lv_label_set_text(button_label, button_text);
      // Position text higher when button is present
      if (text_label) {
        lv_obj_align(text_label, LV_ALIGN_TOP_MID, 0, TEXT_Y_OFFSET);
      }
    } else if (button_container) {
      lv_obj_add_flag(button_container, LV_OBJ_FLAG_HIDDEN);
      // Center text better when no button (pages 1-2)
      if (text_label) {
        lv_obj_align(text_label, LV_ALIGN_TOP_MID, 0, TEXT_Y_OFFSET_NO_BUTTON);
      }
    }
  }
}

lv_obj_t* screen_onboarding_init(void* ctx) {
  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Logo (page 0 only)
  logo_img = lv_img_create(screen);
  lv_img_set_src(logo_img, &bitkey_logo_key);
  lv_obj_set_style_img_recolor(logo_img, lv_color_hex(COLOR_LOGO), 0);
  lv_obj_set_style_img_recolor_opa(logo_img, LV_OPA_COVER, 0);
  lv_obj_align(logo_img, LV_ALIGN_CENTER, 0, 0);

  // Title (pages 1-4)
  title_label = lv_label_create(screen);
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_ONBOARDING_TITLE));
  lv_obj_set_style_text_color(title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Text content (pages 1-4)
  text_label = lv_label_create(screen);
  lv_obj_set_style_text_color(text_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(text_label, FONT_TEXT, 0);
  lv_obj_set_style_text_align(text_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_line_space(text_label, 8, 0);
  lv_obj_align(text_label, LV_ALIGN_TOP_MID, 0, TEXT_Y_OFFSET);

  // Button container (pages 3-4)
  button_container = lv_obj_create(screen);
  lv_obj_set_size(button_container, BUTTON_WIDTH, BUTTON_HEIGHT);
  lv_obj_align(button_container, LV_ALIGN_TOP_MID, 0, BUTTON_Y_OFFSET);
  lv_obj_set_style_radius(button_container, BUTTON_RADIUS, 0);
  lv_obj_set_style_bg_opa(button_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_color(button_container, lv_color_white(), 0);
  lv_obj_set_style_border_width(button_container, BUTTON_BORDER_WIDTH, 0);
  lv_obj_set_style_border_opa(button_container, LV_OPA_COVER, 0);
  lv_obj_clear_flag(button_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(button_container,
                  LV_OBJ_FLAG_EVENT_BUBBLE);  // Allow gestures to bubble to screen

  button_label = lv_label_create(button_container);
  lv_obj_set_style_text_color(button_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(button_label, FONT_BUTTON, 0);
  lv_obj_center(button_label);

  // Page indicator (pages 1-4)
  memset(&page_indicator, 0, sizeof(page_indicator_t));
  page_indicator_create(screen, &page_indicator, PAGE_COUNT - 1);

  // Get initial page from params if available
  uint8_t initial_page = 0;
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_onboarding_tag) {
    initial_page = show_screen->params.onboarding.current_page;
    if (initial_page >= PAGE_COUNT) {
      initial_page = 0;
    }
  }
  update_page_display(initial_page);

  // Start with 0 brightness
  ui_set_local_brightness(BRIGHTNESS_START);

  // Create fade-in animation
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
  if (page_indicator.is_initialized) {
    page_indicator_destroy(&page_indicator);
    memset(&page_indicator, 0, sizeof(page_indicator));
  }

  lv_obj_del(screen);
  screen = NULL;
  logo_img = NULL;
  title_label = NULL;
  text_label = NULL;
  button_container = NULL;
  button_label = NULL;
}

void screen_onboarding_update(void* ctx) {
  if (!screen) {
    screen_onboarding_init(ctx);
    return;
  }

  // Get the page number from screen params
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_onboarding_tag) {
    uint8_t page = show_screen->params.onboarding.current_page;
    if (page < PAGE_COUNT) {
      update_page_display(page);
    }
  }
}
