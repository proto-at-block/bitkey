#include "screen_test_carousel.h"

#include "lvgl/lvgl.h"
#include "screens/screen_test_gesture.h"
#include "screens/screen_test_pin_pad.h"
#include "screens/screen_test_slider.h"

#define CAROUSEL_BG_COLOR         lv_color_hex(0x111111)  // very dark background
#define CAROUSEL_BORDER_COLOR     lv_color_hex(0x444444)  // medium gray border
#define CAROUSEL_ANIM_DURATION_MS 300                     // Screen transition animation duration

// Static screen objects
static lv_obj_t* screen = NULL;
static lv_obj_t* text_container = NULL;
static lv_obj_t* text_label = NULL;
static lv_obj_t* next_button = NULL;
static lv_obj_t* prev_button = NULL;

// Carousel state
static const char* carousel_texts[] = {"TEXT 1\nABCDEFHI\n9876", "TEXT 2\nGHIJKLM\n6543",
                                       "TEXT 3\nLMNOPQRS\n2101"};
// Number of test fields in the carousel
#define CAROUSEL_TEXT_COUNT (sizeof(carousel_texts) / sizeof(carousel_texts[0]))

static uint32_t current_text_index = 0;
static bool animation_in_progress = false;

// Animation complete callback - deletes the old label
static void anim_delete_cb(lv_anim_t* anim) {
  lv_obj_t* obj = (lv_obj_t*)anim->var;
  if (obj != NULL) {
    lv_obj_del(obj);
  }
}

// Animation complete callback
static void anim_ready_cb(lv_anim_t* anim) {
  (void)anim;
  animation_in_progress = false;
}

// Update text display with animation
static void update_text_display_animated(bool swipe_left) {
  if ((text_label == NULL) || animation_in_progress) {
    return;
  }

  animation_in_progress = true;

  // Get container width for animation distance
  lv_coord_t container_width = lv_obj_get_width(text_container);

  // Create old label that will slide out
  lv_obj_t* old_label = text_label;

  // Create new label that will slide in
  text_label = lv_label_create(text_container);
  if (!text_label) {
    return;
  }
  lv_label_set_text(text_label, carousel_texts[current_text_index]);
  lv_obj_set_style_text_color(text_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(text_label, &cash_sans_mono_regular_34, 0);
  lv_obj_clear_flag(text_label, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_flag(text_label, LV_OBJ_FLAG_EVENT_BUBBLE);

  // Force LVGL to calculate the label dimensions
  lv_obj_update_layout(text_label);

  // Calculate center position manually
  lv_coord_t container_width_coord = lv_obj_get_width(text_container);
  lv_coord_t container_height = lv_obj_get_height(text_container);
  lv_coord_t label_width = lv_obj_get_width(text_label);
  lv_coord_t label_height = lv_obj_get_height(text_label);

  lv_coord_t center_x = (container_width_coord - label_width) / 2;
  lv_coord_t center_y = (container_height - label_height) / 2;

  // Position new label off-screen based on swipe direction
  lv_coord_t start_x;
  if (swipe_left) {
    // Swiping left (advancing): new text comes from right
    start_x = container_width;
  } else {
    // Swiping right (going back): new text comes from left
    start_x = -label_width;
  }
  lv_obj_set_pos(text_label, start_x, center_y);

  // Animate old label sliding out
  lv_anim_t anim_out;
  lv_anim_init(&anim_out);
  lv_anim_set_var(&anim_out, old_label);
  lv_anim_set_exec_cb(&anim_out, (lv_anim_exec_xcb_t)lv_obj_set_x);
  lv_anim_set_time(&anim_out, CAROUSEL_ANIM_DURATION_MS);

  lv_coord_t old_x = lv_obj_get_x_aligned(old_label);
  if (swipe_left) {
    // Slide old text to the left
    lv_anim_set_values(&anim_out, old_x, old_x - container_width);
  } else {
    // Slide old text to the right
    lv_anim_set_values(&anim_out, old_x, old_x + container_width);
  }
  lv_anim_set_path_cb(&anim_out, lv_anim_path_ease_in_out);
  lv_anim_set_ready_cb(&anim_out, anim_delete_cb);
  lv_anim_start(&anim_out);

  // Animate new label sliding in
  lv_anim_t anim_in;
  lv_anim_init(&anim_in);
  lv_anim_set_var(&anim_in, text_label);
  lv_anim_set_exec_cb(&anim_in, (lv_anim_exec_xcb_t)lv_obj_set_x);
  lv_anim_set_time(&anim_in, CAROUSEL_ANIM_DURATION_MS);
  lv_anim_set_values(&anim_in, start_x, center_x);
  lv_anim_set_path_cb(&anim_in, lv_anim_path_ease_in_out);
  lv_anim_set_ready_cb(&anim_in, anim_ready_cb);
  lv_anim_start(&anim_in);
}

// Screen gesture event handler for text carousel
static void text_container_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_GESTURE) {
    // Detect swipe direction
    lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_get_act());

    if (dir == LV_DIR_LEFT) {
      // Swipe left - go to next text
      if (current_text_index < (CAROUSEL_TEXT_COUNT - 1)) {
        current_text_index++;
        update_text_display_animated(true);  // true = swipe left
      }
    } else if (dir == LV_DIR_RIGHT) {
      // Swipe right - go to previous text
      if (current_text_index > 0) {
        current_text_index--;
        update_text_display_animated(false);  // false = swipe right
      }
    }
  }
}

