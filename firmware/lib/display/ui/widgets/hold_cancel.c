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
#define ICON_SIZE                   120
#define ICON_COLOR_GREY             0x404040      // Grey (default state)
#define ICON_COLOR_RED              0xF84752      // Red (completed state)
#define OVERLAY_OPA                 LV_OPA_COVER  // Fully opaque
#define DISMISS_BTN_WIDTH           60
#define DISMISS_BTN_HEIGHT          36
#define DISMISS_BTN_RADIUS          22
#define DISMISS_BTN_TOP_MARGIN      32
#define CANCEL_LABEL_Y              80  // Y offset below vertical center
#define HOLD_LABEL_TOP_MARGIN       80  // Top margin for HOLD label
#define DEFAULT_COMPLETION_DELAY_MS 3000
#define DEFAULT_FOLLOWUP_DELAY_MS   4000
#define COMPLETED_ICON_Y \
  (-20)  // Y offset from center for completed checkmark (matches confirmed page)
#define COMPLETED_LABEL_Y               60  // Y offset from center for completed label (matches confirmed page)
#define FOLLOWUP_TITLE_COLOR            0xADADAD
#define FOLLOWUP_TEXT_CONTAINER_WIDTH   400
#define FOLLOWUP_TEXT_CONTAINER_PAD_X   24
#define FOLLOWUP_TEXT_CONTAINER_PAD_Y   16
#define FOLLOWUP_TEXT_CONTAINER_ROW_GAP 18
#define FOLLOWUP_TEXT_LINE_SPACING      8
#define HOLD_TO_CANCEL_DURATION_MS      2000

// Fonts
LV_FONT_DECLARE(cash_sans_mono_regular_24);
LV_FONT_DECLARE(cash_sans_mono_regular_28);
LV_FONT_DECLARE(cash_sans_mono_regular_30);
LV_FONT_DECLARE(cash_sans_mono_regular_26);
#define FONT_CANCEL         (&cash_sans_mono_regular_30)
#define FONT_HOLD           (&cash_sans_mono_regular_26)
#define FONT_FOLLOWUP_TITLE (&cash_sans_mono_regular_24)
#define FONT_FOLLOWUP_BODY  (&cash_sans_mono_regular_28)

// External images
extern const lv_img_dsc_t cross;
extern const lv_img_dsc_t back_arrow;
extern const lv_img_dsc_t check;

// Forward declarations
static void dismiss_button_handler(lv_event_t* e);
static void hold_handler(lv_event_t* e);
static void on_hold_complete(void* user_data);
static void completion_timer_cb(lv_timer_t* timer);
static void followup_timer_cb(lv_timer_t* timer);
static bool hold_cancel_has_followup(const hold_cancel_t* modal);
static uint32_t hold_cancel_completion_delay_ms(const hold_cancel_t* modal);
static uint32_t hold_cancel_followup_delay_ms(const hold_cancel_t* modal);
static void hold_cancel_complete_now(hold_cancel_t* modal);
static bool hold_cancel_show_followup_screen(hold_cancel_t* modal);
static void hold_cancel_cleanup_followup_screen(hold_cancel_t* modal);
static void hold_cancel_clear_object_refs(hold_cancel_t* modal);

static void hold_cancel_set_icon_color(lv_obj_t* icon, lv_color_t color) {
  if (!icon || !lv_obj_is_valid(icon)) {
    return;
  }

  lv_obj_set_style_img_recolor(icon, color, 0);
  lv_obj_set_style_img_recolor_opa(icon, LV_OPA_COVER, 0);
}

void hold_cancel_create(lv_obj_t* parent, hold_cancel_t* modal) {
  ASSERT(parent != NULL);
  ASSERT(modal != NULL);

  memset(modal, 0, sizeof(hold_cancel_t));
  modal->parent = parent;
  modal->is_initialized = true;
}

void hold_cancel_show(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                      hold_cancel_dismiss_cb_t dismiss_cb, void* user_data) {
  hold_cancel_show_with_options(modal, complete_cb, dismiss_cb, user_data, NULL);
}

