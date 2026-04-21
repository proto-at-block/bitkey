#include "screen_onboarding_complete.h"

#include "assert.h"
#include "display.pb.h"
#include "dot_ring.h"
#include "ui.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

// Dot styling
#define DOT_SIZE_RESTING 4
#define DOT_SIZE_ACTIVE  8
#define COLOR_RESTING    0x404040
#define COLOR_ACTIVE     0xFFFFFF
#define BLEND_MAX        255
#define DOT_INACTIVE_OPA LV_OPA_70

// Animation timing
#define ANIM_UPDATE_MS    50
#define RADIATE_CYCLE_MS  1600
#define RADIATE_WIDTH     60
#define SETTLE_TAIL       40
#define RING_START_MS     1200  // Start ring before radiate finishes
#define DISSOLVE_DELAY_MS 350   // How long into ring fill before hexagon starts dissolving
#define DISSOLVE_MS       300   // Total dissolve phase duration
#define DOT_FADE_MS       80    // Per-dot fade duration

// Success ring end state (matches screen_confirmation.c layout, white variant)
#define FONT_TEXT_CONTENT                (&cash_sans_mono_regular_30)
#define SUCCESS_RING_FILL_DURATION_MS    650
#define SUCCESS_RING_HOLD_DURATION_MS    4800
#define SUCCESS_RING_OUTRO_DURATION_MS   650
#define SUCCESS_CONTENT_FADE_DURATION_MS 650
#define SUCCESS_CONTENT_FADE_DELAY_MS \
  (SUCCESS_RING_FILL_DURATION_MS - SUCCESS_CONTENT_FADE_DURATION_MS)
#define COLOR_INACTIVE_RING lv_color_hex(0x404040)
#define INACTIVE_RING_OPA   LV_OPA_70

// Grid center (absolute display coords)
#define GRID_X_CENTER 233
#define GRID_Y_CENTER 234

#define DOTS_COUNT 144

static const struct {
  uint16_t x;
  uint16_t y;
} dot_positions[DOTS_COUNT] = {
  {226, 153}, {239, 153}, {202, 165}, {214, 165}, {226, 165}, {239, 165}, {252, 165}, {264, 165},
  {176, 178}, {189, 178}, {202, 178}, {214, 178}, {226, 178}, {239, 178}, {252, 178}, {264, 178},
  {276, 178}, {289, 178}, {164, 190}, {176, 190}, {189, 190}, {202, 190}, {214, 190}, {226, 190},
  {239, 190}, {252, 190}, {264, 190}, {276, 190}, {289, 190}, {302, 190}, {152, 203}, {164, 203},
  {176, 203}, {189, 203}, {202, 203}, {214, 203}, {226, 203}, {239, 203}, {252, 203}, {264, 203},
  {276, 203}, {289, 203}, {302, 203}, {314, 203}, {152, 215}, {164, 215}, {176, 215}, {189, 215},
  {202, 215}, {214, 215}, {226, 215}, {239, 215}, {252, 215}, {264, 215}, {276, 215}, {289, 215},
  {302, 215}, {314, 215}, {152, 228}, {164, 228}, {176, 228}, {189, 228}, {202, 228}, {214, 228},
  {226, 228}, {239, 228}, {252, 228}, {264, 228}, {276, 228}, {289, 228}, {302, 228}, {314, 228},
  {152, 240}, {164, 240}, {176, 240}, {189, 240}, {202, 240}, {214, 240}, {226, 240}, {239, 240},
  {252, 240}, {264, 240}, {276, 240}, {289, 240}, {302, 240}, {314, 240}, {152, 253}, {164, 253},
  {176, 253}, {189, 253}, {202, 253}, {214, 253}, {226, 253}, {239, 253}, {252, 253}, {264, 253},
  {276, 253}, {289, 253}, {302, 253}, {314, 253}, {152, 265}, {164, 265}, {176, 265}, {189, 265},
  {202, 265}, {214, 265}, {226, 265}, {239, 265}, {252, 265}, {264, 265}, {276, 265}, {289, 265},
  {302, 265}, {314, 265}, {164, 278}, {176, 278}, {189, 278}, {202, 278}, {214, 278}, {226, 278},
  {239, 278}, {252, 278}, {264, 278}, {276, 278}, {289, 278}, {302, 278}, {176, 290}, {189, 290},
  {202, 290}, {214, 290}, {226, 290}, {239, 290}, {252, 290}, {264, 290}, {276, 290}, {289, 290},
  {202, 303}, {214, 303}, {226, 303}, {239, 303}, {252, 303}, {264, 303}, {226, 315}, {239, 315},
};

