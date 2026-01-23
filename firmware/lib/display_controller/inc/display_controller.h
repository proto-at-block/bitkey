#pragma once

#include "display.pb.h"
#include "ui_events.h"

#include <stdbool.h>
#include <stdint.h>

// Transaction flow type
typedef enum {
  TRANSACTION_TYPE_SEND = 0,     // Send money flow (address → amount → confirm)
  TRANSACTION_TYPE_RECEIVE = 1,  // Receive money flow (address → confirm)
} transaction_type_t;

// Flow identifiers
typedef enum {
  FLOW_SCAN = 0,            // Scan screen flow (home/idle state)
  FLOW_ONBOARDING,          // Initial device setup flow
  FLOW_MENU,                // Settings menu navigation
  FLOW_TRANSACTION,         // Money movement
  FLOW_FINGERPRINT_MGMT,    // Fingerprint enrollment
  FLOW_FINGERPRINTS_MENU,   // Fingerprints menu navigation
  FLOW_LOCKED,              // Lock screen flow
  FLOW_RECOVERY,            // Recovery (lost phone/device) - future
  FLOW_FIRMWARE_UPDATE,     // Firmware update
  FLOW_WIPE,                // Wipe device - future
  FLOW_PRIVILEGED_ACTIONS,  // Privileged actions (spending limit, contacts, etc.) - future
  FLOW_BRIGHTNESS,          // Brightness adjustment flow
  FLOW_INFO,                // Device info flow
  FLOW_MFG,                 // Manufacturing test flow
  FLOW_COUNT
} flow_id_t;

// Info screen types
typedef enum {
  INFO_SCREEN_ABOUT = 0,      // About screen (device information)
  INFO_SCREEN_REGULATORY = 1  // Regulatory screen
} info_screen_type_t;

// Display controller context
typedef struct {
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

  // Flow navigation state
  union {
    struct {
      fwpb_display_menu_item selected_item;  // Which menu item is highlighted
    } menu;

    struct {
      uint8_t current_page;         // Multi-page digest (0=verification, 1=confirm, 2=updating)
      bool showing_confirm;         // True when on Verify/Cancel screen
      firmware_update_data_t data;  // Firmware update metadata (version, digest, size)
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
    } locked;

    struct {
      info_screen_type_t screen_type;  // Which info screen is being shown
    } info;

    struct {
      send_transaction_data_t send_data;        // Send transaction details
      receive_transaction_data_t receive_data;  // Receive transaction details
      bool is_receive_flow;                     // True = receive, False = send
    } money_movement;

    struct {
      fwpb_display_params_privileged_action params;  // Privileged action parameters
    } privileged_action;
  } nav;

  // Persistent data storage
  device_info_t device_info;
  bool has_device_info;

  // Fingerprint enrollment state
  bool fingerprint_enrolled[3];    // Which slots have enrolled fingerprints
  char fingerprint_labels[3][32];  // Labels for enrolled fingerprints

  // Battery state
  uint8_t battery_percent;
  bool is_charging;

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
  bool initial_screen_shown;
} display_controller_t;

// Display controller functions (public API)
void display_controller_init(void);
void display_controller_handle_ui_event(ui_event_type_t event, const void* data, uint32_t len);
void display_controller_tick(void);
void display_controller_show_initial_screen(void);

// Screen navigation function for use by screens
void display_controller_navigate_to_screen(pb_size_t params_tag, fwpb_display_transition transition,
                                           uint32_t duration_ms);

/**
 * @brief Sets the display rotation flag.
 *
 * @param rotate_180  If true, set the rotate 180 flag; if false, clear it.
 *
 * @note The flag is sent to the UXC with every show_screen command.
 */
void display_controller_set_rotation(bool rotate_180);

// Display action handlers
void display_controller_handle_action_approve(void);
void display_controller_handle_action_cancel(void);
void display_controller_handle_action_back(void);
void display_controller_handle_action_exit(void);
void display_controller_handle_action_exit_with_data(
  uint32_t data);  // For menu items, fingerprint slots
void display_controller_handle_action_menu(void);
void display_controller_handle_action_lock_device(void);
void display_controller_handle_action_power_off(void);
void display_controller_handle_action_start_enrollment(void);
void display_controller_handle_action_delete_fingerprint(uint8_t fingerprint_index);
