#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef FLASH_PAGE_SIZE
#define FLASH_PAGE_SIZE (0x00002000UL)  // 8 KB
#endif

#include "bio_flash_storage.h"
#include "mcu_flash.h"

static uint8_t flash_bio_backing[BIO_FLASH_PARTITION_SIZE];

// Configurable return values for mcu_flash mocks
static mcu_flash_status_t mock_erase_status;
static mcu_flash_status_t mock_write_status;
static uint32_t mock_erase_call_count;
static uint32_t mock_write_call_count;

// Mock mcu_flash functions: operate on flash_bio_backing in memory
mcu_flash_status_t mcu_flash_erase_page(uint32_t* address) {
  mock_erase_call_count++;
  if (mock_erase_status != MCU_FLASH_STATUS_OK) {
    return mock_erase_status;
  }
  // Simulate erase: set page to 0xFF
  memset(address, 0xFF, FLASH_PAGE_SIZE);
  return MCU_FLASH_STATUS_OK;
}

mcu_flash_status_t mcu_flash_write_word(uint32_t* address, void const* data, uint32_t len) {
  mock_write_call_count++;
  if (mock_write_status != MCU_FLASH_STATUS_OK) {
    return mock_write_status;
  }
  // Simulate write
  memcpy(address, data, len);
  return MCU_FLASH_STATUS_OK;
}

void mcu_flash_init(void) {}

// Stubs for RTOS used by SHARED_TASK_BSS attribute
bool rtos_mutex_lock(void* t) {
  (void)t;
  return true;
}
bool rtos_mutex_unlock(void* t) {
  (void)t;
  return true;
}

static void setup(void) {
  memset(flash_bio_backing, 0xFF, sizeof(flash_bio_backing));
  bio_flash_storage_set_flash(flash_bio_backing, BIO_FLASH_PARTITION_SIZE);
  mock_erase_status = MCU_FLASH_STATUS_OK;
  mock_write_status = MCU_FLASH_STATUS_OK;
  mock_erase_call_count = 0;
  mock_write_call_count = 0;
}

TestSuite(bio_flash_storage, .init = setup);

// ============================================================================
// Migration test support
//
// bio_storage_migrate_to_flash() lives in fpc_storage.c which has heavy
// dependencies (AES, key management, etc.). Rather than pulling in that
// entire file, we copy the migration function here and test it against
// the real bio_flash_storage implementation with mocked filesystem and
// allocator functions.
// ============================================================================

// Constants mirrored from fpc_storage.c
#define BIO_FLASH_TEMPLATE_ID (2)
#define TEMPLATE_PATH_LEN     (sizeof("fpc-template-.bin") + 2)
#define TEMPLATE_PATH_FORMAT  ("fpc-template-%02d.bin")

// Minimal fs_file_t for mocking (real one wraps lfs_file_t)
typedef struct {
  uint8_t dummy;
} fs_file_t;

#define FS_O_RDONLY 1

// --- Filesystem mock state ---
static bool mock_fs_file_exists_val;
static int mock_fs_open_result;
static int32_t mock_fs_file_size_val;
static const uint8_t* mock_fs_file_data;
static int32_t mock_fs_file_read_result;  // -1 = use size, otherwise override
static bool mock_fs_remove_called;
static char mock_fs_remove_path[64];
static fs_file_t mock_fs_file;
static bool mock_malloc_fail;

// --- Filesystem mocks ---
bool fs_file_exists(const char* path) {
  (void)path;
  return mock_fs_file_exists_val;
}

int fs_open_global(fs_file_t** file, const char* path, int flags) {
  (void)path;
  (void)flags;
  *file = &mock_fs_file;
  return mock_fs_open_result;
}

int fs_close_global(fs_file_t* file) {
  (void)file;
  return 0;
}

int32_t fs_file_size(fs_file_t* file) {
  (void)file;
  return mock_fs_file_size_val;
}

int32_t fs_file_read(fs_file_t* file, void* buffer, uint32_t size) {
  (void)file;
  if (mock_fs_file_read_result >= 0) {
    // Return override value (for short read testing)
    uint32_t to_copy = (uint32_t)mock_fs_file_read_result;
    if (mock_fs_file_data && to_copy > 0) {
      memcpy(buffer, mock_fs_file_data, to_copy);
    }
    return mock_fs_file_read_result;
  }
  // Normal: read full size
  if (mock_fs_file_data) {
    memcpy(buffer, mock_fs_file_data, size);
  }
  return (int32_t)size;
}

