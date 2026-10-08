#include "bio.h"
#include "bio_flash_storage.h"
#include "bio_impl.h"
#include "bitlog.h"
#include "fff.h"
#include "filesystem.h"
#include "fpc_malloc.h"
#include "mcu_flash.h"
#include "rtos.h"

#include <criterion/criterion.h>

#include <stdlib.h>
#include <string.h>

DEFINE_FFF_GLOBALS;

FAKE_VALUE_FUNC(bool, fs_file_exists, const char*);
FAKE_VALUE_FUNC(int, fs_remove, const char*);
FAKE_VALUE_FUNC(int, fs_open_global, fs_file_t**, const char*, int);
FAKE_VALUE_FUNC(int, fs_close_global, fs_file_t*);
FAKE_VALUE_FUNC(int32_t, fs_file_size, fs_file_t*);
FAKE_VALUE_FUNC(int32_t, fs_file_read, fs_file_t*, void*, uint32_t);
FAKE_VALUE_FUNC(int32_t, fs_file_write, fs_file_t*, const void*, uint32_t);
FAKE_VALUE_FUNC(int, fs_file_truncate, fs_file_t*, int32_t);
FAKE_VALUE_FUNC(bool, export_key, key_handle_t*, key_handle_t*);
FAKE_VOID_FUNC(_bitlog_record_event, uint16_t, uint8_t, void*, void*);
FAKE_VALUE_FUNC(fpc_bep_result_t, fpc_bep_template_get_size, const fpc_bep_template_t*, size_t*);
FAKE_VALUE_FUNC(fpc_bep_result_t, fpc_bep_template_serialize, const fpc_bep_template_t*, uint8_t*,
                size_t);
FAKE_VALUE_FUNC(fpc_bep_result_t, fpc_bep_template_deserialize, fpc_bep_template_t**, uint8_t*,
                size_t, fpc_bep_mem_release_t);
FAKE_VALUE_FUNC(fpc_bep_result_t, fpc_bep_template_delete, fpc_bep_template_t**);
FAKE_VALUE_FUNC(bool, rtos_in_isr);

static uint8_t flash_backing[BIO_FLASH_PARTITION_SIZE];
static bool template_files[TEMPLATE_MAX_COUNT];
static bool label_files[TEMPLATE_MAX_COUNT];
static const char* template_paths[TEMPLATE_MAX_COUNT] = {
  "fpc-template-00.bin", "fpc-template-01.bin", "fpc-template-02.bin"};
static const char* label_paths[TEMPLATE_MAX_COUNT] = {"bio-label-00.bin", "bio-label-01.bin",
                                                      "bio-label-02.bin"};
static const char* remove_failure_path;
static uint32_t erase_calls;
static uint32_t erase_failure_call;

static bool file_exists(const char* path) {
  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    if (strcmp(path, template_paths[id]) == 0) {
      return template_files[id];
    }
    if (strcmp(path, label_paths[id]) == 0) {
      return label_files[id];
    }
  }
  return false;
}

static int remove_file(const char* path) {
  if (remove_failure_path != NULL && strcmp(path, remove_failure_path) == 0) {
    return LFS_ERR_NOSPC;
  }
  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    if (strcmp(path, template_paths[id]) == 0 && template_files[id]) {
      template_files[id] = false;
      return 0;
    }
    if (strcmp(path, label_paths[id]) == 0 && label_files[id]) {
      label_files[id] = false;
      return 0;
    }
  }
  return LFS_ERR_NOENT;
}

mcu_flash_status_t mcu_flash_erase_page(uint32_t* address) {
  erase_calls++;
  if (erase_calls == erase_failure_call) {
    return MCU_FLASH_STATUS_TIMEOUT;
  }
  memset(address, 0xFF, FLASH_PAGE_SIZE);
  return MCU_FLASH_STATUS_OK;
}

mcu_flash_status_t mcu_flash_write_word(uint32_t* address, const void* data, uint32_t len) {
  memcpy(address, data, len);
  return MCU_FLASH_STATUS_OK;
}

void* fpc_malloc(size_t size) {
  return malloc(size);
}

