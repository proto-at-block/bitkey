#include "bitops.h"
#include "memfault/core/data_packetizer.h"
#include "memfault/core/event_storage.h"
#include "memfault/core/trace_event.h"
#include "memfault/util/crc16_ccitt.h"
#include "telemetry_translator.h"
#include "time.h"

#include <string.h>

#define FLASH_BASE 0x08000000

static uint8_t telem_storage[TELTRA_STORAGE_SIZE] = {0};

static void update_device_info(teltra_device_info_t* device_info) {
  extern teltra_device_info_t g_device_info;
  strncpy(g_device_info.device_serial, device_info->device_serial,
          TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE);
  strncpy(&g_device_info.software_type[0], device_info->software_type,
          TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE);
  strncpy(&g_device_info.hardware_version[0], device_info->hardware_version,
          TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE);
  strncpy(&g_device_info.software_version[0], device_info->software_version,
          TELTRA_DEVICE_INFO_PLACEHOLDER_SIZE);
}

static void teltra_lazy_init(void) {
  static bool initialized = false;
  if (initialized)
    return;

  const sMemfaultEventStorageImpl* evt_storage =
    memfault_events_storage_boot(telem_storage, sizeof(telem_storage));

  memfault_trace_event_boot(evt_storage);

  initialized = true;
}

static uint8_t* find_pattern(uint8_t* buffer, size_t len, uint32_t pattern) {
  for (size_t i = 0; i < len - sizeof(uint32_t); i++) {
    uint32_t value = htonl(*(uint32_t*)(&buffer[i]));
    if (value == pattern) {
      return &buffer[i];
    }
  }
  return NULL;
}

teltra_err_t teltra_translate(teltra_device_info_t* device_info, bitlog_event_t* bitlog_event,
                              uint8_t* serialized_memfault_event, size_t* length) {
  teltra_lazy_init();

  if (*length < sizeof(bitlog_event_t)) {
    return TELTRA_BAD_ARG;
  }

  update_device_info(device_info);

  void* pc = (void*)(uintptr_t)(bitlog_event->pc.v + FLASH_BASE);
  void* lr = (void*)(uintptr_t)(bitlog_event->lr.v + FLASH_BASE);

  memfault_trace_event_with_status_capture(bitlog_event->event, pc, lr, bitlog_event->status);

  if (!memfault_packetizer_get_chunk(serialized_memfault_event, length)) {
    return TELTRA_CHUNK_ERR;
  }

  // Resolve timestamp.
  uint8_t* timestamp = find_pattern(serialized_memfault_event, *length, TIME_PLACEHOLDER_PATTERN);
  if (!timestamp) {
    return TELTRA_TIMESTAMP_ERR;
  }
  uint32_t t = htonl((uint32_t)time(NULL) + bitlog_event->timestamp_delta);
  memcpy(timestamp, &t, sizeof(t));

  // Fix CRC, since we just updated the timestamp.
  uint16_t crc =
    memfault_crc16_ccitt_compute(2048, serialized_memfault_event, *length - sizeof(uint16_t));
  memcpy(&serialized_memfault_event[*length - sizeof(uint16_t)], &crc, sizeof(uint16_t));

  return TELTRA_OK;
}
