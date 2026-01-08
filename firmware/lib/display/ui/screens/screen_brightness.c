#include "screen_brightness.h"

#include "assert.h"
#include "bottom_back.h"
#include "display.pb.h"
#include "langpack.h"
#include "ui.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#define SCREEN_BRIGHTNESS     100
#define BRIGHTNESS_INCREMENT  10
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
#define SCREEN_TITLE_Y_OFFSET 50
#define COLOR_TITLE           0xADADAD
#define COLOR_TEXT            0xFFFFFF
#define COLOR_MARKER_ACTIVE   0xCCCCCC
#define COLOR_MARKER_INACTIVE 0x555555
#define COLOR_MARKER_BORDER   0x000000
#define ARC_BG_OPACITY        LV_OPA_20
#define DEGREES_TO_RADIANS    (M_PI / 180.0f)
#define FONT_SCREEN_TITLE     (&cash_sans_mono_regular_22)
#define FONT_PERCENTAGE       (&cash_sans_mono_regular_36)

#define NUM_MARKERS 10

static lv_obj_t* screen = NULL;
static lv_obj_t* progress_ring = NULL;
static lv_obj_t* brightness_label = NULL;
static lv_obj_t* marker_circles[NUM_MARKERS];
static bottom_back_t back_button;

static void update_brightness_display(uint8_t brightness) {
  if (progress_ring) {
    // Calculate which marker index the brightness corresponds to (0-9)
    int marker_index = (brightness / BRIGHTNESS_INCREMENT);
    if (marker_index > 0) {
      marker_index--;  // Adjust since marker[0] = 10%, marker[1] = 20%, etc.
    }
    if (marker_index >= NUM_MARKERS) {
      marker_index = NUM_MARKERS - 1;
    }

    // Arc should reach the marker position
    float progress_angle = marker_index * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1));
    lv_arc_set_angles(progress_ring, 0, (int32_t)progress_angle);
  }

  if (brightness_label) {
    lv_label_set_text_fmt(brightness_label, "%d%%", brightness);
  }

  // Update marker circles based on brightness
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

      // Add black border to tip marker
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
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create "BRIGHTNESS" title at top
  lv_obj_t* screen_title = lv_label_create(screen);
  lv_label_set_text(screen_title, langpack_get_string(LANGPACK_ID_BRIGHTNESS_TITLE));
  lv_obj_set_style_text_font(screen_title, FONT_SCREEN_TITLE, 0);
  lv_obj_set_style_text_color(screen_title, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_align(screen_title, LV_ALIGN_TOP_MID, 0, SCREEN_TITLE_Y_OFFSET);

  // Create bottom back button
  memset(&back_button, 0, sizeof(bottom_back_t));
  bottom_back_create(screen, &back_button);

  uint8_t brightness = show_screen ? show_screen->brightness_percent : DEFAULT_BRIGHTNESS;

  const lv_coord_t center_x = lv_obj_get_width(screen) / 2;
  const lv_coord_t center_y = lv_obj_get_height(screen) / 2;

  progress_ring = lv_arc_create(screen);
  lv_obj_set_size(progress_ring, ARC_DIAMETER, ARC_DIAMETER);
  lv_obj_center(progress_ring);
  // Background arc should end at the last marker position
  float bg_end_angle = (NUM_MARKERS - 1) * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1));
  lv_arc_set_bg_angles(progress_ring, 0, (int32_t)bg_end_angle);

  // Calculate which marker index the brightness corresponds to (0-9)
  int marker_index = (brightness / BRIGHTNESS_INCREMENT);
  if (marker_index > 0) {
    marker_index--;  // Adjust since marker[0] = 10%, marker[1] = 20%, etc.
  }
  if (marker_index >= NUM_MARKERS) {
    marker_index = NUM_MARKERS - 1;
  }

  // Arc should reach the marker position - use same float calculation as markers
  float progress_angle = marker_index * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1));
  lv_arc_set_angles(progress_ring, 0, (int32_t)progress_angle);

  lv_obj_remove_style(progress_ring, NULL, LV_PART_KNOB);
  lv_obj_set_style_arc_color(progress_ring, lv_color_white(), LV_PART_INDICATOR);
  lv_obj_set_style_arc_width(progress_ring, ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(progress_ring, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(progress_ring, true, LV_PART_INDICATOR);

  // Background ring (faded)
  lv_obj_set_style_arc_color(progress_ring, lv_color_white(), LV_PART_MAIN);
  lv_obj_set_style_arc_width(progress_ring, ARC_WIDTH, LV_PART_MAIN);
  lv_obj_set_style_arc_opa(progress_ring, ARC_BG_OPACITY, LV_PART_MAIN);
  lv_obj_set_style_arc_rounded(progress_ring, true, LV_PART_MAIN);

  lv_arc_set_rotation(progress_ring, ARC_START_ANGLE);
  lv_obj_clear_flag(progress_ring, LV_OBJ_FLAG_CLICKABLE);

  // Create marker circles at 10% increments
  for (int i = 0; i < NUM_MARKERS; i++) {
    // Use float calculation for precise positioning
    float angle_deg = ARC_START_ANGLE + (i * ((float)ARC_TOTAL_ANGLE / (NUM_MARKERS - 1)));
    float angle_rad = angle_deg * DEGREES_TO_RADIANS;

    lv_coord_t x = center_x + (lv_coord_t)(MARKER_CIRCLE_RADIUS * cosf(angle_rad));
    lv_coord_t y = center_y + (lv_coord_t)(MARKER_CIRCLE_RADIUS * sinf(angle_rad));

    marker_circles[i] = lv_obj_create(screen);
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

    // Add black border to tip marker
    if (is_tip) {
      lv_obj_set_style_border_width(marker_circles[i], MARKER_BORDER_WIDTH, 0);
      lv_obj_set_style_border_color(marker_circles[i], lv_color_hex(COLOR_MARKER_BORDER), 0);
    } else {
      lv_obj_set_style_border_width(marker_circles[i], 0, 0);
    }
  }

  // Create brightness percentage label
  brightness_label = lv_label_create(screen);
  lv_label_set_text_fmt(brightness_label, "%d%%", brightness);
  lv_obj_set_style_text_font(brightness_label, FONT_PERCENTAGE, 0);
  lv_obj_set_style_text_color(brightness_label, lv_color_hex(COLOR_TEXT), 0);
  lv_obj_center(brightness_label);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_brightness_destroy(void) {
  if (!screen) {
    return;
  }

  bottom_back_destroy(&back_button);
  lv_obj_del(screen);
  screen = NULL;
  progress_ring = NULL;
  brightness_label = NULL;
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
