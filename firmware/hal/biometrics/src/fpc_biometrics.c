#include "bio.h"
#include "bio_impl.h"

// clang-format off
#include <stddef.h>  // FPC forgot to include stddef.h in some of their headers,
                     // so this include must come first.
// clang-format on
#include "animation.h"
#include "auth.h"
#include "bitlog.h"
#include "fpc_bep_algorithms.h"
#include "fpc_bep_bio.h"
#include "fpc_bep_image.h"
#include "fpc_bep_sensor.h"
#include "fpc_bep_sensor_test.h"
#include "fpc_bep_types.h"
#include "fpc_timebase.h"
#include "ipc.h"
#include "kv.h"
#include "log.h"
#include "metadata.h"
#include "perf.h"
#include "rtos.h"
#include "sysinfo.h"

#define BIO_IMAGE_CAPTURE_MAX_TRIES (4)
#define BLOCKING_WAIT               (RTOS_EVENT_GROUP_TIMEOUT_MAX)

#define TEMPLATE_TO_FW_VERSION_KEY_LEN (4)
#define TEMPLATE_TO_FW_VERSION_KEY_FMT "fp%d"

// See note in fpc_hal.c about why this is bad.
uint32_t number_required_samples = 0;

static struct {
  uint32_t sensor_hw_detect_poll_period_ms;
  uint32_t finger_detect_timeout_ms;
  uint32_t image_capture_tries;
  uint32_t enroll_tries;
  fpc_bep_template_t* template;
  bool cancel_triggered;
  bio_template_id_t template_id;
  bio_match_stats_t match_stats;
} bio_priv = {
  .sensor_hw_detect_poll_period_ms = 4,  // Was 20, not sure if this helps.
  .finger_detect_timeout_ms = 10000,
  .image_capture_tries = 4,
  .enroll_tries = 4,
  .template = NULL,
  .template_id = TEMPLATE_MAX_COUNT + 1,  // Only valid if template != NULL
  .cancel_triggered = false,
  .match_stats = {.pass_counts = {[0 ... TEMPLATE_MAX_COUNT - 1] = {0}}, .fail_count = 0},
};

static struct {
  perf_counter_t* enroll;
  perf_counter_t* auth;
  perf_counter_t* capture;
  perf_counter_t* errors;
  perf_counter_t* enroll_pass;
  perf_counter_t* enroll_fail;
} perf;

static void bio_update_match_stats(bio_template_id_t template, bool matched) {
  if (matched) {
    bio_priv.match_stats.pass_counts[template].tally++;
  } else {
    bio_priv.match_stats.fail_count++;
  }
}

static fpc_bep_result_t wait_for_finger_status(fpc_bep_finger_status_t status,
                                               uint32_t timeout_ms) {
  // We must use a busy wait if checking for finger not present (i.e. finger up), since the
  // external interrupt is only generated when the finger is present. While this does consume
  // more power, this is only required for fingerprint enrollment.
  const bool use_exti = (status == FPC_BEP_FINGER_STATUS_PRESENT);

  for (;;) {
    fpc_bep_result_t result = fpc_bep_sensor_sleep(bio_priv.sensor_hw_detect_poll_period_ms);
    if (result != FPC_BEP_RESULT_OK) {
      LOGE("Sleep failed: %d", result);
      goto fail;
    }

    fpc_sensor_wfi(timeout_ms, fpc_sensor_spi_check_irq, use_exti);

    if (bio_priv.cancel_triggered) {
      goto cancel;
    }

    fpc_bep_finger_status_t finger_present;
    result = fpc_bep_check_finger_present(&finger_present);
    if (finger_present == status) {
      break;
    }
    if (result != FPC_BEP_RESULT_OK) {
      LOGE("Check finger present failed: %d", result);
      goto fail;
    }
  }

  perf_end(perf.capture);
  return FPC_BEP_RESULT_OK;

cancel:
  perf_cancel(perf.capture);
  perf_count(perf.errors);
  LOGD("Wait for finger status cancelled");
  fpc_bep_sensor_deep_sleep();
  return FPC_BEP_RESULT_CANCELLED;

fail:
  perf_cancel(perf.capture);
  perf_count(perf.errors);
  LOGE("Wait for finger status failed");
  fpc_bep_sensor_deep_sleep();
  return FPC_BEP_RESULT_GENERAL_ERROR;
}