void fpc_free(void* data) {
  free(data);
}

static void setup(void) {
  memset(template_files, 0, sizeof(template_files));
  memset(label_files, 0, sizeof(label_files));
  remove_failure_path = NULL;
  erase_calls = 0;
  erase_failure_call = 0;
  RESET_FAKE(fs_file_exists);
  RESET_FAKE(fs_remove);
  fs_file_exists_fake.custom_fake = file_exists;
  fs_remove_fake.custom_fake = remove_file;
  bio_flash_storage_set_flash(flash_backing, sizeof(flash_backing));
  cr_assert(bio_flash_storage_erase());
  erase_calls = 0;
}

static void enroll_flash_template(void) {
  const uint8_t template_data[] = {1, 2, 3, 4};
  cr_assert(bio_flash_storage_save(template_data, sizeof(template_data)));
  label_files[2] = true;
  cr_assert(bio_fingerprint_index_exists(2));
}

static void interrupt_flash_removal(void) {
  uint8_t template_data[BIO_FLASH_MAX_TEMPLATE_SIZE];
  memset(template_data, 0x5A, sizeof(template_data));
  cr_assert(bio_flash_storage_save(template_data, sizeof(template_data)));
  label_files[2] = true;

  erase_calls = 0;
  erase_failure_call = 2;
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_GENERIC);
  cr_assert_eq(flash_backing[0], 0xFF);
  cr_assert_eq(flash_backing[FLASH_PAGE_SIZE], 0x5A);
}

static void assert_flash_erased(void) {
  for (uint32_t i = 0; i < sizeof(flash_backing); i++) {
    cr_assert_eq(flash_backing[i], 0xFF, "Biometric data remains at byte %u", i);
  }
}

TestSuite(fpc_storage, .init = setup);

Test(fpc_storage, removes_each_filesystem_template_and_label) {
  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    template_files[id] = true;
    label_files[id] = true;
    cr_assert_eq(bio_storage_delete_template(id), BIO_ERR_NONE);
    cr_assert_not(bio_fingerprint_index_exists(id));
    cr_assert_not(label_files[id]);
  }
}

Test(fpc_storage, removes_flash_template_without_affecting_other_slots) {
  template_files[0] = template_files[1] = true;
  label_files[0] = label_files[1] = true;
  enroll_flash_template();

  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_NONE);
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(label_files[2]);
  cr_assert(bio_fingerprint_index_exists(0));
  cr_assert(bio_fingerprint_index_exists(1));
  cr_assert(label_files[0]);
  cr_assert(label_files[1]);
}

Test(fpc_storage, removes_both_flash_and_legacy_filesystem_copies) {
  enroll_flash_template();
  template_files[2] = true;

  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_NONE);
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert_not(bio_flash_storage_exists());
  cr_assert_not(label_files[2]);
}

Test(fpc_storage, reports_legacy_template_removal_failure_and_preserves_label) {
  template_files[2] = label_files[2] = true;
  remove_failure_path = template_paths[2];

  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_GENERIC);
  cr_assert(bio_fingerprint_index_exists(2));
  cr_assert(label_files[2]);
}

Test(fpc_storage, retries_legacy_template_removal_after_storage_recovers) {
  enroll_flash_template();
  template_files[2] = true;
  remove_failure_path = template_paths[2];
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_GENERIC);
  cr_assert(bio_fingerprint_index_exists(2));
  cr_assert(label_files[2]);

  remove_failure_path = NULL;
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_NONE);
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert_not(label_files[2]);
}

Test(fpc_storage, removes_flash_template_with_damaged_payload) {
  enroll_flash_template();
  flash_backing[BIO_FLASH_HEADER_SIZE] ^= 1;
  cr_assert_not(bio_flash_storage_exists());
  cr_assert(bio_fingerprint_index_exists(2));

  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_NONE);
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert_not(label_files[2]);
}

Test(fpc_storage, retries_flash_removal_after_partial_erase) {
  enroll_flash_template();
  erase_calls = 0;
  erase_failure_call = 2;
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_GENERIC);
  cr_assert_not(bio_flash_storage_exists());
  cr_assert(bio_fingerprint_index_exists(2));
  cr_assert(label_files[2]);

  erase_failure_call = 0;
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_NONE);
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert_not(label_files[2]);
}

