#include "display_controller.h"
#include "display_controller_internal.h"
#include "log.h"

#include <arithmetic.h>
#include <stdio.h>
#include <string.h>

// Helper function to update screen parameters
static void update_screen_params(display_controller_t* controller) {
  controller->show_screen.params.menu_fingerprints.selected_item =
    controller->nav.fingerprint_menu.selected_item;
  controller->show_screen.params.menu_fingerprints.hit_top = false;
  controller->show_screen.params.menu_fingerprints.hit_bottom = false;

  // Copy enrolled array and labels
  controller->show_screen.params.menu_fingerprints.enrolled_count =
    ARRAY_SIZE(controller->fingerprint_enrolled);
  memcpy(controller->show_screen.params.menu_fingerprints.enrolled,
         controller->fingerprint_enrolled, sizeof(controller->fingerprint_enrolled));

  controller->show_screen.params.menu_fingerprints.labels_count =
    ARRAY_SIZE(controller->fingerprint_labels);
  for (size_t i = 0; i < ARRAY_SIZE(controller->fingerprint_labels); i++) {
    strncpy(controller->show_screen.params.menu_fingerprints.labels[i],
            controller->fingerprint_labels[i],
            sizeof(controller->show_screen.params.menu_fingerprints.labels[i]) - 1);
    controller->show_screen.params.menu_fingerprints
      .labels[i][sizeof(controller->show_screen.params.menu_fingerprints.labels[i]) - 1] = '\0';
  }

  // Set authentication animation trigger (like bounce)
  controller->show_screen.params.menu_fingerprints.show_authenticated =
    controller->nav.fingerprint_menu.show_authenticated;
  controller->show_screen.params.menu_fingerprints.authenticated_index =
    controller->nav.fingerprint_menu.authenticated_index;
}

// Helper function to handle navigation up
static flow_action_t handle_navigate_up(display_controller_t* controller) {
  controller->show_screen.params.menu_fingerprints.hit_top = false;
  controller->show_screen.params.menu_fingerprints.hit_bottom = false;

  if (controller->nav.fingerprint_menu.selected_item > 0) {
    controller->nav.fingerprint_menu.selected_item--;
    update_screen_params(controller);
    return FLOW_ACTION_REFRESH;
  }

  // At top - trigger bounce animation
  controller->show_screen.params.menu_fingerprints.hit_top = true;
  return FLOW_ACTION_REFRESH;
}

// Helper function to handle navigation down
static flow_action_t handle_navigate_down(display_controller_t* controller) {
  controller->show_screen.params.menu_fingerprints.hit_top = false;
  controller->show_screen.params.menu_fingerprints.hit_bottom = false;

  if (controller->nav.fingerprint_menu.selected_item <
      ARRAY_SIZE(controller->fingerprint_enrolled)) {  // 0=back, 1-3=fingerprints
    controller->nav.fingerprint_menu.selected_item++;
    update_screen_params(controller);
    return FLOW_ACTION_REFRESH;
  }

  // At bottom - trigger bounce animation
  controller->show_screen.params.menu_fingerprints.hit_bottom = true;
  return FLOW_ACTION_REFRESH;
}

// Helper function to handle item selection
static flow_action_t handle_select_item(display_controller_t* controller) {
  uint8_t selected = controller->nav.fingerprint_menu.selected_item;

  if (selected == 0) {
    // Back button - return to main menu
    return FLOW_ACTION_EXIT;
  }

  if (selected > 0 && selected <= ARRAY_SIZE(controller->fingerprint_enrolled)) {
    // Fingerprint slot selected (1-3)
    uint8_t fingerprint_index = selected - 1;

    // Store which fingerprint was selected for the main controller to handle
    controller->nav.fingerprint_menu.detail_index = fingerprint_index;

    // Exit the flow - the main controller will determine next action
    // based on enrollment status
    return FLOW_ACTION_EXIT;
  }

  return FLOW_ACTION_NONE;
}

void display_controller_fingerprint_menu_on_enter(display_controller_t* controller,
                                                  const void* data) {
  (void)data;

  // Menu selection logic:
  // - From detail screen (Remove): preserve selection
  // - From enrollment/scanning: select the newly enrolled fingerprint
  // - From main menu or elsewhere: start at back button
  if (controller->previous_flow == FLOW_FINGERPRINT_REMOVE) {
    // Returning from detail screen - preserve selection
  } else if (controller->previous_flow == FLOW_FINGERPRINT_MGMT) {
    // Returning from enrollment - select the newly enrolled fingerprint
    // The detail_index should contain which slot was just enrolled
    if (controller->nav.fingerprint_menu.detail_index <
        ARRAY_SIZE(controller->fingerprint_enrolled)) {
      controller->nav.fingerprint_menu.selected_item =
        controller->nav.fingerprint_menu.detail_index + 1;  // +1 because 0 is back button
    }
  } else {
    // Coming from main menu or elsewhere - start at back button
    controller->nav.fingerprint_menu.selected_item = 0;
  }

  // Set up screen params for fingerprints menu
  update_screen_params(controller);
  controller->show_screen.which_params = fwpb_display_show_screen_menu_fingerprints_tag;
}

flow_action_t display_controller_fingerprint_menu_on_button_press(
  display_controller_t* controller, const button_event_payload_t* event) {
  // Only handle single button presses
  if (event->type != BUTTON_PRESS_SINGLE) {
    return FLOW_ACTION_NONE;
  }

  switch (event->button) {
    case BUTTON_LEFT:
      return handle_navigate_up(controller);

    case BUTTON_RIGHT:
      return handle_navigate_down(controller);

    case BUTTON_BOTH:
      return handle_select_item(controller);

    default:
      return FLOW_ACTION_NONE;
  }
}

void display_controller_fingerprint_menu_on_exit(display_controller_t* controller) {
  (void)controller;
}

void display_controller_fingerprint_menu_on_tick(display_controller_t* controller) {
  (void)controller;
}
