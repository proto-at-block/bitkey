#include "screen_scan.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "hold_cancel.h"
#include "langpack.h"
#include "nfc_dots_animation.h"
#include "top_menu.h"
#include "ui.h"
#include "widgets/approval_button.h"

#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS       100
#define TEXT_CONTAINER_PADDING  12
#define COLOR_CONFIRM_SCAN      APPROVAL_BUTTON_RING_COLOR
#define CANCEL_FALLBACK_HIDE_MS 200

// Error overlay layout
#define ERROR_ICON_Y_OFFSET  (-30)
#define ERROR_LABEL_Y_OFFSET 30

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_36)
#define FONT_ERROR (&cash_sans_mono_regular_30)

// External image declarations
extern const lv_img_dsc_t exclamation_circle;

static lv_obj_t* screen = NULL;
static nfc_dots_animation_t nfc_dots_animation;
static top_menu_t menu_button;
static lv_obj_t* text_container = NULL;
static hold_cancel_t cancel_modal;
static lv_obj_t* error_icon = NULL;
static lv_obj_t* error_label = NULL;
static bool error_overlay_visible = false;
static bool current_use_cancel = false;  // tracks active animation type
static lv_timer_t* cancel_fallback_hide_timer = NULL;

static void stop_cancel_fallback_hide_timer(void) {
  if (cancel_fallback_hide_timer) {
    lv_timer_del(cancel_fallback_hide_timer);
    cancel_fallback_hide_timer = NULL;
  }
}

static void cancel_fallback_hide_timer_cb(lv_timer_t* timer) {
  (void)timer;
  cancel_fallback_hide_timer = NULL;

  if (cancel_modal.is_showing) {
    hold_cancel_hide(&cancel_modal);
  }
}

static void on_cancel_complete(void* user_data) {
  (void)user_data;
  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL, 0);

  // Keep the followup overlay visible long enough for flows that navigate away
  // on cancel. If the action is ignored and the screen stays put, fall back to
  // hiding the modal shortly after.
  stop_cancel_fallback_hide_timer();
  if (cancel_modal.is_showing) {
    cancel_fallback_hide_timer =
      lv_timer_create(cancel_fallback_hide_timer_cb, CANCEL_FALLBACK_HIDE_MS, NULL);
    if (cancel_fallback_hide_timer) {
      lv_timer_set_repeat_count(cancel_fallback_hide_timer, 1);
    }
  }
}

static void on_cancel_dismiss(void* user_data) {
  (void)user_data;
  hold_cancel_hide(&cancel_modal);
}

static void menu_button_cancel_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    hold_cancel_options_t options = {
      .followup_title = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_ON_YOUR_PHONE),
      .followup_text = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCEL_IN_APP),
    };

    hold_cancel_show_with_options(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL,
                                  &options);
  }
}

