#include "fwup_task.h"

#include "auth.h"
#include "bio.h"
#include "fwup.h"
#include "fwup_task_impl.h"
#include "ipc.h"
#include "log.h"
#include "mcu_reset.h"
#include "proto_helpers.h"
#include "secutils.h"
#include "ui_messaging.h"

#include <string.h>

static void fwup_thread(void* UNUSED(args));
static bool fwup_task_handle_start_cmd(ipc_ref_t* message);
static void fwup_task_handle_transfer_cmd(ipc_ref_t* message);
static void fwup_task_handle_finish_cmd(ipc_ref_t* message);

#define FWUP_FINISH_RESET_MS (2000)

static struct {
  rtos_queue_t* queue;
} fwup_priv = {
  .queue = NULL,
};

void fwup_task_create(fwup_task_options_t options) {
  fwup_init((uint32_t*)fwup_target_slot_address(), (uint32_t*)fwup_current_slot_address(),
            (uint32_t*)fwup_target_slot_signature_address(), fwup_slot_size(), options.bl_upgrade);

  fwup_priv.queue = rtos_queue_create(fwup_queue, ipc_ref_t, 4);
  ASSERT(fwup_priv.queue);
  ipc_register_port(fwup_port, fwup_priv.queue);

  rtos_thread_t* fwup_thread_handle =
    rtos_thread_create(fwup_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 4096);
  ASSERT(fwup_thread_handle);
}

static NO_OPTIMIZE void fwup_thread(void* UNUSED(args)) {
  SECURE_ASSERT(rtos_thread_is_privileged() == false);

  fwup_task_register_listeners();

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(fwup_port, &message);

    switch (message.tag) {
      case IPC_PROTO_FWUP_START_CMD: {
        // TODO(W-4580)
        UI_SHOW_EVENT(UI_EVENT_FWUP_START);

        if (!fwup_task_handle_start_cmd(&message)) {
          UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
        }
      } break;
      case IPC_PROTO_FWUP_TRANSFER_CMD:
        fwup_task_handle_transfer_cmd(&message);
        break;
      case IPC_PROTO_FWUP_FINISH_CMD: {
        fwup_task_handle_finish_cmd(&message);
      } break;
      case IPC_FWUP_START_COPROC_RSP: {
        fwup_task_handle_coproc_fwup_start(&message);
      } break;
      case IPC_FWUP_TRANSFER_COPROC_RSP: {
        fwup_task_handle_coproc_fwup_transfer(&message);
      } break;
      case IPC_FWUP_FINISH_COPROC_RSP: {
        fwup_task_handle_coproc_fwup_finish(&message);
      } break;
      default:
        LOGE("unknown message %ld", message.tag);
    }
  }
}

static bool fwup_task_handle_start_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  if (cmd->msg.fwup_start_cmd.mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    return fwup_task_send_coproc_fwup_start_cmd(cmd);
  }

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;

  bool result = fwup_start(&cmd->msg.fwup_start_cmd, &rsp->msg.fwup_start_rsp);

  fwup_mark_pending(result);

  proto_send_rsp(cmd, rsp);

  return result;
}

static void fwup_task_handle_transfer_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  if (cmd->msg.fwup_transfer_cmd.mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    fwup_task_send_coproc_fwup_transfer_cmd(cmd);
    return;
  }

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_transfer_rsp_tag;

  fwup_transfer(&cmd->msg.fwup_transfer_cmd, &rsp->msg.fwup_transfer_rsp);

  proto_send_rsp(cmd, rsp);
}

static NO_OPTIMIZE void fwup_task_handle_finish_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  if (cmd->msg.fwup_finish_cmd.mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    fwup_task_send_coproc_fwup_finish_cmd(cmd);
    return;
  }

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;

  bool success = false;
  if (cmd->msg.fwup_finish_cmd.mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    // Applying the patch in one shot is quite slow, so we need to reply immediately
    // and do the patching outside of the NFC field.
    rsp->msg.fwup_finish_rsp.rsp_status =
      fwpb_fwup_finish_rsp_fwup_finish_rsp_status_WILL_APPLY_PATCH;

    proto_send_rsp_without_free(rsp);
    success = fwup_finish(&cmd->msg.fwup_finish_cmd, &rsp->msg.fwup_finish_rsp);
    proto_free_buffers(cmd, rsp);
  } else {
    // In all other cases we handle finalizing the FWUP and reply with the actual status.
    success = fwup_finish(&cmd->msg.fwup_finish_cmd, &rsp->msg.fwup_finish_rsp);
    proto_send_rsp(cmd, rsp);
  }

  UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
  if (success) {
    UI_SHOW_EVENT(UI_EVENT_FWUP_COMPLETE);
  } else {
    UI_SHOW_EVENT(UI_EVENT_FWUP_FAILED);

    SECURE_DO({ deauthenticate_without_animation(); });
  }

  // Wait a few seconds to give the host a chance to receive the proto, then reset.
  LOGI("FWUP done, resetting in %d ms", FWUP_FINISH_RESET_MS);

  rtos_thread_sleep(FWUP_FINISH_RESET_MS);
  mcu_reset_with_reason(MCU_RESET_FWUP);
}
