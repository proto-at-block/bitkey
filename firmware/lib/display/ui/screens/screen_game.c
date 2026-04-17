#include "screen_game.h"

#include "assert.h"
#include "display_action.h"
#include "lvgl/lvgl.h"
#include "top_back.h"
#include "ui.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

// Game constants
#define CENTER_X                240
#define CENTER_Y                250
#define SCREEN_WIDTH            (CENTER_X * 2)
#define BOUNDARY_RADIUS         180
#define BALL_RADIUS             10
#define BALL_SPEED              5.0f
#define PADDLE_ARC_RADIUS       160
#define PADDLE_HALF_WIDTH       12.0f  // Angular half-width in degrees (24 deg total)
#define PADDLE_THICKNESS        12
#define PADDLE_COLLISION_BUFFER 5
#define BRICK_RADIUS            12
#define BRICK_COUNT             18
#define GAME_TICK_MS            16
#define GAME_PI                 3.14159265f

// Paddle angle limits (paddle center must stay within danger zone)
#define PADDLE_MIN_ANGLE 52.0f   // 40 + half-width
#define PADDLE_MAX_ANGLE 128.0f  // 140 - half-width

// Screen configuration
#define SCREEN_BRIGHTNESS 100
#define SCORE_Y_OFFSET    100

// Colors
#define COLOR_BALL         0xFFFFFF
#define COLOR_PADDLE       0x4080FF
#define COLOR_BRICK_OUTER  0xFF4040
#define COLOR_BRICK_MIDDLE 0xFFAA00
#define COLOR_BRICK_INNER  0x40FF40
#define COLOR_BOUNDARY     0x303030
#define COLOR_DANGER       0x802020

// Fonts
#define FONT_SCORE (&cash_sans_mono_regular_28)
#define FONT_MSG   (&cash_sans_mono_regular_24)

// Game states
typedef enum {
  GAME_STATE_READY,
  GAME_STATE_PLAYING,
  GAME_STATE_WON,
  GAME_STATE_LOST,
} game_state_t;

// Brick data
typedef struct {
  float angle;   // Angle in degrees from center
  float radius;  // Distance from center
  bool active;
} brick_t;

// Game state
static struct {
  // Ball state
  float ball_x;
  float ball_y;
  float ball_vx;
  float ball_vy;

  // Paddle state
  float paddle_angle;  // Center angle in degrees (270 = bottom)

  // Bricks
  brick_t bricks[BRICK_COUNT];

  // Game state
  game_state_t state;
  uint16_t score;

  // Touch tracking
  bool touch_active;
  int32_t touch_start_x;
  float paddle_start_angle;
} game;

// LVGL objects
static lv_obj_t* screen = NULL;
static lv_obj_t* ball_obj = NULL;
static lv_obj_t* paddle_obj = NULL;
static lv_obj_t* brick_objs[BRICK_COUNT];
static lv_obj_t* boundary_obj = NULL;
static lv_obj_t* danger_arc = NULL;
static lv_obj_t* score_label = NULL;
static lv_obj_t* message_label = NULL;
static top_back_t back_button;
static lv_timer_t* game_timer = NULL;

// Forward declarations
static void game_timer_cb(lv_timer_t* timer);
static void touch_event_cb(lv_event_t* e);
static void init_bricks(void);
static void reset_ball(void);
static void update_ball_position(void);
static void check_boundary_collision(void);
static void check_paddle_collision(void);
static void check_brick_collisions(void);
static void check_lose_condition(void);
static void update_ui(void);
static float deg_to_rad(float deg);

static float deg_to_rad(float deg) {
  return deg * GAME_PI / 180.0f;
}

static void init_bricks(void) {
  int idx = 0;

  // 3 rows centered at top (-90 degrees), symmetric around center
  // Spread across 120 degrees (-150 to -30)

  // Outer arc: 6 bricks at radius 155, 25 degree spacing
  for (int i = 0; i < 6; i++) {
    game.bricks[idx].radius = 155;
    game.bricks[idx].angle = -152.5f + i * 25.0f;  // centered at -90
    game.bricks[idx].active = true;
    idx++;
  }

  // Middle arc: 6 bricks at radius 115, 25 degree spacing (offset 12.5 for stagger)
  for (int i = 0; i < 6; i++) {
    game.bricks[idx].radius = 115;
    game.bricks[idx].angle = -140.0f + i * 25.0f;
    game.bricks[idx].active = true;
    idx++;
  }

  // Inner arc: 6 bricks at radius 75, 25 degree spacing
  for (int i = 0; i < 6; i++) {
    game.bricks[idx].radius = 75;
    game.bricks[idx].angle = -152.5f + i * 25.0f;
    game.bricks[idx].active = true;
    idx++;
  }
}

