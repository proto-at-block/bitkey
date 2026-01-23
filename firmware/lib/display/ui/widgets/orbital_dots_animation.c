#include "orbital_dots_animation.h"

#include "secure_rng.h"

#include <math.h>
#include <string.h>

// Display configuration
#define DISPLAY_CENTER_X 233
#define DISPLAY_CENTER_Y 233

// Background dots - concentric circles with proportional dot count
#define BG_DOT_SIZE           3
#define BG_DOT_COLOR          0x555555
#define BG_DOT_RADIUS_START   60     // Radius of innermost circle
#define BG_DOT_RADIUS_STEP    20     // Increase per circle
#define BG_DOT_NUM_CIRCLES    8      // Number of concentric circles
#define BG_DOT_TARGET_SPACING 20.0f  // Target spacing between dots in pixels
#define BG_DOT_MIN_PER_CIRCLE 6      // Minimum dots per circle

// Orbital dots
#define ORBITAL_DOT_SIZE         14
#define ORBITAL_DOT_COLOR        0xD1FB96
#define ORBITAL_DOT_MIN_SPEED    0.2f  // degrees per frame (slower)
#define ORBITAL_DOT_MAX_SPEED    0.8f  // degrees per frame (slower)
#define ORBITAL_DOT_SKIP_INNER   2     // Skip first N circles for orbital dots
#define ORBITAL_DOT_ACTIVE_RINGS 6     // Number of outer rings to use for orbital dots

// Animation
#define ANIMATION_UPDATE_INTERVAL_MS 16  // 60 FPS

// Forward declarations
static void animation_timer_cb(lv_timer_t* timer);

// Helper functions for random initialization
static inline float random_speed(void) {
  uint16_t rand_val = crypto_rand_short();
  float normalized = (float)(rand_val % 1000) / 1000.0f;  // 0.0-1.0
  return ORBITAL_DOT_MIN_SPEED + (normalized * (ORBITAL_DOT_MAX_SPEED - ORBITAL_DOT_MIN_SPEED));
}

static inline float random_angle(void) {
  uint16_t rand_val = crypto_rand_short();
  return (float)(rand_val % 360);
}

// Create background dots in concentric circles with proportional dot counts
static void create_background_dots(lv_obj_t* parent, orbital_dots_animation_t* anim) {
  int dot_count = 0;

  // Create dots on each concentric circle
  // Outer circles have more dots proportional to their circumference
  for (uint8_t circle = 0; circle < BG_DOT_NUM_CIRCLES; circle++) {
    uint16_t radius = BG_DOT_RADIUS_START + (circle * BG_DOT_RADIUS_STEP);

    // Calculate number of dots for this circle based on circumference
    // More dots on larger circles to maintain uniform appearance
    float circumference = 2.0f * M_PI * radius;
    uint16_t num_dots_on_circle = (uint16_t)(circumference / BG_DOT_TARGET_SPACING);
    if (num_dots_on_circle < BG_DOT_MIN_PER_CIRCLE) {
      num_dots_on_circle = BG_DOT_MIN_PER_CIRCLE;
    }

    float angle_step = 360.0f / num_dots_on_circle;

    // Place dots around this circle
    for (uint16_t i = 0; i < num_dots_on_circle; i++) {
      if (dot_count >= ORBITAL_DOTS_NUM_BACKGROUND) {
        break;
      }

      float angle_deg = i * angle_step;
      float angle_rad = angle_deg * (M_PI / 180.0f);
      int32_t x = DISPLAY_CENTER_X + (int32_t)(cosf(angle_rad) * radius);
      int32_t y = DISPLAY_CENTER_Y + (int32_t)(sinf(angle_rad) * radius);

      // Create dot
      lv_obj_t* dot = lv_obj_create(parent);
      lv_obj_set_size(dot, BG_DOT_SIZE, BG_DOT_SIZE);
      lv_obj_set_pos(dot, x - (BG_DOT_SIZE / 2), y - (BG_DOT_SIZE / 2));
      lv_obj_set_style_bg_color(dot, lv_color_hex(BG_DOT_COLOR), 0);
      lv_obj_set_style_radius(dot, LV_RADIUS_CIRCLE, 0);
      lv_obj_set_style_border_width(dot, 0, 0);
      lv_obj_clear_flag(dot, LV_OBJ_FLAG_CLICKABLE);
      lv_obj_add_flag(dot, LV_OBJ_FLAG_HIDDEN);

      anim->bg_dots[dot_count++] = dot;
    }

    if (dot_count >= ORBITAL_DOTS_NUM_BACKGROUND) {
      break;
    }
  }
}

