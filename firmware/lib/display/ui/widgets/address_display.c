#include "address_display.h"

#include <string.h>

// Address display configuration
#define CHARS_PER_GROUP      4
#define GROUPS_PER_LINE      4
#define MAX_LINES            5
#define MAX_CHARS_PER_SCREEN (MAX_LINES * GROUPS_PER_LINE * CHARS_PER_GROUP)

// Layout configuration
#define CHAR_WIDTH   18
#define BULLET_WIDTH 20
#define LINE_HEIGHT  36
#define TOP_PADDING  12

// Colors
#define TEXT_ELLIPSIS "..."

// Fonts
#define FONT_ADDRESS (&cash_sans_mono_regular_28)

// External image declarations
extern const lv_img_dsc_t dot;
extern const lv_font_t cash_sans_mono_regular_28;

// Calculate how many pages needed to display an address
static int calculate_total_address_pages(int addr_len) {
  // True display capacity: 5 lines x 4 groups x 4 chars = 80 chars
  const int chars_per_single_page = MAX_LINES * GROUPS_PER_LINE * CHARS_PER_GROUP;

  if (addr_len <= chars_per_single_page) {
    return 1;
  }

  // First page: 80 - 4 (for ending "...") = 76 effective chars
  // Middle pages: 80 - 4 (start "...") - 4 (end "...") = 72 effective chars each
  // Last page: 80 - 4 (for starting "...") = 76 effective chars
  // Formula: 1 page for first 76 chars, then ceil((remaining) / 72) additional pages
  int remaining_chars = addr_len - (chars_per_single_page - CHARS_PER_GROUP);
  int total_address_pages =
    1 + ((remaining_chars + (chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP) - 1) /
         (chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP));
  return (total_address_pages < 1) ? 1 : total_address_pages;
}

static void clear_rendered_labels(address_display_t* widget) {
  if (!widget) {
    return;
  }

  for (int i = 0; i < widget->label_count; i++) {
    if (widget->char_labels[i]) {
      lv_obj_del(widget->char_labels[i]);
      widget->char_labels[i] = NULL;
    }
  }
  widget->label_count = 0;
}

static int calculate_full_line_count(int addr_len) {
  if (addr_len <= 0) {
    return 0;
  }

  int groups = (addr_len + CHARS_PER_GROUP - 1) / CHARS_PER_GROUP;
  return (groups + GROUPS_PER_LINE - 1) / GROUPS_PER_LINE;
}

static int separator_bullet_y(int line_top_y) {
  return line_top_y + ((lv_font_get_line_height(FONT_ADDRESS) - dot.header.h) / 2);
}

static int address_content_start_x(void) {
  int total_width =
    (GROUPS_PER_LINE * CHARS_PER_GROUP * CHAR_WIDTH) + ((GROUPS_PER_LINE - 1) * BULLET_WIDTH);
  return -total_width / 2;
}

static bool render_address_label(address_display_t* widget, lv_obj_t* parent, const char* text,
                                 int x_pos, int y_pos, int width) {
  if (widget->label_count >= ADDRESS_DISPLAY_MAX_LABELS) {
    return false;
  }

  lv_obj_t* label = lv_label_create(parent);
  if (!label) {
    return false;
  }

  lv_label_set_text(label, text);
  lv_obj_set_style_text_color(label, lv_color_white(), 0);
  lv_obj_set_style_text_font(label, FONT_ADDRESS, 0);
  lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
  if (width > 0) {
    lv_obj_set_width(label, width);
  }
  lv_obj_align(label, LV_ALIGN_TOP_MID, x_pos, y_pos);
  widget->char_labels[widget->label_count++] = label;
  return true;
}