// Next button event handler - navigate to next screen
static void next_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* test_slider_screen = screen_test_slider_init(NULL);
    if (test_slider_screen != NULL) {
      // auto_del=true tells LVGL to automatically delete the old screen
      lv_scr_load_anim(test_slider_screen, LV_SCR_LOAD_ANIM_MOVE_LEFT, CAROUSEL_ANIM_DURATION_MS, 0,
                       true);
    }
  }
}

// Prev button event handler - navigate to previous screen
static void prev_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* test_pin_pad_screen = screen_test_pin_pad_init(NULL);
    if (test_pin_pad_screen != NULL) {
      // auto_del=true tells LVGL to automatically delete the old screen
      lv_scr_load_anim(test_pin_pad_screen, LV_SCR_LOAD_ANIM_MOVE_RIGHT, CAROUSEL_ANIM_DURATION_MS,
                       0, true);
    }
  }
}

lv_obj_t* screen_test_carousel_init(void* ctx) {
  (void)ctx;  // Unused parameter

  // Reset carousel state
  current_text_index = 0;

  // Create the screen with black background
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create text container for top half (swipeable area)
  text_container = lv_obj_create(screen);
  if (!text_container) {
    return NULL;
  }
  lv_obj_set_size(text_container, lv_pct(100), lv_pct(50));
  lv_obj_align(text_container, LV_ALIGN_TOP_MID, 0, 0);
  lv_obj_set_style_bg_color(text_container, CAROUSEL_BG_COLOR, 0);
  lv_obj_set_style_border_width(text_container, 2, 0);
  lv_obj_set_style_border_color(text_container, CAROUSEL_BORDER_COLOR, 0);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE);  // Disable scrolling
  lv_obj_add_flag(text_container, LV_OBJ_FLAG_CLICKABLE);     // Enable gesture events

  // Create text label inside container
  text_label = lv_label_create(text_container);
  if (!text_label) {
    return NULL;
  }
  lv_label_set_text(text_label, carousel_texts[current_text_index]);
  lv_obj_set_style_text_color(text_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(text_label, &cash_sans_mono_regular_34, 0);
  lv_obj_center(text_label);

  // Make sure the label doesn't block events - let them propagate to parent
  lv_obj_clear_flag(text_label, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_flag(text_label, LV_OBJ_FLAG_EVENT_BUBBLE);

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

  // Add gesture event handler to the SCREEN (not the container)
  // This is the pattern used by other test screens
  lv_obj_add_event_cb(screen, text_container_event_handler, LV_EVENT_GESTURE, NULL);

  return screen;
}

void screen_test_carousel_update(void* ctx) {
  (void)ctx;  // Unused parameter
  // Nothing to update for this screen
}

void screen_test_carousel_destroy(void) {
  if (screen != NULL) {
    // Stop any running animations
    if (text_label != NULL) {
      lv_anim_del(text_label, NULL);
    }

    lv_obj_del(screen);
    screen = NULL;
    text_container = NULL;  // Deleted with parent
    text_label = NULL;      // Deleted with parent
    next_button = NULL;     // Deleted with parent
    prev_button = NULL;     // Deleted with parent
  }

  // Reset state
  current_text_index = 0;
  animation_in_progress = false;
}
