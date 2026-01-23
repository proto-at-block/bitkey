#include "mfg_burnin_grid.h"

#include "assert.h"

#define BURNIN_GRID_SPACING 20  // Grid spacing in pixels

// Colors
#define BURNIN_COLOR_RED   lv_color_make(255, 0, 0)
#define BURNIN_COLOR_GREEN lv_color_make(0, 255, 0)
#define BURNIN_COLOR_BLUE  lv_color_make(0, 0, 255)

void mfg_burnin_grid_create(lv_obj_t* parent) {
  ASSERT(parent != NULL);

  // Set white background
  lv_obj_set_style_bg_color(parent, lv_color_white(), 0);

  lv_coord_t w = lv_obj_get_width(parent);
  lv_coord_t h = lv_obj_get_height(parent);

  // Draw vertical lines in R, G, B, BK pattern
  int line_index = 0;
  for (lv_coord_t x = 0; x < w; x += BURNIN_GRID_SPACING, line_index++) {
    lv_obj_t* line_obj = lv_obj_create(parent);
    if (!line_obj) {
      return;
    }
    lv_obj_remove_style_all(line_obj);
    lv_obj_set_size(line_obj, 1, h);
    lv_obj_set_pos(line_obj, x, 0);
    lv_obj_add_flag(line_obj, LV_OBJ_FLAG_IGNORE_LAYOUT);

    // Cycle through colors: Red, Green, Blue, Black
    lv_color_t line_color;
    int pattern_index = line_index % 4;
    switch (pattern_index) {
      case 0:
        line_color = BURNIN_COLOR_RED;
        break;
      case 1:
        line_color = BURNIN_COLOR_GREEN;
        break;
      case 2:
        line_color = BURNIN_COLOR_BLUE;
        break;
      default:
        line_color = lv_color_black();
        break;
    }
    lv_obj_set_style_bg_color(line_obj, line_color, 0);
    lv_obj_set_style_bg_opa(line_obj, LV_OPA_COVER, 0);
  }

  // Draw horizontal lines in R, G, B, BK pattern
  line_index = 0;
  for (lv_coord_t y = 0; y < h; y += BURNIN_GRID_SPACING, line_index++) {
    lv_obj_t* line_obj = lv_obj_create(parent);
    if (!line_obj) {
      return;
    }
    lv_obj_remove_style_all(line_obj);
    lv_obj_set_size(line_obj, w, 1);
    lv_obj_set_pos(line_obj, 0, y);
    lv_obj_add_flag(line_obj, LV_OBJ_FLAG_IGNORE_LAYOUT);

    // Cycle through colors: Red, Green, Blue, Black
    lv_color_t line_color;
    int pattern_index = line_index % 4;
    switch (pattern_index) {
      case 0:
        line_color = BURNIN_COLOR_RED;
        break;
      case 1:
        line_color = BURNIN_COLOR_GREEN;
        break;
      case 2:
        line_color = BURNIN_COLOR_BLUE;
        break;
      default:
        line_color = lv_color_black();
        break;
    }
    lv_obj_set_style_bg_color(line_obj, line_color, 0);
    lv_obj_set_style_bg_opa(line_obj, LV_OPA_COVER, 0);
  }
}