static fpc_bep_result_t wait_for_finger_down(uint32_t timeout_ms) {
  return wait_for_finger_status(FPC_BEP_FINGER_STATUS_PRESENT, timeout_ms);
}

static fpc_bep_result_t wait_for_finger_up(uint32_t timeout_ms) {
  return wait_for_finger_status(FPC_BEP_FINGER_STATUS_NOT_PRESENT, timeout_ms);
}

static bool capture_image_and_extract_template(fpc_bep_image_t* image) {
  bool capture_result = bio_capture_image(image, bio_priv.image_capture_tries);
  if (!capture_result) {
    LOGE("Failed to capture image");
    return false;
  }

  // Extract template from image, template stored within bep_lib for internal matching.
  fpc_bep_result_t result = fpc_bep_image_extract(&image, NULL);
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("Failed to extract template from image: %d", result);
    return false;
  }

  return true;
}

NO_OPTIMIZE static secure_bool_t match_image(fpc_bep_identify_result_t* identify_result) {
  volatile fpc_bep_result_t result =
    fpc_bep_identify((const fpc_bep_template_t**)&bio_priv.template, 1, identify_result);

  // Play animation as fast as possible, i.e. don't use SECURE_ macros to provide the
  // user feedback.
  // Showing the unlocked LED isn't security-sensitive, but the checks below are.
  if (result == FPC_BEP_RESULT_OK && identify_result->match) {
    static led_start_animation_t msg = {.animation = (uint32_t)ANI_UNLOCKED, .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  }

  SECURE_IF_FAILIN(result != FPC_BEP_RESULT_OK) {
    BITLOG_EVENT(bio_auth_error, result);
    return SECURE_FALSE;
  }

  SECURE_DO({ ASSERT_LOG(result == FPC_BEP_RESULT_OK, "%d", result); });

  SECURE_DO_FAILIN(identify_result->match != true, {
    // Free resources for template which didn't match.
    result = fpc_bep_template_delete(&bio_priv.template);
    ASSERT_LOG(result == FPC_BEP_RESULT_OK, "%d", result);
    SECURE_ASSERT(result == FPC_BEP_RESULT_OK);
  });

  SECURE_DO_FAILOUT(identify_result->match == true, { return SECURE_TRUE; });

  return SECURE_FALSE;
}

// Match the currently image (held by FPC-BEP library) against all templates
static secure_bool_t match_against_all_templates(fpc_bep_identify_result_t* identify_result,
                                                 bool* update_template) {
  *update_template = false;
  secure_bool_t match_found = SECURE_FALSE;

  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    LOGD("Trying to match template %d...", id);

    if (!bio_storage_template_retrieve(id, &bio_priv.template)) {
      continue;
    }

    if (match_image(identify_result) == SECURE_TRUE) {
      identify_result->index = id;
      bio_priv.template_id = id;
      match_found = SECURE_TRUE;
      goto out;
    }
  }

out:
  fpc_bep_identify_release(update_template);
  return match_found;
}

void bio_perf_init(void) {
  perf.enroll = perf_create(PERF_COUNT, bio_enroll);
  perf.auth = perf_create(PERF_ELAPSED, bio_auth);
  perf.capture = perf_create(PERF_ELAPSED, bio_capture);
  perf.errors = perf_create(PERF_COUNT, bio_errors);
  perf.enroll_pass = perf_create(PERF_COUNT, bio_enroll_pass);
  perf.enroll_fail = perf_create(PERF_COUNT, bio_enroll_fail);
}

