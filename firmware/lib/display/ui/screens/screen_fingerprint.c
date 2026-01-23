#include "screen_fingerprint.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "top_back.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

static void start_button_click_handler(lv_event_t* e);

// Page definitions
#define PAGE_INTRO    0
#define PAGE_SCANNING 1
#define PAGE_SUCCESS  2

// Layout configuration
#define TITLE_Y_OFFSET      90
#define TEXT_Y_OFFSET       (-30)
#define BUTTON_Y_OFFSET     80
#define PROGRESS_ARC_RADIUS 165
#define PROGRESS_ARC_WIDTH  10
#define TICK_COUNT          32
#define TICK_LENGTH         8
#define TICK_WIDTH          2
#define TEXT_WIDTH          280
#define TEXT_WIDTH_INTRO    420
#define SUCCESS_ARC_RADIUS  165
#define SUCCESS_ARC_WIDTH   8

// Checkmark configuration
#define CHECKMARK_PADDING 20

// Angle configuration
#define START_ANGLE       (-90.0f)
#define FULL_CIRCLE_ANGLE 360.0f

// Button configuration
#define BUTTON_RADIUS       30
#define BUTTON_BORDER_WIDTH 2
#define BUTTON_PADDING_X    30
#define BUTTON_PADDING_Y    16

// Colors
#define COLOR_TITLE 0xADADAD

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_24)
#define FONT_TEXT    (&cash_sans_mono_regular_34)
#define FONT_STATUS  (&cash_sans_mono_regular_34)
#define FONT_BUTTON  (&cash_sans_mono_regular_30)
#define FONT_SUCCESS (&cash_sans_mono_regular_34)

// External image declarations
extern const lv_img_dsc_t exclamation_circle;
extern const lv_img_dsc_t check;

