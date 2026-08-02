/**
 * @file rtos_notification.h
 * @brief POSIX stub for rtos_notification.h - task notifications
 *
 * Replaces lib/rtos/inc/rtos_notification.h for POSIX builds.
 */

#pragma once

#include "rtos_thread.h"

#include <stdbool.h>
#include <stdint.h>

#define RTOS_NOTIFICATION_TIMEOUT_MAX UINT32_MAX

bool rtos_notification_wait_signal(uint32_t timeout_ms);
void rtos_notification_signal(rtos_thread_t* thread);
