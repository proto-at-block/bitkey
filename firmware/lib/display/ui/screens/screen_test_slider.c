#include "screen_test_slider.h"

#include "lvgl/lvgl.h"
#include "screens/screen_test_carousel.h"
#include "screens/screen_test_progress.h"

#include <stdio.h>

#define SLIDER_TRACK_COLOR            lv_color_hex(0x333333)  // dark gray track
#define SLIDER_INDICATOR_COLOR        lv_color_hex(0x0080FF)  // blue indicator fill
#define SLIDER_KNOB_COLOR             lv_color_hex(0x00FF00)  // green knob
#define SLIDER_KNOB_BORDER            lv_color_hex(0xFFFFFF)  // white knob border
#define HANDLE_WIDTH_MIN              10                      // Minimum handle width
#define HANDLE_WIDTH_MAX              80                      // Maximum handle width
#define HANDLE_HEIGHT_MIN             20                      // Minimum handle height
#define HANDLE_HEIGHT_MAX             100                     // Maximum handle height
#define SCREEN_TRANSITION_DURATION_MS 300  // Screen transition animation duration

// Static screen objects
static lv_obj_t* screen = NULL;
static lv_obj_t* size_label = NULL;
static lv_obj_t* value_label = NULL;
static lv_obj_t* slider_track = NULL;      // Background track
static lv_obj_t* slider_indicator = NULL;  // Filled indicator
static lv_obj_t* slider_knob = NULL;       // Draggable knob
static lv_obj_t* btn_width_dec = NULL;
static lv_obj_t* btn_width_inc = NULL;
static lv_obj_t* btn_height_dec = NULL;
static lv_obj_t* btn_height_inc = NULL;
static lv_obj_t* next_button = NULL;
static lv_obj_t* prev_button = NULL;

// Slider dimensions and state
static const int32_t SLIDER_WIDTH = 350;
static const int32_t SLIDER_HEIGHT = 10;
static const int32_t SLIDER_X = 240;  // Center X on 480px screen
static const int32_t SLIDER_Y = 170;  // Centered vertically
static int32_t slider_value = 50;     // 0-100

// Current slider handle dimensions
static int32_t handle_width = 70;   // Width of the slider handle (knob)
static int32_t handle_height = 90;  // Height of the slider handle (knob)

// Update size label display
static void update_size_label(void) {
  if (size_label != NULL) {
    static char buf[32];
    snprintf(buf, sizeof(buf), "W:%d H:%d", (int)handle_width, (int)handle_height);
    lv_label_set_text(size_label, buf);
  }
}

// Update value label display
static void update_value_label(void) {
  if (value_label != NULL) {
    static char buf[16];
    snprintf(buf, sizeof(buf), "%d", (int)slider_value);
    lv_label_set_text(value_label, buf);
  }
}

// Update slider visual position based on value (0-100)
static void update_slider_position(void) {
  if ((slider_knob == NULL) || (slider_indicator == NULL)) {
    return;
  }

  // Calculate knob X position (centered on the value position)
  int32_t value_x = SLIDER_X - (SLIDER_WIDTH / 2) + (slider_value * SLIDER_WIDTH / 100);
  int32_t knob_x = value_x - (handle_width / 2);
  int32_t knob_y = SLIDER_Y - (handle_height / 2) + (SLIDER_HEIGHT / 2);

  // Move the knob
  lv_obj_set_pos(slider_knob, knob_x, knob_y);

  // Update indicator width (from left edge to current value)
  int32_t indicator_width = (slider_value * SLIDER_WIDTH) / 100;
  lv_obj_set_width(slider_indicator, indicator_width);

  // Update value label
  update_value_label();
}

// Update slider handle styling
static void update_slider_style(void) {
  if (slider_knob != NULL) {
    // Set the knob size
    lv_obj_set_size(slider_knob, handle_width, handle_height);

    // Update position to account for new size
    update_slider_position();
  }
}

