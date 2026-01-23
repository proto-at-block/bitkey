/**
 * @file hold_cancel.h
 * @brief Hold-to-cancel confirmation modal widget
 *
 * Creates a semi-transparent overlay with a centered X icon that requires
 * holding to confirm cancellation. Includes a back button to dismiss.
 */

#pragma once

#include "hold_ring.h"
#include "lvgl.h"

#include <stdbool.h>

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
 * @brief Hold-to-cancel modal widget state
 */
typedef struct {
  lv_obj_t* parent;  // Parent screen object

  lv_obj_t* overlay;                // Semi-transparent black overlay
  lv_obj_t* icon_bg;                // Grey circle background for X icon
  lv_obj_t* icon_x;                 // White X icon
  lv_obj_t* cancel_label;           // "CANCEL" text label below icon
  hold_ring_t ring;                 // Red hold ring animation
  lv_obj_t* dismiss_btn_container;  // Dismiss button pill container
  lv_obj_t* dismiss_btn_icon;       // Dismiss button back arrow icon

  hold_cancel_complete_cb_t complete_cb;  // Called when hold completes
  hold_cancel_dismiss_cb_t dismiss_cb;    // Called when dismissed
  void* user_data;                        // User data for callbacks

  const char* initial_text;
  const char* completed_text;

  bool is_showing;      // True if currently visible
  bool is_initialized;  // True if widget created
  bool hold_completed;  // True if hold duration met, waiting for release
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
