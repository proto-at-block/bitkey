#include "screen_privileged_action.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "ui.h"
#include "widgets/address_display.h"
#include "widgets/approval_button.h"
#include "widgets/dot_ring.h"
#include "widgets/hold_cancel.h"
#include "widgets/nfc_dots_animation.h"
#include "widgets/top_menu.h"

#include <stdio.h>
#include <string.h>

// SAP combined action display name lookup via langpack. Keep in sync with sap_action_t.
#define SAP_ACTION_DISPLAY_COUNT 32

static const langpack_string_id_t sap_action_langpack_ids[SAP_ACTION_DISPLAY_COUNT] = {
  LANGPACK_ID_SAP_SET_SPEND_WITHOUT_HARDWARE,
  LANGPACK_ID_SAP_DISABLE_SPEND_WITHOUT_HARDWARE,
  LANGPACK_ID_SAP_SET_VERIFICATION_THRESHOLD,
  LANGPACK_ID_SAP_SET_RECOVERY_EMAIL,
  LANGPACK_ID_SAP_DISABLE_RECOVERY_EMAIL,
  LANGPACK_ID_SAP_SET_RECOVERY_PHONE,
  LANGPACK_ID_SAP_DISABLE_RECOVERY_PHONE,
  LANGPACK_ID_SAP_SET_RECOVERY_PUSH_NOTIFICATIONS,
  LANGPACK_ID_SAP_DISABLE_RECOVERY_PUSH_NOTIFICATIONS,
  LANGPACK_ID_SAP_ADD_RECOVERY_CONTACT,
  LANGPACK_ID_SAP_REMOVE_RECOVERY_CONTACT,
  LANGPACK_ID_SAP_ADD_BENEFICIARY,
  LANGPACK_ID_SAP_REMOVE_BENEFICIARY,
  LANGPACK_ID_SAP_ACCEPT_RECOVERY_CONTACTS_INVITE,
  LANGPACK_ID_SAP_ACCEPT_BENEFICIARIES_INVITE,
  LANGPACK_ID_SAP_INITIATE_LOST_APP_RECOVERY,
  LANGPACK_ID_SAP_CANCEL_LOST_APP_RECOVERY,
  LANGPACK_ID_SAP_DELETE_ACCOUNT,
  LANGPACK_ID_SAP_ROTATE_APP_AUTH_KEYS,
  LANGPACK_ID_SAP_UPDATE_DESCRIPTOR_BACKUPS,
  LANGPACK_ID_SAP_ROTATE_SPENDING_KEYSET,
  LANGPACK_ID_EEK_RESTORATION_UNSEAL,
  LANGPACK_ID_FULL_ACCOUNT_CLOUD_BACKUP_RESTORATION,
  LANGPACK_ID_RECOVER_DATA,
  LANGPACK_ID_START_RECOVERY,
  LANGPACK_ID_COMPLETE_WALLET,
  LANGPACK_ID_UPGRADE_WALLET,
  LANGPACK_ID_SAP_CANCEL_LOST_HARDWARE_RECOVERY,
  LANGPACK_ID_SAP_CANCEL_CONFLICTING_RECOVERY,
  LANGPACK_ID_INITIATE_WALLET_UPGRADE,
  LANGPACK_ID_SAP_REMOVE_RECOVERY_CUSTOMER,
  LANGPACK_ID_SAP_REMOVE_BENEFACTOR,
};

static const char* get_privileged_action_title(
  const fwpb_display_params_privileged_action* params) {
  if (params->title[0] != '\0') {
    return params->title;
  }
  if (params->sap_action < SAP_ACTION_DISPLAY_COUNT) {
    return langpack_get_string(sap_action_langpack_ids[params->sap_action]);
  }
  return langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_UNKNOWN_ERROR);
}

// Screen configuration
#define MAX_PAGES 5

// Timing
#define HOLD_TO_CONFIRM_DURATION_MS 2000
#define CONFIRMED_DELAY_MS          2500
#define STEP_DISPLAY_MS             6000
#define HOLD_DISPLAY_MS             1500
#define HEADER_FADE_DURATION_MS     160
#define MENU_BUTTON_HOLD_OPA        LV_OPA_50

// Layout configuration
#define HEADER_HEIGHT               140
#define HEADER_PADDING_TOP          20
#define HEADER_PADDING_BOTTOM       20
#define TITLE_MARGIN_TOP            0
#define CONTENT_ACTION_NUDGE_Y      4
#define CONTENT_STRING_NUDGE_Y      0
#define CONTENT_LABEL_VALUE_SPACING 16
#define CONFIRMED_LABEL_Y           60
#define SCAN_TEXT_CONTAINER_PADDING 16
#define SCAN_SCREEN_BRIGHTNESS      100

// Colors
#define COLOR_TITLE  0xADADAD
#define COLOR_CANCEL 0xF84752
#define COLOR_RING   APPROVAL_BUTTON_RING_COLOR

// Fonts
#define FONT_HEADER (&cash_sans_mono_regular_26)
#define FONT_TITLE  (&cash_sans_mono_regular_24)
#define FONT_TEXT   (&cash_sans_mono_regular_28)
#define FONT_SCAN   (&cash_sans_mono_regular_36)

// External image declarations
extern const lv_img_dsc_t check;

// Screen state
static lv_obj_t* screen = NULL;
static lv_obj_t* header = NULL;
static lv_obj_t* header_title = NULL;
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* page_containers[MAX_PAGES] = {0};
static int num_pages = 0;
static int current_page_index = 0;

// Top menu button (in header)
static top_menu_t menu_button;

// Cached params for rendering
static fwpb_display_params_privileged_action cached_params;

// Dot ring widget for approve action
static dot_ring_t approve_ring;

// Scan page NFC dots animation
static nfc_dots_animation_t scan_nfc_dots;
static lv_obj_t* scan_text_container = NULL;

