#include "mfgtest_task.h"

#include "arithmetic.h"
#include "attributes.h"
#include "bio.h"
#include "board_id.h"
#include "filesystem.h"
#include "fpc_malloc.h"
#include "hal_nfc_loopback.h"
#include "hash.h"
#include "hex.h"
#include "ipc.h"
#include "ipc_messages_mfgtest_port.h"
#include "led.h"
#include "log.h"
#include "metadata.h"
#include "mfgtest_task_impl.h"
#include "mfgtest_task_port.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "power.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "sysinfo.h"
#include "ui_messaging.h"
#include "wallet.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

mfgtest_priv_t mfgtest_priv = {
  .queue = NULL,
  .bio_selftest_result = {0},
  .image = NULL,
  .image_size = 0,
  .runin_has_results = false,
  .touch_test_has_result = false,
};

void handle_fingerprint_calibrate_cmd(fwpb_wallet_cmd* UNUSED(cmd), fwpb_wallet_rsp* rsp) {
  uint8_t* calibration_data = NULL;
  bool ok = bio_storage_calibration_data_retrieve(
    &calibration_data, &rsp->msg.mfgtest_fingerprint_rsp.rsp.calibrate.calibration_data.size);

  ASSERT(rsp->msg.mfgtest_fingerprint_rsp.rsp.calibrate.calibration_data.size <
         sizeof(rsp->msg.mfgtest_fingerprint_rsp.rsp.calibrate.calibration_data.bytes));
  memcpy(rsp->msg.mfgtest_fingerprint_rsp.rsp.calibrate.calibration_data.bytes, calibration_data,
         rsp->msg.mfgtest_fingerprint_rsp.rsp.calibrate.calibration_data.size);

  fpc_free(calibration_data);

  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    ok ? fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS
       : fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_calibrate_tag;
}

void handle_image_capture_cmd(fwpb_wallet_cmd* UNUSED(cmd), fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_image_capture_tag;

  if (mfgtest_priv.image != NULL) {
    fpc_free(mfgtest_priv.image);
  }

  if (!bio_image_capture_test(&mfgtest_priv.image, &mfgtest_priv.image_size)) {
    rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
      fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;
    return;
  }
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS;
}

void handle_image_get_capture_cmd(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_image_get_capture_tag;

  if (!mfgtest_priv.image) {
    rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
      fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;
    return;
  }

  uint32_t image_offset = cmd->msg.mfgtest_fingerprint_cmd.cmd.image_get_capture.image_offset;

  const uint32_t chunk_size =
    BLK_MIN(sizeof(rsp->msg.mfgtest_fingerprint_rsp.rsp.image_get_capture.image_chunk.bytes),
            (mfgtest_priv.image_size - image_offset));

  ASSERT(image_offset < mfgtest_priv.image_size);
  ASSERT(image_offset + chunk_size <= mfgtest_priv.image_size);
  memcpy(rsp->msg.mfgtest_fingerprint_rsp.rsp.image_get_capture.image_chunk.bytes,
         &mfgtest_priv.image[image_offset], chunk_size);
  rsp->msg.mfgtest_fingerprint_rsp.rsp.image_get_capture.image_chunk.size = chunk_size;

  rsp->msg.mfgtest_fingerprint_rsp.rsp.image_get_capture.bytes_remaining =
    mfgtest_priv.image_size - image_offset - chunk_size;

  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS;
}

void handle_selftest_start_cmd(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  // Respond immediately, then do the test
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_selftest_start_tag;
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS;
  proto_send_rsp(cmd, rsp);

  bio_selftest(&mfgtest_priv.bio_selftest_result);
}

