/**
 * @file hold_cancel.c
 * @brief Hold-to-cancel confirmation modal widget implementation
 */

#include "hold_cancel.h"

#include "assert.h"
#include "display_action.h"
#include "langpack.h"

#include <string.h>

// Layout configuration
#define ICON_SIZE              120
#define ICON_COLOR_GREY        0x404040      // Grey (default state)
#define ICON_COLOR_RED         0xF84752      // Red (completed state)
#define ICON_COLOR_BLACK       0x000000      // Black
#define ICON_COLOR_GREEN       0xD1FB96      // Green (success state)
#define OVERLAY_OPA            LV_OPA_COVER  // Fully opaque
#define DISMISS_BTN_WIDTH      60
#define DISMISS_BTN_HEIGHT     36
#define DISMISS_BTN_RADIUS     22
#define DISMISS_BTN_TOP_MARGIN 32
#define CANCEL_LABEL_Y         80    // Y offset below vertical center
#define HOLD_LABEL_TOP_MARGIN  80    // Top margin for HOLD label
#define COMPLETION_DELAY_MS    3000  // Delay before triggering callback after hold completes
#define COMPLETED_ICON_Y \
  (-20)  // Y offset from center for completed checkmark (matches confirmed page)
#define COMPLETED_LABEL_Y 60  // Y offset from center for completed label (matches confirmed page)

// Fonts
LV_FONT_DECLARE(cash_sans_mono_regular_30);
LV_FONT_DECLARE(cash_sans_mono_regular_26);
#define FONT_CANCEL (&cash_sans_mono_regular_30)
#define FONT_HOLD   (&cash_sans_mono_regular_26)

// External images
extern const lv_img_dsc_t cross;
extern const lv_img_dsc_t back_arrow;
extern const lv_img_dsc_t check;

// Forward declarations
static void dismiss_button_handler(lv_event_t* e);
static void hold_handler(lv_event_t* e);
static void on_hold_complete(void* user_data);
static void completion_timer_cb(lv_timer_t* timer);

void hold_cancel_create(lv_obj_t* parent, hold_cancel_t* modal) {
  ASSERT(parent != NULL);
  ASSERT(modal != NULL);

  memset(modal, 0, sizeof(hold_cancel_t));
  modal->parent = parent;
  modal->is_initialized = true;
}

void hold_cancel_show(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                      hold_cancel_dismiss_cb_t dismiss_cb, void* user_data) {
  hold_cancel_show_with_text(modal, complete_cb, dismiss_cb, user_data, NULL, NULL);
}

