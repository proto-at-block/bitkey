#include "ui.h"

#include "log.h"
#include "lvgl/lvgl.h"
#include "screens.h"

#include <stdio.h>
#include <string.h>

static struct {
  pb_size_t current_params_tag;
  const screen_t* current_screen;
  fwpb_display_show_screen current_screen_data;

  const screen_t* previous_screen;
  lv_obj_t* previous_screen_obj;

  uint8_t brightness_percent;
  uint8_t local_brightness_percent;
  ui_brightness_callback_t brightness_callback;
  ui_fps_callback_t fps_callback;
  ui_fps_callback_t effective_fps_callback;
  ui_rotation_callback_t rotation_callback;

  uint32_t current_flags;
} state;

static const lv_scr_load_anim_t transition_map[] = {
  [fwpb_display_transition_DISPLAY_TRANSITION_NONE] = LV_SCR_LOAD_ANIM_NONE,
  [fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_LEFT] = LV_SCR_LOAD_ANIM_MOVE_LEFT,
  [fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_RIGHT] = LV_SCR_LOAD_ANIM_MOVE_RIGHT,
  [fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_UP] = LV_SCR_LOAD_ANIM_MOVE_TOP,
  [fwpb_display_transition_DISPLAY_TRANSITION_SLIDE_DOWN] = LV_SCR_LOAD_ANIM_MOVE_BOTTOM,
  [fwpb_display_transition_DISPLAY_TRANSITION_FADE] = LV_SCR_LOAD_ANIM_FADE_ON};

// Forward declarations
static void navigate_to_screen(const fwpb_display_show_screen* show_screen);

static void lvgl_log_cb(lv_log_level_t level, const char* buf) {
  switch (level) {
    case LV_LOG_LEVEL_TRACE:
    case LV_LOG_LEVEL_INFO:
      LOGI("LVGL: %s", buf);
      break;
    case LV_LOG_LEVEL_WARN:
      LOGW("LVGL: %s", buf);
      break;
    case LV_LOG_LEVEL_ERROR:
      LOGE("LVGL: %s", buf);
      break;
    default:
      break;
  }
}

static void update_brightness(bool verify) {
  if (state.brightness_callback) {
    // Never let the screen brightness go below the BRIGHTNESS_MIN to avoid completely black screens
    uint8_t level = (state.brightness_percent * state.local_brightness_percent * 255) / 10000;
    uint8_t min_level = (BRIGHTNESS_MIN * 255) / 100;
    if (level < min_level) {
      level = min_level;
    }
    state.brightness_callback(level, verify);
  }
}

void ui_init(ui_brightness_callback_t brightness_callback, ui_fps_callback_t fps_callback,
             ui_fps_callback_t effective_fps_callback) {
  // Register LVGL logging callback
  lv_log_register_print_cb(lvgl_log_cb);

  // Initialize LVGL display and theme
  lv_disp_t* dispp = lv_display_get_default();
  lv_theme_t* theme = lv_theme_simple_init(dispp);
  lv_disp_set_theme(dispp, theme);

  // Set default screen background to black to prevent white flash on boot
  lv_obj_t* scr = lv_scr_act();
  if (scr) {
    lv_obj_set_style_bg_color(scr, lv_color_black(), 0);
  }

  memset(&state, 0, sizeof(state));
  state.brightness_callback = brightness_callback;
  state.fps_callback = fps_callback;
  state.effective_fps_callback = effective_fps_callback;
  state.brightness_percent = 80;
  state.local_brightness_percent = 100;
  update_brightness(true);
}

void ui_set_brightness(uint8_t brightness_percent, bool verify) {
  if (brightness_percent < BRIGHTNESS_MIN) {
    brightness_percent = BRIGHTNESS_MIN;
  }
  if (brightness_percent > BRIGHTNESS_MAX) {
    brightness_percent = BRIGHTNESS_MAX;
  }
  state.brightness_percent = brightness_percent;
  update_brightness(verify);
}

void ui_set_local_brightness(uint8_t percent) {
  if (percent > 100) {
    percent = 100;
  }
  state.local_brightness_percent = percent;
  update_brightness(false);
}

void ui_set_rotation_callback(ui_rotation_callback_t rotation_callback) {
  state.rotation_callback = rotation_callback;
}