static void get_scan_context(const void* ctx, const char** scan_context,
                             bool* use_cancel_menu_action) {
  ASSERT(scan_context != NULL);
  ASSERT(use_cancel_menu_action != NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;

  *scan_context = langpack_get_string(LANGPACK_ID_SCAN_TAP);
  *use_cancel_menu_action = false;

  if (!show_screen || (show_screen->which_params != fwpb_display_show_screen_scan_tag)) {
    return;
  }

  switch (show_screen->params.scan.action) {
    case fwpb_display_params_scan_display_params_scan_action_CONFIRM:
      *scan_context = langpack_get_string(LANGPACK_ID_SCAN_CONFIRM);
      *use_cancel_menu_action = true;
      break;

    case fwpb_display_params_scan_display_params_scan_action_NONE:
      // 'break' intentionally omitted.
    case fwpb_display_params_scan_display_params_scan_action_SIGN:
      // 'break' intentionally omitted.
    case fwpb_display_params_scan_display_params_scan_action_VERIFY:
      // 'break' intentionally omitted.
    case fwpb_display_params_scan_display_params_scan_action_TAP:
      // 'break' intentionally omitted.
    default:
      break;
  }
}

lv_obj_t* screen_scan_init(void* ctx) {
  const char* scan_context = NULL;
  bool use_cancel_menu_action = false;
  get_scan_context(ctx, &scan_context, &use_cancel_menu_action);

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Initialize NFC dots animation structure.
  current_use_cancel = use_cancel_menu_action;
  memset(&nfc_dots_animation, 0, sizeof(nfc_dots_animation));
  if (use_cancel_menu_action) {
    nfc_dots_animation.highlight_color = lv_color_hex(COLOR_CONFIRM_SCAN);
  }
  nfc_dots_animation_create(screen, &nfc_dots_animation);

  // Title container (transparent background)
  text_container = lv_obj_create(screen);
  if (!text_container) {
    return NULL;
  }
  lv_obj_set_style_bg_opa(text_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_all(text_container, TEXT_CONTAINER_PADDING, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* title_label = lv_label_create(text_container);
  if (!title_label) {
    return NULL;
  }
  lv_label_set_text(title_label, scan_context);
  lv_color_t text_color = lv_color_white();
  lv_obj_set_style_text_color(title_label, text_color, 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_center(title_label);

  lv_obj_set_size(text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(text_container, LV_ALIGN_CENTER, 0, 0);

  // Set brightness to full immediately
  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  // Start NFC dots animation immediately
  nfc_dots_animation_start(&nfc_dots_animation);

  // Error overlay: exclamation icon + "Try again" label (initially hidden)
  error_icon = lv_img_create(screen);
  if (!error_icon) {
    return NULL;
  }
  lv_img_set_src(error_icon, &exclamation_circle);
  lv_obj_set_style_img_recolor(error_icon, lv_color_white(), 0);
  lv_obj_set_style_img_recolor_opa(error_icon, LV_OPA_COVER, 0);
  lv_obj_align(error_icon, LV_ALIGN_CENTER, 0, ERROR_ICON_Y_OFFSET);
  lv_obj_add_flag(error_icon, LV_OBJ_FLAG_HIDDEN);

  error_label = lv_label_create(screen);
  if (!error_label) {
    return NULL;
  }
  lv_label_set_text(error_label, langpack_get_string(LANGPACK_ID_SCAN_ERROR));
  lv_obj_set_style_text_color(error_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(error_label, FONT_ERROR, 0);
  lv_obj_set_style_text_align(error_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(error_label, LV_ALIGN_CENTER, 0, ERROR_LABEL_Y_OFFSET);
  lv_obj_add_flag(error_label, LV_OBJ_FLAG_HIDDEN);

  // Create hold-to-cancel modal (used by CONFIRM scan menu action)
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  hold_cancel_create(screen, &cancel_modal);

  // Create top menu button (create last so it's on top)
  memset(&menu_button, 0, sizeof(top_menu_t));
  top_menu_create(screen, &menu_button, use_cancel_menu_action ? menu_button_cancel_handler : NULL);

  return screen;
}

void screen_scan_destroy(void) {
  if (!screen) {
    return;
  }

  stop_cancel_fallback_hide_timer();
  nfc_dots_animation_destroy(&nfc_dots_animation);
  memset(&nfc_dots_animation, 0, sizeof(nfc_dots_animation));
  hold_cancel_destroy(&cancel_modal);
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  top_menu_destroy(&menu_button);
  memset(&menu_button, 0, sizeof(menu_button));
  lv_obj_del(screen);
  screen = NULL;
  text_container = NULL;
  error_icon = NULL;
  error_label = NULL;
  error_overlay_visible = false;
}

static void show_error_overlay(bool show) {
  if (show == error_overlay_visible) {
    return;  // No state change needed
  }
  error_overlay_visible = show;

  if (show) {
    // Hide normal screen elements
    if (text_container) {
      lv_obj_add_flag(text_container, LV_OBJ_FLAG_HIDDEN);
    }
    nfc_dots_animation_stop(&nfc_dots_animation);
    if (nfc_dots_animation.container) {
      lv_obj_add_flag(nfc_dots_animation.container, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }
    if (cancel_modal.is_showing) {
      stop_cancel_fallback_hide_timer();
      hold_cancel_hide(&cancel_modal);
    }

    // Show error elements
    if (error_icon) {
      lv_obj_clear_flag(error_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (error_label) {
      lv_obj_clear_flag(error_label, LV_OBJ_FLAG_HIDDEN);
    }
  } else {
    // Hide error elements
    if (error_icon) {
      lv_obj_add_flag(error_icon, LV_OBJ_FLAG_HIDDEN);
    }
    if (error_label) {
      lv_obj_add_flag(error_label, LV_OBJ_FLAG_HIDDEN);
    }

    // Restore normal screen elements
    if (text_container) {
      lv_obj_clear_flag(text_container, LV_OBJ_FLAG_HIDDEN);
    }
    if (nfc_dots_animation.container) {
      lv_obj_clear_flag(nfc_dots_animation.container, LV_OBJ_FLAG_HIDDEN);
    }
    nfc_dots_animation_start(&nfc_dots_animation);
    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }
  }
}

void screen_scan_update(void* ctx) {
  if (!screen) {
    screen_scan_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  bool show_error = false;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_scan_tag) {
    show_error = show_screen->params.scan.show_error;
  }

  show_error_overlay(show_error);

  if (!show_error) {
    const char* scan_context = NULL;
    bool use_cancel_menu_action = false;
    get_scan_context(ctx, &scan_context, &use_cancel_menu_action);

    // If the cancel mode changed, destroy and recreate the animation with the new color
    if (use_cancel_menu_action != current_use_cancel) {
      current_use_cancel = use_cancel_menu_action;
      nfc_dots_animation_destroy(&nfc_dots_animation);
      memset(&nfc_dots_animation, 0, sizeof(nfc_dots_animation));
      if (use_cancel_menu_action) {
        nfc_dots_animation.highlight_color = lv_color_hex(COLOR_CONFIRM_SCAN);
      }
      nfc_dots_animation_create(screen, &nfc_dots_animation);
      // Update label text
      lv_obj_t* label = lv_obj_get_child(text_container, 0);
      if (label) {
        lv_label_set_text(label, scan_context);
        lv_obj_set_style_text_color(label, lv_color_white(), 0);
      }
      nfc_dots_animation_start(&nfc_dots_animation);
      // Move text_container to top of z-order
      lv_obj_move_foreground(text_container);
    }

    if (menu_button.is_initialized) {
      top_menu_destroy(&menu_button);
      memset(&menu_button, 0, sizeof(menu_button));
    }

    if (cancel_modal.is_showing) {
      stop_cancel_fallback_hide_timer();
      hold_cancel_hide(&cancel_modal);
    }

    top_menu_create(screen, &menu_button,
                    use_cancel_menu_action ? menu_button_cancel_handler : NULL);
  }
}
