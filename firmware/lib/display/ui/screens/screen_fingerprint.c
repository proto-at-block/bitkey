#include "screen_fingerprint.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "fingerprint_dots.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "top_back.h"

#include <stdio.h>
#include <string.h>

static void start_button_click_handler(lv_event_t* e);

// Page definitions
#define PAGE_INTRO    0
#define PAGE_SCANNING 1
#define PAGE_SUCCESS  2

// Layout configuration
#define TITLE_Y_OFFSET     90
#define TEXT_Y_OFFSET      (-30)
#define TEXT_WIDTH         320
#define TEXT_WIDTH_INTRO   420
#define SUCCESS_ARC_RADIUS 165
#define SUCCESS_ARC_WIDTH  8

// Scanning page layout (fingerprint dots above status text)
#define SCANNING_DOTS_Y_OFFSET   (-35)  // Fingerprint dots position (above center)
#define SCANNING_STATUS_Y_OFFSET 130    // Status text position (below dots)

// Checkmark configuration
#define CHECKMARK_PADDING 20

// Angle configuration (for success arc)
#define FULL_CIRCLE_ANGLE 360.0f

// Circle button configuration (matches menu item circles)
#define CIRCLE_BUTTON_SIZE          100
#define CIRCLE_BUTTON_BOTTOM_MARGIN 32

// Colors
#define COLOR_TITLE         0xADADAD
#define COLOR_CIRCLE_BUTTON 0x404040  // Grey (matches menu item circles)

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_24)
#define FONT_TEXT    (&cash_sans_mono_regular_30)
#define FONT_STATUS  (&cash_sans_mono_regular_30)
#define FONT_SUCCESS (&cash_sans_mono_regular_30)

// External image declarations
extern const lv_img_dsc_t exclamation_circle;
extern const lv_img_dsc_t check;
extern const lv_img_dsc_t arrow_right;

static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* text_label = NULL;
static lv_obj_t* status_label = NULL;
static lv_obj_t* circle_button = NULL;
static lv_obj_t* circle_button_icon = NULL;
static lv_obj_t* success_container = NULL;
static lv_obj_t* success_checkmark = NULL;
static lv_obj_t* success_label = NULL;
static lv_obj_t* success_arc = NULL;
static lv_obj_t* exclamation_image = NULL;
static top_back_t back_button = {0};
static fingerprint_dots_t fp_dots = {0};
static uint8_t current_page = PAGE_INTRO;

