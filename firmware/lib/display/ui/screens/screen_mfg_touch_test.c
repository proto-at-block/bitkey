// Touch test screen - interactive touch calibration test

#include "screen_mfg_touch_test.h"

#include "assert.h"
#include "display.pb.h"
#include "lvgl.h"
#include "uc.h"
#include "uxc.pb.h"

#include <math.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Colors
#define COLOR_YELLOW          lv_color_make(255, 255, 0)
#define TOUCH_PROMPT_BG_COLOR lv_color_make(0, 100, 200)  // Blue

// Touch test configuration
#define TOUCH_TEST_BOX_SIZE       30  // Size of each touch box
#define TOUCH_TEST_NUM_EDGE_BOXES 20  // Number of boxes around the edge
#define TOUCH_TEST_NUM_X_BOXES    10  // Number of boxes on each diagonal of the X
#define TOUCH_TEST_MAX_BOXES      (TOUCH_TEST_NUM_EDGE_BOXES + TOUCH_TEST_NUM_X_BOXES * 2)
#define TOUCH_TEST_EDGE_OFFSET    40  // Offset from edge for circle boxes

// Touch test state
static lv_obj_t* screen = NULL;
static lv_obj_t* touch_boxes[TOUCH_TEST_MAX_BOXES] = {NULL};
static bool touch_boxes_cleared[TOUCH_TEST_MAX_BOXES] = {false};
static uint16_t touch_boxes_total = 0;
static uint16_t touch_boxes_cleared_count = 0;
static lv_obj_t* touch_test_container = NULL;

// Helper function to check if point (x, y) is within touch distance of a box
static bool is_point_near_box(lv_coord_t x, lv_coord_t y, lv_coord_t box_x, lv_coord_t box_y) {
  const lv_coord_t tolerance = TOUCH_TEST_BOX_SIZE / 2;
  return (x >= (box_x - tolerance) && x <= (box_x + TOUCH_TEST_BOX_SIZE + tolerance) &&
          y >= (box_y - tolerance) && y <= (box_y + TOUCH_TEST_BOX_SIZE + tolerance));
}

// Touch event handler for the touch test
static void touch_test_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  // Handle both PRESSED and PRESSING for drag support
  if (code != LV_EVENT_PRESSED && code != LV_EVENT_PRESSING) {
    return;
  }

  lv_indev_t* indev = lv_indev_get_act();
  if (!indev) {
    return;
  }

  lv_point_t point;
  lv_indev_get_point(indev, &point);

  // Check if touch point is near any uncleared box
  for (uint16_t i = 0; i < touch_boxes_total; i++) {
    if (touch_boxes_cleared[i] || !touch_boxes[i]) {
      continue;
    }

    lv_coord_t box_x = lv_obj_get_x(touch_boxes[i]);
    lv_coord_t box_y = lv_obj_get_y(touch_boxes[i]);

    if (is_point_near_box(point.x, point.y, box_x, box_y)) {
      touch_boxes_cleared[i] = true;
      touch_boxes_cleared_count++;
      lv_obj_set_style_bg_opa(touch_boxes[i], LV_OPA_TRANSP, 0);
      lv_obj_set_style_border_opa(touch_boxes[i], LV_OPA_TRANSP, 0);

#ifdef EMBEDDED_BUILD
      uint16_t remaining = touch_boxes_total - touch_boxes_cleared_count;
      // Send status update to Core
      fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
      if (msg) {
        msg->which_msg = fwpb_uxc_msg_device_mfg_touch_test_status_tag;
        msg->msg.mfg_touch_test_status.boxes_remaining = remaining;
        uc_send_immediate(msg);
      }
#endif

      break;  // Only clear one box per event
    }
  }
}

