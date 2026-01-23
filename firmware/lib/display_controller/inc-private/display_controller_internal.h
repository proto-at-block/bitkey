#pragma once

#include "display_controller.h"

// Transition durations
#define TRANSITION_DURATION_NONE     0
#define TRANSITION_DURATION_STANDARD 50

typedef enum {
  FLOW_RESULT_HANDLED,    // Flow handled action/event, stay in flow
  FLOW_RESULT_NAVIGATE,   // Navigate to another flow
  FLOW_RESULT_EXIT_FLOW,  // Exit to previous flow (or scan screen)
} flow_result_type_t;

typedef struct {
  flow_result_type_t type;
  flow_id_t target_flow;  // Used when type == NAVIGATE
  fwpb_display_transition transition;
  uint32_t duration_ms;

  union {
    uint8_t fingerprint_index;
    uint8_t menu_selection;
    device_info_t* device_info;
    transaction_type_t transaction_type;
  } data;
  bool has_data;
} flow_action_result_t;

// Helper functions for common result patterns
static inline flow_action_result_t flow_result_handled(void) {
  return (flow_action_result_t){.type = FLOW_RESULT_HANDLED};
}

static inline flow_action_result_t flow_result_exit_to_scan(void) {
  return (flow_action_result_t){
    .type = FLOW_RESULT_EXIT_FLOW,
    .transition = fwpb_display_transition_DISPLAY_TRANSITION_FADE,
    .duration_ms = TRANSITION_DURATION_STANDARD,
  };
}

static inline flow_action_result_t flow_result_exit_with_transition(fwpb_display_transition trans,
                                                                    uint32_t duration_ms) {
  return (flow_action_result_t){
    .type = FLOW_RESULT_EXIT_FLOW,
    .transition = trans,
    .duration_ms = duration_ms,
  };
}

static inline flow_action_result_t flow_result_navigate(flow_id_t target,
                                                        fwpb_display_transition trans) {
  return (flow_action_result_t){
    .type = FLOW_RESULT_NAVIGATE,
    .target_flow = target,
    .transition = trans,
    .duration_ms = 0,
    .has_data = false,
  };
}

// Macro for navigation with data
#define FLOW_NAVIGATE_WITH_DATA(target, trans, field, value) \
  ((flow_action_result_t){                                   \
    .type = FLOW_RESULT_NAVIGATE,                            \
    .target_flow = target,                                   \
    .transition = trans,                                     \
    .duration_ms = 0,                                        \
    .has_data = true,                                        \
    .data.field = value,                                     \
  })

// Wrapper for flows to update their own screen (enforces ownership)
void flow_update_current_screen(display_controller_t* controller,
                                fwpb_display_transition transition, uint32_t duration_ms);

// Flow handler interface
typedef struct {
  // Called when entering the flow - receives flexible entry data
  void (*on_enter)(display_controller_t* controller, const void* entry_data);

  // Called when exiting the flow - cleanup only
  void (*on_exit)(display_controller_t* controller);

  // Called periodically for timers, animations - returns navigation decision
  flow_action_result_t (*on_tick)(display_controller_t* controller);

  // Called to handle flow-specific events - returns navigation decision
  flow_action_result_t (*on_event)(display_controller_t* controller, ui_event_type_t event,
                                   const void* data, uint32_t len);

  // Called to handle display actions - returns navigation decision
  flow_action_result_t (*on_action)(display_controller_t* controller,
                                    fwpb_display_action_display_action_type action,
                                    uint32_t action_data);
} flow_handler_t;

// Internal helper functions
void display_controller_show_screen(display_controller_t* ctrl, pb_size_t params_tag,
                                    fwpb_display_transition transition, uint32_t duration_ms);

// Menu flow
void display_controller_menu_on_enter(display_controller_t* controller, const void* entry_data);
void display_controller_menu_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_menu_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_menu_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Money movement flow
void display_controller_money_movement_on_enter(display_controller_t* controller,
                                                const void* entry_data);
void display_controller_money_movement_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_money_movement_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_money_movement_on_event(display_controller_t* controller,
                                                                ui_event_type_t event,
                                                                const void* data, uint32_t len);
flow_action_result_t display_controller_money_movement_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Brightness flow
void display_controller_brightness_on_enter(display_controller_t* controller,
                                            const void* entry_data);
void display_controller_brightness_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_brightness_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_brightness_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Info flow
void display_controller_info_on_enter(display_controller_t* controller, const void* entry_data);
void display_controller_info_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_info_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_info_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Onboarding flow
void display_controller_onboarding_on_enter(display_controller_t* controller,
                                            const void* entry_data);
void display_controller_onboarding_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_onboarding_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_onboarding_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Fingerprint enrollment flow
void display_controller_fingerprint_on_enter(display_controller_t* controller,
                                             const void* entry_data);
void display_controller_fingerprint_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_fingerprint_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_fingerprint_on_event(display_controller_t* controller,
                                                             ui_event_type_t event,
                                                             const void* data, uint32_t len);
flow_action_result_t display_controller_fingerprint_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Fingerprint menu flow
void display_controller_fingerprint_menu_on_enter(display_controller_t* controller,
                                                  const void* entry_data);
void display_controller_fingerprint_menu_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_fingerprint_menu_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_fingerprint_menu_on_event(display_controller_t* controller,
                                                                  ui_event_type_t event,
                                                                  const void* data, uint32_t len);
flow_action_result_t display_controller_fingerprint_menu_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Firmware update flow
void display_controller_firmware_update_on_enter(display_controller_t* controller,
                                                 const void* entry_data);
void display_controller_firmware_update_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_firmware_update_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_firmware_update_on_event(display_controller_t* controller,
                                                                 ui_event_type_t event,
                                                                 const void* data, uint32_t len);
flow_action_result_t display_controller_firmware_update_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// MFG flow (run-in test)
void display_controller_mfg_on_enter(display_controller_t* controller, const void* entry_data);
void display_controller_mfg_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_mfg_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_mfg_on_event(display_controller_t* controller,
                                                     ui_event_type_t event, const void* data,
                                                     uint32_t len);
flow_action_result_t display_controller_mfg_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Lock screen flow
void display_controller_locked_on_enter(display_controller_t* controller, const void* entry_data);
void display_controller_locked_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_locked_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_locked_on_event(display_controller_t* controller,
                                                        ui_event_type_t event, const void* data,
                                                        uint32_t len);
flow_action_result_t display_controller_locked_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Privileged action flow
void display_controller_privileged_action_on_enter(display_controller_t* controller,
                                                   const void* entry_data);
void display_controller_privileged_action_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_privileged_action_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_privileged_action_on_event(display_controller_t* controller,
                                                                   ui_event_type_t event,
                                                                   const void* data, uint32_t len);
flow_action_result_t display_controller_privileged_action_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Helper functions
void display_controller_query_fingerprint_status(void);
