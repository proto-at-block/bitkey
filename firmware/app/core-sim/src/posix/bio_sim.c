/**
 * @file bio_sim.c
 * @brief Fingerprint sensor simulation for core-sim
 *
 * Implements bio.h using pthread primitives for blocking waits
 * and in-memory template storage with optional persistence.
 */

#include "bio_sim.h"

#include "bio.h"
#include "device_state.h"
#include "display_controller.h"
#include "secutils.h"
#include "sim_persistence.h"
#include "stdio_defs.h"

#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

#define BIO_STATE_FILE    "bio_fingerprints.bin"
#define BIO_STATE_VERSION 1

typedef struct {
  uint32_t version;
  bool templates_enrolled[TEMPLATE_MAX_COUNT];
  char template_labels[TEMPLATE_MAX_COUNT][BIO_LABEL_MAX_LEN];
} bio_persistent_state_t;

// Default enrollment samples to match real firmware
#define DEFAULT_ENROLLMENT_SAMPLES 5

// Simulation state
typedef struct {
  pthread_mutex_t lock;
  pthread_cond_t finger_event;
  bool initialized;

  // Pending finger gesture counts (indexed by bio_gesture_t)
  uint32_t gesture_counts[2];

  // Cancellation
  bool cancel_requested;

  // Template storage (in-memory)
  bool templates_enrolled[TEMPLATE_MAX_COUNT];
  char template_labels[TEMPLATE_MAX_COUNT][BIO_LABEL_MAX_LEN];

  // Enrollment state
  bool enrolling;
  bio_template_id_t enroll_target_id;
  uint32_t enroll_samples_collected;
  uint32_t enroll_samples_required;
  uint32_t enroll_fail_count;

  // Statistics
  bio_match_stats_t match_stats;
  bio_diagnostics_t last_diagnostics;
} bio_sim_state_t;

static bio_sim_state_t state = {
  .lock = PTHREAD_MUTEX_INITIALIZER,
  .finger_event = PTHREAD_COND_INITIALIZER,
  .initialized = false,
  .gesture_counts = {0, 0},
  .cancel_requested = false,
  .enrolling = false,
  .enroll_samples_required = DEFAULT_ENROLLMENT_SAMPLES,
};

static uint32_t count_enrolled_templates(void) {
  uint32_t count = 0;
  for (int i = 0; i < TEMPLATE_MAX_COUNT; i++) {
    if (state.templates_enrolled[i]) {
      count++;
    }
  }
  return count;
}

static void bio_save_state(void) {
  if (!sim_persistence_enabled()) {
    return;
  }
  bio_persistent_state_t ps = {.version = BIO_STATE_VERSION};
  memcpy(ps.templates_enrolled, state.templates_enrolled, sizeof(ps.templates_enrolled));
  memcpy(ps.template_labels, state.template_labels, sizeof(ps.template_labels));
  sim_persistence_save(BIO_STATE_FILE, &ps, sizeof(ps));
}

static void bio_load_state(void) {
  if (!sim_persistence_enabled()) {
    return;
  }
  bio_persistent_state_t ps;
  if (sim_persistence_load(BIO_STATE_FILE, &ps, sizeof(ps)) && ps.version == BIO_STATE_VERSION) {
    memcpy(state.templates_enrolled, ps.templates_enrolled, sizeof(state.templates_enrolled));
    memcpy(state.template_labels, ps.template_labels, sizeof(state.template_labels));
    LOG("bio_sim: loaded %u fingerprints from persistence", count_enrolled_templates());
  }
}

static bio_template_id_t find_first_enrolled_template(void) {
  for (bio_template_id_t i = 0; i < TEMPLATE_MAX_COUNT; i++) {
    if (state.templates_enrolled[i]) {
      return i;
    }
  }
  return BIO_TEMPLATE_ID_INVALID;
}

static void generate_mock_diagnostics(bio_diagnostics_t* diag) {
  diag->finger_coverage_valid = true;
  diag->finger_coverage = 3500;  // Good coverage
  diag->common_mode_noise_valid = true;
  diag->common_mode_noise = 0;  // No noise
  diag->image_quality_valid = true;
  diag->image_quality = 85;  // Good quality
  diag->sensor_coverage_valid = true;
  diag->sensor_coverage = 90;  // Good coverage
  diag->template_data_update_valid = true;
  diag->template_data_update = 1;
}