static void reset_ball(void) {
  // Position ball at center; launched toward paddle on first tap
  game.ball_x = CENTER_X;
  game.ball_y = CENTER_Y;
  game.ball_vx = 0;
  game.ball_vy = 0;
}

static void update_ball_position(void) {
  game.ball_x += game.ball_vx;
  game.ball_y += game.ball_vy;
}

static void check_boundary_collision(void) {
  float dx = game.ball_x - CENTER_X;
  float dy = game.ball_y - CENTER_Y;
  float dist = sqrtf(dx * dx + dy * dy);

  if (dist + BALL_RADIUS > BOUNDARY_RADIUS) {
    // Calculate ball angle (90 degrees = bottom)
    float ball_angle = atan2f(dy, dx) * 180.0f / GAME_PI;

    // Leave a gap at the bottom (around 90 degrees) for ball to escape
    // Gap is from 40 to 140 degrees (same as paddle range)
    if (ball_angle > 40.0f && ball_angle < 140.0f) {
      // Don't bounce - let the ball escape (will trigger loss condition)
      return;
    }

    // Normalize the vector from center to ball
    float nx = dx / dist;
    float ny = dy / dist;

    // Reflect velocity: v' = v - 2(v.n)n
    float dot = game.ball_vx * nx + game.ball_vy * ny;
    game.ball_vx -= 2.0f * dot * nx;
    game.ball_vy -= 2.0f * dot * ny;

    // Push ball back inside
    float overlap = dist + BALL_RADIUS - BOUNDARY_RADIUS;
    game.ball_x -= nx * overlap;
    game.ball_y -= ny * overlap;

    // Enforce a minimum inward radial speed to prevent the ball from orbiting
    // the boundary at a shallow angle for many bounces without making progress.
    float vr = game.ball_vx * nx + game.ball_vy * ny;  // negative = inward after reflection
    float min_radial = 0.3f * BALL_SPEED;
    if (vr > -min_radial) {
      float tx = -ny, ty = nx;  // Tangential unit vector
      float vt = game.ball_vx * tx + game.ball_vy * ty;
      float vt_sign = (vt >= 0.0f) ? 1.0f : -1.0f;
      float new_vr = -min_radial;
      float new_vt = vt_sign * sqrtf(BALL_SPEED * BALL_SPEED - min_radial * min_radial);
      game.ball_vx = new_vr * nx + new_vt * tx;
      game.ball_vy = new_vr * ny + new_vt * ty;
    }
  }
}

