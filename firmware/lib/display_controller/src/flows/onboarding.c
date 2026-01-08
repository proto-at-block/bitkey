#include "display_controller.h"
#include "display_controller_internal.h"
#include "log.h"

#include <stdio.h>
#include <string.h>

#define ONBOARDING_PAGES 5

void display_controller_onboarding_on_enter(display_controller_t* controller, const void* data) {
  (void)data;  // Onboarding doesn't use initialization data

  // Initialize onboarding navigation state
  controller->nav.onboarding.current_page = 0;

  // Set up screen params for first page
  controller->show_screen.params.onboarding.current_page = 0;
  controller->show_screen.which_params = fwpb_display_show_screen_onboarding_tag;
}

flow_action_t display_controller_onboarding_on_button_press(display_controller_t* controller,
                                                            const button_event_payload_t* event) {
  // Only handle single button presses
  if (event->type != BUTTON_PRESS_SINGLE) {
    return FLOW_ACTION_NONE;
  }

  // L button - go to previous page
  if (event->button == BUTTON_LEFT && event->type == BUTTON_PRESS_SINGLE) {
    if (controller->nav.onboarding.current_page > 0) {
      controller->nav.onboarding.current_page--;
      controller->show_screen.params.onboarding.current_page =
        controller->nav.onboarding.current_page;
      return FLOW_ACTION_REFRESH;
    }
  }

  // R button - go to next page
  else if (event->button == BUTTON_RIGHT && event->type == BUTTON_PRESS_SINGLE) {
    if (controller->nav.onboarding.current_page < ONBOARDING_PAGES - 2) {
      controller->nav.onboarding.current_page++;
      controller->show_screen.params.onboarding.current_page =
        controller->nav.onboarding.current_page;
      return FLOW_ACTION_REFRESH;
    }
  }

  // L+R on last two pages - advance or exit onboarding
  else if (event->type == BUTTON_PRESS_SINGLE && event->button == BUTTON_BOTH) {
    if (controller->nav.onboarding.current_page == ONBOARDING_PAGES - 2) {
      // Move to final page
      controller->nav.onboarding.current_page++;
      controller->show_screen.params.onboarding.current_page =
        controller->nav.onboarding.current_page;
      return FLOW_ACTION_REFRESH;
    } else if (controller->nav.onboarding.current_page == ONBOARDING_PAGES - 1) {
      // Exit onboarding flow (completion will be saved in on_exit)
      return FLOW_ACTION_EXIT;
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_onboarding_on_exit(display_controller_t* controller) {
  (void)controller;

  // TODO: Mark onboarding as complete in key-value store
  // This ensures it won't show again on next boot
  // W-14837
}

void display_controller_onboarding_on_tick(display_controller_t* controller) {
  (void)controller;
}