static void hide_all_elements(void) {
  if (title_label) {
    lv_obj_add_flag(title_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (text_label) {
    lv_obj_add_flag(text_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (status_label) {
    lv_obj_add_flag(status_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (circle_button) {
    lv_obj_add_flag(circle_button, LV_OBJ_FLAG_HIDDEN);
  }
  if (success_container) {
    lv_obj_add_flag(success_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (success_label) {
    lv_obj_add_flag(success_label, LV_OBJ_FLAG_HIDDEN);
  }
  if (success_arc) {
    lv_obj_add_flag(success_arc, LV_OBJ_FLAG_HIDDEN);
  }
  if (exclamation_image) {
    lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);
  }
  if (back_button.container) {
    lv_obj_add_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
  }

  fingerprint_dots_hide(&fp_dots);
}

static void update_progress_display(uint8_t progress_percent) {
  if (!screen || current_page != PAGE_SCANNING) {
    return;
  }

  fingerprint_dots_set_percent(&fp_dots, progress_percent);
}

static void start_button_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_START_ENROLLMENT, 0);
  }
}

static void show_page(uint8_t page) {
  hide_all_elements();
  current_page = page;

  if (circle_button) {
    lv_obj_remove_event_cb(circle_button, start_button_click_handler);
  }

  switch (page) {
    case PAGE_INTRO:
      if (text_label) {
        lv_obj_set_width(text_label, TEXT_WIDTH_INTRO);
        lv_label_set_text(text_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_INTRO));
        lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (circle_button) {
        lv_obj_clear_flag(circle_button, LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_event_cb(circle_button, start_button_click_handler, LV_EVENT_CLICKED, NULL);
      }
      if (back_button.container) {
        lv_obj_clear_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_SCANNING:
      if (fp_dots.container) {
        lv_obj_align(fp_dots.container, LV_ALIGN_CENTER, 0, SCANNING_DOTS_Y_OFFSET);
      }
      fingerprint_dots_show(&fp_dots);
      if (status_label) {
        lv_obj_clear_flag(status_label, LV_OBJ_FLAG_HIDDEN);
        lv_obj_align(status_label, LV_ALIGN_CENTER, 0, SCANNING_STATUS_Y_OFFSET);
      }
      if (back_button.container) {
        lv_obj_clear_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_SUCCESS:
      if (fp_dots.container) {
        lv_obj_align(fp_dots.container, LV_ALIGN_CENTER, 0, SCANNING_DOTS_Y_OFFSET);
      }
      fingerprint_dots_set_percent(&fp_dots, 100);
      fingerprint_dots_show(&fp_dots);
      if (success_label) {
        lv_obj_set_parent(success_label, screen);
        lv_label_set_text(success_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_SUCCESS));
        lv_obj_align(success_label, LV_ALIGN_CENTER, 0, SCANNING_STATUS_Y_OFFSET);
        lv_obj_clear_flag(success_label, LV_OBJ_FLAG_HIDDEN);
      }
      break;
  }
}

static void _screen_fingerprint_set_status(lv_obj_t* label,
                                           const fwpb_display_params_fingerprint* params) {
  switch (params->status) {
    case fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FIRST:
      lv_label_set_text(label, langpack_get_string(LANGPACK_ID_FINGERPRINT_ENROLL_FIRST));
      break;

    case fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_REPEAT: {
      char status_msg[64];
      snprintf(status_msg, sizeof(status_msg),
               langpack_get_string(LANGPACK_ID_FINGERPRINT_ENROLL_REPEAT),
               (unsigned long)params->samples_remaining);
      lv_label_set_text(label, status_msg);
      break;
    }

    case fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_TRY_AGAIN:
      lv_label_set_text(label, langpack_get_string(LANGPACK_ID_FINGERPRINT_ENROLL_ERROR));
      break;

    case fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FAILED:
      lv_label_set_text(label, langpack_get_string(LANGPACK_ID_FINGERPRINT_ENROLL_FAIL));
      break;

    case fwpb_display_params_fingerprint_display_params_fingerprint_status_NONE:
    default:
      lv_label_set_text(label, "");
      break;
  }
}

lv_obj_t* screen_fingerprint_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  const fwpb_display_params_fingerprint* params = NULL;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_fingerprint_tag) {
    params = &show_screen->params.fingerprint;
  }

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  text_label = lv_label_create(screen);
  if (!text_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(text_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(text_label, FONT_TEXT, 0);
  lv_obj_set_style_text_align(text_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_width(text_label, TEXT_WIDTH);
  lv_label_set_long_mode(text_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(text_label, LV_ALIGN_CENTER, 0, TEXT_Y_OFFSET);

  circle_button = lv_obj_create(screen);
  if (!circle_button) {
    return NULL;
  }
  lv_obj_set_size(circle_button, CIRCLE_BUTTON_SIZE, CIRCLE_BUTTON_SIZE);
  lv_obj_align(circle_button, LV_ALIGN_BOTTOM_MID, 0, -CIRCLE_BUTTON_BOTTOM_MARGIN);
  lv_obj_set_style_radius(circle_button, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(circle_button, lv_color_hex(COLOR_CIRCLE_BUTTON), 0);
  lv_obj_set_style_bg_opa(circle_button, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(circle_button, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(circle_button, 0, 0);
  lv_obj_clear_flag(circle_button, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(circle_button, LV_OBJ_FLAG_CLICKABLE);

  circle_button_icon = lv_img_create(circle_button);
  if (!circle_button_icon) {
    return NULL;
  }
  lv_img_set_src(circle_button_icon, &arrow_right);
  lv_obj_center(circle_button_icon);

  memset(&fp_dots, 0, sizeof(fp_dots));
  fingerprint_dots_create(screen, &fp_dots);

  status_label = lv_label_create(screen);
  if (!status_label) {
    return NULL;
  }
  if (params) {
    _screen_fingerprint_set_status(status_label, params);
  }
  lv_obj_set_style_text_color(status_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(status_label, FONT_STATUS, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_pad_all(status_label, 0, 0);
  lv_obj_set_width(status_label, TEXT_WIDTH);
  lv_label_set_long_mode(status_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 0);

  success_arc = lv_arc_create(screen);
  if (!success_arc) {
    return NULL;
  }
  lv_obj_set_size(success_arc, SUCCESS_ARC_RADIUS * 2, SUCCESS_ARC_RADIUS * 2);
  lv_obj_center(success_arc);
  lv_arc_set_bg_angles(success_arc, 0, (int)FULL_CIRCLE_ANGLE);
  lv_arc_set_angles(success_arc, 0, (int)FULL_CIRCLE_ANGLE);
  lv_obj_remove_style(success_arc, NULL, LV_PART_KNOB);
  lv_obj_set_style_arc_width(success_arc, SUCCESS_ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(success_arc, lv_color_white(), LV_PART_INDICATOR);
  lv_obj_set_style_arc_width(success_arc, 0, LV_PART_MAIN);
  lv_obj_clear_flag(success_arc, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_flag(success_arc, LV_OBJ_FLAG_HIDDEN);

  success_container = lv_obj_create(screen);
  if (!success_container) {
    return NULL;
  }
  lv_obj_set_size(success_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(success_container, LV_ALIGN_CENTER, 0, 0);
  lv_obj_set_style_bg_opa(success_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(success_container, LV_OPA_TRANSP, 0);
  lv_obj_set_layout(success_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(success_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(success_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_column(success_container, CHECKMARK_PADDING, 0);
  lv_obj_clear_flag(success_container, LV_OBJ_FLAG_SCROLLABLE);

  success_checkmark = lv_img_create(success_container);
  if (!success_checkmark) {
    return NULL;
  }
  lv_img_set_src(success_checkmark, &check);
  lv_obj_set_style_img_recolor(success_checkmark, lv_color_white(), 0);
  lv_obj_set_style_img_recolor_opa(success_checkmark, LV_OPA_COVER, 0);

  success_label = lv_label_create(success_container);
  if (!success_label) {
    return NULL;
  }
  lv_label_set_text(success_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_SUCCESS));
  lv_obj_set_style_text_color(success_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(success_label, FONT_SUCCESS, 0);

  exclamation_image = lv_img_create(screen);
  if (!exclamation_image) {
    return NULL;
  }
  lv_img_set_src(exclamation_image, &exclamation_circle);
  lv_obj_align(exclamation_image, LV_ALIGN_CENTER, 0, -60);
  lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);

  if (params && !params->is_required) {
    top_back_create(screen, &back_button, NULL);
    lv_obj_add_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
  }

  if (params && !params->is_required) {
    show_page(PAGE_SCANNING);
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_START_ENROLLMENT, 0);
  } else {
    show_page(PAGE_INTRO);
  }

  if (params) {
    update_progress_display(params->progress_percent);
  }

  return screen;
}

void screen_fingerprint_destroy(void) {
  if (!screen) {
    return;
  }

  fingerprint_dots_destroy(&fp_dots);
  top_back_destroy(&back_button);

  lv_obj_del(screen);
  screen = NULL;
  title_label = NULL;
  text_label = NULL;
  status_label = NULL;
  circle_button = NULL;
  circle_button_icon = NULL;
  success_container = NULL;
  success_checkmark = NULL;
  success_label = NULL;
  success_arc = NULL;
  exclamation_image = NULL;
  current_page = PAGE_INTRO;
}

void screen_fingerprint_update(void* ctx) {
  if (!screen) {
    screen_fingerprint_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_fingerprint_tag) {
    const fwpb_display_params_fingerprint* params = &show_screen->params.fingerprint;

    if (current_page == PAGE_INTRO &&
        params->status ==
          fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FIRST) {
      show_page(PAGE_SCANNING);
    }

    if (current_page == PAGE_SCANNING && params->progress_percent >= 100) {
      show_page(PAGE_SUCCESS);
    }

    if (current_page == PAGE_SCANNING) {
      if (status_label) {
        _screen_fingerprint_set_status(status_label, params);
      }

      update_progress_display(params->progress_percent);

      if (params->status ==
          fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_TRY_AGAIN) {
        if (exclamation_image) {
          lv_obj_clear_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);
          lv_obj_align(exclamation_image, LV_ALIGN_CENTER, -80, SCANNING_STATUS_Y_OFFSET);
        }
        if (status_label) {
          lv_obj_align(status_label, LV_ALIGN_CENTER, 30, SCANNING_STATUS_Y_OFFSET);
        }
      } else {
        if (exclamation_image) {
          lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);
        }
        if (status_label) {
          lv_obj_align(status_label, LV_ALIGN_CENTER, 0, SCANNING_STATUS_Y_OFFSET);
        }
      }
    }
  }
}
