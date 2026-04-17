#include "display_controller.h"
#include "display_controller_internal.h"

void display_controller_power_off_on_enter(display_controller_t* controller,
                                           const void* entry_data) {
  (void)entry_data;

  controller->show_screen.which_params = fwpb_display_show_screen_power_off_tag;
}

void display_controller_power_off_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_power_off_on_tick(display_controller_t* controller) {
  (void)controller;

  return flow_result_handled();
}

flow_action_result_t display_controller_power_off_on_event(display_controller_t* controller,
                                                           ui_event_type_t event, const void* data,
                                                           uint32_t len) {
  (void)controller;
  (void)event;
  (void)data;
  (void)len;

  return flow_result_handled();
}

flow_action_result_t display_controller_power_off_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)action;
  (void)data;

  return flow_result_handled();
}
