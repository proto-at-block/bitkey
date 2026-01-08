#include "screen_test_pin_pad.h"

#include "lvgl/lvgl.h"
#include "screens/screen_test_carousel.h"
#include "screens/screen_test_scroll.h"

#include <stdio.h>
#include <string.h>

#define PIN_BUTTON_SPACING            15
#define PIN_BUTTON_COUNT              10
#define MAX_PIN_DIGITS                4    // Maximum PIN digits
#define SCREEN_TRANSITION_DURATION_MS 300  // Screen transition animation duration
// Static screen objects
static lv_obj_t* screen = NULL;
static lv_obj_t* display_label = NULL;
static lv_obj_t* size_label = NULL;
static lv_obj_t* number_buttons[PIN_BUTTON_COUNT] = {NULL};  // 0-9
static lv_obj_t* clear_button = NULL;
static lv_obj_t* enter_button = NULL;

// Pin button sizing controls
static int16_t pin_button_width = 94;
static int16_t pin_button_height = 70;
#define PIN_BUTTON_MIN  40
#define PIN_BUTTON_MAX  200
#define PIN_BUTTON_STEP 5

// PIN entry state
static char pin_buffer[5] = {0};  // Max 4 digits + null terminator
static uint8_t pin_length = 0;

typedef enum {
  PIN_SIZE_INC_W = 0,
  PIN_SIZE_DEC_W,
  PIN_SIZE_INC_H,
  PIN_SIZE_DEC_H,
} pin_size_action_t;

static void apply_button_sizes(void);
static void create_size_control_button(const char* text, lv_align_t align, lv_coord_t x_ofs,
                                       lv_coord_t y_ofs, pin_size_action_t action);
static void update_size_label(void);

// Update display label with current PIN
static void update_display(void) {
  if (display_label != NULL) {
    if (pin_length == 0) {
      lv_label_set_text(display_label, "");
    } else {
      lv_label_set_text(display_label, pin_buffer);
    }
  }
}

static void adjust_button_size(pin_size_action_t action) {
  int16_t new_width = pin_button_width;
  int16_t new_height = pin_button_height;

  switch (action) {
    case PIN_SIZE_INC_W:
      new_width += PIN_BUTTON_STEP;
      break;
    case PIN_SIZE_DEC_W:
      new_width -= PIN_BUTTON_STEP;
      break;
    case PIN_SIZE_INC_H:
      new_height += PIN_BUTTON_STEP;
      break;
    case PIN_SIZE_DEC_H:
      new_height -= PIN_BUTTON_STEP;
      break;
    default:
      break;
  }

  if (new_width < PIN_BUTTON_MIN) {
    new_width = PIN_BUTTON_MIN;
  } else if (new_width > PIN_BUTTON_MAX) {
    new_width = PIN_BUTTON_MAX;
  }

  if (new_height < PIN_BUTTON_MIN) {
    new_height = PIN_BUTTON_MIN;
  } else if (new_height > PIN_BUTTON_MAX) {
    new_height = PIN_BUTTON_MAX;
  }

  if ((new_width != pin_button_width) || (new_height != pin_button_height)) {
    pin_button_width = new_width;
    pin_button_height = new_height;
    apply_button_sizes();
    update_size_label();
  }
}

static void pin_size_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_SHORT_CLICKED) {
    return;
  }

  pin_size_action_t action = (pin_size_action_t)(intptr_t)lv_event_get_user_data(e);
  adjust_button_size(action);
}

static void apply_button_sizes(void) {
  for (int i = 0; i < 10; i++) {
    if (number_buttons[i] != NULL) {
      lv_obj_set_size(number_buttons[i], pin_button_width, pin_button_height);
    }
  }

  if (clear_button != NULL) {
    lv_obj_set_size(clear_button, pin_button_width, pin_button_height);
  }
  if (enter_button != NULL) {
    lv_obj_set_size(enter_button, pin_button_width, pin_button_height);
  }
}

