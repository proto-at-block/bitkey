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
  FLOW_SCAN = 0,             // Scan screen flow (home/idle state)
  FLOW_ONBOARDING,           // Initial device setup flow
  FLOW_MENU,                 // Settings menu navigation
  FLOW_TRANSACTION,          // Money movement
  FLOW_FINGERPRINT_MGMT,     // Fingerprint enrollment
  FLOW_FINGERPRINTS_MENU,    // Fingerprints menu navigation
  FLOW_LOCKED,               // Lock screen flow
  FLOW_RECOVERY,             // Recovery (lost phone/device) - future
  FLOW_FIRMWARE_UPDATE,      // Firmware update
  FLOW_WIPE,                 // Wipe device - future
  FLOW_PRIVILEGED_ACTIONS,   // Privileged actions (spending limit, contacts, etc.) - future
  FLOW_BRIGHTNESS,           // Brightness adjustment flow
  FLOW_INFO,                 // Device info flow
  FLOW_MFG,                  // Manufacturing test flow
  FLOW_CONFIRMATION,         // Success confirmation flow
  FLOW_ONBOARDING_COMPLETE,  // Onboarding complete screen
  FLOW_GAME,                 // Brick breaker game flow
  FLOW_POWER_OFF,            // Terminal power-off screen
  FLOW_COUNT
} flow_id_t;

// Display controller tick period in milliseconds
#define DISPLAY_TICK_MS         20
#define MS_TO_DISPLAY_TICKS(ms) ((ms) / DISPLAY_TICK_MS)

// Display controller functions (public API)
void display_controller_init(void);
void display_controller_debug_show_confirm_scan(void);
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
void display_controller_handle_action_page_confirmed(void);