// Button event handlers for slider handle size adjustment
static void width_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* btn = lv_event_get_target(e);

    if (btn == btn_width_dec) {
      // Decrease width by 5 pixels
      handle_width -= 5;
      if (handle_width < HANDLE_WIDTH_MIN)
        handle_width = HANDLE_WIDTH_MIN;
    } else if (btn == btn_width_inc) {
      // Increase width by 5 pixels
      handle_width += 5;
      if (handle_width > HANDLE_WIDTH_MAX)
        handle_width = HANDLE_WIDTH_MAX;
    }

    // Update slider handle styling
    update_slider_style();

    // Update label
    update_size_label();
  }
}

static void height_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* btn = lv_event_get_target(e);

    if (btn == btn_height_dec) {
      // Decrease height by 5 pixels
      handle_height -= 5;
      if (handle_height < HANDLE_HEIGHT_MIN)
        handle_height = HANDLE_HEIGHT_MIN;
    } else if (btn == btn_height_inc) {
      // Increase height by 5 pixels
      handle_height += 5;
      if (handle_height > HANDLE_HEIGHT_MAX)
        handle_height = HANDLE_HEIGHT_MAX;
    }

    // Update slider handle styling
    update_slider_style();

    // Update label
    update_size_label();
  }
}

// Helper function to update slider from touch position
static void update_slider_from_touch(int32_t x) {
  // Calculate value from X position
  int32_t slider_left = SLIDER_X - (SLIDER_WIDTH / 2);
  int32_t slider_right = SLIDER_X + (SLIDER_WIDTH / 2);

  // Clamp to slider bounds
  if (x < slider_left)
    x = slider_left;
  if (x > slider_right)
    x = slider_right;

  // Calculate value (0-100)
  slider_value = ((x - slider_left) * 100) / SLIDER_WIDTH;
  if (slider_value < 0)
    slider_value = 0;
  if (slider_value > 100)
    slider_value = 100;

  // Update visual position
  update_slider_position();
}

// Check if a point is within the slider's touch area (handle height + padding)
static bool is_touch_in_slider_area(int32_t y) {
  int32_t touch_height = handle_height + 10;  // 10px taller than handle
  int32_t touch_top = SLIDER_Y - (touch_height / 2) + (SLIDER_HEIGHT / 2);
  int32_t touch_bottom = touch_top + touch_height;

  return (y >= touch_top) && (y <= touch_bottom);
}

// Knob drag event handler
static void knob_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSING) {
    // Get touch coordinates
    lv_indev_t* indev = lv_indev_get_act();
    lv_point_t point;
    lv_indev_get_point(indev, &point);

    update_slider_from_touch(point.x);
  }
}

// Screen-level event handler - allows grabbing slider by sliding over it
static void screen_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSING) {
    // Get touch coordinates
    lv_indev_t* indev = lv_indev_get_act();
    lv_point_t point;
    lv_indev_get_point(indev, &point);

    // Check if touch is within slider's vertical bounds (handle area)
    if (is_touch_in_slider_area(point.y)) {
      // Check if touch is within slider's horizontal range
      int32_t slider_left = SLIDER_X - (SLIDER_WIDTH / 2);
      int32_t slider_right = SLIDER_X + (SLIDER_WIDTH / 2);

      if ((point.x >= slider_left) && (point.x <= slider_right)) {
        update_slider_from_touch(point.x);
      }
    }
  }
}

// Next button event handler - navigate to next screen
static void next_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* test_progress_screen = screen_test_progress_init(NULL);
    if (test_progress_screen != NULL) {
      lv_scr_load_anim(test_progress_screen, LV_SCR_LOAD_ANIM_MOVE_LEFT,
                       SCREEN_TRANSITION_DURATION_MS, 0, true);
    }
  }
}

