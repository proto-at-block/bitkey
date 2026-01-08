#pragma once
#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

#define WAVE_ANIMATION_NUM_RINGS 3

// Forward declaration
typedef struct wave_animation_s wave_animation_t;

typedef struct {
  lv_obj_t* arc;
  uint8_t ring_index;
  wave_animation_t* parent_animation;
} wave_anim_data_t;

struct wave_animation_s {
  lv_obj_t* parent;
  lv_obj_t* arc_top[WAVE_ANIMATION_NUM_RINGS];
  lv_obj_t* arc_bottom[WAVE_ANIMATION_NUM_RINGS];

  lv_anim_t wave_anims[WAVE_ANIMATION_NUM_RINGS];
  wave_anim_data_t anim_data[WAVE_ANIMATION_NUM_RINGS];

  bool is_initialized;
  bool is_animating;
};

lv_obj_t* wave_animation_create(lv_obj_t* parent, wave_animation_t* animation);
void wave_animation_start(wave_animation_t* animation);
void wave_animation_stop(wave_animation_t* animation);
void wave_animation_destroy(wave_animation_t* animation);