void hold_cancel_show_with_options(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                                   hold_cancel_dismiss_cb_t dismiss_cb, void* user_data,
                                   const hold_cancel_options_t* options) {
  if (!modal || !modal->is_initialized || modal->is_showing) {
    return;
  }

  modal->complete_cb = complete_cb;
  modal->dismiss_cb = dismiss_cb;
  modal->user_data = user_data;
  modal->initial_text = options ? options->initial_text : NULL;
  modal->completed_text = options ? options->completed_text : NULL;
  modal->followup_title = options ? options->followup_title : NULL;
  modal->followup_text = options ? options->followup_text : NULL;
  modal->hold_completed = false;
  modal->complete_timer = NULL;
  modal->followup_timer = NULL;
  modal->followup_container = NULL;
  modal->followup_title_label = NULL;
  modal->followup_text_label = NULL;
  modal->completion_delay_ms = options ? options->completion_delay_ms : 0;
  modal->followup_delay_ms = options ? options->followup_delay_ms : 0;
  modal->hide_after_complete = options ? options->hide_after_complete : false;

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
  lv_obj_clear_flag(modal->icon_bg, LV_OBJ_FLAG_PRESS_LOCK);

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
  hold_cancel_set_icon_color(modal->icon_x, lv_color_white());
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
    lv_label_set_text(modal->hold_label, langpack_get_string(LANGPACK_ID_HOLD_CANCEL_HOLD));
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
    lv_obj_clear_flag(modal->dismiss_btn_container, LV_OBJ_FLAG_PRESS_LOCK);
    lv_obj_align(modal->dismiss_btn_container, LV_ALIGN_TOP_MID, 0, DISMISS_BTN_TOP_MARGIN);
    lv_obj_set_ext_click_area(modal->dismiss_btn_container, 40);  // Match top_back widget
    lv_obj_add_event_cb(modal->dismiss_btn_container, dismiss_button_handler, LV_EVENT_CLICKED,
                        modal);

    // Create back arrow icon in dismiss button
    modal->dismiss_btn_icon = lv_img_create(modal->dismiss_btn_container);
    if (modal->dismiss_btn_icon) {
      lv_img_set_src(modal->dismiss_btn_icon, &back_arrow);
      lv_obj_set_style_img_recolor(modal->dismiss_btn_icon, lv_color_white(), 0);
      lv_obj_set_style_img_recolor_opa(modal->dismiss_btn_icon, LV_OPA_COVER, 0);
      lv_obj_center(modal->dismiss_btn_icon);
    }
  }

  // Move overlay to foreground to ensure it's visible above all other content
  lv_obj_move_foreground(modal->overlay);

  modal->is_showing = true;
}

void hold_cancel_show_with_text(hold_cancel_t* modal, hold_cancel_complete_cb_t complete_cb,
                                hold_cancel_dismiss_cb_t dismiss_cb, void* user_data,
                                const char* initial_text, const char* completed_text) {
  hold_cancel_options_t options = {
    .initial_text = initial_text,
    .completed_text = completed_text,
  };

  hold_cancel_show_with_options(modal, complete_cb, dismiss_cb, user_data, &options);
}

#if LV_USE_SNAPSHOT
static void snapshot_advance_lvgl_time(uint32_t elapsed_ms) {
  const uint32_t frame_step_ms = 16;
  uint32_t remaining_ms = elapsed_ms;

  while (remaining_ms > 0) {
    uint32_t delta_ms = remaining_ms > frame_step_ms ? frame_step_ms : remaining_ms;
    lv_tick_inc(delta_ms);
    lv_timer_handler();
    lv_refr_now(NULL);
    remaining_ms -= delta_ms;
  }
}

static uint32_t snapshot_hold_elapsed_ms(uint8_t percent, uint32_t full_duration_ms) {
  if (percent > 100) {
    percent = 100;
  }

  return (full_duration_ms * percent) / 100;
}

