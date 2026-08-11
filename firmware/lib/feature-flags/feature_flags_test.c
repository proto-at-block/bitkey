#include "feature_flags.h"
#include "filesystem.h"
#include "log.h"
#include "sysevent.h"

#include <criterion/criterion.h>

#include <string.h>

static bool persisted_flags[_fwpb_feature_flag_ARRAYSIZE];
static uint32_t persisted_flags_len;
static int write_count;

void _log(log_level_t level, const char* colour, const char* file, int line, const char* format,
          ...) {
  (void)level;
  (void)colour;
  (void)file;
  (void)line;
  (void)format;
}

bool fs_util_read_all_global(char* filename, uint8_t* data, uint32_t max_size, uint32_t* size_out) {
  (void)filename;
  cr_assert_leq(persisted_flags_len, max_size);
  memcpy(data, persisted_flags, persisted_flags_len);
  *size_out = persisted_flags_len;
  return true;
}

bool fs_util_write_global(char* filename, uint8_t* data, uint32_t size) {
  (void)filename;
  cr_assert_leq(size, sizeof(persisted_flags));
  memcpy(persisted_flags, data, size);
  persisted_flags_len = size;
  write_count++;
  return true;
}

int fs_remove(const char* path) {
  (void)path;
  return 0;
}

void sysevent_wait(const sysevent_t events, const bool wait_for_all) {
  (void)events;
  (void)wait_for_all;
}

void sysevent_set(const sysevent_t events) {
  (void)events;
}

static void init(void) {
  memset(persisted_flags, 0, sizeof(persisted_flags));
  persisted_flags_len = sizeof(persisted_flags);
  write_count = 0;
}

TestSuite(feature_flags_prod_test, .init = init);

Test(feature_flags_prod_test, disables_persisted_unlock_flag) {
  persisted_flags[fwpb_feature_flag_FEATURE_FLAG_UNLOCK] = true;

  cr_assert(feature_flags_init());

  cr_assert_not(feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_UNLOCK));
  cr_assert_not(persisted_flags[fwpb_feature_flag_FEATURE_FLAG_UNLOCK]);
  cr_assert_eq(write_count, 1);
}

Test(feature_flags_prod_test, rejects_unlock_flag_writes_atomically) {
  cr_assert(feature_flags_init());
  cr_assert(feature_flags_set(fwpb_feature_flag_FEATURE_FLAG_RATE_LIMIT_TEMPLATE_UPDATE, true));
  cr_assert(feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_RATE_LIMIT_TEMPLATE_UPDATE));
  int writes_before_rejected_updates = write_count;

  cr_assert_not(feature_flags_set(fwpb_feature_flag_FEATURE_FLAG_UNLOCK, true));

  fwpb_feature_flag_cfg updates[] = {
    {
      .flag = fwpb_feature_flag_FEATURE_FLAG_RATE_LIMIT_TEMPLATE_UPDATE,
      .enabled = false,
    },
    {
      .flag = fwpb_feature_flag_FEATURE_FLAG_UNLOCK,
      .enabled = true,
    },
  };
  cr_assert_not(feature_flags_set_multiple(updates, sizeof(updates) / sizeof(updates[0])));

  cr_assert(feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_RATE_LIMIT_TEMPLATE_UPDATE));
  cr_assert_not(feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_UNLOCK));
  cr_assert_eq(write_count, writes_before_rejected_updates);
}
