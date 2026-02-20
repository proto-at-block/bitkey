#include "screen_power_off.h"

#include "assert.h"
#include "lvgl/lvgl.h"
#include "uc.h"
#include "uxc.pb.h"

static lv_obj_t* screen = NULL;

static void _screen_power_off_handle_touch_event(lv_event_t* event) {
  const lv_event_code_t code = lv_event_get_code(event);
  if (code != LV_EVENT_PRESSED) {
    return;
  }

#ifdef EMBEDDED_BUILD
  lv_indev_t* indev = lv_indev_get_act();
  if (indev == NULL) {
    return;
  }

  lv_point_t point;
  lv_indev_get_point(indev, &point);

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  if (msg == NULL) {
    return;
  }

  msg->which_msg = fwpb_uxc_msg_device_display_touch_tag;
  msg->msg.display_touch.event = fwpb_display_touch_display_touch_event_DISPLAY_TOUCH_EVENT_TOUCH;
  msg->msg.display_touch.has_coord = true;
  msg->msg.display_touch.coord.x = point.x;
  msg->msg.display_touch.coord.y = point.y;
  uc_send_immediate(msg);
#endif
}

lv_obj_t* screen_power_off_init(void* ctx) {
  (void)ctx;
  ASSERT(screen == NULL);

  screen = lv_obj_create(NULL);
  if (screen == NULL) {
    return NULL;
  }

  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_add_event_cb(screen, _screen_power_off_handle_touch_event, LV_EVENT_PRESSED, NULL);
  return screen;
}

void screen_power_off_destroy(void) {
  if (screen != NULL) {
    lv_obj_del(screen);
    screen = NULL;
  }
}

void screen_power_off_update(void* ctx) {
  (void)ctx;
}
