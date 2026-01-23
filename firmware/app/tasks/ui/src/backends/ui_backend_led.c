#include "animation.h"
#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "led.h"
#include "log.h"
#include "power.h"
#include "rtos.h"
#include "sysevent.h"
#include "ui_backend.h"
#include "ui_events.h"

#include <stdbool.h>
#include <stddef.h>

#define ELAPSED_MS(start) (rtos_thread_systime() - start)

#define LED_SET(led, colour) \
  if (colour > 0) {          \
    led_on(led, colour);     \
  } else {                   \
    led_off(led);            \
  }

// LED animation state
static struct {
  const animation_t* animation;
  const animation_t* next_animation;
  const animation_t* rest_animation;
  bool started;
  size_t keyframe_idx;
  uint32_t last_advance_ms;
  ui_event_type_t idle_state;
  bool initialized;
} led_state UI_TASK_DATA = {0};

typedef struct {
  ui_event_type_t event;
  animation_name_t animation;
} ui_event_animation_map_t;

static const ui_event_animation_map_t ui_event_animation_map[] = {
  {UI_EVENT_NO_IDLE, ANI_OFF},
  {UI_EVENT_IDLE, ANI_REST},
  {UI_EVENT_AUTH_SUCCESS, ANI_UNLOCKED},
  {UI_EVENT_AUTH_FAIL, ANI_FINGERPRINT_BAD},
  {UI_EVENT_AUTH_LOCKED, ANI_LOCKED},
  {UI_EVENT_AUTH_LOCKED_FROM_FWUP, ANI_LOCKED_FROM_FWUP},
  {UI_EVENT_AUTH_LOCKED_FROM_ENROLLMENT, ANI_LOCKED_FROM_ENROLLMENT},
  {UI_EVENT_ENROLLMENT_START, ANI_ENROLLMENT},
  {UI_EVENT_ENROLLMENT_PROGRESS_GOOD, ANI_FINGERPRINT_SAMPLE_GOOD},
  {UI_EVENT_ENROLLMENT_PROGRESS_BAD, ANI_FINGERPRINT_SAMPLE_BAD},
  {UI_EVENT_ENROLLMENT_COMPLETE, ANI_ENROLLMENT_COMPLETE},
  {UI_EVENT_ENROLLMENT_FAILED, ANI_ENROLLMENT_FAILED},
  {UI_EVENT_FWUP_START, ANI_FWUP_PROGRESS},
  {UI_EVENT_FWUP_PROGRESS, ANI_FWUP_PROGRESS},
  {UI_EVENT_FWUP_COMPLETE, ANI_FWUP_COMPLETE},
  {UI_EVENT_FWUP_FAILED, ANI_FWUP_FAILED},
  {UI_EVENT_CHARGING, ANI_CHARGING},
  {UI_EVENT_CHARGING_FINISHED, ANI_CHARGING_FINISHED},
  {UI_EVENT_CHARGING_FINISHED_PERSISTENT, ANI_CHARGING_FINISHED_PERSISTENT},
  {UI_EVENT_FINGER_DOWN_FROM_LOCKED, ANI_FINGER_DOWN_FROM_LOCKED},
  {UI_EVENT_FINGER_DOWN_FROM_UNLOCKED, ANI_FINGER_DOWN_FROM_UNLOCKED},
  {UI_EVENT_FINGERPRINT_GOOD, ANI_FINGERPRINT_GOOD},
  {UI_EVENT_FINGERPRINT_BAD, ANI_FINGERPRINT_BAD},
#ifdef MFGTEST
// Only show captouch LED animation in MFGTEST builds
// Note: This appears to have been broken in w1 firmware (no exti enable).
// {UI_EVENT_CAPTOUCH, ANI_MFGTEST_CAPTOUCH},
#endif
  {UI_EVENT_WIPE_STATE, ANI_REST},
};

static animation_name_t ui_event_to_animation(ui_event_type_t event) {
  for (size_t i = 0; i < ARRAY_SIZE(ui_event_animation_map); i++) {
    if (ui_event_animation_map[i].event == event) {
      return ui_event_animation_map[i].animation;
    }
  }
  return ANI_OFF;
}

static void reset_animation(void) {
  led_state.keyframe_idx = 0;
  led_state.started = false;
  led_state.last_advance_ms = 0;
}

