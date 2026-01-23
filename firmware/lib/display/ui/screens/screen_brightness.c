#include "screen_brightness.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "top_back.h"
#include "ui.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

// Forward declarations
static void arc_drag_handler(lv_event_t* e);
static void update_brightness_display(uint8_t brightness);
static void back_button_click_handler(lv_event_t* e);

#define SCREEN_BRIGHTNESS     100
#define BRIGHTNESS_INCREMENT  5
#define DEFAULT_BRIGHTNESS    50
#define ARC_DIAMETER          270
#define ARC_WIDTH             22
#define ARC_GAP_DEG           15
#define ARC_START_ANGLE       ((-90) + (ARC_GAP_DEG / 2))
#define ARC_END_ANGLE         (270 - (ARC_GAP_DEG / 2))
#define ARC_TOTAL_ANGLE       (ARC_END_ANGLE - ARC_START_ANGLE)
#define ANGLE_PER_INCREMENT   (ARC_TOTAL_ANGLE / (NUM_MARKERS - 1))
#define MARKER_CIRCLE_SIZE    12
#define MARKER_CIRCLE_RADIUS  ((ARC_DIAMETER / 2) - (ARC_WIDTH / 2))
#define MARKER_BORDER_WIDTH   2
#define CENTER_LABEL_Y_OFFSET -15
#define COLOR_TITLE           0xADADAD
#define COLOR_TEXT            0xFFFFFF
#define COLOR_MARKER_ACTIVE   0xCCCCCC
#define COLOR_MARKER_INACTIVE 0x555555
#define COLOR_MARKER_BORDER   0x000000
#define ARC_BG_OPACITY        LV_OPA_20
#define DEGREES_TO_RADIANS    (M_PI / 180.0f)
#define FONT_CENTER_TITLE     (&cash_sans_mono_regular_22)
#define FONT_PERCENTAGE       (&cash_sans_mono_regular_48)

#define NUM_MARKERS 20

static lv_obj_t* screen = NULL;
static lv_obj_t* progress_ring = NULL;
static lv_obj_t* center_title_label = NULL;
static lv_obj_t* brightness_label = NULL;
static lv_obj_t* marker_circles[NUM_MARKERS];
static top_back_t back_button;
static uint8_t current_brightness = DEFAULT_BRIGHTNESS;

static void back_button_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT,
                        current_brightness);
  }
}

static void arc_drag_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSING || code == LV_EVENT_PRESSED) {
    lv_indev_t* indev = lv_indev_get_act();
    if (!indev || !progress_ring) {
      return;
    }

    lv_point_t point;
    lv_indev_get_point(indev, &point);

    // Calculate angle from center to touch point
    lv_coord_t center_x = lv_obj_get_width(screen) / 2;
    lv_coord_t center_y = lv_obj_get_height(screen) / 2;

    float dx = point.x - center_x;
    float dy = point.y - center_y;
    float angle_rad = atan2f(dy, dx);
    float angle_deg = angle_rad * 180.0f / M_PI;

    if (angle_deg < 0) {
      angle_deg += 360.0f;
    }

    angle_deg -= ARC_START_ANGLE;
    while (angle_deg < 0) {
      angle_deg += 360.0f;
    }
    while (angle_deg >= 360.0f) {
      angle_deg -= 360.0f;
    }

    uint8_t new_brightness;

    // Handle gap region
    if (angle_deg > ARC_TOTAL_ANGLE) {
      float gap_midpoint = (ARC_TOTAL_ANGLE + 360.0f) / 2.0f;

      if (angle_deg < gap_midpoint) {
        new_brightness = 100;
      } else {
        new_brightness = BRIGHTNESS_INCREMENT;
      }
    } else {
      // Map angle to brightness
      float progress = angle_deg / ARC_TOTAL_ANGLE;
      new_brightness = (uint8_t)(progress * 100.0f);

      new_brightness = ((new_brightness + (BRIGHTNESS_INCREMENT / 2)) / BRIGHTNESS_INCREMENT) *
                       BRIGHTNESS_INCREMENT;

      if (new_brightness < BRIGHTNESS_INCREMENT) {
        new_brightness = BRIGHTNESS_INCREMENT;
      }
      if (new_brightness > 100) {
        new_brightness = 100;
      }
    }

    if (new_brightness != current_brightness) {
      current_brightness = new_brightness;
      update_brightness_display(current_brightness);
      ui_set_brightness(current_brightness);
    }
  }
}

