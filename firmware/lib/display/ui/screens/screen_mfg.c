// Manufacturing test screen with starfield FPS test and color tests

#include "screen_mfg.h"

#include "assert.h"
#include "display.pb.h"
#include "log.h"
#include "lvgl.h"
#include "secure_rng.h"
#include "uc.h"
#include "ui.h"
#include "uxc.pb.h"

#include <math.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100
#define STAR_COUNT        15
#define STAR_LAYERS       3
#define PHASE_DURATION_MS 7500  // 7.5 seconds per test phase

// Layout configuration
#define FPS_LABEL_Y_OFFSET 80  // FPS label vertical position from top

// Star configuration
#define STAR_MIN_SIZE     3   // Smallest star size (layer 0)
#define STAR_MIN_SPEED    1   // Slowest star speed (layer 0)
#define STAR_SPAWN_OFFSET 16  // Random offset when spawning at right edge
#define STAR_OFFSCREEN_X  -4  // X position considered off-screen left

// Opacity values for star layers
#define STAR_OPA_LAYER_0        140  // Far stars are dimmer
#define STAR_OPA_LAYER_1        190  // Medium distance stars
#define STAR_OPA_LAYER_2        255  // Near stars are brightest
#define STAR_OPA_MIN            60   // Minimum opacity during twinkle
#define STAR_OPA_TWINKLE_OFFSET 70   // Offset for twinkle calculation

// Timer intervals
#define FPS_UPDATE_INTERVAL_MS     250  // How often to update FPS display
#define STAR_ANIMATION_INTERVAL_MS 16   // Animation frame rate (~60 FPS)

// Random color generation ranges
#define STAR_COLOR_BASE  180  // Base value for star RGB components
#define STAR_COLOR_RANGE 76   // Random range added to base (180-255)

// Twinkle animation
#define TWINKLE_PHASE_MASK 0x3FF  // Mask for initial phase randomization
#define TWINKLE_SHIFT      3      // Bit shift for twinkle calculation
#define TWINKLE_RANGE_MASK 0x3F   // Mask for twinkle range
#define TWINKLE_BASE       40     // Base twinkle offset
#define TWINKLE_SPEED_MULT 2      // Multiplier for twinkle speed per layer

// Colors
#define COLOR_YELLOW lv_color_make(255, 255, 0)

// Run-in test screen layout configuration
#define START_SCREEN_BG_COLOR       lv_color_make(64, 64, 64)  // Dark gray
#define START_SCREEN_TICK_X_OFFSET  40                         // Green tick X offset from left
#define START_SCREEN_TICK_Y_OFFSET  30                         // Green tick Y offset from top
#define START_SCREEN_CROSS_X_OFFSET -40                        // Red cross X offset from right
#define START_SCREEN_CROSS_Y_OFFSET 30                         // Red cross Y offset from top
#define START_SCREEN_CENTER_TEXT_Y  -20                        // Center text Y offset
#define START_SCREEN_BATTERY_Y      30                         // Battery status Y offset
#define START_SCREEN_COLOR_GREEN    lv_color_make(0, 255, 0)   // Charging color
#define START_SCREEN_COLOR_RED      lv_color_make(255, 0, 0)   // Not charging color

#define TOUCH_PROMPT_BG_COLOR lv_color_make(0, 100, 200)  // Blue

#define STATUS_BG_COLOR_YELLOW lv_color_make(200, 200, 0)  // In progress, no failures
#define STATUS_BG_COLOR_ORANGE lv_color_make(255, 140, 0)  // In progress, with failures
#define STATUS_BG_COLOR_GREEN  lv_color_make(0, 200, 0)    // Complete, passed
#define STATUS_BG_COLOR_RED    lv_color_make(200, 0, 0)    // Complete, failed
#define STATUS_TITLE_Y_OFFSET  10                          // Title Y offset from top

#define BURNIN_GRID_SPACING  20                // Grid spacing in pixels
#define BURNIN_GRID_BG_COLOR lv_color_white()  // White background
#define BURNIN_COLOR_RED     lv_color_make(255, 0, 0)
#define BURNIN_COLOR_GREEN   lv_color_make(0, 255, 0)
#define BURNIN_COLOR_BLUE    lv_color_make(0, 0, 255)

// Fonts
#define FONT_FPS_LABEL   (&cash_sans_mono_regular_20)
#define FONT_STANDARD    (&cash_sans_mono_regular_22)
#define FONT_COUNTDOWN   (&cash_sans_mono_regular_36)
#define FONT_SCROLLING_H (&cash_sans_mono_regular_36)
#define FONT_SYMBOLS     (&cash_sans_mono_regular_22)

