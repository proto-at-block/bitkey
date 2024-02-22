#pragma once

#include "FreeRTOS.h"
#include "assert.h"
#include "rtos_thread.h"
#include "task.h"

// Wrapper around FreeRTOS Task Notifications
// https://www.freertos.org/RTOS-task-notifications.html

#define RTOS_NOTIFICATION_TIMEOUT_MAX UINT32_MAX

// Wait on a notification until the timeout.
bool rtos_notification_wait_signal(uint32_t timeout_ms);

// Signal a thread with no additional data.
void rtos_notification_signal(rtos_thread_t* thread);
