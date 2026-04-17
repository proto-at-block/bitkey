#include "screens.h"

#include "screens/screen_about.h"
#include "screens/screen_brightness.h"
#include "screens/screen_confirmation.h"
#include "screens/screen_fingerprint.h"
#include "screens/screen_firmware_update.h"
#include "screens/screen_game.h"
#include "screens/screen_locked.h"
#include "screens/screen_menu.h"
#include "screens/screen_menu_fingerprints.h"
#include "screens/screen_mfg.h"
#include "screens/screen_mfg_touch_debug.h"
#include "screens/screen_money_movement.h"
#include "screens/screen_onboarding.h"
#include "screens/screen_onboarding_complete.h"
#include "screens/screen_power_off.h"
#include "screens/screen_privileged_action.h"
#include "screens/screen_scan.h"

typedef struct {
  pb_size_t params_tag;
  const screen_t* screen;
} screen_entry_t;

const screen_t screen_onboarding = {
  .init = screen_onboarding_init,
  .destroy = screen_onboarding_destroy,
  .update = screen_onboarding_update,
};

const screen_t screen_scan = {
  .init = screen_scan_init,
  .destroy = screen_scan_destroy,
  .update = screen_scan_update,
};

const screen_t screen_menu = {
  .init = screen_menu_init,
  .destroy = screen_menu_destroy,
  .update = screen_menu_update,
};

const screen_t screen_brightness = {
  .init = screen_brightness_init,
  .destroy = screen_brightness_destroy,
  .update = screen_brightness_update,
};

const screen_t screen_about = {
  .init = screen_about_init,
  .destroy = screen_about_destroy,
  .update = screen_about_update,
};

const screen_t screen_money_movement = {
  .init = screen_money_movement_init,
  .destroy = screen_money_movement_destroy,
  .update = screen_money_movement_update,
};

const screen_t screen_mfg = {
  .init = screen_mfg_init,
  .destroy = screen_mfg_destroy,
  .update = screen_mfg_update,
};

const screen_t screen_locked = {
  .init = screen_locked_init,
  .destroy = screen_locked_destroy,
  .update = screen_locked_update,
};

const screen_t screen_fingerprint = {
  .init = screen_fingerprint_init,
  .destroy = screen_fingerprint_destroy,
  .update = screen_fingerprint_update,
};

const screen_t screen_menu_fingerprints = {
  .init = screen_menu_fingerprints_init,
  .destroy = screen_menu_fingerprints_destroy,
  .update = screen_menu_fingerprints_update,
};

const screen_t screen_firmware_update = {
  .init = screen_firmware_update_init,
  .destroy = screen_firmware_update_destroy,
  .update = screen_firmware_update_update,
};

const screen_t screen_privileged_action = {
  .init = screen_privileged_action_init,
  .destroy = screen_privileged_action_destroy,
  .update = screen_privileged_action_update,
};

const screen_t screen_power_off = {
  .init = screen_power_off_init,
  .destroy = screen_power_off_destroy,
  .update = screen_power_off_update,
};

const screen_t screen_confirmation = {
  .init = screen_confirmation_init,
  .destroy = screen_confirmation_destroy,
  .update = screen_confirmation_update,
};

const screen_t screen_game = {
  .init = screen_game_init,
  .destroy = screen_game_destroy,
  .update = screen_game_update,
};

const screen_t screen_onboarding_complete = {
  .init = screen_onboarding_complete_init,
  .destroy = screen_onboarding_complete_destroy,
  .update = screen_onboarding_complete_update,
};

#ifdef MFGTEST
const screen_t screen_touch_debug = {
  .init = screen_touch_debug_init,
  .destroy = screen_touch_debug_destroy,
  .update = screen_touch_debug_update,
};
#endif

static const screen_entry_t registry[] = {
  {fwpb_display_show_screen_onboarding_tag, &screen_onboarding},
  {fwpb_display_show_screen_scan_tag, &screen_scan},
  {fwpb_display_show_screen_menu_tag, &screen_menu},
  {fwpb_display_show_screen_brightness_tag, &screen_brightness},
  {fwpb_display_show_screen_about_tag, &screen_about},
  {fwpb_display_show_screen_menu_fingerprints_tag, &screen_menu_fingerprints},
  {fwpb_display_show_screen_money_movement_tag, &screen_money_movement},
  {fwpb_display_show_screen_mfg_tag, &screen_mfg},
  {fwpb_display_show_screen_locked_tag, &screen_locked},
  {fwpb_display_show_screen_fingerprint_tag, &screen_fingerprint},
  {fwpb_display_show_screen_firmware_update_tag, &screen_firmware_update},
  {fwpb_display_show_screen_privileged_action_tag, &screen_privileged_action},
  {fwpb_display_show_screen_power_off_tag, &screen_power_off},
  {fwpb_display_show_screen_confirmation_tag, &screen_confirmation},
  {fwpb_display_show_screen_game_tag, &screen_game},
  {fwpb_display_show_screen_onboarding_complete_tag, &screen_onboarding_complete},
#ifdef MFGTEST
  {fwpb_display_show_screen_touch_debug_tag, &screen_touch_debug},
#endif
};

static const size_t registry_count = sizeof(registry) / sizeof(registry[0]);

const screen_t* screen_get_by_params_tag(pb_size_t params_tag) {
  for (size_t i = 0; i < registry_count; i++) {
    if (registry[i].params_tag == params_tag) {
      return registry[i].screen;
    }
  }
  return NULL;
}