// Confirmed page elements
static lv_obj_t* confirmed_checkmark = NULL;
static lv_obj_t* confirmed_label = NULL;
static lv_obj_t* check_button_obj = NULL;
static lv_timer_t* confirmed_timer = NULL;
static bool showing_confirmed_page = false;
static int confirmed_next_page_index = -1;

typedef enum {
  HEADER_PROMPT_MODE_STEP = 0,
  HEADER_PROMPT_MODE_HOLD_TO_CONFIRM = 1,
  HEADER_PROMPT_MODE_KEEP_HOLDING = 2,
} header_prompt_mode_t;

static lv_timer_t* header_hint_timer = NULL;
static header_prompt_mode_t header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static header_prompt_mode_t pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
static bool header_cycle_enabled = false;
static bool check_button_held = false;
static char step_indicator_text[32] = {0};

// Cancel modal
static hold_cancel_t cancel_modal;

// Address display widget (for multi-page addresses/hashes)
static address_display_t address_widget;

// Forward declarations
static void check_button_event_handler(lv_event_t* e);
static void menu_button_custom_handler(lv_event_t* e);
static void on_approve_complete(void* user_data);
static void on_cancel_complete(void* user_data);
static void on_cancel_dismiss(void* user_data);
static void scroll_to_page(int page_index, bool animate);
static void update_step_indicator(int current, int total);
static void create_page_content(int page_index);
static lv_obj_t* create_check_button(lv_obj_t* parent);
static int calculate_total_pages(const fwpb_display_params_privileged_action* params);
static void show_confirmed_page(int next_page_index);
static void hide_confirmed_page(void);
static void confirmed_timer_cb(lv_timer_t* timer);
static void stop_header_hint_cycle(void);
static void restart_header_hint_cycle(void);
static void header_hint_timer_cb(lv_timer_t* timer);
static void set_header_prompt_mode(header_prompt_mode_t mode, bool animate);
static lv_coord_t get_top_group_bottom_screen(void);
static void align_content_between_header_and_check_button(lv_obj_t* parent, lv_obj_t* content,
                                                          lv_coord_t center_nudge_y);
static void align_address_page_between_header_and_check_button(lv_obj_t* parent,
                                                               address_display_t* widget,
                                                               lv_coord_t center_nudge_y);
static void header_text_opa_anim_cb(void* var, int32_t value);
static void header_icon_recolor_opa_anim_cb(void* var, int32_t value);
static void header_fade_out_ready_cb(lv_anim_t* anim);

static lv_obj_t* create_check_button(lv_obj_t* parent) {
  check_button_obj = approval_button_create(parent, check_button_event_handler);
  return check_button_obj;
}

static int calculate_total_pages(const fwpb_display_params_privileged_action* params) {
  int content_pages = 0;

  switch (params->which_action) {
    case fwpb_display_params_privileged_action_confirm_address_tag: {
      address_display_t temp_widget;
      address_display_init(&temp_widget, params->action.confirm_address.address);
      content_pages = address_display_get_page_count(&temp_widget);
      break;
    }
    case fwpb_display_params_privileged_action_confirm_string_tag:
      content_pages = 1;
      break;
    case fwpb_display_params_privileged_action_confirm_action_tag:
      content_pages = 1;
      break;
    default:
      content_pages = 1;
      break;
  }

  return content_pages + 1;  // +1 for scan page
}

static bool should_cycle_header_hint(void) {
  if (!header_title || lv_obj_has_flag(header_title, LV_OBJ_FLAG_HIDDEN)) {
    return false;
  }

  if (!header_cycle_enabled || showing_confirmed_page || check_button_held) {
    return false;
  }

  return (current_page_index < (num_pages - 1));
}

static bool is_single_content_page_confirmation(void) {
  int content_pages = num_pages - 1;  // exclude the scan page
  return content_pages == 1;
}

static lv_opa_t header_prompt_target_opa(header_prompt_mode_t mode) {
  switch (mode) {
    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      return LV_OPA_COVER;
    case HEADER_PROMPT_MODE_HOLD_TO_CONFIRM:
      return LV_OPA_COVER;
    case HEADER_PROMPT_MODE_STEP:
    default:
      return LV_OPA_50;
  }
}

static lv_opa_t header_prompt_icon_target_opa(header_prompt_mode_t mode) {
  switch (mode) {
    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      return LV_OPA_COVER;
    case HEADER_PROMPT_MODE_HOLD_TO_CONFIRM:
      return LV_OPA_COVER;
    case HEADER_PROMPT_MODE_STEP:
    default:
      return LV_OPA_COVER;
  }
}

static lv_obj_t* get_check_button_icon(void) {
  if (!check_button_obj || !lv_obj_is_valid(check_button_obj)) {
    return NULL;
  }

  lv_obj_t* icon = lv_obj_get_child(check_button_obj, 0);
  if (!icon || !lv_obj_is_valid(icon)) {
    return NULL;
  }

  return icon;
}

static void apply_header_prompt_mode(header_prompt_mode_t mode) {
  if (!header_title) {
    return;
  }

  switch (mode) {
    case HEADER_PROMPT_MODE_HOLD_TO_CONFIRM:
      lv_label_set_text(header_title,
                        langpack_get_string(LANGPACK_ID_CONFIRMATION_HOLD_TO_CONFIRM));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
      approval_button_set_icon_highlight(check_button_obj, lv_color_hex(COLOR_RING));
      break;

    case HEADER_PROMPT_MODE_KEEP_HOLDING:
      lv_label_set_text(header_title, langpack_get_string(LANGPACK_ID_CONFIRMATION_KEEP_HOLDING));
      lv_obj_set_style_text_color(header_title, lv_color_hex(COLOR_RING), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_COVER, 0);
      break;

    case HEADER_PROMPT_MODE_STEP:
    default:
      if (is_single_content_page_confirmation()) {
        lv_label_set_text(header_title,
                          langpack_get_string(LANGPACK_ID_CONFIRMATION_HOLD_TO_CONFIRM));
      } else {
        lv_label_set_text(header_title, step_indicator_text);
      }
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
      approval_button_clear_icon_highlight(check_button_obj);
      break;
  }
}

