#include "auth.h"
#include "display_controller.h"
#include "display_controller_internal.h"

#include <string.h>

void display_controller_game_on_enter(display_controller_t* controller, const void* entry_data) {
  (void)entry_data;

  controller->show_screen.which_params = fwpb_display_show_screen_game_tag;
}

void display_controller_game_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_game_on_tick(display_controller_t* controller) {
  (void)controller;
  // The game is an easter egg reachable only through deliberate navigation; refresh the auth
  // timer unconditionally so the inactivity lock doesn't fire mid-game.
  refresh_auth();
  return flow_result_handled();
}

flow_action_result_t display_controller_game_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;
  (void)controller;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
