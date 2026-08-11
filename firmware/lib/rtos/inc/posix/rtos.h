/**
 * @file rtos.h
 * @brief POSIX stub for rtos.h - main RTOS include file
 *
 * Replaces lib/rtos/inc/rtos.h for POSIX builds, providing compatible
 * implementations using pthreads instead of FreeRTOS.
 */

#pragma once

#include "FreeRTOS.h"

/* Tick/millisecond conversion (1:1 on POSIX since portTICK_RATE_MS = 1) */
#define TICKS2MS(t) ((t)*portTICK_RATE_MS)
#define MS2TICKS(m) ((m) / portTICK_RATE_MS)

/* Memory allocation */
#define rtos_malloc pvPortMalloc
#define rtos_free   vPortFree

/* RTOS primitives */
#include "rtos_event_groups.h"
#include "rtos_mpu.h"
#include "rtos_mutex.h"
#include "rtos_notification.h"
#include "rtos_queue.h"
#include "rtos_semaphore.h"
#include "rtos_thread.h"
#include "rtos_timer.h"