// Screen state
static lv_obj_t* screen = NULL;
static fwpb_display_mfg_test_mode current_test_mode =
  fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;
static uint32_t current_custom_rgb = 0;

// FPS test UI elements
static lv_obj_t* fps_label = NULL;
static lv_obj_t* star_layer = NULL;
static lv_obj_t* stars[STAR_COUNT] = {NULL};

// General UI elements (reused across screens)
static lv_obj_t* status_label = NULL;

// Scrolling H test UI elements
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* scroll_label1 = NULL;
static lv_obj_t* scroll_label2 = NULL;
static int32_t scroll_offset = 0;

// Timers
static lv_timer_t* fps_timer = NULL;
static lv_timer_t* star_timer = NULL;
static lv_timer_t* phase_timer = NULL;
static lv_timer_t* scroll_timer = NULL;

// Star properties
static uint8_t star_layer_idx[STAR_COUNT];
static uint16_t twinkle_phase[STAR_COUNT];

// Test phase state
static bool full_invalidate_mode = false;

// Touch test configuration
#define TOUCH_TEST_BOX_SIZE       30  // Size of each touch box
#define TOUCH_TEST_NUM_EDGE_BOXES 20  // Number of boxes around the edge
#define TOUCH_TEST_NUM_X_BOXES    10  // Number of boxes on each diagonal of the X
#define TOUCH_TEST_MAX_BOXES      (TOUCH_TEST_NUM_EDGE_BOXES + TOUCH_TEST_NUM_X_BOXES * 2)
#define TOUCH_TEST_EDGE_OFFSET    40  // Offset from edge for circle boxes

// Touch test state
static lv_obj_t* touch_boxes[TOUCH_TEST_MAX_BOXES] = {NULL};
static bool touch_boxes_cleared[TOUCH_TEST_MAX_BOXES] = {false};
static uint16_t touch_boxes_total = 0;
static uint16_t touch_boxes_cleared_count = 0;
static lv_obj_t* touch_test_container = NULL;

static inline uint16_t ui_rand(void) {
  return crypto_rand_short();
}

// Helper function to format time as H:MM:SS
static void format_time(char* buf, size_t buf_size, uint32_t ms) {
  uint32_t total_sec = ms / 1000;
  uint32_t hours = total_sec / 3600;
  uint32_t minutes = (total_sec % 3600) / 60;
  uint32_t seconds = total_sec % 60;
  snprintf(buf, buf_size, "%lu:%02lu:%02lu", (unsigned long)hours, (unsigned long)minutes,
           (unsigned long)seconds);
}
static inline int16_t rand_range(int16_t a, int16_t b) {
  if (b <= a) {
    return a;
  }
  return a + (int16_t)(ui_rand() % (uint16_t)(b - a + 1));
}

static inline lv_color_t get_star_color(void) {
  uint8_t r = (uint8_t)(STAR_COLOR_BASE + (ui_rand() % STAR_COLOR_RANGE));
  uint8_t g = (uint8_t)(STAR_COLOR_BASE + (ui_rand() % STAR_COLOR_RANGE));
  uint8_t b = (uint8_t)(STAR_COLOR_BASE + (ui_rand() % STAR_COLOR_RANGE));
  return lv_color_make(r, g, b);
}

static inline uint8_t star_size(uint8_t layer) {
  return STAR_MIN_SIZE + layer;
}

static inline uint8_t star_speed(uint8_t layer) {
  return STAR_MIN_SPEED + layer;
}

static void toggle_test_phase(lv_timer_t* timer) {
  (void)timer;
  // Only toggle if we're still showing the FPS test
  if (!star_layer) {
    return;
  }

  full_invalidate_mode = !full_invalidate_mode;
}

static void update_fps_label(lv_timer_t* timer) {
  (void)timer;
  if (!fps_label) {
    return;
  }

  uint32_t flush_rate = ui_get_fps();
  uint32_t effective_fps = ui_get_effective_fps();
  char fps_text[128];

  if (full_invalidate_mode) {
    if (effective_fps > 0) {
      snprintf(fps_text, sizeof(fps_text), "Full Screen Updates\n%lu full fps\n%u stars",
               (unsigned long)effective_fps, STAR_COUNT);
    } else {
      snprintf(fps_text, sizeof(fps_text), "Full Screen Updates\n-- full fps\n%u stars",
               STAR_COUNT);
    }
  } else {
    if (flush_rate > 0) {
      snprintf(fps_text, sizeof(fps_text), "Partial Updates\n%lu flushes/sec\n%u stars",
               (unsigned long)flush_rate, STAR_COUNT);
    } else {
      snprintf(fps_text, sizeof(fps_text), "Partial Updates\n-- flushes/sec\n%u stars", STAR_COUNT);
    }
  }

  lv_label_set_text(fps_label, fps_text);
}

