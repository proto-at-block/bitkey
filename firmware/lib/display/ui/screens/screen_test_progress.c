#include "screen_test_progress.h"

#include "lvgl/lvgl.h"
#include "screens/screen_test_gesture.h"
#include "screens/screen_test_slider.h"

#define PROGRESS_BG_COLOR             lv_color_hex(0x333333)  // dark gray bar background
#define PROGRESS_FILL_COLOR           lv_color_hex(0x00FF00)  // green fill
#define PROGRESS_INCREMENT            2    // Progress increment per tick while holding
#define PROGRESS_MAX                  100  // Maximum progress value
#define SCREEN_TRANSITION_DURATION_MS 300  // Screen transition animation duration

// Static screen objects
static lv_obj_t* screen = NULL;
static lv_obj_t* progress_bar = NULL;
static lv_obj_t* hold_button = NULL;
static lv_obj_t* next_button = NULL;
static lv_obj_t* prev_button = NULL;

// Progress state
static int32_t progress_value = 0;

// Update progress bar display
static void update_progress_bar(void) {
  if (progress_bar != NULL) {
    lv_bar_set_value(progress_bar, progress_value, LV_ANIM_OFF);
  }
}

// Hold button event handler
static void hold_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSED) {
    // Button just pressed - reset progress
    progress_value = 0;
    update_progress_bar();
  } else if (code == LV_EVENT_PRESSING) {
    // Button is being held - increment progress
    progress_value += PROGRESS_INCREMENT;
    if (progress_value > PROGRESS_MAX) {
      progress_value = PROGRESS_MAX;
    }
    update_progress_bar();
  } else if (code == LV_EVENT_RELEASED) {
    // Button released - reset progress
    progress_value = 0;
    update_progress_bar();
  }
}

// Next button event handler - navigate to next screen
static void next_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* test_gesture_screen = screen_test_gesture_init(NULL);
    if (test_gesture_screen != NULL) {
      lv_scr_load_anim(test_gesture_screen, LV_SCR_LOAD_ANIM_MOVE_LEFT,
                       SCREEN_TRANSITION_DURATION_MS, 0, true);
    }
  }
}

// Prev button event handler - navigate to previous screen
static void prev_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* test_slider_screen = screen_test_slider_init(NULL);
    if (test_slider_screen != NULL) {
      lv_scr_load_anim(test_slider_screen, LV_SCR_LOAD_ANIM_MOVE_RIGHT,
                       SCREEN_TRANSITION_DURATION_MS, 0, true);
    }
  }
}

lv_obj_t* screen_test_progress_init(void* ctx) {
  (void)ctx;  // Unused parameter

  // Create the screen with black background
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create progress bar
  progress_bar = lv_bar_create(screen);
  if (!progress_bar) {
    return NULL;
  }
  lv_obj_set_size(progress_bar, 400, 30);
  lv_obj_align(progress_bar, LV_ALIGN_TOP_MID, 0, 90);
  lv_bar_set_range(progress_bar, 0, PROGRESS_MAX);
  lv_bar_set_value(progress_bar, 0, LV_ANIM_OFF);

  // Style the progress bar
  lv_obj_set_style_bg_color(progress_bar, PROGRESS_BG_COLOR, LV_PART_MAIN);
  lv_obj_set_style_bg_color(progress_bar, PROGRESS_FILL_COLOR, LV_PART_INDICATOR);

  // Create hold button in the center
  hold_button = lv_button_create(screen);
  if (!hold_button) {
    return NULL;
  }
  lv_obj_set_size(hold_button, 200, 120);
  lv_obj_align(hold_button, LV_ALIGN_CENTER, 0, 0);

  lv_obj_t* hold_label = lv_label_create(hold_button);
  if (!hold_label) {
    return NULL;
  }
  lv_label_set_text(hold_label, "HOLD ME");
  lv_obj_center(hold_label);

  // Add event callbacks for press, pressing, and release
  lv_obj_add_event_cb(hold_button, hold_button_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(hold_button, hold_button_handler, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(hold_button, hold_button_handler, LV_EVENT_RELEASED, NULL);

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

void screen_test_progress_update(void* ctx) {
  (void)ctx;  // Unused parameter
  // Nothing to update for this screen
}

void screen_test_progress_destroy(void) {
  if (screen != NULL) {
    lv_obj_del(screen);
    screen = NULL;
    progress_bar = NULL;  // Deleted with parent
    hold_button = NULL;   // Deleted with parent
    next_button = NULL;   // Deleted with parent
    prev_button = NULL;   // Deleted with parent
  }

  // Reset to defaults
  progress_value = 0;
}