void hold_cancel_snapshot_show_followup(hold_cancel_t* modal,
                                        const hold_cancel_options_t* options) {
  if (!modal || !modal->is_initialized) {
    return;
  }

  if (!modal->is_showing) {
    hold_cancel_show_with_options(modal, NULL, NULL, NULL, options);
    if (!modal->is_showing) {
      return;
    }
  } else {
    modal->followup_title = options ? options->followup_title : NULL;
    modal->followup_text = options ? options->followup_text : NULL;
  }

  if (modal->followup_container && lv_obj_is_valid(modal->followup_container)) {
    return;
  }

  hold_cancel_show_followup_screen(modal);
}

void hold_cancel_snapshot_start_release_reverse(hold_cancel_t* modal,
                                                const hold_cancel_options_t* options,
                                                uint8_t percent) {
  if (!modal || !modal->is_initialized) {
    return;
  }

  if (!modal->is_showing) {
    hold_cancel_show_with_options(modal, NULL, NULL, NULL, options);
    if (!modal->is_showing) {
      return;
    }
  } else {
    modal->initial_text = options ? options->initial_text : NULL;
    modal->completed_text = options ? options->completed_text : NULL;
    modal->followup_title = options ? options->followup_title : NULL;
    modal->followup_text = options ? options->followup_text : NULL;
  }

  if (!modal->icon_bg) {
    return;
  }

  lv_obj_send_event(modal->icon_bg, LV_EVENT_PRESSED, NULL);
  snapshot_advance_lvgl_time(snapshot_hold_elapsed_ms(percent, HOLD_TO_CANCEL_DURATION_MS));
  lv_obj_send_event(modal->icon_bg, LV_EVENT_RELEASED, NULL);
}
#endif

void hold_cancel_stop_timers(hold_cancel_t* modal) {
  if (!modal) {
    return;
  }

  if (modal->is_showing) {
    dot_ring_stop(&modal->ring);
  }
  if (modal->complete_timer) {
    lv_timer_del(modal->complete_timer);
    modal->complete_timer = NULL;
  }
  if (modal->followup_timer) {
    lv_timer_del(modal->followup_timer);
    modal->followup_timer = NULL;
  }
}

static void hold_cancel_clear_object_refs(hold_cancel_t* modal) {
  if (!modal) {
    return;
  }

  modal->overlay = NULL;
  modal->icon_bg = NULL;
  modal->icon_x = NULL;
  modal->icon_check = NULL;
  modal->cancel_label = NULL;
  modal->hold_label = NULL;
  modal->dismiss_btn_container = NULL;
  modal->dismiss_btn_icon = NULL;
  modal->followup_container = NULL;
  modal->followup_title_label = NULL;
  modal->followup_text_label = NULL;
}

