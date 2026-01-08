#include "display_controller.h"
#include "display_controller_internal.h"

#include <string.h>

#define DEFAULT_BRIGHTNESS 80

// Menu constants - computed from the enum
#ifdef MFGTEST
#define MENU_ITEM_COUNT (fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST + 1)
#else
#define MENU_ITEM_COUNT (fwpb_display_menu_item_DISPLAY_MENU_ITEM_POWER_OFF + 1)
#endif
#define MENU_ITEM_MAX_INDEX (MENU_ITEM_COUNT - 1)

void display_controller_menu_on_enter(display_controller_t* controller, const void* data) {
  (void)data;

  // When returning from a submenu, previous_flow will be FLOW_MENU
  // and the selected_item will have been restored from saved_menu_selection
  // Otherwise, ensure we have a valid selection
  if (controller->previous_flow == FLOW_MENU) {
    // Returning from submenu - selected_item was already restored in flow_exit
  } else {
    // Fresh entry or from another flow - always start at the back button
    controller->nav.menu.selected_item = 0;
  }

  // Set up screen params
  controller->show_screen.params.menu.selected_item = controller->nav.menu.selected_item;
  controller->show_screen.params.menu.hit_top = false;
  controller->show_screen.params.menu.hit_bottom = false;
  controller->show_screen.which_params = fwpb_display_show_screen_menu_tag;
}

flow_action_t display_controller_menu_on_button_press(display_controller_t* controller,
                                                      const button_event_payload_t* event) {
  if (event->type == BUTTON_PRESS_SINGLE) {
    if (event->button == BUTTON_LEFT) {
      // Navigate up in menu (no wrap-around)
      controller->show_screen.params.menu.hit_top = false;
      controller->show_screen.params.menu.hit_bottom = false;

      if (controller->nav.menu.selected_item > 0) {
        controller->nav.menu.selected_item--;
        controller->show_screen.params.menu.selected_item = controller->nav.menu.selected_item;
        return FLOW_ACTION_REFRESH;
      }
      // At top - trigger bounce animation
      controller->show_screen.params.menu.hit_top = true;
      return FLOW_ACTION_REFRESH;

    } else if (event->button == BUTTON_RIGHT) {
      // Navigate down in menu (no wrap-around)
      controller->show_screen.params.menu.hit_top = false;
      controller->show_screen.params.menu.hit_bottom = false;

      if (controller->nav.menu.selected_item < MENU_ITEM_MAX_INDEX) {
        controller->nav.menu.selected_item++;
        controller->show_screen.params.menu.selected_item = controller->nav.menu.selected_item;
        return FLOW_ACTION_REFRESH;
      }
      // At bottom - trigger bounce animation
      controller->show_screen.params.menu.hit_bottom = true;
      return FLOW_ACTION_REFRESH;
    } else if (event->button == BUTTON_BOTH) {
      // L+R to select menu item
      switch (controller->nav.menu.selected_item) {
        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_BACK:
          return FLOW_ACTION_EXIT;

        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS:
          controller->nav.menu.submenu_index = fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS;
          return FLOW_ACTION_EXIT;

        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS:
          controller->nav.menu.submenu_index =
            fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS;
          return FLOW_ACTION_EXIT;

        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT:
          controller->nav.menu.submenu_index = fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT;
          return FLOW_ACTION_EXIT;

        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY:
          controller->nav.menu.submenu_index = fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY;
          return FLOW_ACTION_EXIT;

        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE:
          controller->nav.menu.submenu_index = fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE;
          return FLOW_ACTION_EXIT;

        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_POWER_OFF:
          return FLOW_ACTION_POWER_OFF;

#ifdef MFGTEST
        case fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST:
          controller->nav.menu.submenu_index = fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST;
          return FLOW_ACTION_EXIT;
#endif

        default:
          break;
      }
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_menu_on_exit(display_controller_t* controller) {
  (void)controller;
}

void display_controller_menu_on_tick(display_controller_t* controller) {
  (void)controller;
}