static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* text_label = NULL;
static lv_obj_t* status_label = NULL;
static lv_obj_t* button_container = NULL;
static lv_obj_t* button_label = NULL;
static lv_obj_t* success_container = NULL;
static lv_obj_t* success_checkmark = NULL;
static lv_obj_t* success_label = NULL;
static lv_obj_t* success_arc = NULL;
static lv_obj_t* exclamation_image = NULL;
static top_back_t back_button = {0};
static lv_obj_t* progress_ticks[TICK_COUNT];
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
  if (button_container) {
    lv_obj_add_flag(button_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (success_container) {
    lv_obj_add_flag(success_container, LV_OBJ_FLAG_HIDDEN);
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

  for (int i = 0; i < TICK_COUNT; i++) {
    if (progress_ticks[i]) {
      lv_obj_add_flag(progress_ticks[i], LV_OBJ_FLAG_HIDDEN);
    }
  }
}

static void create_progress_ticks(lv_obj_t* parent) {
  const lv_coord_t center_x = lv_obj_get_width(parent) / 2;
  const lv_coord_t center_y = lv_obj_get_height(parent) / 2;

  for (int i = 0; i < TICK_COUNT; i++) {
    float angle_deg = START_ANGLE + (i * (FULL_CIRCLE_ANGLE / TICK_COUNT));
    float angle_rad = (angle_deg * M_PI) / 180.0f;

    float inner_radius = PROGRESS_ARC_RADIUS;
    float outer_radius = inner_radius + TICK_LENGTH;

    lv_coord_t x1 = center_x + (lv_coord_t)(inner_radius * cosf(angle_rad));
    lv_coord_t y1 = center_y + (lv_coord_t)(inner_radius * sinf(angle_rad));
    lv_coord_t x2 = center_x + (lv_coord_t)(outer_radius * cosf(angle_rad));
    lv_coord_t y2 = center_y + (lv_coord_t)(outer_radius * sinf(angle_rad));

    progress_ticks[i] = lv_line_create(parent);
    if (!progress_ticks[i]) {
      return;
    }
    static lv_point_precise_t line_points[TICK_COUNT][2];
    line_points[i][0].x = x1;
    line_points[i][0].y = y1;
    line_points[i][1].x = x2;
    line_points[i][1].y = y2;

    lv_line_set_points(progress_ticks[i], line_points[i], 2);
    lv_obj_set_style_line_width(progress_ticks[i], TICK_WIDTH, 0);
    lv_obj_set_style_line_color(progress_ticks[i], lv_color_white(), 0);
    lv_obj_set_style_line_rounded(progress_ticks[i], true, 0);
    lv_obj_add_flag(progress_ticks[i], LV_OBJ_FLAG_HIDDEN);
  }
}

static void update_progress_display(uint8_t progress_percent) {
  if (!screen || current_page != PAGE_SCANNING) {
    return;
  }

  int visible_ticks = (TICK_COUNT * progress_percent) / 100;
  for (int i = 0; i < TICK_COUNT; i++) {
    if (progress_ticks[i]) {
      if (i < visible_ticks) {
        lv_obj_clear_flag(progress_ticks[i], LV_OBJ_FLAG_HIDDEN);
      } else {
        lv_obj_add_flag(progress_ticks[i], LV_OBJ_FLAG_HIDDEN);
      }
    }
  }
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

  if (button_container) {
    lv_obj_remove_event_cb(button_container, start_button_click_handler);
  }

  switch (page) {
    case PAGE_INTRO:
      if (title_label) {
        lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_TITLE));
        lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (text_label) {
        lv_obj_set_width(text_label, TEXT_WIDTH_INTRO);
        lv_label_set_text(text_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_INTRO));
        lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (button_container) {
        lv_label_set_text(button_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_BUTTON));
        lv_obj_clear_flag(button_container, LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_event_cb(button_container, start_button_click_handler, LV_EVENT_CLICKED, NULL);
      }
      if (back_button.container) {
        lv_obj_clear_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_SCANNING:
      if (status_label) {
        lv_obj_clear_flag(status_label, LV_OBJ_FLAG_HIDDEN);
        lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 0);
      }
      if (back_button.container) {
        lv_obj_clear_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_SUCCESS:
      if (success_arc) {
        lv_obj_clear_flag(success_arc, LV_OBJ_FLAG_HIDDEN);
      }
      if (success_container) {
        lv_obj_clear_flag(success_container, LV_OBJ_FLAG_HIDDEN);
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
      // 'break' intentionally omitted.
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

  // Title label
  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
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
  lv_obj_set_width(text_label, TEXT_WIDTH);
  lv_label_set_long_mode(text_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(text_label, LV_ALIGN_CENTER, 0, TEXT_Y_OFFSET);

  // Button label
  button_label = lv_label_create(screen);
  if (!button_label) {
    return NULL;
  }
  lv_label_set_text(button_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_BUTTON));
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
  lv_obj_align(button_container, LV_ALIGN_CENTER, 0, BUTTON_Y_OFFSET);
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

  create_progress_ticks(screen);

  // Status label
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
  lv_obj_set_width(status_label, TEXT_WIDTH);
  lv_label_set_long_mode(status_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 0);

  // Success arc
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

  // Success container: checkmark + label
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

  // Checkmark icon
  success_checkmark = lv_img_create(success_container);
  if (!success_checkmark) {
    return NULL;
  }
  lv_img_set_src(success_checkmark, &check);
  lv_obj_set_style_img_recolor(success_checkmark, lv_color_white(), 0);
  lv_obj_set_style_img_recolor_opa(success_checkmark, LV_OPA_COVER, 0);

  // Success label
  success_label = lv_label_create(success_container);
  if (!success_label) {
    return NULL;
  }
  lv_label_set_text(success_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_SUCCESS));
  lv_obj_set_style_text_color(success_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(success_label, FONT_SUCCESS, 0);

  // Exclamation icon for error state
  exclamation_image = lv_img_create(screen);
  if (!exclamation_image) {
    return NULL;
  }
  lv_img_set_src(exclamation_image, &exclamation_circle);
  lv_obj_align(exclamation_image, LV_ALIGN_CENTER, 0, -60);
  lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);

  // Back button (only create if enrollment is optional)
  if (params && !params->is_required) {
    top_back_create(screen, &back_button, NULL);
    lv_obj_add_flag(back_button.container, LV_OBJ_FLAG_HIDDEN);
  }

  show_page(PAGE_INTRO);

  if (params) {
    update_progress_display(params->progress_percent);
  }

  return screen;
}

void screen_fingerprint_destroy(void) {
  if (!screen) {
    return;
  }

  top_back_destroy(&back_button);

  lv_obj_del(screen);
  screen = NULL;
  title_label = NULL;
  text_label = NULL;
  status_label = NULL;
  button_container = NULL;
  button_label = NULL;
  success_container = NULL;
  success_checkmark = NULL;
  success_label = NULL;
  success_arc = NULL;
  exclamation_image = NULL;
  for (int i = 0; i < TICK_COUNT; i++) {
    progress_ticks[i] = NULL;
  }
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

    // Transition to scanning page when enrollment starts
    if (current_page == PAGE_INTRO &&
        params->status ==
          fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FIRST) {
      show_page(PAGE_SCANNING);
    }

    // Transition to success page when enrollment completes
    if (current_page == PAGE_SCANNING && params->progress_percent >= 100) {
      show_page(PAGE_SUCCESS);
    }

    if (current_page == PAGE_SCANNING) {
      if (status_label) {
        _screen_fingerprint_set_status(status_label, params);
      }

      update_progress_display(params->progress_percent);

      // Show/hide exclamation icon based on error state
      if (params->status ==
          fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_TRY_AGAIN) {
        if (exclamation_image) {
          lv_obj_clear_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);
        }
        if (status_label) {
          lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 40);
        }
      } else {
        if (exclamation_image) {
          lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);
        }
        if (status_label) {
          lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 0);
        }
      }
    }
  }
}