static void render_group_separator(address_display_t* widget, lv_obj_t* parent, int x_pos,
                                   int line_top_y) {
  if (widget->label_count >= ADDRESS_DISPLAY_MAX_LABELS) {
    return;
  }

  lv_obj_t* bullet = lv_img_create(parent);
  if (!bullet) {
    return;
  }

  lv_img_set_src(bullet, &dot);
  lv_obj_set_style_img_recolor(bullet, lv_color_white(), 0);
  lv_obj_set_style_img_recolor_opa(bullet, LV_OPA_COVER, 0);
  lv_obj_align(bullet, LV_ALIGN_TOP_MID,
               x_pos + (CHARS_PER_GROUP * CHAR_WIDTH) + (BULLET_WIDTH / 2),
               separator_bullet_y(line_top_y));
  widget->char_labels[widget->label_count++] = bullet;
}

// Helper to render address characters with ellipses.
// address_top_y is a top-aligned offset within the parent.
static void render_address_content(address_display_t* widget, lv_obj_t* parent, int start_offset,
                                   int address_top_y, bool show_start_ellipsis,
                                   bool show_end_ellipsis, int effective_chars) {
  const char* address = widget->address;
  const int addr_len = strlen(address);
  int char_index = start_offset;
  int line = 0;
  int group_in_line = 0;

  int start_x = address_content_start_x();

  // Show starting ellipsis if continuing from previous page
  if (show_start_ellipsis) {
    if (!render_address_label(widget, parent, TEXT_ELLIPSIS,
                              start_x + (CHARS_PER_GROUP * CHAR_WIDTH / 2), address_top_y, 0)) {
      return;
    }
    group_in_line = 1;
  }

  // Render address characters
  while (char_index < addr_len && char_index - start_offset < MAX_CHARS_PER_SCREEN) {
    if (show_end_ellipsis && (char_index >= start_offset + effective_chars)) {
      break;
    }

    int y_pos = address_top_y + (line * LINE_HEIGHT);
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));

    int chars_in_group = 0;
    for (int i = 0; i < CHARS_PER_GROUP && char_index < addr_len; i++) {
      if (show_end_ellipsis && char_index >= start_offset + effective_chars) {
        break;
      }

      char char_str[2] = {address[char_index], '\0'};
      if (!render_address_label(widget, parent, char_str,
                                x_pos + (i * CHAR_WIDTH) + (CHAR_WIDTH / 2), y_pos, CHAR_WIDTH)) {
        return;
      }
      char_index++;
      chars_in_group++;
    }

    // Add bullet between groups
    if (chars_in_group == CHARS_PER_GROUP && char_index < addr_len &&
        group_in_line < GROUPS_PER_LINE - 1 &&
        !(show_end_ellipsis && char_index >= start_offset + effective_chars)) {
      render_group_separator(widget, parent, x_pos, y_pos);
    }

    group_in_line++;
    if (group_in_line >= GROUPS_PER_LINE) {
      group_in_line = 0;
      line++;
      if (line >= MAX_LINES)
        break;
    }
  }

  // Show ending ellipsis if continues on next page
  if (show_end_ellipsis) {
    int y_pos = address_top_y + (line * LINE_HEIGHT);
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));
    if (!render_address_label(widget, parent, TEXT_ELLIPSIS, x_pos + (CHAR_WIDTH * 2), y_pos, 0)) {
      return;
    }
  }
}

void address_display_init(address_display_t* widget, const char* address) {
  if (!widget || !address) {
    return;
  }

  memset(widget, 0, sizeof(address_display_t));
  widget->address = address;
  widget->total_pages = calculate_total_address_pages(strlen(address));
  widget->is_initialized = true;
}

void address_display_set_bottom_reserved(address_display_t* widget, int bottom_reserved_px) {
  if (!widget) {
    return;
  }

  widget->bottom_reserved_px = (bottom_reserved_px > 0) ? bottom_reserved_px : 0;
}