void hold_cancel_hide(hold_cancel_t* modal) {
  if (!modal || !modal->is_showing) {
    return;
  }

  // Mark as not showing first to prevent recursive calls
  modal->is_showing = false;

  // Clean up timers
  hold_cancel_stop_timers(modal);

  // Clean up widgets
  dot_ring_destroy(&modal->ring);

  // Delete overlay
  if (modal->overlay && lv_obj_is_valid(modal->overlay)) {
    lv_obj_add_flag(modal->overlay, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(modal->overlay, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_del_async(modal->overlay);
  }

  hold_cancel_clear_object_refs(modal);
}

void hold_cancel_destroy(hold_cancel_t* modal) {
  if (!modal || !modal->is_initialized) {
    return;
  }

  modal->is_showing = false;
  hold_cancel_stop_timers(modal);
  dot_ring_destroy(&modal->ring);

  if (modal->overlay && lv_obj_is_valid(modal->overlay)) {
    lv_obj_del(modal->overlay);
  }

  hold_cancel_clear_object_refs(modal);
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
    if (modal->cancel_label) {
      lv_label_set_text(modal->cancel_label, "");
    }

    // Show HOLD label and hide dismiss button
    if (modal->hold_label) {
      lv_obj_clear_flag(modal->hold_label, LV_OBJ_FLAG_HIDDEN);
    }
    if (modal->dismiss_btn_container) {
      lv_obj_add_flag(modal->dismiss_btn_container, LV_OBJ_FLAG_HIDDEN);
    }

    // While holding: X → black, circle → red
    if (modal->icon_x) {
      hold_cancel_set_icon_color(modal->icon_x, lv_color_black());
    }
    if (modal->icon_bg) {
      lv_obj_set_style_bg_color(modal->icon_bg, lv_color_hex(ICON_COLOR_RED), 0);
    }

    dot_ring_show(&modal->ring);
    dot_ring_animate_fill_from_current(&modal->ring, 100, HOLD_TO_CANCEL_DURATION_MS,
                                       DOT_RING_COLOR_RED, DOT_RING_FILL_SPLIT, on_hold_complete,
                                       modal);
  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    // Check if hold was completed
    if (modal->hold_completed) {
      // Hold completed - the timer will trigger the callback after the delay
      // If timer is NULL (failed to create or already fired), trigger callback now
      if (!modal->complete_timer && !modal->followup_timer) {
        hold_cancel_complete_now(modal);
      }
      return;
    } else {
      if (dot_ring_animate_release(&modal->ring, HOLD_TO_CANCEL_DURATION_MS)) {
        return;
      }

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
        hold_cancel_set_icon_color(modal->icon_x, lv_color_white());
      }
      if (modal->icon_bg) {
        lv_obj_set_style_bg_color(modal->icon_bg, lv_color_hex(ICON_COLOR_GREY), 0);
      }
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
  modal->complete_timer =
    lv_timer_create(completion_timer_cb, hold_cancel_completion_delay_ms(modal), modal);
  if (modal->complete_timer) {
    lv_timer_set_repeat_count(modal->complete_timer, 1);
  } else {
    // Timer creation failed - trigger callback immediately as fallback
    hold_cancel_complete_now(modal);
  }
}

static void completion_timer_cb(lv_timer_t* timer) {
  hold_cancel_t* modal = (hold_cancel_t*)lv_timer_get_user_data(timer);

  if (!modal) {
    return;
  }

  modal->complete_timer = NULL;

  if (hold_cancel_has_followup(modal)) {
    if (!hold_cancel_show_followup_screen(modal)) {
      hold_cancel_complete_now(modal);
      hold_cancel_hide(modal);
      return;
    }

    modal->followup_timer =
      lv_timer_create(followup_timer_cb, hold_cancel_followup_delay_ms(modal), modal);
    if (modal->followup_timer) {
      lv_timer_set_repeat_count(modal->followup_timer, 1);
      return;
    }

    hold_cancel_complete_now(modal);
    hold_cancel_hide(modal);
    return;
  }

  hold_cancel_complete_now(modal);
}

static void followup_timer_cb(lv_timer_t* timer) {
  hold_cancel_t* modal = (hold_cancel_t*)lv_timer_get_user_data(timer);

  if (!modal) {
    return;
  }

  modal->followup_timer = NULL;

  if (!modal->complete_cb) {
    hold_cancel_hide(modal);
    return;
  }

  // Defer the underlying action until after the followup has been visible for
  // its full display window.
  hold_cancel_complete_now(modal);

  // Default behavior matches the original cancelled state: once the action is
  // dispatched, keep the modal visible and let the next screen transition own
  // teardown. Some flows (for example confirm scan) do not navigate on cancel,
  // so they opt into hiding after callback completion.
  if (modal->hide_after_complete) {
    hold_cancel_hide(modal);
  }
}

static bool hold_cancel_has_followup(const hold_cancel_t* modal) {
  return modal && (modal->followup_title || modal->followup_text);
}

static uint32_t hold_cancel_completion_delay_ms(const hold_cancel_t* modal) {
  return (modal && modal->completion_delay_ms > 0) ? modal->completion_delay_ms
                                                   : DEFAULT_COMPLETION_DELAY_MS;
}

static uint32_t hold_cancel_followup_delay_ms(const hold_cancel_t* modal) {
  return (modal && modal->followup_delay_ms > 0) ? modal->followup_delay_ms
                                                 : DEFAULT_FOLLOWUP_DELAY_MS;
}

static void hold_cancel_complete_now(hold_cancel_t* modal) {
  if (modal && modal->complete_cb) {
    hold_cancel_complete_cb_t complete_cb = modal->complete_cb;
    void* user_data = modal->user_data;

    modal->complete_cb = NULL;
    complete_cb(user_data);
  }
}

static bool hold_cancel_show_followup_screen(hold_cancel_t* modal) {
  lv_obj_t* text_container = NULL;

  if (!modal || !modal->overlay || !lv_obj_is_valid(modal->overlay)) {
    return false;
  }

  modal->followup_container = lv_obj_create(modal->overlay);
  if (!modal->followup_container) {
    return false;
  }

  lv_obj_set_size(modal->followup_container, LV_HOR_RES, LV_VER_RES);
  lv_obj_set_pos(modal->followup_container, 0, 0);
  lv_obj_set_style_bg_color(modal->followup_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(modal->followup_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_opa(modal->followup_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(modal->followup_container, 0, 0);
  lv_obj_set_layout(modal->followup_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(modal->followup_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(modal->followup_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_clear_flag(modal->followup_container, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
  lv_obj_move_foreground(modal->followup_container);

  // Match the phone handoff prompt geometry so cancel followups feel like the
  // same in-app guidance pattern.
  text_container = lv_obj_create(modal->followup_container);
  if (!text_container) {
    hold_cancel_cleanup_followup_screen(modal);
    return false;
  }

  lv_obj_set_size(text_container, FOLLOWUP_TEXT_CONTAINER_WIDTH, LV_SIZE_CONTENT);
  lv_obj_set_style_bg_color(text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(text_container, 0, 0);
  lv_obj_set_style_pad_left(text_container, FOLLOWUP_TEXT_CONTAINER_PAD_X, 0);
  lv_obj_set_style_pad_right(text_container, FOLLOWUP_TEXT_CONTAINER_PAD_X, 0);
  lv_obj_set_style_pad_top(text_container, FOLLOWUP_TEXT_CONTAINER_PAD_Y, 0);
  lv_obj_set_style_pad_bottom(text_container, FOLLOWUP_TEXT_CONTAINER_PAD_Y, 0);
  lv_obj_set_style_pad_row(text_container, FOLLOWUP_TEXT_CONTAINER_ROW_GAP, 0);
  lv_obj_set_layout(text_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(text_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(text_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_clear_flag(text_container, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);

  if (modal->followup_title) {
    modal->followup_title_label = lv_label_create(text_container);
    if (!modal->followup_title_label) {
      hold_cancel_cleanup_followup_screen(modal);
      return false;
    }

    lv_label_set_text(modal->followup_title_label, modal->followup_title);
    lv_obj_set_width(modal->followup_title_label, LV_PCT(100));
    lv_obj_set_style_text_color(modal->followup_title_label, lv_color_hex(FOLLOWUP_TITLE_COLOR), 0);
    lv_obj_set_style_text_font(modal->followup_title_label, FONT_FOLLOWUP_TITLE, 0);
    lv_obj_set_style_text_align(modal->followup_title_label, LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_long_mode(modal->followup_title_label, LV_LABEL_LONG_WRAP);
  }

  if (modal->followup_text) {
    modal->followup_text_label = lv_label_create(text_container);
    if (!modal->followup_text_label) {
      hold_cancel_cleanup_followup_screen(modal);
      return false;
    }

    lv_label_set_text(modal->followup_text_label, modal->followup_text);
    lv_obj_set_style_text_color(modal->followup_text_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(modal->followup_text_label, FONT_FOLLOWUP_BODY, 0);
    lv_obj_set_style_text_line_space(modal->followup_text_label, FOLLOWUP_TEXT_LINE_SPACING, 0);
    lv_obj_set_style_text_align(modal->followup_text_label, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_set_width(modal->followup_text_label, LV_PCT(100));
    lv_label_set_long_mode(modal->followup_text_label, LV_LABEL_LONG_WRAP);
  }

  return true;
}

static void hold_cancel_cleanup_followup_screen(hold_cancel_t* modal) {
  if (!modal) {
    return;
  }

  if (modal->followup_container && lv_obj_is_valid(modal->followup_container)) {
    lv_obj_del(modal->followup_container);
  }

  modal->followup_container = NULL;
  modal->followup_title_label = NULL;
  modal->followup_text_label = NULL;
}
