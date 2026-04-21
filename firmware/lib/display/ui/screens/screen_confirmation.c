#include "screen_confirmation.h"

#include "assert.h"
#include "display.pb.h"
#include "dot_ring.h"
#include "langpack.h"
#include "loading_ring.h"
#include "ui.h"
#include "widgets/nfc_dots_animation.h"

#include <string.h>

#define SCREEN_BRIGHTNESS 100

#define LEGACY_DONE_TEXT      "Success"
#define LEGACY_DONE_TEXT_ALT  "SUCCESS"
#define DONE_DISPLAY_TEXT     "Done"
#define SIGNING_TEXT          "Signing..."
#define LEGACY_SIGNING_TEXT   "SIGNING..."
#define SIGNING_RING_TAIL_OPA 38

#define COLOR_GREEN    lv_color_make(0xD1, 0xFB, 0x96)
#define COLOR_INACTIVE lv_color_hex(0x404040)
#define INACTIVE_OPA   LV_OPA_70

#define FONT_TEXT_SUCCESS (&cash_sans_mono_regular_30)
#define FONT_TEXT_LOADING (&cash_sans_mono_regular_30)

#define SUCCESS_RING_START_DELAY_MS      250
#define SUCCESS_RING_FILL_DURATION_MS    650
#define SUCCESS_RING_HOLD_DURATION_MS    2400
#define SUCCESS_RING_OUTRO_DURATION_MS   650
#define SUCCESS_CONTENT_FADE_DURATION_MS 650
#define SUCCESS_CONTENT_FADE_DELAY_MS \
  (SUCCESS_RING_FILL_DURATION_MS - SUCCESS_CONTENT_FADE_DURATION_MS)
#define SUCCESS_CHECKMARK_Y_OFFSET -28
#define SUCCESS_TEXT_Y_OFFSET      28
#define LOADING_TEXT_Y_OFFSET      0

extern const lv_img_dsc_t check;

static lv_obj_t* screen = NULL;
static lv_obj_t* icon = NULL;
static lv_obj_t* label = NULL;
static dot_ring_t success_ring = {0};
static loading_ring_t loading_ring = {0};
static lv_timer_t* success_ring_intro_timer = NULL;
static lv_timer_t* success_ring_hold_timer = NULL;
static const lv_img_dsc_t* success_icon_dsc = NULL;
static char success_primary_text[sizeof(((fwpb_display_params_confirmation*)0)->text)] = {0};
static bool use_loading_variant = false;

static void success_content_set_opa(lv_opa_t content_opa);
static void success_content_opa_anim_cb(void* var, int32_t value);
static void start_success_ring_intro(void);
static void success_ring_intro_timer_cb(lv_timer_t* timer);
static void success_ring_fill_complete(void* user_data);
static void success_ring_hold_timer_cb(lv_timer_t* timer);
static void success_ring_outro_anim_cb(void* var, int32_t value);
static int get_success_ring_order(int dot_index, int total_dots);
static bool is_done_confirmation(const char* text);
static bool is_signing_confirmation_text(const char* text);
static const char* get_confirmation_display_text(const char* text);
static const char* get_success_content_text(void);
static const lv_img_dsc_t* get_success_content_icon(void);
static void layout_success_content(void);

static const char* resolve_confirmation_text(const fwpb_display_params_confirmation* params) {
  if (!params) {
    return langpack_get_string(LANGPACK_ID_CONFIRMATION_DONE);
  }

  if (params->text_id != 0) {
    const char* localized = langpack_get_string((langpack_string_id_t)params->text_id);
    if (localized != NULL && localized[0] != '\0') {
      return localized;
    }
  }

  if (params->text[0] != '\0') {
    return params->text;
  }

  return (params->mode ==
          fwpb_display_params_confirmation_display_params_confirmation_mode_DISPLAY_PARAMS_CONFIRMATION_MODE_LOADING)
           ? langpack_get_string(LANGPACK_ID_CONFIRMATION_SIGNING)
           : langpack_get_string(LANGPACK_ID_CONFIRMATION_DONE);
}

static bool is_loading_confirmation(const fwpb_display_params_confirmation* params) {
  if (!params) {
    return false;
  }

  if (
    params->mode ==
    fwpb_display_params_confirmation_display_params_confirmation_mode_DISPLAY_PARAMS_CONFIRMATION_MODE_LOADING) {
    return true;
  }

  if (params->text_id == LANGPACK_ID_CONFIRMATION_SIGNING) {
    return true;
  }

  // Preserve legacy behavior for callers that still populate only text while
  // leaving mode at its zero-value default (SUCCESS).
  if (is_signing_confirmation_text(params->text)) {
    return true;
  }

  if (
    params->mode ==
    fwpb_display_params_confirmation_display_params_confirmation_mode_DISPLAY_PARAMS_CONFIRMATION_MODE_SUCCESS) {
    return false;
  }

  if (params->text_id == LANGPACK_ID_CONFIRMATION_DONE) {
    return false;
  }

  return false;
}

