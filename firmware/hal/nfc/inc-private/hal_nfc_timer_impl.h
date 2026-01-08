#pragma once

#include "rtos.h"

#define MAX_NFC_TIMERS 10U

void nfc_timer_init(rtos_timer_callback_t callback);

uint32_t nfc_timer_create(uint32_t duration_ms);
bool nfc_timer_expired(uint32_t index);
void nfc_timer_stop(uint32_t index);

/**
 * @brief Stops all active NFC timers, returning them to the pool.
 *
 * ST-RFAL's rfalNfcInitialize() zeroes gNfcDev.discTmr without calling
 * platformTimerDestroy() first, orphaning the timer in our pool. This
 * function cleans up all timers during mode transitions to prevent pool
 * exhaustion. We can't fix ST-RFAL directly as it's third-party code.
 */
void nfc_timer_stop_all(void);