static void check_paddle_collision(void) {
  // Convert ball position to polar coordinates
  float dx = game.ball_x - CENTER_X;
  float dy = game.ball_y - CENTER_Y;
  float ball_dist = sqrtf(dx * dx + dy * dy);

  // Ball angle in degrees (-180 to 180, 0 = right, 90 = down/bottom)
  float ball_angle = atan2f(dy, dx) * 180.0f / GAME_PI;

  // Check if ball is approaching paddle - trigger earlier for better feel
  // Paddle is at PADDLE_ARC_RADIUS, detect when ball edge reaches paddle inner edge
  float collision_dist = PADDLE_ARC_RADIUS - PADDLE_THICKNESS / 2 - BALL_RADIUS;

  if (ball_dist >= collision_dist - PADDLE_COLLISION_BUFFER &&
      ball_dist <= PADDLE_ARC_RADIUS + BALL_RADIUS) {
    // Check if ball angle is within paddle arc
    float angle_diff = ball_angle - game.paddle_angle;

    // Normalize angle difference to -180 to 180
    while (angle_diff > 180.0f) angle_diff -= 360.0f;
    while (angle_diff < -180.0f) angle_diff += 360.0f;

    if (angle_diff >= -PADDLE_HALF_WIDTH && angle_diff <= PADDLE_HALF_WIDTH) {
      // Check if ball is moving toward paddle (outward from center)
      float radial_velocity = (dx * game.ball_vx + dy * game.ball_vy) / ball_dist;

      if (radial_velocity > 0) {  // Moving outward (toward paddle)
        // Hit position: -1 (left edge) to +1 (right edge)
        float hit_pos = angle_diff / PADDLE_HALF_WIDTH;

        // Calculate bounce direction based on hit position
        // Center hit: ball goes straight back toward center (angle = paddle_angle + 180)
        // Edge hit: ball deflects at an angle
        // Max deflection angle is ~60 degrees from straight back
        float deflection = hit_pos * 60.0f;  // degrees

        // Outgoing angle: opposite of paddle angle plus deflection
        float out_angle_deg = game.paddle_angle + 180.0f + deflection;
        float out_angle_rad = deg_to_rad(out_angle_deg);

        // Set velocity in that direction
        game.ball_vx = BALL_SPEED * cosf(out_angle_rad);
        game.ball_vy = BALL_SPEED * sinf(out_angle_rad);

        // Push ball safely inside (away from paddle)
        game.ball_x =
          CENTER_X + (collision_dist - PADDLE_COLLISION_BUFFER) * cosf(deg_to_rad(ball_angle));
        game.ball_y =
          CENTER_Y + (collision_dist - PADDLE_COLLISION_BUFFER) * sinf(deg_to_rad(ball_angle));
      }
    }
  }
}

static void check_brick_collisions(void) {
  for (int i = 0; i < BRICK_COUNT; i++) {
    if (!game.bricks[i].active) {
      continue;
    }

    // Get brick position in screen coordinates
    float brick_angle_rad = deg_to_rad(game.bricks[i].angle);
    float brick_x = CENTER_X + game.bricks[i].radius * cosf(brick_angle_rad);
    float brick_y = CENTER_Y + game.bricks[i].radius * sinf(brick_angle_rad);

    // Check distance to ball
    float dx = game.ball_x - brick_x;
    float dy = game.ball_y - brick_y;
    float dist = sqrtf(dx * dx + dy * dy);

    if (dist < BALL_RADIUS + BRICK_RADIUS) {
      // Hit brick!
      game.bricks[i].active = false;
      game.score++;

      // Hide brick
      if (brick_objs[i]) {
        lv_obj_add_flag(brick_objs[i], LV_OBJ_FLAG_HIDDEN);
      }

      // Bounce ball (reflect off brick surface)
      if (dist > 0) {
        float nx = dx / dist;
        float ny = dy / dist;
        float dot = game.ball_vx * nx + game.ball_vy * ny;
        game.ball_vx -= 2.0f * dot * nx;
        game.ball_vy -= 2.0f * dot * ny;

        // Push ball out of brick
        float overlap = BALL_RADIUS + BRICK_RADIUS - dist;
        game.ball_x += nx * overlap;
        game.ball_y += ny * overlap;
      }

      // Check win condition
      bool all_cleared = true;
      for (int j = 0; j < BRICK_COUNT; j++) {
        if (game.bricks[j].active) {
          all_cleared = false;
          break;
        }
      }
      if (all_cleared) {
        game.state = GAME_STATE_WON;
      }

      break;  // Only one brick collision per frame
    }
  }
}

static void check_lose_condition(void) {
  // Ball is lost if it exits through the bottom (past boundary in bottom region)
  float dx = game.ball_x - CENTER_X;
  float dy = game.ball_y - CENTER_Y;
  float dist = sqrtf(dx * dx + dy * dy);

  // Check if ball has escaped past the boundary
  if (dist > BOUNDARY_RADIUS + BALL_RADIUS + 5) {
    game.state = GAME_STATE_LOST;
  }
}

