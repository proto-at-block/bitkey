#pragma once

// Translate bitlog events to Memfault events.
// This code does NOT run on the W1. It's a host-side library.

#include "bitlog.h"

#include <stdint.h>
#include <stdlib.h>

typedef enum {
  TELTRA_OK,
  TELTRA_CHUNK_ERR,
  TELTRA_BAD_ARG,
  TELTRA_TIMESTAMP_ERR,
} teltra_err_t;

#define TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE 36
typedef struct {
  char device_serial[TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE];
  char software_type[TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE];
  char software_version[TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE];
  char hardware_version[TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE];
} teltra_device_info_t;

// Although the time in sMemfaultCurrentTime is a uint64_t, it is
// truncated to a uint32_t during serialization. So, the 0's in the magic
// number below do not show up.
#define TIME_PLACEHOLDER_PATTERN (0x00000000BBBBEEEE)

#define TELTRA_STORAGE_SIZE (8192)

teltra_err_t teltra_translate(teltra_device_info_t* device_info, bitlog_event_t* bitlog_event,
                              uint8_t* serialized_memfault_event, size_t* length);