void handle_selftest_get_result_cmd(fwpb_wallet_cmd* UNUSED(cmd), fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_selftest_get_result_tag;
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS;

  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.irq_test =
    mfgtest_priv.bio_selftest_result.irq_test;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.spi_rw_test =
    mfgtest_priv.bio_selftest_result.spi_rw_test;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.spi_speed_test =
    mfgtest_priv.bio_selftest_result.spi_speed_test;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.image_stress_test =
    mfgtest_priv.bio_selftest_result.image_stress_test;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.reg_stress_test =
    mfgtest_priv.bio_selftest_result.reg_stress_test;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.otp_test =
    mfgtest_priv.bio_selftest_result.otp_test;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.selftest_get_result.prod_test =
    mfgtest_priv.bio_selftest_result.prod_test;
}

void handle_security_mode_cmd(fwpb_wallet_cmd* UNUSED(cmd), fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_security_mode_tag;
  bool check_security_ok =
    bio_sensor_is_secured(&rsp->msg.mfgtest_fingerprint_rsp.rsp.security_mode.security_enabled);
  bool check_otp_ok =
    bio_sensor_is_otp_locked(&rsp->msg.mfgtest_fingerprint_rsp.rsp.security_mode.otp_locked);
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    (check_security_ok && check_otp_ok)
      ? fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS
      : fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;
}

void handle_security_enable_cmd(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_security_enable_tag;

  bool ok = bio_provision_cryptographic_keys(
    cmd->msg.mfgtest_fingerprint_cmd.cmd.security_enable.dry_run, true);
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    ok ? fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS
       : fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;
}

void handle_security_test_cmd(fwpb_wallet_cmd* UNUSED(cmd), fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_security_test_tag;

  fpc_bep_security_test_result_t test_result = {0};
  bool ok = bio_security_test(&test_result);
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    ok ? fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS
       : fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;

  rsp->msg.mfgtest_fingerprint_rsp.rsp.security_test.total_errors = test_result.total_errors;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.security_test.cmac_errors = test_result.cmac_errors;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.security_test.data_errors = test_result.data_errors;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.security_test.other_errors = test_result.other_errors;
  rsp->msg.mfgtest_fingerprint_rsp.rsp.security_test.iterations = test_result.iterations;
}

void handle_image_analysis_cmd(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp) {
  rsp->msg.mfgtest_fingerprint_rsp.which_rsp = fwpb_mfgtest_fingerprint_rsp_image_analysis_tag;

  fpc_bep_capture_test_mode_t mode =
    (fpc_bep_capture_test_mode_t)cmd->msg.mfgtest_fingerprint_cmd.cmd.image_analysis.mode;

  fpc_bep_analyze_result_t test_result;
  bool ok = bio_image_analysis_test(mode, &test_result);
  rsp->msg.mfgtest_fingerprint_rsp.rsp_status =
    ok ? fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_SUCCESS
       : fwpb_mfgtest_fingerprint_rsp_mfgtest_fingerprint_rsp_status_ERROR;

  switch (mode) {
    case FPC_BEP_CAPTURE_TEST_MODE_RESET:
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.num_of_defect_pixels =
        test_result.reset.num_of_defect_pixels;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median = test_result.reset.median;
      break;
    case FPC_BEP_CAPTURE_TEST_MODE_CHECKERBOARD:
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.num_of_defect_pixels =
        test_result.cb.num_of_defect_pixels;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.num_of_defect_pixels_in_detect_zones =
        test_result.cb.num_of_defect_pixels_in_detect_zones;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type1_min =
        test_result.cb.median_type1_min;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type1_max =
        test_result.cb.median_type1_max;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type2_min =
        test_result.cb.median_type2_min;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type2_max =
        test_result.cb.median_type2_max;
      break;
    case FPC_BEP_CAPTURE_TEST_MODE_CHECKERBOARD_INVERTED:
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.num_of_defect_pixels =
        test_result.icb.num_of_defect_pixels;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.num_of_defect_pixels_in_detect_zones =
        test_result.icb.num_of_defect_pixels_in_detect_zones;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type1_min =
        test_result.icb.median_type1_min;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type1_max =
        test_result.icb.median_type1_max;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type2_min =
        test_result.icb.median_type2_min;
      rsp->msg.mfgtest_fingerprint_rsp.rsp.image_analysis.median_type2_max =
        test_result.icb.median_type2_max;
      break;
  }
}

