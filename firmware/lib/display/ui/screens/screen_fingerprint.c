#include "screen_fingerprint.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "widgets/bottom_menu.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

// Page definitions
#define PAGE_INTRO        0
#define PAGE_SCANNING     1
#define PAGE_SUCCESS      2
#define PAGE_FINISH       3  // For provisioned devices - auto advance to scan
#define PAGE_APP_DOWNLOAD 4  // For unprovisioned - show app download prompt
#define PAGE_QR_CODE      5  // For unprovisioned - show QR code

// Layout configuration
#define TITLE_Y_OFFSET      50
#define TEXT_Y_OFFSET       (-30)
#define BUTTON_Y_OFFSET     80
#define PROGRESS_ARC_RADIUS 160
#define PROGRESS_ARC_WIDTH  10
#define TICK_COUNT          32
#define TICK_LENGTH         8
#define TICK_WIDTH          2
#define TEXT_WIDTH          420
#define SUCCESS_ARC_RADIUS  150    // Radius for success circle
#define SUCCESS_ARC_WIDTH   8      // Width of the success circle line
#define QR_IMAGE_Y_OFFSET   (-20)  // Y offset for QR image to make room for menu button

// Checkmark configuration
#define CHECKMARK_PADDING 20  // Padding between checkmark and text

// Angle configuration
#define START_ANGLE       (-90.0f)  // Starting angle for progress ticks
#define FULL_CIRCLE_ANGLE 360.0f    // Full circle angle

// Button configuration
#define BUTTON_WIDTH        200
#define BUTTON_HEIGHT       60
#define BUTTON_RADIUS       30
#define BUTTON_BORDER_WIDTH 2

// Colors
#define COLOR_TITLE 0xADADAD

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_24)
#define FONT_TEXT    (&cash_sans_mono_regular_34)
#define FONT_STATUS  (&cash_sans_mono_regular_34)
#define FONT_BUTTON  (&cash_sans_mono_regular_30)
#define FONT_SUCCESS (&cash_sans_mono_regular_34)

// External image declarations
extern const lv_img_dsc_t bitkey_qr;
extern const lv_img_dsc_t exclamation_circle;

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
static lv_obj_t* qr_image = NULL;
static lv_obj_t* exclamation_image = NULL;
static bottom_menu_t menu_button;
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
  if (qr_image) {
    lv_obj_add_flag(qr_image, LV_OBJ_FLAG_HIDDEN);
  }
  if (exclamation_image) {
    lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);
  }
  if (menu_button.container) {
    lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
  }

  // Hide all ticks
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

    // Calculate tick positions - outward facing
    float inner_radius = PROGRESS_ARC_RADIUS;
    float outer_radius = inner_radius + TICK_LENGTH;

    lv_coord_t x1 = center_x + (lv_coord_t)(inner_radius * cosf(angle_rad));
    lv_coord_t y1 = center_y + (lv_coord_t)(inner_radius * sinf(angle_rad));
    lv_coord_t x2 = center_x + (lv_coord_t)(outer_radius * cosf(angle_rad));
    lv_coord_t y2 = center_y + (lv_coord_t)(outer_radius * sinf(angle_rad));

    // Create line for tick
    progress_ticks[i] = lv_line_create(parent);
    static lv_point_precise_t line_points[TICK_COUNT][2];
    line_points[i][0].x = x1;
    line_points[i][0].y = y1;
    line_points[i][1].x = x2;
    line_points[i][1].y = y2;

    lv_line_set_points(progress_ticks[i], line_points[i], 2);
    lv_obj_set_style_line_width(progress_ticks[i], TICK_WIDTH, 0);
    lv_obj_set_style_line_color(progress_ticks[i], lv_color_white(), 0);
    lv_obj_set_style_line_rounded(progress_ticks[i], true, 0);
    lv_obj_add_flag(progress_ticks[i], LV_OBJ_FLAG_HIDDEN);  // Hidden by default
  }
}

