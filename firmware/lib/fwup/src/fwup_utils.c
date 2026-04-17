#include "fwup_utils.h"

#include "attributes.h"
#include "metadata.h"
#include "rtos.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

bool fwup_semver_equals(const fwpb_semver* v1, const fwpb_semver* v2) {
  if (v1 == NULL || v2 == NULL) {
    return false;
  }
  return (v1->major == v2->major) && (v1->minor == v2->minor) && (v1->patch == v2->patch);
}

SYSCALL bool fwup_get_self_version(fwpb_semver* version_out) {
  if (version_out == NULL) {
    return false;
  }

  bool success = false;
  RTOS_THREAD_WITH_PRIVILEGE({
    do {
      metadata_t metadata = {0};
      fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
      if (metadata_get_active_slot(&metadata, &active_slot) != METADATA_VALID) {
        break;
      }

      // Verify metadata.version is actually populated (not just zeros)
      if (metadata.version.major == 0 && metadata.version.minor == 0 &&
          metadata.version.patch == 0) {
        break;
      }

      version_out->major = metadata.version.major;
      version_out->minor = metadata.version.minor;
      version_out->patch = metadata.version.patch;
      success = true;
    } while (0);
  });
  return success;
}

SYSCALL bool fwup_get_target_version(fwpb_semver* version_out) {
  if (version_out == NULL) {
    return false;
  }

  bool success = false;
  RTOS_THREAD_WITH_PRIVILEGE({
    do {
      // Determine the active slot, then read metadata from the inactive (target) slot.
      // Reuse a single metadata_t to avoid overflowing the syscall stack (~512 bytes).
      metadata_t meta = {0};
      fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
      if (metadata_get_active_slot(&meta, &active_slot) != METADATA_VALID) {
        break;
      }

      metadata_target_t target =
        (active_slot == fwpb_firmware_slot_SLOT_A) ? META_TGT_APP_B : META_TGT_APP_A;

      memset(&meta, 0, sizeof(meta));
      if (metadata_get(target, &meta) != METADATA_VALID) {
        break;
      }

      version_out->major = meta.version.major;
      version_out->minor = meta.version.minor;
      version_out->patch = meta.version.patch;
      success = true;
    } while (0);
  });
  return success;
}

bool fwup_format_version_string(const fwpb_semver* version, char* buffer, size_t buffer_size) {
  if (!version || !buffer || buffer_size < 16) {
    return false;
  }

  int written = snprintf(buffer, buffer_size, "v%lu.%lu.%lu", (unsigned long)version->major,
                         (unsigned long)version->minor, (unsigned long)version->patch);

  if (written < 0 || (size_t)written >= buffer_size) {
    return false;
  }

  return true;
}
