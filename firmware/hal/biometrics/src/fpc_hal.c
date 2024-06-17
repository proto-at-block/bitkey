#include "aes.h"
#include "arithmetic.h"
#include "attributes.h"
#include "bio.h"
#include "bio_impl.h"
#include "filesystem.h"
#include "log.h"
#include "mcu.h"
#include "mcu_dma.h"
#include "secure_rng.h"
#include "security_config.h"
#include "sysevent.h"

// clang-format off
#include <stddef.h>  // FPC forgot to include stddef.h in some of their headers,
                     // so this include must come first.
// clang-format on
#include "assert.h"
#include "bio_platform_config.h"
#include "fpc_bep_algorithms.h"
#include "fpc_bep_bio.h"
#include "fpc_bep_calibration.h"
#include "fpc_bep_image.h"
#include "fpc_bep_sensor.h"
#include "fpc_bep_sensor_security.h"
#include "fpc_bep_sensor_test.h"
#include "fpc_bep_types.h"
#include "fpc_malloc.h"
#include "fpc_sensor_spi.h"
#include "fpc_timebase.h"

extern bio_config_t fpc_config;
extern security_config_t security_config;

// This being externed is evidence that fpc_biometrics and fpc_hal have improper
// separation of concerns. We should fix that if we end up writing more biometrics code.
extern uint32_t number_required_samples;

static struct {
  const fpc_bep_sensor_t* bep_sensor;
  const fpc_bep_algorithm_t* algorithm;
  mcu_spi_state_t spi_state;
  uint32_t sensor_hw_detect_poll_period_ms;
  uint32_t finger_detect_timeout_ms;
  key_handle_t mac_key;
  uint8_t mac_key_buf[AES_128_LENGTH_BYTES];
  uint8_t* calibration_data;
  uint32_t calibration_data_size;
} fpc_priv = {
  .bep_sensor = &SENSOR,    // SENSOR is defined at compile time
  .algorithm = &ALGORITHM,  // ditto
  .spi_state = {0},
  .sensor_hw_detect_poll_period_ms = 20,
  .finger_detect_timeout_ms = 2000,
  .mac_key_buf = {0},
  .mac_key =
    {
      .alg = ALG_AES_128,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key.bytes = &fpc_priv.mac_key_buf[0],
      .key.size = sizeof(fpc_priv.mac_key_buf),
    },
  .calibration_data = NULL,
  .calibration_data_size = 0,
};

static const uint8_t nist_sp800_58b_test_key[AES_128_LENGTH_BYTES] = {
  0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09, 0xcf, 0x4f, 0x3c,
};

void toggle_cs(const bool on) {
  mcu_gpio_output_set(&fpc_config.spi_config.cs, !on);
}

void bio_hal_init(void) {
  mcu_err_t result = mcu_spi_init(&fpc_priv.spi_state, &fpc_config.spi_config);
  ASSERT_LOG(result == MCU_ERROR_OK, "%d", result);

  mcu_gpio_configure(&fpc_config.spi_config.cs, false);  // CS
  mcu_gpio_configure(&fpc_config.rst, false);
  exti_enable(&fpc_config.exti);

  bio_perf_init();
  fpc_timebase_init();
  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);
}

bool bio_lib_init(void) {
#if 0
  if (!bio_quick_selftest()) {
    return false;
  }
#endif

  // Get max template size based on algorithm.
  size_t template_max_size;
  fpc_bep_result_t result =
    fpc_bep_algorithm_get_max_template_size(fpc_priv.algorithm, &template_max_size);
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("failed to get fpc template size. error %i", result);
    return false;
  }

  if (bio_storage_calibration_data_exists()) {
    uint16_t size_out;
    if (!bio_storage_calibration_data_retrieve(&fpc_priv.calibration_data, &size_out)) {
      LOGE("Couldn't retrieve calibration data");
    } else {
      // Must not be null in this case.
      ASSERT(fpc_priv.calibration_data != NULL);
    }
  }

  if (bio_storage_key_exists()) {
    ASSERT(bio_storage_key_retrieve_unwrapped(&fpc_priv.mac_key));
  }

  toggle_cs(false);
  result = fpc_bep_sensor_init(fpc_priv.bep_sensor, fpc_priv.calibration_data, NULL);
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("failed to init fpc sensor. error %i", result);
    return false;
  }

  if (fpc_priv.calibration_data == NULL) {
    LOGI("First time initialization, storing sensor calibration data");
    result = fpc_bep_cal_get(&fpc_priv.calibration_data, (size_t*)&fpc_priv.calibration_data_size);
    if (result != FPC_BEP_RESULT_OK) {
      LOGE("failed to calibrate fpc. error %i", result);
      return false;
    }
    ASSERT(
      bio_storage_calibration_data_save(fpc_priv.calibration_data, fpc_priv.calibration_data_size));
  }

  fpc_biometrics_init();

  return true;
}