static void header_text_opa_anim_cb(void* var, int32_t value) {
  lv_obj_t* obj = (lv_obj_t*)var;
  if (!obj || !lv_obj_is_valid(obj)) {
    return;
  }
  lv_obj_set_style_text_opa(obj, (lv_opa_t)value, 0);
}

static void header_icon_recolor_opa_anim_cb(void* var, int32_t value) {
  lv_obj_t* obj = (lv_obj_t*)var;
  if (!obj || !lv_obj_is_valid(obj)) {
    return;
  }
  lv_obj_set_style_img_recolor_opa(obj, (lv_opa_t)value, 0);
}

static void header_fade_out_ready_cb(lv_anim_t* anim) {
  (void)anim;

  if (!header_title || !lv_obj_is_valid(header_title)) {
    return;
  }

  header_prompt_mode = pending_header_prompt_mode;
  apply_header_prompt_mode(header_prompt_mode);
  lv_obj_set_style_text_opa(header_title, LV_OPA_TRANSP, 0);

  lv_obj_t* icon = get_check_button_icon();
  if (icon) {
    lv_obj_set_style_img_recolor_opa(icon, LV_OPA_TRANSP, 0);
  }

  lv_anim_t fade_in;
  lv_anim_init(&fade_in);
  lv_anim_set_var(&fade_in, header_title);
  lv_anim_set_exec_cb(&fade_in, header_text_opa_anim_cb);
  lv_anim_set_time(&fade_in, HEADER_FADE_DURATION_MS);
  lv_anim_set_values(&fade_in, LV_OPA_TRANSP, header_prompt_target_opa(header_prompt_mode));
  lv_anim_set_path_cb(&fade_in, lv_anim_path_ease_in_out);
  lv_anim_start(&fade_in);

  if (icon) {
    lv_anim_t icon_fade_in;
    lv_anim_init(&icon_fade_in);
    lv_anim_set_var(&icon_fade_in, icon);
    lv_anim_set_exec_cb(&icon_fade_in, header_icon_recolor_opa_anim_cb);
    lv_anim_set_time(&icon_fade_in, HEADER_FADE_DURATION_MS);
    lv_anim_set_values(&icon_fade_in, LV_OPA_TRANSP,
                       header_prompt_icon_target_opa(header_prompt_mode));
    lv_anim_set_path_cb(&icon_fade_in, lv_anim_path_ease_in_out);
    lv_anim_start(&icon_fade_in);
  }
}

static void set_header_prompt_mode(header_prompt_mode_t mode, bool animate) {
  if (!header_title || !lv_obj_is_valid(header_title)) {
    return;
  }

  pending_header_prompt_mode = mode;
  lv_anim_del(header_title, header_text_opa_anim_cb);
  lv_obj_t* icon = get_check_button_icon();
  if (icon) {
    lv_anim_del(icon, header_icon_recolor_opa_anim_cb);
  }

  if (!animate) {
    header_prompt_mode = mode;
    apply_header_prompt_mode(mode);
    return;
  }

  lv_opa_t current_opa = lv_obj_get_style_text_opa(header_title, 0);
  if (current_opa == LV_OPA_TRANSP) {
    header_fade_out_ready_cb(NULL);
    return;
  }

  lv_anim_t fade_out;
  lv_anim_init(&fade_out);
  lv_anim_set_var(&fade_out, header_title);
  lv_anim_set_exec_cb(&fade_out, header_text_opa_anim_cb);
  lv_anim_set_time(&fade_out, HEADER_FADE_DURATION_MS);
  lv_anim_set_values(&fade_out, current_opa, LV_OPA_TRANSP);
  lv_anim_set_path_cb(&fade_out, lv_anim_path_ease_in_out);
  lv_anim_set_ready_cb(&fade_out, header_fade_out_ready_cb);
  lv_anim_start(&fade_out);

  if (icon) {
    lv_anim_t icon_fade_out;
    lv_anim_init(&icon_fade_out);
    lv_anim_set_var(&icon_fade_out, icon);
    lv_anim_set_exec_cb(&icon_fade_out, header_icon_recolor_opa_anim_cb);
    lv_anim_set_time(&icon_fade_out, HEADER_FADE_DURATION_MS);
    lv_anim_set_values(&icon_fade_out, lv_obj_get_style_img_recolor_opa(icon, 0), LV_OPA_TRANSP);
    lv_anim_set_path_cb(&icon_fade_out, lv_anim_path_ease_in_out);
    lv_anim_start(&icon_fade_out);
  }
}

static void stop_header_hint_cycle(void) {
  if (header_hint_timer) {
    lv_timer_del(header_hint_timer);
    header_hint_timer = NULL;
  }
}

static void restart_header_hint_cycle(void) {
  stop_header_hint_cycle();

  if (!should_cycle_header_hint()) {
    return;
  }

  uint32_t initial_period =
    (header_prompt_mode == HEADER_PROMPT_MODE_STEP) ? STEP_DISPLAY_MS : HOLD_DISPLAY_MS;
  header_hint_timer = lv_timer_create(header_hint_timer_cb, initial_period, NULL);
  if (header_hint_timer) {
    lv_timer_set_repeat_count(header_hint_timer, -1);
  }
}

