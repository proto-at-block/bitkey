#include "screen_firmware_update.h"

#include "display.pb.h"
#include "lvgl/lvgl.h"
#include "page_indicator.h"
#include "ui.h"

#include <stdio.h>
#include <string.h>

// Screen configuration
#define SCREEN_BRIGHTNESS 100

// Layout configuration - Page 1 (SHA-256)
#define TITLE_Y_OFFSET    50
#define HASH_Y_OFFSET     -20
#define VERSION_Y_OFFSET  120
#define HASH_LINE_SPACING 3
#define HASH_CHUNK_SIZE   16

// Layout configuration - Page 2 (Confirm)
#define BUTTON_WIDTH           200
#define BUTTON_HEIGHT          60
#define BUTTON_SPACING         15
#define PILL_RADIUS            30
#define BUTTON_BORDER_WIDTH    3
#define VERIFY_BUTTON_Y_OFFSET -40
#define CANCEL_BUTTON_Y_OFFSET 30

// Colors
#define COLOR_WHITE  lv_color_white()
#define COLOR_BLACK  lv_color_black()
#define COLOR_GRAY   lv_color_make(128, 128, 128)
#define COLOR_TITLE  0xADADAD
#define COLOR_VERIFY 0x1CB843
#define COLOR_CANCEL 0xF84752

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_24)
#define FONT_HASH    (&cash_sans_mono_regular_34)
#define FONT_VERSION (&cash_sans_mono_regular_24)
#define FONT_BUTTON  (&cash_sans_mono_regular_30)

#define PAGE_INDICATOR_PAGES 2

static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static page_indicator_t page_indicator;

// Page 1 elements
static lv_obj_t* hash_container = NULL;
static lv_obj_t* hash_labels[4] = {NULL};  // SHA-256 displayed in 4 lines
static lv_obj_t* version_label = NULL;

// Page 2 elements
static lv_obj_t* verify_pill = NULL;
static lv_obj_t* verify_label = NULL;
static lv_obj_t* cancel_pill = NULL;
static lv_obj_t* cancel_label = NULL;

// Updating page elements
static lv_obj_t* updating_label = NULL;

