#pragma once

#include "lvgl/lvgl.h"

#include <stdbool.h>

typedef struct {
  lv_obj_t* char_labels[100];
  int label_count;
  int total_pages;
  const char* address;
  bool is_initialized;
} address_display_t;

/**
 * @brief Initialize address display widget and calculate pagination.
 *
 * Analyzes the address string and calculates how many pages are needed
 * to display it using the standard pagination layout (60 chars per page).
 *
 * @param widget Pointer to address_display_t structure
 * @param address Address string to display (can be address, hash, or any text)
 */
void address_display_init(address_display_t* widget, const char* address);

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
 * @brief Clean up and destroy the address display widget.
 *
 * Frees all LVGL objects created by the widget.
 *
 * @param widget Pointer to address_display_t structure
 */
void address_display_destroy(address_display_t* widget);

/**
 * @brief Get the total number of pages needed for the address.
 *
 * @param widget Pointer to initialized address_display_t structure
 * @return Total number of pages
 */
static inline int address_display_get_page_count(const address_display_t* widget) {
  return widget->total_pages;
}