// Create orbital dots - one per outer track
static void create_orbital_dots(lv_obj_t* parent, orbital_dots_animation_t* anim) {
  for (uint8_t i = 0; i < ORBITAL_DOTS_NUM_ANIMATED; i++) {
    // Create dot
    lv_obj_t* dot = lv_obj_create(parent);
    lv_obj_set_size(dot, ORBITAL_DOT_SIZE, ORBITAL_DOT_SIZE);
    lv_obj_set_style_bg_color(dot, lv_color_hex(ORBITAL_DOT_COLOR), 0);
    lv_obj_set_style_radius(dot, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_border_width(dot, 0, 0);
    lv_obj_clear_flag(dot, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_flag(dot, LV_OBJ_FLAG_HIDDEN);

    // Assign one dot to each outer track (skip inner 2 circles)
    anim->orbital_dots[i] = dot;
    uint8_t circle_index = ORBITAL_DOT_SKIP_INNER + i;  // One per outer circle
    anim->orbital_radii[i] = BG_DOT_RADIUS_START + (circle_index * BG_DOT_RADIUS_STEP);
    anim->orbital_speeds[i] = random_speed();
    anim->orbital_angles[i] = random_angle();

    // Set initial position
    float angle_rad = anim->orbital_angles[i] * (M_PI / 180.0f);
    int32_t x = DISPLAY_CENTER_X + (int32_t)(cosf(angle_rad) * anim->orbital_radii[i]);
    int32_t y = DISPLAY_CENTER_Y + (int32_t)(sinf(angle_rad) * anim->orbital_radii[i]);
    lv_obj_set_pos(dot, x - (ORBITAL_DOT_SIZE / 2), y - (ORBITAL_DOT_SIZE / 2));
  }
}

// Animation timer callback - updates orbital dot positions
static void animation_timer_cb(lv_timer_t* timer) {
  orbital_dots_animation_t* anim = (orbital_dots_animation_t*)lv_timer_get_user_data(timer);

  if (!anim || !anim->is_animating) {
    return;
  }

  for (uint8_t i = 0; i < ORBITAL_DOTS_NUM_ANIMATED; i++) {
    if (!anim->orbital_dots[i]) {
      continue;
    }

    // Update angle
    anim->orbital_angles[i] += anim->orbital_speeds[i];
    if (anim->orbital_angles[i] >= 360.0f) {
      anim->orbital_angles[i] -= 360.0f;
    }

    // Calculate position on circular path
    float angle_rad = anim->orbital_angles[i] * (M_PI / 180.0f);
    int32_t x = DISPLAY_CENTER_X + (int32_t)(cosf(angle_rad) * anim->orbital_radii[i]);
    int32_t y = DISPLAY_CENTER_Y + (int32_t)(sinf(angle_rad) * anim->orbital_radii[i]);

    // Update position
    lv_obj_set_pos(anim->orbital_dots[i], x - (ORBITAL_DOT_SIZE / 2), y - (ORBITAL_DOT_SIZE / 2));
  }
}

// Public API implementation

lv_obj_t* orbital_dots_animation_create(lv_obj_t* parent, orbital_dots_animation_t* animation) {
  if (!animation || animation->is_initialized) {
    return NULL;
  }

  memset(animation, 0, sizeof(orbital_dots_animation_t));
  animation->parent = parent;

  // Create background dots in concentric circles
  create_background_dots(parent, animation);

  // Create orbital dots
  create_orbital_dots(parent, animation);

  animation->is_initialized = true;
  return parent;
}

void orbital_dots_animation_start(orbital_dots_animation_t* animation) {
  if (!animation || !animation->is_initialized || animation->is_animating) {
    return;
  }

  // Show orbital dots
  for (uint8_t i = 0; i < ORBITAL_DOTS_NUM_ANIMATED; i++) {
    if (animation->orbital_dots[i]) {
      lv_obj_clear_flag(animation->orbital_dots[i], LV_OBJ_FLAG_HIDDEN);
    }
  }

  // Create update timer
  animation->update_timer =
    lv_timer_create(animation_timer_cb, ANIMATION_UPDATE_INTERVAL_MS, animation);

  animation->is_animating = true;
}

void orbital_dots_animation_stop(orbital_dots_animation_t* animation) {
  if (!animation || !animation->is_initialized || !animation->is_animating) {
    return;
  }

  // Delete update timer
  if (animation->update_timer) {
    lv_timer_del(animation->update_timer);
    animation->update_timer = NULL;
  }

  animation->is_animating = false;
}

void orbital_dots_animation_destroy(orbital_dots_animation_t* animation) {
  if (!animation || !animation->is_initialized) {
    return;
  }

  // Stop animation first
  orbital_dots_animation_stop(animation);

  // Clear object references (LVGL auto-deletes children when parent deleted)
  for (int i = 0; i < ORBITAL_DOTS_NUM_BACKGROUND; i++) {
    animation->bg_dots[i] = NULL;
  }
  for (int i = 0; i < ORBITAL_DOTS_NUM_ANIMATED; i++) {
    animation->orbital_dots[i] = NULL;
  }

  animation->is_initialized = false;
  memset(animation, 0, sizeof(orbital_dots_animation_t));
}
