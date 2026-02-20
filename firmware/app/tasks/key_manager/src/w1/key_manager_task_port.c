#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "wallet.pb.h"

void key_manager_task_handle_uxc_session_response(ipc_ref_t* message) {
  (void)message;
  LOGE("Unexpected call to UXC session response handler.");
}

void key_manager_task_handle_uxc_boot(void) {
  LOGE("Unexpected call to UXC boot handler.");
}

void key_manager_task_port_handle_get_address(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_address_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("getAddress not supported on W1");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_verify_keys_and_build_descriptor(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_verify_keys_and_build_descriptor_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("verify_keys_and_build_descriptor not supported on W1");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_register_listeners(void) {}
