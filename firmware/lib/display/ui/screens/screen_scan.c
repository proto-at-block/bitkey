#include "screen_scan.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "nfc_dots_animation.h"
#include "top_menu.h"
#include "ui.h"

#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS      100
#define TEXT_CONTAINER_PADDING 12

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_36)

static lv_obj_t* screen = NULL;
static nfc_dots_animation_t nfc_dots_animation;
static top_menu_t menu_button;
static lv_obj_t* title_label = NULL;

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
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Initialize NFC dots animation structure
  memset(&nfc_dots_animation, 0, sizeof(nfc_dots_animation_t));

  // Create NFC dots animation widget
  nfc_dots_animation_create(screen, &nfc_dots_animation);

  // Title with black background box
  lv_obj_t* text_container = lv_obj_create(screen);
  if (!text_container) {
    return NULL;
  }
  lv_obj_set_style_bg_color(text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_all(text_container, TEXT_CONTAINER_PADDING, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);

  title_label = lv_label_create(text_container);
  if (!title_label) {
    return NULL;
  }
  lv_label_set_text(title_label, scan_context);
  lv_obj_set_style_text_color(title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_center(title_label);

  // Size container to fit the text
  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Set brightness to full immediately
  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  // Start NFC dots animation immediately
  nfc_dots_animation_start(&nfc_dots_animation);

  // Create top menu button (create last so it's on top)
  memset(&menu_button, 0, sizeof(top_menu_t));
  top_menu_create(screen, &menu_button, NULL);

  return screen;
}

void screen_scan_destroy(void) {
  if (!screen) {
    return;
  }

  nfc_dots_animation_destroy(&nfc_dots_animation);
  memset(&nfc_dots_animation, 0, sizeof(nfc_dots_animation));
  top_menu_destroy(&menu_button);
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