int fs_remove(const char* path) {
  mock_fs_remove_called = true;
  strncpy(mock_fs_remove_path, path, sizeof(mock_fs_remove_path) - 1);
  return 0;
}

// --- fpc_malloc/fpc_free mocks ---
void* fpc_malloc(size_t size) {
  if (mock_malloc_fail) {
    return NULL;
  }
  return malloc(size);
}

void fpc_free(void* data) {
  free(data);
}

// --- Migration function copied from fpc_storage.c ---
// Keep in sync with hal/biometrics/src/fpc_storage.c:bio_storage_migrate_to_flash()
static void bio_storage_migrate_to_flash(void) {
  bio_flash_storage_init();

  char filename[TEMPLATE_PATH_LEN] = {0};
  snprintf(filename, sizeof(filename), TEMPLATE_PATH_FORMAT, BIO_FLASH_TEMPLATE_ID);

  if (!fs_file_exists(filename)) {
    return;
  }

  if (bio_flash_storage_template_exists()) {
    fs_remove(filename);
    return;
  }

  fs_file_t* file = NULL;
  if (fs_open_global(&file, filename, FS_O_RDONLY) != 0) {
    return;
  }

  int32_t size = fs_file_size(file);
  if (size <= 0) {
    fs_close_global(file);
    return;
  }

  uint8_t* buf = fpc_malloc((uint32_t)size);
  if (buf == NULL) {
    fs_close_global(file);
    return;
  }

  int32_t bytes_read = fs_file_read(file, buf, (uint32_t)size);
  fs_close_global(file);

  if (bytes_read != size) {
    fpc_free(buf);
    return;
  }

  if (!bio_flash_storage_save(buf, (uint32_t)size)) {
    fpc_free(buf);
    return;
  }

  fpc_free(buf);
  fs_remove(filename);
}

static void migration_setup(void) {
  setup();
  mock_fs_file_exists_val = false;
  mock_fs_open_result = 0;
  mock_fs_file_size_val = 0;
  mock_fs_file_data = NULL;
  mock_fs_file_read_result = -1;  // default: read succeeds with full size
  mock_fs_remove_called = false;
  memset(mock_fs_remove_path, 0, sizeof(mock_fs_remove_path));
  mock_malloc_fail = false;
}

TestSuite(migration, .init = migration_setup);

// --- Basic save/read/exists tests ---

Test(bio_flash_storage, save_and_read) {
  uint8_t data[128];
  for (uint32_t i = 0; i < sizeof(data); i++) {
    data[i] = (uint8_t)(i & 0xFF);
  }

  cr_assert(bio_flash_storage_save(data, sizeof(data)));
  cr_assert(bio_flash_storage_exists());

  uint8_t readback[128] = {0};
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, sizeof(data));
  cr_assert_eq(memcmp(data, readback, sizeof(data)), 0);
}

Test(bio_flash_storage, exists_false_when_empty) {
  cr_assert_not(bio_flash_storage_exists());
}

Test(bio_flash_storage, save_then_erase) {
  uint8_t data[] = {1, 2, 3, 4};
  cr_assert(bio_flash_storage_save(data, sizeof(data)));
  cr_assert(bio_flash_storage_exists());

  cr_assert(bio_flash_storage_erase());
  cr_assert_not(bio_flash_storage_exists());
}

Test(bio_flash_storage, get_size) {
  uint8_t data[200];
  memset(data, 0xAB, sizeof(data));
  cr_assert(bio_flash_storage_save(data, sizeof(data)));

  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_get_size(&size_out));
  cr_assert_eq(size_out, sizeof(data));
}

Test(bio_flash_storage, max_size) {
  uint32_t expected = BIO_FLASH_PARTITION_SIZE - BIO_FLASH_HEADER_SIZE;
  cr_assert_eq(bio_flash_storage_max_size(), expected);
}

// --- Unaligned size (not multiple of 4) ---

Test(bio_flash_storage, save_unaligned_size) {
  uint8_t data[13];  // Not 4-byte aligned
  memset(data, 0xCC, sizeof(data));

  cr_assert(bio_flash_storage_save(data, sizeof(data)));

  uint8_t readback[13] = {0};
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, sizeof(data));
  cr_assert_eq(memcmp(data, readback, sizeof(data)), 0);
}

// --- CRC integrity ---

Test(bio_flash_storage, read_detects_corrupted_data) {
  uint8_t data[64];
  memset(data, 0xAA, sizeof(data));
  cr_assert(bio_flash_storage_save(data, sizeof(data)));

  // Corrupt one byte of stored template data (after the header)
  flash_bio_backing[BIO_FLASH_HEADER_SIZE + 10] ^= 0xFF;

  uint8_t readback[64] = {0};
  uint32_t size_out = 0;
  cr_assert_not(bio_flash_storage_read(readback, &size_out));
}