static void stop_all_leds(void) {
  LED_SET(LED_R, 0);
  LED_SET(LED_G, 0);
  LED_SET(LED_B, 0);
  LED_SET(LED_W, 0);
}

static void run_animation_step(void) {
  // If no animation, run idle animation if available
  if (led_state.animation == NULL) {
    if (led_state.rest_animation != NULL) {
      led_state.animation = led_state.rest_animation;
    } else {
      return;
    }
  }

  // Calculate elapsed time since last keyframe advance
  uint32_t elapsed_ms = led_state.started ? ELAPSED_MS(led_state.last_advance_ms) : 0;

  // Attempt to advance the keyframe if duration has elapsed
  const bool advanced =
    led_state.started
      ? animation_keyframe_advance(led_state.animation, &led_state.keyframe_idx, elapsed_ms)
      : true;

  if (advanced) {
    elapsed_ms = 0;
    led_state.last_advance_ms = rtos_thread_systime();
  }

  // Check for end of animation
  if (animation_ended(led_state.animation, led_state.keyframe_idx)) {
    if (led_state.animation->loop) {
      // Loop the animation
      reset_animation();
    } else if (led_state.next_animation != NULL) {
      // Play the next pending animation
      led_state.animation = led_state.next_animation;
      led_state.next_animation = NULL;
      reset_animation();
    } else {
      // Animation ended, clear and go back to idle
      stop_all_leds();
      led_state.animation = NULL;
      reset_animation();
    }
    return;
  }

  // Run the current keyframe
  animation_colour_t colours = {0};
  if (animation_keyframe_run(&led_state.animation->keyframes[led_state.keyframe_idx], &colours,
                             advanced, elapsed_ms)) {
    LED_SET(LED_R, colours.red);
    LED_SET(LED_G, colours.green);
    LED_SET(LED_B, colours.blue);
    LED_SET(LED_W, colours.white);
    led_state.started = true;
  }
}

// Backend implementation functions
static void led_backend_init(void) {
  ASSERT(!led_state.initialized);

  sysevent_wait(SYSEVENT_POWER_READY, true);

  // Initialize rest animation
  led_state.rest_animation = animation_get(ANI_REST);
  ASSERT(led_state.rest_animation != NULL);

  led_state.idle_state = UI_EVENT_IDLE;
  led_state.initialized = true;
}

static void led_backend_show_event(ui_event_type_t event) {
  // Handle special clear event
  if (event == UI_EVENT_LED_CLEAR) {
    stop_all_leds();
    reset_animation();
    led_state.animation = NULL;
    return;
  }

  // Map event to animation
  animation_name_t ani_name = ui_event_to_animation(event);
  if (ani_name == ANI_OFF) {
    return;
  }

  const animation_t* animation = animation_get(ani_name);
  if (animation) {
    reset_animation();
    led_state.animation = animation;

    // Update rest animation for idle events
    if (event == UI_EVENT_IDLE) {
      led_state.rest_animation = animation;
    }
  }
}

static void led_backend_show_event_with_data(ui_event_type_t event, const uint8_t* data,
                                             uint32_t len) {
  (void)data;
  (void)len;

  // LED backend doesn't use data, just forward to regular event handler
  led_backend_show_event(event);
}

static void led_backend_set_idle_state(ui_event_type_t idle_state) {
  led_state.idle_state = idle_state;

  // Update rest animation based on idle state
  animation_name_t ani_name = ui_event_to_animation(idle_state);
  const animation_t* animation = animation_get(ani_name);
  if (animation) {
    led_state.rest_animation = animation;
  }

  LOGI("LED idle state set to: %d", idle_state);
}

static void led_backend_clear(void) {
  stop_all_leds();
  reset_animation();
  led_state.animation = NULL;
}

static void led_backend_run(void) {
  if (led_state.initialized) {
    run_animation_step();
    // Rate limit animation runs
    rtos_thread_sleep(10);
  }
}

// Backend operations table
static const ui_backend_ops_t led_backend_ops = {
  .init = led_backend_init,
  .show_event = led_backend_show_event,
  .show_event_with_data = led_backend_show_event_with_data,
  .set_idle_state = led_backend_set_idle_state,
  .clear = led_backend_clear,
  .handle_display_action = NULL,
  .run = led_backend_run,
};

// Backend registration function
const ui_backend_ops_t* ui_backend_get(void) {
  return &led_backend_ops;
}