// Precomputed per-dot data
static uint16_t dot_dist[DOTS_COUNT];
static uint8_t dot_is_major[DOTS_COUNT];
static uint16_t max_dist = 0;

static void precompute_dot_data(void) {
  for (int i = 0; i < DOTS_COUNT; i++) {
    float dx = (float)dot_positions[i].x - (float)GRID_X_CENTER;
    float dy = (float)dot_positions[i].y - (float)GRID_Y_CENTER;
    dot_dist[i] = (uint16_t)sqrtf(dx * dx + dy * dy);
    if (dot_dist[i] > max_dist) {
      max_dist = dot_dist[i];
    }
    int row = ((int)dot_positions[i].y - 153) / 12;
    int col = ((int)dot_positions[i].x - 152) / 12;
    dot_is_major[i] = ((row + col) % 2 == 0) ? 1 : 0;
  }
}

// Animation phases
typedef enum {
  PHASE_RADIATE,    // Highlight sweep radiating outward
  PHASE_CROSSFADE,  // Ring fills while hexagon dissolves (overlapped)
  PHASE_SUCCESS,    // Success ring + content hold
  PHASE_DONE,       // Final state, animation stopped
} anim_phase_t;

static bool ring_started = false;
static bool ring_start_attempted = false;
static bool dissolve_started = false;
static bool success_ring_filled = false;
static bool success_content_visible = false;

static lv_obj_t* screen = NULL;
static lv_obj_t* dot_objs[DOTS_COUNT];
static lv_obj_t* success_label = NULL;
static dot_ring_t success_ring = {0};
static lv_timer_t* anim_timer = NULL;
static lv_timer_t* success_ring_hold_timer = NULL;

static anim_phase_t current_phase = PHASE_RADIATE;
static uint32_t phase_elapsed_ms = 0;
static bool dot_settled[DOTS_COUNT];

// Dissolve random order
static uint8_t dissolve_order[DOTS_COUNT];

static void shuffle_dissolve_order(void) {
  for (int i = 0; i < DOTS_COUNT; i++) {
    dissolve_order[i] = (uint8_t)i;
  }
  uint32_t rng = 0x12345678;
  for (int i = DOTS_COUNT - 1; i > 0; i--) {
    rng = rng * 1664525u + 1013904223u;
    int j = (int)(rng % (uint32_t)(i + 1));
    uint8_t tmp = dissolve_order[i];
    dissolve_order[i] = dissolve_order[j];
    dissolve_order[j] = tmp;
  }
}

static lv_color_t lerp_color(lv_color_t from, lv_color_t to, uint8_t t) {
  lv_color_t result;
  result.red = (uint8_t)(from.red + ((int16_t)to.red - from.red) * t / BLEND_MAX);
  result.green = (uint8_t)(from.green + ((int16_t)to.green - from.green) * t / BLEND_MAX);
  result.blue = (uint8_t)(from.blue + ((int16_t)to.blue - from.blue) * t / BLEND_MAX);
  return result;
}

