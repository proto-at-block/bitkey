#include "screen_test_gesture.h"

#include "log.h"
#include "lvgl/lvgl.h"
#include "printf.h"
#include "screens/screen_test_scroll.h"

#include <stdio.h>

// Fingerprint logging macro that bypasses the log level system
#define LOG_FP(...)      \
  do {                   \
    printf(__VA_ARGS__); \
    printf("\r\n");      \
  } while (0)

#define CROSSHAIR_COLOR               lv_color_hex(0xFF0000)  // red
#define GESTURE_CLEAR_DELAY_MS        500                     // Delay before clearing gesture label
#define SCREEN_TRANSITION_DURATION_MS 300  // Screen transition animation duration

// Static screen objects
static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* gesture_label = NULL;
static lv_obj_t* coord_label = NULL;
static lv_timer_t* clear_timer = NULL;
static lv_obj_t* nav_button = NULL;
static lv_obj_t* crosshair_h = NULL;  // Horizontal crosshair line
static lv_obj_t* crosshair_v = NULL;  // Vertical crosshair line
static lv_obj_t* touch_dot = NULL;    // Dot marking press position
static lv_timer_t* dot_timer = NULL;

// Reference dots for robo touch testing
static lv_obj_t* dot_top = NULL;
static lv_obj_t* dot_bottom = NULL;
static lv_obj_t* dot_left = NULL;
static lv_obj_t* dot_right = NULL;
static lv_obj_t* dot_center = NULL;

// Track if a gesture occurred to suppress click
static bool gesture_occurred = false;

// Track test mode: true = robo touch test
static bool robo_touch_mode = false;

// Store previous log level to restore on exit
static log_level_t saved_log_level = LOG_NONE;

// Processes all screen events
static void screen_event_handler(lv_event_t* e);

// Restores log level when screen is deleted (handles direct lv_scr_load_anim transitions)
static void screen_delete_handler(lv_event_t* e) {
  (void)e;
  if (saved_log_level != LOG_NONE) {
    log_set_level(saved_log_level);
    saved_log_level = LOG_NONE;
  }
}

// Track initial press position to suppress long press during movement
static int32_t press_start_x = 0;
static int32_t press_start_y = 0;

#define MOVEMENT_THRESHOLD 15  // Pixels of movement to consider it a drag/scroll
#define TOUCH_DOT_SIZE     10

// Timer callback to clear the gesture label
static void clear_gesture_timer_cb(lv_timer_t* timer) {
  (void)timer;
  if (gesture_label != NULL) {
    lv_label_set_text(gesture_label, "");
  }
  clear_timer = NULL;
}

// Helper to show gesture and start clear timer
static void show_gesture(const char* gesture_text) {
  // Don't show gestures in robo touch mode
  if (robo_touch_mode) {
    return;
  }

  if (gesture_label != NULL) {
    lv_label_set_text(gesture_label, gesture_text);
  }

  // Delete existing timer if any
  if (clear_timer != NULL) {
    lv_timer_del(clear_timer);
    clear_timer = NULL;
  }

  // Create new timer to clear after delay
  clear_timer = lv_timer_create(clear_gesture_timer_cb, GESTURE_CLEAR_DELAY_MS, NULL);
  lv_timer_set_repeat_count(clear_timer, 1);  // One-shot timer
}

static void hide_touch_dot(void) {
  if (touch_dot != NULL) {
    lv_obj_add_flag(touch_dot, LV_OBJ_FLAG_HIDDEN);
  }

  if (dot_timer != NULL) {
    lv_timer_del(dot_timer);
    dot_timer = NULL;
  }
}

static void touch_dot_timer_cb(lv_timer_t* timer) {
  (void)timer;
  hide_touch_dot();
}

static void show_touch_dot(int32_t x, int32_t y) {
  if (touch_dot == NULL) {
    return;
  }

  hide_touch_dot();

  // You have to pass the upper left corner of the dot position
  lv_obj_set_pos(touch_dot, x - (TOUCH_DOT_SIZE / 2), y - (TOUCH_DOT_SIZE / 2));
  lv_obj_clear_flag(touch_dot, LV_OBJ_FLAG_HIDDEN);

  dot_timer = lv_timer_create(touch_dot_timer_cb, GESTURE_CLEAR_DELAY_MS, NULL);
  lv_timer_set_repeat_count(dot_timer, 1);
}

