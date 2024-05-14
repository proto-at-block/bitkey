#pragma once

#include "bio.h"
#include "fpc_bep_bio.h"
#include "fpc_bep_types.h"
#include "fpc_sensor_spi.h"
#include "key_management.h"

bool bio_capture_image(fpc_bep_image_t* image, uint8_t max_tries);
void bio_perf_init(void);

bool bio_storage_template_save(bio_template_id_t id, fpc_bep_template_t* template);
bool bio_storage_template_retrieve(bio_template_id_t id, fpc_bep_template_t** template_out);
bool bio_storage_calibration_data_exists(void);
bool bio_storage_calibration_data_save(uint8_t* calibration_data, uint32_t size);

bool bio_storage_key_save(uint8_t* wrapped_key, uint32_t size);
bool bio_storage_key_exists(void);
bool bio_storage_key_retrieve_unwrapped(key_handle_t* raw_key_handle);

bool bio_storage_key_plaintext_save(uint8_t* plaintext_key,
                                    uint32_t size);  // Development mode only.

bool bio_storage_timestamp_retrieve(uint32_t* timestamp_out);
bool bio_storage_timestamp_save(uint32_t timestamp);

// Write a user-provided key. Use this to recover a unit who lost a key on the MCU,
// but is paired to a sensor with a key provisioned.
bool bio_write_plaintext_key(const char* hex_encoded_key);  // Development mode only.

fpc_bep_result_t fpc_sensor_wfi(uint16_t timeout_ms, fpc_bep_wfi_check_t enter_wfi,
                                bool enter_wfi_mode);
fpc_bep_result_t fpc_sensor_spi_write_read(uint8_t* data, size_t write_size, size_t read_size,
                                           bool leave_cs_asserted);
void toggle_cs(bool on);

bool bio_quick_selftest(void);

void fpc_biometrics_init(void);

bool bio_update_template(bio_template_id_t id, fpc_bep_template_t* template,
                         uint32_t comms_timestamp);

void fpc_sensor_wfi_cancel(void);