void bio_lib_reset(void) {
  fpc_free(fpc_priv.calibration_data);
}

void fpc_biometrics_init(void) {
  fpc_bep_bio_param_t param;
  fpc_bep_result_t result = fpc_bep_bio_get_recommended_param(fpc_priv.algorithm, &param);
  ASSERT_LOG(result == FPC_BEP_RESULT_OK, "%d", result);

  number_required_samples = param.enroll.nbr_of_images;

  // By default, the max number of immobile touches is 4. We want to be more aggressive, and
  // ensure a high quality template is enrolled, so we set it to 0 to enforce all samples are
  // good.
  param.enroll.max_nbr_of_immobile_touches = 0;

  result = fpc_bep_bio_init(&param, fpc_priv.algorithm);
  ASSERT_LOG(result == FPC_BEP_RESULT_OK, "%d", result);
}

void fpc_sensor_spi_init(uint32_t UNUSED(speed_hz)) {}

void fpc_sensor_spi_reset(const bool state) {
  mcu_gpio_output_set(&fpc_config.rst, !state);
}

fpc_bep_result_t fpc_sensor_spi_write_read(uint8_t* data, size_t write_size, size_t read_size,
                                           bool leave_cs_asserted) {
  if (!data) {
    return FPC_BEP_RESULT_IO_ERROR;
  }

  toggle_cs(true);

  fpc_timebase_delay_ms(1);
  int remaining = write_size + read_size;
  int off = 0;
  uint8_t* tx_buf = data;
  uint8_t* rx_buf = data;
  while (remaining > 0) {
    int count = BLK_MIN(remaining, MCU_DMA_MAX_XFER_COUNT);
    mcu_err_t result =
      mcu_spi_master_transfer_b(&fpc_priv.spi_state, tx_buf + off, rx_buf + off, count);
    if (result != MCU_ERROR_OK) {
      LOGE("SPI transfer failed (%zu) size: %d", result, write_size + read_size);
      ASSERT(false);
    }

    off += count;
    remaining -= count;
  }

  if (!leave_cs_asserted) {
    toggle_cs(false);
  }

  return FPC_BEP_RESULT_OK;
}

void* fpc_malloc(size_t size) {
  return rtos_malloc(size);
}

void fpc_free(void* data) {
  rtos_free(data);
}

void fpc_timebase_init(void) {}

void fpc_timebase_delay_ms(uint32_t ms) {
  rtos_thread_sleep(ms);
}

void fpc_log_diag(const char* str) {
  printf(str);
}

uint32_t fpc_timebase_get_tick(void) {
  return rtos_thread_systime();
}

fpc_bep_spi_duplex_mode_t fpc_sensor_spi_get_duplex_mode(void) {
  return FPC_BEP_SPI_FULL_DUPLEX;
}

bool fpc_sensor_spi_check_irq(void) {
  return exti_pending(&fpc_config.exti);
}

bool fpc_sensor_spi_read_irq(void) {
  if (exti_pending(&fpc_config.exti)) {
    exti_clear(&fpc_config.exti);
    return true;
  }

  return false;
}

fpc_bep_result_t fpc_sensor_wfi(uint16_t timeout_ms, fpc_bep_wfi_check_t UNUSED(enter_wfi),
                                bool enter_wfi_mode) {
  if (enter_wfi_mode) {
    uint32_t timeout = timeout_ms;
    if (timeout_ms == 0) {
      timeout = RTOS_EVENT_GROUP_TIMEOUT_MAX;
    }

    if (exti_wait(&fpc_config.exti, timeout, false)) {
      return FPC_BEP_RESULT_OK;
    } else {
      return FPC_BEP_RESULT_TIMEOUT;
    }
  }

  return FPC_BEP_RESULT_OK;
}

void fpc_sensor_wfi_cancel(void) {
  exti_signal(&fpc_config.exti);
}