static void set_dot(int idx, uint8_t intensity) {
  if (!dot_objs[idx]) {
    return;
  }

  lv_coord_t size;
  uint8_t color_t;

  if (dot_is_major[idx]) {
    lv_coord_t max_size = DOT_SIZE_ACTIVE;
    size = (lv_coord_t)(DOT_SIZE_RESTING + ((max_size - DOT_SIZE_RESTING) * intensity) / BLEND_MAX);
    color_t = intensity;
  } else {
    size = DOT_SIZE_RESTING;
    color_t = (uint8_t)((intensity * 80) / BLEND_MAX);
  }

  lv_coord_t x = (lv_coord_t)dot_positions[idx].x;
  lv_coord_t y = (lv_coord_t)dot_positions[idx].y;

  lv_obj_set_size(dot_objs[idx], size, size);
  lv_obj_set_pos(dot_objs[idx], x - size / 2, y - size / 2);

  lv_color_t resting = lv_color_hex(COLOR_RESTING);
  lv_color_t active = lv_color_hex(COLOR_ACTIVE);
  lv_obj_set_style_bg_color(dot_objs[idx], lerp_color(resting, active, color_t), 0);
  lv_opa_t opa =
    (lv_opa_t)(DOT_INACTIVE_OPA + ((LV_OPA_COVER - DOT_INACTIVE_OPA) * color_t) / BLEND_MAX);
  lv_obj_set_style_bg_opa(dot_objs[idx], opa, 0);
}

static void set_dot_settled(int idx) {
  if (!dot_objs[idx]) {
    return;
  }
  lv_coord_t x = (lv_coord_t)dot_positions[idx].x;
  lv_coord_t y = (lv_coord_t)dot_positions[idx].y;
  lv_obj_set_size(dot_objs[idx], DOT_SIZE_RESTING, DOT_SIZE_RESTING);
  lv_obj_set_pos(dot_objs[idx], x - DOT_SIZE_RESTING / 2, y - DOT_SIZE_RESTING / 2);
  lv_obj_set_style_bg_color(dot_objs[idx], lv_color_hex(COLOR_ACTIVE), 0);
  lv_obj_set_style_bg_opa(dot_objs[idx], LV_OPA_COVER, 0);
}

static void set_dot_settling(int idx, uint8_t shrink_t) {
  if (!dot_objs[idx]) {
    return;
  }
  lv_coord_t from_size = dot_is_major[idx] ? DOT_SIZE_ACTIVE : DOT_SIZE_RESTING;
  lv_coord_t size =
    (lv_coord_t)(from_size - ((from_size - DOT_SIZE_RESTING) * shrink_t) / BLEND_MAX);
  lv_coord_t x = (lv_coord_t)dot_positions[idx].x;
  lv_coord_t y = (lv_coord_t)dot_positions[idx].y;
  lv_obj_set_size(dot_objs[idx], size, size);
  lv_obj_set_pos(dot_objs[idx], x - size / 2, y - size / 2);
  lv_obj_set_style_bg_color(dot_objs[idx], lv_color_hex(COLOR_ACTIVE), 0);
  lv_obj_set_style_bg_opa(dot_objs[idx], LV_OPA_COVER, 0);
}

// Forward declarations for success ring animation
static void success_content_set_opa(lv_opa_t opa);
static void success_content_opa_anim_cb(void* var, int32_t value);
static void success_content_fade_in_ready_cb(lv_anim_t* anim);
static void success_ring_fill_complete(void* user_data);
static void success_ring_hold_timer_cb(lv_timer_t* timer);
static void success_ring_outro_anim_cb(void* var, int32_t value);
static int get_success_ring_order(int dot_index, int total_dots);
static void layout_success_content(void);
static void maybe_start_success_hold_timer(void);
static bool start_success_sequence(void);
static void create_success_content(void);

