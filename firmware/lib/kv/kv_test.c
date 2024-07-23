#include "bd/lfs_emubd.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "filesystem.h"
#include "kv.h"
#include "rtos.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

extern void kv_print(void);

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_take(rtos_mutex_t* UNUSED(a), uint32_t UNUSED(b)) {
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) {
  return true;
}
bool rtos_mutex_lock_from_isr(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_unlock_from_isr(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_in_isr(void) {
  return false;
}
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) {
  return true;
}

typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*, const uint32_t,
                const bool, const bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, const uint32_t);

static lfs_t lfs;
#define FS_BLOCK_CYCLES   (500)          /* Wear leveling cycles */
#define FS_LOOKAHEAD_SIZE (128)          /* Must be a multiple of 8 */
#define FLASH_PAGE_SIZE   (0x00002000UL) /* Flash Memory page size */
#define FS_BLOCKS         16u            /* Number of flash blocks */

static uint8_t lfs_read_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_prog_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_lookahead_buf[FS_LOOKAHEAD_SIZE];
const struct lfs_emubd_config emubd_cfg = {
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .erase_size = FLASH_PAGE_SIZE,
  .erase_count = FS_BLOCK_COUNT,
  .erase_value = -1,
};

static lfs_emubd_t emubd = {0};
rtos_thread_mpu_t _fs_mount_task_regions;

const struct lfs_config cfg = {
  // block device operations
  .read = lfs_emubd_read,
  .prog = lfs_emubd_prog,
  .erase = lfs_emubd_erase,
  .sync = lfs_emubd_sync,

  // block device configuration
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .block_size = FLASH_PAGE_SIZE,
  .block_count = FS_BLOCK_COUNT,
  .cache_size = FLASH_PAGE_SIZE,
  .lookahead_size = FS_LOOKAHEAD_SIZE,
  .block_cycles = FS_BLOCK_CYCLES,

  .read_buffer = lfs_read_buf,
  .prog_buffer = lfs_prog_buf,
  .lookahead_buffer = lfs_lookahead_buf,

  .context = &emubd,
};

static void init_lfs() {
  cr_assert(lfs_emubd_create(&cfg, &emubd_cfg) == 0);
  cr_assert(lfs_format(&lfs, &cfg) == 0);
  cr_assert(lfs_mount(&lfs, &cfg) == 0);
  set_lfs(&lfs);
}

bool nop(void) {
  return true;
}

void init() {
  init_lfs();
  cr_assert_eq(kv_init((kv_api_t){.lock = &nop, .unlock = &nop}), KV_ERR_NONE);
}

void fini() {
  lfs_emubd_destroy(&cfg);
}

TestSuite(kv_test, .init = init, .fini = fini);

Test(kv_test, set_get_single_key_value) {
  const char* key = "key1";
  const char* value = "value1";
  uint8_t value_len = strlen(value);

  kv_result_t result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set key-value pair");

  char retrieved_value[KV_MAX_VALUE_LEN];
  uint8_t retrieved_value_len = sizeof(retrieved_value);
  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get key-value pair");
  cr_assert_eq(retrieved_value_len, value_len, "Retrieved value length mismatch");
  cr_assert(memcmp(retrieved_value, value, value_len) == 0, "Retrieved value mismatch");
}

Test(kv_test, get_non_existent_key) {
  const char* key = "nope";
  char value[KV_MAX_VALUE_LEN];
  uint8_t value_len = sizeof(value);

  kv_result_t result = kv_get(key, value, &value_len);
  cr_assert(result == KV_ERR_NOT_FOUND, "Expected KV_ERR_NOT_FOUND for non-existent key");
}

Test(kv_test, set_update_existing_key) {
  const char* key = "key1";
  const char* initial_value = "initial";
  uint8_t initial_value_len = strlen(initial_value);
  const char* updated_value = "updated";
  uint8_t updated_value_len = strlen(updated_value);

  kv_result_t result = kv_set(key, initial_value, initial_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set initial key-value pair");

  char retrieved_value[KV_MAX_VALUE_LEN];
  uint8_t retrieved_value_len = sizeof(retrieved_value);
  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get initial key-value pair");
  cr_assert_eq(retrieved_value_len, initial_value_len, "Initial retrieved value length mismatch");
  cr_assert(memcmp(retrieved_value, initial_value, initial_value_len) == 0,
            "Initial retrieved value mismatch");

  result = kv_set(key, updated_value, updated_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to update key-value pair");

  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get updated key-value pair");
  cr_assert_eq(retrieved_value_len, updated_value_len, "Retrieved value length mismatch");
  cr_assert(memcmp(retrieved_value, updated_value, updated_value_len) == 0,
            "Retrieved value mismatch");
}

Test(kv_test, set_key_value_with_max_length) {
  const char* key = "1234567890";
  char value[KV_MAX_VALUE_LEN];
  memset(value, 'A', KV_MAX_VALUE_LEN);
  uint8_t value_len = KV_MAX_VALUE_LEN;

  kv_result_t result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set key-value pair with max length");

  kv_print();

  char retrieved_value[KV_MAX_VALUE_LEN];
  uint8_t retrieved_value_len = sizeof(retrieved_value);
  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert_eq(result, KV_ERR_NONE, "Failed to get key-value pair with max length");
  cr_assert_eq(retrieved_value_len, value_len, "Retrieved value length mismatch");
  cr_assert(memcmp(retrieved_value, value, value_len) == 0, "Retrieved value mismatch");
}

Test(kv_test, truncation) {
  const char* key = "hello";
  char value[KV_MAX_VALUE_LEN];
  memset(value, 'A', KV_MAX_VALUE_LEN);

  kv_result_t result = kv_set(key, value, sizeof(value));
  cr_assert(result == KV_ERR_NONE, "Failed to set key-value pair");

  // Try to get into a too-small buffer
  char retrieved_value[10] = {0};
  uint8_t retrieved_value_len = sizeof(retrieved_value);

  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_TRUNCATED, "Expected KV_ERR_TRUNCATED when buffer is too small");
  cr_assert(retrieved_value_len == retrieved_value_len,
            "Expected retrieved value length to be truncated");
  cr_assert(memcmp(retrieved_value, value, retrieved_value_len) == 0, "Retrieved value mismatch");
}

Test(kv_test, set_key_value_with_too_long_key) {
  const char* key = "12345678901";
  char value[KV_MAX_VALUE_LEN];
  memset(value, 'A', KV_MAX_VALUE_LEN);
  uint8_t value_len = KV_MAX_VALUE_LEN;

  kv_result_t result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_INVALID, "Failed to set key-value pair with max length");
}

