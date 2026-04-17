#include "display_controller.h"
#include "display_controller_internal.h"

#include <arithmetic.h>
#include <stdio.h>
#include <string.h>

// Map visual position to raw slot index. Enrolled slots appear first, then empty.
static uint8_t visual_to_slot(const bool enrolled[FINGERPRINT_SLOT_COUNT], uint8_t visual) {
  uint8_t pos = 0;
  for (uint8_t s = 0; s < FINGERPRINT_SLOT_COUNT; s++) {
    if (enrolled[s]) {
      if (pos == visual)
        return s;
      pos++;
    }
  }
  for (uint8_t s = 0; s < FINGERPRINT_SLOT_COUNT; s++) {
    if (!enrolled[s]) {
      if (pos == visual)
        return s;
      pos++;
    }
  }
  return visual;
}

// Map raw slot index to visual position.
static uint8_t slot_to_visual(const bool enrolled[FINGERPRINT_SLOT_COUNT], uint8_t slot) {
  if (slot >= FINGERPRINT_SLOT_COUNT) {
    return slot;
  }
  uint8_t visual = 0;
  if (enrolled[slot]) {
    for (uint8_t s = 0; s < slot; s++) {
      if (enrolled[s])
        visual++;
    }
  } else {
    for (uint8_t s = 0; s < FINGERPRINT_SLOT_COUNT; s++) {
      if (enrolled[s])
        visual++;
    }
    for (uint8_t s = 0; s < slot; s++) {
      if (!enrolled[s])
        visual++;
    }
  }
  return visual;
}

// Reorders fingerprints so enrolled slots come first, followed by empty slots.
static void update_screen_params(display_controller_t* controller) {
  controller->show_screen.params.menu_fingerprints.enrolled_count =
    ARRAY_SIZE(controller->fingerprint_enrolled);
  controller->show_screen.params.menu_fingerprints.labels_count =
    ARRAY_SIZE(controller->fingerprint_labels);

  for (uint8_t v = 0; v < ARRAY_SIZE(controller->fingerprint_enrolled); v++) {
    uint8_t slot = visual_to_slot(controller->fingerprint_enrolled, v);
    controller->show_screen.params.menu_fingerprints.enrolled[v] =
      controller->fingerprint_enrolled[slot];
    strncpy(controller->show_screen.params.menu_fingerprints.labels[v],
            controller->fingerprint_labels[slot],
            sizeof(controller->show_screen.params.menu_fingerprints.labels[v]) - 1);
    controller->show_screen.params.menu_fingerprints
      .labels[v][sizeof(controller->show_screen.params.menu_fingerprints.labels[v]) - 1] = '\0';
  }

  controller->show_screen.params.menu_fingerprints.show_authenticated =
    controller->nav.fingerprint_menu.show_authenticated;
  controller->show_screen.params.menu_fingerprints.authenticated_index =
    controller->nav.fingerprint_menu.show_authenticated
      ? slot_to_visual(controller->fingerprint_enrolled,
                       controller->nav.fingerprint_menu.authenticated_index)
      : controller->nav.fingerprint_menu.authenticated_index;
}

void display_controller_fingerprint_menu_on_enter(display_controller_t* controller,
                                                  const void* entry_data) {
  (void)entry_data;

  controller->nav.fingerprint_menu.show_authenticated = false;

  display_controller_query_fingerprint_status();

  // If coming from menu (depth==1), always start at first fingerprint slot.
  // Otherwise (depth > 1, returning from enrollment), keep the restored value from nav_stack.
  if (controller->nav_stack_depth == 1) {
    controller->nav.fingerprint_menu.selected_item = 0;
  }

  update_screen_params(controller);

  controller->show_screen.params.menu_fingerprints.initial_slot =
    (controller->nav_stack_depth == 1)
      ? 0
      : slot_to_visual(controller->fingerprint_enrolled,
                       controller->nav.fingerprint_menu.selected_item);

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
  if (event == UI_EVENT_FINGERPRINT_DELETED || event == UI_EVENT_FINGERPRINT_DELETE_FAILED) {
    display_controller_query_fingerprint_status();
    return flow_result_handled();
  } else if (event == UI_EVENT_FINGERPRINT_STATUS) {
    controller->nav.fingerprint_menu.show_authenticated = false;
    update_screen_params(controller);
    display_controller_show_screen(controller, fwpb_display_show_screen_menu_fingerprints_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                   TRANSITION_DURATION_NONE);
    return flow_result_handled();
  } else if (event == UI_EVENT_AUTH_SUCCESS && data && len == sizeof(fingerprint_auth_data_t)) {
    const fingerprint_auth_data_t* auth_data = (const fingerprint_auth_data_t*)data;
    if (auth_data->template_index < FINGERPRINT_SLOT_COUNT) {
      controller->nav.fingerprint_menu.authenticated_index = auth_data->template_index;
      controller->nav.fingerprint_menu.show_authenticated = true;
      update_screen_params(controller);
      display_controller_show_screen(controller, fwpb_display_show_screen_menu_fingerprints_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);
      controller->nav.fingerprint_menu.show_authenticated = false;
    }
    return flow_result_handled();
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_fingerprint_menu_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT) {
    uint8_t visual_index = (uint8_t)data;

    if (visual_index < FINGERPRINT_SLOT_COUNT) {
      uint8_t slot_index = visual_to_slot(controller->fingerprint_enrolled, visual_index);
      controller->nav.fingerprint_menu.selected_item = slot_index;

      if (controller->fingerprint_enrolled[slot_index]) {
        // Enrolled slot - screen layer handles showing deletion modal
        return flow_result_handled();
      } else {
        controller->nav.fingerprint.slot_index = slot_index;
        controller->fingerprint_enrollment_is_required = false;
        return flow_result_navigate(FLOW_FINGERPRINT_MGMT,
                                    fwpb_display_transition_DISPLAY_TRANSITION_FADE);
      }
    }
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_DELETE_FINGERPRINT) {
    uint8_t visual_index = (uint8_t)data;
    if (visual_index < FINGERPRINT_SLOT_COUNT) {
      uint8_t slot_index = visual_to_slot(controller->fingerprint_enrolled, visual_index);
      display_controller_handle_action_delete_fingerprint(slot_index);
    }
    return flow_result_handled();
  }

  return flow_result_handled();
}