// Helper to update crosshairs positions
static void update_crosshairs(int32_t x, int32_t y) {
  if ((crosshair_h != NULL) && (crosshair_v != NULL)) {
    // Position horizontal line
    lv_obj_set_pos(crosshair_h, 0, y);

    // Position vertical line
    lv_obj_set_pos(crosshair_v, x, 0);

    // Show crosshairs
    lv_obj_clear_flag(crosshair_h, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(crosshair_v, LV_OBJ_FLAG_HIDDEN);

    // Only show coordinates in manual mode, not in robo touch mode
    if (!robo_touch_mode && (coord_label != NULL)) {
      char coord_text[24];
      snprintf(coord_text, sizeof(coord_text), "x:%ld y:%ld", x, y);
      lv_label_set_text(coord_label, coord_text);
      lv_obj_clear_flag(coord_label, LV_OBJ_FLAG_HIDDEN);
    }
  }
}

// Helper to hide crosshairs
static void hide_crosshairs(void) {
  if (crosshair_h != NULL) {
    lv_obj_add_flag(crosshair_h, LV_OBJ_FLAG_HIDDEN);
  }
  if (crosshair_v != NULL) {
    lv_obj_add_flag(crosshair_v, LV_OBJ_FLAG_HIDDEN);
  }
  if (coord_label != NULL) {
    lv_obj_add_flag(coord_label, LV_OBJ_FLAG_HIDDEN);
  }
}

// Helper to create a single-pixel reference dot for robo touch testing
static lv_obj_t* create_reference_dot(lv_obj_t* parent, int32_t x, int32_t y) {
  lv_obj_t* dot = lv_obj_create(parent);
  if (!dot) {
    return NULL;
  }
  lv_obj_set_size(dot, 1, 1);
  lv_obj_set_pos(dot, x, y);
  lv_obj_set_style_bg_color(dot, lv_color_hex(0x00FF00), 0);
  lv_obj_set_style_bg_opa(dot, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(dot, 0, 0);
  lv_obj_add_flag(dot, LV_OBJ_FLAG_HIDDEN);
  lv_obj_add_flag(dot, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(dot, LV_OBJ_FLAG_CLICKABLE);
  return dot;
}

// Register gesture/tap handlers on any object that should forward events
static void attach_gesture_handlers(lv_obj_t* obj) {
  if (obj == NULL) {
    return;
  }
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_RELEASED, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_SHORT_CLICKED, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_DOUBLE_CLICKED, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_TRIPLE_CLICKED, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_LONG_PRESSED, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_LONG_PRESSED_REPEAT, NULL);
  lv_obj_add_event_cb(obj, screen_event_handler, LV_EVENT_GESTURE, NULL);
}

// Navigation button click handler
static void nav_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    // Enter robo touch mode instead of advancing to next screen
    robo_touch_mode = true;

    // Change background to dark gray
    if (screen != NULL) {
      lv_obj_set_style_bg_color(screen, lv_color_hex(0x404040), 0);
    }

    // Hide the navigation button
    if (nav_button != NULL) {
      lv_obj_add_flag(nav_button, LV_OBJ_FLAG_HIDDEN);
    }

    // Update title text, font, and position for robo touch instructions
    if (title_label != NULL) {
      lv_label_set_text(title_label, "  robo touch test\ndouble tap to advance");
      lv_obj_set_style_text_font(title_label, &cash_sans_mono_regular_20, 0);
      lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, 35);
    }

    // Clear and hide gesture label (no more gesture text)
    if (gesture_label != NULL) {
      lv_label_set_text(gesture_label, "");
      lv_obj_add_flag(gesture_label, LV_OBJ_FLAG_HIDDEN);
    }

    // Clear any pending gesture clear timer
    if (clear_timer != NULL) {
      lv_timer_del(clear_timer);
      clear_timer = NULL;
    }

    // Ensure coord_label stays hidden
    if (coord_label != NULL) {
      lv_obj_add_flag(coord_label, LV_OBJ_FLAG_HIDDEN);
    }

    // Hide the white touch dot permanently
    hide_touch_dot();

    // Show the reference dots for robo touch testing
    if (dot_top != NULL) {
      lv_obj_clear_flag(dot_top, LV_OBJ_FLAG_HIDDEN);
    }
    if (dot_bottom != NULL) {
      lv_obj_clear_flag(dot_bottom, LV_OBJ_FLAG_HIDDEN);
    }
    if (dot_left != NULL) {
      lv_obj_clear_flag(dot_left, LV_OBJ_FLAG_HIDDEN);
    }
    if (dot_right != NULL) {
      lv_obj_clear_flag(dot_right, LV_OBJ_FLAG_HIDDEN);
    }
    if (dot_center != NULL) {
      lv_obj_clear_flag(dot_center, LV_OBJ_FLAG_HIDDEN);
    }
  }
}

