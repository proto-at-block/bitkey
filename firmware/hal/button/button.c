#include "button.h"

#include "attributes.h"
#include "exti.h"
#include "log.h"
#include "mcu_gpio.h"
#include "rtos.h"

#include <string.h>

#define BUTTON_DEBOUNCE_MS       30   // Debounce time for button state changes
#define BUTTON_LONG_PRESS_MS     500  // Time to trigger long press
#define BUTTON_BOTH_WINDOW_MS    50   // Window for detecting simultaneous both-button presses
#define BUTTON_EVENT_BUFFER_SIZE 8

extern const button_config_t button_configs[HAL_BUTTON_COUNT];

// Individual button state
typedef struct {
  bool hw_state;
  bool prev_hw_state;
  uint32_t last_change_time;
  uint32_t press_start_time;
  bool long_press_sent;
} button_state_t;

// Both-button state
typedef struct {
  bool active;
  uint32_t press_start_time;
  bool long_press_sent;
} both_state_t;

// Event ring buffer
typedef struct {
  button_event_t events[BUTTON_EVENT_BUFFER_SIZE];
  uint8_t head;
  uint8_t tail;
  uint8_t count;
} event_buffer_t;

static button_state_t button_state[HAL_BUTTON_COUNT] SHARED_TASK_DATA = {0};
static both_state_t both_state SHARED_TASK_DATA = {0};
static event_buffer_t event_buffer SHARED_TASK_DATA = {0};

static void queue_event(hal_button_id_t button, button_event_type_t type, uint32_t timestamp_ms,
                        uint32_t duration_ms) {
  if (event_buffer.count >= BUTTON_EVENT_BUFFER_SIZE) {
    // Buffer full, drop oldest
    event_buffer.tail = (event_buffer.tail + 1) % BUTTON_EVENT_BUFFER_SIZE;
    event_buffer.count--;
  }

  button_event_t* event = &event_buffer.events[event_buffer.head];
  event->button = button;
  event->type = type;
  event->timestamp_ms = timestamp_ms;
  event->duration_ms = duration_ms;

  event_buffer.head = (event_buffer.head + 1) % BUTTON_EVENT_BUFFER_SIZE;
  event_buffer.count++;
}

void button_init(void) {
  memset(button_state, 0, sizeof(button_state));
  memset(&both_state, 0, sizeof(both_state));
  memset(&event_buffer, 0, sizeof(event_buffer));

  // Read initial hardware state
  for (hal_button_id_t id = 0; id < HAL_BUTTON_COUNT; id++) {
    bool pressed = !mcu_gpio_read(&button_configs[id].gpio);
    button_state[id].hw_state = pressed;
    button_state[id].prev_hw_state = pressed;
    exti_enable(&button_configs[id]);
  }
}

static void read_hardware_state(uint32_t now) {
  for (hal_button_id_t id = 0; id < HAL_BUTTON_COUNT; id++) {
    // Clear any pending interrupts
    if (exti_pending(&button_configs[id])) {
      exti_clear(&button_configs[id]);
    }

    // Read current hardware state (active low)
    bool pressed = !mcu_gpio_read(&button_configs[id].gpio);

    // Only update state if debounce time has passed since last change
    if (pressed != button_state[id].hw_state) {
      if (now - button_state[id].last_change_time >= BUTTON_DEBOUNCE_MS) {
        button_state[id].last_change_time = now;
        button_state[id].hw_state = pressed;
      }
    }
  }
}

bool button_update_state(void) {
  uint32_t now = rtos_thread_systime();
  bool any_events = false;

  // Read and debounce hardware state
  read_hardware_state(now);

  // Cache current and previous states for readability
  bool left_curr = button_state[HAL_BUTTON_LEFT].hw_state;
  bool right_curr = button_state[HAL_BUTTON_RIGHT].hw_state;
  bool left_prev = button_state[HAL_BUTTON_LEFT].prev_hw_state;
  bool right_prev = button_state[HAL_BUTTON_RIGHT].prev_hw_state;

  bool both_pressed = left_curr && right_curr;
  bool both_prev = left_prev && right_prev;

  // Handle both-button logic
  if (both_pressed && !both_state.active) {
    // Both buttons just pressed - start both-button mode
    both_state.active = true;
    both_state.press_start_time = now;
    both_state.long_press_sent = false;
    // Don't queue a press event - wait for release or long press
    // Clear individual button state to prevent spurious events
    button_state[HAL_BUTTON_LEFT].press_start_time = 0;
    button_state[HAL_BUTTON_RIGHT].press_start_time = 0;
  } else if (both_state.active && both_pressed && !both_state.long_press_sent) {
    // Both held - check for long press
    if (now - both_state.press_start_time >= BUTTON_LONG_PRESS_MS) {
      both_state.long_press_sent = true;
      queue_event(HAL_BUTTON_BOTH, BUTTON_EVENT_LONG_PRESS_START, now, BUTTON_LONG_PRESS_MS);
      any_events = true;
    }
  } else if (both_state.active && !both_pressed) {
    // Both-button mode ended - at least one released
    uint32_t duration = now - both_state.press_start_time;
    if (both_state.long_press_sent) {
      queue_event(HAL_BUTTON_BOTH, BUTTON_EVENT_LONG_PRESS_STOP, now, duration);
    } else {
      queue_event(HAL_BUTTON_BOTH, BUTTON_EVENT_SHORT_PRESS, now, duration);
    }
    both_state.active = false;
    any_events = true;
  }

  // Handle individual buttons (only if not in both-button mode)
  if (!both_state.active && !both_pressed) {
    for (hal_button_id_t id = 0; id < HAL_BUTTON_COUNT; id++) {
      bool curr = button_state[id].hw_state;
      bool prev = button_state[id].prev_hw_state;

      if (curr && !prev && !both_prev) {
        // Button pressed (and we weren't coming from both-button mode)
        button_state[id].press_start_time = now;
        button_state[id].long_press_sent = false;
        // Delay event generation to detect potential both-button press
      } else if (curr && prev && button_state[id].press_start_time > 0) {
        // Button held - check for long press
        if (!button_state[id].long_press_sent &&
            now - button_state[id].press_start_time >= BUTTON_LONG_PRESS_MS) {
          button_state[id].long_press_sent = true;
          queue_event(id, BUTTON_EVENT_LONG_PRESS_START, now, BUTTON_LONG_PRESS_MS);
          any_events = true;
        }
      } else if (!curr && prev && button_state[id].press_start_time > 0) {
        // Button released
        uint32_t duration = now - button_state[id].press_start_time;

        // Only send events for presses longer than the both-button window
        if (duration >= BUTTON_BOTH_WINDOW_MS) {
          if (button_state[id].long_press_sent) {
            queue_event(id, BUTTON_EVENT_LONG_PRESS_STOP, now, duration);
          } else {
            queue_event(id, BUTTON_EVENT_SHORT_PRESS, now, duration);
          }
          any_events = true;
        }

        button_state[id].press_start_time = 0;
        button_state[id].long_press_sent = false;
      }
    }
  }

  // Update previous states
  button_state[HAL_BUTTON_LEFT].prev_hw_state = left_curr;
  button_state[HAL_BUTTON_RIGHT].prev_hw_state = right_curr;

  return any_events;
}

bool button_get_event(button_event_t* event) {
  if (!event || event_buffer.count == 0) {
    return false;
  }

  *event = event_buffer.events[event_buffer.tail];
  event_buffer.tail = (event_buffer.tail + 1) % BUTTON_EVENT_BUFFER_SIZE;
  event_buffer.count--;

  return true;
}