static void spawn_star(uint16_t i, lv_coord_t w, lv_coord_t h, bool at_right_edge) {
  star_layer_idx[i] = (uint8_t)(ui_rand() % STAR_LAYERS);
  twinkle_phase[i] = (uint16_t)(ui_rand() & TWINKLE_PHASE_MASK);

  lv_coord_t y = (h > 0) ? (lv_coord_t)rand_range(0, (int16_t)(h - 1)) : 0;
  lv_coord_t x = at_right_edge ? (lv_coord_t)(w + rand_range(0, STAR_SPAWN_OFFSET))
                               : (w > 0 ? (lv_coord_t)rand_range(0, (int16_t)(w - 1)) : 0);

  if (!stars[i]) {
    stars[i] = lv_obj_create(star_layer);
    lv_obj_remove_style_all(stars[i]);
    lv_obj_set_style_border_width(stars[i], 0, 0);
    lv_obj_set_style_radius(stars[i], 0, 0);
    lv_obj_add_flag(stars[i], LV_OBJ_FLAG_IGNORE_LAYOUT);
  }

  lv_obj_set_style_bg_color(stars[i], get_star_color(), 0);
  uint8_t size = star_size(star_layer_idx[i]);
  lv_obj_set_size(stars[i], size, size);

  // Far stars are dimmer
  uint8_t base_opa = (star_layer_idx[i] == 0)   ? STAR_OPA_LAYER_0
                     : (star_layer_idx[i] == 1) ? STAR_OPA_LAYER_1
                                                : STAR_OPA_LAYER_2;
  lv_obj_set_style_bg_opa(stars[i], base_opa, 0);

  lv_obj_set_pos(stars[i], x, y);
}

static void animate_stars(lv_timer_t* timer) {
  (void)timer;
  if (!screen || !star_layer) {
    return;
  }

  lv_coord_t w = lv_obj_get_width(star_layer);
  lv_coord_t h = lv_obj_get_height(star_layer);

  for (uint16_t i = 0; i < STAR_COUNT; i++) {
    if (!stars[i]) {
      continue;
    }

    // Move star left based on its depth layer
    uint8_t speed = star_speed(star_layer_idx[i]);
    lv_coord_t x = lv_obj_get_x(stars[i]) - speed;

    if (x < STAR_OFFSCREEN_X) {
      spawn_star(i, w, h, true);
    } else {
      lv_obj_set_x(stars[i], x);
    }

    // Animate twinkle effect
    twinkle_phase[i] += (uint16_t)((star_layer_idx[i] + 1) * TWINKLE_SPEED_MULT);
    uint8_t twinkle =
      (uint8_t)(TWINKLE_BASE + ((twinkle_phase[i] >> TWINKLE_SHIFT) & TWINKLE_RANGE_MASK));
    uint8_t base_opa = (star_layer_idx[i] == 0)   ? STAR_OPA_LAYER_0
                       : (star_layer_idx[i] == 1) ? STAR_OPA_LAYER_1
                                                  : STAR_OPA_LAYER_2;
    int opacity = base_opa + twinkle - STAR_OPA_TWINKLE_OFFSET;
    if (opacity < STAR_OPA_MIN) {
      opacity = STAR_OPA_MIN;
    }
    if (opacity > 255) {
      opacity = 255;
    }
    lv_obj_set_style_bg_opa(stars[i], (lv_opa_t)opacity, 0);
  }

  if (full_invalidate_mode) {
    lv_obj_invalidate(screen);
  }
}

