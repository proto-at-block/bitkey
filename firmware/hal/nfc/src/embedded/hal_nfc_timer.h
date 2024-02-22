#pragma once

#include "rtos.h"

#define MAX_NFC_TIMERS 10U

void nfc_timer_init(rtos_timer_callback_t callback);

uint32_t nfc_timer_create(uint32_t duration_ms);
bool nfc_timer_expired(uint32_t index);
void nfc_timer_stop(uint32_t index);
