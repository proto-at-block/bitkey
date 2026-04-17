#include "display_controller.h"
#include "display_controller_internal.h"

#define ONBOARDING_COMPLETE_LOCK_TICKS MS_TO_DISPLAY_TICKS(6000)

void display_controller_onboarding_complete_on_enter(display_controller_t* controller,
                                                     const void* entry_data) {
  (void)entry_data;
  controller->show_screen.which_params = fwpb_display_show_screen_onboarding_complete_tag;
  controller->nav.onboarding_complete.lock_timer = ONBOARDING_COMPLETE_LOCK_TICKS;
}

void display_controller_onboarding_complete_on_exit(display_controller_t* controller) {
  controller->nav.onboarding_complete.lock_timer = 0;
}

flow_action_result_t display_controller_onboarding_complete_on_tick(
  display_controller_t* controller) {
  if (!controller->initial_screen_shown) {
    return flow_result_handled();
  }

  if (controller->nav.onboarding_complete.lock_timer > 0) {
    controller->nav.onboarding_complete.lock_timer--;
    if (controller->nav.onboarding_complete.lock_timer == 0) {
      return flow_result_lock();
    }
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_onboarding_complete_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)action;
  (void)data;
  return flow_result_handled();
}