// --- Error paths ---

Test(bio_flash_storage, save_rejects_null_data) {
  cr_assert_not(bio_flash_storage_save(NULL, 10));
}

Test(bio_flash_storage, save_rejects_zero_size) {
  uint8_t data[] = {1};
  cr_assert_not(bio_flash_storage_save(data, 0));
}

Test(bio_flash_storage, save_rejects_oversized) {
  uint8_t data[1];
  cr_assert_not(bio_flash_storage_save(data, BIO_FLASH_PARTITION_SIZE));  // Way too large
}

Test(bio_flash_storage, save_fails_on_erase_error) {
  mock_erase_status = MCU_FLASH_STATUS_TIMEOUT;
  uint8_t data[] = {1, 2, 3, 4};
  cr_assert_not(bio_flash_storage_save(data, sizeof(data)));
}

Test(bio_flash_storage, save_fails_on_write_error) {
  mock_write_status = MCU_FLASH_STATUS_PROG_ERROR;
  uint8_t data[] = {1, 2, 3, 4};
  cr_assert_not(bio_flash_storage_save(data, sizeof(data)));
}

// --- Init caches existence ---

Test(bio_flash_storage, init_caches_existence) {
  // Before save, cached value should be false
  bio_flash_storage_init();
  cr_assert_not(bio_flash_storage_template_exists());

  // Save a template
  uint8_t data[] = {1, 2, 3, 4};
  cr_assert(bio_flash_storage_save(data, sizeof(data)));

  // Re-init should pick up the template
  bio_flash_storage_init();
  cr_assert(bio_flash_storage_template_exists());

  // Erase and re-init
  cr_assert(bio_flash_storage_erase());
  bio_flash_storage_init();
  cr_assert_not(bio_flash_storage_template_exists());
}

// --- Check capacity ---

Test(bio_flash_storage, check_capacity_matches) {
  cr_assert(bio_flash_storage_check_capacity(BIO_FLASH_MAX_TEMPLATE_SIZE));
}

Test(bio_flash_storage, check_capacity_mismatch) {
  cr_assert_not(bio_flash_storage_check_capacity(BIO_FLASH_MAX_TEMPLATE_SIZE + 1));
  cr_assert_not(bio_flash_storage_check_capacity(BIO_FLASH_MAX_TEMPLATE_SIZE - 1));
}

// --- Large template (near max) ---

Test(bio_flash_storage, save_max_size_template) {
  uint32_t max = bio_flash_storage_max_size();
  uint8_t* data = malloc(max);
  cr_assert_not_null(data);
  memset(data, 0x55, max);

  cr_assert(bio_flash_storage_save(data, max));
  cr_assert(bio_flash_storage_exists());

  uint8_t* readback = malloc(max);
  cr_assert_not_null(readback);
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, max);
  cr_assert_eq(memcmp(data, readback, max), 0);

  free(data);
  free(readback);
}

// --- Header version is written ---

Test(bio_flash_storage, header_version_written) {
  uint8_t data[] = {1, 2, 3, 4};
  cr_assert(bio_flash_storage_save(data, sizeof(data)));

  // Read version field directly from fake flash (offset 4 in header)
  uint32_t version;
  memcpy(&version, &flash_bio_backing[4], sizeof(version));
  cr_assert_eq(version, BIO_FLASH_HEADER_VERSION);
}

// ============================================================================
// Migration tests
// ============================================================================

// --- Happy path: no old file, nothing to migrate ---

Test(migration, no_old_file_is_noop) {
  mock_fs_file_exists_val = false;

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Happy path: old file migrated to flash ---

Test(migration, migrates_template_to_flash) {
  uint8_t template_data[256];
  for (uint32_t i = 0; i < sizeof(template_data); i++) {
    template_data[i] = (uint8_t)(i & 0xFF);
  }

  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = (int32_t)sizeof(template_data);
  mock_fs_file_data = template_data;

  bio_storage_migrate_to_flash();

  // Template should now be in flash
  cr_assert(bio_flash_storage_exists());

  // Verify data integrity via read-back
  uint8_t readback[256] = {0};
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, sizeof(template_data));
  cr_assert_eq(memcmp(template_data, readback, sizeof(template_data)), 0);

  // Old file should have been removed
  cr_assert(mock_fs_remove_called);
}

// --- Happy path: already migrated, just clean up old file ---