bool bio_security_test(fpc_bep_security_test_result_t* test_result) {
  bool locked;
  fpc_bep_result_t result = fpc_bep_sensor_security_get_otp_locked_status(&locked);
  if (result != FPC_BEP_RESULT_OK || locked != true) {
    LOGE("Unexpected OTP lock status: %d (%d)", locked, result);
    return false;
  }

  fpc_bep_sensor_security_mode_t mode;
  result = fpc_bep_sensor_security_get_mode(&mode);
  if (result != FPC_BEP_RESULT_OK || mode != FPC_BEP_SENSOR_SECURITY_MODE_MAC) {
    LOGE("Unexpected security mode: %d (%d)", mode, result);
    return false;
  }

  fpc_priv.mac_key.key.bytes = (uint8_t*)nist_sp800_58b_test_key;

  result = fpc_bep_sensor_security_run_test(FPC_BEP_SENSOR_SECURITY_MODE_MAC,
                                            (fpc_bep_security_test_result_t*)test_result);
  if (result != FPC_BEP_RESULT_OK || test_result->total_errors > 0) {
    LOGE("Sensor security test error: %d", result);
    LOGE("Test result: %ld, %ld, %ld, %ld, %ld", test_result->total_errors,
         test_result->cmac_errors, test_result->data_errors, test_result->other_errors,
         test_result->iterations);
    fpc_priv.mac_key.key.bytes = fpc_priv.mac_key_buf;
    return false;
  }

  fpc_priv.mac_key.key.bytes = fpc_priv.mac_key_buf;
  return true;
}

static NO_OPTIMIZE bool bio_generate_key(key_handle_t* wrapped_key, key_handle_t* plaintext_key) {
#if BIO_DEV_MODE
  SECURE_IF_FAILIN(security_config.is_production == SECURE_TRUE) {
    LOGE("BIO_DEV_MODE set in a production build");
    return false;
  }

  // In development, use a fixed key and import into the SE.
  memcpy(plaintext_key->key.bytes, security_config.biometrics_mac_key, AES_128_LENGTH_BYTES);

  if (!import_key(plaintext_key, wrapped_key)) {
    LOGE("Failed to import key");
    return false;
  }
#else
  // In prod, generate a random key in the SE and export it so that we can
  // write it to the FP sensor.
  if (!generate_key(wrapped_key)) {
    LOGE("Failed to generate key");
    return false;
  }
  if (!export_key(wrapped_key, plaintext_key)) {
    LOGE("Failed to export key");
    return false;
  }
#endif
  return true;
}

bool bio_provision_cryptographic_keys(bool dry_run, bool i_realize_this_is_irreversible) {
  ASSERT(i_realize_this_is_irreversible);

  // Check that this device hasn't been provisioned yet
  fpc_bep_sensor_security_mode_t mode;
  fpc_bep_result_t result = fpc_bep_sensor_security_get_mode(&mode);
  if (result != FPC_BEP_RESULT_OK || mode != FPC_BEP_SENSOR_SECURITY_MODE_NONE) {
    LOGE("Unexpected security mode: %d (%d)", mode, result);
    return false;
  }

  bool locked;
  result = fpc_bep_sensor_security_get_otp_locked_status(&locked);
  if (result != FPC_BEP_RESULT_OK || locked != false) {
    LOGE("Unexpected OTP lock status: %d (%d)", locked, result);
    return false;
  }

  // Generate wrapped key
  uint8_t wrapped_key_bytes[AES_128_LENGTH_BYTES + SE_WRAPPED_KEY_OVERHEAD] = {0};
  key_handle_t key = {
    .alg = ALG_AES_128,
    .storage_type = KEY_STORAGE_EXTERNAL_WRAPPED,
    .key.bytes = wrapped_key_bytes,
    .key.size = sizeof(wrapped_key_bytes),
  };

  // Export key so we can send it into FPC sensor
  uint8_t key_bytes[AES_128_LENGTH_BYTES] = {0};
  key_handle_t key_plaintext = {
    .alg = ALG_AES_128,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = key_bytes,
    .key.size = sizeof(key_bytes),
  };

  if (!bio_generate_key(&key, &key_plaintext)) {
    LOGE("Failed to generate key");
    return false;
  }

#if BIO_DEV_MODE
  LOGW("FPC provisioning info:");
  LOGW("  key bytes: ");
  for (uint32_t i = 0; i < sizeof(key_bytes); i++) {
    printf("%02x", key_plaintext.key.bytes[i]);
  }
  printf("\n");
  LOGW("  mode: %d\n", FPC_BEP_SENSOR_SECURITY_MODE_MAC);
  LOGW("!! Be sure to save this key for development units! If it's lost, the sensor is bricked.");
  LOGW("!! The key has been written to flash.");
  bio_storage_key_plaintext_save(key_plaintext.key.bytes, key_plaintext.key.size);
#endif

  if (dry_run) {
    return true;
  }

  // Save key
  if (!bio_storage_key_save(key.key.bytes, key.key.size)) {
    LOGE("Failed to save key");
    return false;
  }

  // Provision
  result = fpc_bep_sensor_security_set_mode(FPC_BEP_SENSOR_SECURITY_MODE_MAC,
                                            key_plaintext.key.bytes, NULL, 0);
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("Failed to set security mode: %d", result);
    return false;
  }

  return true;
}

