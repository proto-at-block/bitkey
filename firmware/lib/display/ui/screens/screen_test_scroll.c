#include "screen_test_scroll.h"

#include "lvgl/lvgl.h"
#include "screens/screen_test_gesture.h"
#include "screens/screen_test_pin_pad.h"

#include <stdio.h>

#define LIST_BG_COLOR                 lv_color_hex(0x222222)  // dark gray background
#define LIST_BORDER_COLOR             lv_color_hex(0x444444)  // medium gray border
#define SCREEN_TRANSITION_DURATION_MS 300  // Screen transition animation duration

#define TOUCH_ITEM_COUNT  20
#define TOUCH_WIDTH_MIN   80
#define TOUCH_WIDTH_MAX   320
#define TOUCH_HEIGHT_MIN  30
#define TOUCH_HEIGHT_MAX  120
#define TOUCH_STEP        10
#define TOUCH_HEIGHT_STEP 5

// Static screen objects
static lv_obj_t* screen = NULL;
static lv_obj_t* size_label = NULL;
static lv_obj_t* list = NULL;
static lv_obj_t* selected_item = NULL;
static lv_obj_t* btn_width_dec = NULL;
static lv_obj_t* btn_width_inc = NULL;
static lv_obj_t* btn_height_dec = NULL;
static lv_obj_t* btn_height_inc = NULL;
static lv_obj_t* list_items[TOUCH_ITEM_COUNT] = {0};

// Touch target sizing state
static int32_t touch_width = 200;
static int32_t touch_height = 50;

// Display size helper
static void update_size_label(void) {
  if (size_label != NULL) {
    char buf[40];
    snprintf(buf, sizeof(buf), "Text W:%d  H:%d", (int)touch_width, (int)touch_height);
    lv_label_set_text(size_label, buf);
  }
}

static void apply_touch_sizes(void) {
  for (int i = 0; i < TOUCH_ITEM_COUNT; i++) {
    if (list_items[i] != NULL) {
      lv_obj_set_width(list_items[i], touch_width);
      lv_obj_set_height(list_items[i], touch_height);
    }
  }
}

// Width button handler changes touch width
static void width_button_handler(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_SHORT_CLICKED) {
    return;
  }

  lv_obj_t* btn = lv_event_get_target(e);
  if (btn == btn_width_dec) {
    touch_width -= TOUCH_STEP;
  } else if (btn == btn_width_inc) {
    touch_width += TOUCH_STEP;
  }

  if (touch_width < TOUCH_WIDTH_MIN) {
    touch_width = TOUCH_WIDTH_MIN;
  } else if (touch_width > TOUCH_WIDTH_MAX) {
    touch_width = TOUCH_WIDTH_MAX;
  }

  apply_touch_sizes();
  update_size_label();
}

// Height button handler changes touch height
static void height_button_handler(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_SHORT_CLICKED) {
    return;
  }

  lv_obj_t* btn = lv_event_get_target(e);
  if (btn == btn_height_dec) {
    touch_height -= TOUCH_HEIGHT_STEP;
  } else if (btn == btn_height_inc) {
    touch_height += TOUCH_HEIGHT_STEP;
  }

  if (touch_height < TOUCH_HEIGHT_MIN) {
    touch_height = TOUCH_HEIGHT_MIN;
  } else if (touch_height > TOUCH_HEIGHT_MAX) {
    touch_height = TOUCH_HEIGHT_MAX;
  }

  apply_touch_sizes();
  update_size_label();
}

// List item click handler
static void list_item_event_handler(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_CLICKED) {
    return;
  }

  lv_obj_t* clicked_item = lv_event_get_target(e);

  if (selected_item != NULL) {
    lv_obj_set_style_bg_color(selected_item, lv_color_hex(0x333333), 0);
  }

  selected_item = clicked_item;
  lv_obj_set_style_bg_color(selected_item, lv_color_hex(0x0080FF), 0);
}

// Gesture handler (left -> pin pad, right -> gesture)
static void screen_event_handler(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_GESTURE) {
    return;
  }

  lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_get_act());

  if (dir == LV_DIR_LEFT) {
    lv_obj_t* test_pin_pad_screen = screen_test_pin_pad_init(NULL);
    if (test_pin_pad_screen != NULL) {
      lv_scr_load_anim(test_pin_pad_screen, LV_SCR_LOAD_ANIM_MOVE_LEFT,
                       SCREEN_TRANSITION_DURATION_MS, 0, true);
    }
  } else if (dir == LV_DIR_RIGHT) {
    lv_obj_t* test_gesture_screen = screen_test_gesture_init(NULL);
    if (test_gesture_screen != NULL) {
      lv_scr_load_anim(test_gesture_screen, LV_SCR_LOAD_ANIM_MOVE_RIGHT,
                       SCREEN_TRANSITION_DURATION_MS, 0, true);
    }
  }
}