// Prev button event handler - navigate to previous screen
static void prev_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* test_carousel_screen = screen_test_carousel_init(NULL);
    if (test_carousel_screen != NULL) {
      lv_scr_load_anim(test_carousel_screen, LV_SCR_LOAD_ANIM_MOVE_RIGHT,
                       SCREEN_TRANSITION_DURATION_MS, 0, true);
    }
  }
}

lv_obj_t* screen_test_slider_init(void* ctx) {
  (void)ctx;  // Unused parameter

  // Create the screen with black background
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Add screen-level event handler for slide-over slider grabbing
  lv_obj_add_event_cb(screen, screen_event_handler, LV_EVENT_PRESSING, NULL);

  // Create size label at the top
  size_label = lv_label_create(screen);
  if (!size_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(size_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(size_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(size_label, LV_ALIGN_TOP_MID, 0, 10);

  // Create value label below the size label
  value_label = lv_label_create(screen);
  if (!value_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(value_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(value_label, &cash_sans_mono_regular_30, 0);
  lv_obj_align(value_label, LV_ALIGN_TOP_MID, 0, 40);
  lv_label_set_text(value_label, "50");

  // Create custom slider using basic objects

  // Create track (background bar)
  slider_track = lv_obj_create(screen);
  if (!slider_track) {
    return NULL;
  }
  lv_obj_set_size(slider_track, SLIDER_WIDTH, SLIDER_HEIGHT);
  lv_obj_set_pos(slider_track, SLIDER_X - (SLIDER_WIDTH / 2), SLIDER_Y - (SLIDER_HEIGHT / 2));
  lv_obj_set_style_bg_color(slider_track, SLIDER_TRACK_COLOR, 0);
  lv_obj_set_style_border_width(slider_track, 0, 0);
  lv_obj_set_style_radius(slider_track, 5, 0);
  lv_obj_remove_flag(slider_track, LV_OBJ_FLAG_CLICKABLE);  // Pass through clicks to screen

  // Create indicator (filled portion)
  slider_indicator = lv_obj_create(screen);
  if (!slider_indicator) {
    return NULL;
  }
  int32_t initial_indicator_width = (slider_value * SLIDER_WIDTH) / 100;
  lv_obj_set_size(slider_indicator, initial_indicator_width, SLIDER_HEIGHT);
  lv_obj_set_pos(slider_indicator, SLIDER_X - (SLIDER_WIDTH / 2), SLIDER_Y - (SLIDER_HEIGHT / 2));
  lv_obj_set_style_bg_color(slider_indicator, SLIDER_INDICATOR_COLOR, 0);
  lv_obj_set_style_border_width(slider_indicator, 0, 0);
  lv_obj_set_style_radius(slider_indicator, 5, 0);
  lv_obj_remove_flag(slider_indicator, LV_OBJ_FLAG_CLICKABLE);  // Pass through clicks to screen

  // Create knob (draggable handle)
  slider_knob = lv_obj_create(screen);
  if (!slider_knob) {
    return NULL;
  }
  lv_obj_set_size(slider_knob, handle_width, handle_height);
  lv_obj_set_style_bg_color(slider_knob, SLIDER_KNOB_COLOR, 0);
  lv_obj_set_style_border_width(slider_knob, 2, 0);
  lv_obj_set_style_border_color(slider_knob, SLIDER_KNOB_BORDER, 0);
  lv_obj_set_style_radius(slider_knob, 8, 0);

  // Make knob draggable
  lv_obj_add_flag(slider_knob, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(slider_knob, knob_event_handler, LV_EVENT_PRESSING, NULL);

  // Set initial position
  update_slider_position();

  // Update the size label with initial dimensions
  update_size_label();

  // Create width adjustment buttons below the slider (centered row)
  btn_width_dec = lv_button_create(screen);
  if (!btn_width_dec) {
    return NULL;
  }
  lv_obj_set_size(btn_width_dec, 80, 60);
  lv_obj_set_pos(btn_width_dec, 58, 250);
  lv_obj_t* label_width_dec = lv_label_create(btn_width_dec);
  if (!label_width_dec) {
    return NULL;
  }
  lv_label_set_text(label_width_dec, "-W");
  lv_obj_center(label_width_dec);
  lv_obj_add_event_cb(btn_width_dec, width_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  btn_width_inc = lv_button_create(screen);
  if (!btn_width_inc) {
    return NULL;
  }
  lv_obj_set_size(btn_width_inc, 80, 60);
  lv_obj_set_pos(btn_width_inc, 148, 250);
  lv_obj_t* label_width_inc = lv_label_create(btn_width_inc);
  if (!label_width_inc) {
    return NULL;
  }
  lv_label_set_text(label_width_inc, "+W");
  lv_obj_center(label_width_inc);
  lv_obj_add_event_cb(btn_width_inc, width_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  // Create height adjustment buttons below the slider (centered row)
  btn_height_dec = lv_button_create(screen);
  if (!btn_height_dec) {
    return NULL;
  }
  lv_obj_set_size(btn_height_dec, 80, 60);
  lv_obj_set_pos(btn_height_dec, 238, 250);
  lv_obj_t* label_height_dec = lv_label_create(btn_height_dec);
  if (!label_height_dec) {
    return NULL;
  }
  lv_label_set_text(label_height_dec, "-H");
  lv_obj_center(label_height_dec);
  lv_obj_add_event_cb(btn_height_dec, height_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  btn_height_inc = lv_button_create(screen);
  if (!btn_height_inc) {
    return NULL;
  }
  lv_obj_set_size(btn_height_inc, 80, 60);
  lv_obj_set_pos(btn_height_inc, 328, 250);
  lv_obj_t* label_height_inc = lv_label_create(btn_height_inc);
  if (!label_height_inc) {
    return NULL;
  }
  lv_label_set_text(label_height_inc, "+H");
  lv_obj_center(label_height_inc);
  lv_obj_add_event_cb(btn_height_inc, height_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  // Create Prev button at bottom left
  prev_button = lv_button_create(screen);
  if (!prev_button) {
    return NULL;
  }
  lv_obj_set_size(prev_button, 130, 65);
  lv_obj_align(prev_button, LV_ALIGN_BOTTOM_LEFT, 60, -60);
  lv_obj_t* prev_label = lv_label_create(prev_button);
  if (!prev_label) {
    return NULL;
  }
  lv_label_set_text(prev_label, "Prev");
  lv_obj_center(prev_label);
  lv_obj_add_event_cb(prev_button, prev_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  // Create Next button at bottom right
  next_button = lv_button_create(screen);
  if (!next_button) {
    return NULL;
  }
  lv_obj_set_size(next_button, 130, 65);
  lv_obj_align(next_button, LV_ALIGN_BOTTOM_RIGHT, -60, -60);
  lv_obj_t* next_label = lv_label_create(next_button);
  if (!next_label) {
    return NULL;
  }
  lv_label_set_text(next_label, "Next");
  lv_obj_center(next_label);
  lv_obj_add_event_cb(next_button, next_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  return screen;
}

void screen_test_slider_update(void* ctx) {
  (void)ctx;  // Unused parameter
  // Nothing to update for this screen
}

void screen_test_slider_destroy(void) {
  if (screen != NULL) {
    lv_obj_del(screen);
    screen = NULL;
    size_label = NULL;        // Deleted with parent
    value_label = NULL;       // Deleted with parent
    slider_track = NULL;      // Deleted with parent
    slider_indicator = NULL;  // Deleted with parent
    slider_knob = NULL;       // Deleted with parent
    btn_width_dec = NULL;     // Deleted with parent
    btn_width_inc = NULL;     // Deleted with parent
    btn_height_dec = NULL;    // Deleted with parent
    btn_height_inc = NULL;    // Deleted with parent
    next_button = NULL;       // Deleted with parent
    prev_button = NULL;       // Deleted with parent
  }

  // Reset to defaults
  handle_width = 70;
  handle_height = 90;
  slider_value = 50;
}