// Event handler for detecting taps, swipes, and long press
static void screen_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  lv_obj_t* target = lv_event_get_target(e);

  // Ignore bubbled events coming from child objects (e.g. navigation button)
  if (target != screen) {
    return;
  }

  lv_indev_t* indev = lv_indev_get_act();
  lv_point_t point;
  lv_indev_get_point(indev, &point);

  switch (code) {
    case LV_EVENT_PRESSED: {
      // Reset gesture flag at start of new interaction
      gesture_occurred = false;

      // Capture initial press position for movement detection
      press_start_x = point.x;
      press_start_y = point.y;

      // Show crosshairs at press location
      update_crosshairs(point.x, point.y);

      // Only show touch dot in manual mode, not in robo touch mode
      if (!robo_touch_mode) {
        show_touch_dot(point.x, point.y);
      }
      break;
    }

    case LV_EVENT_PRESSING: {
      // Update crosshairs as finger moves
      update_crosshairs(point.x, point.y);
      break;
    }

    case LV_EVENT_RELEASED: {
      // Hide crosshairs when touch is released
      hide_crosshairs();

      // Draw touch dot on release (only in manual mode)
      if (!robo_touch_mode) {
        show_touch_dot(point.x, point.y);
      }
      break;
    }

    case LV_EVENT_LONG_PRESSED:
    case LV_EVENT_LONG_PRESSED_REPEAT: {
      // Check if user has moved significantly - if so, suppress long press
      int32_t dx = point.x - press_start_x;
      int32_t dy = point.y - press_start_y;
      int32_t distance = (dx * dx) + (dy * dy);  // Squared distance (avoid sqrt)
      int32_t threshold_sq = MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD;

      // Only show long press if movement is below threshold
      if (distance < threshold_sq) {
        const char* text = (code == LV_EVENT_LONG_PRESSED) ? "long press" : "long press repeat";
        show_gesture(text);
      }
      break;
    }

    case LV_EVENT_GESTURE: {
      // Mark that a gesture occurred
      gesture_occurred = true;

      update_crosshairs(point.x, point.y);

      // LVGL detected a gesture, determine which direction
      lv_dir_t dir = lv_indev_get_gesture_dir(indev);
      switch (dir) {
        case LV_DIR_LEFT:
          show_gesture("swipe left");
          break;
        case LV_DIR_RIGHT:
          show_gesture("swipe right");
          break;
        case LV_DIR_TOP:
          show_gesture("swipe up");
          break;
        case LV_DIR_BOTTOM:
          show_gesture("swipe down");
          break;
        default:
          show_gesture("swipe");
          break;
      }
      break;
    }

    case LV_EVENT_SHORT_CLICKED:
      // Only show tap if no gesture occurred
      if (!gesture_occurred) {
        show_gesture("tap");
      }
      // Reset flag for next interaction
      gesture_occurred = false;
      break;

    case LV_EVENT_DOUBLE_CLICKED:
      // In robo touch mode, double tap advances to next screen
      if (robo_touch_mode) {
        // Clean up timers before screen transition
        hide_touch_dot();
        if (clear_timer != NULL) {
          lv_timer_del(clear_timer);
          clear_timer = NULL;
        }

        // Create and load test_scroll screen
        lv_obj_t* test_scroll_screen = screen_test_scroll_init(NULL);
        if (test_scroll_screen != NULL) {
          // Load test_scroll screen with a slide left animation
          lv_scr_load_anim(test_scroll_screen, LV_SCR_LOAD_ANIM_MOVE_LEFT,
                           SCREEN_TRANSITION_DURATION_MS, 0, true);
        }
      } else {
        // In manual mode, just show the gesture text
        show_gesture("double tap");
      }
      break;

    case LV_EVENT_TRIPLE_CLICKED:
      show_gesture("triple tap");
      break;

    default:
      break;
  }

  // Stop processing so gesture_tx doesn't forward to Core
  lv_event_stop_processing(e);

  // Log coordinates and event code to UART (if enabled)
  if (gesture_occurred) {
    LOG_FP("X=%ld, Y=%ld, e=%d g=%d ts=%lu", (long)point.x, (long)point.y, (int)code,
           lv_indev_get_gesture_dir(indev), (unsigned long)lv_tick_get());
  } else {
    LOG_FP("X=%ld, Y=%ld, e=%d g=- ts=%lu", (long)point.x, (long)point.y, (int)code,
           (unsigned long)lv_tick_get());
  }
}