void hold_cancel_show_with_text(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                                hold_cancel_dismiss_cb_t dismiss_cb, void* user_data,
                                const char* initial_text, const char* completed_text) {
  if (!modal || !modal->is_initialized || modal->is_showing) {
    return;
  }

  modal->complete_cb = complete_cb;
  modal->dismiss_cb = dismiss_cb;
  modal->user_data = user_data;
  modal->initial_text = initial_text;
  modal->completed_text = completed_text;
  modal->hold_completed = false;
  modal->complete_timer = NULL;

  // Create semi-transparent black overlay
  // Ensure parent is valid before creating
  if (!modal->parent || !lv_obj_is_valid(modal->parent)) {
    return;
  }

  modal->overlay = lv_obj_create(modal->parent);
  if (!modal->overlay) {
    return;
  }
  lv_obj_set_size(modal->overlay, LV_HOR_RES, LV_VER_RES);
  lv_obj_set_pos(modal->overlay, 0, 0);
  lv_obj_set_style_bg_color(modal->overlay, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(modal->overlay, OVERLAY_OPA, 0);
  lv_obj_set_style_border_opa(modal->overlay, LV_OPA_TRANSP, 0);
  lv_obj_clear_flag(modal->overlay, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(modal->overlay, LV_OBJ_FLAG_CLICKABLE);

  // Create grey circle in center
  modal->icon_bg = lv_obj_create(modal->overlay);
  if (!modal->icon_bg) {
    return;
  }
  lv_obj_set_size(modal->icon_bg, ICON_SIZE, ICON_SIZE);
  lv_obj_set_style_radius(modal->icon_bg, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(modal->icon_bg, lv_color_hex(ICON_COLOR_GREY), 0);
  lv_obj_set_style_bg_opa(modal->icon_bg, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(modal->icon_bg, LV_OPA_TRANSP, 0);
  lv_obj_center(modal->icon_bg);
  lv_obj_add_flag(modal->icon_bg, LV_OBJ_FLAG_CLICKABLE);

  // Add hold event handlers to circle
  lv_obj_add_event_cb(modal->icon_bg, hold_handler, LV_EVENT_PRESSED, modal);
  lv_obj_add_event_cb(modal->icon_bg, hold_handler, LV_EVENT_RELEASED, modal);
  lv_obj_add_event_cb(modal->icon_bg, hold_handler, LV_EVENT_PRESS_LOST, modal);

  // Create white X icon centered in circle
  modal->icon_x = lv_img_create(modal->icon_bg);
  if (!modal->icon_x) {
    return;
  }
  lv_img_set_src(modal->icon_x, &cross);
  lv_obj_center(modal->icon_x);

  // Create check icon centered in circle (hidden initially, shown on completion)
  modal->icon_check = lv_img_create(modal->icon_bg);
  if (modal->icon_check) {
    lv_img_set_src(modal->icon_check, &check);
    lv_obj_center(modal->icon_check);
    lv_obj_add_flag(modal->icon_check, LV_OBJ_FLAG_HIDDEN);
  }

  // Create HOLD label at top (hidden initially, shown while holding)
  modal->hold_label = lv_label_create(modal->overlay);
  if (modal->hold_label) {
    lv_label_set_text(modal->hold_label, langpack_get_string(LANGPACK_ID_MONEY_MOVEMENT_HOLD));
    lv_obj_set_style_text_color(modal->hold_label, lv_color_hex(ICON_COLOR_RED), 0);
    lv_obj_set_style_text_font(modal->hold_label, FONT_HOLD, 0);
    lv_obj_set_style_text_align(modal->hold_label, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_align(modal->hold_label, LV_ALIGN_TOP_MID, 0, HOLD_LABEL_TOP_MARGIN);
    lv_obj_add_flag(modal->hold_label, LV_OBJ_FLAG_HIDDEN);
  }

  // Create label below the icon (use custom text if provided, otherwise use default langpack)
  modal->cancel_label = lv_label_create(modal->overlay);
  if (modal->cancel_label) {
    const char* text = modal->initial_text ? modal->initial_text
                                           : langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCEL);
    lv_label_set_text(modal->cancel_label, text);
    lv_obj_set_style_text_color(modal->cancel_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(modal->cancel_label, FONT_CANCEL, 0);
    lv_obj_set_style_text_align(modal->cancel_label, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_align(modal->cancel_label, LV_ALIGN_TOP_MID, 0, (LV_VER_RES / 2) + CANCEL_LABEL_Y);
  }

  // Create red dot ring on the overlay
  memset(&modal->ring, 0, sizeof(dot_ring_t));
  dot_ring_create(modal->overlay, &modal->ring);

  // Create dismiss button (pill with back arrow) - same style as top_back widget
  modal->dismiss_btn_container = lv_obj_create(modal->overlay);
  if (modal->dismiss_btn_container) {
    lv_obj_set_size(modal->dismiss_btn_container, DISMISS_BTN_WIDTH, DISMISS_BTN_HEIGHT);
    lv_obj_set_style_radius(modal->dismiss_btn_container, DISMISS_BTN_RADIUS, 0);
    lv_obj_set_style_bg_color(modal->dismiss_btn_container, lv_color_hex(ICON_COLOR_GREY), 0);
    lv_obj_set_style_bg_opa(modal->dismiss_btn_container, LV_OPA_80, 0);
    lv_obj_set_style_border_width(modal->dismiss_btn_container, 0, 0);
    lv_obj_set_style_shadow_opa(modal->dismiss_btn_container, LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(modal->dismiss_btn_container, 0, 0);
    lv_obj_clear_flag(modal->dismiss_btn_container, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(modal->dismiss_btn_container, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_align(modal->dismiss_btn_container, LV_ALIGN_TOP_MID, 0, DISMISS_BTN_TOP_MARGIN);
    lv_obj_set_ext_click_area(modal->dismiss_btn_container, 40);  // Match top_back widget
    lv_obj_add_event_cb(modal->dismiss_btn_container, dismiss_button_handler, LV_EVENT_CLICKED,
                        modal);

    // Create back arrow icon in dismiss button
    modal->dismiss_btn_icon = lv_img_create(modal->dismiss_btn_container);
    if (modal->dismiss_btn_icon) {
      lv_img_set_src(modal->dismiss_btn_icon, &back_arrow);
      lv_obj_center(modal->dismiss_btn_icon);
    }
  }

  // Move overlay to foreground to ensure it's visible above all other content
  lv_obj_move_foreground(modal->overlay);

  modal->is_showing = true;
}

void hold_cancel_hide(hold_cancel_t* modal) {
  if (!modal || !modal->is_showing) {
    return;
  }

  // Mark as not showing first to prevent recursive calls
  modal->is_showing = false;

  // Clean up completion timer if running
  if (modal->complete_timer) {
    lv_timer_del(modal->complete_timer);
    modal->complete_timer = NULL;
  }

  // Clean up widgets
  dot_ring_destroy(&modal->ring);

  // Delete overlay
  if (modal->overlay && lv_obj_is_valid(modal->overlay)) {
    lv_obj_add_flag(modal->overlay, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(modal->overlay, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_del_async(modal->overlay);
  }

  modal->overlay = NULL;
  modal->icon_bg = NULL;
  modal->icon_x = NULL;
  modal->icon_check = NULL;
  modal->cancel_label = NULL;
  modal->hold_label = NULL;
  modal->dismiss_btn_container = NULL;
  modal->dismiss_btn_icon = NULL;
}

void hold_cancel_destroy(hold_cancel_t* modal) {
  if (!modal || !modal->is_initialized) {
    return;
  }

  hold_cancel_hide(modal);
  memset(modal, 0, sizeof(hold_cancel_t));
}

// ========================================================================
// Event Handlers
// ========================================================================

static void dismiss_button_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  // Get modal from event user data
  hold_cancel_t* modal = (hold_cancel_t*)lv_event_get_user_data(e);
  if (modal && modal->dismiss_cb) {
    modal->dismiss_cb(modal->user_data);
  }
}

static void hold_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  hold_cancel_t* modal = (hold_cancel_t*)lv_event_get_user_data(e);

  if (!modal) {
    return;
  }

  if (code == LV_EVENT_PRESSED) {
    // Show HOLD label and hide dismiss button
    if (modal->hold_label) {
      lv_obj_clear_flag(modal->hold_label, LV_OBJ_FLAG_HIDDEN);
    }
    if (modal->dismiss_btn_container) {
      lv_obj_add_flag(modal->dismiss_btn_container, LV_OBJ_FLAG_HIDDEN);
    }

    // While holding: X → black, circle → red
    if (modal->icon_x) {
      lv_obj_set_style_img_recolor(modal->icon_x, lv_color_black(), 0);
      lv_obj_set_style_img_recolor_opa(modal->icon_x, LV_OPA_COVER, 0);
    }
    if (modal->icon_bg) {
      lv_obj_set_style_bg_color(modal->icon_bg, lv_color_hex(ICON_COLOR_RED), 0);
    }

    dot_ring_show(&modal->ring);
    dot_ring_animate_fill(&modal->ring, 100, 2000, DOT_RING_COLOR_RED, DOT_RING_FILL_SPLIT,
                          on_hold_complete, modal);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    // Check if hold was completed
    if (modal->hold_completed) {
      // Hold completed - the timer will trigger the callback after the delay
      // If timer is NULL (failed to create or already fired), trigger callback now
      if (!modal->complete_timer && modal->complete_cb) {
        modal->complete_cb(modal->user_data);
      }
      return;
    } else {
      // Hide HOLD label and show dismiss button
      if (modal->hold_label) {
        lv_obj_add_flag(modal->hold_label, LV_OBJ_FLAG_HIDDEN);
      }
      if (modal->dismiss_btn_container) {
        lv_obj_clear_flag(modal->dismiss_btn_container, LV_OBJ_FLAG_HIDDEN);
      }

      // Released before completing - revert to original (X → white, circle → grey, text → initial)
      if (modal->cancel_label) {
        const char* text = modal->initial_text
                             ? modal->initial_text
                             : langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCEL);
        lv_label_set_text(modal->cancel_label, text);
      }
      if (modal->icon_x) {
        lv_obj_set_style_img_recolor_opa(modal->icon_x, LV_OPA_TRANSP, 0);
      }
      if (modal->icon_bg) {
        lv_obj_set_style_bg_color(modal->icon_bg, lv_color_hex(ICON_COLOR_GREY), 0);
      }

      dot_ring_stop(&modal->ring);
      dot_ring_hide(&modal->ring);
    }
  }
}

static void on_hold_complete(void* user_data) {
  hold_cancel_t* modal = (hold_cancel_t*)user_data;

  if (!modal) {
    return;
  }

  // Mark hold as completed
  modal->hold_completed = true;

  // Keep the ring visible at 100% - don't destroy it
  // The ring will be cleaned up when the modal is hidden/destroyed

  // Hide HOLD label
  if (modal->hold_label) {
    lv_obj_add_flag(modal->hold_label, LV_OBJ_FLAG_HIDDEN);
  }

  // On complete: Text → completed text
  if (modal->cancel_label) {
    const char* text = modal->completed_text
                         ? modal->completed_text
                         : langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCELLED);
    lv_label_set_text(modal->cancel_label, text);
  }

  // Hide X icon and circle
  if (modal->icon_x) {
    lv_obj_add_flag(modal->icon_x, LV_OBJ_FLAG_HIDDEN);
  }
  if (modal->icon_bg) {
    lv_obj_add_flag(modal->icon_bg, LV_OBJ_FLAG_HIDDEN);
  }

  // Show check icon with red color to match the ring
  // Reparent to overlay and position to match confirmed page alignment
  if (modal->icon_check) {
    lv_obj_set_parent(modal->icon_check, modal->overlay);
    lv_obj_clear_flag(modal->icon_check, LV_OBJ_FLAG_HIDDEN);
    lv_obj_set_style_img_recolor(modal->icon_check, lv_color_hex(ICON_COLOR_RED), 0);
    lv_obj_set_style_img_recolor_opa(modal->icon_check, LV_OPA_COVER, 0);
    lv_obj_align(modal->icon_check, LV_ALIGN_CENTER, 0, COMPLETED_ICON_Y);
  }

  // Update the label color and position to match confirmed page alignment
  if (modal->cancel_label) {
    lv_obj_set_style_text_color(modal->cancel_label, lv_color_hex(ICON_COLOR_RED), 0);
    lv_obj_align(modal->cancel_label, LV_ALIGN_CENTER, 0, COMPLETED_LABEL_Y);
  }

  // Start timer for completion delay - callback will be triggered after the delay
  modal->complete_timer = lv_timer_create(completion_timer_cb, COMPLETION_DELAY_MS, modal);
  if (modal->complete_timer) {
    lv_timer_set_repeat_count(modal->complete_timer, 1);
  } else {
    // Timer creation failed - trigger callback immediately as fallback
    if (modal->complete_cb) {
      modal->complete_cb(modal->user_data);
    }
  }
}

static void completion_timer_cb(lv_timer_t* timer) {
  hold_cancel_t* modal = (hold_cancel_t*)lv_timer_get_user_data(timer);

  if (!modal) {
    return;
  }

  modal->complete_timer = NULL;

  // Trigger the complete callback after the delay
  if (modal->complete_cb) {
    modal->complete_cb(modal->user_data);
  }
}
