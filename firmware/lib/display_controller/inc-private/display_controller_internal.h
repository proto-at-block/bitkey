#pragma once

#include "display_controller.h"

// Transition durations
#define TRANSITION_DURATION_NONE     0
#define TRANSITION_DURATION_QUICK    100
#define TRANSITION_DURATION_STANDARD 200

// Actions that flow handlers can return
typedef enum {
  FLOW_ACTION_NONE = 0,            // No action needed
  FLOW_ACTION_APPROVE,             // User approved → go to WAITING_FOR_SCAN
  FLOW_ACTION_CANCEL,              // User cancelled → return to IDLE
  FLOW_ACTION_REFRESH,             // Refresh current screen with new params
  FLOW_ACTION_EXIT,                // Exit flow without cancel (for onboarding, menu)
  FLOW_ACTION_START_ENROLLMENT,    // Trigger fingerprint enrollment
  FLOW_ACTION_QUERY_FINGERPRINTS,  // Query enrolled fingerprints status
  FLOW_ACTION_DELETE_FINGERPRINT,  // Delete a fingerprint
  FLOW_ACTION_POWER_OFF,           // Request system power off
} flow_action_t;

// Flow handler interface (renamed from display_state_handler_t)
typedef struct {
  // Called when entering the flow - sets up initial screen and nav state
  void (*on_enter)(display_controller_t* controller, const void* data);

  // Called when exiting the flow - cleanup
  void (*on_exit)(display_controller_t* controller);

  // Called on button press - handles navigation within flow
  flow_action_t (*on_button_press)(display_controller_t* controller,
                                   const button_event_payload_t* event);

  // Called periodically for timers, animations
  void (*on_tick)(display_controller_t* controller);

  // Called to handle flow-specific events (optional)
  void (*on_event)(display_controller_t* controller, ui_event_type_t event, const void* data,
                   uint32_t len);
} flow_handler_t;

// Internal helper functions
void display_controller_show_screen(display_controller_t* ctrl, pb_size_t params_tag,
                                    fwpb_display_transition transition, uint32_t duration_ms);

// Menu flow
void display_controller_menu_on_enter(display_controller_t* controller, const void* data);
void display_controller_menu_on_exit(display_controller_t* controller);
flow_action_t display_controller_menu_on_button_press(display_controller_t* controller,
                                                      const button_event_payload_t* event);
void display_controller_menu_on_tick(display_controller_t* controller);

// Money movement flow
void display_controller_money_movement_on_enter(display_controller_t* controller, const void* data);
void display_controller_money_movement_on_exit(display_controller_t* controller);
flow_action_t display_controller_money_movement_on_button_press(
  display_controller_t* controller, const button_event_payload_t* event);
void display_controller_money_movement_on_tick(display_controller_t* controller);

// Brightness flow
void display_controller_brightness_on_enter(display_controller_t* controller, const void* data);
void display_controller_brightness_on_exit(display_controller_t* controller);
flow_action_t display_controller_brightness_on_button_press(display_controller_t* controller,
                                                            const button_event_payload_t* event);
void display_controller_brightness_on_tick(display_controller_t* controller);

// Info flow
void display_controller_info_on_enter(display_controller_t* controller, const void* data);
void display_controller_info_on_exit(display_controller_t* controller);
flow_action_t display_controller_info_on_button_press(display_controller_t* controller,
                                                      const button_event_payload_t* event);
void display_controller_info_on_tick(display_controller_t* controller);

// Onboarding flow
void display_controller_onboarding_on_enter(display_controller_t* controller, const void* data);
void display_controller_onboarding_on_exit(display_controller_t* controller);
flow_action_t display_controller_onboarding_on_button_press(display_controller_t* controller,
                                                            const button_event_payload_t* event);
void display_controller_onboarding_on_tick(display_controller_t* controller);

// Fingerprint enrollment flow
void display_controller_fingerprint_on_enter(display_controller_t* controller, const void* data);
void display_controller_fingerprint_on_exit(display_controller_t* controller);
flow_action_t display_controller_fingerprint_on_button_press(display_controller_t* controller,
                                                             const button_event_payload_t* event);
void display_controller_fingerprint_on_tick(display_controller_t* controller);
void display_controller_fingerprint_on_event(display_controller_t* controller,
                                             ui_event_type_t event, const void* data, uint32_t len);

// Fingerprint menu flow
void display_controller_fingerprint_menu_on_enter(display_controller_t* controller,
                                                  const void* data);
void display_controller_fingerprint_menu_on_exit(display_controller_t* controller);
flow_action_t display_controller_fingerprint_menu_on_button_press(
  display_controller_t* controller, const button_event_payload_t* event);
void display_controller_fingerprint_menu_on_tick(display_controller_t* controller);

// Fingerprint remove flow
void display_controller_fingerprint_remove_on_enter(display_controller_t* controller,
                                                    const void* data);
void display_controller_fingerprint_remove_on_exit(display_controller_t* controller);
flow_action_t display_controller_fingerprint_remove_on_process(display_controller_t* controller,
                                                               const button_event_payload_t* event);
void display_controller_fingerprint_remove_on_tick(display_controller_t* controller);

// Firmware update flow
void display_controller_firmware_update_on_enter(display_controller_t* controller,
                                                 const void* data);
void display_controller_firmware_update_on_exit(display_controller_t* controller);
flow_action_t display_controller_firmware_update_on_button_press(
  display_controller_t* controller, const button_event_payload_t* event);
void display_controller_firmware_update_on_tick(display_controller_t* controller);
void display_controller_firmware_update_on_event(display_controller_t* controller,
                                                 ui_event_type_t event, const void* data,
                                                 uint32_t len);

// MFG flow (run-in test)
void display_controller_mfg_on_enter(display_controller_t* controller, const void* data);
void display_controller_mfg_on_exit(display_controller_t* controller);
flow_action_t display_controller_mfg_on_button_press(display_controller_t* controller,
                                                     const button_event_payload_t* event);
void display_controller_mfg_on_tick(display_controller_t* controller);
void display_controller_mfg_on_event(display_controller_t* controller, ui_event_type_t event,
                                     const void* data, uint32_t len);

// Lock screen flow
void display_controller_lock_screen_on_enter(display_controller_t* controller, const void* data);
void display_controller_lock_screen_on_exit(display_controller_t* controller);
flow_action_t display_controller_lock_screen_on_button_press(display_controller_t* controller,
                                                             const button_event_payload_t* event);
void display_controller_lock_screen_on_tick(display_controller_t* controller);
void display_controller_lock_screen_on_event(display_controller_t* controller,
                                             ui_event_type_t event, const void* data, uint32_t len);
