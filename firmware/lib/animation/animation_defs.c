#include "animation.h"
#include "arithmetic.h"
#include "assert.h"

#define COLOUR(r, g, b, w) \
  { (r), (g), (b), (w), }

#define HALF_DUTY (MAX_DUTY / 2)

#define C8TOC16(c)  ((c * MAX_DUTY) / CHAR_MAX)
#define PCTTOC16(c) ((c * MAX_DUTY) / 100)

#define COLOUR_NONE   COLOUR(0, 0, 0, 0)
#define COLOUR_RED    COLOUR(PCTTOC16(100), PCTTOC16(5), 0, 0)
#define COLOUR_GREEN  COLOUR(PCTTOC16(10), PCTTOC16(100), PCTTOC16(10), 0)
#define COLOUR_BLUE   COLOUR(0, 0, MAX_DUTY, (MAX_DUTY * 0.05))
#define COLOUR_WHITE  COLOUR(0, 0, HALF_DUTY, MAX_DUTY)
#define COLOUR_PURPLE COLOUR(MAX_DUTY, 0, MAX_DUTY, 0)
#define COLOUR_ORANGE COLOUR(MAX_DUTY, (MAX_DUTY * 0.20), 0, 0)

#define COLOUR_LIGHT_BLUE COLOUR(0, (MAX_DUTY * 0.20), MAX_DUTY, 0)
#define COLOUR_YELLOW     COLOUR(MAX_DUTY, (MAX_DUTY), 0, 0)
#define COLOUR_TEAL       COLOUR(0, (MAX_DUTY), HALF_DUTY, 0)

#define COLOUR_WHITE_DIM  COLOUR(0, 0, HALF_DUTY / 3, MAX_DUTY / 3)
#define COLOUR_ORANGE_DIM COLOUR(MAX_DUTY / 2, (MAX_DUTY * 0.20) / 2, 0, 0)
#define COLOUR_GREEN_DIM  COLOUR(PCTTOC16(5), PCTTOC16(50), PCTTOC16(5), 0)

#define DEFINE_ANIMATION(name, keyframes, loop) [name] = {keyframes, ARRAY_SIZE(keyframes), loop}

#define KEYFRAME_OFF(duration) \
  { ANIMATION_OFF, COLOUR_NONE, COLOUR_NONE, duration }

#define KEYFRAME_SOLID(colour, duration) \
  { ANIMATION_SOLID, colour, COLOUR_NONE, duration }

#define KEYFRAME_EASE_IN(colour, duration) \
  { ANIMATION_EASE_IN, colour, COLOUR_NONE, duration }
#define KEYFRAME_EASE_OUT(colour, duration) \
  { ANIMATION_EASE_OUT, colour, COLOUR_NONE, duration }
#define KEYFRAME_EASE_INOUT(colour, duration)               \
  {ANIMATION_EASE_IN, colour, COLOUR_NONE, duration / 2}, { \
    ANIMATION_EASE_OUT, colour, COLOUR_NONE, duration / 2   \
  }

#define KEYFRAME_PULSE_IN(colour, duration) \
  { ANIMATION_PULSE_IN, colour, COLOUR_NONE, duration }
#define KEYFRAME_PULSE_OUT(colour, duration) \
  { ANIMATION_PULSE_OUT, colour, COLOUR_NONE, duration }
#define KEYFRAME_PULSE_INOUT(colour, duration)               \
  {ANIMATION_PULSE_IN, colour, COLOUR_NONE, duration / 2}, { \
    ANIMATION_PULSE_OUT, colour, COLOUR_NONE, duration / 2   \
  }

#define KEYFRAME_LERP(colour_from, colour_to, duration) \
  { ANIMATION_LERP, colour_from, colour_to, duration }

#define DURATION_HZ(hz)   (1000 / hz)
#define DURATION_SEC(sec) (sec * 1000)
#define DURATION_BLINK    (325)
#define DURATION_MAX      (UINT32_MAX)

static const animation_keyframe_t off[] = {
  KEYFRAME_OFF(DURATION_MAX),
};

static const animation_keyframe_t demo_keyframes[] = {
  KEYFRAME_SOLID(COLOUR_RED, DURATION_HZ(2)),
  KEYFRAME_SOLID(COLOUR_GREEN, DURATION_HZ(2)),
  KEYFRAME_SOLID(COLOUR_RED, DURATION_HZ(2)),
  KEYFRAME_SOLID(COLOUR_GREEN, DURATION_HZ(2)),
};

