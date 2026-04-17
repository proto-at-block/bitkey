/**
 * @file screen_mfg_touch_debug_internal.h
 * @brief Shared state and API for touch debug screen modules (mfgtest only).
 *
 * This header is internal to the screen_mfg_touch_debug_*.c files.
 * It exposes shared screen infrastructure so each mode file can:
 * - Access the common screen object and title label
 * - Set the current mode
 * - Call clear_screen_content() before setting up its UI
 * - Call show_menu() to return to the main menu
 */

#pragma once

#ifdef MFGTEST

#include "lvgl/lvgl.h"
#include "touch.h"
#include "touch_mfgtest.h"

#include <stdbool.h>
#include <stdint.h>

// =============================================================================
// Screen configuration defines (shared across all mode files)
// =============================================================================
#define TRAIL_COLOR          lv_color_hex(0x00FFFF)
#define TAP_DOT_COLOR        lv_color_hex(0xFFFF00)
#define TAP_DOT_RADIUS       6
#define CAP_READ_INTERVAL_MS 10000
#define SCAN_POLL_TIMEOUT_MS 2000

// =============================================================================
// Mode enum — all modes across all files
// =============================================================================
typedef enum {
  MODE_MENU,
  MODE_ROBOT_TEST,
  MODE_TOUCH_VIEWER_V2,
  MODE_TOUCH_VIEWER_V2_SETTINGS,
  MODE_CAP_VIEWER,
  MODE_SELF_CAP_VIEWER,
  MODE_PLACEHOLDER,
  MODE_CALIBRATE_TEST,
  MODE_FORCE_FWUP,
  MODE_POWER_MODE,
  MODE_TAP_LATENCY,
  MODE_COLLECT_DATA,
  MODE_DISPLAY_TEST,
} screen_mode_t;

// =============================================================================
// Shared screen state (defined in screen_mfg_touch_debug.c)
// =============================================================================
extern lv_obj_t* td_screen;
extern lv_obj_t* td_title_label;
extern screen_mode_t td_current_mode;

// Cached touch firmware info (read once on first menu display)
extern uint8_t td_touch_fw_version;
extern bool td_touch_fw_version_read;

// =============================================================================
// Shared functions (implemented in screen_mfg_touch_debug.c)
// =============================================================================

/**
 * @brief Clean the screen and reset all mode-specific state.
 *
 * Every show_*() function must call this before setting up its UI.
 * Handles timer deletion, fwup_in_progress restoration, and child cleanup.
 */
void td_clear_screen_content(void);

/**
 * @brief Return to the touch debug main menu.
 */
void td_show_menu(void);

/**
 * @brief Screen delete event handler — resets all state.
 *
 * Must be re-registered by modes that remove screen event callbacks
 * (e.g., display test).
 */
void td_screen_delete_handler(lv_event_t* e);

// =============================================================================
// Per-file reset functions (called by td_clear_screen_content and destroy)
// =============================================================================
void td_viewers_reset_state(void);
void td_viewers_cleanup_timers(void);
void td_diag_reset_state(void);
void td_diag_cleanup_timers(void);
void td_diag_cleanup_mode(void);

// =============================================================================
// Mode entry points (called from menu_item_click_handler)
// =============================================================================

// Viewers (screen_mfg_touch_debug_viewers.c)
void td_show_touch_viewer_v2_settings(void);
void td_show_cap_viewer(void);
void td_show_self_cap_viewer(void);
void td_show_calibrate_test(void);
void td_show_power_mode(void);

// Diagnostics (screen_mfg_touch_debug_diag.c)
void td_show_force_fwup(void);
void td_show_collect_data(void);
void td_show_display_test(void);

#endif /* MFGTEST */
