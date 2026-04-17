#include "mfg_burnin_checker.h"

#include "assert.h"
#include "lvgl.h"

#define GRID_SIZE   8
#define SQUARE_SIZE 59

static void draw_event_cb(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_DRAW_MAIN) {
    return;
  }

  lv_obj_t* obj = lv_event_get_target(e);
  lv_layer_t* layer = lv_event_get_layer(e);
  lv_area_t obj_coords;
  lv_obj_get_coords(obj, &obj_coords);

  lv_draw_rect_dsc_t rect_dsc;
  lv_draw_rect_dsc_init(&rect_dsc);
  rect_dsc.bg_color = lv_color_black();
  rect_dsc.bg_opa = LV_OPA_COVER;

  for (int row = 0; row < GRID_SIZE; row++) {
    for (int col = 0; col < GRID_SIZE; col++) {
      if ((row + col) % 2 == 0) {
        lv_area_t area;
        area.x1 = obj_coords.x1 + col * SQUARE_SIZE;
        area.y1 = obj_coords.y1 + row * SQUARE_SIZE;
        area.x2 = area.x1 + SQUARE_SIZE - 1;
        area.y2 = area.y1 + SQUARE_SIZE - 1;

        if (area.x2 > obj_coords.x2)
          area.x2 = obj_coords.x2;
        if (area.y2 > obj_coords.y2)
          area.y2 = obj_coords.y2;

        lv_draw_rect(layer, &rect_dsc, &area);
      }
    }
  }
}

void mfg_burnin_checker_create(lv_obj_t* parent) {
  ASSERT(parent != NULL);

  lv_obj_set_style_bg_color(parent, lv_color_white(), 0);
  lv_obj_set_style_bg_opa(parent, LV_OPA_COVER, 0);
  lv_obj_add_event_cb(parent, draw_event_cb, LV_EVENT_DRAW_MAIN, NULL);
  lv_obj_invalidate(parent);
}