static void anim_timer_cb(lv_timer_t* timer) {
  (void)timer;

  phase_elapsed_ms += ANIM_UPDATE_MS;

  switch (current_phase) {
    case PHASE_RADIATE: {
      uint32_t total_travel = max_dist + RADIATE_WIDTH;
      uint32_t ring_center = (phase_elapsed_ms * total_travel) / RADIATE_CYCLE_MS;

      for (int i = 0; i < DOTS_COUNT; i++) {
        if (dot_settled[i]) {
          continue;
        }
        int32_t dist_from_ring = (int32_t)dot_dist[i] - (int32_t)ring_center;

        if (dist_from_ring < -(int32_t)SETTLE_TAIL) {
          dot_settled[i] = true;
          set_dot_settled(i);
        } else if (dist_from_ring < 0) {
          uint8_t shrink_t = (uint8_t)((-dist_from_ring) * BLEND_MAX / (int32_t)SETTLE_TAIL);
          set_dot_settling(i, shrink_t);
        } else if (dist_from_ring < (int32_t)(RADIATE_WIDTH / 2)) {
          uint8_t intensity =
            (uint8_t)(BLEND_MAX - (dist_from_ring * BLEND_MAX) / (RADIATE_WIDTH / 2));
          set_dot(i, intensity);
        } else {
          set_dot(i, 0);
        }
      }

      if (!ring_start_attempted && phase_elapsed_ms >= RING_START_MS) {
        ring_start_attempted = true;
        ring_started = start_success_sequence();
      }

      if (phase_elapsed_ms >= RADIATE_CYCLE_MS) {
        for (int i = 0; i < DOTS_COUNT; i++) {
          if (!dot_settled[i]) {
            dot_settled[i] = true;
            set_dot_settled(i);
          }
        }
        current_phase = PHASE_CROSSFADE;
        phase_elapsed_ms = 0;
        dissolve_started = false;
      }
      break;
    }

    case PHASE_CROSSFADE: {
      if (!dissolve_started && phase_elapsed_ms >= DISSOLVE_DELAY_MS) {
        dissolve_started = true;
        shuffle_dissolve_order();
      }

      if (dissolve_started) {
        uint32_t dissolve_elapsed = phase_elapsed_ms - DISSOLVE_DELAY_MS;
        uint32_t stagger_range = (DISSOLVE_MS > DOT_FADE_MS) ? (DISSOLVE_MS - DOT_FADE_MS) : 1;

        for (int i = 0; i < DOTS_COUNT; i++) {
          int idx = dissolve_order[i];
          if (!dot_objs[idx]) {
            continue;
          }

          uint32_t start_ms = (uint32_t)((uint64_t)i * stagger_range / DOTS_COUNT);
          if (dissolve_elapsed <= start_ms) {
            continue;
          }

          uint32_t elapsed_since_start = dissolve_elapsed - start_ms;
          if (elapsed_since_start >= DOT_FADE_MS) {
            lv_obj_set_style_bg_opa(dot_objs[idx], LV_OPA_TRANSP, 0);
          } else {
            lv_opa_t opa =
              (lv_opa_t)(LV_OPA_COVER - (elapsed_since_start * LV_OPA_COVER) / DOT_FADE_MS);
            lv_obj_set_style_bg_opa(dot_objs[idx], opa, 0);
          }
        }

        if (dissolve_elapsed >= DISSOLVE_MS) {
          for (int i = 0; i < DOTS_COUNT; i++) {
            if (dot_objs[i]) {
              lv_obj_del(dot_objs[i]);
              dot_objs[i] = NULL;
            }
          }

          // Retry once after the hexagon dots are reclaimed if the overlapped
          // ring start failed under memory pressure.
          if (!ring_started) {
            ring_started = start_success_sequence();
            if (!ring_started) {
              success_ring_filled = true;
            }
          }

          create_success_content();
          current_phase = PHASE_SUCCESS;
          phase_elapsed_ms = 0;
          if (anim_timer) {
            lv_timer_del(anim_timer);
            anim_timer = NULL;
          }
        }
      }
      break;
    }

    case PHASE_SUCCESS:
    case PHASE_DONE:
      break;
  }
}

