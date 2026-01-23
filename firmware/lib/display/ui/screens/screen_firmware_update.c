#include "screen_firmware_update.h"

#include "assert.h"
#include "display.pb.h"
#include "langpack.h"
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

// Colors
#define COLOR_WHITE lv_color_white()
#define COLOR_BLACK lv_color_black()
#define COLOR_GRAY  lv_color_make(128, 128, 128)
#define COLOR_TITLE 0xADADAD

// Fonts
#define FONT_TITLE   (&cash_sans_mono_regular_24)
#define FONT_HASH    (&cash_sans_mono_regular_34)
#define FONT_VERSION (&cash_sans_mono_regular_24)

#define PAGE_INDICATOR_PAGES 1

static lv_obj_t* screen = NULL;
static lv_obj_t* title_label = NULL;
static page_indicator_t page_indicator;

// Page 1 elements
static lv_obj_t* hash_container = NULL;
static lv_obj_t* hash_labels[4] = {NULL};  // SHA-256 displayed in 4 lines
static lv_obj_t* version_label = NULL;

static void create_page1_content(void) {
  hash_container = lv_obj_create(screen);
  if (!hash_container) {
    return;
  }
  lv_obj_remove_style_all(hash_container);
  lv_obj_set_size(hash_container, lv_pct(90), LV_SIZE_CONTENT);
  lv_obj_align(hash_container, LV_ALIGN_CENTER, 0, HASH_Y_OFFSET);
  lv_obj_set_flex_flow(hash_container, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(hash_container, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER,
                        LV_FLEX_ALIGN_CENTER);

  for (int i = 0; i < 4; i++) {
    hash_labels[i] = lv_label_create(hash_container);
    if (!hash_labels[i]) {
      return;
    }
    lv_obj_set_style_text_color(hash_labels[i], COLOR_WHITE, 0);
    lv_obj_set_style_text_font(hash_labels[i], FONT_HASH, 0);
    lv_obj_set_style_text_align(hash_labels[i], LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_text(hash_labels[i], "");
    if (i > 0) {
      lv_obj_set_style_margin_top(hash_labels[i], HASH_LINE_SPACING, 0);
    }
  }

  version_label = lv_label_create(screen);
  if (!version_label) {
    return;
  }
  lv_obj_set_style_text_color(version_label, COLOR_GRAY, 0);
  lv_obj_set_style_text_font(version_label, FONT_VERSION, 0);
  lv_obj_align(version_label, LV_ALIGN_CENTER, 0, VERSION_Y_OFFSET);
  lv_label_set_text(version_label, "");
}

static void hide_all_content(void) {
  if (hash_container) {
    lv_obj_add_flag(hash_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (version_label) {
    lv_obj_add_flag(version_label, LV_OBJ_FLAG_HIDDEN);
  }
}

static void show_page1_content(const char* hash, const char* version) {
  if (hash_container) {
    lv_obj_clear_flag(hash_container, LV_OBJ_FLAG_HIDDEN);
  }
  if (version_label) {
    lv_obj_clear_flag(version_label, LV_OBJ_FLAG_HIDDEN);
  }

  lv_label_set_text(title_label, langpack_get_string(LANGPACK_ID_FIRMWARE_UPDATE_HASH_TITLE));

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

lv_obj_t* screen_firmware_update_init(void* ctx) {
  ASSERT(screen == NULL);

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, COLOR_BLACK, 0);

  title_label = lv_label_create(screen);
  if (!title_label) {
    return NULL;
  }
  lv_obj_set_style_text_color(title_label, lv_color_hex(COLOR_TITLE), 0);
  lv_obj_set_style_text_font(title_label, FONT_TITLE, 0);
  lv_obj_align(title_label, LV_ALIGN_TOP_MID, 0, TITLE_Y_OFFSET);

  // Create page content (initially hidden)
  create_page1_content();

  // Get initial parameters if available
  const fwpb_display_show_screen* show_screen = (const fwpb_display_show_screen*)ctx;
  if (show_screen && show_screen->which_params == fwpb_display_show_screen_firmware_update_tag) {
    hide_all_content();

    // Touch UI manages pages internally - always start at hash verification page
    page_indicator_create(screen, &page_indicator, PAGE_INDICATOR_PAGES);
    show_page1_content(show_screen->params.firmware_update.hash,
                       show_screen->params.firmware_update.version);
    page_indicator_update(&page_indicator, 0);
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

  // Touch UI manages page display internally
  // Show initial hash verification content
  if (!page_indicator.is_initialized) {
    page_indicator_create(screen, &page_indicator, PAGE_INDICATOR_PAGES);
  }
  show_page1_content(fw_params->hash, fw_params->version);
  page_indicator_update(&page_indicator, 0);
}
