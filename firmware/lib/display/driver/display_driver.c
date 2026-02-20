#include "display_driver.h"

#include "gfx.h"
#include "log.h"
#include "lvgl.h"
#include "mcu_gpio.h"
#include "rtos.h"
#include "touch.h"
#include "ui.h"

#define DISPLAY_PWR_RAIL_MIN_DELAY_MS   1     // Minimum delay between power rail transitions
#define DISPLAY_PWR_RESET_DELAY_MS      10    // Delay after final rail before reset
#define TOUCH_SCROLL_LIMIT              15    // Scroll limit in pixels
#define TOUCH_LONG_PRESS_TIME_MS        500   // Long press detection time
#define TOUCH_LONG_PRESS_REPEAT_TIME_MS 2000  // Long press repeat time
#define TOUCH_MAX_EVENT_AGE_MS          50    // Discard touch events older than this

// Display configuration.
extern display_config_t display_config;

// Double buffers
#define MAX_DISP_WIDTH  466
#define MAX_DISP_HEIGHT 466
#define MAX_BUF_HEIGHT  233
#define MAX_BUF_SIZE    (MAX_DISP_WIDTH * MAX_BUF_HEIGHT)
static lv_color16_t buf1[MAX_BUF_SIZE];
static lv_color16_t buf2[MAX_BUF_SIZE];
static lv_draw_buf_t draw_buf1;
static lv_draw_buf_t draw_buf2;
static lv_display_t* s_display = NULL;

// ICNA3312 requires even addr writes.
static void even_rounder_cb(lv_event_t* e) {
  lv_area_t* area = lv_event_get_param(e);

  // Floor x1/y1 down to even
  area->x1 &= ~1;
  area->y1 &= ~1;

  // Ceiling of x2/y2 to be odd.
  area->x2 |= 1;
  area->y2 |= 1;

  // Clip
  if (area->x2 >= display_config.gfx_config.display_width) {
    area->x2 = display_config.gfx_config.display_width - 1;
  }
  if (area->y2 >= display_config.gfx_config.display_height) {
    area->y2 = display_config.gfx_config.display_height - 1;
  }
}

static void gfx_flush_complete(void* user_data) {
  lv_display_t* disp = (lv_display_t*)user_data;
  lv_display_flush_ready(disp);
}

static void lvgl_flush_cb(lv_display_t* disp, const lv_area_t* area, uint8_t* color_p) {
  // Swap the RGB565 bytes
  const uint32_t w = area->x2 - area->x1 + 1;
  const uint32_t h = area->y2 - area->y1 + 1;
  const uint32_t pixel_count = w * h;
  lv_draw_sw_rgb565_swap((lv_color16_t*)color_p, pixel_count);

  // Flush to display
  gfx_flush(color_p, area->x1, area->y1, area->x2, area->y2, gfx_flush_complete, disp);
}

static void display_configure_power_pins(void) {
  for (uint8_t i = 0; i < sizeof(display_config.pwr_pins) / sizeof(display_config.pwr_pins[0]);
       i++) {
    if (display_config.pwr_pins[i] != (mcu_gpio_config_t*)NULL) {
      mcu_gpio_configure(display_config.pwr_pins[i], false);
    }
  }
}

// LVGL touch read callback - feeds buffered events with timestamps via continue_reading
static void touch_read_cb(lv_indev_t* indev, lv_indev_data_t* data) {
  (void)indev;

  // Track last known point for reporting position when buffer is empty
  static lv_point_t last_point = {0, 0};

  touch_event_t event;
  uint32_t now_ms = rtos_thread_systime();

  // Pop events, discarding any that are too old
  while (touch_pop_event(&event)) {
    uint32_t age_ms = now_ms - event.timestamp_ms;
    if (age_ms <= TOUCH_MAX_EVENT_AGE_MS) {
      // Event is fresh enough, process it
      // Map coordinates (inverted)
      int32_t x = MAX_DISP_WIDTH - event.coord.x - 1;
      int32_t y = MAX_DISP_HEIGHT - event.coord.y - 1;
      data->point.x = (x < 0) ? 0 : (x >= MAX_DISP_WIDTH) ? MAX_DISP_WIDTH - 1 : x;
      data->point.y = (y < 0) ? 0 : (y >= MAX_DISP_HEIGHT) ? MAX_DISP_HEIGHT - 1 : y;

      // Set state based on event type
      switch (event.event_type) {
        case TOUCH_EVENT_TOUCH_DOWN:
        case TOUCH_EVENT_CONTACT:
          data->state = LV_INDEV_STATE_PRESSED;
          break;
        case TOUCH_EVENT_TOUCH_UP:
        default:
          data->state = LV_INDEV_STATE_RELEASED;
          break;
      }

      // Pass the actual event timestamp - LVGL tick is now synced with rtos_thread_systime()
      data->timestamp = event.timestamp_ms;

      // Check if more events are buffered
      data->continue_reading = (touch_event_buffer_count() > 0);

      // Save point for when buffer is empty
      last_point = data->point;
      return;
    }
    // Event is stale, discard and check next
  }

  // No fresh events in buffer - report RELEASED state
  data->point = last_point;
  data->state = LV_INDEV_STATE_RELEASED;
  data->timestamp = 0;
  data->continue_reading = false;
}

