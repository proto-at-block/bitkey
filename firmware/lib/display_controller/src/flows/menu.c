#include "display_controller.h"
#include "display_controller_internal.h"

#ifdef MFGTEST
#include "sleep.h"
#endif

#include <string.h>

void display_controller_menu_on_enter(display_controller_t* controller, const void* entry_data) {
  (void)entry_data;

  controller->nav.menu.selected_item =
    display_controller_normalize_menu_selection(controller->nav.menu.selected_item);

  // Set up screen params
  controller->show_screen.which_params = fwpb_display_show_screen_menu_tag;
  controller->show_screen.params.menu.hide_lock_device =
    !display_controller_menu_show_lock_device();
  controller->show_screen.params.menu.hide_fingerprints =
    !display_controller_menu_show_fingerprints();

  // Pass the selected item to the screen so it can restore scroll position
  controller->show_screen.params.menu.selected_item = controller->nav.menu.selected_item;

#ifdef MFGTEST
  // Populate sleep state for MFG builds
  uint32_t timeout = sleep_get_configured_timeout();
  controller->show_screen.params.menu.sleep_disabled = (timeout == SLEEP_INHIBIT_INFINITE);
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
  // Handle back button
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
#ifdef MFGTEST
    // If showing touch debug sub-screen, go back to menu instead of exiting to scan
    if (controller->show_screen.which_params == fwpb_display_show_screen_touch_debug_tag) {
      controller->nav.menu.selected_item = fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_DEBUG;
      controller->show_screen.params.menu.selected_item =
        fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_DEBUG;
      display_controller_show_screen(controller, fwpb_display_show_screen_menu_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                     TRANSITION_DURATION_STANDARD);
      return flow_result_handled();
    }
#endif
    // Default: return to scan flow
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
        if (!display_controller_menu_show_fingerprints()) {
          return flow_result_handled();
        }
        // Pre-fetch enrollment status so data is ready during transition animation
        display_controller_query_fingerprint_status();
        return flow_result_navigate(FLOW_FINGERPRINTS_MENU,
                                    fwpb_display_transition_DISPLAY_TRANSITION_FADE);

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT:
        return flow_result_navigate(FLOW_INFO, fwpb_display_transition_DISPLAY_TRANSITION_FADE);

        // Note: LOCK_DEVICE and POWER_OFF send their own action types directly,
        // so they don't come through EXIT

#ifdef MFGTEST
      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_GAME:
        return flow_result_navigate(FLOW_GAME, fwpb_display_transition_DISPLAY_TRANSITION_FADE);

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_DEBUG:
        // Show touch debug screen
        controller->current_flow = FLOW_MENU;  // Stay in menu conceptually
        controller->show_screen.which_params = fwpb_display_show_screen_touch_debug_tag;
        display_controller_show_screen(controller, fwpb_display_show_screen_touch_debug_tag,
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
            sleep_inhibit(SLEEP_INHIBIT_INFINITE);
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

      case fwpb_display_menu_item_DISPLAY_MENU_ITEM_MONEY_MOVEMENT:
        // Start money movement test flow with mock data
        controller->nav.money_movement.flow = fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SEND;
        memset(&controller->nav.money_movement.send_data, 0,
               sizeof(controller->nav.money_movement.send_data));
        strncpy(controller->nav.money_movement.send_data.amount_sats, "250000",
                sizeof(controller->nav.money_movement.send_data.amount_sats) - 1);
        strncpy(controller->nav.money_movement.send_data.fee_sats, "450",
                sizeof(controller->nav.money_movement.send_data.fee_sats) - 1);
        controller->nav.money_movement.send_data.btc_display_unit =
          fwpb_display_btc_unit_DISPLAY_BTC_UNIT_BITCOIN;
        strncpy(controller->nav.money_movement.send_data.address,
                "tb1qpa39yw443tn27kvs68gpztf7tvvmnzh5xjp5jdwdqlf73jls6tpsmxy5rx",
                sizeof(controller->nav.money_movement.send_data.address) - 1);
        return flow_result_navigate(FLOW_TRANSACTION,
                                    fwpb_display_transition_DISPLAY_TRANSITION_FADE);
#endif

      default:
        break;
    }
  }

  return flow_result_handled();
}
