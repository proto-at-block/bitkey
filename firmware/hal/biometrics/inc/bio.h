#pragma once

#include "fpc_bep_sensor_security.h"
#include "fpc_bep_sensor_test.h"
#include "secutils.h"

#include <stdbool.h>
#include <stdint.h>

// Total number of supported templates
#define TEMPLATE_MAX_COUNT (3)

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
} bio_enroll_stats_t;

typedef enum {
  BIO_ERR_NONE = 0,
  BIO_ERR_GENERIC = 1,
  BIO_ERR_TEMPLATE_DOESNT_EXIST = 2,
  BIO_ERR_TEMPLATE_INVALID = 3,
} bio_err_t;

typedef uint16_t bio_template_id_t;
#define BIO_TEMPLATE_ID_INVALID (UINT16_MAX)

// Initialize third-party libs used for biometrics. Requires stack depth of ~1.5K, so
// must be called from a task with sufficient memory.
bool bio_lib_init(void);
void bio_lib_reset(void);
void bio_hal_init(void);

void bio_selftest(bio_selftest_result_t* result);

void bio_wait_for_finger_blocking(void);

// Retrieve how many fingers have already been enrolled in `count`
void bio_storage_get_template_count(uint32_t* count);
bio_err_t bio_storage_delete_template(bio_template_id_t id);

// Returns true if there is at least one enrolled fingerprint.
bool bio_fingerprint_exists(void);

// Fingerprint must be down BEFORE enroll or authenticate are called.
// Use bio_wait_for_finger_blocking() to achieve this.
bool bio_enroll_finger(bio_template_id_t id, bio_enroll_stats_t* stats);
secure_bool_t bio_authenticate_finger(secure_bool_t* is_match, bio_template_id_t* match_template_id,
                                      uint32_t comms_timestamp);

bool bio_provision_cryptographic_keys(bool dry_run, bool i_realize_this_is_irreversible);
bool bio_security_test(fpc_bep_security_test_result_t* test_result);

bool bio_storage_calibration_data_retrieve(uint8_t** calibration_data, uint16_t* size_out);

bool bio_image_analysis_test(fpc_bep_capture_test_mode_t mode,
                             fpc_bep_analyze_result_t* test_result);
bool bio_sensor_is_secured(bool* secured);
bool bio_sensor_is_otp_locked(bool* locked);
bool bio_image_capture_test(uint8_t** image_out, uint32_t* image_size_out);

void bio_wipe_state(void);