static void header_hint_timer_cb(lv_timer_t* timer) {
  if (!should_cycle_header_hint()) {
    stop_header_hint_cycle();
    return;
  }

  header_prompt_mode_t next_mode = (header_prompt_mode == HEADER_PROMPT_MODE_STEP)
                                     ? HEADER_PROMPT_MODE_HOLD_TO_CONFIRM
                                     : HEADER_PROMPT_MODE_STEP;

  if (is_single_content_page_confirmation()) {
    lv_opa_t current_opa = lv_obj_get_style_text_opa(header_title, 0);
    lv_opa_t target_opa = header_prompt_target_opa(next_mode);
    lv_obj_t* icon = get_check_button_icon();
    lv_opa_t current_icon_opa = icon ? lv_obj_get_style_img_recolor_opa(icon, 0) : LV_OPA_TRANSP;
    lv_opa_t target_icon_opa = header_prompt_icon_target_opa(next_mode);

    pending_header_prompt_mode = next_mode;
    header_prompt_mode = next_mode;
    lv_anim_del(header_title, header_text_opa_anim_cb);
    if (icon) {
      lv_anim_del(icon, header_icon_recolor_opa_anim_cb);
    }
    apply_header_prompt_mode(next_mode);
    lv_obj_set_style_text_opa(header_title, current_opa, 0);
    if (icon) {
      lv_obj_set_style_img_recolor_opa(icon, current_icon_opa, 0);
    }

    lv_anim_t crossfade;
    lv_anim_init(&crossfade);
    lv_anim_set_var(&crossfade, header_title);
    lv_anim_set_exec_cb(&crossfade, header_text_opa_anim_cb);
    lv_anim_set_time(&crossfade, HEADER_FADE_DURATION_MS);
    lv_anim_set_values(&crossfade, current_opa, target_opa);
    lv_anim_set_path_cb(&crossfade, lv_anim_path_ease_in_out);
    lv_anim_start(&crossfade);

    if (icon) {
      lv_anim_t icon_crossfade;
      lv_anim_init(&icon_crossfade);
      lv_anim_set_var(&icon_crossfade, icon);
      lv_anim_set_exec_cb(&icon_crossfade, header_icon_recolor_opa_anim_cb);
      lv_anim_set_time(&icon_crossfade, HEADER_FADE_DURATION_MS);
      lv_anim_set_values(&icon_crossfade, current_icon_opa, target_icon_opa);
      lv_anim_set_path_cb(&icon_crossfade, lv_anim_path_ease_in_out);
      lv_anim_start(&icon_crossfade);
    }
  } else {
    set_header_prompt_mode(next_mode, true);
  }

  lv_timer_set_period(
    timer, next_mode == HEADER_PROMPT_MODE_HOLD_TO_CONFIRM ? HOLD_DISPLAY_MS : STEP_DISPLAY_MS);
}

static lv_coord_t get_top_group_bottom_screen(void) {
  lv_coord_t top_group_bottom =
    HEADER_PADDING_TOP + 44 + lv_font_get_line_height(FONT_HEADER) + TITLE_MARGIN_TOP;

  if (header_title && lv_obj_is_valid(header_title) &&
      !lv_obj_has_flag(header_title, LV_OBJ_FLAG_HIDDEN)) {
    lv_area_t title_coords;
    lv_obj_get_coords(header_title, &title_coords);
    lv_coord_t title_bottom = title_coords.y2 + 1;
    if (title_bottom > top_group_bottom) {
      top_group_bottom = title_bottom;
    }
  }

  if (menu_button.is_initialized && menu_button.container &&
      lv_obj_is_valid(menu_button.container) &&
      !lv_obj_has_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN)) {
    lv_area_t menu_coords;
    lv_obj_get_coords(menu_button.container, &menu_coords);
    lv_coord_t menu_bottom = menu_coords.y2 + 1;
    if (menu_bottom > top_group_bottom) {
      top_group_bottom = menu_bottom;
    }
  }

  return top_group_bottom;
}

static void align_content_between_header_and_check_button(lv_obj_t* parent, lv_obj_t* content,
                                                          lv_coord_t center_nudge_y) {
  if (!parent || !content) {
    return;
  }

  lv_obj_update_layout(screen ? screen : parent);
  lv_obj_update_layout(content);
  lv_area_t parent_coords;
  lv_obj_get_coords(parent, &parent_coords);
  lv_coord_t parent_height = lv_obj_get_height(parent);
  lv_coord_t check_button_top =
    parent_height - APPROVAL_BUTTON_BOTTOM_MARGIN - APPROVAL_BUTTON_SIZE;
  lv_coord_t content_height = lv_obj_get_height(content);
  lv_coord_t top_group_bottom_local = get_top_group_bottom_screen() - parent_coords.y1;
  lv_coord_t center_y =
    ((check_button_top + top_group_bottom_local - content_height) / 2) + center_nudge_y;
  if (center_y < 0) {
    center_y = 0;
  }

  lv_obj_align(content, LV_ALIGN_TOP_MID, 0, center_y);
}

static void align_address_page_between_header_and_check_button(lv_obj_t* parent,
                                                               address_display_t* widget,
                                                               lv_coord_t center_nudge_y) {
  if (!parent || !widget || widget->label_count <= 0) {
    return;
  }

  lv_obj_update_layout(screen ? screen : parent);

  lv_area_t parent_coords;
  lv_obj_get_coords(parent, &parent_coords);
  lv_coord_t parent_height = lv_obj_get_height(parent);
  lv_coord_t check_button_top =
    parent_height - APPROVAL_BUTTON_BOTTOM_MARGIN - APPROVAL_BUTTON_SIZE;
  lv_coord_t top_group_bottom_local = get_top_group_bottom_screen() - parent_coords.y1;

  lv_coord_t content_top = LV_COORD_MAX;
  lv_coord_t content_bottom = LV_COORD_MIN;
  for (int i = 0; i < widget->label_count; i++) {
    lv_obj_t* child = widget->char_labels[i];
    if (!child || !lv_obj_is_valid(child)) {
      continue;
    }

    lv_area_t child_coords;
    lv_obj_get_coords(child, &child_coords);
    lv_coord_t child_top = child_coords.y1 - parent_coords.y1;
    lv_coord_t child_bottom = child_coords.y2 - parent_coords.y1 + 1;
    if (child_top < content_top) {
      content_top = child_top;
    }
    if (child_bottom > content_bottom) {
      content_bottom = child_bottom;
    }
  }

  if (content_top == LV_COORD_MAX || content_bottom == LV_COORD_MIN) {
    return;
  }

  lv_coord_t content_height = content_bottom - content_top;
  lv_coord_t desired_top =
    ((check_button_top + top_group_bottom_local - content_height) / 2) + center_nudge_y;
  if (desired_top < 0) {
    desired_top = 0;
  }
  lv_coord_t delta = desired_top - content_top;

  for (int i = 0; i < widget->label_count; i++) {
    lv_obj_t* child = widget->char_labels[i];
    if (!child || !lv_obj_is_valid(child)) {
      continue;
    }
    lv_obj_set_y(child, lv_obj_get_y(child) + delta);
  }
}