fwpb_display_result ui_execute_command(const fwpb_display_command* cmd) {
  if (!cmd) {
    return fwpb_display_result_DISPLAY_RESULT_NOT_INITIALIZED;
  }

  switch (cmd->which_command) {
    case fwpb_display_command_show_screen_tag: {
      if (!cmd->command.show_screen.which_params) {
        return fwpb_display_result_DISPLAY_RESULT_ERROR;
      }

      navigate_to_screen(&cmd->command.show_screen);
      return fwpb_display_result_DISPLAY_RESULT_SUCCESS;
    }

    default: {
      return fwpb_display_result_DISPLAY_RESULT_INVALID_PARAM;
    }
  }
}

static void cleanup_previous_screen(void) {
  // Clean up the previous screen if it exists
  if (state.previous_screen && state.previous_screen->destroy) {
    state.previous_screen->destroy();
    state.previous_screen = NULL;
  }
  state.previous_screen_obj = NULL;
}

static void navigate_to_screen(const fwpb_display_show_screen* show_screen) {
  const screen_t* new_screen = screen_get_by_params_tag(show_screen->which_params);
  if (!new_screen) {
    LOGE("Unknown screen params tag: %u", (unsigned)show_screen->which_params);
    return;  // Unknown screen type
  }

  // Check if rotation flag changed
  bool new_rotate = (show_screen->flags & fwpb_display_flag_DISPLAY_FLAG_ROTATE_180) != 0;
  bool old_rotate = (state.current_flags & fwpb_display_flag_DISPLAY_FLAG_ROTATE_180) != 0;
  if (new_rotate != old_rotate && state.rotation_callback) {
    state.rotation_callback(new_rotate);
  }
  state.current_flags = show_screen->flags;

  // Copy the new screen data
  memcpy(&state.current_screen_data, show_screen, sizeof(fwpb_display_show_screen));

  // Apply brightness if it changed
  if (show_screen->brightness_percent != state.brightness_percent) {
    ui_set_brightness(show_screen->brightness_percent, true);
  }

  const bool is_same_screen =
    (show_screen->which_params == state.current_params_tag && state.current_screen);

  if (is_same_screen) {
    if (state.current_screen->update) {
      state.current_screen->update(&state.current_screen_data);
    }
    return;
  }

  // Force cleanup if re-entering the same screen type
  // This prevents stale event handlers from previous instantiation
  if (show_screen->which_params == state.current_params_tag && state.previous_screen) {
    // Same screen type but new instance - clean up immediately
    cleanup_previous_screen();
  }

  // Before switching screens - check if we have a previous screen to clean up
  // Handles the case where we're doing back-to-back transitions
  if (state.previous_screen_obj && lv_obj_is_valid(state.previous_screen_obj)) {
    // If the previous screen object is still valid but not active, clean it up
    if (lv_scr_act() != state.previous_screen_obj) {
      cleanup_previous_screen();
    }
  }

  lv_obj_t* current_screen_obj = lv_scr_act();

  lv_obj_t* new_screen_obj = NULL;
  if (new_screen->init) {
    new_screen_obj = new_screen->init(&state.current_screen_data);
  }

  if (new_screen_obj) {
    // Store current as previous for next transition only after init succeeds.
    state.previous_screen = state.current_screen;
    state.previous_screen_obj = current_screen_obj;
    state.current_params_tag = show_screen->which_params;

    // Only update current_screen if init succeeded
    state.current_screen = new_screen;

    lv_scr_load_anim_t anim_type = LV_SCR_LOAD_ANIM_NONE;
    if (show_screen->transition >= 0 &&
        show_screen->transition < (int)(sizeof(transition_map) / sizeof(transition_map[0]))) {
      anim_type = transition_map[show_screen->transition];
    }
    if (anim_type == LV_SCR_LOAD_ANIM_NONE || show_screen->duration_ms == 0) {
      lv_scr_load(new_screen_obj);
      // For immediate transitions, we can destroy the previous screen right away
      cleanup_previous_screen();
    } else {
      // For animated transitions, the old screen needs to stay valid during animation
      // We'll clean it up on the next screen transition
      lv_scr_load_anim(new_screen_obj, anim_type, show_screen->duration_ms, 0, false);
    }
  } else {
    if (new_screen->destroy) {
      new_screen->destroy();
    }
    LOGE("Failed to initialize screen params tag: %u", (unsigned)show_screen->which_params);
  }
}

uint32_t ui_get_fps(void) {
  if (state.fps_callback != NULL) {
    return state.fps_callback();
  }
  return 0;
}

uint32_t ui_get_effective_fps(void) {
  if (state.effective_fps_callback != NULL) {
    return state.effective_fps_callback();
  }
  return 0;
}

pb_size_t ui_get_current_params_tag(void) {
  return state.current_params_tag;
}
