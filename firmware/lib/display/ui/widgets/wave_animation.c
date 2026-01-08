#include "wave_animation.h"

#include <string.h>

// Visual tuning
#define RING_COLOR    0x1CB843
#define ARC_THICKNESS 12

// Angles
#define ANGLE_CENTER    270
#define ANGLE_SPAN_BASE 55  // inner arc span in degrees
#define ANGLE_SPAN_STEP 1   // additional span per ring

// Ring sizing
#define DIAM_BASE_PX 100  // inner ring diameter
#define DIAM_STEP_PX 50   // increase diameter by this amount per ring
#define YOFF_BASE_PX -20
#define YOFF_STEP_PX 0  // 0 = concentric circles

// Animation timing
#define WAVE_IN_TIME_MS  600
#define WAVE_OUT_TIME_MS 600
#define REPEAT_DELAY_MS  400
#define PHASE_OFFSET_MS  200

// Forward declaration
static void start_wave_anim(lv_anim_t* a, void* data, uint32_t delay_ms);

static inline lv_coord_t ring_diameter(uint8_t ring) {
  return (lv_coord_t)(DIAM_BASE_PX + (int)ring * DIAM_STEP_PX);
}

static inline lv_coord_t ring_yoffset(uint8_t ring) {
  return (lv_coord_t)(YOFF_BASE_PX + (int)ring * YOFF_STEP_PX);
}

static inline int16_t ring_span(uint8_t ring) {
  return (int16_t)(ANGLE_SPAN_BASE + (int)ring * ANGLE_SPAN_STEP);
}

static void wave_anim_cb(void* var, int32_t value) {
  wave_anim_data_t* data = (wave_anim_data_t*)var;

  // Set opacity for top arc
  lv_obj_set_style_arc_opa(data->arc, (lv_opa_t)value, LV_PART_INDICATOR);

  // Keep bottom arc in sync using the parent_animation pointer
  if (data->parent_animation) {
    lv_obj_t* bottom = data->parent_animation->arc_bottom[data->ring_index];
    if (bottom) {
      lv_obj_set_style_arc_opa(bottom, (lv_opa_t)value, LV_PART_INDICATOR);
    }
  }
}

static lv_obj_t* create_arc(lv_obj_t* parent, lv_coord_t diameter, int16_t start_deg,
                            int16_t end_deg) {
  lv_obj_t* arc = lv_arc_create(parent);
  lv_obj_set_size(arc, diameter, diameter);

  lv_arc_set_mode(arc, LV_ARC_MODE_NORMAL);
  lv_arc_set_bg_angles(arc, 0, 360);
  lv_arc_set_angles(arc, start_deg, end_deg);

  lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
  lv_obj_set_style_arc_opa(arc, 0, LV_PART_MAIN);
  lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE);

  lv_obj_set_style_arc_color(arc, lv_color_hex(RING_COLOR), LV_PART_INDICATOR);
  lv_obj_set_style_arc_width(arc, ARC_THICKNESS, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(arc, 0, LV_PART_INDICATOR);

  return arc;
}

static void start_wave_anim(lv_anim_t* a, void* data, uint32_t delay_ms) {
  lv_anim_init(a);
  lv_anim_set_var(a, data);
  lv_anim_set_values(a, 0, LV_OPA_COVER);
  lv_anim_set_time(a, WAVE_IN_TIME_MS);
  lv_anim_set_delay(a, delay_ms);
  lv_anim_set_exec_cb(a, wave_anim_cb);
  lv_anim_set_path_cb(a, lv_anim_path_ease_out);
  lv_anim_set_playback_time(a, WAVE_OUT_TIME_MS);
  lv_anim_set_playback_delay(a, 0);
  lv_anim_set_repeat_count(a, LV_ANIM_REPEAT_INFINITE);
  lv_anim_set_repeat_delay(a, REPEAT_DELAY_MS);
  lv_anim_start(a);
}

lv_obj_t* wave_animation_create(lv_obj_t* parent, wave_animation_t* animation) {
  if (!animation || animation->is_initialized) {
    return NULL;
  }

  memset(animation, 0, sizeof(wave_animation_t));
  animation->parent = parent;

  for (uint8_t ring = 0; ring < WAVE_ANIMATION_NUM_RINGS; ++ring) {
    const lv_coord_t diam = ring_diameter(ring);
    const lv_coord_t yoff = ring_yoffset(ring);
    const int16_t span = ring_span(ring);

    // Top arc
    const int16_t t_start = ANGLE_CENTER - span / 2;
    const int16_t t_end = ANGLE_CENTER + span / 2;
    animation->arc_top[ring] = create_arc(parent, diam, t_start, t_end);
    lv_obj_align(animation->arc_top[ring], LV_ALIGN_CENTER, 0, yoff);

    // Bottom arc: mirrored +180°
    const int16_t b_start = (t_start + 180) % 360;
    const int16_t b_end = (t_end + 180) % 360;
    animation->arc_bottom[ring] = create_arc(parent, diam, b_start, b_end);
    lv_obj_align(animation->arc_bottom[ring], LV_ALIGN_CENTER, 0, -yoff);

    // Prepare animation data
    animation->anim_data[ring].arc = animation->arc_top[ring];
    animation->anim_data[ring].ring_index = ring;
    animation->anim_data[ring].parent_animation = animation;
  }

  animation->is_initialized = true;
  return parent;
}

void wave_animation_start(wave_animation_t* animation) {
  if (!animation || !animation->is_initialized || animation->is_animating) {
    return;
  }

  for (uint8_t ring = 0; ring < WAVE_ANIMATION_NUM_RINGS; ++ring) {
    start_wave_anim(&animation->wave_anims[ring], &animation->anim_data[ring],
                    (uint32_t)(ring * PHASE_OFFSET_MS));
  }

  animation->is_animating = true;
}

void wave_animation_stop(wave_animation_t* animation) {
  if (!animation || !animation->is_initialized || !animation->is_animating) {
    return;
  }

  for (int i = 0; i < WAVE_ANIMATION_NUM_RINGS; ++i) {
    lv_anim_del(&animation->anim_data[i], wave_anim_cb);
  }

  animation->is_animating = false;
}

void wave_animation_destroy(wave_animation_t* animation) {
  if (!animation || !animation->is_initialized) {
    return;
  }

  wave_animation_stop(animation);

  for (int i = 0; i < WAVE_ANIMATION_NUM_RINGS; ++i) {
    animation->arc_top[i] = NULL;
    animation->arc_bottom[i] = NULL;
  }

  animation->is_initialized = false;

  // Clear for safety
  memset(animation, 0, sizeof(wave_animation_t));
}
