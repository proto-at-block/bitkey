#include "fff.h"
#include "filesystem.h"
#include "rtos.h"
#include "telemetry_storage.h"

#include <criterion/criterion.h>

DEFINE_FFF_GLOBALS;

FAKE_VALUE_FUNC(int, fs_open_global, fs_file_t**, const char*, int);
FAKE_VALUE_FUNC(int, fs_close_global, fs_file_t*);
FAKE_VALUE_FUNC(int32_t, fs_file_size, fs_file_t*);
FAKE_VALUE_FUNC(int32_t, fs_file_read, fs_file_t*, void*, uint32_t);
FAKE_VALUE_FUNC(int32_t, fs_file_write, fs_file_t*, const void*, uint32_t);
FAKE_VALUE_FUNC(int32_t, fs_file_seek, fs_file_t*, int32_t, fs_whence_flags_t);
FAKE_VALUE_FUNC(int, fs_file_truncate, fs_file_t*, int32_t);

uint8_t active_coredump[TELEMETRY_COREDUMP_SIZE];

bool rtos_in_isr(void) {
  return false;
}

static fs_file_t fake_global_file;
static int open_result;

static int fake_open_global(fs_file_t** file, const char* path, int flags) {
  (void)path;
  (void)flags;

  *file = &fake_global_file;
  return open_result;
}

static void setup(void) {
  RESET_FAKE(fs_open_global);
  RESET_FAKE(fs_close_global);
  RESET_FAKE(fs_file_size);
  RESET_FAKE(fs_file_read);
  RESET_FAKE(fs_file_write);
  RESET_FAKE(fs_file_seek);
  RESET_FAKE(fs_file_truncate);
  FFF_RESET_HISTORY();

  open_result = -1;
  fs_open_global_fake.custom_fake = fake_open_global;
}

Test(telemetry_storage, does_not_access_global_file_when_fragment_open_fails, .init = setup) {
  fwpb_coredump_fragment frag = {0};

  cr_assert_not(telemetry_coredump_read_fragment(0, &frag));
  cr_assert_eq(fs_open_global_fake.call_count, 1);
  cr_assert_eq(fs_file_size_fake.call_count, 0);
  cr_assert_eq(fs_close_global_fake.call_count, 0);
  cr_assert_eq(frag.coredumps_remaining, 0);
}

Test(telemetry_storage, checks_legacy_coredumps_using_fragment_file, .init = setup) {
  fwpb_coredump_fragment frag = {0};
  int32_t file_sizes[] = {1, 0, 0};
  open_result = 0;
  SET_RETURN_SEQ(fs_file_size, file_sizes, 3);

  cr_assert_not(telemetry_coredump_read_fragment(0, &frag));
  cr_assert_eq(fs_open_global_fake.call_count, 1);
  cr_assert_eq(fs_file_size_fake.call_count, 3);
  cr_assert_eq(fs_file_truncate_fake.call_count, 1);
  cr_assert_eq(fs_close_global_fake.call_count, 1);
  cr_assert_eq(frag.coredumps_remaining, 0);
}
