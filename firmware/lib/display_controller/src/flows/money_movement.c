#include "display_controller.h"
#include "display_controller_internal.h"

#include <stdio.h>
#include <string.h>

// Populate screen parameters with transaction data
static void update_screen_params(display_controller_t* controller) {
  // Clear existing params
  memset(&controller->show_screen.params.money_movement, 0,
         sizeof(controller->show_screen.params.money_movement));

  // Set flow type
  controller->show_screen.params.money_movement.is_receive_flow =
    controller->nav.money_movement.is_receive_flow;

  // Populate address - Display MCU will calculate pages
  const char* address = controller->nav.money_movement.is_receive_flow
                          ? controller->nav.money_movement.receive_data.address
                          : controller->nav.money_movement.send_data.address;
  strncpy(controller->show_screen.params.money_movement.address, address,
          sizeof(controller->show_screen.params.money_movement.address) - 1);

  // Populate amount data for send flow
  if (!controller->nav.money_movement.is_receive_flow) {
    strncpy(controller->show_screen.params.money_movement.amount_sats,
            controller->nav.money_movement.send_data.amount_sats,
            sizeof(controller->show_screen.params.money_movement.amount_sats) - 1);
    strncpy(controller->show_screen.params.money_movement.fee_sats,
            controller->nav.money_movement.send_data.fee_sats,
            sizeof(controller->show_screen.params.money_movement.fee_sats) - 1);
  }

  // Note: step field no longer set - Display MCU manages all navigation
}

void display_controller_money_movement_on_enter(display_controller_t* controller,
                                                const void* entry_data) {
  (void)entry_data;

  // Transaction data was already stored by scan flow before navigation

  update_screen_params(controller);

  controller->show_screen.which_params = fwpb_display_show_screen_money_movement_tag;
}

void display_controller_money_movement_on_exit(display_controller_t* controller) {
  // Clean up transaction data
  memset(&controller->nav.money_movement, 0, sizeof(controller->nav.money_movement));
}

flow_action_result_t display_controller_money_movement_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_money_movement_on_event(display_controller_t* controller,
                                                                ui_event_type_t event,
                                                                const void* data, uint32_t len) {
  (void)controller;
  (void)data;
  (void)len;
  (void)event;

  // Transaction events are handled by scan flow before navigation
  // Data is already stored in static variables before we're entered
  return flow_result_handled();
}

flow_action_result_t display_controller_money_movement_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;
  (void)controller;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE) {
    // Verify & Sign
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL) {
    // Cancel transaction
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU) {
    // Menu button on confirm screen
    return flow_result_navigate(FLOW_MENU, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  }

  return flow_result_handled();
}
