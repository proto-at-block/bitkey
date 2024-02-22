#include "animation.h"

#include "arithmetic.h"
#include "assert.h"
#include "log.h"

#include <math.h>

// RGB color to duty cycle.
#define C2D(color) (((double)color) / MAX_DUTY) * MAX_DUTY

// Easing below this value causes a perceivable 'jump' in the brightness of the LED.
#define MIN_EASE_COLOUR (90)

#define EASE_COLOUR(colour, progress) \
  (BLK_MAX((double)colour * progress * progress, MIN_EASE_COLOUR))

#define PULSE_IN_COLOUR(colour, progress) \
  (BLK_MAX((double)colour * progress * progress, MIN_EASE_COLOUR))

// https://easings.net/#easeInSine
#define PULSE_OUT_COLOUR(colour, progress) \
  (BLK_MAX((double)colour * (1 - cos((double)((progress * M_PI) / 2))), MIN_EASE_COLOUR))

#define LERP_COLOUR(colour_from, colour_to, progress) \
  (colour_from * (1.0 - progress) + (colour_to)*progress)

bool animation_ended(const animation_t* animation, size_t keyframe_idx) {
  if (keyframe_idx >= animation->length) {
    return true;
  }
  return false;
}

bool animation_keyframe_advance(const animation_t* animation, size_t* keyframe_idx,
                                const uint32_t elapsed_ms) {
  if (elapsed_ms >= animation->keyframes[*keyframe_idx].duration_ms) {
    (*keyframe_idx)++;
    return true;
  }
  return false;
}

bool animation_keyframe_run(const animation_keyframe_t* current, animation_colour_t* colours,
                            const bool advanced, const uint32_t elapsed_ms) {
  bool updated = false;

  if (advanced) {
    // LOGD("Starting new animation %u %lu %lu", current->type, elapsed_ms, current->duration_ms);
  }

  switch (current->type) {
    case ANIMATION_OFF: /* falls-through */
      if (advanced) {
        colours->red = 0;
        colours->green = 0;
        colours->blue = 0;
        colours->white = 0;
        updated = true;
      }
      break;
    case ANIMATION_SOLID:
      if (advanced) {
        colours->red = C2D(current->colour.red);
        colours->green = C2D(current->colour.green);
        colours->blue = C2D(current->colour.blue);
        colours->white = C2D(current->colour.white);
        updated = true;
      }
      break;

    case ANIMATION_EASE_IN: /* falls-through */
    case ANIMATION_EASE_OUT: {
      const double progress = current->type == ANIMATION_EASE_IN
                                ? (double)elapsed_ms / (double)current->duration_ms
                                : 1.0f - (double)elapsed_ms / (double)current->duration_ms;
      colours->red = C2D(EASE_COLOUR(current->colour.red, progress));
      colours->green = C2D(EASE_COLOUR(current->colour.green, progress));
      colours->blue = C2D(EASE_COLOUR(current->colour.blue, progress));
      colours->white = C2D(EASE_COLOUR(current->colour.white, progress));
      updated = true;
    } break;
    case ANIMATION_PULSE_IN: /* falls-through */
    case ANIMATION_PULSE_OUT: {
      const double progress = current->type == ANIMATION_PULSE_IN
                                ? (double)elapsed_ms / (double)current->duration_ms
                                : 1.0f - (double)elapsed_ms / (double)current->duration_ms;

      if (current->type == ANIMATION_PULSE_IN) {
        colours->red = C2D(PULSE_IN_COLOUR(current->colour.red, progress));
        colours->green = C2D(PULSE_IN_COLOUR(current->colour.green, progress));
        colours->blue = C2D(PULSE_IN_COLOUR(current->colour.blue, progress));
        colours->white = C2D(PULSE_IN_COLOUR(current->colour.white, progress));
      } else {
        colours->red = C2D(PULSE_OUT_COLOUR(current->colour.red, progress));
        colours->green = C2D(PULSE_OUT_COLOUR(current->colour.green, progress));
        colours->blue = C2D(PULSE_OUT_COLOUR(current->colour.blue, progress));
        colours->white = C2D(PULSE_OUT_COLOUR(current->colour.white, progress));
      }

      updated = true;
    } break;

    case ANIMATION_LERP: {
      const double progress = (double)elapsed_ms / (double)current->duration_ms;

      colours->red = C2D(LERP_COLOUR(current->colour.red, current->colour2.red, progress));
      colours->green = C2D(LERP_COLOUR(current->colour.green, current->colour2.green, progress));
      colours->blue = C2D(LERP_COLOUR(current->colour.blue, current->colour2.blue, progress));
      colours->white = C2D(LERP_COLOUR(current->colour.white, current->colour2.white, progress));

      updated = true;
    } break;

    default:
      break;
  }

  return updated;
}
