/**
 * @file nfc_dots_animation.h
 * @brief NFC dots widget for scan screen
 *
 * Displays NFC wave pattern dots centered on the screen.
 */

#pragma once
#include "lvgl.h"

#include <stdbool.h>
#include <stdint.h>

#define NFC_DOTS_COUNT      88
#define NFC_DOT_SIZE_ACTIVE 10

typedef struct {
  lv_obj_t* parent;
  lv_obj_t* container;
  lv_obj_t* dots[NFC_DOTS_COUNT];
  lv_timer_t* update_timer;
  bool is_initialized;
  bool is_animating;
} nfc_dots_animation_t;

lv_obj_t* nfc_dots_animation_create(lv_obj_t* parent, nfc_dots_animation_t* animation);
void nfc_dots_animation_start(nfc_dots_animation_t* animation);
void nfc_dots_animation_stop(nfc_dots_animation_t* animation);
void nfc_dots_animation_destroy(nfc_dots_animation_t* animation);