void bio_sim_init(void) {
  pthread_mutex_lock(&state.lock);

  // Reset transient state
  memset(state.gesture_counts, 0, sizeof(state.gesture_counts));
  state.cancel_requested = false;
  state.enrolling = false;
  state.enroll_samples_collected = 0;
  state.enroll_fail_count = 0;
  state.enroll_samples_required = emu_enrollment_required_passes();
  LOG("bio_sim_init: enroll_samples_required=%u (auth_mode=%d)", state.enroll_samples_required,
      emu_get_auth_mode());

  // Clear template state (will be restored from persistence if available)
  memset(state.templates_enrolled, 0, sizeof(state.templates_enrolled));
  memset(state.template_labels, 0, sizeof(state.template_labels));
  memset(&state.match_stats, 0, sizeof(state.match_stats));
  memset(&state.last_diagnostics, 0, sizeof(state.last_diagnostics));

  state.initialized = true;

  pthread_mutex_unlock(&state.lock);

  // Load persisted fingerprint state (if available)
  bio_load_state();

  LOG("bio_sim: initialized");
}

void bio_sim_signal_finger(bio_gesture_t gesture) {
  pthread_mutex_lock(&state.lock);

  if (gesture < (sizeof(state.gesture_counts) / sizeof(state.gesture_counts[0]))) {
    state.gesture_counts[gesture]++;
  }

  LOG("bio_sim: signal finger %s", gesture == BIO_FINGER_DOWN ? "DOWN" : "UP");

  pthread_cond_broadcast(&state.finger_event);
  pthread_mutex_unlock(&state.lock);
}

void bio_sim_reset(void) {
  bio_sim_init();
}

uint32_t bio_sim_get_enrollment_samples_required(void) {
  return state.enroll_samples_required;
}

void bio_sim_set_enrollment_samples_required(uint32_t samples) {
  pthread_mutex_lock(&state.lock);
  state.enroll_samples_required = samples;
  pthread_mutex_unlock(&state.lock);
}

bool bio_lib_init(void) {
  if (!state.initialized) {
    bio_sim_init();
  }
  LOG("bio_lib_init: success");
  return true;
}

void bio_lib_reset(void) {
  LOG("bio_lib_reset: called");
  // Just reset the cancel flag, keep templates
  pthread_mutex_lock(&state.lock);
  state.cancel_requested = false;
  pthread_mutex_unlock(&state.lock);
}

void bio_hal_init(void) {
  LOG("bio_hal_init: called");
}

bool bio_wait_for_finger_non_blocking(bio_gesture_t gesture) {
  pthread_mutex_lock(&state.lock);
  bool result = false;
  if (gesture < (sizeof(state.gesture_counts) / sizeof(state.gesture_counts[0]))) {
    result = (state.gesture_counts[gesture] > 0);
  }
  pthread_mutex_unlock(&state.lock);
  return result;
}

bool bio_wait_for_finger_blocking(bio_gesture_t gesture) {
  pthread_mutex_lock(&state.lock);

  LOG("bio_wait_for_finger_blocking: waiting for %s", gesture == BIO_FINGER_DOWN ? "DOWN" : "UP");

  // Wait until we get the gesture we're looking for or cancel is requested
  while (!state.cancel_requested) {
    // Check if we already have the gesture signaled
    if (gesture < (sizeof(state.gesture_counts) / sizeof(state.gesture_counts[0])) &&
        state.gesture_counts[gesture] > 0) {
      state.gesture_counts[gesture]--;
      LOG("bio_wait_for_finger_blocking: got %s", gesture == BIO_FINGER_DOWN ? "DOWN" : "UP");
      pthread_mutex_unlock(&state.lock);
      return true;
    }

    // Wait for a signal
    pthread_cond_wait(&state.finger_event, &state.lock);
  }

  LOG("bio_wait_for_finger_blocking: cancelled");
  pthread_mutex_unlock(&state.lock);
  return false;
}

void bio_storage_get_template_count(uint32_t* count) {
  if (count) {
    pthread_mutex_lock(&state.lock);
    *count = count_enrolled_templates();
    pthread_mutex_unlock(&state.lock);
  }
}

bool bio_fingerprint_index_exists(bio_template_id_t id) {
  if (id >= TEMPLATE_MAX_COUNT) {
    return false;
  }
  pthread_mutex_lock(&state.lock);
  bool exists = state.templates_enrolled[id];
  pthread_mutex_unlock(&state.lock);
  return exists;
}

bool bio_fingerprint_exists(void) {
  pthread_mutex_lock(&state.lock);
  bool exists = (count_enrolled_templates() > 0);
  pthread_mutex_unlock(&state.lock);
  return exists;
}

bio_err_t bio_storage_delete_template(bio_template_id_t id) {
  if (id >= TEMPLATE_MAX_COUNT) {
    return BIO_ERR_TEMPLATE_DOESNT_EXIST;
  }

  pthread_mutex_lock(&state.lock);

  if (!state.templates_enrolled[id]) {
    pthread_mutex_unlock(&state.lock);
    return BIO_ERR_TEMPLATE_DOESNT_EXIST;
  }

  state.templates_enrolled[id] = false;
  memset(state.template_labels[id], 0, BIO_LABEL_MAX_LEN);

  LOG("bio_storage_delete_template: deleted id=%d", id);

  pthread_mutex_unlock(&state.lock);

  bio_save_state();

  return BIO_ERR_NONE;
}

