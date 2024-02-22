#include "assert.h"
#include "bio.h"
#include "bio_impl.h"
#include "feature_flags.h"
#include "fpc_bep_bio.h"
#include "fpc_bep_types.h"
#include "log.h"

#include <stdbool.h>
#include <stdint.h>

#define ONE_DAY (24 * 60 * 60)

static bool past_update_period(uint32_t earlier, uint32_t later) {
  if (later < earlier) {
    LOGE("later timestamp (%lu) is less than earlier timestamp (%lu)", later, earlier);
    return false;
  }

  // Return true if the difference is at least 3 days.
  return (later - earlier) >= (3 * ONE_DAY);
}

static bool do_update(bio_template_id_t id, fpc_bep_template_t* template,
                      uint32_t comms_timestamp) {
  LOGI("Writing new template");
  if (!bio_storage_template_save(id, template)) {
    LOGE("Failed to save template (%d)", id);
    return false;
  }

  if (comms_timestamp != 0) {
    LOGI("Writing new timestamp %ld", comms_timestamp);
    if (!bio_storage_timestamp_save(comms_timestamp)) {
      LOGE("Failed to save timestamp (%ld)", comms_timestamp);
      return false;
    }
  }

  return true;
}

// Update a template if it is expired or if no comms_timestamp is set.
//
// comms_timestamp won't be set if the phone hasn't sent any protos to the hardware.
// In that case, we can't be sure if the stored template is old or not, so we default
// to updating it, to be safe.
bool bio_update_template(bio_template_id_t id, fpc_bep_template_t* template,
                         uint32_t comms_timestamp) {
  if (!feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_RATE_LIMIT_TEMPLATE_UPDATE)) {
    LOGI("Feature flag disabled, updating template");
    return do_update(id, template, comms_timestamp);
  }

  if (comms_timestamp == 0) {
    // No timestamp set, default to updating template.
    LOGI("No timestamp set, updating template");
    return do_update(id, template, comms_timestamp);
  }

  bool should_update = false;
  uint32_t last_timestamp;
  if (!bio_storage_timestamp_retrieve(&last_timestamp)) {
    should_update = true;  // Failed to read last timestamp; it may have not existed. Update.
    LOGW("Failed to read last timestamp, updating template");
  } else {
    should_update = past_update_period(last_timestamp, comms_timestamp);
    if (should_update) {
      LOGI("Expired template (last timestamp: %lu, new timestamp: %lu)", last_timestamp,
           comms_timestamp);
    }
  }

  if (should_update) {
    return do_update(id, template, comms_timestamp);
  } else {
    LOGI("Not updating template (last timestamp: %lu, new timestamp: %lu)", last_timestamp,
         comms_timestamp);
  }

  return true;
}
