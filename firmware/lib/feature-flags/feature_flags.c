#include "feature_flags.h"

#include "filesystem.h"
#include "log.h"
#include "sysevent.h"
#include "wallet.pb.h"

#define FEATURE_FLAGS_FILE_NAME "feature-flags.bin"
#define FEATURE_FLAG_COUNT      _fwpb_feature_flag_ARRAYSIZE

static bool feature_flags[FEATURE_FLAG_COUNT] = {0};
static uint32_t feature_flags_len = 0;

static bool feature_flags_read(void);
static bool feature_flags_write(void);

bool feature_flags_init(void) {
  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);
  bool ret = feature_flags_read();
  sysevent_set(SYSEVENT_FEATURE_FLAGS_READY);
  return ret;
}

bool feature_flags_get(fwpb_feature_flag flag) {
  return feature_flags[flag];
}

bool* feature_flags_get_all(pb_size_t* len) {
  *len = feature_flags_len;
  return feature_flags;
}

bool feature_flags_set(fwpb_feature_flag flag, bool value) {
  feature_flags[flag] = value;
  return feature_flags_write();
}

bool feature_flags_set_multiple(fwpb_feature_flag_cfg* flags, pb_size_t num_flags) {
  for (int i = 0; i < num_flags; i++) {
    fwpb_feature_flag_cfg cfg = flags[i];
    feature_flags[cfg.flag] = cfg.enabled;
  }
  return feature_flags_write();
}

static void set_default_values(void) {
  for (uint32_t i = feature_flags_len; i < FEATURE_FLAG_COUNT; i++) {
    switch (i) {
      case fwpb_feature_flag_FEATURE_FLAG_RATE_LIMIT_TEMPLATE_UPDATE:
        // fall-through
      case fwpb_feature_flag_FEATURE_FLAG_UNLOCK:
        // Unlock is a special case. We shipped the GA firmware with support for pin/password
        // unlock, but the app doesn't support it. Just in case there's a security problem, we
        // left the feature flag turned off.
        feature_flags[i] = false;
        LOGD("Feature flag %ld is disabled by default", i);
        continue;
    }
    feature_flags[i] = true;
  }

  feature_flags_len = FEATURE_FLAG_COUNT;
}

static bool feature_flags_read(void) {
  if (!fs_util_read_all_global(FEATURE_FLAGS_FILE_NAME, (uint8_t*)feature_flags,
                               sizeof(feature_flags), &feature_flags_len)) {
    LOGE("Failed to read feature flags; recreating the file");
    set_default_values();
    fs_remove(FEATURE_FLAGS_FILE_NAME);
    return feature_flags_write();
  }

  // If we've added a new feature flag, initialize to defaults and update the file.
  if (feature_flags_len < FEATURE_FLAG_COUNT) {
    set_default_values();
    return feature_flags_write();
  }

  return true;
}

static bool feature_flags_write(void) {
  if (!fs_util_write_global(FEATURE_FLAGS_FILE_NAME, (uint8_t*)feature_flags, feature_flags_len)) {
    LOGE("Failed to write feature flags");
    return false;
  }
  return true;
}
