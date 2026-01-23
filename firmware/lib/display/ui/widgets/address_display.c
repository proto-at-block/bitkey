#include "address_display.h"

#include <string.h>

// Address display configuration
#define MAX_CHARS_PER_SCREEN 80
#define CHARS_PER_GROUP      4
#define GROUPS_PER_LINE      3
#define MAX_LINES            5
#define MAX_LABELS           100

// Layout configuration
#define CHAR_WIDTH   24
#define BULLET_WIDTH 20
#define LINE_HEIGHT  36

// Colors
#define TEXT_ELLIPSIS "..."

// Fonts
#define FONT_ADDRESS (&cash_sans_mono_regular_28)

// External image declarations
extern const lv_img_dsc_t dot;
extern const lv_font_t cash_sans_mono_regular_28;

// Calculate how many pages needed to display an address
static int calculate_total_address_pages(int addr_len) {
  // True display capacity: 5 lines × 3 groups × 4 chars = 60 chars
  const int chars_per_single_page = MAX_LINES * GROUPS_PER_LINE * CHARS_PER_GROUP;

  if (addr_len <= chars_per_single_page) {
    return 1;
  }

  // First page: 60 - 4 (for ending "...") = 56 effective chars
  // Middle pages: 60 - 4 (start "...") - 4 (end "...") = 52 effective chars each
  // Last page: 60 - 4 (for starting "...") = 56 effective chars
  // Formula: 1 page for first 56 chars, then ceil((remaining) / 52) additional pages
  int remaining_chars = addr_len - (chars_per_single_page - CHARS_PER_GROUP);
  int total_address_pages =
    1 + ((remaining_chars + (chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP) - 1) /
         (chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP));
  return (total_address_pages < 1) ? 1 : total_address_pages;
}