static void create_size_control_button(const char* text, lv_align_t align, lv_coord_t x_ofs,
                                       lv_coord_t y_ofs, pin_size_action_t action) {
  lv_obj_t* btn = lv_button_create(screen);
  lv_obj_set_size(btn, 45, 40);
  lv_obj_align(btn, align, x_ofs, y_ofs);
  lv_obj_t* label = lv_label_create(btn);
  lv_label_set_text(label, text);
  lv_obj_center(label);
  lv_obj_add_event_cb(btn, pin_size_button_handler, LV_EVENT_SHORT_CLICKED,
                      (void*)(intptr_t)action);
}

static void update_size_label(void) {
  if (size_label != NULL) {
    char buf[48];
    snprintf(buf, sizeof(buf), "Width: %d  Height: %d", pin_button_width, pin_button_height);
    lv_label_set_text(size_label, buf);
  }
}

// Number button event handler
static void number_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    lv_obj_t* btn = lv_event_get_target(e);

    // Find which number button was pressed
    for (int i = 0; i < 10; i++) {
      if (btn == number_buttons[i]) {
        // If we've already shown maximum digits, clear and start fresh
        if (pin_length >= MAX_PIN_DIGITS) {
          memset(pin_buffer, 0, sizeof(pin_buffer));
          pin_length = 0;
          update_display();
        }

        pin_buffer[pin_length] = '0' + i;
        pin_length++;
        pin_buffer[pin_length] = '\0';
        update_display();
        break;
      }
    }
  }
}

// Clear button event handler
static void clear_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    // Clear PIN buffer
    memset(pin_buffer, 0, sizeof(pin_buffer));
    pin_length = 0;
    update_display();
  }
}

// Enter button event handler
static void enter_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_SHORT_CLICKED) {
    // Clear PIN buffer after enter
    memset(pin_buffer, 0, sizeof(pin_buffer));
    pin_length = 0;
    update_display();
  }
}

// Gesture event handler for screen navigation
static void screen_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_GESTURE) {
    // Detect swipe direction
    lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_get_act());

    if (dir == LV_DIR_LEFT) {
      // Swipe left goes to test_carousel screen
      lv_obj_t* test_carousel_screen = screen_test_carousel_init(NULL);
      if (test_carousel_screen != NULL) {
        // auto_del=true tells LVGL to automatically delete the old screen
        lv_scr_load_anim(test_carousel_screen, LV_SCR_LOAD_ANIM_MOVE_LEFT,
                         SCREEN_TRANSITION_DURATION_MS, 0, true);
      }
    } else if (dir == LV_DIR_RIGHT) {
      // Swipe right goes to test_scroll screen
      lv_obj_t* test_scroll_screen = screen_test_scroll_init(NULL);
      if (test_scroll_screen != NULL) {
        // auto_del=true tells LVGL to automatically delete the old screen
        lv_scr_load_anim(test_scroll_screen, LV_SCR_LOAD_ANIM_MOVE_RIGHT,
                         SCREEN_TRANSITION_DURATION_MS, 0, true);
      }
    }
  }
}

