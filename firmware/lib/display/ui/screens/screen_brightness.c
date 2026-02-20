#include "screen_brightness.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "top_back.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Forward declarations
static void slider_drag_handler(lv_event_t* e);
static void update_brightness_display(uint8_t brightness);
static void back_button_click_handler(lv_event_t* e);

#define SCREEN_BRIGHTNESS  100
#define DEFAULT_BRIGHTNESS 50
#define BRIGHTNESS_MIN     15   // Minimum brightness
#define BRIGHTNESS_MAX     100  // Maximum brightness

// Slider layout configuration
#define SLIDER_WIDTH  400
#define SLIDER_HEIGHT 120
#define SLIDER_RADIUS 100  // Fully rounded ends (half of height)

// Knob configuration
#define KNOB_SIZE 100

// Slider vertical offset from center (positive = down)
#define SLIDER_Y_OFFSET 0

// Colors
#define COLOR_SLIDER_BG     0x333333
#define COLOR_SLIDER_ACTIVE 0xFFFFFF

#define FONT_PERCENTAGE (&cash_sans_mono_regular_48)

// External image declarations
extern const lv_img_dsc_t brightness_min;
extern const lv_img_dsc_t brightness_mid;
extern const lv_img_dsc_t brightness_max;

// Brightness thresholds for icon changes
#define BRIGHTNESS_ICON_MID_THRESHOLD 40  // Switch to mid icon above this value
#define BRIGHTNESS_ICON_MAX_THRESHOLD 70  // Switch to max icon above this value

static lv_obj_t* screen = NULL;
static lv_obj_t* slider_container = NULL;
static lv_obj_t* slider_bg = NULL;
static lv_obj_t* slider_active = NULL;
static lv_obj_t* sun_knob = NULL;
static lv_obj_t* icon_min = NULL;
static lv_obj_t* icon_mid = NULL;
static lv_obj_t* icon_max = NULL;
static top_back_t back_button;
static uint8_t current_brightness = DEFAULT_BRIGHTNESS;

static void back_button_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT,
                        current_brightness);
  }
}

// Convert touch X position to brightness percentage
static uint8_t touch_x_to_brightness(lv_coord_t touch_x) {
  // Clamp to slider bounds
  if (touch_x < 0)
    touch_x = 0;
  if (touch_x > SLIDER_WIDTH)
    touch_x = SLIDER_WIDTH;

  // Map position to brightness (BRIGHTNESS_MIN to BRIGHTNESS_MAX)
  int brightness = BRIGHTNESS_MIN + ((touch_x * (BRIGHTNESS_MAX - BRIGHTNESS_MIN)) / SLIDER_WIDTH);

  if (brightness < BRIGHTNESS_MIN)
    brightness = BRIGHTNESS_MIN;
  if (brightness > BRIGHTNESS_MAX)
    brightness = BRIGHTNESS_MAX;

  return (uint8_t)brightness;
}

// Convert brightness to slider width
static lv_coord_t brightness_to_width(uint8_t brightness) {
  // At minimum brightness, width equals height to form a circle
  // Map brightness (BRIGHTNESS_MIN to BRIGHTNESS_MAX) to width (SLIDER_HEIGHT to SLIDER_WIDTH)
  if (brightness <= BRIGHTNESS_MIN)
    return SLIDER_HEIGHT;  // Circle when at minimum
  if (brightness >= BRIGHTNESS_MAX)
    return SLIDER_WIDTH;

  lv_coord_t width =
    SLIDER_HEIGHT + (((brightness - BRIGHTNESS_MIN) * (SLIDER_WIDTH - SLIDER_HEIGHT)) /
                     (BRIGHTNESS_MAX - BRIGHTNESS_MIN));
  if (width < SLIDER_HEIGHT)
    width = SLIDER_HEIGHT;
  return width;
}

// Convert brightness to knob X position (centered within the active area)
static lv_coord_t brightness_to_knob_x(uint8_t brightness) {
  lv_coord_t width = brightness_to_width(brightness);
  // At minimum, knob is centered in the circle
  // Otherwise, knob is at the right edge of active area
  if (brightness <= BRIGHTNESS_MIN) {
    return (SLIDER_HEIGHT - KNOB_SIZE) / 2;  // Centered in the circle
  }
  return width - KNOB_SIZE - (SLIDER_HEIGHT - KNOB_SIZE) / 2;
}

// Track drag start position
static lv_coord_t drag_start_x = 0;
static uint8_t drag_start_brightness = 0;