void handle_fingerprint_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_mfgtest_fingerprint_rsp_tag;

  switch (cmd->msg.mfgtest_fingerprint_cmd.which_cmd) {
    case fwpb_mfgtest_fingerprint_cmd_calibrate_tag:
      handle_fingerprint_calibrate_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_image_capture_tag:
      handle_image_capture_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_image_get_capture_tag:
      handle_image_get_capture_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_selftest_start_tag:
      // Respond immediately, then do the test
      handle_selftest_start_cmd(cmd, rsp);
      return;  // Above function sends the protos out.
    case fwpb_mfgtest_fingerprint_cmd_selftest_get_result_tag:
      handle_selftest_get_result_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_security_mode_tag:
      handle_security_mode_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_security_test_tag:
      handle_security_test_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_security_enable_tag:
      handle_security_enable_cmd(cmd, rsp);
      break;
    case fwpb_mfgtest_fingerprint_cmd_image_analysis_tag:
      handle_image_analysis_cmd(cmd, rsp);
      break;
    default:
      LOGE("unknown message: %d", cmd->msg.mfgtest_fingerprint_cmd.which_cmd);
  }

  proto_send_rsp(cmd, rsp);
}

void handle_serial_write_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_mfgtest_serial_write_rsp_tag;
  _Static_assert(
    SYSINFO_SERIAL_NUMBER_LENGTH == sizeof(cmd->msg.mfgtest_serial_write_cmd.serial) - 1,
    "wrong serial size");

  bool ok = false;
  switch (cmd->msg.mfgtest_serial_write_cmd.type) {
    case fwpb_serial_type_ASSY_SERIAL:
      ok = sysinfo_assy_serial_write((char*)cmd->msg.mfgtest_serial_write_cmd.serial,
                                     SYSINFO_SERIAL_NUMBER_LENGTH);
      break;
    case fwpb_serial_type_MLB_SERIAL:
      ok = sysinfo_mlb_serial_write((char*)cmd->msg.mfgtest_serial_write_cmd.serial,
                                    SYSINFO_SERIAL_NUMBER_LENGTH);
      break;
    default:
      LOGE("unknown serial type %d", cmd->msg.mfgtest_serial_write_cmd.type);
      break;
  }
  rsp->msg.mfgtest_serial_write_rsp.rsp_status =
    ok ? fwpb_mfgtest_serial_write_rsp_mfgtest_serial_write_status_SUCCESS
       : fwpb_mfgtest_serial_write_rsp_mfgtest_serial_write_status_ERROR;

  proto_send_rsp(cmd, rsp);
}