static bool is_done_confirmation(const char* text) {
  if (text == NULL) {
    return false;
  }

  return strcmp(text, LEGACY_DONE_TEXT) == 0 || strcmp(text, LEGACY_DONE_TEXT_ALT) == 0;
}

static bool is_signing_confirmation_text(const char* text) {
  if (text == NULL) {
    return false;
  }

  return strcmp(text, SIGNING_TEXT) == 0 || strcmp(text, LEGACY_SIGNING_TEXT) == 0;
}

static const char* get_confirmation_display_text(const char* text) {
  if (is_signing_confirmation_text(text)) {
    return SIGNING_TEXT;
  }

  if (is_done_confirmation(text)) {
    return DONE_DISPLAY_TEXT;
  }

  return text;
}

lv_obj_t* screen_confirmation_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_confirmation* params = NULL;

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_confirmation_tag) {
    params = &show_screen->params.confirmation;
  }
  use_loading_variant = is_loading_confirmation(params);
  const char* text = resolve_confirmation_text(params);
  success_icon_dsc = get_success_content_icon();
  strncpy(success_primary_text, get_confirmation_display_text(text),
          sizeof(success_primary_text) - 1);
  success_primary_text[sizeof(success_primary_text) - 1] = '\0';

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  if (use_loading_variant) {
    loading_ring_create(screen, &loading_ring);
    loading_ring_set_palette(&loading_ring, 0xFF, 0xFF, 0xFF, 0x33, 0x33, 0x33,
                             SIGNING_RING_TAIL_OPA);
    loading_ring_start(&loading_ring);
  } else {
    dot_ring_create(screen, &success_ring);
    dot_ring_show(&success_ring);

    icon = lv_img_create(screen);
    if (!icon) {
      return NULL;
    }
    lv_img_set_src(icon, success_icon_dsc);
    lv_obj_set_style_img_recolor(icon, COLOR_GREEN, 0);
    lv_obj_set_style_img_recolor_opa(icon, LV_OPA_COVER, 0);
    lv_obj_set_style_img_opa(icon, LV_OPA_TRANSP, 0);
  }

  label = lv_label_create(screen);
  if (!label) {
    return NULL;
  }
  lv_label_set_text(label, get_success_content_text());
  lv_obj_set_style_text_color(label, use_loading_variant ? lv_color_white() : COLOR_GREEN, 0);
  lv_obj_set_style_text_font(label, use_loading_variant ? FONT_TEXT_LOADING : FONT_TEXT_SUCCESS, 0);
  if (use_loading_variant) {
    lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_set_width(label, 400);
    lv_label_set_long_mode(label, LV_LABEL_LONG_WRAP);
    lv_obj_align(label, LV_ALIGN_CENTER, 0, LOADING_TEXT_Y_OFFSET);
  } else {
    lv_obj_set_style_text_opa(label, LV_OPA_TRANSP, 0);
    layout_success_content();

    if (SUCCESS_RING_START_DELAY_MS > 0) {
      success_ring_intro_timer =
        lv_timer_create(success_ring_intro_timer_cb, SUCCESS_RING_START_DELAY_MS, NULL);
      if (success_ring_intro_timer) {
        lv_timer_set_repeat_count(success_ring_intro_timer, 1);
      } else {
        start_success_ring_intro();
      }
    } else {
      start_success_ring_intro();
    }
  }

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_confirmation_destroy(void) {
  if (!screen) {
    return;
  }

  if (success_ring_hold_timer) {
    lv_timer_del(success_ring_hold_timer);
    success_ring_hold_timer = NULL;
  }
  if (success_ring_intro_timer) {
    lv_timer_del(success_ring_intro_timer);
    success_ring_intro_timer = NULL;
  }
  lv_anim_del(&success_ring, success_content_opa_anim_cb);
  lv_anim_del(&success_ring, success_ring_outro_anim_cb);
  dot_ring_destroy(&success_ring);
  loading_ring_destroy(&loading_ring);

  lv_obj_del(screen);
  screen = NULL;
  icon = NULL;
  label = NULL;
  success_icon_dsc = NULL;
  memset(success_primary_text, 0, sizeof(success_primary_text));
  use_loading_variant = false;
}

void screen_confirmation_update(void* ctx) {
  if (!screen) {
    screen_confirmation_init(ctx);
  }
}