// Helper to render address characters with ellipses
static void render_address_content(address_display_t* widget, lv_obj_t* parent, int start_offset,
                                   int address_start_y, bool show_start_ellipsis,
                                   bool show_end_ellipsis, int effective_chars) {
  const char* address = widget->address;
  const int addr_len = strlen(address);
  int char_index = start_offset;
  int line = 0;
  int group_in_line = 0;

  int total_width =
    (GROUPS_PER_LINE * CHARS_PER_GROUP * CHAR_WIDTH) + ((GROUPS_PER_LINE - 1) * BULLET_WIDTH);
  int start_x = -total_width / 2;

  // Show starting ellipsis if continuing from previous page
  if (show_start_ellipsis) {
    if (widget->label_count >= MAX_LABELS)
      return;  // Safety check
    lv_obj_t* ellipsis = lv_label_create(parent);
    if (!ellipsis)
      return;
    lv_label_set_text(ellipsis, TEXT_ELLIPSIS);
    lv_obj_set_style_text_color(ellipsis, lv_color_white(), 0);
    lv_obj_set_style_text_font(ellipsis, FONT_ADDRESS, 0);
    lv_obj_align(ellipsis, LV_ALIGN_CENTER, start_x + (CHARS_PER_GROUP * CHAR_WIDTH / 2),
                 address_start_y);
    widget->char_labels[widget->label_count++] = ellipsis;
    group_in_line = 1;
  }

  // Render address characters
  while (char_index < addr_len && char_index - start_offset < MAX_CHARS_PER_SCREEN) {
    if (show_end_ellipsis && (char_index >= start_offset + effective_chars)) {
      break;
    }

    int y_pos = address_start_y + (line * LINE_HEIGHT);
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));

    int chars_in_group = 0;
    for (int i = 0; i < CHARS_PER_GROUP && char_index < addr_len; i++) {
      if (show_end_ellipsis && char_index >= start_offset + effective_chars) {
        break;
      }

      if (widget->label_count >= MAX_LABELS)
        return;  // Safety check
      char char_str[2] = {address[char_index], '\0'};
      lv_obj_t* label = lv_label_create(parent);
      if (!label)
        return;

      lv_label_set_text(label, char_str);
      lv_obj_set_style_text_color(label, lv_color_white(), 0);
      lv_obj_set_style_text_font(label, FONT_ADDRESS, 0);
      lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, 0);
      lv_obj_set_width(label, CHAR_WIDTH);
      lv_obj_align(label, LV_ALIGN_CENTER, x_pos + (i * CHAR_WIDTH) + (CHAR_WIDTH / 2), y_pos);

      widget->char_labels[widget->label_count++] = label;
      char_index++;
      chars_in_group++;
    }

    // Add bullet between groups
    if (chars_in_group == CHARS_PER_GROUP && char_index < addr_len &&
        group_in_line < GROUPS_PER_LINE - 1 &&
        !(show_end_ellipsis && char_index >= start_offset + effective_chars)) {
      if (widget->label_count >= MAX_LABELS)
        return;  // Safety check
      lv_obj_t* bullet = lv_img_create(parent);
      if (!bullet)
        return;
      lv_img_set_src(bullet, &dot);
      lv_obj_align(bullet, LV_ALIGN_CENTER,
                   x_pos + (CHARS_PER_GROUP * CHAR_WIDTH) + (BULLET_WIDTH / 2), y_pos);
      widget->char_labels[widget->label_count++] = bullet;
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
    if (widget->label_count >= MAX_LABELS)
      return;  // Safety check
    int y_pos = address_start_y + (line * LINE_HEIGHT);
    int x_pos = start_x + (group_in_line * (CHARS_PER_GROUP * CHAR_WIDTH + BULLET_WIDTH));

    lv_obj_t* ellipsis = lv_label_create(parent);
    if (!ellipsis)
      return;
    lv_label_set_text(ellipsis, TEXT_ELLIPSIS);
    lv_obj_set_style_text_color(ellipsis, lv_color_white(), 0);
    lv_obj_set_style_text_font(ellipsis, FONT_ADDRESS, 0);
    lv_obj_align(ellipsis, LV_ALIGN_CENTER, x_pos + (CHAR_WIDTH * 2), y_pos);
    widget->char_labels[widget->label_count++] = ellipsis;
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

void address_display_create_page(lv_obj_t* parent, address_display_t* widget, int page_num) {
  if (!widget || !widget->is_initialized || !parent || !widget->address) {
    return;
  }

  // Clear any previous labels (in case we're reusing the widget)
  for (int i = 0; i < widget->label_count; i++) {
    if (widget->char_labels[i]) {
      lv_obj_del(widget->char_labels[i]);
      widget->char_labels[i] = NULL;
    }
  }
  widget->label_count = 0;

  // True display capacity: 5 lines × 3 groups × 4 chars = 60 chars
  const int chars_per_single_page = MAX_LINES * GROUPS_PER_LINE * CHARS_PER_GROUP;
  const bool show_start_ellipsis = (page_num > 0);
  const bool show_end_ellipsis = (page_num < widget->total_pages - 1);

  // Calculate starting offset for this page
  int start_offset;
  if (page_num == 0) {
    start_offset = 0;
  } else {
    // First page had 56 chars, then each subsequent page has 52
    start_offset = (chars_per_single_page - CHARS_PER_GROUP) +
                   ((page_num - 1) * (chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP));
  }

  // Calculate effective chars for this page
  int effective_chars;
  if (show_start_ellipsis && show_end_ellipsis) {
    // Middle page: 60 - 4 - 4 = 52
    effective_chars = chars_per_single_page - CHARS_PER_GROUP - CHARS_PER_GROUP;
  } else if (show_end_ellipsis) {
    // First page: 60 - 4 = 56
    effective_chars = chars_per_single_page - CHARS_PER_GROUP;
  } else if (show_start_ellipsis) {
    // Last page: 60 - 4 = 56
    effective_chars = chars_per_single_page - CHARS_PER_GROUP;
  } else {
    // Single page: 60
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

  // Center vertically, accounting for check button at bottom (80px + 40px margin = 120px)
  // Shift content up by half the check button space to visually center
  const int check_button_space = 120;
  int address_start_y = -(total_height / 2) - (check_button_space / 2) + 10;

  render_address_content(widget, parent, start_offset, address_start_y, show_start_ellipsis,
                         show_end_ellipsis, effective_chars);
}

void address_display_destroy(address_display_t* widget) {
  if (!widget) {
    return;
  }

  // Delete all created labels
  for (int i = 0; i < widget->label_count; i++) {
    if (widget->char_labels[i]) {
      lv_obj_del(widget->char_labels[i]);
      widget->char_labels[i] = NULL;
    }
  }

  // Clear the widget state
  memset(widget, 0, sizeof(address_display_t));
}