static void setup_fps_test(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);

  // Create star layer
  star_layer = lv_obj_create(scr);
  lv_obj_remove_style_all(star_layer);
  lv_obj_set_size(star_layer, LV_PCT(100), LV_PCT(100));
  lv_obj_add_flag(star_layer, LV_OBJ_FLAG_IGNORE_LAYOUT);
  lv_obj_clear_flag(star_layer, LV_OBJ_FLAG_CLICKABLE);

  // Spawn all stars
  lv_coord_t w = lv_obj_get_width(scr);
  lv_coord_t h = lv_obj_get_height(scr);
  full_invalidate_mode = false;
  for (uint16_t i = 0; i < STAR_COUNT; i++) {
    spawn_star(i, w, h, false);
  }

  // Create FPS label
  fps_label = lv_label_create(scr);
  lv_obj_set_style_text_color(fps_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(fps_label, FONT_FPS_LABEL, 0);
  lv_obj_set_style_text_align(fps_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(fps_label, LV_ALIGN_TOP_MID, 0, FPS_LABEL_Y_OFFSET);
  lv_label_set_text(fps_label, "-- FPS");

  // Create timers
  fps_timer = lv_timer_create(update_fps_label, FPS_UPDATE_INTERVAL_MS, NULL);
  star_timer = lv_timer_create(animate_stars, STAR_ANIMATION_INTERVAL_MS, NULL);
  phase_timer = lv_timer_create(toggle_test_phase, PHASE_DURATION_MS, NULL);

  update_fps_label(NULL);
}

static void cleanup_fps_test(void) {
  if (fps_timer) {
    lv_timer_del(fps_timer);
    fps_timer = NULL;
  }
  if (star_timer) {
    lv_timer_del(star_timer);
    star_timer = NULL;
  }
  if (phase_timer) {
    lv_timer_del(phase_timer);
    phase_timer = NULL;
  }
  if (fps_label) {
    lv_obj_del(fps_label);
    fps_label = NULL;
  }
  if (star_layer) {
    lv_obj_del(star_layer);
    star_layer = NULL;
    for (uint16_t i = 0; i < STAR_COUNT; i++) {
      stars[i] = NULL;
    }
  }
}

static void animate_scroll_h(lv_timer_t* timer) {
  (void)timer;
  if (!scroll_label1 || !scroll_label2 || !scroll_container) {
    return;
  }

  // Scroll left by 3 pixels per frame
  scroll_offset -= 3;

  // Get the width of one label
  lv_coord_t label_width = lv_obj_get_width(scroll_label1);

  // When first label scrolls completely off screen, reset position
  if (scroll_offset <= -label_width) {
    scroll_offset = 0;
  }

  // Position both labels - second one follows first
  lv_obj_set_x(scroll_label1, scroll_offset);
  lv_obj_set_x(scroll_label2, scroll_offset + label_width);
}

static void setup_scrolling_h_test(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);

  // Create scrolling container with clipping enabled
  scroll_container = lv_obj_create(scr);
  lv_obj_remove_style_all(scroll_container);
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_PCT(100));
  lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_IGNORE_LAYOUT);
  lv_obj_set_style_clip_corner(scroll_container, true, 0);

  // Generate grid of H characters
  // Display is 466x466 pixels, 1.43" (3.63 cm) wide
  // Target: 3 chars/cm = ~11 characters across screen width
  // Using 36pt font (~29px wide) = 466px/29px = ~16 chars visible
  // 15 rows × (22 H's + 1 newline) = ~345 chars
  char h_text[360];
  int pos = 0;
  for (int row = 0; row < 15; row++) {    // 15 rows with 36pt font fills screen height
    for (int col = 0; col < 22; col++) {  // 22 chars = slightly more than visible for smooth scroll
      h_text[pos++] = 'H';
    }
    h_text[pos++] = '\n';
  }
  h_text[pos] = '\0';

  // Create two identical labels for seamless scrolling
  scroll_label1 = lv_label_create(scroll_container);
  lv_obj_set_style_text_color(scroll_label1, lv_color_black(), 0);
  lv_obj_set_style_text_font(scroll_label1, FONT_SCROLLING_H, 0);
  lv_label_set_text(scroll_label1, h_text);
  lv_obj_set_pos(scroll_label1, 0, 0);

  scroll_label2 = lv_label_create(scroll_container);
  lv_obj_set_style_text_color(scroll_label2, lv_color_black(), 0);
  lv_obj_set_style_text_font(scroll_label2, FONT_SCROLLING_H, 0);
  lv_label_set_text(scroll_label2, h_text);

  // Position second label right after the first (will be updated in animation)
  lv_coord_t label_width = lv_obj_get_width(scroll_label1);
  lv_obj_set_pos(scroll_label2, label_width, 0);

  scroll_offset = 0;

  // Create timer for scrolling animation (30 FPS)
  scroll_timer = lv_timer_create(animate_scroll_h, 33, NULL);
}

static void cleanup_scrolling_h_test(void) {
  if (scroll_timer) {
    lv_timer_del(scroll_timer);
    scroll_timer = NULL;
  }
  if (scroll_label1) {
    lv_obj_del(scroll_label1);
    scroll_label1 = NULL;
  }
  if (scroll_label2) {
    lv_obj_del(scroll_label2);
    scroll_label2 = NULL;
  }
  if (scroll_container) {
    lv_obj_del(scroll_container);
    scroll_container = NULL;
  }
  scroll_offset = 0;
}

