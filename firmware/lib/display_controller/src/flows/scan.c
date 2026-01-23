#include "display_controller.h"
#include "display_controller_internal.h"
#include "ui_events.h"

#include <string.h>

void display_controller_scan_on_enter(display_controller_t* controller, const void* entry_data) {
  (void)entry_data;

  // Set screen params to scan/tap
  controller->show_screen.which_params = fwpb_display_show_screen_scan_tag;
  controller->show_screen.params.scan.action =
    fwpb_display_params_scan_display_params_scan_action_TAP;
}

void display_controller_scan_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_scan_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)data;

  // Handle menu access from scan screen (primary entry point)
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU) {
    return flow_result_navigate(FLOW_MENU, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  // Other actions (APPROVE, CANCEL, EXIT, BACK) have no meaning on scan screen
  return flow_result_handled();
}

flow_action_result_t display_controller_scan_on_event(display_controller_t* controller,
                                                      ui_event_type_t event, const void* data,
                                                      uint32_t len) {
  // Handle transaction start events - store data in nav union then navigate
  if (event == UI_EVENT_START_SEND_TRANSACTION && data && len == sizeof(send_transaction_data_t)) {
    controller->nav.money_movement.is_receive_flow = false;
    memcpy(&controller->nav.money_movement.send_data, data, sizeof(send_transaction_data_t));
    return flow_result_navigate(FLOW_TRANSACTION, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  if (event == UI_EVENT_START_RECEIVE_TRANSACTION && data &&
      len == sizeof(receive_transaction_data_t)) {
    controller->nav.money_movement.is_receive_flow = true;
    memcpy(&controller->nav.money_movement.receive_data, data, sizeof(receive_transaction_data_t));
    return flow_result_navigate(FLOW_TRANSACTION, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  if (event == UI_EVENT_START_PRIVILEGED_ACTION) {
    if (data && len == sizeof(fwpb_display_params_privileged_action)) {
      // Store privileged action data in nav union before navigation
      memcpy(&controller->nav.privileged_action.params, data,
             sizeof(fwpb_display_params_privileged_action));
      return flow_result_navigate(FLOW_PRIVILEGED_ACTIONS,
                                  fwpb_display_transition_DISPLAY_TRANSITION_FADE);
    }
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_scan_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

const flow_handler_t scan_handler = {
  .on_enter = display_controller_scan_on_enter,
  .on_exit = display_controller_scan_on_exit,
  .on_action = display_controller_scan_on_action,
  .on_event = display_controller_scan_on_event,
  .on_tick = display_controller_scan_on_tick,
};