lv_obj_t* screen_test_gesture_init(void* ctx) {
  (void)ctx;  // Unused parameter

  // Reset robo touch mode
  robo_touch_mode = false;

  // Save current log level and disable normal logging to prevent interference
  saved_log_level = log_get_level();
  log_set_level(LOG_NONE);

  // Create the screen with black background
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Remove padding so dots can be positioned at absolute edges
  lv_obj_set_style_pad_all(screen, 0, 0);

  // Create gesture label near the top
  gesture_label = lv_label_create(screen);
  if (!gesture_label) {
    return NULL;
  }
  lv_label_set_text(gesture_label, "");  // Initially empty
  lv_obj_set_style_text_color(gesture_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(gesture_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(gesture_label, LV_ALIGN_TOP_MID, 0, 20);

  coord_label = lv_label_create(screen);
  if (!coord_label) {
    return NULL;
  }
  lv_label_set_text(coord_label, "");
  lv_obj_set_style_text_color(coord_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(coord_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(coord_label, LV_ALIGN_TOP_MID, 0, 48);
  lv_obj_add_flag(coord_label, LV_OBJ_FLAG_HIDDEN);

  // Create "Test Gestures" label in the center
  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_label_set_text(title_label, "Test Gestures");
  lv_obj_set_style_text_color(title_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(title_label, &cash_sans_mono_regular_28, 0);
  lv_obj_align(title_label, LV_ALIGN_CENTER, 0, 0);

  // Create crosshair lines (initially hidden)
  // Horizontal line - spans full screen width
  crosshair_h = lv_obj_create(screen);
  if (!crosshair_h) {
    return NULL;
  }
  lv_obj_set_size(crosshair_h, lv_obj_get_width(screen), 1);   // Full screen width, 1px height
  lv_obj_set_style_bg_color(crosshair_h, CROSSHAIR_COLOR, 0);  // Red color
  lv_obj_set_style_bg_opa(crosshair_h, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(crosshair_h, 0, 0);        // No border
  lv_obj_add_flag(crosshair_h, LV_OBJ_FLAG_HIDDEN);        // Start hidden
  lv_obj_add_flag(crosshair_h, LV_OBJ_FLAG_EVENT_BUBBLE);  // Don't swallow touch events
  lv_obj_clear_flag(crosshair_h, LV_OBJ_FLAG_CLICKABLE);   // Let touches fall through

  // Vertical line - spans full screen height
  crosshair_v = lv_obj_create(screen);
  if (!crosshair_v) {
    return NULL;
  }
  lv_obj_set_size(crosshair_v, 1, lv_obj_get_height(screen));  // 1px width, full screen height
  lv_obj_set_style_bg_color(crosshair_v, CROSSHAIR_COLOR, 0);  // Red color
  lv_obj_set_style_bg_opa(crosshair_v, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(crosshair_v, 0, 0);        // No border
  lv_obj_add_flag(crosshair_v, LV_OBJ_FLAG_HIDDEN);        // Start hidden
  lv_obj_add_flag(crosshair_v, LV_OBJ_FLAG_EVENT_BUBBLE);  // Bubble touches to the screen
  lv_obj_clear_flag(crosshair_v, LV_OBJ_FLAG_CLICKABLE);   // Let touches fall through

  // Small dot for showing press position
  touch_dot = lv_obj_create(screen);
  if (!touch_dot) {
    return NULL;
  }
  lv_obj_set_size(touch_dot, TOUCH_DOT_SIZE, TOUCH_DOT_SIZE);
  lv_obj_set_style_bg_color(touch_dot, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(touch_dot, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(touch_dot, 0, 0);
  lv_obj_set_style_radius(touch_dot, LV_RADIUS_CIRCLE, 0);
  lv_obj_add_flag(touch_dot, LV_OBJ_FLAG_HIDDEN);
  lv_obj_add_flag(touch_dot, LV_OBJ_FLAG_EVENT_BUBBLE);
  lv_obj_clear_flag(touch_dot, LV_OBJ_FLAG_CLICKABLE);

  // Create 5 single-pixel reference dots for robo touch testing
  int32_t screen_w = lv_obj_get_width(screen);
  int32_t screen_h = lv_obj_get_height(screen);

  dot_top = create_reference_dot(screen, screen_w / 2, 2);
  dot_bottom = create_reference_dot(screen, screen_w / 2, screen_h - 3);
  dot_left = create_reference_dot(screen, 0, screen_h / 2);
  dot_right = create_reference_dot(screen, screen_w - 9, screen_h / 2);
  dot_center = create_reference_dot(screen, screen_w / 2, screen_h / 2);

  // Create navigation button at the bottom middle
  nav_button = lv_button_create(screen);
  if (!nav_button) {
    return NULL;
  }
  lv_obj_set_size(nav_button, 130, 65);
  lv_obj_align(nav_button, LV_ALIGN_BOTTOM_MID, 0, -20);

  // Create "next" label for navigation button
  lv_obj_t* nav_label = lv_label_create(nav_button);
  if (!nav_label) {
    return NULL;
  }
  lv_label_set_text(nav_label, "next");
  lv_obj_center(nav_label);

  // Add click event handler to navigation button
  lv_obj_add_event_cb(nav_button, nav_button_event_handler, LV_EVENT_SHORT_CLICKED, NULL);

  // Register delete handler to restore log level when screen is auto-deleted
  lv_obj_add_event_cb(screen, screen_delete_handler, LV_EVENT_DELETE, NULL);

  attach_gesture_handlers(screen);

  return screen;
}

void screen_test_gesture_update(void* ctx) {
  (void)ctx;  // Unused parameter
  // Nothing to update for this simple screen
}

void screen_test_gesture_destroy(void) {
  // Restore previous log level
  if (saved_log_level != LOG_NONE) {
    log_set_level(saved_log_level);
    saved_log_level = LOG_NONE;
  }

  // Clean up timer if it exists
  if (clear_timer != NULL) {
    lv_timer_del(clear_timer);
    clear_timer = NULL;
  }

  if (screen != NULL) {
    hide_touch_dot();
    lv_obj_del(screen);
    screen = NULL;
    title_label = NULL;    // Deleted with parent
    gesture_label = NULL;  // Deleted with parent
    coord_label = NULL;    // Deleted with parent
    nav_button = NULL;     // Deleted with parent
    crosshair_h = NULL;    // Deleted with parent
    crosshair_v = NULL;    // Deleted with parent
    touch_dot = NULL;      // Deleted with parent
    dot_top = NULL;        // Deleted with parent
    dot_bottom = NULL;     // Deleted with parent
    dot_left = NULL;       // Deleted with parent
    dot_right = NULL;      // Deleted with parent
    dot_center = NULL;     // Deleted with parent
  }

  // Reset flags
  gesture_occurred = false;
  robo_touch_mode = false;
}