static void knob_drag_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSED) {
    // Store initial drag position and brightness
    lv_indev_t* indev = lv_indev_get_act();
    if (!indev)
      return;

    lv_point_t point;
    lv_indev_get_point(indev, &point);
    drag_start_x = point.x;
    drag_start_brightness = current_brightness;

  } else if (code == LV_EVENT_PRESSING) {
    lv_indev_t* indev = lv_indev_get_act();
    if (!indev || !slider_container) {
      return;
    }

    lv_point_t point;
    lv_indev_get_point(indev, &point);

    // Calculate drag delta
    lv_coord_t delta_x = point.x - drag_start_x;

    // Convert delta to brightness change
    // Full slider width corresponds to full brightness range
    int brightness_delta =
      (delta_x * (BRIGHTNESS_MAX - BRIGHTNESS_MIN)) / (SLIDER_WIDTH - SLIDER_HEIGHT);
    int new_brightness = (int)drag_start_brightness + brightness_delta;

    // Clamp to valid range
    if (new_brightness < BRIGHTNESS_MIN)
      new_brightness = BRIGHTNESS_MIN;
    if (new_brightness > BRIGHTNESS_MAX)
      new_brightness = BRIGHTNESS_MAX;

    if ((uint8_t)new_brightness != current_brightness) {
      current_brightness = (uint8_t)new_brightness;
      update_brightness_display(current_brightness);
      ui_set_brightness(current_brightness);
    }
  }
}

// Icon fade animation duration in milliseconds
#define ICON_FADE_DURATION_MS 150

// Track which icon level is currently shown (0=min, 1=mid, 2=max)
static int current_icon_level = -1;

// Get icon level based on brightness (0=min, 1=mid, 2=max)
static int get_icon_level(uint8_t brightness) {
  if (brightness > BRIGHTNESS_ICON_MAX_THRESHOLD) {
    return 2;  // max
  } else if (brightness > BRIGHTNESS_ICON_MID_THRESHOLD) {
    return 1;  // mid
  } else {
    return 0;  // min
  }
}

// Animation callback wrapper for setting image opacity
static void anim_img_opa_cb(void* var, int32_t value) {
  lv_obj_set_style_img_opa((lv_obj_t*)var, (lv_opa_t)value, 0);
}

// Animate opacity of an icon
static void animate_icon_opacity(lv_obj_t* icon, lv_opa_t target_opa) {
  if (!icon)
    return;

  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, icon);
  lv_anim_set_values(&anim, lv_obj_get_style_img_opa(icon, 0), target_opa);
  lv_anim_set_duration(&anim, ICON_FADE_DURATION_MS);
  lv_anim_set_exec_cb(&anim, anim_img_opa_cb);
  lv_anim_start(&anim);
}

// Update icon opacities based on brightness level
static void update_icon_opacities(uint8_t brightness) {
  int new_level = get_icon_level(brightness);

  if (new_level == current_icon_level)
    return;

  current_icon_level = new_level;

  // Instantly set opacities - active icon visible, others hidden
  // This avoids flash from crossfade where both are semi-transparent
  if (icon_min) {
    lv_obj_set_style_img_opa(icon_min, (new_level == 0) ? LV_OPA_COVER : LV_OPA_TRANSP, 0);
  }
  if (icon_mid) {
    lv_obj_set_style_img_opa(icon_mid, (new_level == 1) ? LV_OPA_COVER : LV_OPA_TRANSP, 0);
  }
  if (icon_max) {
    lv_obj_set_style_img_opa(icon_max, (new_level == 2) ? LV_OPA_COVER : LV_OPA_TRANSP, 0);
  }
}

static void update_brightness_display(uint8_t brightness) {
  // Update active area width
  if (slider_active) {
    lv_coord_t width = brightness_to_width(brightness);
    lv_obj_set_width(slider_active, width);
  }

  // Update knob position
  if (sun_knob) {
    lv_coord_t knob_x = brightness_to_knob_x(brightness);
    lv_obj_set_x(sun_knob, knob_x);
  }

  // Update icon opacities based on brightness level
  update_icon_opacities(brightness);
}