static void layout_success_content(void) {
  if (!icon || !label) {
    return;
  }

  lv_obj_align(icon, LV_ALIGN_CENTER, 0, SUCCESS_CHECKMARK_Y_OFFSET);
  lv_obj_align(label, LV_ALIGN_CENTER, 0, SUCCESS_TEXT_Y_OFFSET);
}

static void start_success_ring_intro(void) {
  lv_anim_t fade_in_anim;
  lv_anim_init(&fade_in_anim);
  lv_anim_set_var(&fade_in_anim, &success_ring);
  lv_anim_set_values(&fade_in_anim, 0, LV_OPA_COVER);
  lv_anim_set_duration(&fade_in_anim, SUCCESS_CONTENT_FADE_DURATION_MS);
  lv_anim_set_delay(&fade_in_anim, SUCCESS_CONTENT_FADE_DELAY_MS);
  lv_anim_set_exec_cb(&fade_in_anim, success_content_opa_anim_cb);
  lv_anim_set_path_cb(&fade_in_anim, lv_anim_path_ease_out);
  lv_anim_start(&fade_in_anim);

  dot_ring_animate_fill(&success_ring, 100, SUCCESS_RING_FILL_DURATION_MS, DOT_RING_COLOR_GREEN,
                        DOT_RING_FILL_CLOCKWISE_TOP, success_ring_fill_complete, NULL);
}

static void success_ring_intro_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (success_ring_intro_timer) {
    lv_timer_del(success_ring_intro_timer);
    success_ring_intro_timer = NULL;
  }

  start_success_ring_intro();
}

static void success_ring_fill_complete(void* user_data) {
  (void)user_data;

  success_content_set_opa(LV_OPA_COVER);
  if (success_ring_hold_timer) {
    lv_timer_del(success_ring_hold_timer);
  }
  success_ring_hold_timer =
    lv_timer_create(success_ring_hold_timer_cb, SUCCESS_RING_HOLD_DURATION_MS, NULL);
  if (success_ring_hold_timer) {
    lv_timer_set_repeat_count(success_ring_hold_timer, 1);
  }
}

static void success_ring_hold_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (success_ring_hold_timer) {
    lv_timer_del(success_ring_hold_timer);
    success_ring_hold_timer = NULL;
  }

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &success_ring);
  lv_anim_set_values(&anim, 0, success_ring.dot_count);
  lv_anim_set_duration(&anim, SUCCESS_RING_OUTRO_DURATION_MS);
  lv_anim_set_exec_cb(&anim, success_ring_outro_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);

  lv_anim_t fade_out_anim;
  lv_anim_init(&fade_out_anim);
  lv_anim_set_var(&fade_out_anim, &success_ring);
  lv_anim_set_values(&fade_out_anim, LV_OPA_COVER, LV_OPA_TRANSP);
  lv_anim_set_duration(&fade_out_anim, SUCCESS_CONTENT_FADE_DURATION_MS);
  lv_anim_set_delay(&fade_out_anim, SUCCESS_CONTENT_FADE_DELAY_MS);
  lv_anim_set_exec_cb(&fade_out_anim, success_content_opa_anim_cb);
  lv_anim_set_path_cb(&fade_out_anim, lv_anim_path_ease_out);
  lv_anim_start(&fade_out_anim);
}

static void success_ring_outro_anim_cb(void* var, int32_t value) {
  dot_ring_t* ring = (dot_ring_t*)var;
  if (!ring || !ring->is_initialized || ring->dot_count == 0) {
    return;
  }

  int32_t inactive_count = value;
  if (inactive_count < 0) {
    inactive_count = 0;
  } else if (inactive_count > ring->dot_count) {
    inactive_count = ring->dot_count;
  }

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    bool should_be_active = get_success_ring_order(i, ring->dot_count) >= inactive_count;
    dot_ring_set_dot_state(ring, i, should_be_active, COLOR_GREEN, LV_OPA_COVER, COLOR_INACTIVE,
                           INACTIVE_OPA);
  }
}

static void success_content_set_opa(lv_opa_t content_opa) {
  if (icon) {
    lv_obj_set_style_img_opa(icon, content_opa, 0);
  }
  if (label) {
    lv_obj_set_style_text_opa(label, content_opa, 0);
  }
}

static void success_content_opa_anim_cb(void* var, int32_t value) {
  (void)var;
  success_content_set_opa((lv_opa_t)value);
}

static const char* get_success_content_text(void) {
  return success_primary_text;
}

static const lv_img_dsc_t* get_success_content_icon(void) {
  return &check;
}

static int get_success_ring_order(int dot_index, int total_dots) {
  int top_index = total_dots / 2;
  int rotated_index = dot_index - top_index;

  if (rotated_index < 0) {
    rotated_index += total_dots;
  }

  return rotated_index;
}
