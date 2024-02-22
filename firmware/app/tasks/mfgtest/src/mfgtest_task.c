#include "mfgtest_task.h"

#include "animation.h"
#include "attributes.h"
#include "bio.h"
#include "filesystem.h"
#include "fpc_malloc.h"
#include "hash.h"
#include "hex.h"
#include "ipc.h"
#include "led.h"
#include "log.h"
#include "metadata.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "power.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "sysinfo.h"
#include "wallet.h"
#include "wallet.pb.h"

#include <stdbool.h>

static struct {
  rtos_queue_t* queue;
  bio_selftest_result_t bio_selftest_result;
  uint8_t* image;
  uint32_t image_size;
} mfgtest_priv = {
  .queue = NULL,
  .bio_selftest_result = {0},
  .image = NULL,
  .image_size = 0,
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
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_gpio_rsp_tag;

  fwpb_mfgtest_gpio_cmd* cmd = &wallet_cmd->msg.mfgtest_gpio_cmd;
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

void mfgtest_thread(void* UNUSED(args)) {
  bio_hal_init();
  bio_lib_init();

  power_fast_charge();

  static led_set_rest_animation_t LED_TASK_DATA rest_msg = {.animation = (uint32_t)ANI_REST};
  ipc_send(led_port, &rest_msg, sizeof(rest_msg), IPC_LED_SET_REST_ANIMATION);

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