void address_display_create_page(lv_obj_t* parent, address_display_t* widget, int page_num) {
  if (!widget || !widget->is_initialized || !parent || !widget->address) {
    return;
  }

  // Clear any previous labels (in case we're reusing the widget)
  clear_rendered_labels(widget);

  // True display capacity: 5 lines x 4 groups x 4 chars = 80 chars
  const int chars_per_single_page = MAX_LINES * GROUPS_PER_LINE * CHARS_PER_GROUP;
  const bool show_start_ellipsis = (page_num > 0);
  const bool show_end_ellipsis = (page_num < widget->total_pages - 1);

  // Calculate starting offset for this page
  int start_offset;
  if (page_num == 0) {
    start_offset = 0;
  } else {
    // First page has 76 chars, then each subsequent page has 72
    start_offset = (chars_per_single_page - CHARS_PER_GROUP) +
                   ((page_num - 1) * (chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP));
  }

  // Calculate effective chars for this page
  int effective_chars;
  if (show_start_ellipsis && show_end_ellipsis) {
    // Middle page: 80 - 4 - 4 = 72
    effective_chars = chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP;
  } else if (show_end_ellipsis) {
    // First page: 80 - 4 = 76
    effective_chars = chars_per_single_page - CHARS_PER_GROUP;
  } else if (show_start_ellipsis) {
    // Last page: 80 - 4 = 76
    effective_chars = chars_per_single_page - CHARS_PER_GROUP;
  } else {
    // Single page: 80
    effective_chars = chars_per_single_page;
  }

  // Calculate how many lines will be rendered to center vertically
  int chars_to_render = effective_chars;
  int remaining = strlen(widget->address) - start_offset;
  if (remaining < chars_to_render) {
    chars_to_render = remaining;
  }

  // Add ellipsis groups to line count if needed
  int groups_to_render = (chars_to_render + CHARS_PER_GROUP - 1) / CHARS_PER_GROUP;
  if (show_start_ellipsis)
    groups_to_render++;
  if (show_end_ellipsis)
    groups_to_render++;

  int total_lines = (groups_to_render + GROUPS_PER_LINE - 1) / GROUPS_PER_LINE;
  int total_height = total_lines * LINE_HEIGHT;

  // Center vertically between parent top and reserved bottom area, but keep
  // a minimum top inset so dense 5-line pages don't crowd the clip boundary.
  lv_obj_update_layout(parent);
  int bottom_reserved_px = widget->bottom_reserved_px;
  int parent_height = lv_obj_get_height(parent);
  if (parent_height > 0 && bottom_reserved_px > parent_height) {
    bottom_reserved_px = parent_height;
  }
  int address_top_y = 0;
  if (parent_height > 0) {
    int usable_height = parent_height - bottom_reserved_px;
    if (usable_height < 0) {
      usable_height = 0;
    }
    address_top_y = (usable_height - total_height) / 2;
    if (address_top_y < TOP_PADDING) {
      address_top_y = TOP_PADDING;
    }
  }

  render_address_content(widget, parent, start_offset, address_top_y, show_start_ellipsis,
                         show_end_ellipsis, effective_chars);
}

void address_display_create_full(lv_obj_t* parent, address_display_t* widget) {
  if (!widget || !widget->is_initialized || !parent || !widget->address) {
    return;
  }

  clear_rendered_labels(widget);

  const char* address = widget->address;
  const int addr_len = strlen(address);
  int char_index = 0;
  int line = 0;
  int group_in_line = 0;

  int start_x = address_content_start_x();

  while (char_index < addr_len) {
    int y_pos = line * LINE_HEIGHT;
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));

    int chars_in_group = 0;
    for (int i = 0; i < CHARS_PER_GROUP && char_index < addr_len; i++) {
      char char_str[2] = {address[char_index], '\0'};
      if (!render_address_label(widget, parent, char_str,
                                x_pos + (i * CHAR_WIDTH) + (CHAR_WIDTH / 2), y_pos, CHAR_WIDTH)) {
        return;
      }
      char_index++;
      chars_in_group++;
    }

    if (chars_in_group == CHARS_PER_GROUP && char_index < addr_len &&
        group_in_line < GROUPS_PER_LINE - 1) {
      render_group_separator(widget, parent, x_pos, y_pos);
    }

    group_in_line++;
    if (group_in_line >= GROUPS_PER_LINE) {
      group_in_line = 0;
      line++;
    }
  }
}

int address_display_get_full_height(const address_display_t* widget) {
  if (!widget || !widget->is_initialized || !widget->address) {
    return 0;
  }

  return calculate_full_line_count(strlen(widget->address)) * LINE_HEIGHT;
}