lv_obj_t* screen_mfg_touch_test_init(void* ctx) {
  ASSERT(screen == NULL);
  (void)ctx;

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, TOUCH_PROMPT_BG_COLOR, 0);
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);

  // Initialize touch test state
  touch_boxes_total = 0;
  touch_boxes_cleared_count = 0;

  // Create container for touch boxes
  touch_test_container = lv_obj_create(screen);
  if (!touch_test_container) {
    return NULL;
  }
  lv_obj_remove_style_all(touch_test_container);
  lv_obj_set_size(touch_test_container, LV_PCT(100), LV_PCT(100));
  lv_obj_add_flag(touch_test_container, LV_OBJ_FLAG_CLICKABLE);

  // Attach touch event handlers for both press and drag
  lv_obj_add_event_cb(touch_test_container, touch_test_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(touch_test_container, touch_test_event_handler, LV_EVENT_PRESSING, NULL);

  // Get screen dimensions from LVGL
  const lv_coord_t screen_width = lv_obj_get_width(screen);
  const lv_coord_t screen_radius = screen_width / 2;
  const lv_coord_t center_x = screen_radius;
  const lv_coord_t center_y = screen_radius;

  // Generate boxes around the circular edge
  const double circle_radius = screen_radius - TOUCH_TEST_EDGE_OFFSET;
  for (uint16_t i = 0; i < TOUCH_TEST_NUM_EDGE_BOXES; i++) {
    double angle = (2.0 * M_PI * i) / TOUCH_TEST_NUM_EDGE_BOXES;
    lv_coord_t x = (lv_coord_t)(center_x + circle_radius * cos(angle) - TOUCH_TEST_BOX_SIZE / 2);
    lv_coord_t y = (lv_coord_t)(center_y + circle_radius * sin(angle) - TOUCH_TEST_BOX_SIZE / 2);

    // Check if box center is within visible circular display
    lv_coord_t box_center_x = x + TOUCH_TEST_BOX_SIZE / 2;
    lv_coord_t box_center_y = y + TOUCH_TEST_BOX_SIZE / 2;
    lv_coord_t dx = box_center_x - center_x;
    lv_coord_t dy = box_center_y - center_y;
    double dist_from_center = sqrt(dx * dx + dy * dy);

    // Skip boxes outside the circular display (would be cut off)
    if (dist_from_center > (screen_radius - TOUCH_TEST_BOX_SIZE)) {
      continue;
    }

    touch_boxes[touch_boxes_total] = lv_obj_create(touch_test_container);
    lv_obj_remove_style_all(touch_boxes[touch_boxes_total]);
    lv_obj_set_size(touch_boxes[touch_boxes_total], TOUCH_TEST_BOX_SIZE, TOUCH_TEST_BOX_SIZE);
    lv_obj_set_pos(touch_boxes[touch_boxes_total], x, y);
    lv_obj_set_style_bg_color(touch_boxes[touch_boxes_total], lv_color_white(), 0);
    lv_obj_set_style_bg_opa(touch_boxes[touch_boxes_total], LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(touch_boxes[touch_boxes_total], 2, 0);
    lv_obj_set_style_border_color(touch_boxes[touch_boxes_total], COLOR_YELLOW, 0);
    lv_obj_add_flag(touch_boxes[touch_boxes_total], LV_OBJ_FLAG_IGNORE_LAYOUT);
    touch_boxes_cleared[touch_boxes_total] = false;
    touch_boxes_total++;
  }

  // Generate boxes for the X pattern (top-left to bottom-right diagonal)
  for (uint16_t i = 0; i < TOUCH_TEST_NUM_X_BOXES; i++) {
    float t = (float)(i + 1) / (float)(TOUCH_TEST_NUM_X_BOXES + 1);
    lv_coord_t x = (lv_coord_t)(t * (2 * screen_radius) - TOUCH_TEST_BOX_SIZE / 2);
    lv_coord_t y = (lv_coord_t)(t * (2 * screen_radius) - TOUCH_TEST_BOX_SIZE / 2);

    // Check if box center is within visible circular display
    lv_coord_t box_center_x = x + TOUCH_TEST_BOX_SIZE / 2;
    lv_coord_t box_center_y = y + TOUCH_TEST_BOX_SIZE / 2;
    lv_coord_t dx = box_center_x - center_x;
    lv_coord_t dy = box_center_y - center_y;
    double dist_from_center = sqrt(dx * dx + dy * dy);

    // Skip boxes outside the circular display
    if (dist_from_center > (screen_radius - TOUCH_TEST_BOX_SIZE)) {
      continue;
    }

    touch_boxes[touch_boxes_total] = lv_obj_create(touch_test_container);
    if (!touch_boxes[touch_boxes_total]) {
      return NULL;
    }
    lv_obj_remove_style_all(touch_boxes[touch_boxes_total]);
    lv_obj_set_size(touch_boxes[touch_boxes_total], TOUCH_TEST_BOX_SIZE, TOUCH_TEST_BOX_SIZE);
    lv_obj_set_pos(touch_boxes[touch_boxes_total], x, y);
    lv_obj_set_style_bg_color(touch_boxes[touch_boxes_total], lv_color_white(), 0);
    lv_obj_set_style_bg_opa(touch_boxes[touch_boxes_total], LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(touch_boxes[touch_boxes_total], 2, 0);
    lv_obj_set_style_border_color(touch_boxes[touch_boxes_total], COLOR_YELLOW, 0);
    lv_obj_add_flag(touch_boxes[touch_boxes_total], LV_OBJ_FLAG_IGNORE_LAYOUT);
    touch_boxes_cleared[touch_boxes_total] = false;
    touch_boxes_total++;
  }

  // Generate boxes for the X pattern (top-right to bottom-left diagonal)
  for (uint16_t i = 0; i < TOUCH_TEST_NUM_X_BOXES; i++) {
    float t = (float)(i + 1) / (float)(TOUCH_TEST_NUM_X_BOXES + 1);
    lv_coord_t x = (lv_coord_t)((1.0f - t) * (2 * screen_radius) - TOUCH_TEST_BOX_SIZE / 2);
    lv_coord_t y = (lv_coord_t)(t * (2 * screen_radius) - TOUCH_TEST_BOX_SIZE / 2);

    // Check if box center is within visible circular display
    lv_coord_t box_center_x = x + TOUCH_TEST_BOX_SIZE / 2;
    lv_coord_t box_center_y = y + TOUCH_TEST_BOX_SIZE / 2;
    lv_coord_t dx = box_center_x - center_x;
    lv_coord_t dy = box_center_y - center_y;
    double dist_from_center = sqrt(dx * dx + dy * dy);

    // Skip boxes outside the circular display
    if (dist_from_center > (screen_radius - TOUCH_TEST_BOX_SIZE)) {
      continue;
    }

    touch_boxes[touch_boxes_total] = lv_obj_create(touch_test_container);
    if (!touch_boxes[touch_boxes_total]) {
      return NULL;
    }
    lv_obj_remove_style_all(touch_boxes[touch_boxes_total]);
    lv_obj_set_size(touch_boxes[touch_boxes_total], TOUCH_TEST_BOX_SIZE, TOUCH_TEST_BOX_SIZE);
    lv_obj_set_pos(touch_boxes[touch_boxes_total], x, y);
    lv_obj_set_style_bg_color(touch_boxes[touch_boxes_total], lv_color_white(), 0);
    lv_obj_set_style_bg_opa(touch_boxes[touch_boxes_total], LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(touch_boxes[touch_boxes_total], 2, 0);
    lv_obj_set_style_border_color(touch_boxes[touch_boxes_total], COLOR_YELLOW, 0);
    lv_obj_add_flag(touch_boxes[touch_boxes_total], LV_OBJ_FLAG_IGNORE_LAYOUT);
    touch_boxes_cleared[touch_boxes_total] = false;
    touch_boxes_total++;
  }

#ifdef EMBEDDED_BUILD
  // Send initial status to Core
  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  if (msg) {
    msg->which_msg = fwpb_uxc_msg_device_mfg_touch_test_status_tag;
    msg->msg.mfg_touch_test_status.boxes_remaining = touch_boxes_total;
    uc_send_immediate(msg);
  }
#endif

  return screen;
}

void screen_mfg_touch_test_destroy(void) {
  if (!screen) {
    return;
  }

  if (touch_test_container) {
    lv_obj_del(touch_test_container);
    touch_test_container = NULL;
  }

  for (uint16_t i = 0; i < TOUCH_TEST_MAX_BOXES; i++) {
    touch_boxes[i] = NULL;
    touch_boxes_cleared[i] = false;
  }
  touch_boxes_total = 0;
  touch_boxes_cleared_count = 0;

  lv_obj_del(screen);
  screen = NULL;
}

uint16_t screen_mfg_touch_test_get_boxes_remaining(void) {
  if (touch_boxes_total == 0) {
    return 0;
  }

  return touch_boxes_total - touch_boxes_cleared_count;
}
