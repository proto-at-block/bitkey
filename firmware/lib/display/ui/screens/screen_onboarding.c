#include "screen_onboarding.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "nfc_dots_animation.h"
#include "top_menu.h"
#include "ui.h"

#include <string.h>

// Boot fade-in: screen opacity animates from transparent to opaque over 1s
#define FADE_DURATION_MS   1000
#define FADE_HOLD_DELAY_MS 1000  // pause after fade-in before advancing

// Branding animation: logo shrinks + slides left, text fades in
#define BRANDING_ANIM_MS   400  // duration for logo shrink and slide
#define TEXT_FADE_MS       400  // duration for text fade-in
#define TEXT_FADE_DELAY_MS 400  // delay before text starts fading in
#define LOGO_SCALE_START   256  // LVGL 256 = 100%
#define LOGO_SCALE_END     87   // ~34%
#define LOGO_X_OFFSET      -90  // pixels left of center
#define LOGO_PIVOT_X       68   // scale origin x (135/2, center of logo)
#define LOGO_PIVOT_Y       58   // scale origin y (above center for alignment with text)
#define TEXT_X_OFFSET      30   // pixels right of center

// Scan screen
#define SCAN_TEXT_PADDING 12
#define FONT_SCAN_TITLE   (&cash_sans_mono_regular_36)

// Colors
#define COLOR_LOGO 0xADADAD
#define COLOR_TEXT 0xFFFFFF

// Images
extern const lv_img_dsc_t bitkey_logo_key;
extern const lv_img_dsc_t bitkey_text;

// Forward declarations
static void screen_opa_anim_cb(void* var, int32_t value);
static void logo_scale_anim_cb(void* var, int32_t value);
static void logo_pos_anim_cb(void* var, int32_t value);
static void text_opa_anim_cb(void* var, int32_t value);
static void fade_in_ready_cb(lv_anim_t* a);
static void branding_touch_handler(lv_event_t* e);
static void show_logo_screen(void);
static void show_branding_screen(void);
static void show_scan_screen(void);

// State
static lv_obj_t* screen = NULL;
static lv_obj_t* logo_img = NULL;
static lv_obj_t* text_img = NULL;
static lv_obj_t* scan_label = NULL;
static nfc_dots_animation_t nfc_dots;
static top_menu_t menu_button;
static lv_anim_t screen_opa_anim;
static lv_anim_t logo_scale_anim;
static lv_anim_t logo_pos_anim;
static lv_anim_t text_fade_anim;
static lv_timer_t* fade_hold_timer = NULL;
static bool is_scan_state = false;
static bool is_branding_state = false;

// Animation callbacks

static void screen_opa_anim_cb(void* var, int32_t value) {
  (void)var;
  if (logo_img) {
    lv_obj_set_style_opa(logo_img, (lv_opa_t)value, 0);
  }
}

static void logo_scale_anim_cb(void* var, int32_t value) {
  (void)var;
  if (logo_img) {
    lv_obj_set_style_transform_scale(logo_img, value, 0);
  }
}

static void logo_pos_anim_cb(void* var, int32_t value) {
  (void)var;
  if (logo_img) {
    lv_obj_align(logo_img, LV_ALIGN_CENTER, value, 0);
  }
}

static void text_opa_anim_cb(void* var, int32_t value) {
  (void)var;
  if (text_img) {
    lv_obj_set_style_opa(text_img, (lv_opa_t)value, 0);
  }
}

// Auto-advance after fade-in completes (with hold delay)

static void fade_hold_timer_cb(lv_timer_t* timer) {
  (void)timer;
  fade_hold_timer = NULL;
  show_branding_screen();
}

static void fade_in_ready_cb(lv_anim_t* a) {
  (void)a;
  fade_hold_timer = lv_timer_create(fade_hold_timer_cb, FADE_HOLD_DELAY_MS, NULL);
  if (fade_hold_timer) {
    lv_timer_set_repeat_count(fade_hold_timer, 1);
  } else {
    show_branding_screen();
  }
}

static void branding_touch_handler(lv_event_t* e) {
  if (lv_event_get_code(e) == LV_EVENT_CLICKED) {
    lv_anim_del(&logo_scale_anim, logo_scale_anim_cb);
    lv_anim_del(&logo_pos_anim, logo_pos_anim_cb);
    lv_anim_del(&text_fade_anim, text_opa_anim_cb);

    if (logo_img) {
      lv_obj_del(logo_img);
      logo_img = NULL;
    }
    if (text_img) {
      lv_obj_del(text_img);
      text_img = NULL;
    }

    lv_obj_remove_event_cb(screen, branding_touch_handler);
    lv_obj_clear_flag(screen, LV_OBJ_FLAG_CLICKABLE);
    is_branding_state = false;

    show_scan_screen();
  }
}

// Screens

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

  // Fade-in animation: logo fades from transparent to opaque over 1000ms,
  // then auto-advances to branding screen.
  lv_obj_set_style_opa(logo_img, LV_OPA_TRANSP, 0);
  lv_anim_init(&screen_opa_anim);
  lv_anim_set_var(&screen_opa_anim, &screen_opa_anim);
  lv_anim_set_values(&screen_opa_anim, LV_OPA_TRANSP, LV_OPA_COVER);
  lv_anim_set_time(&screen_opa_anim, FADE_DURATION_MS);
  lv_anim_set_exec_cb(&screen_opa_anim, screen_opa_anim_cb);
  lv_anim_set_ready_cb(&screen_opa_anim, fade_in_ready_cb);
  lv_anim_start(&screen_opa_anim);

  is_scan_state = false;
}