static void update_ui(void) {
  // Update ball position
  if (ball_obj) {
    lv_obj_set_pos(ball_obj, (int32_t)(game.ball_x - BALL_RADIUS),
                   (int32_t)(game.ball_y - BALL_RADIUS));
  }

  // Update paddle arc angles
  if (paddle_obj) {
    lv_arc_set_bg_angles(paddle_obj, (int16_t)(game.paddle_angle - PADDLE_HALF_WIDTH),
                         (int16_t)(game.paddle_angle + PADDLE_HALF_WIDTH));
  }

  // Update score
  if (score_label) {
    char score_str[16];
    snprintf(score_str, sizeof(score_str), "%d", game.score);
    lv_label_set_text(score_label, score_str);
  }

  // Update message
  if (message_label) {
    switch (game.state) {
      case GAME_STATE_READY:
        lv_label_set_text(message_label, "Tap to start");
        lv_obj_clear_flag(message_label, LV_OBJ_FLAG_HIDDEN);
        break;
      case GAME_STATE_PLAYING:
        lv_obj_add_flag(message_label, LV_OBJ_FLAG_HIDDEN);
        break;
      case GAME_STATE_WON:
        lv_label_set_text(message_label, "You win!");
        lv_obj_clear_flag(message_label, LV_OBJ_FLAG_HIDDEN);
        break;
      case GAME_STATE_LOST:
        lv_label_set_text(message_label, "Game over");
        lv_obj_clear_flag(message_label, LV_OBJ_FLAG_HIDDEN);
        break;
    }
  }
}

static void game_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (game.state != GAME_STATE_PLAYING) {
    return;
  }

  update_ball_position();
  check_boundary_collision();
  check_paddle_collision();
  check_brick_collisions();
  check_lose_condition();
  update_ui();
}

static void touch_event_cb(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);

  if (code == LV_EVENT_PRESSED) {
    lv_indev_t* indev = lv_indev_get_act();
    if (indev) {
      lv_point_t point;
      lv_indev_get_point(indev, &point);

      // Record starting position for relative movement
      game.touch_active = true;
      game.touch_start_x = point.x;
      game.paddle_start_angle = game.paddle_angle;

      // If in ready state, start the game on tap
      if (game.state == GAME_STATE_READY) {
        game.state = GAME_STATE_PLAYING;
        // Launch ball toward paddle (10 degrees right of straight down)
        float launch_rad = deg_to_rad(game.paddle_angle - 10.0f);
        game.ball_vx = BALL_SPEED * cosf(launch_rad);
        game.ball_vy = BALL_SPEED * sinf(launch_rad);
      }
    }
  } else if (code == LV_EVENT_PRESSING) {
    lv_indev_t* indev = lv_indev_get_act();
    if (indev && game.touch_active) {
      lv_point_t point;
      lv_indev_get_point(indev, &point);

      // Move paddle relative to touch start (inverted: drag right = paddle goes left)
      float delta_x = (float)(point.x - game.touch_start_x);
      float angle = game.paddle_start_angle - (delta_x / (float)SCREEN_WIDTH) * 100.0f;

      // Clamp to danger zone edges (paddle must stay within 40-140 degree zone)
      if (angle < PADDLE_MIN_ANGLE)
        angle = PADDLE_MIN_ANGLE;
      if (angle > PADDLE_MAX_ANGLE)
        angle = PADDLE_MAX_ANGLE;
      game.paddle_angle = angle;
    }
  } else if (code == LV_EVENT_RELEASED) {
    game.touch_active = false;
  } else if (code == LV_EVENT_CLICKED) {
    // Handle tap to restart after win/loss
    if (game.state == GAME_STATE_WON || game.state == GAME_STATE_LOST) {
      // Reset game
      init_bricks();
      game.score = 0;
      game.paddle_angle = 90.0f;
      game.touch_active = false;
      reset_ball();
      game.state = GAME_STATE_READY;

      // Show all bricks again
      for (int i = 0; i < BRICK_COUNT; i++) {
        if (brick_objs[i]) {
          lv_obj_clear_flag(brick_objs[i], LV_OBJ_FLAG_HIDDEN);
        }
      }

      update_ui();
    }
  }
}

