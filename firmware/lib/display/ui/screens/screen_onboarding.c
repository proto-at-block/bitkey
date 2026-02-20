#include "screen_onboarding.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "orbital_dots_animation.h"
#include "ui.h"

#include <string.h>

// Layout configuration
#define FADE_DURATION_MS       1000
#define BRIGHTNESS_START       0
#define BRIGHTNESS_END         100
#define TEXT_CONTAINER_PADDING 12

// Colors
#define COLOR_LOGO 0xADADAD

// Fonts
#define FONT_SCAN_TITLE (&cash_sans_mono_regular_36)

// External image declarations
extern const lv_img_dsc_t bitkey_logo_key;

// Forward declarations
static void brightness_anim_cb(void* var, int32_t value);
static void logo_touch_handler(lv_event_t* e);
static void show_logo_screen(void);
static void show_scan_screen(void);

static lv_obj_t* screen = NULL;
static lv_obj_t* logo_img = NULL;
static lv_obj_t* scan_title_label = NULL;
static orbital_dots_animation_t orbital_dots;
static lv_anim_t brightness_anim;
static bool is_scan_state = false;

static void brightness_anim_cb(void* var, int32_t value) {
  (void)var;
  ui_set_local_brightness((uint8_t)value);
}

static void logo_touch_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    // Transition directly from logo to scan screen
    lv_obj_del(logo_img);
    logo_img = NULL;
    lv_anim_del(&brightness_anim, brightness_anim_cb);
    lv_obj_remove_event_cb(screen, logo_touch_handler);
    lv_obj_clear_flag(screen, LV_OBJ_FLAG_CLICKABLE);

    show_scan_screen();
  }
}

static void show_logo_screen(void) {
  // Create logo image
  logo_img = lv_img_create(screen);
  if (!logo_img) {
    return;
  }
  lv_img_set_src(logo_img, &bitkey_logo_key);
  lv_obj_set_style_img_recolor(logo_img, lv_color_hex(COLOR_LOGO), 0);
  lv_obj_set_style_img_recolor_opa(logo_img, LV_OPA_COVER, 0);
  lv_obj_align(logo_img, LV_ALIGN_CENTER, 0, 0);

  lv_obj_add_flag(screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(screen, logo_touch_handler, LV_EVENT_CLICKED, NULL);

  // Fade-in animation (0 to 100 brightness over 1000ms)
  ui_set_local_brightness(BRIGHTNESS_START);
  lv_anim_init(&brightness_anim);
  lv_anim_set_var(&brightness_anim, NULL);
  lv_anim_set_values(&brightness_anim, BRIGHTNESS_START, BRIGHTNESS_END);
  lv_anim_set_time(&brightness_anim, FADE_DURATION_MS);
  lv_anim_set_exec_cb(&brightness_anim, brightness_anim_cb);
  lv_anim_start(&brightness_anim);

  is_scan_state = false;
}

static void show_scan_screen(void) {
  // Initialize orbital dots animation
  memset(&orbital_dots, 0, sizeof(orbital_dots_animation_t));
  orbital_dots_animation_create(screen, &orbital_dots);

  // Title label with black background box (same as normal scan screen)
  lv_obj_t* text_container = lv_obj_create(screen);
  if (!text_container) {
    return;
  }
  lv_obj_set_style_bg_color(text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_all(text_container, TEXT_CONTAINER_PADDING, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);

  scan_title_label = lv_label_create(text_container);
  if (!scan_title_label) {
    return;
  }
  lv_label_set_text(scan_title_label, langpack_get_string(LANGPACK_ID_SCAN_TAP));
  lv_obj_set_style_text_color(scan_title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(scan_title_label, FONT_SCAN_TITLE, 0);
  lv_obj_center(scan_title_label);

  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Set brightness to full immediately (no fade for scan screen)
  ui_set_local_brightness(BRIGHTNESS_END);

  // Start orbital dots animation
  orbital_dots_animation_start(&orbital_dots);

  is_scan_state = true;
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

  // Always start with logo screen
  show_logo_screen();

  return screen;
}

void screen_onboarding_destroy(void) {
  if (!screen) {
    return;
  }

  lv_anim_del(&brightness_anim, brightness_anim_cb);

  if (is_scan_state) {
    orbital_dots_animation_destroy(&orbital_dots);
    memset(&orbital_dots, 0, sizeof(orbital_dots));
  }

  lv_obj_del(screen);
  screen = NULL;
  logo_img = NULL;
  scan_title_label = NULL;
  is_scan_state = false;
}

void screen_onboarding_update(void* ctx) {
  (void)ctx;

  if (!screen) {
    screen_onboarding_init(ctx);
  }
}
