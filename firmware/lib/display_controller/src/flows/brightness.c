#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"

#ifdef EMBEDDED_BUILD
#include "ipc.h"
#endif

#include <string.h>

void display_controller_brightness_on_enter(display_controller_t* controller,
                                            const void* entry_data) {
  (void)entry_data;

  controller->show_screen.which_params = fwpb_display_show_screen_brightness_tag;
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

flow_action_result_t display_controller_brightness_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_brightness_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT) {
    // EXIT action includes the final brightness value (0-100) in the data field
    controller->show_screen.brightness_percent = (uint8_t)data;
    // Exit returns to caller (MENU) with menu selection restored automatically
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // BACK action without brightness data (shouldn't happen with current UI)
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
