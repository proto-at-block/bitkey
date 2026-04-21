#include "display_controller.h"
#include "display_controller_internal.h"
#include "langpack_ids.h"

#include <string.h>

#define SIGNING_CONFIRMATION_TEXT        "Signing..."
#define LEGACY_SIGNING_CONFIRMATION_TEXT "SIGNING..."
#define SIGNING_CONFIRMATION_DISMISS_MS  3000
#define SUCCESS_CONFIRMATION_DISMISS_MS  4000

static uint32_t confirmation_dismiss_ticks(const fwpb_display_params_confirmation* params) {
  uint32_t dismiss_ms = SUCCESS_CONFIRMATION_DISMISS_MS;

  if (!params) {
    return (dismiss_ms + DISPLAY_TICK_MS - 1) / DISPLAY_TICK_MS;
  }

  if (
    params->mode ==
    fwpb_display_params_confirmation_display_params_confirmation_mode_DISPLAY_PARAMS_CONFIRMATION_MODE_LOADING) {
    dismiss_ms = SIGNING_CONFIRMATION_DISMISS_MS;
  } else if (params->text_id == LANGPACK_ID_CONFIRMATION_SIGNING ||
             (params->text[0] != '\0' &&
              (strcmp(params->text, SIGNING_CONFIRMATION_TEXT) == 0 ||
               strcmp(params->text, LEGACY_SIGNING_CONFIRMATION_TEXT) == 0))) {
    dismiss_ms = SIGNING_CONFIRMATION_DISMISS_MS;
  }

  return (dismiss_ms + DISPLAY_TICK_MS - 1) / DISPLAY_TICK_MS;
}

void display_controller_confirmation_on_enter(display_controller_t* controller,
                                              const void* entry_data) {
  controller->show_screen.which_params = fwpb_display_show_screen_confirmation_tag;

  const fwpb_display_params_confirmation* params =
    (const fwpb_display_params_confirmation*)entry_data;
  controller->show_screen.params.confirmation = *params;
  controller->nav.confirmation.dismiss_timer = confirmation_dismiss_ticks(params);
}

void display_controller_confirmation_on_exit(display_controller_t* controller) {
  (void)controller;
}

flow_action_result_t display_controller_confirmation_on_tick(display_controller_t* controller) {
  if (controller->nav.confirmation.dismiss_timer > 0) {
    controller->nav.confirmation.dismiss_timer--;

    if (controller->nav.confirmation.dismiss_timer == 0) {
      if (controller->show_screen.params.confirmation.lock_on_dismiss) {
        return flow_result_lock();
      }
      return flow_result_exit_to_scan();
    }
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_confirmation_on_event(display_controller_t* controller,
                                                              ui_event_type_t event,
                                                              const void* data, uint32_t len) {
  (void)controller;
  (void)event;
  (void)data;
  (void)len;

  return flow_result_handled();
}

flow_action_result_t display_controller_confirmation_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)controller;
  (void)action;
  (void)data;

  return flow_result_handled();
}
