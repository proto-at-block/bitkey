#include "led_task.h"

#include "animation.h"
#include "attributes.h"
#include "ipc.h"
#include "led.h"
#include "log.h"
#include "power.h"
#include "rtos.h"
#include "secutils.h"
#include "sysevent.h"

typedef enum {
  LED_STATE_INIT = 0,
  LED_STATE_IDLE,
  LED_STATE_ANIMATE,
  LED_STATE_ANIMATE_END,
} led_task_state_t;

typedef struct {
  led_task_state_t state;
  rtos_queue_t* queue;
  const animation_t* animation;
  const animation_t* next_animation;
  const animation_t* rest_animation;
  bool started;
  size_t keyframe_idx;
  uint32_t last_advance_ms;
} led_task_priv_t;

#define LED_QUEUE_DEPTH (5ul)

static led_task_priv_t LED_TASK_DATA priv = {0};

static led_task_state_t service_ipc(void);
static led_task_state_t run_init(void);
static led_task_state_t run_idle(void);
static led_task_state_t run_animate(void);

#define ELAPSED_MS(start) (rtos_thread_systime() - start)

#define LED_SET(led, colour) \
  if (colour > 0) {          \
    led_on(led, colour);     \
  } else {                   \
    led_off(led);            \
  }

static void reset_animation() {
  priv.keyframe_idx = 0;
  priv.started = false;
  priv.last_advance_ms = 0;
}

NO_OPTIMIZE void led_thread(void* UNUSED(args)) {
  SECURE_ASSERT(rtos_thread_is_privileged() == false);

  for (;;) {
    switch (priv.state) {
      case LED_STATE_INIT:
        priv.state = run_init();
        break;
      case LED_STATE_IDLE:
        priv.state = run_idle();
        break;
      case LED_STATE_ANIMATE:
        priv.state = run_animate();
        break;
      case LED_STATE_ANIMATE_END: {
        LED_SET(LED_R, 0);
        LED_SET(LED_G, 0);
        LED_SET(LED_B, 0);
        LED_SET(LED_W, 0);
        priv.animation = NULL;
        reset_animation();
        priv.state = LED_STATE_IDLE;
      } break;
      default:
        break;
    }

    if (priv.state > LED_STATE_INIT) {
      priv.state = service_ipc();
    }
  }
}

void led_task_create(void) {
  // Create the queue before any tasks are running
  priv.queue = rtos_queue_create(led_queue, ipc_ref_t, LED_QUEUE_DEPTH);
  ASSERT(priv.queue);
  ipc_register_port(led_port, priv.queue);

  rtos_thread_t* led_thread_handle =
    rtos_thread_create(led_thread, NULL, RTOS_THREAD_PRIORITY_LOW, 1024);
  ASSERT(led_thread_handle);
}

static led_task_state_t service_ipc(void) {
  ipc_ref_t message = {0};
  if (!ipc_recv_opt(led_port, &message, (ipc_options_t){.timeout_ms = 0})) {
    return priv.state;
  }

  switch (message.tag) {
    case IPC_LED_START_ANIMATION: {
      led_start_animation_t* msg = (led_start_animation_t*)message.object;
      if (priv.state == LED_STATE_ANIMATE && msg->immediate == false) {
        priv.next_animation = animation_get(msg->animation);
      }
      reset_animation();
      priv.animation = animation_get(msg->animation);
      return LED_STATE_ANIMATE;
    } break;
    case IPC_LED_STOP_ANIMATION: {
      LED_SET(LED_R, 0);
      LED_SET(LED_G, 0);
      LED_SET(LED_B, 0);
      LED_SET(LED_W, 0);
      reset_animation();
      return LED_STATE_IDLE;
    } break;
    case IPC_LED_SET_REST_ANIMATION: {
      led_set_rest_animation_t* msg = (led_set_rest_animation_t*)message.object;
      animation_name_t name = (animation_name_t)msg->animation;
      priv.rest_animation = animation_get(name);
      return priv.state;
    } break;
    default:
      LOGE("unknown message %ld", message.tag);
  }

  return priv.state;
}

static led_task_state_t run_init(void) {
  sysevent_wait(SYSEVENT_POWER_READY, true);
  return LED_STATE_IDLE;
}

static led_task_state_t run_idle(void) {
  // NOTE: This assumes that the rest animations always loop
  if (priv.rest_animation != NULL) {
    priv.animation = priv.rest_animation;
    run_animate();
  }
  return LED_STATE_IDLE;
}

static led_task_state_t run_animate(void) {
  // Wait for an animation to start
  if (priv.animation == NULL) {
    return LED_STATE_IDLE;
  }

  // Order is important here.
  // Keyframes must be advanced, then checked if they have ended

  // Attempt to advance the keyframe, if the keyframe duration has elapsed
  uint32_t elapsed_ms = priv.started ? ELAPSED_MS(priv.last_advance_ms) : 0;
  const bool advanced =
    priv.started ? animation_keyframe_advance(priv.animation, &priv.keyframe_idx, elapsed_ms)
                 : true;

  if (advanced) {
    elapsed_ms = 0;
    priv.last_advance_ms = rtos_thread_systime();
  }

  // Check for end of an amimation, loop and reset or stop animating
  if (animation_ended(priv.animation, priv.keyframe_idx)) {
    if (priv.animation->loop) {
      // Loop the animation
      reset_animation();
      return priv.state;
    } else if (priv.next_animation != NULL) {
      // Play the next pending animation
      priv.animation = priv.next_animation;
      priv.next_animation = NULL;
      reset_animation();
      return priv.state;
    }

    return LED_STATE_ANIMATE_END;
  }

  // Run the keyframe. If the colours are updated then set the LED channels
  animation_colour_t colours = {0};
  if (animation_keyframe_run(&priv.animation->keyframes[priv.keyframe_idx], &colours, advanced,
                             elapsed_ms)) {
    LED_SET(LED_R, colours.red);
    LED_SET(LED_G, colours.green);
    LED_SET(LED_B, colours.blue);
    LED_SET(LED_W, colours.white);
    priv.started = true;
  }

  // Rate limit animation runs
  rtos_thread_sleep(10);

  return priv.state;
}