Test(fpc_storage, retries_flash_removal_after_partial_erase_and_reboot) {
  interrupt_flash_removal();

  // Boot rebuilds the existence cache after the header page has been erased.
  bio_flash_storage_init();
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert(label_files[2]);

  erase_failure_call = 0;
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_NONE);
  assert_flash_erased();
  cr_assert_not(label_files[2]);
}

Test(fpc_storage, cleans_hidden_flash_template_during_boot) {
  template_files[0] = template_files[1] = true;
  label_files[0] = label_files[1] = true;
  interrupt_flash_removal();

  erase_failure_call = 0;
  bio_storage_migrate_to_flash();

  uint32_t count = 0;
  bio_storage_get_template_count(&count);
  cr_assert_eq(count, 2);
  cr_assert(bio_fingerprint_index_exists(0));
  cr_assert(bio_fingerprint_index_exists(1));
  cr_assert_not(bio_fingerprint_index_exists(2));
  assert_flash_erased();
}

Test(fpc_storage, retries_failed_boot_cleanup_on_next_boot) {
  template_files[0] = label_files[0] = true;
  interrupt_flash_removal();

  erase_calls = 0;
  bio_storage_migrate_to_flash();
  cr_assert_eq(erase_calls, 2);
  cr_assert(bio_flash_storage_has_data());
  cr_assert_not(bio_fingerprint_index_exists(2));
  cr_assert(bio_fingerprint_index_exists(0));

  erase_failure_call = 0;
  bio_storage_migrate_to_flash();
  assert_flash_erased();
  cr_assert_not(bio_fingerprint_index_exists(2));
  uint32_t count = 0;
  bio_storage_get_template_count(&count);
  cr_assert_eq(count, 1);
  cr_assert(bio_fingerprint_index_exists(0));
}

Test(fpc_storage, preserves_valid_flash_template_during_boot) {
  enroll_flash_template();
  erase_calls = 0;

  bio_storage_migrate_to_flash();

  cr_assert_eq(erase_calls, 0);
  cr_assert(bio_flash_storage_exists());
  cr_assert(bio_fingerprint_index_exists(2));
  cr_assert(label_files[2]);
}

Test(fpc_storage, skips_boot_cleanup_for_empty_partition) {
  bio_storage_migrate_to_flash();

  cr_assert_eq(erase_calls, 0);
  cr_assert_not(bio_flash_storage_has_data());
  cr_assert_not(bio_fingerprint_index_exists(2));
}

Test(fpc_storage, preserves_legacy_template_when_boot_cleanup_fails) {
  template_files[2] = true;
  interrupt_flash_removal();
  erase_calls = 0;
  RESET_FAKE(fs_open_global);

  bio_storage_migrate_to_flash();

  cr_assert_eq(erase_calls, 2);
  cr_assert_eq(fs_open_global_fake.call_count, 0);
  cr_assert(bio_flash_storage_has_data());
  cr_assert(bio_fingerprint_index_exists(2));
  cr_assert(template_files[2]);
  cr_assert(label_files[2]);
}

Test(fpc_storage, reports_filesystem_removal_failure_for_other_slots) {
  template_files[1] = label_files[1] = true;
  remove_failure_path = template_paths[1];

  cr_assert_eq(bio_storage_delete_template(1), BIO_ERR_GENERIC);
  cr_assert(bio_fingerprint_index_exists(1));
  cr_assert(label_files[1]);
}

Test(fpc_storage, rejects_missing_template) {
  cr_assert_eq(bio_storage_delete_template(2), BIO_ERR_TEMPLATE_DOESNT_EXIST);
}

Test(fpc_storage, rejects_invalid_slot_without_removing_templates) {
  enroll_flash_template();

  cr_assert_eq(bio_storage_delete_template(TEMPLATE_MAX_COUNT), BIO_ERR_TEMPLATE_INVALID);
  cr_assert(bio_fingerprint_index_exists(2));
  cr_assert(label_files[2]);
}
