#pragma once

#include "FreeRTOS.h"

#define TICKS2MS(t) ((t) * (portTICK_RATE_MS))
#define MS2TICKS(m) ((m) / (portTICK_RATE_MS))

#define rtos_malloc pvPortMalloc
#define rtos_free   vPortFree

#include "rtos_event_groups.h"
#include "rtos_mpu.h"
#include "rtos_mutex.h"
#include "rtos_notification.h"
#include "rtos_queue.h"
#include "rtos_semaphore.h"
#include "rtos_thread.h"
#include "rtos_timer.h"