static void check_button_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  lv_obj_t* check_button = lv_event_get_target(e);

  if (code == LV_EVENT_PRESSED) {
    check_button_held = true;
    stop_header_hint_cycle();
    if (header_title) {
      lv_obj_clear_flag(header_title, LV_OBJ_FLAG_HIDDEN);
    }
    set_header_prompt_mode(HEADER_PROMPT_MODE_KEEP_HOLDING, false);

    // Change check button background to green and icon to black
    if (check_button) {
      approval_button_set_hold_state(check_button);
    }

    // Dim menu button while holding so it stays visible but de-emphasized.
    top_menu_fade_to_opacity(&menu_button, MENU_BUTTON_HOLD_OPA, HEADER_FADE_DURATION_MS);
    dot_ring_animate_fill_from_current(&approve_ring, 100, HOLD_TO_CONFIRM_DURATION_MS,
                                       DOT_RING_COLOR_GREEN, DOT_RING_FILL_SPLIT,
                                       on_approve_complete, NULL);

  } else if (code == LV_EVENT_RELEASED || code == LV_EVENT_PRESS_LOST) {
    if (showing_confirmed_page) {
      return;
    }

    if (dot_ring_animate_release_to_inactive(&approve_ring, HOLD_TO_CONFIRM_DURATION_MS)) {
      return;
    }

    check_button_held = false;

    // Rewind the ring and restore the idle hint cycle immediately.
    update_step_indicator(current_page_index, num_pages);

    // Restore check button background to white and icon to original
    if (check_button) {
      approval_button_set_idle_state(check_button);
    }

    top_menu_fade_to_opacity(&menu_button, LV_OPA_COVER, HEADER_FADE_DURATION_MS);
  }
}

static void on_approve_complete(void* user_data) {
  (void)user_data;

  int next_page = current_page_index + 1;
  bool is_on_last_content_page = (next_page == num_pages - 1);

  if (is_on_last_content_page) {
    // Last content page: send approve before moving to scan.
    display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE, 0);
  }

  // Show confirmed interstitial after every successful hold.
  show_confirmed_page(next_page);
}

static void menu_button_custom_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_CLICKED) {
    hold_cancel_options_t options = {
      .followup_title = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_ON_YOUR_PHONE),
      .followup_text = langpack_get_string(LANGPACK_ID_HOLD_CANCEL_CANCEL_IN_APP),
    };

    stop_header_hint_cycle();
    hold_cancel_show_with_options(&cancel_modal, on_cancel_complete, on_cancel_dismiss, NULL,
                                  &options);
  }
}

static void on_cancel_complete(void* user_data) {
  (void)user_data;

  if (current_page_index == num_pages - 1) {
    if (scan_nfc_dots.container) {
      nfc_dots_animation_stop(&scan_nfc_dots);
      lv_obj_add_flag(scan_nfc_dots.container, LV_OBJ_FLAG_HIDDEN);
    }
    if (scan_text_container) {
      lv_obj_add_flag(scan_text_container, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_button.is_initialized && menu_button.container) {
      lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    }
  }

  display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL, 0);
}

static void on_cancel_dismiss(void* user_data) {
  (void)user_data;
  hold_cancel_hide(&cancel_modal);
  update_step_indicator(current_page_index, num_pages);
}

static void show_confirmed_page(int next_page_index) {
  showing_confirmed_page = true;
  check_button_held = false;
  stop_header_hint_cycle();
  confirmed_next_page_index = next_page_index;

  if (header) {
    lv_obj_set_style_bg_opa(header, LV_OPA_TRANSP, 0);
  }
  if (header_title) {
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  }
  if (scroll_container) {
    lv_obj_add_flag(scroll_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_add_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
  }

  confirmed_checkmark = lv_img_create(screen);
  if (confirmed_checkmark) {
    lv_img_set_src(confirmed_checkmark, &check);
    lv_obj_set_style_img_recolor(confirmed_checkmark, lv_color_white(), 0);
    lv_obj_set_style_img_recolor_opa(confirmed_checkmark, LV_OPA_COVER, 0);
    lv_obj_align(confirmed_checkmark, LV_ALIGN_CENTER, 0, -20);
  }

  confirmed_label = lv_label_create(screen);
  if (confirmed_label) {
    lv_label_set_text(confirmed_label,
                      langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_CONFIRMED));
    lv_obj_set_style_text_color(confirmed_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(confirmed_label, FONT_TITLE, 0);
    lv_obj_align(confirmed_label, LV_ALIGN_CENTER, 0, CONFIRMED_LABEL_Y);
  }

  confirmed_timer = lv_timer_create(confirmed_timer_cb, CONFIRMED_DELAY_MS, NULL);
  if (confirmed_timer) {
    lv_timer_set_repeat_count(confirmed_timer, 1);
  }
}

static void hide_confirmed_page(void) {
  showing_confirmed_page = false;
  confirmed_next_page_index = -1;
  dot_ring_hide(&approve_ring);
  dot_ring_reset(&approve_ring);

  if (confirmed_checkmark) {
    lv_obj_del(confirmed_checkmark);
    confirmed_checkmark = NULL;
  }
  if (confirmed_label) {
    lv_obj_del(confirmed_label);
    confirmed_label = NULL;
  }
  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }
}