static void update_brightness_display(uint8_t brightness) {
  if (progress_ring) {
    int marker_index = (brightness / BRIGHTNESS_INCREMENT);
    if (marker_index > 0) {
      marker_index--;
    }
    if (marker_index >= NUM_MARKERS) {
      marker_index = NUM_MARKERS - 1;
    }

    float progress_angle = marker_index * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1));
    lv_arc_set_angles(progress_ring, 0, (int32_t)progress_angle);
  }

  if (brightness_label) {
    lv_label_set_text_fmt(brightness_label, "%d%%", brightness);
  }

  // Update marker colors
  for (int i = 0; i < NUM_MARKERS; i++) {
    if (marker_circles[i]) {
      int marker_brightness = (i + 1) * BRIGHTNESS_INCREMENT;
      bool is_lit = brightness >= marker_brightness;
      bool is_tip = (brightness == marker_brightness);

      if (is_lit) {
        lv_obj_set_style_bg_color(marker_circles[i], lv_color_hex(COLOR_MARKER_ACTIVE), 0);
      } else {
        lv_obj_set_style_bg_color(marker_circles[i], lv_color_hex(COLOR_MARKER_INACTIVE), 0);
      }

      if (is_tip) {
        lv_obj_set_style_border_width(marker_circles[i], MARKER_BORDER_WIDTH, 0);
        lv_obj_set_style_border_color(marker_circles[i], lv_color_hex(COLOR_MARKER_BORDER), 0);
      } else {
        lv_obj_set_style_border_width(marker_circles[i], 0, 0);
      }
    }
  }
}

