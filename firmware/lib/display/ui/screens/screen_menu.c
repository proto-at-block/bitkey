#include "screen_menu.h"

#include "assert.h"
#include "display.pb.h"
#include "display_action.h"
#include "langpack.h"
#include "lvgl/lvgl.h"
#include "top_back.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Menu item constants
#define MENU_ITEM_FINGERPRINTS fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS
#define MENU_ITEM_BRIGHTNESS   fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS
#define MENU_ITEM_ABOUT        fwpb_display_menu_item_DISPLAY_MENU_ITEM_ABOUT
#define MENU_ITEM_REGULATORY   fwpb_display_menu_item_DISPLAY_MENU_ITEM_REGULATORY
#define MENU_ITEM_LOCK_DEVICE  fwpb_display_menu_item_DISPLAY_MENU_ITEM_LOCK_DEVICE
#define MENU_ITEM_POWER_OFF    fwpb_display_menu_item_DISPLAY_MENU_ITEM_POWER_OFF
#ifdef MFGTEST
#define MENU_ITEM_TOUCH_TEST   fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOUCH_TEST
#define MENU_ITEM_TOGGLE_SLEEP fwpb_display_menu_item_DISPLAY_MENU_ITEM_TOGGLE_SLEEP
#define MENU_ITEM_RUN_IN       fwpb_display_menu_item_DISPLAY_MENU_ITEM_RUN_IN
#define MENU_ITEM_COUNT        9
#else
#define MENU_ITEM_COUNT 6
#endif

// Screen configuration
#define SCREEN_BRIGHTNESS 100
#define TITLE_Y_OFFSET    80
#define ICON_SIZE         64
#define ITEM_LABEL_Y      120

// Carousel layout configuration
#define ITEM_WIDTH            220
#define OPACITY_SELECTED      LV_OPA_COVER
#define OPACITY_DIMMED        LV_OPA_40
#define SCROLL_ANIM_TIME      150
#define ICON_CIRCLE_SIZE      80
#define ICON_CIRCLE_COLOR     0x404040  // Gray
#define ICON_CIRCLE_COLOR_MFG 0x803030  // Red for MFG items

// Colors
#define COLOR_TITLE 0xADADAD

// Fonts
#define FONT_TITLE (&cash_sans_mono_regular_24)
#define FONT_ITEM  (&cash_sans_mono_regular_28)

// Menu item data
typedef struct {
  langpack_string_id_t label_id;
  const lv_img_dsc_t* icon;
  fwpb_display_menu_item item_id;
} menu_item_data_t;

// External image declarations
extern const lv_img_dsc_t brightness;
extern const lv_img_dsc_t fingerprint;
extern const lv_img_dsc_t info_circle;
extern const lv_img_dsc_t paper_ribbon;
extern const lv_img_dsc_t lock;
extern const lv_img_dsc_t power;
#ifdef MFGTEST
extern const lv_img_dsc_t touch;
extern const lv_img_dsc_t sleep;
extern const lv_img_dsc_t run_in;
#endif

static const menu_item_data_t menu_items[MENU_ITEM_COUNT] = {
  {LANGPACK_ID_MENU_FINGERPRINTS, &fingerprint, MENU_ITEM_FINGERPRINTS},
  {LANGPACK_ID_MENU_BRIGHTNESS, &brightness, MENU_ITEM_BRIGHTNESS},
  {LANGPACK_ID_MENU_ABOUT, &info_circle, MENU_ITEM_ABOUT},
  {LANGPACK_ID_MENU_REGULATORY, &paper_ribbon, MENU_ITEM_REGULATORY},
  {LANGPACK_ID_MENU_LOCK, &lock, MENU_ITEM_LOCK_DEVICE},
  {LANGPACK_ID_MENU_OFF, &power, MENU_ITEM_POWER_OFF},
#ifdef MFGTEST
  {LANGPACK_ID_MENU_TOUCH_TEST, &touch, MENU_ITEM_TOUCH_TEST},
  {LANGPACK_ID_MENU_SLEEP_ENABLED, &sleep, MENU_ITEM_TOGGLE_SLEEP},
  {LANGPACK_ID_MENU_RUN_IN, &run_in, MENU_ITEM_RUN_IN},
#endif
};