lv_obj_t* screen_brightness_init(void* ctx) {
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(screen, &back_button, back_button_click_handler);

  uint8_t init_brightness = show_screen ? show_screen->brightness_percent : DEFAULT_BRIGHTNESS;
  current_brightness = init_brightness;

  // Create slider container (centered on screen)
  slider_container = lv_obj_create(screen);
  if (!slider_container) {
    return NULL;
  }
  lv_obj_set_size(slider_container, SLIDER_WIDTH, SLIDER_HEIGHT);
  lv_obj_align(slider_container, LV_ALIGN_CENTER, 0, SLIDER_Y_OFFSET);
  lv_obj_set_style_bg_opa(slider_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(slider_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(slider_container, 0, 0);
  lv_obj_clear_flag(slider_container, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_clear_flag(slider_container, LV_OBJ_FLAG_CLICKABLE);

  // Slider background bar (dark grey, full width)
  slider_bg = lv_obj_create(slider_container);
  if (!slider_bg) {
    return NULL;
  }
  lv_obj_set_size(slider_bg, SLIDER_WIDTH, SLIDER_HEIGHT);
  lv_obj_set_pos(slider_bg, 0, 0);
  lv_obj_set_style_radius(slider_bg, SLIDER_RADIUS, 0);
  lv_obj_set_style_bg_color(slider_bg, lv_color_hex(COLOR_SLIDER_BG), 0);
  lv_obj_set_style_bg_opa(slider_bg, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(slider_bg, LV_OPA_TRANSP, 0);
  lv_obj_clear_flag(slider_bg, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(slider_bg, LV_OBJ_FLAG_SCROLLABLE);

  // Slider active bar (white at 50% opacity, from left to current position)
  lv_coord_t initial_width = brightness_to_width(current_brightness);
  slider_active = lv_obj_create(slider_container);
  if (!slider_active) {
    return NULL;
  }
  lv_obj_set_size(slider_active, initial_width, SLIDER_HEIGHT);
  lv_obj_set_pos(slider_active, 0, 0);
  lv_obj_set_style_radius(slider_active, SLIDER_RADIUS, 0);
  lv_obj_set_style_bg_color(slider_active, lv_color_hex(COLOR_SLIDER_ACTIVE), 0);
  lv_obj_set_style_bg_opa(slider_active, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(slider_active, LV_OPA_TRANSP, 0);
  lv_obj_clear_flag(slider_active, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(slider_active, LV_OBJ_FLAG_SCROLLABLE);

  // Sun knob (circular, same color/opacity as active slider so it blends in, with icon)
  sun_knob = lv_obj_create(slider_container);
  if (!sun_knob) {
    return NULL;
  }
  lv_obj_set_size(sun_knob, KNOB_SIZE, KNOB_SIZE);
  lv_coord_t knob_x = brightness_to_knob_x(current_brightness);
  lv_coord_t knob_y = (SLIDER_HEIGHT - KNOB_SIZE) / 2;
  lv_obj_set_pos(sun_knob, knob_x, knob_y);
  lv_obj_set_style_radius(sun_knob, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_opa(sun_knob, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(sun_knob, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(sun_knob, 0, 0);
  lv_obj_add_flag(sun_knob, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(sun_knob, LV_OBJ_FLAG_SCROLLABLE);

  // Add drag handlers to knob
  lv_obj_add_event_cb(sun_knob, knob_drag_handler, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(sun_knob, knob_drag_handler, LV_EVENT_PRESSING, NULL);

  // Create all three brightness icons stacked on top of each other
  // Bottom layer: min icon
  icon_min = lv_img_create(sun_knob);
  if (icon_min) {
    lv_img_set_src(icon_min, &brightness_min);
    lv_obj_set_style_bg_opa(icon_min, LV_OPA_TRANSP, 0);
    lv_obj_center(icon_min);
  }

  // Middle layer: mid icon
  icon_mid = lv_img_create(sun_knob);
  if (icon_mid) {
    lv_img_set_src(icon_mid, &brightness_mid);
    lv_obj_set_style_bg_opa(icon_mid, LV_OPA_TRANSP, 0);
    lv_obj_center(icon_mid);
  }

  // Top layer: max icon
  icon_max = lv_img_create(sun_knob);
  if (icon_max) {
    lv_img_set_src(icon_max, &brightness_max);
    lv_obj_set_style_bg_opa(icon_max, LV_OPA_TRANSP, 0);
    lv_obj_center(icon_max);
  }

  // Set initial opacities based on current brightness (no animation)
  int initial_level = get_icon_level(current_brightness);
  current_icon_level = initial_level;
  lv_obj_set_style_img_opa(icon_min, (initial_level == 0) ? LV_OPA_COVER : LV_OPA_TRANSP, 0);
  lv_obj_set_style_img_opa(icon_mid, (initial_level == 1) ? LV_OPA_COVER : LV_OPA_TRANSP, 0);
  lv_obj_set_style_img_opa(icon_max, (initial_level == 2) ? LV_OPA_COVER : LV_OPA_TRANSP, 0);

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
  slider_container = NULL;
  slider_bg = NULL;
  slider_active = NULL;
  sun_knob = NULL;
  icon_min = NULL;
  icon_mid = NULL;
  icon_max = NULL;
  current_brightness = DEFAULT_BRIGHTNESS;
  current_icon_level = -1;
}

void screen_brightness_update(void* ctx) {
  if (!screen) {
    screen_brightness_init(ctx);
    return;
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen) {
    current_brightness = show_screen->brightness_percent;
    update_brightness_display(current_brightness);
  }
}