Test(kv_test, fill_up) {
  const char* value = "value1";
  uint8_t value_len = strlen(value);

  // Fill the file with KV_MAX_ENTRIES key-value pairs
  for (int i = 0; i < KV_MAX_ENTRIES; i++) {
    kv_print();
    char key[KV_MAX_KEY_LEN];
    snprintf(key, KV_MAX_KEY_LEN, "key%d", i);
    cr_assert_eq(kv_set(key, value, value_len), KV_ERR_NONE, "Failed to set key-value pair");
  }

  // Attempt to set another key-value pair should fail with KV_ERR_APPEND
  char key[KV_MAX_KEY_LEN];
  snprintf(key, KV_MAX_KEY_LEN, "key%d", KV_MAX_ENTRIES);
  kv_result_t result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_APPEND, "Expected KV_ERR_APPEND when file is full");
}

Test(kv_test, set_key_value_invalid_input) {
  const char* key = NULL;
  const char* value = "value1";
  uint8_t value_len = strlen(value);

  kv_result_t result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_INVALID, "Expected KV_ERR_INVALID for NULL key");

  key = "key1";
  value = NULL;
  result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_INVALID, "Expected KV_ERR_INVALID for NULL value");

  value = "value1";
  value_len = 0;
  result = kv_set(key, value, value_len);
  cr_assert(result == KV_ERR_INVALID, "Expected KV_ERR_INVALID for zero value length");
}

Test(kv_test, get_key_value_invalid_input) {
  const char* key = NULL;
  char value[KV_MAX_VALUE_LEN];
  uint8_t value_len;

  kv_result_t result = kv_get(key, value, &value_len);
  cr_assert(result == KV_ERR_INVALID, "Expected KV_ERR_INVALID for NULL key");

  key = "key1";
  result = kv_get(key, NULL, &value_len);
  cr_assert(result == KV_ERR_INVALID, "Expected KV_ERR_INVALID for NULL value");

  result = kv_get(key, value, NULL);
  cr_assert(result == KV_ERR_INVALID, "Expected KV_ERR_INVALID for NULL value length pointer");
}

Test(kv_test, set_get_multiple_keys) {
  const char* key1 = "key1";
  const char* value1 = "value1";
  uint8_t value1_len = strlen(value1);

  const char* key2 = "key2";
  const char* value2 = "value2";
  uint8_t value2_len = strlen(value2);

  kv_result_t result = kv_set(key1, value1, value1_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set first key-value pair");

  result = kv_set(key2, value2, value2_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set second key-value pair");

  char retrieved_value[KV_MAX_VALUE_LEN];
  uint8_t retrieved_value_len = sizeof(retrieved_value);

  result = kv_get(key1, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get first key-value pair");
  cr_assert_eq(retrieved_value_len, value1_len, "First retrieved value length mismatch");
  cr_assert(memcmp(retrieved_value, value1, value1_len) == 0, "First retrieved value mismatch");

  result = kv_get(key2, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get second key-value pair");
  cr_assert_eq(retrieved_value_len, value2_len, "Second retrieved value length mismatch");
  cr_assert(memcmp(retrieved_value, value2, value2_len) == 0, "Second retrieved value mismatch");
}

Test(kv_test, overwrite_multiple_times) {
  const char* key = "key1";
  const char* initial_value = "initial";
  uint8_t initial_value_len = strlen(initial_value);

  const char* second_value = "second";
  uint8_t second_value_len = strlen(second_value);

  const char* third_value = "third";
  uint8_t third_value_len = strlen(third_value);

  char retrieved_value[KV_MAX_VALUE_LEN];
  uint8_t retrieved_value_len = sizeof(retrieved_value);

  kv_result_t result = kv_set(key, initial_value, initial_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set initial key-value pair");

  result = kv_get(key, retrieved_value, &retrieved_value_len);
  printf("reslut %d\n", result);
  cr_assert(result == KV_ERR_NONE, "Failed to get initial key-value pair");

  result = kv_set(key, second_value, second_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set second key-value pair");

  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get second key-value pair");

  result = kv_set(key, third_value, third_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to set third key-value pair");

  result = kv_get(key, retrieved_value, &retrieved_value_len);
  cr_assert(result == KV_ERR_NONE, "Failed to get third key-value pair");
}

Test(kv_test, kv_set_with_large_value_len) {
  const char* key = "k";
  const char* value = "v";
  kv_result_t result = kv_set(key, value, 1);
  cr_assert(result == KV_ERR_NONE, "Failed to set key-value pair with max length");
  result = kv_set(key, value, 255);
  cr_assert(result == KV_ERR_INVALID,
            "Expected KV_ERR_INVALID for value length > KV_MAX_VALUE_LEN");
}