lv_obj_t* screen_test_pin_pad_init(void* ctx) {
  (void)ctx;  // Unused parameter

  // Reset PIN state
  memset(pin_buffer, 0, sizeof(pin_buffer));
  pin_length = 0;

  // Create the screen with black background
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Create display label at the top
  display_label = lv_label_create(screen);
  lv_obj_set_style_text_color(display_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(display_label, &cash_sans_mono_regular_28, 0);
  lv_obj_align(display_label, LV_ALIGN_TOP_MID, 0, 20);
  lv_label_set_text(display_label, "");

  size_label = lv_label_create(screen);
  lv_obj_set_style_text_color(size_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(size_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(size_label, LV_ALIGN_TOP_MID, 0, 60);
  update_size_label();

  /* Create number pad buttons in 3x4 grid
   * Standard phone layout:
   *  1--2--3
   *  4--5--6
   *  7--8--9
   *  Clear--0--Enter
   */
  pin_button_width = 94;
  pin_button_height = 70;
  const int btn_width = pin_button_width;
  const int btn_height = pin_button_height;
  const int spacing = PIN_BUTTON_SPACING;
  const int start_x = -100;  // Centered offset
  const int start_y = -110;

  // Create buttons 1-9
  for (int i = 1; i <= 9; i++) {
    int row = (i - 1) / 3;
    int col = (i - 1) % 3;

    number_buttons[i] = lv_button_create(screen);
    lv_obj_set_size(number_buttons[i], btn_width, btn_height);
    lv_obj_align(number_buttons[i], LV_ALIGN_CENTER, start_x + col * (btn_width + spacing),
                 start_y + row * (btn_height + spacing));

    lv_obj_t* label = lv_label_create(number_buttons[i]);
    char buf[2];
    snprintf(buf, sizeof(buf), "%d", i);
    lv_label_set_text(label, buf);
    lv_obj_center(label);

    lv_obj_add_event_cb(number_buttons[i], number_button_handler, LV_EVENT_SHORT_CLICKED, NULL);
  }

  // Row 4: Clear, 0, Enter
  int row4_y = start_y + 3 * (btn_height + spacing);

  // Clear button
  clear_button = lv_button_create(screen);
  lv_obj_set_size(clear_button, btn_width, btn_height);
  lv_obj_align(clear_button, LV_ALIGN_CENTER, start_x, row4_y);
  lv_obj_t* clear_label = lv_label_create(clear_button);
  lv_label_set_text(clear_label, "CLR");
  lv_obj_center(clear_label);
  lv_obj_add_event_cb(clear_button, clear_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  // 0 button
  number_buttons[0] = lv_button_create(screen);
  lv_obj_set_size(number_buttons[0], btn_width, btn_height);
  lv_obj_align(number_buttons[0], LV_ALIGN_CENTER, start_x + (btn_width + spacing), row4_y);
  lv_obj_t* zero_label = lv_label_create(number_buttons[0]);
  lv_label_set_text(zero_label, "0");
  lv_obj_center(zero_label);
  lv_obj_add_event_cb(number_buttons[0], number_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  // Enter button
  enter_button = lv_button_create(screen);
  lv_obj_set_size(enter_button, btn_width, btn_height);
  lv_obj_align(enter_button, LV_ALIGN_CENTER, start_x + 2 * (btn_width + spacing), row4_y);
  lv_obj_t* enter_label = lv_label_create(enter_button);
  lv_label_set_text(enter_label, "ENTER");
  lv_obj_center(enter_label);
  lv_obj_add_event_cb(enter_button, enter_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  apply_button_sizes();

  // Create controls for adjusting button size (match layout similar to test_scroll)
  create_size_control_button("+W", LV_ALIGN_LEFT_MID, 10, -30, PIN_SIZE_INC_W);
  create_size_control_button("-W", LV_ALIGN_LEFT_MID, 10, 20, PIN_SIZE_DEC_W);
  create_size_control_button("+H", LV_ALIGN_RIGHT_MID, -10, -30, PIN_SIZE_INC_H);
  create_size_control_button("-H", LV_ALIGN_RIGHT_MID, -10, 20, PIN_SIZE_DEC_H);

  // Add gesture event handler for swipe navigation
  lv_obj_add_event_cb(screen, screen_event_handler, LV_EVENT_GESTURE, NULL);

  return screen;
}

void screen_test_pin_pad_update(void* ctx) {
  (void)ctx;  // Unused parameter
  // Nothing to update for this screen
}

void screen_test_pin_pad_destroy(void) {
  if (screen != NULL) {
    lv_obj_del(screen);
    screen = NULL;
    display_label = NULL;
    size_label = NULL;

    for (int i = 0; i < PIN_BUTTON_COUNT; i++) {
      number_buttons[i] = NULL;
    }

    clear_button = NULL;
    enter_button = NULL;
  }

  // Clear PIN state
  memset(pin_buffer, 0, sizeof(pin_buffer));
  pin_length = 0;
}
