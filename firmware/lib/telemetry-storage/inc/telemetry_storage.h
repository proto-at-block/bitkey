#pragma once

#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

#define TELEMETRY_EVENT_STORAGE_SIZE (1024)

#define TELEMETRY_LOG_STORAGE_SIZE (96)
#define TELEMETRY_COREDUMP_SIZE    (588)

#define TELEMETRY_CHUNK_SIZE (160)

#define EVENT_STORAGE_VERSION (1)

typedef bool (*get_telemetry_chunk_t)(void*, size_t*);
typedef void (*set_drain_all_t)(void);
typedef void (*set_drain_only_events_t)(void);

typedef struct {
  get_telemetry_chunk_t get_chunk;
  set_drain_all_t set_drain_all;
  set_drain_only_events_t set_drain_only_events;
} telemetry_api_t;

void telemetry_init(telemetry_api_t api);

uint8_t* telemetry_event_storage_get(void);
uint8_t* telemetry_log_storage_get(void);

// Coredump API

// Save the active coredump into flash. This function
// is called after a crash has happened, right before the
// system will be reset.
bool telemetry_coredump_save(void);
bool telemetry_coredump_read_fragment(uint32_t offset, fwpb_coredump_fragment* frag);
uint32_t telemetry_coredump_count(void);