static void update_progress_display(uint8_t progress_percent) {
  if (!screen || current_page != PAGE_SCANNING) {
    return;
  }

  // Update visible ticks based on progress
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

static void show_page(uint8_t page) {
  hide_all_elements();
  current_page = page;

  switch (page) {
    case PAGE_INTRO:
      // Show intro page elements
      if (title_label) {
        lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_TITLE));
        lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (text_label) {
        lv_label_set_text(text_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_INTRO));
        lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (button_container) {
        lv_obj_clear_flag(button_container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_SCANNING:
      // Show scanning page elements (no title, just status and ticks)
      if (status_label) {
        lv_obj_clear_flag(status_label, LV_OBJ_FLAG_HIDDEN);
        // Ensure status label is at correct position
        lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 0);
      }
      // Ticks will be shown by update_progress_display
      break;

    case PAGE_SUCCESS:
      // Show success page elements
      if (success_arc) {
        lv_obj_clear_flag(success_arc, LV_OBJ_FLAG_HIDDEN);
      }
      if (success_container) {
        lv_obj_clear_flag(success_container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_FINISH:
      // Show finish page elements (for provisioned devices)
      if (title_label) {
        lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_FINISH_TITLE));
        lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (text_label) {
        lv_label_set_text(text_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_FINISH));
        lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_APP_DOWNLOAD:
      // Show app download page (for unprovisioned devices)
      if (title_label) {
        lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_FINISH_TITLE));
        lv_obj_clear_flag(title_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (text_label) {
        lv_label_set_text(text_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_APP_DOWNLOAD));
        lv_obj_clear_flag(text_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (button_container) {
        if (button_label) {
          lv_label_set_text(button_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_QR));
        }
        lv_obj_clear_flag(button_container, LV_OBJ_FLAG_HIDDEN);
      }
      break;

    case PAGE_QR_CODE:
      // Show QR code page (for unprovisioned devices)
      if (qr_image) {
        lv_obj_clear_flag(qr_image, LV_OBJ_FLAG_HIDDEN);
      }
      if (menu_button.container) {
        lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
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
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Title label (used for intro and finish pages)
  title_label = lv_label_create(screen);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Text label (used for intro and finish pages)
  text_label = lv_label_create(screen);
  lv_obj_set_style_text_color(text_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(text_label, FONT_TEXT, 0);
  lv_obj_set_style_text_align(text_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_width(text_label, TEXT_WIDTH);
  lv_label_set_long_mode(text_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(text_label, LV_ALIGN_CENTER, 0, TEXT_Y_OFFSET);

  // Button container (intro page)
  button_container = lv_obj_create(screen);
  lv_obj_set_size(button_container, BUTTON_WIDTH, BUTTON_HEIGHT);
  lv_obj_align(button_container, LV_ALIGN_CENTER, 0, BUTTON_Y_OFFSET);
  lv_obj_set_style_radius(button_container, BUTTON_RADIUS, 0);
  lv_obj_set_style_bg_opa(button_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_color(button_container, lv_color_white(), 0);
  lv_obj_set_style_border_width(button_container, BUTTON_BORDER_WIDTH, 0);
  lv_obj_set_style_border_opa(button_container, LV_OPA_COVER, 0);
  lv_obj_clear_flag(button_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(button_container,
                  LV_OBJ_FLAG_EVENT_BUBBLE);  // Allow gestures to bubble to screen

  button_label = lv_label_create(button_container);
  lv_label_set_text(button_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_BUTTON));
  lv_obj_set_style_text_color(button_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(button_label, FONT_BUTTON, 0);
  lv_obj_center(button_label);

  // Create progress ticks (scanning page)
  create_progress_ticks(screen);

  // Status label (scanning page)
  status_label = lv_label_create(screen);
  if (params) {
    _screen_fingerprint_set_status(status_label, params);
  }
  lv_obj_set_style_text_color(status_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(status_label, FONT_STATUS, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_width(status_label, TEXT_WIDTH);
  lv_label_set_long_mode(status_label, LV_LABEL_LONG_WRAP);
  lv_obj_align(status_label, LV_ALIGN_CENTER, 0, 0);

  // Success arc (circle around checkmark on success page)
  success_arc = lv_arc_create(screen);
  lv_obj_set_size(success_arc, SUCCESS_ARC_RADIUS * 2, SUCCESS_ARC_RADIUS * 2);
  lv_obj_center(success_arc);
  lv_arc_set_bg_angles(success_arc, 0, (int)FULL_CIRCLE_ANGLE);
  lv_arc_set_angles(success_arc, 0, (int)FULL_CIRCLE_ANGLE);
  lv_obj_remove_style(success_arc, NULL, LV_PART_KNOB);
  lv_obj_set_style_arc_width(success_arc, SUCCESS_ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(success_arc, lv_color_white(), LV_PART_INDICATOR);
  lv_obj_set_style_arc_width(success_arc, 0, LV_PART_MAIN);  // Hide background arc
  lv_obj_clear_flag(success_arc, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_flag(success_arc, LV_OBJ_FLAG_HIDDEN);

  // Success container (success page)
  success_container = lv_obj_create(screen);
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

  success_checkmark = lv_label_create(success_container);
  lv_label_set_text(success_checkmark, LV_SYMBOL_OK);
  lv_obj_set_style_text_color(success_checkmark, lv_color_white(), 0);
  lv_obj_set_style_text_font(success_checkmark, FONT_SUCCESS, 0);

  success_label = lv_label_create(success_container);
  lv_label_set_text(success_label, langpack_get_string(LANGPACK_ID_FINGERPRINT_SUCCESS));
  lv_obj_set_style_text_color(success_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(success_label, FONT_SUCCESS, 0);

  // QR code image (QR code page)
  qr_image = lv_img_create(screen);
  lv_img_set_src(qr_image, &bitkey_qr);
  lv_obj_align(qr_image, LV_ALIGN_CENTER, 0, QR_IMAGE_Y_OFFSET);
  lv_obj_add_flag(qr_image, LV_OBJ_FLAG_HIDDEN);

  // Exclamation circle image (for "Try again" error state)
  exclamation_image = lv_img_create(screen);
  lv_img_set_src(exclamation_image, &exclamation_circle);
  lv_obj_align(exclamation_image, LV_ALIGN_CENTER, 0, -60);  // Position above status text
  lv_obj_add_flag(exclamation_image, LV_OBJ_FLAG_HIDDEN);

  // Bottom menu button (QR code page)
  memset(&menu_button, 0, sizeof(bottom_menu_t));
  bottom_menu_create(screen, &menu_button, false);  // false = no circle
  lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);

  // Set initial page and progress
  uint8_t initial_page = params ? params->current_page : PAGE_INTRO;
  show_page(initial_page);

  if (params && initial_page == PAGE_SCANNING) {
    update_progress_display(params->progress_percent);
  }

  return screen;
}

void screen_fingerprint_destroy(void) {
  if (!screen) {
    return;
  }

  // Clean up menu button if it exists
  if (menu_button.container) {
    bottom_menu_destroy(&menu_button);
  }

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
  qr_image = NULL;
  exclamation_image = NULL;
  memset(&menu_button, 0, sizeof(bottom_menu_t));
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

    // Check if page changed
    if (params->current_page != current_page) {
      show_page(params->current_page);
    }

    if (params->current_page == PAGE_SCANNING) {
      if (status_label) {
        _screen_fingerprint_set_status(status_label, params);
      }

      update_progress_display(params->progress_percent);

      // Handle error state display
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