static void setup_solid_color_screen(lv_obj_t* scr, lv_color_t color) {
  lv_obj_set_style_bg_color(scr, color, 0);
}

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
      uint16_t remaining = touch_boxes_total - touch_boxes_cleared_count;

#ifdef EMBEDDED_BUILD
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

// Cleanup touch test UI elements
static void cleanup_touch_test(void) {
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
}

static void setup_runin_start_screen(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  lv_obj_set_style_bg_color(scr, START_SCREEN_BG_COLOR, 0);

  // Green tick (top-left)
  lv_obj_t* tick = lv_label_create(scr);
  lv_obj_set_style_text_color(tick, START_SCREEN_COLOR_GREEN, 0);
  lv_obj_set_style_text_font(tick, FONT_SYMBOLS, 0);
  lv_label_set_text(tick, LV_SYMBOL_OK);
  lv_obj_align(tick, LV_ALIGN_TOP_LEFT, START_SCREEN_TICK_X_OFFSET, START_SCREEN_TICK_Y_OFFSET);

  // Red cross (top-right)
  lv_obj_t* cross = lv_label_create(scr);
  lv_obj_set_style_text_color(cross, START_SCREEN_COLOR_RED, 0);
  lv_obj_set_style_text_font(cross, FONT_SYMBOLS, 0);
  lv_label_set_text(cross, LV_SYMBOL_CLOSE);
  lv_obj_align(cross, LV_ALIGN_TOP_RIGHT, START_SCREEN_CROSS_X_OFFSET, START_SCREEN_CROSS_Y_OFFSET);

  // Center text
  lv_obj_t* center_label = lv_label_create(scr);
  lv_obj_set_style_text_color(center_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(center_label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(center_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(center_label, "Start Run-In App?");
  lv_obj_align(center_label, LV_ALIGN_CENTER, 0, START_SCREEN_CENTER_TEXT_Y);

  // Battery status below center
  uint8_t battery_pct = show_screen->params.mfg.battery_percent;
  bool charging = show_screen->params.mfg.is_charging;

  status_label = lv_label_create(scr);
  lv_obj_set_style_text_font(status_label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);

  char status_text[64];
  if (charging) {
    lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_GREEN, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Charging)",
             (unsigned long)battery_pct);
  } else {
    lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_RED, 0);
    snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Not Charging)",
             (unsigned long)battery_pct);
  }
  lv_label_set_text(status_label, status_text);
  lv_obj_align(status_label, LV_ALIGN_CENTER, 0, START_SCREEN_BATTERY_Y);
}

static void setup_runin_touch_prompt(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, TOUCH_PROMPT_BG_COLOR, 0);

  lv_obj_t* label = lv_label_create(scr);
  lv_obj_set_style_text_color(label, lv_color_white(), 0);
  lv_obj_set_style_text_font(label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(label, "Touch to Start Test");
  lv_obj_center(label);
}

static void setup_touch_test_boxes(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, TOUCH_PROMPT_BG_COLOR, 0);

  // Initialize touch test state
  touch_boxes_total = 0;
  touch_boxes_cleared_count = 0;

  // Create container for touch boxes
  touch_test_container = lv_obj_create(scr);
  lv_obj_remove_style_all(touch_test_container);
  lv_obj_set_size(touch_test_container, LV_PCT(100), LV_PCT(100));
  lv_obj_add_flag(touch_test_container, LV_OBJ_FLAG_CLICKABLE);

  // Attach touch event handlers for both press and drag
  lv_obj_add_event_cb(touch_test_container, touch_test_event_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(touch_test_container, touch_test_event_handler, LV_EVENT_PRESSING, NULL);

  // Get screen dimensions from LVGL
  const lv_coord_t screen_width = lv_obj_get_width(scr);
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
}