static const animation_keyframe_t pulsate_white_keyframes[] = {
  KEYFRAME_EASE_INOUT(COLOUR_WHITE, DURATION_SEC(2)),
};

static const animation_keyframe_t ok_not_ok[] = {
  KEYFRAME_SOLID(COLOUR_GREEN, DURATION_HZ(1)),
  KEYFRAME_SOLID(COLOUR_RED, DURATION_HZ(1)),
};

static const animation_keyframe_t purple_blink[] = {
  KEYFRAME_SOLID(COLOUR_PURPLE, DURATION_HZ(4)),
};

static const animation_keyframe_t enrollment[] = {
  KEYFRAME_SOLID(COLOUR_WHITE_DIM, DURATION_MAX),
};

static const animation_keyframe_t enrollment_complete[] = {
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_LIGHT_BLUE, DURATION_SEC(0.25)),
  // Fade into unlocked animation
  KEYFRAME_LERP(COLOUR_LIGHT_BLUE, COLOUR_GREEN, DURATION_SEC(0.75)),
  KEYFRAME_SOLID(COLOUR_GREEN, DURATION_MAX),
};

static const animation_keyframe_t enrollment_failed[] = {
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_NONE, DURATION_SEC(0.25)),
};

static const animation_keyframe_t fingerprint_good[] = {
  KEYFRAME_SOLID(COLOUR_GREEN, DURATION_HZ(1)),
};

static const animation_keyframe_t fingerprint_bad[] = {
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_RED, DURATION_SEC(0.25)),
  KEYFRAME_SOLID(COLOUR_RED, DURATION_SEC(0.75)),
  KEYFRAME_LERP(COLOUR_RED, COLOUR_NONE, DURATION_SEC(0.5)),
};

// NOTE: If changing this, you may also need to change `fingerprint_sample_bad`.
static const animation_keyframe_t fingerprint_sample_good[] = {
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_LIGHT_BLUE, DURATION_SEC(0.1)),
  KEYFRAME_SOLID(COLOUR_LIGHT_BLUE, DURATION_SEC(0.5)),
  KEYFRAME_LERP(COLOUR_LIGHT_BLUE, COLOUR_WHITE_DIM, DURATION_SEC(0.1)),
};

// Currently this is the same as `fingerprint_sample_good` -- but it might change it,
// so it's broken out.
static const animation_keyframe_t fingerprint_sample_bad[] = {
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_LIGHT_BLUE, DURATION_SEC(0.1)),
  KEYFRAME_SOLID(COLOUR_LIGHT_BLUE, DURATION_SEC(0.5)),
  KEYFRAME_LERP(COLOUR_LIGHT_BLUE, COLOUR_WHITE_DIM, DURATION_SEC(0.1)),
};

static const animation_keyframe_t fwup_progress[] = {
  KEYFRAME_SOLID(COLOUR_LIGHT_BLUE, DURATION_MAX),
};

static const animation_keyframe_t fwup_complete[] = {
  KEYFRAME_OFF(DURATION_BLINK), KEYFRAME_SOLID(COLOUR_LIGHT_BLUE, DURATION_BLINK),
  KEYFRAME_OFF(DURATION_BLINK), KEYFRAME_SOLID(COLOUR_LIGHT_BLUE, DURATION_BLINK),
  KEYFRAME_OFF(DURATION_BLINK), KEYFRAME_SOLID(COLOUR_LIGHT_BLUE, DURATION_BLINK),
};

static const animation_keyframe_t fwup_failed[] = {
  KEYFRAME_OFF(DURATION_BLINK), KEYFRAME_SOLID(COLOUR_RED, DURATION_BLINK),
  KEYFRAME_OFF(DURATION_BLINK), KEYFRAME_SOLID(COLOUR_RED, DURATION_BLINK),
  KEYFRAME_OFF(DURATION_BLINK), KEYFRAME_SOLID(COLOUR_RED, DURATION_BLINK),
};

static const animation_keyframe_t rest[] = {
  KEYFRAME_PULSE_INOUT(COLOUR_WHITE, DURATION_SEC(3)),
};

static const animation_keyframe_t unlocked[] = {
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_GREEN, DURATION_SEC(0.25)),
  KEYFRAME_SOLID(COLOUR_GREEN, DURATION_MAX),
};

static const animation_keyframe_t locked[] = {
  KEYFRAME_LERP(COLOUR_GREEN, COLOUR_NONE, DURATION_SEC(0.5)),
};