void handle_gpio_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_mfgtest_gpio_cmd* cmd = &wallet_cmd->msg.mfgtest_gpio_cmd;
  if (cmd->mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    mfgtest_task_port_handle_coproc_gpio_command(wallet_cmd);
    return;
  }

  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_gpio_rsp_tag;

  fwpb_mfgtest_gpio_rsp* rsp = &wallet_rsp->msg.mfgtest_gpio_rsp;
  const mcu_gpio_config_t gpio = {.port = cmd->port, .pin = cmd->pin};

  switch (cmd->action) {
    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_READ:
      rsp->output = mcu_gpio_read(&gpio);
      break;
    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_SET:
      mcu_gpio_set(&gpio);
      rsp->output = mcu_gpio_read(&gpio);
      break;
    case fwpb_mfgtest_gpio_cmd_mfgtest_gpio_action_CLEAR:
      mcu_gpio_clear(&gpio);
      rsp->output = mcu_gpio_read(&gpio);
      break;
    default:
      LOGE("unknown gpio action %d", cmd->action);
      break;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void handle_battery_variant(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_battery_variant_rsp_tag;

  fwpb_mfgtest_battery_variant_cmd* cmd = &wallet_cmd->msg.mfgtest_battery_variant_cmd;
  fwpb_mfgtest_battery_variant_rsp* rsp = &wallet_rsp->msg.mfgtest_battery_variant_rsp;

  if (power_set_battery_variant(cmd->variant)) {
    rsp->status = fwpb_mfgtest_battery_variant_rsp_mfgtest_battery_variant_status_SUCCESS;
  } else {
    rsp->status = fwpb_mfgtest_battery_variant_rsp_mfgtest_battery_variant_status_ERROR;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void handle_runin_get_data_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_runin_get_data_rsp_tag;
  fwpb_mfgtest_runin_get_data_rsp* rsp = &wallet_rsp->msg.mfgtest_runin_get_data_rsp;

  // Return stored results from display_controller IPC callback
  if (!mfgtest_priv.runin_has_results) {
    rsp->rsp_status = fwpb_mfgtest_runin_get_data_rsp_mfgtest_runin_get_data_rsp_status_NO_DATA;
  } else {
    rsp->discharging = !mfgtest_priv.runin_results.plugged_in;
    rsp->loop_count = mfgtest_priv.runin_results.loop_count;
    rsp->initial_soc = mfgtest_priv.runin_results.initial_soc;
    rsp->final_soc = mfgtest_priv.runin_results.final_soc;
    rsp->min_soc = mfgtest_priv.runin_results.min_soc;
    rsp->duration_ms = mfgtest_priv.runin_results.elapsed_ms;
    rsp->phantom_button_events = mfgtest_priv.runin_results.button_events;
    rsp->phantom_touch_events = mfgtest_priv.runin_results.touch_events;
    rsp->phantom_captouch_events = mfgtest_priv.runin_results.captouch_events;
    rsp->phantom_fingerprint_events = mfgtest_priv.runin_results.fingerprint_events;

    if (mfgtest_priv.runin_results.success) {
      rsp->rsp_status = fwpb_mfgtest_runin_get_data_rsp_mfgtest_runin_get_data_rsp_status_SUCCESS;
    } else {
      rsp->rsp_status = fwpb_mfgtest_runin_get_data_rsp_mfgtest_runin_get_data_rsp_status_FAILED;
    }
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

static void handle_spi_loopback_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_spi_loopback_rsp_tag;

  fwpb_mfgtest_spi_loopback_cmd* cmd = &wallet_cmd->msg.mfgtest_spi_loopback_cmd;
  fwpb_mfgtest_spi_loopback_rsp* rsp = &wallet_rsp->msg.mfgtest_spi_loopback_rsp;

  if (cmd->instance !=
      fwpb_mfgtest_spi_loopback_cmd_mfgtest_spi_loopback_cmd_instance_FINGERPRINT) {
    rsp->rsp_status =
      fwpb_mfgtest_spi_loopback_rsp_mfgtest_spi_loopback_rsp_status_INVALID_INSTANCE;
    rsp->data.size = 0;
  } else if (cmd->data.size == 0) {
    rsp->rsp_status = fwpb_mfgtest_spi_loopback_rsp_mfgtest_spi_loopback_rsp_status_ERROR;
    rsp->data.size = 0;
  } else {
    // `bio_sensor_write_read()` reads/writes into the provided buffer, so we
    // use the response buffer instead of the command buffer.
    memcpy(rsp->data.bytes, cmd->data.bytes, cmd->data.size);

    if (bio_sensor_write_read(rsp->data.bytes, cmd->data.size)) {
      // Even if the transfer is successful, the bytes may differ, which
      // indicates a failure.
      if (memcmp(rsp->data.bytes, cmd->data.bytes, cmd->data.size) == 0) {
        rsp->rsp_status = fwpb_mfgtest_spi_loopback_rsp_mfgtest_spi_loopback_rsp_status_SUCCESS;
      } else {
        rsp->rsp_status = fwpb_mfgtest_spi_loopback_rsp_mfgtest_spi_loopback_rsp_status_FAIL;
      }
      rsp->data.size = cmd->data.size;
    } else {
      rsp->rsp_status = fwpb_mfgtest_spi_loopback_rsp_mfgtest_spi_loopback_rsp_status_ERROR;
      rsp->data.size = 0;
    }
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

static void handle_nfc_loopback_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_nfc_loopback_rsp_tag;
  fwpb_mfgtest_nfc_loopback_cmd* cmd = &wallet_cmd->msg.mfgtest_nfc_loopback_cmd;
  fwpb_mfgtest_nfc_loopback_rsp* rsp = &wallet_rsp->msg.mfgtest_nfc_loopback_rsp;

  switch (cmd->cmd) {
    case fwpb_mfgtest_nfc_loopback_cmd_mfgtest_nfc_loopback_cmd_type_START: {
      const uint32_t timeout_ms = cmd->timeout_ms;
      const uint32_t delay_ms = cmd->delay_ms;
      const uint32_t test = cmd->test;

      rsp->rsp_status = fwpb_mfgtest_nfc_loopback_rsp_mfgtest_nfc_loopback_rsp_status_SUCCESS;

      if (((test == fwpb_mfgtest_nfc_loopback_test_type_NFC_LOOPBACK_TEST_A) ||
           (test == fwpb_mfgtest_nfc_loopback_test_type_NFC_LOOPBACK_TEST_B)) &&
          (hal_nfc_get_mode() == HAL_NFC_MODE_LISTENER)) {
        // Response sent here to allow test to start.
        proto_send_rsp(wallet_cmd, wallet_rsp);

        // Wait the delay MS.
        rtos_thread_sleep(delay_ms);

        // Start the loopback test.
        hal_nfc_loopback_test_start(
          (test == fwpb_mfgtest_nfc_loopback_test_type_NFC_LOOPBACK_TEST_A)
            ? HAL_NFC_MODE_LOOPBACK_A
            : HAL_NFC_MODE_LOOPBACK_B,
          timeout_ms);
        return;
      }

      rsp->rsp_status = fwpb_mfgtest_nfc_loopback_rsp_mfgtest_nfc_loopback_rsp_status_ERROR;
      break;
    }

    case fwpb_mfgtest_nfc_loopback_cmd_mfgtest_nfc_loopback_cmd_type_REQUEST_DATA:
      rsp->rsp_status = hal_nfc_loopback_test_passed()
                          ? fwpb_mfgtest_nfc_loopback_rsp_mfgtest_nfc_loopback_rsp_status_SUCCESS
                          : fwpb_mfgtest_nfc_loopback_rsp_mfgtest_nfc_loopback_rsp_status_FAILED;
      break;

    case fwpb_mfgtest_nfc_loopback_cmd_mfgtest_nfc_loopback_cmd_type_UNSPECIFIED:
      // 'break' intentionally omitted.

    default:
      // Unsupported command.
      rsp->rsp_status = fwpb_mfgtest_nfc_loopback_rsp_mfgtest_nfc_loopback_rsp_status_ERROR;
      break;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

static void handle_mfgtest_board_id_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_board_id_rsp_tag;

  fwpb_mfgtest_board_id_rsp* rsp = &wallet_rsp->msg.mfgtest_board_id_rsp;
  if (board_id_read((uint8_t*)&rsp->board_id)) {
    rsp->rsp_status = fwpb_mfgtest_board_id_rsp_mfgtest_board_id_rsp_status_SUCCESS;
  } else {
    rsp->rsp_status = fwpb_mfgtest_board_id_rsp_mfgtest_board_id_rsp_status_FAILED;
    rsp->board_id = 0;
  }
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

static void handle_charger_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_charger_rsp_tag;

  fwpb_mfgtest_charger_cmd* cmd = &wallet_cmd->msg.mfgtest_charger_cmd;
  fwpb_mfgtest_charger_rsp* rsp = &wallet_rsp->msg.mfgtest_charger_rsp;

  switch (power_get_charger_id()) {
    case POWER_CHARGER_MAX77734:
      rsp->charger_id = fwpb_mfgtest_charger_rsp_mfgtest_charger_id_MAX77734;
      break;

    default:
      rsp->charger_id = fwpb_mfgtest_charger_rsp_mfgtest_charger_id_UNKNOWN_CHARGER;
      break;
  }

  switch (cmd->cmd_id) {
    case fwpb_mfgtest_charger_cmd_mfgtest_charger_cmd_id_READ_REGISTERS: {
      rsp->rsp_status = fwpb_mfgtest_charger_rsp_mfgtest_charger_rsp_status_SUCCESS;
      rsp->registers_count = power_get_charger_register_count();
      for (uint8_t i = 0; i < BLK_MIN(rsp->registers_count, ARRAY_SIZE(rsp->registers)); i++) {
        power_read_charger_register(i, (uint8_t*)&rsp->registers[i].offset,
                                    (uint8_t*)&rsp->registers[i].value);
      }
      break;
    }

    case fwpb_mfgtest_charger_cmd_mfgtest_charger_cmd_id_GET_INFO: {
      rsp->rsp_status = fwpb_mfgtest_charger_rsp_mfgtest_charger_rsp_status_SUCCESS;
      rsp->charging = power_is_charging();
      rsp->registers_count = 0;

      switch (power_get_charger_mode()) {
        case POWER_CHARGER_MODE_OFF:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_OFF;
          break;

        case POWER_CHARGER_MODE_PREQUAL:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_PREQUALIFICATION;
          break;

        case POWER_CHARGER_MODE_CC:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_FAST_CHARGE_CONSTANT_CURRENT;
          break;

        case POWER_CHARGER_MODE_JEITA_CC:
          rsp->mode =
            fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_JEITA_MODIFIED_FAST_CHARGE_CONSTANT_CURRENT;
          break;

        case POWER_CHARGER_MODE_CV:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_FAST_CHARGE_CONSTANT_VOLTAGE;
          break;

        case POWER_CHARGER_MODE_JEITA_CV:
          rsp->mode =
            fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_JEITA_MODIFIED_FAST_CHARGE_CONSTANT_VOLTAGE;
          break;

        case POWER_CHARGER_MODE_TOP_OFF:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_TOP_OFF;
          break;

        case POWER_CHARGER_MODE_JEITA_TOP_OFF:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_JEITA_MODIFIED_TOP_OFF;
          break;

        case POWER_CHARGER_MODE_DONE:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_DONE;
          break;

        case POWER_CHARGER_MODE_JEITA_DONE:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_JEITA_MODIFIED_DONE;
          break;

        case POWER_CHARGER_MODE_PREQUAL_TIMEOUT:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_PREQUALIFICATION_TIMEOUT_FAULT;
          break;

        case POWER_CHARGER_MODE_FAST_CHARGE_TIMEOUT:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_FAST_CHARGE_TIMEOUT_FAULT;
          break;

        case POWER_CHARGER_MODE_TEMP_FAULT:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_BATTERY_TEMP_FAULT;
          break;

        case POWER_CHARGER_MODE_INVALID:
          // 'break' intentionally omitted.

        default:
          rsp->mode = fwpb_mfgtest_charger_rsp_mfgtest_charger_mode_UNKNOWN;
          rsp->rsp_status = fwpb_mfgtest_charger_rsp_mfgtest_charger_rsp_status_ERROR;
          break;
      }
      break;
    }

    case fwpb_mfgtest_charger_cmd_mfgtest_charger_cmd_id_UNSPECIFIED:
      // 'break' intentionally omitted.

    default:
      rsp->rsp_status = fwpb_mfgtest_charger_rsp_mfgtest_charger_rsp_status_ERROR;
      break;
  }

  proto_send_rsp(wallet_cmd, wallet_rsp);
}

static void handle_runin_complete_internal(ipc_ref_t* message) {
  // IPC callback from display_controller when run-in test completes
  const mfgtest_runin_complete_internal_t* results =
    (const mfgtest_runin_complete_internal_t*)message->object;

  LOGI(
    "[MfgTest] Run-in complete: loops=%lu battery=%lu%%->%lu%% min=%lu%% duration=%lums usb=%s "
    "phantom(btn=%lu touch=%lu fp=%lu)",
    (unsigned long)results->loop_count,             //
    (unsigned long)results->initial_soc,            //
    (unsigned long)results->final_soc,              //
    (unsigned long)results->min_soc,                //
    (unsigned long)results->elapsed_ms,             //
    results->plugged_in ? "plugged" : "unplugged",  //
    (unsigned long)results->button_events,          //
    (unsigned long)results->touch_events,           //
    (unsigned long)results->fingerprint_events);

  // Store results for get_data command
  memcpy(&mfgtest_priv.runin_results, results, sizeof(mfgtest_priv.runin_results));
  mfgtest_priv.runin_has_results = true;
}

static void handle_touch_test_result_internal(ipc_ref_t* message) {
  // IPC callback from display_controller when touch test completes.
  const mfgtest_touch_test_result_internal_t* result =
    (const mfgtest_touch_test_result_internal_t*)message->object;

  memcpy(&mfgtest_priv.touch_test_result, result, sizeof(*result));
  mfgtest_priv.touch_test_has_result = true;
}

void mfgtest_thread(void* UNUSED(args)) {
  bio_lib_init();
  mfgtest_task_port_init();

  power_fast_charge();

  UI_SET_IDLE_STATE(UI_EVENT_IDLE);

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(mfgtest_port, &message);

    switch (message.tag) {
      case IPC_PROTO_MFGTEST_FINGERPRINT_CMD:
        handle_fingerprint_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_SERIAL_WRITE_CMD:
        handle_serial_write_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_GPIO_CMD:
        handle_gpio_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_BATTERY_VARIANT_CMD:
        handle_battery_variant(&message);
        break;
      case IPC_PROTO_MFGTEST_RUNIN_GET_DATA_CMD:
        handle_runin_get_data_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_BUTTON_CMD:
        mfgtest_task_port_handle_button_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_SHOW_SCREEN_CMD:
        mfgtest_task_port_handle_show_screen_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_SPI_LOOPBACK_CMD:
        handle_spi_loopback_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_NFC_LOOPBACK_CMD:
        handle_nfc_loopback_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_BOARD_ID_CMD:
        handle_mfgtest_board_id_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_TOUCH_TEST_CMD:
        mfgtest_task_port_handle_touch_cmd(&message);
        break;
      case IPC_PROTO_MFGTEST_CHARGER_CMD:
        handle_charger_cmd(&message);
        break;
      case IPC_MFGTEST_RUNIN_COMPLETE_INTERNAL:
        handle_runin_complete_internal(&message);
        break;
      case IPC_MFGTEST_TOUCH_TEST_RESULT_INTERNAL:
        handle_touch_test_result_internal(&message);
        break;
      case IPC_MFGTEST_COPROC_GPIO_RESPONSE:
        mfgtest_task_port_handle_coproc_gpio_response(&message);
        break;
      default:
        LOGE("unknown message %ld", message.tag);
    }
  }
}

void mfgtest_task_create(void) {
  mfgtest_priv.queue = rtos_queue_create(mfgtest_queue, ipc_ref_t, 4);
  ASSERT(mfgtest_priv.queue);
  ipc_register_port(mfgtest_port, mfgtest_priv.queue);

  rtos_thread_t* mfgtest_thread_handle =
    rtos_thread_create(mfgtest_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 8192);
  ASSERT(mfgtest_thread_handle);
}