static void confirmed_timer_cb(lv_timer_t* timer) {
  (void)timer;
  confirmed_timer = NULL;
  int next_page = confirmed_next_page_index;
  bool moving_to_scan_page = (next_page == num_pages - 1);

  if (moving_to_scan_page) {
    // The controller will replace this screen with the phone-handoff prompt.
    // Keep the confirmed interstitial visible until then so we do not briefly
    // reveal the local scan page between the two transitions.
    return;
  }

  hide_confirmed_page();

  // Restore scroll container visibility (hidden by show_confirmed_page)
  if (scroll_container) {
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_HIDDEN);
  }

  dot_ring_show(&approve_ring);

  // Restore menu button (hidden by show_confirmed_page)
  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_clear_flag(menu_button.container, LV_OBJ_FLAG_HIDDEN);
    top_menu_set_opacity(&menu_button, LV_OPA_COVER);
    lv_obj_move_foreground(menu_button.container);
  }

  if (!moving_to_scan_page && header_title) {
    lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
    lv_obj_set_style_text_opa(header_title, LV_OPA_50, 0);
  }

  if (next_page >= 0 && next_page < num_pages) {
    scroll_to_page(next_page, false);
  }
}

static void scroll_to_page(int page_index, bool animate) {
  if (!scroll_container || page_index < 0 || page_index >= num_pages) {
    return;
  }

  if (!page_containers[page_index]) {
    return;
  }

  // Create page content if needed
  create_page_content(page_index);
  current_page_index = page_index;
  lv_coord_t scroll_x = page_index * LV_HOR_RES;
  lv_obj_scroll_to_x(scroll_container, scroll_x, animate ? LV_ANIM_ON : LV_ANIM_OFF);
  update_step_indicator(current_page_index, num_pages);
}

static void update_step_indicator(int current, int total) {
  if (!header_title) {
    return;
  }

  // total includes the scan page; content pages = total - 1
  int content_pages = total - 1;
  if (content_pages <= 0 || current >= content_pages) {
    // Scan page: hide the prompt header entirely.
    header_cycle_enabled = false;
    stop_header_hint_cycle();
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
    return;
  }

  lv_obj_clear_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  header_cycle_enabled = true;

  if (content_pages == 1) {
    step_indicator_text[0] = '\0';
  } else {
    snprintf(step_indicator_text, sizeof(step_indicator_text), "%d OF %d", current + 1,
             content_pages);
  }
  set_header_prompt_mode(
    content_pages == 1 ? HEADER_PROMPT_MODE_HOLD_TO_CONFIRM : HEADER_PROMPT_MODE_STEP, false);

  if (should_cycle_header_hint()) {
    restart_header_hint_cycle();
  } else {
    stop_header_hint_cycle();
  }
}