// Initialize touch integration with display
static bool display_touch_init(void) {
  static lv_indev_t* touch_indev = NULL;
  // Create LVGL input device
  touch_indev = lv_indev_create();
  if (!touch_indev) {
    return false;
  }

  lv_indev_set_type(touch_indev, LV_INDEV_TYPE_POINTER);
  lv_indev_set_read_cb(touch_indev, touch_read_cb);

  lv_indev_set_scroll_limit(touch_indev, TOUCH_SCROLL_LIMIT);
  lv_indev_set_long_press_time(touch_indev, TOUCH_LONG_PRESS_TIME_MS);
  lv_indev_set_long_press_repeat_time(touch_indev, TOUCH_LONG_PRESS_REPEAT_TIME_MS);

  return true;
}

void display_init(void) {
  display_configure_power_pins();

  display_power_on();

  // Initialize the display hardware
  gfx_init(&display_config.gfx_config);

  // Start LVGL
  lv_init();

  // Sync LVGL tick with RTOS time so touch timestamps work correctly
  lv_tick_inc(rtos_thread_systime());

  // Setup draw buffers using config dimensions
  uint16_t disp_width = display_config.gfx_config.display_width;
  uint16_t disp_height = display_config.gfx_config.display_height;
  uint16_t buf_height = MAX_BUF_HEIGHT;
  uint32_t stride = disp_width * sizeof(lv_color16_t);

  lv_draw_buf_init(&draw_buf1, disp_width, buf_height, LV_COLOR_FORMAT_RGB565, stride, buf1,
                   sizeof(buf1));
  lv_draw_buf_init(&draw_buf2, disp_width, buf_height, LV_COLOR_FORMAT_RGB565, stride, buf2,
                   sizeof(buf2));
  // Setup display
  lv_display_t* disp = lv_display_create(disp_width, disp_height);
  s_display = disp;  // Store for runtime rotation updates
  lv_display_set_draw_buffers(disp, &draw_buf1, &draw_buf2);
  lv_display_set_flush_cb(disp, lvgl_flush_cb);

  // Setup Rounder
  lv_display_add_event_cb(disp, even_rounder_cb, LV_EVENT_INVALIDATE_AREA, NULL);

  // Initialize the UI with brightness and FPS callbacks
  ui_init(gfx_set_brightness, gfx_get_fps, gfx_get_effective_fps);

  // Initialize touch driver
  if (!display_touch_init()) {
    LOGE("Touch initialization failed, continuing without touch");
  }
}

void display_update(void) {
  // Sync LVGL tick to current RTOS time before processing events.
  // This ensures touch event timestamps (from rtos_thread_systime()) are always
  // <= lv_tick_get(), preventing unsigned underflow in elapsed time calculations.
  uint32_t now_ms = rtos_thread_systime();
  uint32_t current_tick = lv_tick_get();
  if (now_ms > current_tick) {
    lv_tick_inc(now_ms - current_tick);
  }

  lv_task_handler();
}

void display_power_on(void) {
  // Power sequencing: 1v8 -> 3v3 -> avdd -> vbat
  if (display_config.pwr.pwr_1v8_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_set(display_config.pwr.pwr_1v8_en);
    rtos_thread_sleep(DISPLAY_PWR_RAIL_MIN_DELAY_MS);
  }

  if (display_config.pwr.pwr_3v3_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_set(display_config.pwr.pwr_3v3_en);
    rtos_thread_sleep(DISPLAY_PWR_RAIL_MIN_DELAY_MS);
  }

  if (display_config.pwr.pwr_avdd_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_set(display_config.pwr.pwr_avdd_en);
    rtos_thread_sleep(DISPLAY_PWR_RAIL_MIN_DELAY_MS);
  }

  if (display_config.pwr.pwr_vbat_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_set(display_config.pwr.pwr_vbat_en);
    rtos_thread_sleep(DISPLAY_PWR_RESET_DELAY_MS);
  }
}

void display_power_off(void) {
  // Power sequencing: reverse order - vbat -> avdd -> 3v3 -> 1v8
  if (display_config.pwr.pwr_vbat_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_clear(display_config.pwr.pwr_vbat_en);
    rtos_thread_sleep(DISPLAY_PWR_RAIL_MIN_DELAY_MS);
  }

  if (display_config.pwr.pwr_avdd_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_clear(display_config.pwr.pwr_avdd_en);
    rtos_thread_sleep(DISPLAY_PWR_RAIL_MIN_DELAY_MS);
  }

  if (display_config.pwr.pwr_3v3_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_clear(display_config.pwr.pwr_3v3_en);
    rtos_thread_sleep(DISPLAY_PWR_RAIL_MIN_DELAY_MS);
  }

  if (display_config.pwr.pwr_1v8_en != (mcu_gpio_config_t*)NULL) {
    mcu_gpio_clear(display_config.pwr.pwr_1v8_en);
  }
}

void display_set_rotation(bool rotate_180) {
  // Update hardware MADCTL register
  gfx_set_rotation(rotate_180);

  // Update LVGL rotation
  if (s_display) {
    lv_display_set_rotation(s_display,
                            rotate_180 ? LV_DISPLAY_ROTATION_180 : LV_DISPLAY_ROTATION_0);
  }
}