static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static lv_obj_t* scroll_container = NULL;
static lv_obj_t* item_containers[MENU_ITEM_COUNT];
static lv_obj_t* item_icon_circles[MENU_ITEM_COUNT];
static lv_obj_t* item_icons[MENU_ITEM_COUNT];
static lv_obj_t* item_labels[MENU_ITEM_COUNT];
static top_back_t back_button;
static int current_item = 0;

#ifdef MFGTEST
static bool sleep_disabled = false;

static langpack_string_id_t get_sleep_toggle_label(void) {
  if (sleep_disabled) {
    return LANGPACK_ID_MENU_SLEEP_DISABLED;
  } else {
    return LANGPACK_ID_MENU_SLEEP_ENABLED;
  }
}

static bool is_mfg_menu_item(fwpb_display_menu_item item_id) {
  return (item_id == MENU_ITEM_TOUCH_TEST || item_id == MENU_ITEM_TOGGLE_SLEEP ||
          item_id == MENU_ITEM_RUN_IN);
}
#endif

// Forward declarations
static void menu_item_click_handler(lv_event_t* e);
static void scroll_event_handler(lv_event_t* e);
static void scroll_to_item(int index, bool animate);

static void update_item_styles_by_position(void) {
  if (!scroll_container) {
    return;
  }

  lv_coord_t scroll_x = lv_obj_get_scroll_x(scroll_container);
  lv_coord_t screen_center = LV_HOR_RES / 2;
  lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;

  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    lv_coord_t item_x = side_padding + (i * ITEM_WIDTH);
    lv_coord_t item_center = item_x + (ITEM_WIDTH / 2) - scroll_x;
    lv_coord_t distance =
      (item_center > screen_center) ? (item_center - screen_center) : (screen_center - item_center);

    // Calculate opacity: full at center, fades to OPACITY_DIMMED away
    lv_opa_t opacity;
    if (distance < ITEM_WIDTH / 2) {
      uint32_t opacity_range = OPACITY_SELECTED - OPACITY_DIMMED;
      uint32_t fade = (distance * opacity_range) / (ITEM_WIDTH / 2);
      opacity = OPACITY_SELECTED - fade;
    } else {
      opacity = OPACITY_DIMMED;
    }

    lv_obj_set_style_opa(item_icon_circles[i], opacity, 0);
    lv_obj_set_style_opa(item_icons[i], opacity, 0);
    lv_obj_set_style_text_opa(item_labels[i], opacity, 0);
  }
}

static void scroll_event_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code == LV_EVENT_SCROLL) {
    update_item_styles_by_position();
  } else if (code == LV_EVENT_SCROLL_END) {
    lv_coord_t scroll_x = lv_obj_get_scroll_x(scroll_container);
    lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;

    int centered_item = 0;
    lv_coord_t min_distance = LV_COORD_MAX;
    lv_coord_t screen_center = LV_HOR_RES / 2;

    for (int i = 0; i < MENU_ITEM_COUNT; i++) {
      lv_coord_t item_x = side_padding + (i * ITEM_WIDTH);
      lv_coord_t item_center = item_x + (ITEM_WIDTH / 2) - scroll_x;
      lv_coord_t distance = (item_center > screen_center) ? (item_center - screen_center)
                                                          : (screen_center - item_center);

      if (distance < min_distance) {
        min_distance = distance;
        centered_item = i;
      }
    }

    current_item = centered_item;
    update_item_styles_by_position();
  }
}

static void menu_item_click_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  uint32_t item_idx = (uint32_t)(uintptr_t)lv_event_get_user_data(e);

  if (item_idx < MENU_ITEM_COUNT) {
    // If not selected, navigate to it first
    if (item_idx != (uint32_t)current_item) {
      scroll_to_item(item_idx, true);
      return;
    }

    // Already selected - trigger action
    fwpb_display_menu_item menu_item = menu_items[item_idx].item_id;

    switch (menu_item) {
      case MENU_ITEM_LOCK_DEVICE:
        display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_LOCK_DEVICE, 0);
        break;
      case MENU_ITEM_POWER_OFF:
        display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_POWER_OFF, 0);
        break;
      default:
        display_send_action(fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT, menu_item);
        break;
    }
  }
}

