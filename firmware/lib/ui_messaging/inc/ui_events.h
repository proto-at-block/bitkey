#pragma once

#include <stdbool.h>
#include <stdint.h>

// Button types for UI_EVENT_BUTTON payload
typedef enum {
  BUTTON_LEFT = 0x01,
  BUTTON_RIGHT = 0x02,
  BUTTON_BOTH = 0x03,
} button_id_t;

// Button press types for UI_EVENT_BUTTON payload
typedef enum {
  BUTTON_PRESS_SINGLE,      // Short press (any button)
  BUTTON_PRESS_LONG_START,  // Long press threshold reached
  BUTTON_PRESS_LONG_STOP,   // Button released after long press
} button_press_type_t;

// Payload structure for UI_EVENT_BUTTON
typedef struct {
  button_id_t button;
  button_press_type_t type;
  uint32_t duration_ms;
} button_event_payload_t;

// Payload for UI_EVENT_START_SEND_TRANSACTION
typedef struct {
  char amount_sats[32];
  char fee_sats[32];
  char address[128];
} send_transaction_data_t;

// Payload for UI_EVENT_START_RECEIVE_TRANSACTION
typedef struct {
  char address[128];
} receive_transaction_data_t;

// Payload for UI_EVENT_SET_DEVICE_INFO
typedef struct {
  char firmware_version[32];
  char hardware_version[32];
  char serial_number[32];
  uint8_t brightness_percent;  // Display brightness (0-100), added for boot init
} device_info_t;

// Payload for UI_EVENT_MFGTEST_SHOW_SCREEN
typedef struct {
  uint32_t test_mode;
  uint32_t timeout_ms;         // Timeout (ms) to show the specific test screen.
  uint32_t custom_rgb;         // RGB color as 0xRRGGBB for CUSTOM_COLOR mode
  uint8_t brightness_percent;  // Brightness (0 = don't change, 1-100 = set percent)
} mfgtest_show_screen_payload_t;

// Payload for UI_EVENT_MFGTEST_BUTTON_BYPASS
typedef struct {
  bool bypass_enabled;  // true = disable button processing, false = enable
} mfgtest_button_bypass_payload_t;

// Payload for UI_EVENT_MFGTEST_TOUCH_TEST_STATUS
typedef struct {
  uint16_t boxes_remaining;  // Number of touch test boxes remaining
} mfgtest_touch_test_status_payload_t;

// Payload for UI_EVENT_FWUP_START
typedef struct {
  char digest[256];  // Firmware update digest/hash
  uint32_t version;  // Firmware version
  uint32_t size;     // Update size in bytes
} firmware_update_data_t;

// Payload for UI_EVENT_BATTERY_SOC
typedef struct {
  uint8_t battery_percent;  // Battery SOC (0-100)
} battery_soc_data_t;

// Enrollment progress data for fingerprint enrollment events
typedef struct {
  uint32_t samples_remaining;  // How many more samples needed
  uint32_t total_samples;      // Total samples required (for % calculation)
} enrollment_progress_data_t;

// Payload for UI_EVENT_AUTH_SUCCESS (optional - only when authenticated via fingerprint)
typedef struct {
  uint8_t template_index;  // Which fingerprint template was authenticated
} fingerprint_auth_data_t;

/**
 * @brief UI touch event.
 */
typedef struct {
  /**
   * @brief X coordinate of the touch event.
   */
  uint16_t x;

  /**
   * @brief Y coordinate of the touch event.
   */
  uint16_t y;
} ui_event_touch_t;

// Unified UI event types that both LED (W1) and Display (W3) can interpret
// This provides a platform-agnostic way to communicate UI state
typedef enum {
  UI_EVENT_UNSPECIFIED = 0,
  UI_EVENT_LED_CLEAR,  // Clear LED animations (W1-specific)
  UI_EVENT_NO_IDLE,    // Set idle/rest animation to none (ANI_OFF)

  // System events
  UI_EVENT_IDLE,
  UI_EVENT_DISPLAY_READY,  // Display subsystem (UXC) is ready to receive commands

  // Authentication events
  UI_EVENT_AUTH_SUCCESS,
  UI_EVENT_AUTH_FAIL,
  UI_EVENT_AUTH_LOCKED,
  UI_EVENT_AUTH_LOCKED_FROM_FWUP,
  UI_EVENT_AUTH_LOCKED_FROM_ENROLLMENT,
  UI_EVENT_FINGER_DOWN_FROM_LOCKED,
  UI_EVENT_FINGER_DOWN_FROM_UNLOCKED,
  UI_EVENT_FINGERPRINT_GOOD,
  UI_EVENT_FINGERPRINT_BAD,
  UI_EVENT_FINGERPRINT_STATUS,
  UI_EVENT_FINGERPRINT_DELETED,
  UI_EVENT_FINGERPRINT_DELETE_FAILED,

  // Enrollment events
  UI_EVENT_ENROLLMENT_START,
  UI_EVENT_ENROLLMENT_PROGRESS_GOOD,
  UI_EVENT_ENROLLMENT_PROGRESS_BAD,
  UI_EVENT_ENROLLMENT_COMPLETE,
  UI_EVENT_ENROLLMENT_FAILED,

  // Firmware update events
  UI_EVENT_FWUP_START,
  UI_EVENT_FWUP_PROGRESS,
  UI_EVENT_FWUP_COMPLETE,
  UI_EVENT_FWUP_FAILED,

  // Power/charging events
  UI_EVENT_CHARGING,
  UI_EVENT_CHARGING_FINISHED,
  UI_EVENT_CHARGING_FINISHED_PERSISTENT,
  UI_EVENT_CHARGING_UNPLUGGED,
  UI_EVENT_BATTERY_SOC,

  // Capacitive touch event
  UI_EVENT_CAPTOUCH,

  // Manufacturing test events
  UI_EVENT_MFGTEST_PASS,
  UI_EVENT_MFGTEST_FAIL,
  UI_EVENT_MFGTEST_SHOW_SCREEN,
  UI_EVENT_MFGTEST_BUTTON_BYPASS,
  UI_EVENT_MFGTEST_TOUCH,
  UI_EVENT_MFGTEST_TOUCH_TEST_STATUS,

  // Other events
  UI_EVENT_WIPE_STATE,
  UI_EVENT_ERROR,

  // Button events
  UI_EVENT_BUTTON,

  // Display data events (with payloads)
  UI_EVENT_SET_DEVICE_INFO,
  UI_EVENT_START_SEND_TRANSACTION,
  UI_EVENT_START_RECEIVE_TRANSACTION,
  UI_EVENT_SHOW_MENU,
  UI_EVENT_START_PRIVILEGED_ACTION,

  UI_EVENT_MAX
} ui_event_type_t;
