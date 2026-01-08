#include "hex.h"
#include "log.h"
#include "rtos.h"
#include "secure_channel.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"

#include <stdint.h>

#define KEY_MANAGER_TASK_PRIORITY   (RTOS_THREAD_PRIORITY_NORMAL)
#define KEY_MANAGER_TASK_STACK_SIZE (8192u)
#define KEY_MANAGER_TASK_QUEUE_SIZE (2u)

static void handle_secure_channel_establish(void* proto) {
  fwpb_secure_channel_establish_cmd* establish_cmd =
    &((fwpb_uxc_msg_host*)proto)->msg.secure_channel_establish;

  if (establish_cmd->protocol_version > SECURE_CHANNEL_PROTOCOL_VERSION) {
    LOGE("Incompatable protocol version: %ld", establish_cmd->protocol_version);
    uc_free_recv_proto(proto);
    return;
  }

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  msg->which_msg = fwpb_uxc_msg_device_secure_channel_response_tag;
  fwpb_secure_channel_establish_rsp* rsp = &msg->msg.secure_channel_response;

  uint32_t pk_len = sizeof(rsp->pk_device.bytes);
  if (secure_uart_channel_establish(establish_cmd->pk_host.bytes, establish_cmd->pk_host.size,
                                    rsp->pk_device.bytes, &pk_len, rsp->exchange_sig.bytes,
                                    sizeof(rsp->exchange_sig.bytes),
                                    rsp->key_confirmation_tag.bytes) != SECURE_CHANNEL_OK) {
    LOGE("UXC Secure Channel: key derivation failed");
    uc_free_send_proto(msg);
    uc_free_recv_proto(proto);
    return;
  }

  rsp->pk_device.size = pk_len;
  rsp->exchange_sig.size = sizeof(rsp->exchange_sig.bytes);
  rsp->key_confirmation_tag.size = sizeof(rsp->key_confirmation_tag.bytes);
  rsp->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;

  uc_free_recv_proto(proto);
  (void)uc_send(msg);
}

static void handle_secure_channel_confirm(void* proto) {
  fwpb_secure_channel_establish_confirm* confirmation_message =
    &((fwpb_uxc_msg_host*)proto)->msg.secure_channel_confirm;

  if (confirmation_message->protocol_version > SECURE_CHANNEL_PROTOCOL_VERSION) {
    LOGE("Incompatable protocol version: %ld", confirmation_message->protocol_version);
    uc_free_recv_proto(proto);
    return;
  }

  secure_channel_err_t ret =
    secure_uart_channel_confirm_session(confirmation_message->key_confirmation_tag.bytes);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC Secure Channel: confirmation failed: %d", ret);
  } else {
    LOGI("UXC Secure Channel: established.");
  }

  uc_free_recv_proto(proto);
}

static void key_manager_thread(void* args) {
  rtos_queue_t* queue = args;

  uc_route_register_queue(fwpb_uxc_msg_host_secure_channel_establish_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_secure_channel_confirm_tag, queue);

  while (true) {
    fwpb_uxc_msg_host* proto = uc_route_pend_queue(queue);
    ASSERT(proto != NULL);
    switch (proto->which_msg) {
      case fwpb_uxc_msg_host_secure_channel_establish_tag: {
        handle_secure_channel_establish(proto);
        break;
      }
      case fwpb_uxc_msg_host_secure_channel_confirm_tag: {
        handle_secure_channel_confirm(proto);
        break;
      }
      default: {
        uc_free_recv_proto(proto);
        break;
      }
    }
  }
}

void key_manager_task_create(void) {
  rtos_queue_t* queue =
    rtos_queue_create(key_manager_task_queue, fwpb_uxc_msg_host*, KEY_MANAGER_TASK_QUEUE_SIZE);
  rtos_thread_t* thread = rtos_thread_create(key_manager_thread, queue, KEY_MANAGER_TASK_PRIORITY,
                                             KEY_MANAGER_TASK_STACK_SIZE);
  ASSERT(thread != NULL);
}
