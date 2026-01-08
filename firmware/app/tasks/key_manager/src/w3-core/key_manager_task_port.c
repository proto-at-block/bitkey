#include "hex.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "secure_channel.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"

static void _key_manager_task_handle_uxc_session_response(void* proto, void* UNUSED(context)) {
  ipc_send(key_manager_port, proto, sizeof(proto), IPC_KEY_MANAGER_UXC_SESSION_RESPONSE);
}

void key_manager_task_handle_uxc_session_response(ipc_ref_t* message) {
  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_secure_channel_establish_rsp* cmd = &msg_device->msg.secure_channel_response;

  if (cmd->protocol_version > SECURE_CHANNEL_PROTOCOL_VERSION) {
    LOGE("Incompatable protocol version: %ld", cmd->protocol_version);
    uc_free_recv_proto(message->object);
    return;
  }

  fwpb_uxc_msg_host* rsp_msg = uc_alloc_send_proto();
  rsp_msg->which_msg = fwpb_uxc_msg_host_secure_channel_confirm_tag;
  fwpb_secure_channel_establish_confirm* rsp = &rsp_msg->msg.secure_channel_confirm;
  secure_channel_err_t ret = secure_uart_channel_establish(
    cmd->pk_device.bytes, cmd->pk_device.size, NULL, NULL, rsp->exchange_sig.bytes,
    sizeof(rsp->exchange_sig.bytes), rsp->key_confirmation_tag.bytes);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC secure channel establishment failed: %d", ret);
    uc_free_send_proto(rsp_msg);
    uc_free_recv_proto(message->object);
    return;
  }

  ret = secure_uart_channel_confirm_session(cmd->key_confirmation_tag.bytes);
  uc_free_recv_proto(message->object);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC secure channel confirmation failed: %d", ret);
    uc_free_send_proto(rsp_msg);
    return;
  }

  rsp->exchange_sig.size = sizeof(rsp->exchange_sig.bytes);
  rsp->key_confirmation_tag.size = sizeof(rsp->key_confirmation_tag.bytes);
  rsp->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;
  LOGI("UXC secure channel established, sending confirmation to UXC.");

  (void)uc_send(rsp_msg);
}

// Send ephemeral public key to the UXC on boot to kick off the key establishment process
void key_manager_task_handle_uxc_boot(void) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);
  msg->which_msg = fwpb_uxc_msg_host_secure_channel_establish_tag;
  fwpb_secure_channel_establish_cmd* secure_channel_establish_cmd =
    &msg->msg.secure_channel_establish;

  uint8_t public_key_bytes[SECURE_CHANNEL_PUBKEY_MAX_LEN] = {0};
  uint32_t public_key_len = sizeof(public_key_bytes);
  secure_channel_err_t err = secure_uart_channel_public_key_init(public_key_bytes, &public_key_len);
  if (err != SECURE_CHANNEL_OK) {
    LOGE("Failed to initialize the public key for the uart secure channel: %d", err);
  }
  PROTO_FILL_BYTES(secure_channel_establish_cmd, pk_host, public_key_bytes, public_key_len);
  secure_channel_establish_cmd->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;
  (void)uc_send(msg);
}

void key_manager_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_secure_channel_response_tag,
                    _key_manager_task_handle_uxc_session_response, NULL);
}
