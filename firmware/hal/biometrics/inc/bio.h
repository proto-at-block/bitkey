#pragma once

#include "fpc_bep_sensor.h"
#include "fpc_bep_sensor_security.h"
#include "fpc_bep_sensor_test.h"
#include "kv.h"
#include "secutils.h"

#include <stdbool.h>
#include <stdint.h>

// Total number of supported templates
#define TEMPLATE_MAX_COUNT      (3)
#define BIO_LABEL_MAX_LEN       (32)
#define BIO_TEMPLATE_ID_INVALID (UINT16_MAX)

typedef enum {
  BIO_ERR_NONE = 0,
  BIO_ERR_GENERIC = 1,
  BIO_ERR_TEMPLATE_DOESNT_EXIST = 2,
  BIO_ERR_TEMPLATE_INVALID = 3,
  BIO_ERR_LABEL_DOESNT_EXIST = 4,
} bio_err_t;

typedef uint16_t bio_template_id_t;

// This is like fpc_bep_diagnostics_parameters_t, but with the top 'valid' bit removed.
// and bools to indicate if the value is valid.
typedef struct {
  // Sensor finger detect zone coverage, this value only depends on the sensor hardware.
  // Each zone is represented by one bit.
  // See fpc_bep_get_finger_detect_zones API for more information.
  // Range 0:4095
  bool finger_coverage_valid;
  uint16_t finger_coverage;

  // Indicates if Common Mode Noise were detected in the extraction process.
  // Value 0 CMN was not detected, value 100 CMN was detected.
  bool common_mode_noise_valid;
  uint8_t common_mode_noise;

  // The estimated image quality during extraction.
  // Range 0:100 where 0 Is the lowest quality, 100 is the highest quality.
  bool image_quality_valid;
  uint8_t image_quality;

  // Estimated sensor coverage from extraction of template.
  // Range 0:100
  bool sensor_coverage_valid;
  uint8_t sensor_coverage;

  // Indicates if template data was updated after extraction.
  // Value 0 Template was not updated, value 1 Template was updated.
  bool template_data_update_valid;
  uint8_t template_data_update;
} bio_diagnostics_t;

typedef struct {
  bool irq_test;
  bool spi_rw_test;
  bool spi_speed_test;
  bool image_stress_test;
  bool reg_stress_test;
  bool otp_test;
  bool prod_test;
} bio_selftest_result_t;

typedef struct {
  uint32_t pass_count;
  uint32_t fail_count;
  bio_diagnostics_t diagnostics;
} bio_enroll_stats_t;

typedef struct {
  uint8_t tally;
} bio_match_tally_t;

typedef struct {
  bio_match_tally_t pass_counts[TEMPLATE_MAX_COUNT];
  uint8_t fail_count;
} bio_match_stats_t;

typedef enum {
  BIO_FINGER_DOWN = 0,
  BIO_FINGER_UP = 1,
} bio_gesture_t;

typedef uint16_t bio_template_id_t;
#define BIO_TEMPLATE_ID_INVALID (UINT16_MAX)

// Initialize third-party libs used for biometrics. Requires stack depth of ~1.5K, so
// must be called from a task with sufficient memory.
bool bio_lib_init(void);
void bio_lib_reset(void);
void bio_hal_init(void);

void bio_wait_for_finger_blocking(bio_gesture_t gesture);

// Retrieve how many fingers have already been enrolled in `count`
void bio_storage_get_template_count(uint32_t* count);

// Returns true if there is at least one enrolled fingerprint.
bool bio_fingerprint_exists(void);

bool bio_fingerprint_index_exists(bio_template_id_t id);

bio_err_t bio_storage_delete_template(bio_template_id_t id);

bool bio_storage_label_save(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN]);
bool bio_storage_label_retrieve(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN]);

bio_diagnostics_t bio_get_diagnostics(void);
void bio_get_and_update_diagnostics(bio_diagnostics_t* diagnostics);

void bio_selftest(bio_selftest_result_t* result);

bool bio_enroll_finger(bio_template_id_t id, char label[BIO_LABEL_MAX_LEN],
                       bio_enroll_stats_t* stats);
secure_bool_t bio_authenticate_finger(secure_bool_t* is_match, bio_template_id_t* match_template_id,
                                      uint32_t comms_timestamp);
void bio_enroll_cancel(void);

bio_match_stats_t* bio_match_stats_get(void);
void bio_match_stats_clear(void);

bool bio_provision_cryptographic_keys(bool dry_run, bool i_realize_this_is_irreversible);
bool bio_security_test(fpc_bep_security_test_result_t* test_result);
bool bio_storage_calibration_data_retrieve(uint8_t** calibration_data, uint16_t* size_out);
bool bio_image_analysis_test(fpc_bep_capture_test_mode_t mode,
                             fpc_bep_analyze_result_t* test_result);
bool bio_sensor_is_secured(bool* secured);
bool bio_sensor_is_otp_locked(bool* locked);
bool bio_image_capture_test(uint8_t** image_out, uint32_t* image_size_out);

// Get the firmware version that enrolled a given template.
kv_result_t bio_template_enrolled_by_version_get(bio_template_id_t id, uint32_t* version_out);
// Record the firmware version that enrolled a given template.
kv_result_t bio_template_enrolled_by_version_store(bio_template_id_t id);

void bio_wipe_state(void);
