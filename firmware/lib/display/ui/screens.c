#include "screens.h"

#include "screens/screen_about.h"
#include "screens/screen_brightness.h"
#include "screens/screen_fingerprint.h"
#include "screens/screen_fingerprint_remove.h"
#include "screens/screen_firmware_update.h"
#include "screens/screen_locked.h"
#include "screens/screen_menu.h"
#include "screens/screen_menu_fingerprints.h"
#include "screens/screen_mfg.h"
#include "screens/screen_money_movement.h"
#include "screens/screen_onboarding.h"
#include "screens/screen_regulatory.h"
#include "screens/screen_scan.h"
#include "screens/screen_test_carousel.h"
#include "screens/screen_test_gesture.h"
#include "screens/screen_test_pin_pad.h"
#include "screens/screen_test_progress.h"
#include "screens/screen_test_scroll.h"
#include "screens/screen_test_slider.h"

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

const screen_t screen_regulatory = {
  .init = screen_regulatory_init,
  .destroy = screen_regulatory_destroy,
  .update = screen_regulatory_update,
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

const screen_t screen_fingerprint_remove = {
  .init = screen_fingerprint_remove_init,
  .destroy = screen_fingerprint_remove_destroy,
  .update = screen_fingerprint_remove_update,
};

const screen_t screen_firmware_update = {
  .init = screen_firmware_update_init,
  .destroy = screen_firmware_update_destroy,
  .update = screen_firmware_update_update,
};

#ifdef MFGTEST
const screen_t screen_test_gesture = {
  .init = screen_test_gesture_init,
  .destroy = screen_test_gesture_destroy,
  .update = screen_test_gesture_update,
};

const screen_t screen_test_scroll = {
  .init = screen_test_scroll_init,
  .destroy = screen_test_scroll_destroy,
  .update = screen_test_scroll_update,
};

const screen_t screen_test_pin_pad = {
  .init = screen_test_pin_pad_init,
  .destroy = screen_test_pin_pad_destroy,
  .update = screen_test_pin_pad_update,
};

const screen_t screen_test_carousel = {
  .init = screen_test_carousel_init,
  .destroy = screen_test_carousel_destroy,
  .update = screen_test_carousel_update,
};

const screen_t screen_test_slider = {
  .init = screen_test_slider_init,
  .destroy = screen_test_slider_destroy,
  .update = screen_test_slider_update,
};

const screen_t screen_test_progress = {
  .init = screen_test_progress_init,
  .destroy = screen_test_progress_destroy,
  .update = screen_test_progress_update,
};
#endif

static const screen_entry_t registry[] = {
  {fwpb_display_show_screen_onboarding_tag, &screen_onboarding},
  {fwpb_display_show_screen_scan_tag, &screen_scan},
  {fwpb_display_show_screen_menu_tag, &screen_menu},
  {fwpb_display_show_screen_brightness_tag, &screen_brightness},
  {fwpb_display_show_screen_about_tag, &screen_about},
  {fwpb_display_show_screen_regulatory_tag, &screen_regulatory},
  {fwpb_display_show_screen_menu_fingerprints_tag, &screen_menu_fingerprints},
  {fwpb_display_show_screen_fingerprint_remove_tag, &screen_fingerprint_remove},
  {fwpb_display_show_screen_money_movement_tag, &screen_money_movement},
  {fwpb_display_show_screen_mfg_tag, &screen_mfg},
  {fwpb_display_show_screen_locked_tag, &screen_locked},
  {fwpb_display_show_screen_fingerprint_tag, &screen_fingerprint},
  {fwpb_display_show_screen_firmware_update_tag, &screen_firmware_update},
#ifdef MFGTEST
  {fwpb_display_show_screen_test_gesture_tag, &screen_test_gesture},
  {fwpb_display_show_screen_test_scroll_tag, &screen_test_scroll},
  {fwpb_display_show_screen_test_pin_pad_tag, &screen_test_pin_pad},
  {fwpb_display_show_screen_test_carousel_tag, &screen_test_carousel},
  {fwpb_display_show_screen_test_slider_tag, &screen_test_slider},
  {fwpb_display_show_screen_test_progress_tag, &screen_test_progress},
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
