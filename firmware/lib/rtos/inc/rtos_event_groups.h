#pragma once

#include "FreeRTOS.h"
#include "event_groups.h"

#include <stdbool.h>

#define RTOS_EVENT_GROUP_TIMEOUT_MAX 0xffffffff

#if configUSE_16_BIT_TICKS == 1
#define RTOS_EVENT_GROUP_BITS 16
#else
#define RTOS_EVENT_GROUP_BITS 24
#endif

typedef struct {
  EventGroupHandle_t handle;
  StaticEventGroup_t buffer;
} rtos_event_group_t;

void rtos_event_group_create(rtos_event_group_t* event_group);
uint32_t rtos_event_group_get_bits(rtos_event_group_t* event_group);
uint32_t rtos_event_group_set_bits(rtos_event_group_t* event_group, const uint32_t bits);
bool rtos_event_group_set_bits_from_isr(rtos_event_group_t* event_group, const uint32_t bits,
                                        bool* wokenp);
uint32_t rtos_event_group_clear_bits(rtos_event_group_t* event_group, const uint32_t bits);
uint32_t rtos_event_group_wait_bits(rtos_event_group_t* event_group, const uint32_t bits,
                                    const bool clear_on_exit, const bool wait_for_all_bits,
                                    uint32_t timeout_ms);