static void show_branding_screen(void) {
  is_branding_state = true;

  lv_obj_set_style_transform_pivot_x(logo_img, LOGO_PIVOT_X, 0);
  lv_obj_set_style_transform_pivot_y(logo_img, LOGO_PIVOT_Y, 0);

  lv_anim_init(&logo_scale_anim);
  lv_anim_set_var(&logo_scale_anim, &logo_scale_anim);
  lv_anim_set_values(&logo_scale_anim, LOGO_SCALE_START, LOGO_SCALE_END);
  lv_anim_set_time(&logo_scale_anim, BRANDING_ANIM_MS);
  lv_anim_set_exec_cb(&logo_scale_anim, logo_scale_anim_cb);
  lv_anim_start(&logo_scale_anim);

  lv_anim_init(&logo_pos_anim);
  lv_anim_set_var(&logo_pos_anim, &logo_pos_anim);
  lv_anim_set_values(&logo_pos_anim, 0, LOGO_X_OFFSET);
  lv_anim_set_time(&logo_pos_anim, BRANDING_ANIM_MS);
  lv_anim_set_exec_cb(&logo_pos_anim, logo_pos_anim_cb);
  lv_anim_start(&logo_pos_anim);

  text_img = lv_img_create(screen);
  if (!text_img) {
    is_branding_state = false;
    lv_obj_add_flag(screen, LV_OBJ_FLAG_CLICKABLE);
    return;
  }
  lv_img_set_src(text_img, &bitkey_text);
  lv_obj_set_style_img_recolor(text_img, lv_color_hex(COLOR_TEXT), 0);
  lv_obj_set_style_img_recolor_opa(text_img, LV_OPA_COVER, 0);
  lv_obj_align(text_img, LV_ALIGN_CENTER, TEXT_X_OFFSET, 0);
  lv_obj_set_style_opa(text_img, LV_OPA_TRANSP, 0);

  lv_anim_init(&text_fade_anim);
  lv_anim_set_var(&text_fade_anim, &text_fade_anim);
  lv_anim_set_values(&text_fade_anim, LV_OPA_TRANSP, LV_OPA_COVER);
  lv_anim_set_time(&text_fade_anim, TEXT_FADE_MS);
  lv_anim_set_delay(&text_fade_anim, TEXT_FADE_DELAY_MS);
  lv_anim_set_exec_cb(&text_fade_anim, text_opa_anim_cb);
  lv_anim_start(&text_fade_anim);

  lv_obj_add_flag(screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(screen, branding_touch_handler, LV_EVENT_CLICKED, NULL);
}

static void show_scan_screen(void) {
  // Initialize NFC dots animation.
  memset(&nfc_dots, 0, sizeof(nfc_dots));
  nfc_dots_animation_create(screen, &nfc_dots);

  // Title label with black background box (same as normal scan screen)
  lv_obj_t* text_container = lv_obj_create(screen);
  if (!text_container) {
    return;
  }
  lv_obj_set_style_bg_color(text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_all(text_container, SCAN_TEXT_PADDING, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);

  scan_label = lv_label_create(text_container);
  if (!scan_label) {
    return;
  }
  lv_label_set_text(scan_label, langpack_get_string(LANGPACK_ID_SCAN_TAP));
  lv_obj_set_style_text_color(scan_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(scan_label, FONT_SCAN_TITLE, 0);
  lv_obj_center(scan_label);

  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Start NFC dots animation
  nfc_dots_animation_start(&nfc_dots);

  memset(&menu_button, 0, sizeof(menu_button));
  top_menu_create(screen, &menu_button, NULL);

  is_scan_state = true;
  is_branding_state = false;
}

// Public API

lv_obj_t* screen_onboarding_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  bool resume_at_scan = false;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_onboarding_tag) {
    resume_at_scan = show_screen->params.onboarding.resume_at_scan;
  }

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  if (resume_at_scan) {
    show_scan_screen();
  } else {
    // Always start with logo screen
    show_logo_screen();
  }

  return screen;
}

void screen_onboarding_destroy(void) {
  if (!screen) {
    return;
  }

  lv_anim_del(&screen_opa_anim, screen_opa_anim_cb);

  if (fade_hold_timer) {
    lv_timer_del(fade_hold_timer);
    fade_hold_timer = NULL;
  }

  if (is_branding_state) {
    lv_anim_del(&logo_scale_anim, logo_scale_anim_cb);
    lv_anim_del(&logo_pos_anim, logo_pos_anim_cb);
    lv_anim_del(&text_fade_anim, text_opa_anim_cb);
  }

  if (is_scan_state) {
    nfc_dots_animation_destroy(&nfc_dots);
    memset(&nfc_dots, 0, sizeof(nfc_dots));
  }

  top_menu_destroy(&menu_button);
  memset(&menu_button, 0, sizeof(menu_button));

  lv_obj_del(screen);
  screen = NULL;
  logo_img = NULL;
  text_img = NULL;
  scan_label = NULL;
  is_scan_state = false;
  is_branding_state = false;
}

void screen_onboarding_update(void* ctx) {
  if (!screen) {
    screen_onboarding_init(ctx);
  }
}
