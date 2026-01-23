#include "display_controller.h"
#include "display_controller_internal.h"

#ifdef MFGTEST
#include "sleep.h"
#endif

#include <string.h>

void display_controller_menu_on_enter(display_controller_t* controller, const void* entry_data) {
  (void)entry_data;

  // Set up screen params
  controller->show_screen.which_params = fwpb_display_show_screen_menu_tag;

  // Pass the selected item to the screen so it can restore scroll position
  controller->show_screen.params.menu.selected_item = controller->nav.menu.selected_item;

#ifdef MFGTEST
  // Populate sleep state for MFG builds
  uint32_t timeout = sleep_get_configured_timeout();
  controller->show_screen.params.menu.sleep_disabled = (timeout >= UINT32_MAX);
#endif
}

void display_controller_menu_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_menu_on_tick(display_controller_t* controller) {
  (void)controller;
  return flow_result_handled();
}

flow_action_result_t display_controller_menu_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  // Handle back button - return to scan flow
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT) {
    // data contains menu_item selected
    fwpb_display_menu_item menu_item = (fwpb_display_menu_item)data;

    // Update the controller's selected item so it can be restored later
    controller->nav.menu.selected_item = menu_item;

    switch (menu_item) {
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_BACK:
        // Back button clicked - return to scan flow
        return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                                TRANSITION_DURATION_STANDARD);

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS:
        return flow_result_navigate(FLOW_BRIGHTNESS,
                                    fwpb_display_transition_DISPLAY_TRANSITION_FADE);

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS:
        return flow_result_navigate(FLOW_FINGERPRINTS_MENU,
                                    fwpb_display_transition_DISPLAY_TRANSITION_FADE);

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT:
        // Pass device_info via entry_data to show About screen (don't corrupt union here)
        return FLOW_NAVIGATE_WITH_DATA(FLOW_INFO, fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                       device_info, &controller->device_info);

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY:
        // Pass NULL via entry_data to show Regulatory screen (don't corrupt union here)
        return flow_result_navigate(FLOW_INFO, fwpb_display_transition_DISPLAY_TRANSITION_FADE);

        // Note: LOCK_DEVICE and POWER_OFF send their own action types directly,
        // so they don't come through EXIT

#ifdef MFGTEST
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST:
        // Special case - show test screen directly
        controller->current_flow = FLOW_MENU;  // Stay in menu conceptually
        controller->show_screen.which_params = fwpb_display_show_screen_test_gesture_tag;
        display_controller_show_screen(controller, fwpb_display_show_screen_test_gesture_tag,
                                       fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                       TRANSITION_DURATION_STANDARD);
        return flow_result_handled();

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOGGLE_SLEEP:
        // Toggle sleep mode
        {
          // Use the stored state rather than comparing timeout
          if (controller->show_screen.params.menu.sleep_disabled) {
            // Currently disabled, enable it
            sleep_clear_inhibit();
            controller->show_screen.params.menu.sleep_disabled = false;
          } else {
            // Currently enabled, disable it
            sleep_inhibit(UINT32_MAX);
            controller->show_screen.params.menu.sleep_disabled = true;
          }

          // Update the display with new state
          display_controller_show_screen(controller, fwpb_display_show_screen_menu_tag,
                                         fwpb_display_transition_DISPLAY_TRANSITION_NONE, 0);

          // Stay in menu
          return flow_result_handled();
        }

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_RUN_IN:
        // Start run-in test
        return flow_result_navigate(FLOW_MFG, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
#endif

      default:
        break;
    }
  }

  return flow_result_handled();
}