// For development; writes a user-controlled key.
bool bio_write_plaintext_key(const char* hex_encoded_key) {
  if (!hex_encoded_key)
    return false;

  const size_t length = strnlen(hex_encoded_key, AES_128_LENGTH_BYTES * 2);
  if (length != (AES_128_LENGTH_BYTES * 2)) {
    LOGE("Key must be 16 bytes, but is %zu", length);
    return false;
  }

  // Hex decode
  char* off = (char*)hex_encoded_key;
  uint8_t key_bytes[AES_128_LENGTH_BYTES] = {0};
  for (size_t i = 0; i < AES_128_LENGTH_BYTES; i++) {
    sscanf(off, "%2x", (unsigned int*)&key_bytes[i]);
    off += 2;
  }

  key_handle_t key_plaintext = {
    .alg = ALG_AES_128,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = key_bytes,
    .key.size = sizeof(key_bytes),
  };

  // Write plaintext key
  bio_storage_key_plaintext_save(key_plaintext.key.bytes, key_plaintext.key.size);

  // Wrap key
  uint8_t wrapped_key_bytes[AES_128_LENGTH_BYTES + SE_WRAPPED_KEY_OVERHEAD] = {0};
  key_handle_t key = {
    .alg = ALG_AES_128,
    .storage_type = KEY_STORAGE_EXTERNAL_WRAPPED,
    .key.bytes = wrapped_key_bytes,
    .key.size = sizeof(wrapped_key_bytes),
  };

  if (!import_key(&key_plaintext, &key)) {
    LOGE("Failed to import key");
    return false;
  }

  // Write wrapped key
  bio_storage_key_save(wrapped_key_bytes, key.key.size);

  return true;
}

bool bio_image_analysis_test(fpc_bep_capture_test_mode_t mode,
                             fpc_bep_analyze_result_t* test_result) {
  bool ret = false;

  fpc_bep_image_t* image = fpc_bep_image_new();
  fpc_bep_result_t result = fpc_bep_sensor_prod_test_mode_capture(image, mode);
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("Failed to do prod test image capture: %d", result);
    goto out;
  }

  result = fpc_bep_sensor_prod_test_mode_analyze(image, mode, test_result);
  if (result != FPC_BEP_RESULT_OK) {
    LOGE("Failed to do analyze prod test results");
    goto out;
  }

  ret = true;

out:
  fpc_bep_image_delete(&image);
  return ret;
}

bool bio_sensor_is_secured(bool* secured) {
  fpc_bep_sensor_security_mode_t mode;
  fpc_bep_result_t result = fpc_bep_sensor_security_get_mode(&mode);
  if (result != FPC_BEP_RESULT_OK) {
    return false;
  }
  *secured = (mode == FPC_BEP_SENSOR_SECURITY_MODE_MAC);
  return true;
}

bool bio_sensor_is_otp_locked(bool* locked) {
  return fpc_bep_sensor_security_get_otp_locked_status(locked) == FPC_BEP_RESULT_OK;
}

void fpc_sensor_security_get_cmac_challenge(uint8_t* challenge) {
  // Challenge is 128 bits.
  ASSERT(crypto_random(challenge, 16));
}

void fpc_sensor_security_aes(uint8_t* block) {
  ASSERT(aes_one_block_encrypt(block, block, AES_128_LENGTH_BYTES, &fpc_priv.mac_key));
}

void fpc_sensor_security_aes_cbc_init(const uint8_t* UNUSED(iv)) {}
void fpc_sensor_security_aes_cbc_decrypt(uint8_t* UNUSED(message), size_t UNUSED(size)) {}
void fpc_sensor_security_aes_cmac_init(void) {}
void fpc_sensor_security_aes_release(void) {}

fpc_bep_result_t fpc_sensor_security_aes_cmac_external(const uint8_t* challenge, const uint8_t* iv,
                                                       const uint8_t* message,
                                                       size_t message_length, uint8_t* mac) {
  const uint8_t challenge_length = 16;
  const uint8_t iv_length = iv ? 16 : 0;

  uint32_t total_message_length = challenge_length + iv_length + message_length;
  uint8_t* total_message = fpc_malloc(total_message_length);
  ASSERT(total_message != NULL);

  memcpy(total_message, challenge, challenge_length);
  memcpy(total_message + challenge_length, iv, iv_length);
  memcpy(total_message + challenge_length + iv_length, message, message_length);

  fpc_bep_result_t result = FPC_BEP_RESULT_OK;
  if (!aes_cmac(total_message, total_message_length, mac, &fpc_priv.mac_key)) {
    result = FPC_BEP_RESULT_GENERAL_ERROR;
  }

  fpc_free(total_message);
  return result;
}
