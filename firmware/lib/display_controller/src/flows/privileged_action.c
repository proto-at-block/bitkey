#include "display_controller.h"
#include "display_controller_internal.h"

#ifdef EMBEDDED_BUILD
#include "confirmation_manager.h"
#include "ipc.h"
#endif

#include <stdio.h>
#include <string.h>

#ifdef EMBEDDED_BUILD
static void clear_privileged_action_confirmation_if_pending(void) {
  if (!confirmation_manager_is_pending()) {
    return;
  }

  confirmation_manager_clear();
}
#endif

void display_controller_privileged_action_on_enter(display_controller_t* controller,
                                                   const void* entry_data) {
  // Zero the full nav state first so handoff timers start clean.
  memset(&controller->nav.privileged_action, 0, sizeof(controller->nav.privileged_action));

  // Extract privileged action data from entry parameters
  if (entry_data) {
    const fwpb_display_params_privileged_action* entry =
      (const fwpb_display_params_privileged_action*)entry_data;
    memcpy(&controller->nav.privileged_action.params, entry,
           sizeof(fwpb_display_params_privileged_action));
  }

  // Copy to show_screen params for display
  memcpy(&controller->show_screen.params.privileged_action,
         &controller->nav.privileged_action.params, sizeof(fwpb_display_params_privileged_action));

  controller->show_screen.which_params = fwpb_display_show_screen_privileged_action_tag;
}

void display_controller_privileged_action_on_exit(display_controller_t* controller) {
  // Clean up privileged action data
  memset(&controller->nav.privileged_action, 0, sizeof(controller->nav.privileged_action));
}

flow_action_result_t display_controller_privileged_action_on_tick(
  display_controller_t* controller) {
#ifdef EMBEDDED_BUILD
  // Exit promptly if the pending privileged-action confirmation expires while
  // the approval UI is still on screen.
  if (controller->nav.privileged_action.handoff_delay_timer == 0 &&
      controller->nav.privileged_action.handoff_timer == 0 && confirmation_manager_is_expired()) {
    confirmation_manager_clear();
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }
#endif

  if (controller->nav.privileged_action.handoff_delay_timer > 0) {
    controller->nav.privileged_action.handoff_delay_timer--;
    if (controller->nav.privileged_action.handoff_delay_timer == 0) {
      controller->nav.privileged_action.handoff_timer = 1;
    }
  } else {
    display_controller_tick_handoff_to_scan(controller,
                                            &controller->nav.privileged_action.handoff_timer);
  }
  return flow_result_handled();
}

flow_action_result_t display_controller_privileged_action_on_event(display_controller_t* controller,
                                                                   ui_event_type_t event,
                                                                   const void* data, uint32_t len) {
  (void)controller;
  (void)event;
  (void)data;
  (void)len;

  // All data provided via entry_data at flow entry
  return flow_result_handled();
}

flow_action_result_t display_controller_privileged_action_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)data;

  switch (action) {
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE:
#ifdef EMBEDDED_BUILD
      confirmation_manager_approve();
      ipc_send_empty(key_manager_port, IPC_KEY_MANAGER_SIGN_DEFERRED);
#endif
      // Approve immediately so the confirmation cannot expire, then wait for
      // the display-side "Confirmed" interstitial before showing confirm scan.
      controller->nav.privileged_action.handoff_delay_timer =
        PRIVILEGED_ACTION_CONFIRMED_DELAY_TICKS;
      controller->nav.privileged_action.handoff_timer = 0;
      return flow_result_handled();

    case fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL:
#ifdef EMBEDDED_BUILD
      // Cancel should invalidate any pending privileged-action confirmation so
      // a later NFC tap cannot continue the previously rejected operation.
      clear_privileged_action_confirmation_if_pending();
#endif
      // Cancel returns to scan screen
      return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                              TRANSITION_DURATION_STANDARD);

    case fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU:
#ifdef EMBEDDED_BUILD
      // Leaving through menu should also invalidate the pending confirmation.
      clear_privileged_action_confirmation_if_pending();
#endif
      // Menu button navigates to menu
      return flow_result_navigate(FLOW_MENU, fwpb_display_transition_DISPLAY_TRANSITION_FADE);

    case fwpb_display_action_display_action_type_DISPLAY_ACTION_PAGE_CONFIRMED:
#ifdef EMBEDDED_BUILD
      if (confirmation_manager_is_pending()) {
        confirmation_manager_refresh_timestamp();
      }
#endif
      return flow_result_handled();

    default:
      break;
  }

  return flow_result_handled();
}
