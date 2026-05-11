#pragma once

#include "battery_rescale.h"
#include "display_controller.h"
#include "langpack_ids.h"

#include <string.h>

#define FINGERPRINT_SLOT_COUNT              3
#define DISPLAY_UXC_RESET_FAILURE_THRESHOLD 2u

bool display_controller_update_uxc_send_failures(bool send_success, uint8_t* failure_count);

// Display controller context
typedef struct display_controller_t {
  // State model
  bool is_locked;          // Device requires fingerprint to unlock
  flow_id_t current_flow;  // Current active flow

  // Navigation stack
  struct {
    flow_id_t flow;
    uint8_t saved_selection;  // For menu restoration
  } nav_stack[4];             // Max depth: scan -> menu -> submenu -> detail
  uint8_t nav_stack_depth;

  // Current screen command - contains params and transition info
  fwpb_display_show_screen show_screen;

  // Root menu navigation state
  flow_id_t menu_root_return_flow;
  bool onboarding_resume_at_scan;
  bool scan_confirm_on_enter;  // One-shot override for "continue on phone" scan
  bool scan_confirm_cancel_returns_to_onboarding;
  bool fingerprint_enrollment_is_required;
  bool onboarding_complete_pending;

  // Flow navigation state
  union {
    struct {
      fwpb_display_menu_item selected_item;  // Which menu item is highlighted
    } menu;

    struct {
      fwpb_display_params_firmware_update params;  // FWUP page/version state
      uint32_t handoff_timer;  // Phone handoff countdown in ticks (0 = inactive)
    } firmware_update;

    struct {
      uint8_t current_page;    // Which page (0-3) we're on
      uint32_t total_samples;  // Total samples required for enrollment
      uint32_t samples_done;   // Samples successfully captured so far
      uint8_t slot_index;      // Which slot (0-2) we're enrolling to
    } fingerprint;

    struct {
      uint8_t selected_item;        // Which fingerprint slot is selected (0-2)
      uint8_t detail_index;         // Which fingerprint detail is being shown
      uint8_t authenticated_index;  // Which fingerprint was authenticated (for animation)
      bool show_authenticated;      // True to trigger authentication animation
    } fingerprint_menu;

    struct {
      bool unlocking;         // True when showing unlock animation
      uint32_t unlock_timer;  // Countdown timer in ticks
      uint32_t error_timer;   // Countdown timer for "try again" error display
    } locked;

    struct {
      uint32_t error_timer;  // Countdown timer for "try again" error display
    } scan;

    struct {
      send_transaction_data_t send_data;        // Send transaction details
      receive_transaction_data_t receive_data;  // Receive transaction details
      fwpb_money_movement_flow flow;            // SEND, RECEIVE, or SELF_SEND
      uint32_t receive_start_time;              // Systime when receive flow entered (for 5min cap)
      uint8_t handoff_phase;                    // 0=normal, 1=signing
      uint32_t handoff_timer;                   // Countdown in ticks for current phase
    } money_movement;

    struct {
      fwpb_display_params_privileged_action params;  // Privileged action parameters
      uint32_t handoff_delay_timer;  // Delay before showing confirm scan (0 = inactive)
      uint32_t handoff_timer;        // Confirm scan countdown in ticks (0 = inactive)
    } privileged_action;

    struct {
      uint32_t dismiss_timer;  // Countdown timer in ticks
    } confirmation;

    struct {
      uint32_t lock_timer;  // Countdown timer in ticks
    } onboarding_complete;
  } nav;

  // Persistent data storage
  device_info_t device_info;
  bool has_device_info;

  // Fingerprint enrollment state
  bool fingerprint_enrolled[FINGERPRINT_SLOT_COUNT];    // Which slots have enrolled fingerprints
  char fingerprint_labels[FINGERPRINT_SLOT_COUNT][32];  // Labels for enrolled fingerprints

  // Battery state
  uint8_t battery_percent;
  uint8_t battery_percent_raw;  // Raw SOC from fuel gauge (before rescaling)
  bool is_charging;
  battery_rescale_state_t battery_rescale_state;

  /**
   * @brief Touch test state.
   */
  struct {
    /**
     * @brief End time of the touch test in milliseconds.
     *
     * @details A touch event is only recorded if the touch test is not
     * complete, and the current time is less than the test end time.
     */
    uint32_t end_time_ms;

    /**
     * @brief Recorded touch event.
     */
    ui_event_touch_t touch_event;

    /**
     * @brief Boolean indicating if a touch test is active.
     */
    bool active;

    /**
     * @brief Number of boxes remaining in the touch test.
     *
     * @details Updated each time a touch test status event is received.
     * Used to report the last known state when the test times out.
     */
    uint16_t boxes_remaining;
  } touch_test;

  // Initialization state
  bool uxc_ready;  // UXC has sent DISPLAY_ACTION_READY
  bool initial_screen_shown;
} display_controller_t;