bool bio_storage_label_save(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN]) {
  if (id >= TEMPLATE_MAX_COUNT) {
    return false;
  }

  pthread_mutex_lock(&state.lock);

  if (!state.templates_enrolled[id]) {
    pthread_mutex_unlock(&state.lock);
    return false;
  }

  strncpy(state.template_labels[id], label, BIO_LABEL_MAX_LEN - 1);
  state.template_labels[id][BIO_LABEL_MAX_LEN - 1] = '\0';

  LOG("bio_storage_label_save: id=%d label='%s'", id, label);

  pthread_mutex_unlock(&state.lock);

  bio_save_state();

  return true;
}

bool bio_storage_label_retrieve(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN]) {
  if (id >= TEMPLATE_MAX_COUNT || !label) {
    return false;
  }

  pthread_mutex_lock(&state.lock);

  if (!state.templates_enrolled[id]) {
    pthread_mutex_unlock(&state.lock);
    return false;
  }

  strncpy(label, state.template_labels[id], BIO_LABEL_MAX_LEN);
  label[BIO_LABEL_MAX_LEN - 1] = '\0';

  pthread_mutex_unlock(&state.lock);
  return true;
}

bool bio_enroll_finger(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN],
                       bio_enroll_stats_t* stats) {
  if (id >= TEMPLATE_MAX_COUNT) {
    LOG("bio_enroll_finger: invalid id=%d", id);
    return false;
  }

  pthread_mutex_lock(&state.lock);

  // Initialize enrollment state
  state.enrolling = true;
  state.enroll_target_id = id;
  // Start with 1 sample: the finger DOWN that woke auth_matching_thread counts
  state.enroll_samples_collected = 1;
  state.enroll_fail_count = 0;
  state.cancel_requested = false;

  uint32_t samples_needed = state.enroll_samples_required;
  LOG("bio_enroll_finger: starting enrollment for id=%d, need %u samples (first touch counted)", id,
      samples_needed);

  pthread_mutex_unlock(&state.lock);

  // Initialize stats
  if (stats) {
    memset(stats, 0, sizeof(*stats));
    stats->pass_count = 1;  // First sample already counted
  }

  // Send progress for first sample (the wake-up touch)
  {
    uint32_t samples_remaining = (1 < samples_needed) ? (samples_needed - 1) : 0;
    enrollment_progress_data_t progress = {
      .samples_remaining = samples_remaining,
      .total_samples = samples_needed,
    };
    display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_PROGRESS_GOOD, &progress,
                                       sizeof(progress));
    LOG("bio_enroll_finger: sample 1/%u (from wake-up touch)", samples_needed);
  }

  // Check if already complete (single-sample enrollment)
  if (samples_needed <= 1) {
    goto enrollment_complete;
  }

  // Wait for finger UP from the initial touch
  bio_wait_for_finger_blocking(BIO_FINGER_UP);

  // Enrollment loop - wait for remaining finger touches
  while (true) {
    // Wait for finger down
    bio_wait_for_finger_blocking(BIO_FINGER_DOWN);

    pthread_mutex_lock(&state.lock);

    // Check for cancellation
    if (state.cancel_requested) {
      LOG("bio_enroll_finger: cancelled");
      state.enrolling = false;
      pthread_mutex_unlock(&state.lock);
      return false;
    }

    // Simulate successful sample capture (always pass in simulation)
    state.enroll_samples_collected++;
    uint32_t samples_done = state.enroll_samples_collected;
    uint32_t samples_needed = state.enroll_samples_required;

    LOG("bio_enroll_finger: sample %u/%u", samples_done, samples_needed);

    // Update stats
    if (stats) {
      stats->pass_count = samples_done;
      stats->fail_count = state.enroll_fail_count;
      generate_mock_diagnostics(&stats->diagnostics);
    }

    // Send UI event for progress
    uint32_t samples_remaining =
      (samples_done < samples_needed) ? (samples_needed - samples_done) : 0;
    enrollment_progress_data_t progress = {
      .samples_remaining = samples_remaining,
      .total_samples = samples_needed,
    };

    pthread_mutex_unlock(&state.lock);

    // Send progress event
    display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_PROGRESS_GOOD, &progress,
                                       sizeof(progress));

    // Check if enrollment is complete
    if (samples_done >= samples_needed) {
    enrollment_complete:
      pthread_mutex_lock(&state.lock);

      // Store the template
      state.templates_enrolled[id] = true;
      if (label) {
        strncpy(state.template_labels[id], label, BIO_LABEL_MAX_LEN - 1);
        state.template_labels[id][BIO_LABEL_MAX_LEN - 1] = '\0';
      }

      state.enrolling = false;
      LOG("bio_enroll_finger: complete, template stored at id=%d", id);

      pthread_mutex_unlock(&state.lock);

      // Persist the new fingerprint
      bio_save_state();

      // Notify display controller that enrollment is complete
      display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_COMPLETE, NULL, 0);

      return true;
    }

    // Wait for finger up before next sample
    bio_wait_for_finger_blocking(BIO_FINGER_UP);

    pthread_mutex_lock(&state.lock);
    if (state.cancel_requested) {
      LOG("bio_enroll_finger: cancelled during finger up wait");
      state.enrolling = false;
      pthread_mutex_unlock(&state.lock);
      return false;
    }
    pthread_mutex_unlock(&state.lock);
  }
}