lv_obj_t* screen_brightness_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(screen, &back_button, back_button_click_handler);

  uint8_t brightness = show_screen ? show_screen->brightness_percent : DEFAULT_BRIGHTNESS;

  const lv_coord_t center_x = lv_obj_get_width(screen) / 2;
  const lv_coord_t center_y = lv_obj_get_height(screen) / 2;

  // Progress arc
  progress_ring = lv_arc_create(screen);
  if (!progress_ring) {
    return NULL;
  }
  lv_obj_set_size(progress_ring, ARC_DIAMETER, ARC_DIAMETER);
  lv_obj_center(progress_ring);

  float bg_end_angle = (NUM_MARKERS - 1) * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1));
  lv_arc_set_bg_angles(progress_ring, 0, (int32_t)bg_end_angle);

  int marker_index = (brightness / BRIGHTNESS_INCREMENT);
  if (marker_index > 0) {
    marker_index--;
  }
  if (marker_index >= NUM_MARKERS) {
    marker_index = NUM_MARKERS - 1;
  }

  float progress_angle = marker_index * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1));
  lv_arc_set_angles(progress_ring, 0, (int32_t)progress_angle);

  lv_obj_remove_style(progress_ring, NULL, LV_PART_KNOB);
  lv_obj_set_style_arc_color(progress_ring, lv_color_white(), LV_PART_INDICATOR);
  lv_obj_set_style_arc_width(progress_ring, ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(progress_ring, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(progress_ring, true, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(progress_ring, lv_color_white(), LV_PART_MAIN);
  lv_obj_set_style_arc_width(progress_ring, ARC_WIDTH, LV_PART_MAIN);
  lv_obj_set_style_arc_opa(progress_ring, ARC_BG_OPACITY, LV_PART_MAIN);
  lv_obj_set_style_arc_rounded(progress_ring, true, LV_PART_MAIN);

  lv_arc_set_rotation(progress_ring, ARC_START_ANGLE);
  lv_obj_add_flag(progress_ring, LV_OBJ_FLAG_CLICKABLE);

  lv_obj_add_event_cb(progress_ring, arc_drag_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(progress_ring, arc_drag_handler, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(progress_ring, arc_drag_handler, LV_EVENT_RELEASED, NULL);

  current_brightness = brightness;

  // Create marker circles
  for (int i = 0; i < NUM_MARKERS; i++) {
    float angle_deg = ARC_START_ANGLE + (i * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1)));
    float angle_rad = angle_deg * DEGREES_TO_RADIANS;

    lv_coord_t x = center_x + (lv_coord_t)(MARKER_CIRCLE_RADIUS * cosf(angle_rad));
    lv_coord_t y = center_y + (lv_coord_t)(MARKER_CIRCLE_RADIUS * sinf(angle_rad));

    marker_circles[i] = lv_obj_create(screen);
    if (!marker_circles[i]) {
      return NULL;
    }
    lv_obj_set_size(marker_circles[i], MARKER_CIRCLE_SIZE, MARKER_CIRCLE_SIZE);
    lv_obj_set_style_radius(marker_circles[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_pos(marker_circles[i], x - MARKER_CIRCLE_SIZE / 2, y - MARKER_CIRCLE_SIZE / 2);

    int marker_brightness = (i + 1) * BRIGHTNESS_INCREMENT;
    bool is_lit = brightness >= marker_brightness;
    bool is_tip = (brightness == marker_brightness);

    if (is_lit) {
      lv_obj_set_style_bg_color(marker_circles[i], lv_color_hex(COLOR_MARKER_ACTIVE), 0);
    } else {
      lv_obj_set_style_bg_color(marker_circles[i], lv_color_hex(COLOR_MARKER_INACTIVE), 0);
    }

    if (is_tip) {
      lv_obj_set_style_border_width(marker_circles[i], MARKER_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(marker_circles[i], lv_color_hex(COLOR_MARKER_BORDER), 0);
    } else {
      lv_obj_set_style_border_width(marker_circles[i], 0, 0);
    }
  }

  // Center title
  center_title_label = lv_label_create(screen);
  if (!center_title_label) {
    return NULL;
  }
  lv_label_set_text(center_title_label, langpack_get_string(LANGPACK_ID_BRIGHTNESS_TITLE));
  lv_obj_set_style_text_font(center_title_label, FONT_CENTER_TITLE, 0);
  lv_obj_set_style_text_color(center_title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_align(center_title_label, LV_ALIGN_CENTER, 0, CENTER_LABEL_Y_OFFSET);

  // Percentage label
  brightness_label = lv_label_create(screen);
  if (!brightness_label) {
    return NULL;
  }
  lv_label_set_text_fmt(brightness_label, "%d%%", brightness);
  lv_obj_set_style_text_font(brightness_label, FONT_PERCENTAGE, 0);
  lv_obj_set_style_text_color(brightness_label, lv_color_hex(COLOR_TEXT), 0);
  lv_obj_align(brightness_label, LV_ALIGN_CENTER, 0, 20);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_brightness_destroy(void) {
  if (!screen) {
    return;
  }

  top_back_destroy(&back_button);
  lv_obj_del(screen);
  screen = NULL;
  progress_ring = NULL;
  center_title_label = NULL;
  brightness_label = NULL;
  current_brightness = DEFAULT_BRIGHTNESS;
  for (int i = 0; i < NUM_MARKERS; i++) {
    marker_circles[i] = NULL;
  }
}

void screen_brightness_update(void* ctx) {
  if (!screen) {
    screen_brightness_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_brightness_tag) {
    update_brightness_display(show_screen->brightness_percent);
  }
}
