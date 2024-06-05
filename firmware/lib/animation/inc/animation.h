#pragma once

#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
  ANI_OFF,

  // Demo animations
  DEMO_PATTERN,
  PULSATE_WHITE,
  OK_GREEN,

  // Mfgtest animations
  ANI_MFGTEST_CAPTOUCH,

  // Real animations
  ANI_ENROLLMENT,
  ANI_ENROLLMENT_COMPLETE,
  ANI_ENROLLMENT_FAILED,
  ANI_FINGERPRINT_GOOD,
  ANI_FINGERPRINT_BAD,
  ANI_FINGERPRINT_SAMPLE_GOOD,
  ANI_FINGERPRINT_SAMPLE_BAD,
  ANI_FWUP_PROGRESS,
  ANI_FWUP_COMPLETE,
  ANI_FWUP_FAILED,
  ANI_REST,
  ANI_UNLOCKED,
  ANI_LOCKED,
  ANI_LOCKED_FROM_FWUP,
  ANI_LOCKED_FROM_ENROLLMENT,
  ANI_CHARGING,
  ANI_CHARGING_FINISHED,
  ANI_CHARGING_FINISHED_PERSISTENT,
  ANI_FINGER_DOWN_FROM_LOCKED,
  ANI_FINGER_DOWN_FROM_UNLOCKED,

  // End of enum
  ANI_MAX,
} animation_name_t;

typedef enum {
  ANIMATION_OFF,
  ANIMATION_SOLID,
  ANIMATION_EASE_IN,
  ANIMATION_EASE_OUT,
  ANIMATION_PULSE_IN,
  ANIMATION_PULSE_OUT,
  ANIMATION_LERP,
} animation_keyframe_type_t;

typedef struct {
  uint32_t red;
  uint32_t green;
  uint32_t blue;
  uint32_t white;
} animation_colour_t;

typedef struct {
  const animation_keyframe_type_t type;
  const animation_colour_t colour;
  const animation_colour_t colour2;  // If unused, set to COLOUR_NONE.
  const uint32_t duration_ms;
} animation_keyframe_t;

typedef struct {
  const animation_keyframe_t* keyframes;
  const size_t length;
  const bool loop;
} animation_t;

#define MAX_DUTY USHRT_MAX

const animation_t* animation_get(const animation_name_t name);
bool animation_ended(const animation_t* animation, size_t keyframe_idx);
bool animation_keyframe_advance(const animation_t* animation, size_t* keyframe_idx,
                                const uint32_t elapsed_ms);
bool animation_keyframe_run(const animation_keyframe_t* current, animation_colour_t* colours,
                            const bool advanced, const uint32_t elapsed_ms);