lv_obj_t* screen_game_init(void* ctx) {
  (void)ctx;
  ASSERT(screen == NULL);

  screen = lv_obj_create(NULL);
  if (!screen) {
    return NULL;
  }
  lv_obj_set_style_bg_color(screen, lv_color_black(), 0);

  // Make screen touchable but not scrollable
  lv_obj_add_flag(screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_event_cb(screen, touch_event_cb, LV_EVENT_PRESSED, NULL);
  lv_obj_add_event_cb(screen, touch_event_cb, LV_EVENT_PRESSING, NULL);
  lv_obj_add_event_cb(screen, touch_event_cb, LV_EVENT_RELEASED, NULL);
  lv_obj_add_event_cb(screen, touch_event_cb, LV_EVENT_CLICKED, NULL);

  // Create boundary circle (visual reference)
  boundary_obj = lv_obj_create(screen);
  if (boundary_obj) {
    lv_obj_set_size(boundary_obj, BOUNDARY_RADIUS * 2, BOUNDARY_RADIUS * 2);
    lv_obj_set_pos(boundary_obj, CENTER_X - BOUNDARY_RADIUS, CENTER_Y - BOUNDARY_RADIUS);
    lv_obj_set_style_radius(boundary_obj, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_opa(boundary_obj, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(boundary_obj, 2, 0);
    lv_obj_set_style_border_color(boundary_obj, lv_color_hex(COLOR_BOUNDARY), 0);
    lv_obj_clear_flag(boundary_obj, LV_OBJ_FLAG_CLICKABLE);
  }

  // Create danger zone arc (shows where ball can escape)
  danger_arc = lv_arc_create(screen);
  if (danger_arc) {
    lv_obj_set_size(danger_arc, BOUNDARY_RADIUS * 2 + 20, BOUNDARY_RADIUS * 2 + 20);
    lv_obj_set_pos(danger_arc, CENTER_X - BOUNDARY_RADIUS - 10, CENTER_Y - BOUNDARY_RADIUS - 10);
    lv_arc_set_rotation(danger_arc, 0);
    lv_arc_set_bg_angles(danger_arc, 40, 140);  // Danger zone at bottom
    lv_arc_set_value(danger_arc, 0);            // No indicator needle
    lv_obj_set_style_arc_width(danger_arc, 8, LV_PART_MAIN);
    lv_obj_set_style_arc_color(danger_arc, lv_color_hex(COLOR_DANGER), LV_PART_MAIN);
    lv_obj_set_style_arc_opa(danger_arc, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_arc_width(danger_arc, 0, LV_PART_INDICATOR);      // Hide indicator
    lv_obj_set_style_bg_opa(danger_arc, LV_OPA_TRANSP, LV_PART_KNOB);  // Hide knob
    lv_obj_clear_flag(danger_arc, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_remove_style(danger_arc, NULL, LV_PART_KNOB);
  }

  // Initialize game state
  init_bricks();
  game.score = 0;
  game.paddle_angle = 90.0f;  // Start at bottom
  game.state = GAME_STATE_READY;
  game.touch_active = false;
  reset_ball();

  // Create brick objects
  for (int i = 0; i < BRICK_COUNT; i++) {
    brick_objs[i] = lv_obj_create(screen);
    if (brick_objs[i]) {
      float brick_angle_rad = deg_to_rad(game.bricks[i].angle);
      int32_t bx =
        CENTER_X + (int32_t)(game.bricks[i].radius * cosf(brick_angle_rad)) - BRICK_RADIUS;
      int32_t by =
        CENTER_Y + (int32_t)(game.bricks[i].radius * sinf(brick_angle_rad)) - BRICK_RADIUS;

      lv_obj_set_size(brick_objs[i], BRICK_RADIUS * 2, BRICK_RADIUS * 2);
      lv_obj_set_pos(brick_objs[i], bx, by);
      lv_obj_set_style_radius(brick_objs[i], LV_RADIUS_CIRCLE, 0);
      lv_obj_set_style_border_width(brick_objs[i], 0, 0);

      // Color based on radius (outer=red, middle=orange, inner=green)
      uint32_t color;
      if (game.bricks[i].radius > 135) {
        color = COLOR_BRICK_OUTER;
      } else if (game.bricks[i].radius > 95) {
        color = COLOR_BRICK_MIDDLE;
      } else {
        color = COLOR_BRICK_INNER;
      }
      lv_obj_set_style_bg_color(brick_objs[i], lv_color_hex(color), 0);
      lv_obj_clear_flag(brick_objs[i], LV_OBJ_FLAG_CLICKABLE);
    }
  }

  // Create curved paddle using arc
  paddle_obj = lv_arc_create(screen);
  if (paddle_obj) {
    // Arc widget: the arc is drawn at the outer edge of the widget
    // To get arc at PADDLE_ARC_RADIUS from game center, size = 2 * PADDLE_ARC_RADIUS
    int32_t arc_size = PADDLE_ARC_RADIUS * 2;
    lv_obj_set_size(paddle_obj, arc_size, arc_size);
    lv_obj_set_pos(paddle_obj, CENTER_X - PADDLE_ARC_RADIUS, CENTER_Y - PADDLE_ARC_RADIUS);
    lv_arc_set_rotation(paddle_obj, 0);
    lv_arc_set_bg_angles(paddle_obj, (int16_t)(game.paddle_angle - PADDLE_HALF_WIDTH),
                         (int16_t)(game.paddle_angle + PADDLE_HALF_WIDTH));
    lv_arc_set_value(paddle_obj, 0);
    lv_obj_set_style_arc_width(paddle_obj, PADDLE_THICKNESS, LV_PART_MAIN);
    lv_obj_set_style_arc_color(paddle_obj, lv_color_hex(COLOR_PADDLE), LV_PART_MAIN);
    lv_obj_set_style_arc_rounded(paddle_obj, true, LV_PART_MAIN);
    lv_obj_set_style_arc_width(paddle_obj, 0, LV_PART_INDICATOR);
    lv_obj_set_style_bg_opa(paddle_obj, LV_OPA_TRANSP, LV_PART_KNOB);
    lv_obj_clear_flag(paddle_obj, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_remove_style(paddle_obj, NULL, LV_PART_KNOB);
  }

  // Create ball
  ball_obj = lv_obj_create(screen);
  if (ball_obj) {
    lv_obj_set_size(ball_obj, BALL_RADIUS * 2, BALL_RADIUS * 2);
    lv_obj_set_style_radius(ball_obj, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(ball_obj, lv_color_hex(COLOR_BALL), 0);
    lv_obj_set_style_border_width(ball_obj, 0, 0);
    lv_obj_clear_flag(ball_obj, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_pos(ball_obj, (int32_t)(game.ball_x - BALL_RADIUS),
                   (int32_t)(game.ball_y - BALL_RADIUS));
  }

  // Score label
  score_label = lv_label_create(screen);
  if (score_label) {
    lv_label_set_text(score_label, "0");
    lv_obj_set_style_text_color(score_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(score_label, FONT_SCORE, 0);
    lv_obj_align(score_label, LV_ALIGN_TOP_MID, 0, SCORE_Y_OFFSET);
  }

  // Message label
  message_label = lv_label_create(screen);
  if (message_label) {
    lv_label_set_text(message_label, "Tap to start");
    lv_obj_set_style_text_color(message_label, lv_color_white(), 0);
    lv_obj_set_style_text_font(message_label, FONT_MSG, 0);
    lv_obj_align(message_label, LV_ALIGN_CENTER, 0, 60);
  }

  // Back button
  memset(&back_button, 0, sizeof(top_back_t));
  top_back_create(screen, &back_button, NULL);
  if (back_button.container) {
    lv_obj_move_foreground(back_button.container);
  }

  // Start game timer
  game_timer = lv_timer_create(game_timer_cb, GAME_TICK_MS, NULL);

  ui_set_local_brightness(SCREEN_BRIGHTNESS);

  return screen;
}

void screen_game_destroy(void) {
  if (!screen) {
    return;
  }

  if (game_timer) {
    lv_timer_del(game_timer);
    game_timer = NULL;
  }

  top_back_destroy(&back_button);
  lv_obj_del(screen);

  screen = NULL;
  ball_obj = NULL;
  paddle_obj = NULL;
  boundary_obj = NULL;
  danger_arc = NULL;
  score_label = NULL;
  message_label = NULL;
  for (int i = 0; i < BRICK_COUNT; i++) {
    brick_objs[i] = NULL;
  }
  memset(&game, 0, sizeof(game));
}

void screen_game_update(void* ctx) {
  (void)ctx;
  if (!screen) {
    if (game_timer) {
      lv_timer_del(game_timer);
      game_timer = NULL;
    }
    screen_game_init(ctx);
  }
}
