#include "hex.h"
#include "log.h"
#include "rtos.h"
#include "secure_channel.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"

#include <inttypes.h>
#include <stdint.h>

#define KEY_MANAGER_TASK_PRIORITY   (RTOS_THREAD_PRIORITY_NORMAL)
#define KEY_MANAGER_TASK_STACK_SIZE (8192u)
#define KEY_MANAGER_TASK_QUEUE_SIZE (4u)

NO_OPTIMIZE static void handle_secure_channel_establish(void* proto) {
  // Copy command to stack and free recv buffer immediately to avoid
  // holding a shared UC recv buffer during ECDH crypto.
  fwpb_secure_channel_establish_cmd establish_local =
    ((fwpb_uxc_msg_host*)proto)->msg.secure_channel_establish;
  uc_free_recv_proto(proto);

  volatile uint32_t protocol_version = establish_local.protocol_version;
  SECURE_IF_FAILIN(!secure_channel_protocol_version_supported(protocol_version)) {
    LOGE("Incompat proto ver: %" PRIu32, protocol_version);
    return;
  }

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  msg->which_msg = fwpb_uxc_msg_device_secure_channel_response_tag;
  fwpb_secure_channel_establish_rsp* rsp = &msg->msg.secure_channel_response;

  uint32_t pk_len = sizeof(rsp->pk_device.bytes);
  if (secure_uart_channel_establish(establish_local.pk_host.bytes, establish_local.pk_host.size,
                                    rsp->pk_device.bytes, &pk_len, rsp->exchange_sig.bytes,
                                    sizeof(rsp->exchange_sig.bytes),
                                    rsp->key_confirmation_tag.bytes) != SECURE_CHANNEL_OK) {
    LOGE("UXC SC: key derive fail");
    uc_free_send_proto(msg);
    return;
  }

  rsp->pk_device.size = pk_len;
  rsp->exchange_sig.size = sizeof(rsp->exchange_sig.bytes);
  rsp->key_confirmation_tag.size = sizeof(rsp->key_confirmation_tag.bytes);
  rsp->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;

  (void)uc_send(msg);
}

NO_OPTIMIZE static void handle_secure_channel_confirm(void* proto) {
  // Copy confirm data to stack and free recv buffer immediately to avoid
  // holding a shared UC recv buffer during session confirmation.
  fwpb_secure_channel_establish_confirm confirm_local =
    ((fwpb_uxc_msg_host*)proto)->msg.secure_channel_confirm;
  uc_free_recv_proto(proto);

  volatile uint32_t protocol_version = confirm_local.protocol_version;
  SECURE_IF_FAILIN(!secure_channel_protocol_version_supported(protocol_version)) {
    LOGE("Incompat proto ver: %" PRIu32, protocol_version);
    return;
  }

  secure_channel_err_t ret = secure_uart_channel_confirm_session(
    confirm_local.key_confirmation_tag.bytes, confirm_local.exchange_sig.bytes,
    confirm_local.exchange_sig.size);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC SC: confirm fail: %d", ret);
  } else {
    LOGI("UXC Secure Channel: established.");
  }
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