static const animation_keyframe_t locked_from_fwup[] = {
  KEYFRAME_LERP(COLOUR_LIGHT_BLUE, COLOUR_NONE, DURATION_SEC(0.5)),
};

static const animation_keyframe_t charging[] = {
  KEYFRAME_SOLID(COLOUR_ORANGE_DIM, DURATION_SEC(1.25)),
  KEYFRAME_LERP(COLOUR_ORANGE_DIM, COLOUR_NONE, DURATION_SEC(0.5)),
};

static const animation_keyframe_t charging_finished[] = {
  KEYFRAME_SOLID(COLOUR_GREEN_DIM, DURATION_SEC(1.25)),
  KEYFRAME_LERP(COLOUR_GREEN_DIM, COLOUR_NONE, DURATION_SEC(0.5)),
};

static const animation_keyframe_t charging_finished_persistent[] = {
  KEYFRAME_SOLID(COLOUR_GREEN_DIM, DURATION_MAX),
};

// Played from LED off state. Fade into white to start.
static const animation_keyframe_t finger_down_from_locked[] = {
  KEYFRAME_LERP(COLOUR_NONE, COLOUR_WHITE_DIM, DURATION_SEC(0.25)),
  KEYFRAME_SOLID(COLOUR_WHITE_DIM, DURATION_SEC(0.5)),
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_NONE, DURATION_SEC(0.25)),
};

// Played when LED is green. Fade from green to white to start.
static const animation_keyframe_t finger_down_from_unlocked[] = {
  KEYFRAME_LERP(COLOUR_GREEN, COLOUR_WHITE_DIM, DURATION_SEC(0.25)),
  KEYFRAME_SOLID(COLOUR_WHITE_DIM, DURATION_SEC(0.5)),
  KEYFRAME_LERP(COLOUR_WHITE_DIM, COLOUR_NONE, DURATION_SEC(0.25)),
};

static const animation_t animations[] = {
  DEFINE_ANIMATION(ANI_OFF, off, true),
  DEFINE_ANIMATION(DEMO_PATTERN, demo_keyframes, false),
  DEFINE_ANIMATION(PULSATE_WHITE, pulsate_white_keyframes, true),
  DEFINE_ANIMATION(OK_GREEN, ok_not_ok, true),

  DEFINE_ANIMATION(ANI_MFGTEST_CAPTOUCH, purple_blink, false),

  DEFINE_ANIMATION(ANI_ENROLLMENT, enrollment, false),
  DEFINE_ANIMATION(ANI_ENROLLMENT_COMPLETE, enrollment_complete, false),
  DEFINE_ANIMATION(ANI_ENROLLMENT_FAILED, enrollment_failed, false),
  DEFINE_ANIMATION(ANI_FINGERPRINT_GOOD, fingerprint_good, false),
  DEFINE_ANIMATION(ANI_FINGERPRINT_BAD, fingerprint_bad, false),
  DEFINE_ANIMATION(ANI_FINGERPRINT_SAMPLE_GOOD, fingerprint_sample_good, false),
  DEFINE_ANIMATION(ANI_FINGERPRINT_SAMPLE_BAD, fingerprint_sample_bad, false),
  DEFINE_ANIMATION(ANI_FWUP_PROGRESS, fwup_progress, true),
  DEFINE_ANIMATION(ANI_FWUP_COMPLETE, fwup_complete, false),
  DEFINE_ANIMATION(ANI_FWUP_FAILED, fwup_failed, false),
  DEFINE_ANIMATION(ANI_REST, rest, true),
  DEFINE_ANIMATION(ANI_UNLOCKED, unlocked, false),
  DEFINE_ANIMATION(ANI_LOCKED, locked, false),
  DEFINE_ANIMATION(ANI_LOCKED_FROM_FWUP, locked_from_fwup, false),
  DEFINE_ANIMATION(ANI_CHARGING, charging, false),
  DEFINE_ANIMATION(ANI_CHARGING_FINISHED, charging_finished, false),
  DEFINE_ANIMATION(ANI_CHARGING_FINISHED_PERSISTENT, charging_finished_persistent, false),
  DEFINE_ANIMATION(ANI_FINGER_DOWN_FROM_LOCKED, finger_down_from_locked, false),
  DEFINE_ANIMATION(ANI_FINGER_DOWN_FROM_UNLOCKED, finger_down_from_unlocked, false),
};

const animation_t* animation_get(const animation_name_t name) {
  assert(name < NAME_MAX);

  return &animations[name];
}