bool bio_capture_image(fpc_bep_image_t* image, uint8_t max_tries) {
  perf_begin(perf.capture);

  uint8_t tries = 0;
  while (tries < max_tries) {
    fpc_bep_result_t result = wait_for_finger_down(bio_priv.finger_detect_timeout_ms);
    if (result != FPC_BEP_RESULT_OK) {
      LOGE("Wait for finger down failed: %d", result);
      goto fail;
    }

    result = fpc_bep_capture(image);  // Sensor is put into deep sleep mode after this call
    if (result == FPC_BEP_RESULT_OK) {
      // Clean capture
      break;
    }

    // Errors can easily be happen in case of a partial fingerprint capture: try again.
    tries++;
  }

  if (tries >= max_tries) {
    LOGE("Exceeded tries limit");
    goto fail;
  }

  perf_end(perf.capture);
  return true;

fail:
  perf_cancel(perf.capture);
  perf_count(perf.errors);
  return false;
}

void bio_wait_for_finger_blocking(bio_gesture_t gesture) {
#if BIO_DEV_MODE
  // This function is called from auth_task, and interferes with other FPC
  // sensor API calls.
  while (true) rtos_thread_sleep(5000);
#endif
  fpc_bep_result_t result = FPC_BEP_RESULT_GENERAL_ERROR;
  switch (gesture) {
    case BIO_FINGER_DOWN:
      result = wait_for_finger_down(BLOCKING_WAIT);
      break;
    case BIO_FINGER_UP:
      result = wait_for_finger_up(BLOCKING_WAIT);
      break;
    default:
      LOGE("Invalid gesture: %d", gesture);
      break;
  }
  ASSERT_LOG(result == FPC_BEP_RESULT_OK, "%d", result);
}

bool bio_enroll_finger(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN],
                       bio_enroll_stats_t* stats) {
  LOGD("Enroll begin");
  perf_count(perf.enroll);
  perf_reset(perf.enroll_pass);
  perf_reset(perf.enroll_fail);

  static led_set_rest_animation_t start_msg = {.animation = (uint32_t)ANI_ENROLLMENT};
  ipc_send(led_port, &start_msg, sizeof(start_msg), IPC_LED_SET_REST_ANIMATION);

  fpc_bep_enrollment_status_t enroll_status;

  fpc_biometrics_init();

  fpc_bep_image_t* image = fpc_bep_image_new();
  fpc_bep_template_t* enroll_template = NULL;

  if (id > TEMPLATE_MAX_COUNT) {
    LOGE("Invalid template id: %d", id);
    goto hard_fail;
  }

  fpc_bep_result_t result = fpc_bep_enroll_start();
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("Enrollment failed to begin: %d", result);
    BITLOG_EVENT(bio_enroll_error, result);
    goto hard_fail;
  }

  uint32_t tries = 0;
  uint32_t previous_samples_remaining = number_required_samples;
  while (tries < bio_priv.enroll_tries) {
    if (bio_priv.cancel_triggered) {
      LOGD("Enrollment cancelled");
      bio_priv.cancel_triggered = false;
      goto fail;
    }

    if (image == NULL) {
      LOGE("Null image pointer");
      goto fail;
    }

    // Capture image. Finger must be lifted before capture.
    if (!bio_capture_image(image, BIO_IMAGE_CAPTURE_MAX_TRIES)) {
      LOGE("Failed to capture image");
      continue;
    }

    // Enroll fingerprint
    result = fpc_bep_enroll(image, &enroll_status);
    if (result != FPC_BEP_RESULT_OK) {
      BITLOG_EVENT(bio_enroll_error, result);

      if (result == FPC_BEP_RESULT_TOO_MANY_BAD_IMAGES) {
        // Must abort
        goto fail;
      }

      LOGE("Failed to enroll fingerprint: %d", result);
      tries++;
      continue;
    }

    if (enroll_status.samples_remaining == 0) {
      perf_count(perf.enroll_pass);
      break;
    }

    // Indicate whether the sample was accepted to the user via the LED.
    static led_start_animation_t msg = {.animation = 0, .immediate = true};
    if (enroll_status.samples_remaining < previous_samples_remaining) {
      // Record good samples only; we want to see the quality of the template, which
      // is formed from only samples we accept.
      bio_get_and_update_diagnostics(&stats->diagnostics);

      perf_count(perf.enroll_pass);
      msg.animation = (uint32_t)ANI_FINGERPRINT_SAMPLE_GOOD;
      ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
    } else {
      msg.animation = (uint32_t)ANI_FINGERPRINT_SAMPLE_BAD;
      ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
      perf_count(perf.enroll_fail);
    }

    previous_samples_remaining = enroll_status.samples_remaining;

    LOGD("Remaining touches %lu", enroll_status.samples_remaining);

    wait_for_finger_up(BLOCKING_WAIT);
  }

  static led_start_animation_t msg = {.animation = (uint32_t)ANI_ENROLLMENT_COMPLETE,
                                      .immediate = true};
  ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);

  result = fpc_bep_enroll_finish(&enroll_template);  // May be null
  if (result != FPC_BEP_RESULT_OK) {
    BITLOG_EVENT(bio_enroll_error, result);
    goto hard_fail;
  }

  // Save the template
  result = bio_storage_template_save(id, enroll_template);
  if (!result) {
    LOGE("Failed to save template");
    goto fail;
  }

  fpc_bep_image_delete(&image);  // Noop if image is null
  fpc_bep_template_delete(&enroll_template);

  // Save the label
  if (!bio_storage_label_save(id, label)) {
    LOGE("Failed to save label");
    goto fail;
  }

  // Save which firmware version this template was enrolled with
  kv_result_t kv_result = bio_template_enrolled_by_version_store(id);
  if (kv_result != KV_ERR_NONE) {
    LOGE("Failed to save firmware version for template (%d)", kv_result);
    goto fail;
  }

  const uint32_t pass = (uint32_t)perf_get_count(perf.enroll_pass);
  const uint32_t fail = (uint32_t)perf_get_count(perf.enroll_fail);
  LOGD("Enrollment complete (%ld, %ld)", pass, fail);

  stats->pass_count = pass;
  stats->fail_count = fail;

  return true;

