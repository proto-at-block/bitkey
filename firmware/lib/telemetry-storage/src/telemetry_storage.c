#include "telemetry_storage.h"

#include "assert.h"
#include "bitops.h"
#include "filesystem.h"
#include "log.h"

static uint8_t SHARED_TASK_DATA event_storage[TELEMETRY_EVENT_STORAGE_SIZE];
static uint8_t SHARED_TASK_DATA log_storage[TELEMETRY_LOG_STORAGE_SIZE];
extern uint8_t SHARED_TASK_DATA active_coredump[TELEMETRY_COREDUMP_SIZE];

#define COREDUMPS_PATH        "coredumps.bin"
#define COREDUMP_MAX_FILESIZE (TELEMETRY_COREDUMP_SIZE * 6)

static struct { telemetry_api_t api; } telemetry_priv = {0};
static bool wrapping_seek(fs_file_t* file, int32_t max_filesize);
static void maybe_delete_old_coredumps(void);

void telemetry_init(telemetry_api_t api) {
  telemetry_priv.api = api;
}

uint8_t* telemetry_log_storage_get(void) {
  return log_storage;
}

uint8_t* telemetry_event_storage_get(void) {
  return event_storage;
}

bool telemetry_coredump_save(void) {
  bool ret = false;

  fs_file_t* file = NULL;
  if (fs_open_global(&file, COREDUMPS_PATH, FS_O_CREAT | FS_O_RDWR) != 0) {
    goto out;
  }

  if (!wrapping_seek(file, COREDUMP_MAX_FILESIZE)) {
    goto out;
  }

  ret = (fs_file_write(file, active_coredump, sizeof(active_coredump)) > 0);

out:
  fs_close_global(file);
  return ret;
}

bool telemetry_coredump_read_fragment(uint32_t offset, fwpb_coredump_fragment* frag) {
  ASSERT(frag);

  maybe_delete_old_coredumps();

  bool ret = false;
  frag->complete = false;

  fs_file_t* file = NULL;
  if (fs_open_global(&file, COREDUMPS_PATH, FS_O_RDWR) != 0) {
    LOGE("Failed to open coredump file");
    goto out;
  }

  if (fs_file_size(file) < TELEMETRY_COREDUMP_SIZE) {
    // Don't try to seek if there is no coredump.
    LOGE("No coredump to read.");
    goto out;
  }

  if (offset > TELEMETRY_COREDUMP_SIZE) {
    LOGE("Offset too big");
    goto out;
  }

  // Seek to last coredump in the flash-backed queue.
  // Note that this may not be the most recent coredump, in the case that
  // we hadn't drained coredumps before we wrapped back around.

  if (fs_file_seek(file, -TELEMETRY_COREDUMP_SIZE + offset, FS_SEEK_END) < 0) {
    LOGE("Failed to seek.");
    goto out;
  }

  int32_t bytes_read = fs_file_read(file, frag->data.bytes, sizeof(frag->data.bytes));
  if (bytes_read < 0) {
    LOGE("Failed to read coredump file");
    goto out;
  }

  frag->data.size = bytes_read;
  frag->offset = offset + bytes_read;

  LOGD("coredump offset: %ld", frag->offset);

  if (frag->offset >= TELEMETRY_COREDUMP_SIZE) {
    LOGI("Coredump processed.");
    // Done processing a coredump. Truncate to remove it from flash.
    if (fs_file_truncate(file, fs_file_size(file) - TELEMETRY_COREDUMP_SIZE) < 0) {
      LOGE("Failed to truncate.");
      goto out;
    }

    frag->complete = true;
  }

  ret = true;

out:
  frag->coredumps_remaining = (fs_file_size(file) / TELEMETRY_COREDUMP_SIZE);
  fs_close_global(file);
  return ret;
}

static bool wrapping_seek(fs_file_t* file, int32_t max_filesize) {
  int32_t size = fs_file_size(file);
  if (size < 0) {
    return false;
  }

  if (size >= max_filesize) {
    // Seek to beginning, start overwriting old coredumps.
    if (fs_file_seek(file, 0, FS_SEEK_SET) < 0) {
      return false;
    }
  } else {
    // Seek to end.
    if (fs_file_seek(file, 0, FS_SEEK_END) < 0) {
      return false;
    }
  }
  return true;
}

uint32_t telemetry_coredump_count(void) {
  fs_file_t* file = NULL;
  if (fs_open_global(&file, COREDUMPS_PATH, FS_O_RDWR) != 0) {
    return 0;
  }
  uint32_t count = fs_file_size(file) / TELEMETRY_COREDUMP_SIZE;
  fs_close_global(file);
  return count;
}

static void maybe_delete_old_coredumps(void) {
  static bool ran_once = false;

  if (ran_once)
    return;

  fs_file_t* file = NULL;
  if (fs_open_global(&file, COREDUMPS_PATH, FS_O_RDWR) != 0) {
    goto out;
  }

  // Check for presence of old (4K size) coredumps.

  // https://docs.memfault.com/docs/mcu/coredumps/#memfault-coredump-format
  uint8_t header[12];
  int32_t bytes_read = fs_file_read(file, header, sizeof(header));
  if (bytes_read < 0) {
    LOGE("Failed to read coredump file");
    goto out;
  }

  uint32_t size = *(uint32_t*)&header[8];
  if (size == 4096) {
    LOGI("Deleting old coredumps");
    fs_file_truncate(file, 0);
  }

out:
  ran_once = true;
  fs_close_global(file);
}
