#include "attributes.h"
#include "display.pb.h"
#include "display_controller.h"
#include "display_controller_internal.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

// Page states for the remove flow
#define PAGE_REMOVE  0
#define PAGE_CONFIRM 1
#define PAGE_REMOVED 2

// Timer for "removed" page (2 seconds = 200 ticks at 10ms/tick)
#define REMOVED_PAGE_TICKS 200

static UI_TASK_DATA uint32_t removed_page_timer = 0;

static void update_screen(display_controller_t* controller) {
  // Update screen params directly
  controller->show_screen.params.fingerprint_remove.fingerprint_index =
    controller->nav.fingerprint_remove.fingerprint_index;
  controller->show_screen.params.fingerprint_remove.page =
    controller->nav.fingerprint_remove.current_page;
  controller->show_screen.params.fingerprint_remove.selected_button =
    controller->nav.fingerprint_remove.selected_button;

  // Always update the same screen
  if (controller->show_screen.which_params != fwpb_display_show_screen_fingerprint_remove_tag) {
    display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_remove_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT,
                                   TRANSITION_DURATION_STANDARD);
  }
}

void display_controller_fingerprint_remove_on_enter(display_controller_t* controller,
                                                    const void* data) {
  if (data) {
    const uint8_t* fingerprint_index = (const uint8_t*)data;
    controller->nav.fingerprint_remove.fingerprint_index = *fingerprint_index;
  } else {
    controller->nav.fingerprint_remove.fingerprint_index = 0;
  }

  // Start with the first page
  controller->nav.fingerprint_remove.current_page = PAGE_REMOVE;
  controller->nav.fingerprint_remove.selected_button = 1;  // Start with remove button selected

  display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_remove_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT,
                                 TRANSITION_DURATION_STANDARD);
  update_screen(controller);
}

void display_controller_fingerprint_remove_on_exit(display_controller_t* controller) {
  // Reset timer if we're exiting
  removed_page_timer = 0;
  (void)controller;
}

flow_action_t display_controller_fingerprint_remove_on_process(
  display_controller_t* controller, const button_event_payload_t* event) {
  // Only handle single button presses
  if (event->type != BUTTON_PRESS_SINGLE) {
    return FLOW_ACTION_NONE;
  }

  uint8_t current_page = controller->nav.fingerprint_remove.current_page;

  if (current_page == PAGE_REMOVE) {
    // First page - back button and remove button
    if (event->button == BUTTON_LEFT || event->button == BUTTON_RIGHT) {
      // Toggle between buttons
      controller->nav.fingerprint_remove.selected_button =
        1 - controller->nav.fingerprint_remove.selected_button;
      update_screen(controller);
      return FLOW_ACTION_REFRESH;
    }

    if (event->button == BUTTON_BOTH) {
      if (controller->nav.fingerprint_remove.selected_button == 0) {
        // Back button - return to fingerprints menu
        return FLOW_ACTION_EXIT;
      } else {
        // Remove button - go to confirmation page
        controller->nav.fingerprint_remove.current_page = PAGE_CONFIRM;
        controller->nav.fingerprint_remove.selected_button = 1;  // Start with Back selected
        update_screen(controller);
        return FLOW_ACTION_REFRESH;
      }
    }
  } else if (current_page == PAGE_CONFIRM) {
    // Confirmation page - Yes remove and Back buttons
    if (event->button == BUTTON_LEFT || event->button == BUTTON_RIGHT) {
      // Toggle between buttons
      controller->nav.fingerprint_remove.selected_button =
        1 - controller->nav.fingerprint_remove.selected_button;
      update_screen(controller);
      return FLOW_ACTION_REFRESH;
    }

    if (event->button == BUTTON_BOTH) {
      if (controller->nav.fingerprint_remove.selected_button == 0) {
        // Yes, remove button - actually remove the fingerprint
        uint8_t index = controller->nav.fingerprint_remove.fingerprint_index;

        // Mark the fingerprint as not enrolled
        if (index < 3) {
          controller->fingerprint_enrolled[index] = false;
        }

        // Show removed page
        controller->nav.fingerprint_remove.current_page = PAGE_REMOVED;
        update_screen(controller);

        // Start the timer
        removed_page_timer = REMOVED_PAGE_TICKS;
        return FLOW_ACTION_REFRESH;
      } else {
        // Back button - go back to remove page
        controller->nav.fingerprint_remove.current_page = PAGE_REMOVE;
        controller->nav.fingerprint_remove.selected_button = 1;  // Remove button selected
        update_screen(controller);
        return FLOW_ACTION_REFRESH;
      }
    }
  } else if (current_page == PAGE_REMOVED) {
    // Removed page - no button handling, just wait for timer
    // Any button press can dismiss it early
    if (event->button == BUTTON_LEFT || event->button == BUTTON_RIGHT ||
        event->button == BUTTON_BOTH) {
      return FLOW_ACTION_EXIT;
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_fingerprint_remove_on_tick(display_controller_t* controller) {
  // Handle timer for removed page
  if (controller->nav.fingerprint_remove.current_page == PAGE_REMOVED && removed_page_timer > 0) {
    removed_page_timer--;

    if (removed_page_timer == 0) {
      // Timer expired - trigger flow exit
      controller->pending_flow_exit = true;
    }
  }
}