hard_fail:
  perf_count(perf.errors);
fail:
  if (image != NULL) {
    fpc_bep_image_delete(&image);
  }
  fpc_bep_result_t finish_result = fpc_bep_enroll_finish(&enroll_template);
  LOGD("Enroll finish result: %d", finish_result);
  fpc_bep_template_delete(&enroll_template);
  // NOTE: Caller must play an animation here.
  return false;
}

void bio_enroll_cancel(void) {
  bio_priv.cancel_triggered = true;
  fpc_sensor_wfi_cancel();
}

NO_OPTIMIZE secure_bool_t bio_authenticate_finger(secure_bool_t* is_match,
                                                  bio_template_id_t* match_template_id,
                                                  uint32_t comms_timestamp) {
  secure_bool_t ret = SECURE_FALSE;

  SECURE_DO({ *is_match = SECURE_FALSE; });
  *match_template_id = BIO_TEMPLATE_ID_INVALID;

  perf_begin(perf.auth);

  fpc_bep_image_t* image = fpc_bep_image_new();
  if (!image) {
    LOGE("Couldn't allocate image");
    perf_cancel(perf.auth);
    perf_count(perf.errors);
    goto out;
  }

  if (!capture_image_and_extract_template(image)) {
    static led_start_animation_t msg = {.animation = (uint32_t)ANI_FINGERPRINT_BAD,
                                        .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
    perf_cancel(perf.auth);
    perf_count(perf.errors);
    goto out;
  }

  fpc_bep_identify_result_t identify_result = {.match = false, .index = 0};
  bool update_template = false;
  if (match_against_all_templates(&identify_result, &update_template) != SECURE_TRUE) {
    static led_start_animation_t msg = {.animation = (uint32_t)ANI_FINGERPRINT_BAD,
                                        .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
    perf_cancel(perf.auth);
    perf_count(perf.errors);
    goto out;
  }

  SECURE_DO_FAILOUT(identify_result.match, {
    *match_template_id = identify_result.index;
    *is_match = SECURE_TRUE;

    LOGD("Authenticated with template %d", *match_template_id);
    if (update_template) {
      bio_update_template(*match_template_id, bio_priv.template, comms_timestamp);
    }
  });

  ret = SECURE_TRUE;

  perf_end(perf.auth);

out:
  bio_update_match_stats(*match_template_id, identify_result.match);
  fpc_bep_template_delete(&bio_priv.template);
  if (image != NULL) {
    LOGD("Deleting image");
    fpc_bep_image_delete(&image);
  }
  return ret;
}

bio_match_stats_t* bio_match_stats_get(void) {
  return &bio_priv.match_stats;
}

void bio_match_stats_clear(void) {
  bio_priv.match_stats.fail_count = 0;
  for (uint32_t i = 0; i < TEMPLATE_MAX_COUNT; i++) {
    bio_priv.match_stats.pass_counts[i].tally = 0;
  }
}

bio_diagnostics_t bio_get_diagnostics(void) {
  const fpc_bep_diagnostics_parameters_t diagnostics = fpc_bep_diagnostic_data_read();

  bio_diagnostics_t out = {0};

  if (diagnostics.finger_coverage & 0x8000) {
    out.finger_coverage = diagnostics.finger_coverage & 0x7FFF;
    out.finger_coverage_valid = true;
  }

  if (diagnostics.common_mode_noise & 0x80) {
    out.common_mode_noise = diagnostics.common_mode_noise & 0x7F;
    out.common_mode_noise_valid = true;
  }

  if (diagnostics.image_quality & 0x80) {
    out.image_quality = diagnostics.image_quality & 0x7F;
    out.image_quality_valid = true;
  }

  if (diagnostics.sensor_coverage & 0x80) {
    out.sensor_coverage = diagnostics.sensor_coverage & 0x7F;
    out.sensor_coverage_valid = true;
  }

  if (diagnostics.template_data_update & 0x80) {
    out.template_data_update = diagnostics.template_data_update & 0x7F;
    out.template_data_update_valid = true;
  }

  return out;
}

// Calculate a running average of the diagnostics fields.
#define UPDATE_DIAGNOSTIC_FIELD(diagnostics, new, field)           \
  do {                                                             \
    if (new.field##_valid) {                                       \
      if (diagnostics->field##_valid) {                            \
        diagnostics->field = (diagnostics->field + new.field) / 2; \
      } else {                                                     \
        diagnostics->field = new.field;                            \
        diagnostics->field##_valid = true;                         \
      }                                                            \
    }                                                              \
  } while (0)

void bio_get_and_update_diagnostics(bio_diagnostics_t* diagnostics) {
  ASSERT(diagnostics);
  const bio_diagnostics_t new = bio_get_diagnostics();
  UPDATE_DIAGNOSTIC_FIELD(diagnostics, new, finger_coverage);
  UPDATE_DIAGNOSTIC_FIELD(diagnostics, new, common_mode_noise);
  UPDATE_DIAGNOSTIC_FIELD(diagnostics, new, image_quality);
  UPDATE_DIAGNOSTIC_FIELD(diagnostics, new, sensor_coverage);
  UPDATE_DIAGNOSTIC_FIELD(diagnostics, new, template_data_update);
}

kv_result_t bio_template_enrolled_by_version_get(bio_template_id_t id, uint32_t* version_out) {
  char template_id[TEMPLATE_TO_FW_VERSION_KEY_LEN] = {0};
  snprintf(template_id, sizeof(template_id), TEMPLATE_TO_FW_VERSION_KEY_FMT, id);
  uint8_t length = sizeof(*version_out);
  return kv_get(template_id, version_out, &length);
}

kv_result_t bio_template_enrolled_by_version_store(bio_template_id_t id) {
  uint32_t fw_version = 0;
  if (metadata_get_firmware_version(&fw_version) != METADATA_VALID) {
    LOGE("Failed to get firmware version");
    return false;
  }

  char template_id[TEMPLATE_TO_FW_VERSION_KEY_LEN] = {0};
  snprintf(template_id, sizeof(template_id), TEMPLATE_TO_FW_VERSION_KEY_FMT, id);

  return kv_set(template_id, &fw_version, sizeof(fw_version));
}
