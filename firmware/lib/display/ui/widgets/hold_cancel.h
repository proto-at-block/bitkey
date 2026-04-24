/**
 * @file hold_cancel.h
 * @brief Hold-to-cancel confirmation modal widget
 *
 * Creates a semi-transparent overlay with a centered X icon that requires
 * holding to confirm cancellation. Includes a back button to dismiss.
 */

#pragma once

#include "dot_ring.h"
#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Callback invoked when cancel hold completes
 *
 * @param user_data User data passed to hold_cancel_show()
 */
typedef void (*hold_cancel_complete_cb_t)(void* user_data);

/**
 * @brief Callback invoked when cancel is dismissed (back button pressed)
 *
 * @param user_data User data passed to hold_cancel_show()
 */
typedef void (*hold_cancel_dismiss_cb_t)(void* user_data);

/**
 * @brief Optional configuration for hold-to-cancel presentation.
 */
typedef struct {
  const char* initial_text;
  const char* completed_text;
  const char* followup_title;
  const char* followup_text;
  uint32_t completion_delay_ms;
  uint32_t followup_delay_ms;
  bool hide_after_complete;
} hold_cancel_options_t;

/**
 * @brief Hold-to-cancel modal widget state
 */
typedef struct {
  lv_obj_t* parent;  // Parent screen object

  lv_obj_t* overlay;                // Semi-transparent black overlay
  lv_obj_t* icon_bg;                // Grey circle background for X icon
  lv_obj_t* icon_x;                 // White X icon
  lv_obj_t* icon_check;             // Check icon (shown on completion)
  lv_obj_t* cancel_label;           // "CANCEL" text label below icon
  lv_obj_t* hold_label;             // "HOLD" text label at top (shown while holding)
  dot_ring_t ring;                  // Red dot ring animation
  lv_obj_t* dismiss_btn_container;  // Dismiss button pill container
  lv_obj_t* dismiss_btn_icon;       // Dismiss button back arrow icon

  hold_cancel_complete_cb_t complete_cb;  // Called when hold completes
  hold_cancel_dismiss_cb_t dismiss_cb;    // Called when dismissed
  void* user_data;                        // User data for callbacks

  const char* initial_text;
  const char* completed_text;
  const char* followup_title;
  const char* followup_text;

  bool is_showing;             // True if currently visible
  bool is_initialized;         // True if widget created
  bool hold_completed;         // True if hold duration met, waiting for release
  lv_timer_t* complete_timer;  // Timer for completion delay
  lv_timer_t* followup_timer;  // Timer for dismissing the followup state
  lv_obj_t* followup_container;
  lv_obj_t* followup_title_label;
  lv_obj_t* followup_text_label;
  uint32_t completion_delay_ms;
  uint32_t followup_delay_ms;
  bool hide_after_complete;
} hold_cancel_t;

/**
 * @brief Create hold-to-cancel modal widget
 *
 * Creates the widget structure but does not show it. Call hold_cancel_show()
 * to display the modal.
 *
 * @param parent Parent screen object
 * @param modal Modal widget structure (must be zero-initialized)
 */
void hold_cancel_create(lv_obj_t* parent, hold_cancel_t* modal);

/**
 * @brief Show the hold-to-cancel modal
 *
 * Displays the modal overlay with hold-to-cancel interaction.
 *
 * @param modal Modal widget structure
 * @param complete_cb Callback when hold completes (can be NULL)
 * @param dismiss_cb Callback when dismissed via back button (can be NULL)
 * @param user_data User data passed to callbacks
 */
void hold_cancel_show(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                      hold_cancel_dismiss_cb_t dismiss_cb, void* user_data);

/**
 * @brief Show the hold-to-cancel modal with optional followup content.
 *
 * If followup content is provided, it is shown after the completion delay and
 * the complete callback is deferred until the followup timeout expires.
 *
 * @param modal Modal widget structure
 * @param complete_cb Callback when flow should complete (can be NULL)
 * @param dismiss_cb Callback when dismissed via back button (can be NULL)
 * @param user_data User data passed to callbacks
 * @param options Optional presentation configuration
 */
void hold_cancel_show_with_options(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                                   hold_cancel_dismiss_cb_t dismiss_cb, void* user_data,
                                   const hold_cancel_options_t* options);

/**
 * @brief Show the hold-to-cancel modal with custom text
 *
 * Displays the modal overlay with hold-to-cancel interaction using custom text labels.
 *
 * @param modal Modal widget structure
 * @param complete_cb Callback when hold completes (can be NULL)
 * @param dismiss_cb Callback when dismissed via back button (can be NULL)
 * @param user_data User data passed to callbacks
 * @param initial_text Text to show initially (e.g., "Remove")
 * @param completed_text Text to show when hold completes (e.g., "Removed")
 */
void hold_cancel_show_with_text(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                                hold_cancel_dismiss_cb_t dismiss_cb, void* user_data,
                                const char* initial_text, const char* completed_text);

#if LV_USE_SNAPSHOT
/**
 * @brief Snapshot helper that renders the completed cancel state directly.
 *
 * Shows the completed cancel icon + label state before any followup transition.
 *
 * @param modal Modal widget structure
 * @param options Modal presentation configuration
 */
void hold_cancel_snapshot_show_completed(hold_cancel_t* modal,
                                         const hold_cancel_options_t* options);

/**
 * @brief Snapshot helper that renders the followup state directly.
 *
 * @param modal Modal widget structure
 * @param options Followup presentation configuration
 */
void hold_cancel_snapshot_show_followup(hold_cancel_t* modal, const hold_cancel_options_t* options);

/**
 * @brief Snapshot helper that starts the release rewind from partial progress.
 *
 * @param modal Modal widget structure
 * @param options Modal presentation configuration
 * @param percent Starting ring progress (0-100)
 */
void hold_cancel_snapshot_start_release_reverse(hold_cancel_t* modal,
                                                const hold_cancel_options_t* options,
                                                uint8_t percent);
#endif

/**
 * @brief Stop all internal timers and animations without touching LVGL objects.
 *
 * Call this before deleting the parent screen so that timer callbacks don't fire
 * on already-freed objects.  Unlike hold_cancel_hide/destroy, this does NOT
 * delete any lv_obj_t children (the screen deletion will handle that).
 *
 * @param modal Modal widget structure
 */
void hold_cancel_stop_timers(hold_cancel_t* modal);

/**
 * @brief Hide the hold-to-cancel modal
 *
 * Hides the modal without triggering callbacks.
 *
 * @param modal Modal widget structure
 */
void hold_cancel_hide(hold_cancel_t* modal);

/**
 * @brief Destroy hold-to-cancel modal widget
 *
 * Cleans up all resources.
 *
 * @param modal Modal widget structure
 */
void hold_cancel_destroy(hold_cancel_t* modal);
