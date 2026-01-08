#include "mfgtest_task_port.h"

#include "assert.h"
#include "log.h"
#include "proto_helpers.h"
#include "wallet.pb.h"

void mfgtest_task_port_init(void) {
  // No-op on W1.
}

void mfgtest_task_port_handle_button_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_button_rsp_tag;

  fwpb_mfgtest_button_rsp* rsp = &wallet_rsp->msg.mfgtest_button_rsp;
  rsp->rsp_status = fwpb_mfgtest_button_rsp_mfgtest_button_rsp_status_ERROR;
  rsp->events_count = 0;
  rsp->bypass_enabled = false;

  LOGE("Button commands not supported on W1");
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_show_screen_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_show_screen_rsp_tag;

  fwpb_mfgtest_show_screen_rsp* rsp = &wallet_rsp->msg.mfgtest_show_screen_rsp;
  rsp->rsp_status = fwpb_mfgtest_show_screen_rsp_mfgtest_show_screen_rsp_status_ERROR;

  LOGE("Screen commands not supported on W1");
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_touch_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_touch_test_rsp_tag;

  fwpb_mfgtest_touch_test_rsp* rsp = &wallet_rsp->msg.mfgtest_touch_test_rsp;
  rsp->rsp_status = fwpb_mfgtest_touch_test_rsp_mfgtest_touch_test_rsp_status_ERROR;

  LOGE("Touch commands not supported on W1");
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_coproc_gpio_command(fwpb_wallet_cmd* wallet_cmd) {
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  fwpb_mfgtest_gpio_rsp* rsp = &wallet_rsp->msg.mfgtest_gpio_rsp;

  wallet_rsp->which_msg = fwpb_wallet_rsp_mfgtest_gpio_rsp_tag;
  rsp->output = 0;

  LOGE("Coprocessor GPIO commands not supported on W1");
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void mfgtest_task_port_handle_coproc_gpio_response(ipc_ref_t* message) {
  // Should never be called on W1.
  (void)message;
  ASSERT(false);
}