void bio_enroll_cancel(void) {
  pthread_mutex_lock(&state.lock);

  LOG("bio_enroll_cancel: called (enrolling=%d)", state.enrolling);

  state.cancel_requested = true;
  state.enrolling = false;

  // Wake up any blocked waits
  pthread_cond_broadcast(&state.finger_event);

  pthread_mutex_unlock(&state.lock);
}

/* NO_OPTIMIZE mirrors the real implementation (fpc_biometrics.c) so the
 * secure_bool_t result handling matches the hardware build's codegen. */
NO_OPTIMIZE secure_bool_t bio_authenticate_finger(secure_bool_t* is_match,
                                                  bio_template_id_t* match_template_id,
                                                  uint32_t comms_timestamp) {
  (void)comms_timestamp;  // Not used in simulation

  pthread_mutex_lock(&state.lock);

  // Match against first enrolled template
  bio_template_id_t enrolled = find_first_enrolled_template();

  if (enrolled != BIO_TEMPLATE_ID_INVALID) {
    LOG("bio_authenticate_finger: matched template id=%d", enrolled);

    if (is_match)
      *is_match = SECURE_TRUE;
    if (match_template_id)
      *match_template_id = enrolled;

    state.match_stats.pass_counts[enrolled].tally++;

    // Send UI event
    pthread_mutex_unlock(&state.lock);

    // Send success event with template index
    uint8_t template_index = (uint8_t)enrolled;
    display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, &template_index,
                                       sizeof(template_index));

    return SECURE_TRUE;
  }

  // No enrolled templates
  LOG("bio_authenticate_finger: no enrolled templates, fail");

  if (is_match)
    *is_match = SECURE_FALSE;
  if (match_template_id)
    *match_template_id = BIO_TEMPLATE_ID_INVALID;

  state.match_stats.fail_count++;

  pthread_mutex_unlock(&state.lock);
  return SECURE_TRUE;
}

bio_match_stats_t* bio_match_stats_get(void) {
  return &state.match_stats;
}

void bio_match_stats_clear(void) {
  pthread_mutex_lock(&state.lock);
  memset(&state.match_stats, 0, sizeof(state.match_stats));
  pthread_mutex_unlock(&state.lock);
}

bio_diagnostics_t bio_get_diagnostics(void) {
  pthread_mutex_lock(&state.lock);
  bio_diagnostics_t diag = state.last_diagnostics;
  pthread_mutex_unlock(&state.lock);
  return diag;
}

void bio_get_and_update_diagnostics(bio_diagnostics_t* diagnostics) {
  if (!diagnostics)
    return;

  pthread_mutex_lock(&state.lock);
  generate_mock_diagnostics(diagnostics);
  state.last_diagnostics = *diagnostics;
  pthread_mutex_unlock(&state.lock);
}

void bio_selftest(bio_selftest_result_t* result) {
  if (result) {
    result->irq_test = true;
    result->spi_rw_test = true;
    result->spi_speed_test = true;
    result->image_stress_test = true;
    result->reg_stress_test = true;
    result->otp_test = true;
    result->prod_test = true;
  }
}

bool bio_sensor_is_secured(bool* secured) {
  if (secured)
    *secured = true;
  return true;
}

bool bio_sensor_is_otp_locked(bool* locked) {
  if (locked)
    *locked = true;
  return true;
}

kv_result_t bio_template_enrolled_by_version_get(bio_template_id_t id, uint32_t* version_out) {
  (void)id;
  (void)version_out;
  return KV_ERR_NOT_FOUND;
}

kv_result_t bio_template_enrolled_by_version_store(bio_template_id_t id) {
  (void)id;
  return KV_ERR_NONE;
}

void bio_wipe_state(void) {
  bio_sim_reset();
  // Delete persisted fingerprint data
  sim_persistence_delete(BIO_STATE_FILE);
}
