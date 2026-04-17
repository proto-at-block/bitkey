#pragma once

#include "lvgl/lvgl.h"

#include <stdbool.h>

#define ADDRESS_DISPLAY_MAX_LABELS 200

typedef struct {
  lv_obj_t* char_labels[ADDRESS_DISPLAY_MAX_LABELS];
  int label_count;
  int total_pages;
  int bottom_reserved_px;
  const char* address;
  bool is_initialized;
} address_display_t;

/**
 * @brief Initialize address display widget and calculate pagination.
 *
 * Analyzes the address string and calculates how many pages are needed
 * to display it using the standard pagination layout (up to 80 chars per page).
 *
 * @param widget Pointer to address_display_t structure
 * @param address Address string to display (can be address, hash, or any text)
 */
void address_display_init(address_display_t* widget, const char* address);

/**
 * @brief Reserve bottom space when vertically centering address content.
 *
 * Use this to reserve UI chrome at the bottom of the parent (for example,
 * check button size + bottom margin). The address content will be centered
 * between the top of the parent and the top edge of this reserved area.
 *
 * @param widget Pointer to initialized address_display_t structure
 * @param bottom_reserved_px Bottom reserved space in pixels
 */
void address_display_set_bottom_reserved(address_display_t* widget, int bottom_reserved_px);

/**
 * @brief Create and render a specific page of the address.
 *
 * Renders one page of the address with proper pagination (showing ellipsis
 * for continuation). Must call address_display_init() first.
 *
 * @param parent LVGL parent object to attach the address content to
 * @param widget Pointer to initialized address_display_t structure
 * @param page_num Page number to render (0-indexed, 0 to total_pages-1)
 */
void address_display_create_page(lv_obj_t* parent, address_display_t* widget, int page_num);

/**
 * @brief Create and render the full address without pagination.
 *
 * Renders the address using the same 4-character grouping as the paginated
 * layout, but allows it to extend downward for scrollable containers.
 *
 * @param parent LVGL parent object to attach the address content to
 * @param widget Pointer to initialized address_display_t structure
 */
void address_display_create_full(lv_obj_t* parent, address_display_t* widget);

/**
 * @brief Get the total height needed to render the full address.
 *
 * Useful when embedding the address in a scrollable container so the parent
 * can reserve enough vertical space before rendering.
 *
 * @param widget Pointer to initialized address_display_t structure
 * @return Height in pixels required for the full address
 */
int address_display_get_full_height(const address_display_t* widget);

/**
 * @brief Get the total number of pages needed for the address.
 *
 * @param widget Pointer to initialized address_display_t structure
 * @return Total number of pages
 */
static inline int address_display_get_page_count(const address_display_t* widget) {
  return widget->total_pages;
}