Test(migration, already_migrated_removes_old_file) {
  // Pre-populate flash with a template
  uint8_t data[] = {0xAA, 0xBB, 0xCC, 0xDD};
  cr_assert(bio_flash_storage_save(data, sizeof(data)));

  // Old file still exists on filesystem
  mock_fs_file_exists_val = true;

  bio_storage_migrate_to_flash();

  // Old file should be removed
  cr_assert(mock_fs_remove_called);

  // Flash data should be unchanged (original data, not re-migrated)
  uint8_t readback[4] = {0};
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, sizeof(data));
  cr_assert_eq(memcmp(data, readback, sizeof(data)), 0);
}

// --- Failure: fs_open_global fails ---

Test(migration, open_fail_leaves_flash_empty) {
  mock_fs_file_exists_val = true;
  mock_fs_open_result = -1;

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Failure: fs_file_size returns 0 ---

Test(migration, zero_size_leaves_flash_empty) {
  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = 0;

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Failure: fs_file_size returns negative ---

Test(migration, negative_size_leaves_flash_empty) {
  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = -1;

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Failure: fpc_malloc fails ---

Test(migration, malloc_fail_leaves_flash_empty) {
  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = 256;
  mock_malloc_fail = true;

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Failure: short read ---

Test(migration, short_read_leaves_flash_empty) {
  uint8_t template_data[256];
  memset(template_data, 0x42, sizeof(template_data));

  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = (int32_t)sizeof(template_data);
  mock_fs_file_data = template_data;
  mock_fs_file_read_result = 100;  // Only 100 of 256 bytes

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Failure: flash write fails ---

Test(migration, flash_write_fail_leaves_flash_empty) {
  uint8_t template_data[64];
  memset(template_data, 0x55, sizeof(template_data));

  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = (int32_t)sizeof(template_data);
  mock_fs_file_data = template_data;

  // Make the flash erase fail so bio_flash_storage_save returns false
  mock_erase_status = MCU_FLASH_STATUS_TIMEOUT;

  bio_storage_migrate_to_flash();

  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(mock_fs_remove_called);
}

// --- Idempotency: calling migrate twice ---

Test(migration, idempotent_double_call) {
  uint8_t template_data[128];
  memset(template_data, 0xAB, sizeof(template_data));

  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = (int32_t)sizeof(template_data);
  mock_fs_file_data = template_data;

  // First call: migrates and removes old file
  bio_storage_migrate_to_flash();
  cr_assert(bio_flash_storage_exists());
  cr_assert(mock_fs_remove_called);

  // Reset remove tracking; old file no longer exists
  mock_fs_remove_called = false;
  mock_fs_file_exists_val = false;

  // Second call: no-op since file is gone
  bio_storage_migrate_to_flash();
  cr_assert_not(mock_fs_remove_called);

  // Flash data intact
  uint8_t readback[128] = {0};
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, sizeof(template_data));
  cr_assert_eq(memcmp(template_data, readback, sizeof(template_data)), 0);
}

// --- Interrupted migration: old file still present, flash already written ---

Test(migration, interrupted_migration_cleans_up) {
  uint8_t original[64];
  memset(original, 0xDE, sizeof(original));

  // Simulate: flash already has the template (previous migration wrote it)
  cr_assert(bio_flash_storage_save(original, sizeof(original)));

  // But old file wasn't removed (power loss after flash write, before fs_remove)
  mock_fs_file_exists_val = true;

  bio_storage_migrate_to_flash();

  // Should just remove old file, not re-migrate
  cr_assert(mock_fs_remove_called);

  // Flash should still have original data
  uint8_t readback[64] = {0};
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, sizeof(original));
  cr_assert_eq(memcmp(original, readback, sizeof(original)), 0);
}

// --- Large template: near max size ---

Test(migration, large_template) {
  uint32_t max = bio_flash_storage_max_size();
  uint8_t* template_data = malloc(max);
  cr_assert_not_null(template_data);
  for (uint32_t i = 0; i < max; i++) {
    template_data[i] = (uint8_t)(i & 0xFF);
  }

  mock_fs_file_exists_val = true;
  mock_fs_file_size_val = (int32_t)max;
  mock_fs_file_data = template_data;

  bio_storage_migrate_to_flash();

  cr_assert(bio_flash_storage_exists());
  cr_assert(mock_fs_remove_called);

  uint8_t* readback = malloc(max);
  cr_assert_not_null(readback);
  uint32_t size_out = 0;
  cr_assert(bio_flash_storage_read(readback, &size_out));
  cr_assert_eq(size_out, max);
  cr_assert_eq(memcmp(template_data, readback, max), 0);

  free(template_data);
  free(readback);
}