static void setup_runin_countdown(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  lv_obj_set_style_bg_color(scr, lv_color_black(), 0);

  char countdown_text[8];
  snprintf(countdown_text, sizeof(countdown_text), "%lu",
           (unsigned long)show_screen->params.mfg.countdown_value);

  status_label = lv_label_create(scr);
  lv_obj_set_style_text_color(status_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(status_label, FONT_COUNTDOWN, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(status_label, countdown_text);
  lv_obj_center(status_label);
}

static void setup_runin_status_screen(lv_obj_t* scr, const fwpb_display_show_screen* show_screen) {
  // Dynamic background color based on test state
  lv_color_t bg_color;
  const char* result_text;

  if (show_screen->params.mfg.test_complete) {
    // Test complete: green = pass, red = fail
    if (show_screen->params.mfg.has_failures) {
      bg_color = STATUS_BG_COLOR_RED;
      result_text = "FAIL";
    } else {
      bg_color = STATUS_BG_COLOR_GREEN;
      result_text = "PASS";
    }
  } else {
    // Test in progress: yellow or orange
    if (show_screen->params.mfg.has_failures) {
      bg_color = STATUS_BG_COLOR_ORANGE;
    } else {
      bg_color = STATUS_BG_COLOR_YELLOW;
    }
    result_text = "In Progress";
  }

  lv_obj_set_style_bg_color(scr, bg_color, 0);

  // Title
  lv_obj_t* title = lv_label_create(scr);
  lv_obj_set_style_text_color(title, lv_color_black(), 0);
  lv_obj_set_style_text_font(title, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(title, "STATUS");
  lv_obj_align(title, LV_ALIGN_TOP_MID, 0, STATUS_TITLE_Y_OFFSET);

  // Status details
  char elapsed_str[32];
  char phase_remaining_str[32];
  format_time(elapsed_str, sizeof(elapsed_str), show_screen->params.mfg.elapsed_ms);
  format_time(phase_remaining_str, sizeof(phase_remaining_str),
              show_screen->params.mfg.phase_time_remaining_ms);

  // Get phase name based on power_phase enum value
  const char* phase_name;
  switch (show_screen->params.mfg.power_phase) {
    case 0:
      phase_name = "CHARGE 1";
      break;
    case 1:
      phase_name = "DISCHARGE";
      break;
    case 2:
      phase_name = "CHARGE 2";
      break;
    case 3:
      phase_name = "COMPLETE";
      break;
    default:
      phase_name = "UNKNOWN";
      break;
  }

  // Get phase number (1-based) and holding indicator
  uint32_t phase_num = show_screen->params.mfg.power_phase + 1;
  if (phase_num > 3) {
    phase_num = 3;  // Cap at 3 for complete phase
  }
  const char* holding_text = show_screen->params.mfg.target_reached ? " [HOLD]" : "";

  char status_text[256];
  uint32_t total_failures =
    show_screen->params.mfg.button_events + show_screen->params.mfg.captouch_events +
    show_screen->params.mfg.display_touch_events + show_screen->params.mfg.fingerprint_events;

  if (total_failures == 0) {
    snprintf(status_text, sizeof(status_text),
             "Result: %s\nPhase %lu/3: %s%s\nRemain: %s\nTime: %s\nLoops: %lu\nSOC: %lu%%",
             result_text, (unsigned long)phase_num, phase_name, holding_text, phase_remaining_str,
             elapsed_str, (unsigned long)show_screen->params.mfg.loop_count,
             (unsigned long)show_screen->params.mfg.battery_percent);
  } else {
    snprintf(status_text, sizeof(status_text),
             "Result: %s\nPhase %lu/3: %s%s\nRemain: %s\nLoops: %lu\nBtn:%lu Cap:%lu FP:%lu T:%lu",
             result_text, (unsigned long)phase_num, phase_name, holding_text, phase_remaining_str,
             (unsigned long)show_screen->params.mfg.loop_count,
             (unsigned long)show_screen->params.mfg.button_events,
             (unsigned long)show_screen->params.mfg.captouch_events,
             (unsigned long)show_screen->params.mfg.fingerprint_events,
             (unsigned long)show_screen->params.mfg.display_touch_events);
  }

  status_label = lv_label_create(scr);
  lv_obj_set_style_text_color(status_label, lv_color_black(), 0);
  lv_obj_set_style_text_font(status_label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(status_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(status_label, status_text);
  lv_obj_center(status_label);
}

static void setup_nfc_test_screen(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_white(), 0);

  lv_obj_t* label = lv_label_create(scr);
  lv_obj_set_style_text_color(label, lv_color_black(), 0);
  lv_obj_set_style_text_font(label, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(label, "NFC TAP");
  lv_obj_center(label);
}

static void setup_burnin_grid(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, BURNIN_GRID_BG_COLOR, 0);

  lv_coord_t w = lv_obj_get_width(scr);
  lv_coord_t h = lv_obj_get_height(scr);

  // Draw vertical lines in R, G, B, BK pattern
  int line_index = 0;
  for (lv_coord_t x = 0; x < w; x += BURNIN_GRID_SPACING, line_index++) {
    lv_obj_t* line_obj = lv_obj_create(scr);
    lv_obj_remove_style_all(line_obj);
    lv_obj_set_size(line_obj, 1, h);
    lv_obj_set_pos(line_obj, x, 0);
    lv_obj_add_flag(line_obj, LV_OBJ_FLAG_IGNORE_LAYOUT);

    // Cycle through colors: Red, Green, Blue, Black
    lv_color_t line_color;
    int pattern_index = line_index % 4;
    switch (pattern_index) {
      case 0:
        line_color = BURNIN_COLOR_RED;
        break;
      case 1:
        line_color = BURNIN_COLOR_GREEN;
        break;
      case 2:
        line_color = BURNIN_COLOR_BLUE;
        break;
      default:
        line_color = lv_color_black();
        break;
    }
    lv_obj_set_style_bg_color(line_obj, line_color, 0);
    lv_obj_set_style_bg_opa(line_obj, LV_OPA_COVER, 0);
  }

  // Draw horizontal lines in R, G, B, BK pattern
  line_index = 0;
  for (lv_coord_t y = 0; y < h; y += BURNIN_GRID_SPACING, line_index++) {
    lv_obj_t* line_obj = lv_obj_create(scr);
    lv_obj_remove_style_all(line_obj);
    lv_obj_set_size(line_obj, w, 1);
    lv_obj_set_pos(line_obj, 0, y);
    lv_obj_add_flag(line_obj, LV_OBJ_FLAG_IGNORE_LAYOUT);

    // Cycle through colors: Red, Green, Blue, Black
    lv_color_t line_color;
    int pattern_index = line_index % 4;
    switch (pattern_index) {
      case 0:
        line_color = BURNIN_COLOR_RED;
        break;
      case 1:
        line_color = BURNIN_COLOR_GREEN;
        break;
      case 2:
        line_color = BURNIN_COLOR_BLUE;
        break;
      default:
        line_color = lv_color_black();
        break;
    }
    lv_obj_set_style_bg_color(line_obj, line_color, 0);
    lv_obj_set_style_bg_opa(line_obj, LV_OPA_COVER, 0);
  }
}

static void setup_color_bars(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_black(), 0);

  lv_coord_t w = lv_obj_get_width(scr);
  lv_coord_t h = lv_obj_get_height(scr);
  lv_coord_t bar_width = w / 8;

  // Define the 8 EBU color bars: White, Yellow, Cyan, Green, Magenta, Red, Blue, Black
  lv_color_t colors[8] = {
    lv_color_make(255, 255, 255),  // White
    lv_color_make(255, 255, 0),    // Yellow
    lv_color_make(0, 255, 255),    // Cyan
    lv_color_make(0, 255, 0),      // Green
    lv_color_make(255, 0, 255),    // Magenta
    lv_color_make(255, 0, 0),      // Red
    lv_color_make(0, 0, 255),      // Blue
    lv_color_make(0, 0, 0),        // Black
  };

  for (int i = 0; i < 8; i++) {
    lv_obj_t* bar = lv_obj_create(scr);
    lv_obj_remove_style_all(bar);
    lv_obj_set_style_bg_color(bar, colors[i], 0);
    lv_obj_set_style_bg_opa(bar, LV_OPA_COVER, 0);
    lv_obj_set_size(bar, bar_width, h);
    lv_obj_set_pos(bar, i * bar_width, 0);
    lv_obj_add_flag(bar, LV_OBJ_FLAG_IGNORE_LAYOUT);
  }
}

static void setup_button_bypass_warning(lv_obj_t* scr) {
  lv_obj_set_style_bg_color(scr, lv_color_make(255, 165, 0), 0);

  // Title
  lv_obj_t* title = lv_label_create(scr);
  lv_obj_set_style_text_color(title, lv_color_black(), 0);
  lv_obj_set_style_text_font(title, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(title, "BUTTON TEST MODE");
  lv_obj_align(title, LV_ALIGN_CENTER, 0, -30);

  // Subtitle
  lv_obj_t* subtitle = lv_label_create(scr);
  lv_obj_set_style_text_color(subtitle, lv_color_black(), 0);
  lv_obj_set_style_text_font(subtitle, FONT_STANDARD, 0);
  lv_obj_set_style_text_align(subtitle, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_text(subtitle, "Buttons Disabled");
  lv_obj_align(subtitle, LV_ALIGN_CENTER, 0, 10);
}

lv_obj_t* screen_mfg_init(void* ctx) {
  ASSERT(screen == NULL);

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  fwpb_display_mfg_test_mode test_mode = fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;
  uint32_t brightness_percent = 0;  // 0 = don't change

  if (show_screen && show_screen->which_params == fwpb_display_show_screen_mfg_tag) {
    test_mode = show_screen->params.mfg.test_mode;
    brightness_percent = show_screen->params.mfg.brightness_percent;
  }

  // Track current mode
  current_test_mode = test_mode;

  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);

  // Set brightness: 0 = don't change (use default), 1-100 = set percent
  if (brightness_percent > 0 && brightness_percent <= 100) {
    ui_set_local_brightness((uint8_t)brightness_percent);
  } else {
    ui_set_local_brightness(SCREEN_BRIGHTNESS);
  }

  // Render based on mode
  switch (test_mode) {
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN:
      setup_runin_start_screen(screen, show_screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_TOUCH_PROMPT:
      setup_runin_touch_prompt(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_TOUCH_TEST_BOXES:
      setup_touch_test_boxes(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COUNTDOWN:
      setup_runin_countdown(screen, show_screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS:
      setup_runin_status_screen(screen, show_screen);
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION:
      setup_fps_test(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_SCROLLING_H:
      setup_scrolling_h_test(screen);
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR:
      current_custom_rgb = show_screen ? show_screen->params.mfg.custom_rgb : 0;
      setup_solid_color_screen(screen, lv_color_hex(current_custom_rgb));
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COLOR_BARS:
      setup_color_bars(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BURNIN_GRID:
      setup_burnin_grid(screen);
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_NFC_TEST:
      setup_nfc_test_screen(screen);
      break;
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BUTTON_BYPASS_WARNING:
      setup_button_bypass_warning(screen);
      break;

    default:
      setup_solid_color_screen(screen, lv_color_hex(0xFF0000));
      break;
  }

  return screen;
}

void screen_mfg_destroy(void) {
  if (!screen) {
    return;
  }

  cleanup_fps_test();
  cleanup_scrolling_h_test();
  cleanup_touch_test();

  lv_obj_del(screen);
  screen = NULL;
  status_label = NULL;

  // Reset arrays and flags
  memset(star_layer_idx, 0, sizeof(star_layer_idx));
  memset(twinkle_phase, 0, sizeof(twinkle_phase));
  full_invalidate_mode = false;
}

uint16_t screen_mfg_get_touch_boxes_remaining(void) {
  if (touch_boxes_total == 0) {
    return 0;
  }

  return touch_boxes_total - touch_boxes_cleared_count;
}

void screen_mfg_update(void* ctx) {
  if (!screen) {
    lv_obj_t* new_screen = screen_mfg_init(ctx);
    if (new_screen) {
      lv_scr_load(new_screen);
    }
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_mfg_tag) {
    return;
  }

  const fwpb_display_params_mfg* params = &show_screen->params.mfg;

  // Apply brightness if specified (1-100), regardless of other changes
  if (params->brightness_percent > 0 && params->brightness_percent <= 100) {
    ui_set_local_brightness((uint8_t)params->brightness_percent);
  }

  // If test mode changed, or custom RGB changed for CUSTOM_COLOR mode, recreate the screen
  bool mode_changed = (params->test_mode != current_test_mode);
  bool rgb_changed =
    (params->test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR &&
     params->custom_rgb != current_custom_rgb);
  if (mode_changed || rgb_changed) {
    screen_mfg_destroy();
    lv_obj_t* new_screen = screen_mfg_init(ctx);
    if (new_screen) {
      lv_scr_load(new_screen);
    }
    return;
  }

  // Same test mode - update dynamic screens
  switch (current_test_mode) {
    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN:
      // Update battery status on start screen
      if (status_label) {
        char status_text[64];
        if (params->is_charging) {
          lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_GREEN, 0);
          snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Charging)",
                   (unsigned long)params->battery_percent);
        } else {
          lv_obj_set_style_text_color(status_label, START_SCREEN_COLOR_RED, 0);
          snprintf(status_text, sizeof(status_text), "Battery: %lu%%\n(Not Charging)",
                   (unsigned long)params->battery_percent);
        }
        lv_label_set_text(status_label, status_text);
      }
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COUNTDOWN:
      // Update countdown value
      if (status_label) {
        char countdown_text[8];
        snprintf(countdown_text, sizeof(countdown_text), "%lu",
                 (unsigned long)params->countdown_value);
        lv_label_set_text(status_label, countdown_text);
      }
      break;

    case fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS: {
      // Recreate screen to update status (background color changes)
      screen_mfg_destroy();
      lv_obj_t* new_screen = screen_mfg_init(ctx);
      if (new_screen) {
        lv_scr_load(new_screen);
      }
      break;
    }

    default:
      // Other modes don't need updating
      break;
  }
}