lv_obj_t* screen_test_scroll_init(void* ctx) {
  (void)ctx;

  touch_width = 200;
  touch_height = 50;
  selected_item = NULL;

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  size_label = lv_label_create(screen);
  if (!size_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(size_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(size_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(size_label, LV_ALIGN_TOP_MID, 0, 35);
  update_size_label();

  btn_width_inc = lv_button_create(screen);
  if (!btn_width_inc) {
    return NULL;
  }
  lv_obj_set_size(btn_width_inc, 45, 80);
  lv_obj_align(btn_width_inc, LV_ALIGN_LEFT_MID, 10, -50);
  lv_obj_t* label_width_inc = lv_label_create(btn_width_inc);
  if (!label_width_inc) {
    return NULL;
  }
  lv_label_set_text(label_width_inc, "+W");
  lv_obj_center(label_width_inc);
  lv_obj_add_event_cb(btn_width_inc, width_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  btn_width_dec = lv_button_create(screen);
  if (!btn_width_dec) {
    return NULL;
  }
  lv_obj_set_size(btn_width_dec, 45, 80);
  lv_obj_align(btn_width_dec, LV_ALIGN_LEFT_MID, 10, 40);
  lv_obj_t* label_width_dec = lv_label_create(btn_width_dec);
  if (!label_width_dec) {
    return NULL;
  }
  lv_label_set_text(label_width_dec, "-W");
  lv_obj_center(label_width_dec);
  lv_obj_add_event_cb(btn_width_dec, width_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  btn_height_inc = lv_button_create(screen);
  if (!btn_height_inc) {
    return NULL;
  }
  lv_obj_set_size(btn_height_inc, 45, 80);
  lv_obj_align(btn_height_inc, LV_ALIGN_RIGHT_MID, -10, -50);
  lv_obj_t* label_height_inc = lv_label_create(btn_height_inc);
  if (!label_height_inc) {
    return NULL;
  }
  lv_label_set_text(label_height_inc, "+H");
  lv_obj_center(label_height_inc);
  lv_obj_add_event_cb(btn_height_inc, height_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  btn_height_dec = lv_button_create(screen);
  if (!btn_height_dec) {
    return NULL;
  }
  lv_obj_set_size(btn_height_dec, 45, 80);
  lv_obj_align(btn_height_dec, LV_ALIGN_RIGHT_MID, -10, 40);
  lv_obj_t* label_height_dec = lv_label_create(btn_height_dec);
  if (!label_height_dec) {
    return NULL;
  }
  lv_label_set_text(label_height_dec, "-H");
  lv_obj_center(label_height_dec);
  lv_obj_add_event_cb(btn_height_dec, height_button_handler, LV_EVENT_SHORT_CLICKED, NULL);

  list = lv_obj_create(screen);
  if (!list) {
    return NULL;
  }
  lv_obj_set_size(list, 360, 300);
  lv_obj_align(list, LV_ALIGN_CENTER, 0, 20);
  lv_obj_set_style_bg_color(list, LIST_BG_COLOR, 0);
  lv_obj_set_style_border_width(list, 2, 0);
  lv_obj_set_style_border_color(list, LIST_BORDER_COLOR, 0);
  lv_obj_set_style_pad_all(list, 12, 0);
  lv_obj_set_scroll_dir(list, LV_DIR_VER);
  lv_obj_set_scrollbar_mode(list, LV_SCROLLBAR_MODE_AUTO);
  lv_obj_set_flex_flow(list, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(list, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER);

  for (int i = 0; i < TOUCH_ITEM_COUNT; i++) {
    lv_obj_t* item = lv_button_create(list);
    if (!item) {
      return NULL;
    }
    list_items[i] = item;
    lv_obj_set_style_bg_color(item, lv_color_hex(0x333333), 0);
    lv_obj_set_style_border_color(item, lv_color_hex(0x555555), 0);
    lv_obj_set_style_border_width(item, 1, 0);
    lv_obj_set_style_radius(item, 8, 0);
    lv_obj_set_style_pad_all(item, 0, 0);
    lv_obj_add_event_cb(item, list_item_event_handler, LV_EVENT_CLICKED, NULL);
    lv_obj_clear_flag(item, LV_OBJ_FLAG_SCROLL_ON_FOCUS);

    lv_obj_t* label = lv_label_create(item);
    if (!label) {
      return NULL;
    }
    char buf[32];
    snprintf(buf, sizeof(buf), "row %d row %d row %d", i + 1, i + 1, i + 1);
    lv_label_set_text(label, buf);
    lv_obj_set_style_text_color(label, lv_color_white(), 0);
    lv_obj_center(label);
  }

  apply_touch_sizes();
  lv_obj_update_layout(list);
  if (list_items[0] != NULL) {
    lv_obj_scroll_to_y(list, lv_obj_get_y(list_items[0]), LV_ANIM_OFF);
  } else {
    lv_obj_scroll_to_y(list, 0, LV_ANIM_OFF);
  }

  lv_obj_add_event_cb(screen, screen_event_handler, LV_EVENT_GESTURE, NULL);

  return screen;
}

void screen_test_scroll_update(void* ctx) {
  (void)ctx;
}

void screen_test_scroll_destroy(void) {
  if (screen != NULL) {
    lv_obj_del(screen);
    screen = NULL;
  }

  size_label = NULL;
  list = NULL;
  selected_item = NULL;
  btn_width_dec = NULL;
  btn_width_inc = NULL;
  btn_height_dec = NULL;
  btn_height_inc = NULL;
  for (int i = 0; i < TOUCH_ITEM_COUNT; i++) {
    list_items[i] = NULL;
  }
}