lv_obj_t* screen_onboarding_complete_init(void* ctx) {
  (void)ctx;

  ASSERT(screen == NULL);
  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);

  precompute_dot_data();
  memset(dot_objs, 0, sizeof(dot_objs));
  memset(dot_settled, 0, sizeof(dot_settled));
  memset(&success_ring, 0, sizeof(success_ring));
  current_phase = PHASE_RADIATE;
  phase_elapsed_ms = 0;
  ring_started = false;
  ring_start_attempted = false;
  dissolve_started = false;
  success_ring_filled = false;
  success_content_visible = false;
  success_label = NULL;
  success_ring_hold_timer = NULL;

  for (int i = 0; i < DOTS_COUNT; i++) {
    dot_objs[i] = lv_obj_create(screen);
    if (!dot_objs[i]) {
      continue;
    }

    lv_obj_set_style_radius(dot_objs[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_border_opa(dot_objs[i], LV_OPA_TRANSP, 0);
    lv_obj_clear_flag(dot_objs[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);

    set_dot(i, 0);
  }

  // Success ring and label are created later in start_success_sequence
  // to avoid OOM — the 144 grid dots use most of the LVGL memory.

  anim_timer = lv_timer_create(anim_timer_cb, ANIM_UPDATE_MS, NULL);

  return screen;
}

void screen_onboarding_complete_destroy(void) {
  if (!screen) {
    return;
  }

  lv_obj_t* screen_to_delete = screen;
  screen = NULL;

  if (success_ring_hold_timer) {
    lv_timer_del(success_ring_hold_timer);
    success_ring_hold_timer = NULL;
  }
  if (anim_timer) {
    lv_timer_del(anim_timer);
    anim_timer = NULL;
  }
  lv_anim_del(&success_ring, success_content_opa_anim_cb);
  lv_anim_del(&success_ring, success_ring_outro_anim_cb);
  dot_ring_destroy(&success_ring);

  lv_obj_del(screen_to_delete);
  success_label = NULL;
  memset(dot_objs, 0, sizeof(dot_objs));
}

void screen_onboarding_complete_update(void* ctx) {
  if (!screen) {
    screen_onboarding_complete_init(ctx);
  }
}

// --- Success ring animation (adapted from screen_confirmation.c, white variant) ---

static void layout_success_content(void) {
  if (!success_label)
    return;

  lv_obj_align(success_label, LV_ALIGN_CENTER, 0, 0);
}

static void success_content_set_opa(lv_opa_t opa) {
  if (success_label) {
    lv_obj_set_style_text_opa(success_label, opa, 0);
  }
}

static void success_content_opa_anim_cb(void* var, int32_t value) {
  (void)var;
  success_content_set_opa((lv_opa_t)value);
}

static void success_content_fade_in_ready_cb(lv_anim_t* anim) {
  (void)anim;

  if (!screen) {
    return;
  }

  success_content_set_opa(LV_OPA_COVER);
  success_content_visible = true;
  maybe_start_success_hold_timer();
}

static int get_success_ring_order(int dot_index, int total_dots) {
  int top_index = total_dots / 2;
  int rotated_index = dot_index - top_index;
  if (rotated_index < 0)
    rotated_index += total_dots;
  return rotated_index;
}

static bool start_success_sequence(void) {
  if (success_ring.is_initialized && success_ring.dot_count > 0) {
    return true;
  }

  dot_ring_create(screen, &success_ring);
  if (!success_ring.is_initialized || success_ring.dot_count == 0) {
    return false;
  }

  dot_ring_show(&success_ring);
  dot_ring_animate_fill(&success_ring, 100, SUCCESS_RING_FILL_DURATION_MS, DOT_RING_COLOR_WHITE,
                        DOT_RING_FILL_CLOCKWISE_TOP, success_ring_fill_complete, NULL);
  return true;
}

static void create_success_content(void) {
  success_label = lv_label_create(screen);
  if (success_label) {
    lv_label_set_text(success_label, "YOUR WALLET IS READY");
    lv_obj_set_style_text_color(success_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(success_label, FONT_TEXT_CONTENT, 0);
    lv_obj_set_style_text_opa(success_label, LV_OPA_TRANSP, 0);
    layout_success_content();
  }

  // Fade in content
  lv_anim_t fade_in_anim;
  lv_anim_init(&fade_in_anim);
  lv_anim_set_var(&fade_in_anim, &success_ring);
  lv_anim_set_values(&fade_in_anim, 0, LV_OPA_COVER);
  lv_anim_set_duration(&fade_in_anim, SUCCESS_CONTENT_FADE_DURATION_MS);
  lv_anim_set_exec_cb(&fade_in_anim, success_content_opa_anim_cb);
  lv_anim_set_ready_cb(&fade_in_anim, success_content_fade_in_ready_cb);
  lv_anim_set_path_cb(&fade_in_anim, lv_anim_path_ease_out);
  lv_anim_start(&fade_in_anim);
}

static void maybe_start_success_hold_timer(void) {
  if (!screen || success_ring_hold_timer || !success_ring_filled || !success_content_visible) {
    return;
  }

  success_ring_hold_timer =
    lv_timer_create(success_ring_hold_timer_cb, SUCCESS_RING_HOLD_DURATION_MS, NULL);
  if (success_ring_hold_timer) {
    lv_timer_set_repeat_count(success_ring_hold_timer, 1);
  }
}

static void success_ring_fill_complete(void* user_data) {
  (void)user_data;

  if (!screen) {
    return;
  }

  success_ring_filled = true;
  maybe_start_success_hold_timer();
}

static void success_ring_hold_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (success_ring_hold_timer) {
    lv_timer_del(success_ring_hold_timer);
    success_ring_hold_timer = NULL;
  }

  // Outro: ring dots deactivate from top, content fades out
  lv_anim_t anim;
  lv_anim_init(&anim);
  lv_anim_set_var(&anim, &success_ring);
  lv_anim_set_values(&anim, 0, success_ring.dot_count);
  lv_anim_set_duration(&anim, SUCCESS_RING_OUTRO_DURATION_MS);
  lv_anim_set_exec_cb(&anim, success_ring_outro_anim_cb);
  lv_anim_set_path_cb(&anim, lv_anim_path_ease_out);
  lv_anim_start(&anim);

  lv_anim_t fade_out_anim;
  lv_anim_init(&fade_out_anim);
  lv_anim_set_var(&fade_out_anim, &success_ring);
  lv_anim_set_values(&fade_out_anim, LV_OPA_COVER, LV_OPA_TRANSP);
  lv_anim_set_duration(&fade_out_anim, SUCCESS_CONTENT_FADE_DURATION_MS);
  lv_anim_set_delay(&fade_out_anim, SUCCESS_CONTENT_FADE_DELAY_MS);
  lv_anim_set_exec_cb(&fade_out_anim, success_content_opa_anim_cb);
  lv_anim_set_path_cb(&fade_out_anim, lv_anim_path_ease_out);
  lv_anim_start(&fade_out_anim);
}

static void success_ring_outro_anim_cb(void* var, int32_t value) {
  dot_ring_t* ring = (dot_ring_t*)var;
  if (!ring || !ring->is_initialized || ring->dot_count == 0)
    return;

  int32_t inactive_count = value;
  if (inactive_count < 0)
    inactive_count = 0;
  else if (inactive_count > ring->dot_count)
    inactive_count = ring->dot_count;

  for (uint16_t i = 0; i < ring->dot_count; i++) {
    bool should_be_active = get_success_ring_order(i, ring->dot_count) >= inactive_count;
    dot_ring_set_dot_state(ring, i, should_be_active, lv_color_white(), LV_OPA_COVER,
                           COLOR_INACTIVE_RING, INACTIVE_RING_OPA);
  }
}
