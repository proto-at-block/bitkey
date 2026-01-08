#include "screen_scan.h"

#include "assert.h"
#include "bottom_menu.h"
#include "display.pb.h"
#include "langpack.h"
#include "ui.h"
#include "wave_animation.h"

#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100
#define TEXT_FADE_MS      1000

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_36)

static lv_obj_t* screen = NULL;
static wave_animation_t wave_animation;
static bottom_menu_t menu_button;
static lv_obj_t* title_label = NULL;
static lv_anim_t text_fade_anim;

static void text_fade_anim_cb(void* var, int32_t value) {
  lv_obj_t* label = (lv_obj_t*)var;
  lv_obj_set_style_text_opa(label, (lv_opa_t)value, 0);
}

static void text_fade_ready_cb(lv_anim_t* a) {
  (void)a;
  // Start wave animation after text fade-in completes
  wave_animation_start(&wave_animation);
}

lv_obj_t* screen_scan_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const char* scan_context = langpack_get_string(LANGPACK_ID_SCAN_TITLE);

  if (show_screen && (show_screen->which_params == fwpb_display_show_screen_scan_tag)) {
    switch (show_screen->params.scan.action) {
      case fwpb_display_params_scan_display_params_scan_action_SIGN:
        scan_context = langpack_get_string(LANGPACK_ID_SCAN_SIGN);
        break;

      case fwpb_display_params_scan_display_params_scan_action_VERIFY:
        scan_context = langpack_get_string(LANGPACK_ID_SCAN_VERIFY);
        break;

      case fwpb_display_params_scan_display_params_scan_action_CONFIRM:
        scan_context = langpack_get_string(LANGPACK_ID_SCAN_CONFIRM);
        break;

      case fwpb_display_params_scan_display_params_scan_action_TAP:
        scan_context = langpack_get_string(LANGPACK_ID_SCAN_TAP);
        break;

      case fwpb_display_params_scan_display_params_scan_action_NONE:
        // 'break' intentionally omitted.
      default:
        break;
    }
  }

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Initialize wave_animation structure
  memset(&wave_animation, 0, sizeof(wave_animation_t));

  // Create wave animation widget
  wave_animation_create(screen, &wave_animation);

  // Create bottom menu button
  memset(&menu_button, 0, sizeof(bottom_menu_t));
  bottom_menu_create(screen, &menu_button, true);

  // Title
  title_label = lv_label_create(screen);
  lv_label_set_text(title_label, scan_context);
  lv_obj_set_style_text_color(title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_set_style_text_opa(title_label, 0, 0);
  lv_obj_align(title_label, LV_ALIGN_CENTER, 0, 0);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  // Create text fade-in animation
  lv_anim_init(&text_fade_anim);
  lv_anim_set_var(&text_fade_anim, title_label);
  lv_anim_set_values(&text_fade_anim, 0, LV_OPA_COVER);
  lv_anim_set_time(&text_fade_anim, TEXT_FADE_MS);
  lv_anim_set_exec_cb(&text_fade_anim, text_fade_anim_cb);
  lv_anim_set_path_cb(&text_fade_anim, lv_anim_path_ease_in_out);
  lv_anim_set_ready_cb(&text_fade_anim, text_fade_ready_cb);
  lv_anim_start(&text_fade_anim);

  return screen;
}

void screen_scan_destroy(void) {
  if (!screen) {
    return;
  }

  lv_anim_del(title_label, text_fade_anim_cb);
  wave_animation_destroy(&wave_animation);
  memset(&wave_animation, 0, sizeof(wave_animation));
  bottom_menu_destroy(&menu_button);
  memset(&menu_button, 0, sizeof(menu_button));
  lv_obj_del(screen);
  screen = NULL;
  title_label = NULL;
}

void screen_scan_update(void* ctx) {
  (void)ctx;
  if (!screen) {
    screen_scan_init(NULL);
  }
}
