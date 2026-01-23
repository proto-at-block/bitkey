#include "display_controller.h"
#include "display_controller_internal.h"

#include <arithmetic.h>
#include <stdio.h>
#include <string.h>

// Helper function to update screen parameters
static void update_screen_params(display_controller_t* controller) {
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

void display_controller_fingerprint_menu_on_enter(display_controller_t* controller,
                                                  const void* entry_data) {
  (void)entry_data;

  controller->nav.fingerprint_menu.show_authenticated = false;

  // If coming from menu (depth==1), always start at first fingerprint slot.
  // Otherwise (depth > 1, returning from enrollment), keep the restored value from nav_stack.
  if (controller->nav_stack_depth == 1) {
    controller->nav.fingerprint_menu.selected_item = 0;
  }

  // Set up screen params for fingerprints menu
  update_screen_params(controller);

  // Pass selected item to screen for scroll restoration
  controller->show_screen.params.menu_fingerprints.initial_slot =
    controller->nav.fingerprint_menu.selected_item;

  controller->show_screen.which_params = fwpb_display_show_screen_menu_fingerprints_tag;
}

void display_controller_fingerprint_menu_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_fingerprint_menu_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_fingerprint_menu_on_event(display_controller_t* controller,
                                                                  ui_event_type_t event,
                                                                  const void* data, uint32_t len) {
  (void)data;
  (void)len;

  if (event == UI_EVENT_FINGERPRINT_DELETED) {
    // Fingerprint successfully deleted - query fresh enrollment status
    display_controller_query_fingerprint_status();
    return flow_result_handled();
  } else if (event == UI_EVENT_FINGERPRINT_STATUS) {
    // Enrollment status updated - refresh screen with new data
    controller->nav.fingerprint_menu.show_authenticated = false;
    update_screen_params(controller);
    display_controller_show_screen(controller, fwpb_display_show_screen_menu_fingerprints_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                   TRANSITION_DURATION_NONE);
    return flow_result_handled();
  } else if (event == UI_EVENT_FINGERPRINT_DELETE_FAILED) {
    // Silent failure - just stay in current state
    return flow_result_handled();
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_fingerprint_menu_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // Back button (top_back widget) - return to caller (MENU)
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT) {
    // data contains fingerprint slot index (0-2)
    uint8_t index = (uint8_t)data;

    if (index < 3) {
      // Save selection before navigation
      controller->nav.fingerprint_menu.selected_item = index;

      // Fingerprint slot selected
      if (controller->fingerprint_enrolled[index]) {
        // Enrolled slot - screen will handle showing modal for deletion
        // Single-fingerprint protection is handled at screen layer
        return flow_result_handled();
      } else {
        // Empty slot - start enrollment
        controller->nav.fingerprint.slot_index = index;
        return flow_result_navigate(FLOW_FINGERPRINT_MGMT,
                                    fwpb_display_transition_DISPLAY_TRANSITION_FADE);
      }
    }
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_DELETE_FINGERPRINT) {
    // User confirmed deletion via hold_cancel modal - trigger actual deletion
    uint8_t fingerprint_index = (uint8_t)data;
    display_controller_handle_action_delete_fingerprint(fingerprint_index);
    return flow_result_handled();
  }

  return flow_result_handled();
}