// Transition durations
#define TRANSITION_DURATION_NONE     0
#define TRANSITION_DURATION_STANDARD 50

// Keep in sync with screen_privileged_action.c CONFIRMED_DELAY_MS.
#define PRIVILEGED_ACTION_CONFIRMED_DELAY_TICKS MS_TO_DISPLAY_TICKS(2500)

typedef enum {
  FLOW_RESULT_HANDLED,    // Flow handled action/event, stay in flow
  FLOW_RESULT_NAVIGATE,   // Navigate to another flow
  FLOW_RESULT_EXIT_FLOW,  // Exit to previous flow (or scan screen)
  FLOW_RESULT_LOCK,       // Lock the device after dismissing current flow
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
    fwpb_display_params_confirmation confirmation;
  } data;
  bool has_data;
} flow_action_result_t;

// Entry data for FLOW_TRANSACTION.
typedef struct {
  fwpb_money_movement_flow flow;  // SEND, RECEIVE, or SELF_SEND
  union {
    send_transaction_data_t send;
    receive_transaction_data_t receive;
  } data;
} flow_transaction_entry_data_t;

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

static inline flow_action_result_t flow_result_lock(void) {
  return (flow_action_result_t){.type = FLOW_RESULT_LOCK};
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

// Internal helper functions
void display_controller_show_screen(display_controller_t* ctrl, pb_size_t params_tag,
                                    fwpb_display_transition transition, uint32_t duration_ms);
bool display_controller_menu_show_lock_device(void);
bool display_controller_menu_show_fingerprints(void);
fwpb_display_menu_item display_controller_default_root_menu_selection(void);
fwpb_display_menu_item display_controller_normalize_menu_selection(
  fwpb_display_menu_item selected_item);
bool display_controller_lock_device_action_supported(void);

// Tick a handoff timer and transition to the scan screen when it fires.
// Returns true on the tick the timer reaches zero (scan screen shown).
static inline bool display_controller_tick_handoff_to_scan(display_controller_t* ctrl,
                                                           uint32_t* timer) {
  if (*timer == 0) {
    return false;
  }
  (*timer)--;
  if (*timer == 0) {
    memset(&ctrl->show_screen.params, 0, sizeof(ctrl->show_screen.params));
    ctrl->show_screen.params.scan.action =
      fwpb_display_params_scan_display_params_scan_action_CONFIRM;
    ctrl->show_screen.params.scan.show_error = false;
    display_controller_show_screen(ctrl, fwpb_display_show_screen_scan_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                   TRANSITION_DURATION_STANDARD);
    return true;
  }
  return false;
}

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

#ifdef MFGTEST
// MFG flow (run-in test)
void display_controller_mfg_on_enter(display_controller_t* controller, const void* entry_data);
void display_controller_mfg_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_mfg_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_mfg_on_event(display_controller_t* controller,
                                                     ui_event_type_t event, const void* data,
                                                     uint32_t len);
flow_action_result_t display_controller_mfg_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);
#endif

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

// Confirmation flow
void display_controller_confirmation_on_enter(display_controller_t* controller,
                                              const void* entry_data);
void display_controller_confirmation_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_confirmation_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_confirmation_on_event(display_controller_t* controller,
                                                              ui_event_type_t event,
                                                              const void* data, uint32_t len);
flow_action_result_t display_controller_confirmation_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Onboarding complete flow
void display_controller_onboarding_complete_on_enter(display_controller_t* controller,
                                                     const void* entry_data);
void display_controller_onboarding_complete_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_onboarding_complete_on_tick(
  display_controller_t* controller);
flow_action_result_t display_controller_onboarding_complete_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Game flow
void display_controller_game_on_enter(display_controller_t* controller, const void* entry_data);
void display_controller_game_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_game_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_game_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Power-off flow
void display_controller_power_off_on_enter(display_controller_t* controller,
                                           const void* entry_data);
void display_controller_power_off_on_exit(display_controller_t* controller);
flow_action_result_t display_controller_power_off_on_tick(display_controller_t* controller);
flow_action_result_t display_controller_power_off_on_event(display_controller_t* controller,
                                                           ui_event_type_t event, const void* data,
                                                           uint32_t len);
flow_action_result_t display_controller_power_off_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data);

// Helper functions
void display_controller_query_fingerprint_status(void);