static void scroll_to_item(int index, bool animate) {
  if (!scroll_container || index < 0 || index >= MENU_ITEM_COUNT) {
    return;
  }

  current_item = index;
  lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;
  lv_coord_t item_x = side_padding + (index * ITEM_WIDTH);
  lv_coord_t scroll_x = item_x + (ITEM_WIDTH / 2) - (LV_HOR_RES / 2);
  lv_obj_scroll_to_x(scroll_container, scroll_x, animate ? LV_ANIM_ON : LV_ANIM_OFF);
  update_item_styles_by_position();
}

lv_obj_t* screen_menu_init(void* ctx) {
  ASSERT(screen == NULL);

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Title
  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_MENU_TITLE));
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_clear_flag(title_label, LV_OBJ_FLAG_CLICKABLE);

  // Scroll container
  scroll_container = lv_obj_create(screen);
  if (!scroll_container) {
    return NULL;
  }
  lv_obj_set_size(scroll_container, LV_PCT(100), LV_VER_RES);
  lv_obj_set_pos(scroll_container, 0, 0);
  lv_obj_set_style_bg_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_opa(scroll_container, LV_OPA_TRANSP, 0);
  lv_obj_set_style_pad_all(scroll_container, 0, 0);

  lv_coord_t side_padding = (LV_HOR_RES - ITEM_WIDTH) / 2;
  lv_obj_set_style_pad_left(scroll_container, side_padding, 0);
  lv_obj_set_style_pad_right(scroll_container, side_padding, 0);
  lv_obj_set_scroll_dir(scroll_container, LV_DIR_HOR);
  lv_obj_set_scrollbar_mode(scroll_container, LV_SCROLLBAR_MODE_OFF);
  lv_obj_set_scroll_snap_x(scroll_container, LV_SCROLL_SNAP_CENTER);
  lv_obj_set_style_anim_time(scroll_container, SCROLL_ANIM_TIME, LV_PART_MAIN);
  lv_obj_set_flex_flow(scroll_container, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(scroll_container, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(scroll_container, 0, 0);
  lv_obj_clear_flag(scroll_container, LV_OBJ_FLAG_CLICKABLE);

  lv_obj_add_event_cb(scroll_container, scroll_event_handler, LV_EVENT_SCROLL, NULL);
  lv_obj_add_event_cb(scroll_container, scroll_event_handler, LV_EVENT_SCROLL_END, NULL);

  // Create menu items
  for (uint8_t i = 0; i < MENU_ITEM_COUNT; i++) {
    // Item container
    item_containers[i] = lv_obj_create(scroll_container);
    if (!item_containers[i]) {
      return NULL;
    }
    lv_obj_set_size(item_containers[i], ITEM_WIDTH, LV_VER_RES);
    lv_obj_set_style_bg_opa(item_containers[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_opa(item_containers[i], LV_OPA_TRANSP, 0);
    lv_obj_set_style_pad_all(item_containers[i], 0, 0);
    lv_obj_clear_flag(item_containers[i], LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_clear_flag(item_containers[i], LV_OBJ_FLAG_CLICKABLE);

    // Icon circle background
    item_icon_circles[i] = lv_obj_create(item_containers[i]);
    if (!item_icon_circles[i]) {
      return NULL;
    }
    lv_obj_set_size(item_icon_circles[i], ICON_CIRCLE_SIZE, ICON_CIRCLE_SIZE);
    lv_obj_set_style_radius(item_icon_circles[i], LV_RADIUS_CIRCLE, 0);
#ifdef MFGTEST
    // Red circle for MFG items
    uint32_t circle_color =
      is_mfg_menu_item(menu_items[i].item_id) ? ICON_CIRCLE_COLOR_MFG : ICON_CIRCLE_COLOR;
    lv_obj_set_style_bg_color(item_icon_circles[i], lv_color_hex(circle_color), 0);
#else
    lv_obj_set_style_bg_color(item_icon_circles[i], lv_color_hex(ICON_CIRCLE_COLOR), 0);
#endif
    lv_obj_set_style_bg_opa(item_icon_circles[i], LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(item_icon_circles[i], 0, 0);
    lv_obj_set_style_pad_all(item_icon_circles[i], 0, 0);
    lv_obj_align(item_icon_circles[i], LV_ALIGN_CENTER, 0, -20);
    lv_obj_add_flag(item_icon_circles[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(item_icon_circles[i], menu_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);

    // Icon
    item_icons[i] = lv_img_create(item_icon_circles[i]);
    if (!item_icons[i]) {
      return NULL;
    }
    lv_img_set_src(item_icons[i], menu_items[i].icon);
    lv_obj_set_width(item_icons[i], ICON_SIZE);
    lv_obj_set_height(item_icons[i], ICON_SIZE);
    lv_obj_align(item_icons[i], LV_ALIGN_CENTER, 0, 0);
    lv_obj_add_flag(item_icons[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(item_icons[i], menu_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);

    // Label
    item_labels[i] = lv_label_create(item_containers[i]);
    if (!item_labels[i]) {
      return NULL;
    }
#ifdef MFGTEST
    if (menu_items[i].item_id == MENU_ITEM_TOGGLE_SLEEP) {
      lv_label_set_text(item_labels[i], langpack_get_string(get_sleep_toggle_label()));
    } else {
      lv_label_set_text(item_labels[i], langpack_get_string(menu_items[i].label_id));
    }
#else
    lv_label_set_text(item_labels[i], langpack_get_string(menu_items[i].label_id));
#endif
    lv_obj_set_width(item_labels[i], ITEM_WIDTH - 10);
    lv_label_set_long_mode(item_labels[i], LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_align(item_labels[i], LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_set_style_text_color(item_labels[i], lv_color_white(), 0);
    lv_obj_set_style_text_font(item_labels[i], FONT_ITEM, 0);
    lv_obj_align(item_labels[i], LV_ALIGN_CENTER, 0, ITEM_LABEL_Y);
    lv_obj_add_flag(item_labels[i], LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(item_labels[i], menu_item_click_handler, LV_EVENT_CLICKED,
                        (void*)(uintptr_t)i);
  }

  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;

  // Scroll to initial item
  int initial_item = 0;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_tag) {
    uint32_t selected = show_screen->params.menu.selected_item;
    for (int i = 0; i < MENU_ITEM_COUNT; i++) {
      if (menu_items[i].item_id == selected) {
        initial_item = i;
        break;
      }
    }
  }
  scroll_to_item(initial_item, false);
  update_item_styles_by_position();

  // Back button
  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(screen, &back_button, NULL);
  lv_obj_move_foreground(back_button.container);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

#ifdef MFGTEST
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_tag) {
    sleep_disabled = show_screen->params.menu.sleep_disabled;
  }
#endif

  return screen;
}

void screen_menu_destroy(void) {
  if (!screen) {
    return;
  }

  top_back_destroy(&back_button);
  lv_obj_del(screen);

  screen = NULL;
  title_label = NULL;
  scroll_container = NULL;
  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    item_containers[i] = NULL;
    item_icon_circles[i] = NULL;
    item_icons[i] = NULL;
    item_labels[i] = NULL;
  }
  current_item = 0;
}

void screen_menu_update(void* ctx) {
  if (!screen) {
    screen_menu_init(ctx);
    return;
  }

#ifdef MFGTEST
  // Update sleep toggle label
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_menu_tag) {
    sleep_disabled = show_screen->params.menu.sleep_disabled;

    for (uint8_t i = 0; i < MENU_ITEM_COUNT; i++) {
      if (menu_items[i].item_id == MENU_ITEM_TOGGLE_SLEEP) {
        lv_label_set_text(item_labels[i], langpack_get_string(get_sleep_toggle_label()));
        break;
      }
    }
  }
#endif
}
