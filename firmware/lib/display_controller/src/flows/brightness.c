#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"

#ifdef EMBEDDED_BUILD
#include "ipc.h"
#endif

#include <string.h>

#define MIN_BRIGHTNESS  20
#define MAX_BRIGHTNESS  100
#define BRIGHTNESS_STEP 10

void display_controller_brightness_on_enter(display_controller_t* controller, const void* data) {
  (void)data;

  controller->show_screen.which_params = fwpb_display_show_screen_brightness_tag;
}

flow_action_t display_controller_brightness_on_button_press(display_controller_t* controller,
                                                            const button_event_payload_t* event) {
  if (event->type == BUTTON_PRESS_SINGLE) {
    if (event->button == BUTTON_LEFT) {
      // Decrease brightness
      uint8_t brightness = controller->show_screen.brightness_percent;
      if (brightness > MIN_BRIGHTNESS) {
        brightness -= BRIGHTNESS_STEP;
        if (brightness < MIN_BRIGHTNESS) {
          brightness = MIN_BRIGHTNESS;
        }
        controller->show_screen.brightness_percent = brightness;
        return FLOW_ACTION_REFRESH;
      }
    } else if (event->button == BUTTON_RIGHT) {
      // Increase brightness
      uint8_t brightness = controller->show_screen.brightness_percent;
      if (brightness < MAX_BRIGHTNESS) {
        brightness += BRIGHTNESS_STEP;
        if (brightness > MAX_BRIGHTNESS) {
          brightness = MAX_BRIGHTNESS;
        }
        controller->show_screen.brightness_percent = brightness;
        return FLOW_ACTION_REFRESH;
      }
    } else if (event->button == BUTTON_BOTH) {
      // L+R to save and exit
      return FLOW_ACTION_EXIT;
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_brightness_on_exit(display_controller_t* controller) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_DATA sysinfo_set_brightness_internal_t brightness_msg;
  brightness_msg.brightness_percent = controller->show_screen.brightness_percent;
  ipc_send(sysinfo_port, &brightness_msg, sizeof(brightness_msg),
           IPC_SYSINFO_SET_BRIGHTNESS_INTERNAL);
#else
  (void)controller;
#endif
}

void display_controller_brightness_on_tick(display_controller_t* controller) {
  (void)controller;
}
