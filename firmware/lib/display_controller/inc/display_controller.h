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
  FLOW_ONBOARDING = 0,
  FLOW_MENU,                // Settings menu navigation
  FLOW_TRANSACTION,         // Money movement
  FLOW_FINGERPRINT_MGMT,    // Fingerprint enrollment
  FLOW_FINGERPRINTS_MENU,   // Fingerprints menu navigation
  FLOW_FINGERPRINT_REMOVE,  // Fingerprint remove flow
  FLOW_RECOVERY,            // Recovery (lost phone/device) - future
  FLOW_FIRMWARE_UPDATE,     // Firmware update
  FLOW_WIPE,                // Wipe device - future
  FLOW_PRIVILEGED_ACTIONS,  // Privileged actions (spending limit, contacts, etc.) - future
  FLOW_BRIGHTNESS,          // Brightness adjustment flow
  FLOW_INFO,                // Device info flow
  FLOW_MFG,                 // Manufacturing test flow
  FLOW_COUNT
} flow_id_t;

// Display controller context
typedef struct {
  // State model
  bool is_locked;           // Device requires fingerprint to unlock
  flow_id_t current_flow;   // FLOW_COUNT = no flow, < FLOW_COUNT = active flow
  flow_id_t previous_flow;  // Flow to return to after current flow exits
  bool pending_flow_exit;   // Set by tick handlers to trigger flow exit

  // Current screen command - contains params and transition info
  fwpb_display_show_screen show_screen;

  // Flow navigation state
  union {
    struct {
      uint8_t current_page;  // Onboarding: which page (0-4)
    } onboarding;

    struct {
      uint8_t current_page;  // Transaction: address=0, amount=1, fee=2
      bool showing_confirm;  // True when on Verify/Cancel screen
    } transaction;

    struct {
      fwpb_display_menu_item selected_item;  // Which menu item is highlighted
      uint8_t submenu_index;                 // If in submenu, which screen
    } menu;

    struct {
      uint8_t current_page;  // Multi-page digest
      bool showing_confirm;  // True when on Verify/Cancel screen
    } firmware_update;

    struct {
      uint8_t confirm_count;  // Wipe has 2 confirm screens
    } wipe;

    struct {
      uint8_t current_page;    // Which page (0-3) we're on
      uint32_t total_samples;  // Total samples required for enrollment
      uint32_t samples_done;   // Samples successfully captured so far
      uint8_t slot_index;      // Which slot (0-2) we're enrolling to
    } fingerprint;

    struct {
      uint8_t selected_item;        // Which fingerprint slot is selected (0-2)
      bool showing_detail;          // True when showing fingerprint detail screen
      uint8_t detail_index;         // Which fingerprint detail is being shown
      uint8_t authenticated_index;  // Which fingerprint was authenticated (for animation)
      bool show_authenticated;      // True to trigger authentication animation
    } fingerprint_menu;

    struct {
      uint8_t fingerprint_index;  // Which fingerprint (0-2)
      uint8_t current_page;       // 0 = remove, 1 = confirm, 2 = removed
      uint8_t selected_button;    // Button selection on current page
    } fingerprint_remove;

    struct {
      bool showing_regulatory;  // True = regulatory, False = about
    } info;

    // Generic for other flows
    struct {
      uint8_t current_page;
      bool showing_confirm;
    } generic;
  } nav;

  // Persistent data storage
  device_info_t device_info;
  bool has_device_info;

  // Saved menu state when entering submenus
  fwpb_display_menu_item saved_menu_selection;

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