static void create_scan_page(lv_obj_t* parent) {
  (void)parent;

  stop_header_hint_cycle();

  if (header_title) {
    lv_obj_add_flag(header_title, LV_OBJ_FLAG_HIDDEN);
  }

  memset(&scan_nfc_dots, 0, sizeof(scan_nfc_dots));
  scan_nfc_dots.highlight_color = lv_color_hex(COLOR_RING);
  nfc_dots_animation_create(screen, &scan_nfc_dots);

  scan_text_container = lv_obj_create(screen);
  if (!scan_text_container) {
    return;
  }
  lv_obj_set_style_bg_color(scan_text_container, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(scan_text_container, LV_OPA_COVER, 0);
  lv_obj_set_style_border_width(scan_text_container, 0, 0);
  lv_obj_set_style_pad_all(scan_text_container, SCAN_TEXT_CONTAINER_PADDING, 0);
  lv_obj_clear_flag(scan_text_container, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(scan_text_container, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* scan_label = lv_label_create(scan_text_container);
  if (!scan_label) {
    return;
  }
  lv_label_set_text(scan_label, langpack_get_string(LANGPACK_ID_SCAN_CONFIRM));
  lv_obj_set_style_text_align(scan_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_set_style_text_color(scan_label, lv_color_hex(COLOR_RING), 0);
  lv_obj_set_style_text_font(scan_label, FONT_SCAN, 0);
  lv_obj_center(scan_label);

  lv_obj_set_size(scan_text_container, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_align(scan_text_container, LV_ALIGN_CENTER, 0, 0);

  ui_set_local_brightness(SCAN_SCREEN_BRIGHTNESS);

  nfc_dots_animation_start(&scan_nfc_dots);

  if (menu_button.is_initialized && menu_button.container) {
    lv_obj_move_foreground(menu_button.container);
  }
}

static void create_action_page(lv_obj_t* parent,
                               const fwpb_display_params_privileged_action* params) {
  if (params->which_action != fwpb_display_params_privileged_action_confirm_action_tag) {
    return;
  }

  lv_obj_t* content_container = lv_obj_create(parent);
  if (!content_container) {
    return;
  }
  lv_obj_set_size(content_container, LV_PCT(100), LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(content_container, 0, 0);
  lv_obj_set_layout(content_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(content_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(content_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(content_container, CONTENT_LABEL_VALUE_SPACING, 0);
  lv_obj_clear_flag(content_container, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* title = lv_label_create(content_container);
  if (title) {
    lv_obj_set_style_text_color(title, lv_color_hex(COLOR_TITLE), 0);
    lv_obj_set_style_text_font(title, FONT_TITLE, 0);
    lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_text(title, get_privileged_action_title(params));
  }

  const char* warning = NULL;
  switch (params->action.confirm_action.action_type) {
    case fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_WIPE_DEVICE:
      warning = langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_WIPE_WARNING);
      break;
    default:
      warning = langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_CONFIRM);
      break;
  }

  if (warning) {
    lv_obj_t* warning_label = lv_label_create(content_container);
    if (warning_label) {
      lv_obj_set_style_text_color(warning_label, lv_color_white(), 0);
      lv_obj_set_style_text_font(warning_label, FONT_TEXT, 0);
      lv_label_set_text(warning_label, warning);
      lv_obj_set_width(warning_label, 400);
      lv_label_set_long_mode(warning_label, LV_LABEL_LONG_WRAP);
      lv_obj_set_style_text_align(warning_label, LV_TEXT_ALIGN_CENTER, 0);
    }
  }

  align_content_between_header_and_check_button(parent, content_container, CONTENT_ACTION_NUDGE_Y);
  create_check_button(parent);
}

static void create_string_page(lv_obj_t* parent,
                               const fwpb_display_params_privileged_action* params) {
  if (params->which_action != fwpb_display_params_privileged_action_confirm_string_tag) {
    return;
  }

  lv_obj_t* content_container = lv_obj_create(parent);
  if (!content_container) {
    return;
  }
  lv_obj_set_size(content_container, LV_PCT(100), LV_SIZE_CONTENT);
  lv_obj_set_style_bg_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(content_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(content_container, 0, 0);
  lv_obj_set_layout(content_container, LV_LAYOUT_FLEX);
  lv_obj_set_flex_flow(content_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(content_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(content_container, CONTENT_LABEL_VALUE_SPACING, 0);
  lv_obj_clear_flag(content_container, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t* title = lv_label_create(content_container);
  if (title) {
    lv_obj_set_style_text_color(title, lv_color_hex(COLOR_TITLE), 0);
    lv_obj_set_style_text_font(title, FONT_TITLE, 0);
    lv_obj_set_style_text_align(title, LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_text(title, get_privileged_action_title(params));
  }

  lv_obj_t* value_label = lv_label_create(content_container);
  if (value_label) {
    lv_obj_set_style_text_color(value_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(value_label, FONT_TEXT, 0);
    lv_label_set_text(value_label, params->action.confirm_string.value);
    lv_obj_set_width(value_label, 400);
    lv_label_set_long_mode(value_label, LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_align(value_label, LV_TEXT_ALIGN_CENTER, 0);
  }

  align_content_between_header_and_check_button(parent, content_container, CONTENT_STRING_NUDGE_Y);
  create_check_button(parent);
}

static void create_page_content(int page_index) {
  if (page_index < 0 || page_index >= num_pages) {
    return;
  }

  lv_obj_t* parent = page_containers[page_index];
  if (!parent) {
    return;
  }

  if (page_index == num_pages - 1) {
    create_scan_page(parent);
    return;
  }

  switch (cached_params.which_action) {
    case fwpb_display_params_privileged_action_confirm_address_tag:
      address_display_create_page(parent, &address_widget, page_index);
      align_address_page_between_header_and_check_button(parent, &address_widget, 0);
      create_check_button(parent);
      break;

    case fwpb_display_params_privileged_action_confirm_string_tag:
      create_string_page(parent, &cached_params);
      break;

    case fwpb_display_params_privileged_action_confirm_action_tag:
      create_action_page(parent, &cached_params);
      break;

    default: {
      lv_obj_t* error_label = lv_label_create(parent);
      if (error_label) {
        lv_obj_set_style_text_color(error_label, lv_color_hex(COLOR_CANCEL), 0);
        lv_obj_set_style_text_font(error_label, FONT_TITLE, 0);
        lv_obj_center(error_label);
        lv_label_set_text(error_label,
                          langpack_get_string(LANGPACK_ID_PRIVILEGED_ACTION_UNKNOWN_ERROR));
      }
      break;
    }
  }
}

lv_obj_t* screen_privileged_action_init(void* ctx) {
  ASSERT(screen == NULL);

  if (!ctx) {
    return NULL;
  }

  fwpb_display_show_screen* params = (fwpb_display_show_screen*)ctx;

  // Cache params
  memcpy(&cached_params, &params->params.privileged_action,
         sizeof(fwpb_display_params_privileged_action));

  // Initialize address widget if needed
  memset(&address_widget, 0, sizeof(address_widget));
  switch (cached_params.which_action) {
    case fwpb_display_params_privileged_action_confirm_address_tag:
      address_display_init(&address_widget, cached_params.action.confirm_address.address);
      address_display_set_bottom_reserved(&address_widget,
                                          APPROVAL_BUTTON_SIZE + APPROVAL_BUTTON_BOTTOM_MARGIN);
      break;
    default:
      break;
  }

  // Calculate total pages
  num_pages = calculate_total_pages(&cached_params);
  current_page_index = 0;

  // Create screen
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_size(screen, LV_HOR_RES, LV_VER_RES);
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  // Create header
  header = lv_obj_create(screen);
  if (header) {
    lv_obj_set_size(header, LV_HOR_RES, HEADER_HEIGHT);
    lv_obj_align(header, LV_ALIGN_TOP_MID, 0, 0);
    lv_obj_set_style_bg_opa(header, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(header, LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(header, 0, 0);
    lv_obj_set_style_pad_top(header, HEADER_PADDING_TOP, 0);
    lv_obj_set_style_pad_bottom(header, HEADER_PADDING_BOTTOM, 0);
    lv_obj_clear_flag(header, LV_OBJ_FLAG_SCROLLABLE);

    // Header title (page indicator)
    header_title = lv_label_create(header);
    if (header_title) {
      lv_obj_set_style_text_color(header_title, lv_color_white(), 0);
      lv_obj_set_style_text_font(header_title, FONT_HEADER, 0);
      lv_obj_align(header_title, LV_ALIGN_TOP_MID, 0, HEADER_PADDING_TOP + 44 + TITLE_MARGIN_TOP);
    }
  }

  // Create scroll container
  scroll_container = lv_obj_create(screen);
  if (scroll_container) {
    lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES - HEADER_HEIGHT);
    lv_obj_set_pos(scroll_container, 0, HEADER_HEIGHT);
    lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(scroll_container, 0, 0);
    lv_obj_set_scroll_dir(scroll_container, LV_DIR_NONE);
    lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_layout(scroll_container, LV_LAYOUT_FLEX);
    lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                          LV_FLEX_ALIGN_CENTER);
    lv_obj_set_scroll_snap_x(scroll_container, LV_SCROLL_SNAP_CENTER);
    lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_SCROLL_ELASTIC);

    // Create page containers
    for (int i = 0; i < num_pages; i++) {
      page_containers[i] = lv_obj_create(scroll_container);
      if (page_containers[i]) {
        lv_obj_set_size(page_containers[i], LV_HOR_RES, LV_VER_RES - HEADER_HEIGHT);
        lv_obj_set_style_bg_opa(page_containers[i], LV_OPA_TRANSP, 0);
        lv_obj_set_style_border_opa(page_containers[i], LV_OPA_TRANSP, 0);
        lv_obj_set_style_pad_all(page_containers[i], 0, 0);
        lv_obj_clear_flag(page_containers[i], LV_OBJ_FLAG_SCROLLABLE);
      }
    }
  }

  // Create widgets
  memset(&approve_ring, 0, sizeof(approve_ring));
  dot_ring_create(screen, &approve_ring);
  dot_ring_show(&approve_ring);

  memset(&cancel_modal, 0, sizeof(cancel_modal));
  hold_cancel_create(screen, &cancel_modal);

  memset(&scan_nfc_dots, 0, sizeof(scan_nfc_dots));

  // Create menu button as child of screen so it keeps the same top spacing
  // as money movement and firmware update.
  memset(&menu_button, 0, sizeof(menu_button));
  top_menu_create(screen, &menu_button, menu_button_custom_handler);

  // Create first page content (lazy load others)
  create_page_content(0);
  update_step_indicator(0, num_pages);

  return screen;
}

void screen_privileged_action_destroy(void) {
  if (!screen) {
    return;
  }

  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }
  lv_obj_t* icon = get_check_button_icon();
  if (icon) {
    lv_anim_del(icon, header_icon_recolor_opa_anim_cb);
  }

  // Clean up confirmed page timer first (before deleting screen)
  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }

  // Stop animations/timers before deleting screen.
  nfc_dots_animation_stop(&scan_nfc_dots);

  if (approve_ring.is_initialized) {
    dot_ring_destroy(&approve_ring);
  }
  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }

  // Destroy top_menu widget (has no timers/animations, just clears state)
  if (menu_button.is_initialized) {
    top_menu_destroy(&menu_button);
  }

  // Delete screen - remaining LVGL children are cleaned up with the screen.
  lv_obj_del(screen);
  screen = NULL;
  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  scan_text_container = NULL;
  confirmed_checkmark = NULL;
  confirmed_label = NULL;
  showing_confirmed_page = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  header_cycle_enabled = false;
  check_button_held = false;
  memset(step_indicator_text, 0, sizeof(step_indicator_text));
  confirmed_next_page_index = -1;

  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;

  // Clear all widget state (objects already deleted with screen)
  memset(&menu_button, 0, sizeof(top_menu_t));
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&address_widget, 0, sizeof(address_widget));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_nfc_dots, 0, sizeof(scan_nfc_dots));
  check_button_obj = NULL;
}

// Handles screen re-entry safely by stopping all animations before recreation.
// Prevents callback conflicts when showing the same screen with different action data.
void screen_privileged_action_update(void* ctx) {
  // First-time init
  if (!screen) {
    screen_privileged_action_init(ctx);
    return;
  }

  // Validate parameters
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (!show_screen || show_screen->which_params != fwpb_display_show_screen_privileged_action_tag) {
    return;
  }

  // Cancel confirmed timer before recreating - its callback would operate on new screen state
  if (confirmed_timer) {
    lv_timer_del(confirmed_timer);
    confirmed_timer = NULL;
  }
  stop_header_hint_cycle();
  if (header_title) {
    lv_anim_del(header_title, header_text_opa_anim_cb);
  }
  lv_obj_t* icon = get_check_button_icon();
  if (icon) {
    lv_anim_del(icon, header_icon_recolor_opa_anim_cb);
  }

  // New action may have different structure - stop animations before recreating
  nfc_dots_animation_stop(&scan_nfc_dots);
  if (approve_ring.is_initialized) {
    dot_ring_destroy(&approve_ring);
  }
  if (cancel_modal.is_initialized) {
    hold_cancel_destroy(&cancel_modal);
  }

  // Preserve old screen for async deletion
  lv_obj_t* old_screen = screen;

  // Clear all screen state
  screen = NULL;
  header = NULL;
  header_title = NULL;
  scroll_container = NULL;
  scan_text_container = NULL;
  confirmed_checkmark = NULL;
  confirmed_label = NULL;
  showing_confirmed_page = false;
  header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  pending_header_prompt_mode = HEADER_PROMPT_MODE_STEP;
  header_cycle_enabled = false;
  check_button_held = false;
  memset(step_indicator_text, 0, sizeof(step_indicator_text));
  confirmed_next_page_index = -1;
  for (int i = 0; i < MAX_PAGES; i++) {
    page_containers[i] = NULL;
  }
  num_pages = 0;
  current_page_index = 0;

  // Clear widget state
  memset(&cached_params, 0, sizeof(cached_params));
  memset(&approve_ring, 0, sizeof(approve_ring));
  memset(&cancel_modal, 0, sizeof(cancel_modal));
  memset(&scan_nfc_dots, 0, sizeof(scan_nfc_dots));
  memset(&address_widget, 0, sizeof(address_widget));
  check_button_obj = NULL;
  if (menu_button.is_initialized) {
    memset(&menu_button, 0, sizeof(menu_button));
  }

  // Create and display new screen
  lv_obj_t* new_screen = screen_privileged_action_init(ctx);
  if (new_screen) {
    lv_scr_load(new_screen);

    // Delete old screen asynchronously to avoid callback conflicts
    if (old_screen) {
      lv_obj_del_async(old_screen);
    }
  }
}
