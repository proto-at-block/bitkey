#include "page_indicator.h"

#include "assert.h"

// Arc configuration
#define ARC_BACKGROUND_WIDTH 50  // Total arc span in degrees
#define ARC_WIDTH            12
#define ARC_EXTRA_DEGREES    2  // Extra size for foreground arc
#define ARC_EDGE_GAP         8  // Gap between outer edge of arc and screen edge
#define ARC_CENTER_ANGLE     0  // Center position: 0° is right, 90° is bottom
#define ARC_START_ANGLE \
  (360 -                \
   ARC_BACKGROUND_WIDTH / 2)  // Calculated start angle (wraps around from 360 to center on 0°)
#define ARC_BG_COLOR     0x404040                              // Light grey for background
#define ARC_FG_COLOR     0xFFFFFF                              // White for foreground
#define ARC_RADIUS       (233 - ARC_WIDTH / 2 - ARC_EDGE_GAP)  // Radius with gap from screen edge
#define ANIM_DURATION_MS 200                                   // Animation duration

// Calculate arc angles based on page
static void calculate_arc_angles(int current_page, int total_pages, int16_t* start_angle,
                                 int16_t* end_angle) {
  // Calculate the arc segment size for each page
  int16_t segment_degrees = ARC_BACKGROUND_WIDTH / total_pages;
  int16_t fg_arc_degrees = segment_degrees + ARC_EXTRA_DEGREES;

  // Calculate position for current page
  int16_t offset = current_page * segment_degrees;

  *start_angle = ARC_START_ANGLE + offset;
  *end_angle = *start_angle + fg_arc_degrees;
}

// Animation callback to update foreground arc position
static void arc_anim_cb(void* var, int32_t value) {
  lv_obj_t* arc = (lv_obj_t*)var;
  page_indicator_t* indicator = (page_indicator_t*)lv_obj_get_user_data(arc);

  if (!indicator || !indicator->is_initialized) {
    return;
  }

  // Calculate angles for the animated position (value represents the interpolated page number)
  int16_t animated_start, animated_end;
  calculate_arc_angles(value, indicator->total_pages, &animated_start, &animated_end);

  lv_arc_set_angles(arc, animated_start, animated_end);
}

void page_indicator_create(lv_obj_t* parent, page_indicator_t* indicator, int total_pages) {
  ASSERT(parent != NULL);
  ASSERT(indicator != NULL);
  ASSERT(total_pages > 0 && total_pages <= PAGE_INDICATOR_MAX_PAGES);

  indicator->total_pages = total_pages;
  indicator->current_page = 0;
  indicator->is_initialized = true;

  // Create background arc
  indicator->background_arc = lv_arc_create(parent);
  lv_obj_set_size(indicator->background_arc, ARC_RADIUS * 2, ARC_RADIUS * 2);
  lv_obj_center(indicator->background_arc);

  // Remove knob
  lv_obj_remove_style(indicator->background_arc, NULL, LV_PART_KNOB);
  lv_obj_clear_flag(indicator->background_arc, LV_OBJ_FLAG_CLICKABLE);

  // Set the arc angles and range - use indicator part for the visible arc
  lv_arc_set_bg_angles(indicator->background_arc, ARC_START_ANGLE,
                       ARC_START_ANGLE + ARC_BACKGROUND_WIDTH);
  lv_arc_set_angles(indicator->background_arc, ARC_START_ANGLE,
                    ARC_START_ANGLE + ARC_BACKGROUND_WIDTH);
  lv_arc_set_range(indicator->background_arc, 0, 100);
  lv_arc_set_value(indicator->background_arc, 100);

  // Hide the background track
  lv_obj_set_style_arc_opa(indicator->background_arc, LV_OPA_TRANSP, LV_PART_MAIN);

  // Style the indicator arc
  lv_obj_set_style_arc_width(indicator->background_arc, ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(indicator->background_arc, lv_color_hex(ARC_BG_COLOR),
                             LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(indicator->background_arc, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(indicator->background_arc, true, LV_PART_INDICATOR);

  // Make the background transparent
  lv_obj_set_style_bg_opa(indicator->background_arc, LV_OPA_TRANSP, 0);

  // Create foreground arc (white, slides over background)
  indicator->foreground_arc = lv_arc_create(parent);
  lv_obj_set_size(indicator->foreground_arc, ARC_RADIUS * 2, ARC_RADIUS * 2);
  lv_obj_center(indicator->foreground_arc);

  // Remove knob
  lv_obj_remove_style(indicator->foreground_arc, NULL, LV_PART_KNOB);
  lv_obj_clear_flag(indicator->foreground_arc, LV_OBJ_FLAG_CLICKABLE);

  // Calculate initial position
  int16_t segment_degrees = ARC_BACKGROUND_WIDTH / total_pages;
  int16_t fg_arc_degrees = segment_degrees + ARC_EXTRA_DEGREES;

  // Style the foreground arc
  lv_arc_set_bg_angles(indicator->foreground_arc, 0, 0);
  lv_obj_set_style_arc_opa(indicator->foreground_arc, LV_OPA_TRANSP, LV_PART_MAIN);

  lv_arc_set_angles(indicator->foreground_arc, ARC_START_ANGLE, ARC_START_ANGLE + fg_arc_degrees);
  lv_obj_set_style_arc_width(indicator->foreground_arc, ARC_WIDTH, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(indicator->foreground_arc, lv_color_hex(ARC_FG_COLOR),
                             LV_PART_INDICATOR);
  lv_obj_set_style_arc_opa(indicator->foreground_arc, LV_OPA_COVER, LV_PART_INDICATOR);
  lv_obj_set_style_arc_rounded(indicator->foreground_arc, true, LV_PART_INDICATOR);

  // Make the background transparent
  lv_obj_set_style_bg_opa(indicator->foreground_arc, LV_OPA_TRANSP, 0);

  // Store pointer to indicator struct for animation callback
  lv_obj_set_user_data(indicator->foreground_arc, indicator);

  // Initialize animation (not started yet)
  lv_anim_init(&indicator->position_anim);
}

void page_indicator_update(page_indicator_t* indicator, int current_page) {
  if (!indicator || !indicator->is_initialized || current_page < 0 ||
      current_page >= indicator->total_pages) {
    return;
  }

  int old_page = indicator->current_page;
  indicator->current_page = current_page;

  // Delete any existing animation
  lv_anim_del(&indicator->position_anim, NULL);

  // Set up animation to transition from old page to new page
  lv_anim_set_var(&indicator->position_anim, indicator->foreground_arc);
  lv_anim_set_values(&indicator->position_anim, old_page, current_page);
  lv_anim_set_time(&indicator->position_anim, ANIM_DURATION_MS);
  lv_anim_set_path_cb(&indicator->position_anim, lv_anim_path_ease_in_out);
  lv_anim_set_exec_cb(&indicator->position_anim, arc_anim_cb);

  lv_anim_start(&indicator->position_anim);
}

void page_indicator_destroy(page_indicator_t* indicator) {
  if (!indicator || !indicator->is_initialized) {
    return;
  }

  lv_anim_del(&indicator->position_anim, NULL);

  if (indicator->background_arc) {
    lv_obj_del(indicator->background_arc);
    indicator->background_arc = NULL;
  }

  if (indicator->foreground_arc) {
    lv_obj_del(indicator->foreground_arc);
    indicator->foreground_arc = NULL;
  }

  indicator->is_initialized = false;
}