static void create_page1_content(void) {
  hash_container = lv_obj_create(screen);
  lv_obj_remove_style_all(hash_container);
  lv_obj_set_size(hash_container, lv_pct(90), LV_SIZE_CONTENT);
  lv_obj_align(hash_container, LV_ALIGN_CENTER, 0, HASH_Y_OFFSET);
  lv_obj_set_flex_flow(hash_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(hash_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);

  // Create hash labels (will be populated by update function)
  for (int i = 0; i < 4; i++) {
    hash_labels[i] = lv_label_create(hash_container);
    lv_obj_set_style_text_color(hash_labels[i], COLOR_WHITE, 0);
    lv_obj_set_style_text_font(hash_labels[i], FONT_HASH, 0);
    lv_obj_set_style_text_align(hash_labels[i], LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_text(hash_labels[i], "");
    if (i > 0) {
      lv_obj_set_style_margin_top(hash_labels[i], HASH_LINE_SPACING, 0);
    }
  }

  version_label = lv_label_create(screen);
  lv_obj_set_style_text_color(version_label, COLOR_GRAY, 0);
  lv_obj_set_style_text_font(version_label, FONT_VERSION, 0);
  lv_obj_align(version_label, LV_ALIGN_CENTER, 0, VERSION_Y_OFFSET);
  lv_label_set_text(version_label, "");
}

static void create_page2_content(void) {
  verify_pill = lv_obj_create(screen);
  lv_obj_remove_style_all(verify_pill);
  lv_obj_set_size(verify_pill, BUTTON_WIDTH, BUTTON_HEIGHT);
  lv_obj_align(verify_pill, LV_ALIGN_CENTER, 0, VERIFY_BUTTON_Y_OFFSET);
  lv_obj_set_style_bg_opa(verify_pill, LV_OPA_TRANSP, 0);
  lv_obj_set_style_radius(verify_pill, PILL_RADIUS, 0);
  lv_obj_set_style_border_color(verify_pill, COLOR_WHITE, 0);
  lv_obj_set_style_border_width(verify_pill, 0, 0);  // Will be set by update

  verify_label = lv_label_create(verify_pill);
  lv_label_set_text(verify_label, LV_SYMBOL_OK " Verify");
  lv_obj_set_style_text_color(verify_label, lv_color_hex(COLOR_VERIFY), 0);
  lv_obj_set_style_text_font(verify_label, FONT_BUTTON, 0);
  lv_obj_center(verify_label);

  cancel_pill = lv_obj_create(screen);
  lv_obj_remove_style_all(cancel_pill);
  lv_obj_set_size(cancel_pill, BUTTON_WIDTH, BUTTON_HEIGHT);
  lv_obj_align(cancel_pill, LV_ALIGN_CENTER, 0, CANCEL_BUTTON_Y_OFFSET);
  lv_obj_set_style_bg_opa(cancel_pill, LV_OPA_TRANSP, 0);
  lv_obj_set_style_radius(cancel_pill, PILL_RADIUS, 0);
  lv_obj_set_style_border_color(cancel_pill, COLOR_WHITE, 0);
  lv_obj_set_style_border_width(cancel_pill, 0, 0);  // Will be set by update

  cancel_label = lv_label_create(cancel_pill);
  lv_label_set_text(cancel_label, LV_SYMBOL_CLOSE " Cancel");
  lv_obj_set_style_text_color(cancel_label, lv_color_hex(COLOR_CANCEL), 0);
  lv_obj_set_style_text_font(cancel_label, FONT_BUTTON, 0);
  lv_obj_center(cancel_label);
}

static void create_page3_content(void) {
  // "Update in progress" label
  updating_label = lv_label_create(screen);
  lv_obj_set_style_text_color(updating_label, COLOR_WHITE, 0);
  lv_obj_set_style_text_font(updating_label, FONT_TITLE, 0);
  lv_obj_align(updating_label, LV_ALIGN_CENTER, 0, 0);
  lv_label_set_text(updating_label, "Update in progress...");
}

static void hide_all_content(void) {
  // Page 1 elements
  if (hash_container) {
    lv_obj_add_flag(hash_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (version_label) {
    lv_obj_add_flag(version_label, LV_OBJ_FLAG_HIDDEN);
  }

  // Page 2 elements
  if (verify_pill) {
    lv_obj_add_flag(verify_pill, LV_OBJ_FLAG_HIDDEN);
  }
  if (cancel_pill) {
    lv_obj_add_flag(cancel_pill, LV_OBJ_FLAG_HIDDEN);
  }

  // Updating page elements
  if (updating_label) {
    lv_obj_add_flag(updating_label, LV_OBJ_FLAG_HIDDEN);
  }
}

static void show_page1_content(const char* hash, const char* version) {
  if (hash_container) {
    lv_obj_clear_flag(hash_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (version_label) {
    lv_obj_clear_flag(version_label, LV_OBJ_FLAG_HIDDEN);
  }

  lv_label_set_text(title_label, "SHA-256");

  // Split hash into 4 lines (16 chars each for 64-char hash)
  if (hash && strlen(hash) > 0) {
    char chunk[17];  // 16 chars + null terminator
    size_t hash_len = strlen(hash);

    for (int i = 0; i < 4; i++) {
      size_t start = i * HASH_CHUNK_SIZE;
      size_t len = (start + HASH_CHUNK_SIZE <= hash_len) ? HASH_CHUNK_SIZE : (hash_len - start);

      if (len > 0) {
        memcpy(chunk, hash + start, len);
        chunk[len] = '\0';
        lv_label_set_text(hash_labels[i], chunk);
      } else {
        lv_label_set_text(hash_labels[i], "");
      }
    }
  }

  if (version) {
    lv_label_set_text(version_label, version);
  }
}

static void show_page2_content(uint8_t button_selection) {
  if (verify_pill) {
    lv_obj_clear_flag(verify_pill, LV_OBJ_FLAG_HIDDEN);
  }
  if (cancel_pill) {
    lv_obj_clear_flag(cancel_pill, LV_OBJ_FLAG_HIDDEN);
  }

  lv_label_set_text(title_label, "FINISH");

  if (button_selection == 0) {
    lv_obj_set_style_border_width(verify_pill, BUTTON_BORDER_WIDTH, 0);
    lv_obj_set_style_border_width(cancel_pill, 0, 0);
  } else {
    lv_obj_set_style_border_width(verify_pill, 0, 0);
    lv_obj_set_style_border_width(cancel_pill, BUTTON_BORDER_WIDTH, 0);
  }
}

static void show_page3_content(void) {
  if (updating_label) {
    lv_obj_clear_flag(updating_label, LV_OBJ_FLAG_HIDDEN);
  }

  lv_label_set_text(title_label, "UPDATING");
}

lv_obj_t* screen_firmware_update_init(void* ctx) {
  screen = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(screen, COLOR_BLACK, 0);

  title_label = lv_label_create(screen);
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Create content for all pages (initially hidden)
  create_page1_content();
  create_page2_content();
  create_page3_content();

  // Get initial parameters if available
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_firmware_update_tag) {
    hide_all_content();

    uint8_t page = show_screen->params.firmware_update.current_page;
    if (page == 0 || page == 1) {
      // Hash verification or confirm page: create page indicator
      page_indicator_create(screen, &page_indicator, PAGE_INDICATOR_PAGES);

      if (page == 0) {
        show_page1_content(show_screen->params.firmware_update.hash,
                           show_screen->params.firmware_update.version);
      } else {
        show_page2_content(show_screen->params.firmware_update.button_selection);
      }
      page_indicator_update(&page_indicator, page);
    } else {
      show_page3_content();
    }
  } else {
    hide_all_content();
    show_page1_content("", "");
    page_indicator_create(screen, &page_indicator, PAGE_INDICATOR_PAGES);
    page_indicator_update(&page_indicator, 0);
  }

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_firmware_update_destroy(void) {
  if (!screen) {
    return;
  }

  page_indicator_destroy(&page_indicator);
  memset(&page_indicator, 0, sizeof(page_indicator));
  lv_obj_del(screen);

  screen = NULL;
  title_label = NULL;
  hash_container = NULL;
  version_label = NULL;
  verify_pill = NULL;
  verify_label = NULL;
  cancel_pill = NULL;
  cancel_label = NULL;
  updating_label = NULL;

  for (int i = 0; i < 4; i++) {
    hash_labels[i] = NULL;
  }
}

void screen_firmware_update_update(void* params) {
  if (!params || !screen) {
    return;
  }

  const fwpb_display_show_screen* show_screen_data = (const fwpb_display_show_screen*)params;
  const fwpb_display_params_firmware_update* fw_params = &show_screen_data->params.firmware_update;

  // Hide all content first
  hide_all_content();

  // Show content and manage page indicator based on current page
  uint8_t page = fw_params->current_page;
  if (page == 0 || page == 1) {
    // Hash verification or confirm page: ensure page indicator exists
    if (!page_indicator.is_initialized) {
      page_indicator_create(screen, &page_indicator, PAGE_INDICATOR_PAGES);
    }

    if (page == 0) {
      show_page1_content(fw_params->hash, fw_params->version);
    } else {
      show_page2_content(fw_params->button_selection);
    }
    page_indicator_update(&page_indicator, page);
  } else {
    if (page_indicator.is_initialized) {
      page_indicator_destroy(&page_indicator);
    }
    show_page3_content();
  }
}
