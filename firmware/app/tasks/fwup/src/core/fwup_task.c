#include "fwup_task.h"

#include "auth.h"
#include "bio.h"
#include "fwup.h"
#include "fwup_task_impl.h"
#include "ipc.h"
#include "log.h"
#include "mcu_reset.h"
#include "proto_helpers.h"
#include "rtos_thread.h"
#include "secutils.h"
#include "sysevent.h"
#include "ui_messaging.h"

#include <string.h>

static void fwup_thread(void* UNUSED(args));
static bool fwup_task_handle_start_cmd(ipc_ref_t* message);
static void fwup_task_handle_transfer_cmd(ipc_ref_t* message);
static void fwup_task_handle_finish_cmd(ipc_ref_t* message);

#define FWUP_FINISH_RESET_MS  (2000)
#define FWUP_FS_READY_WAIT_MS (1000)
#define FWUP_UI_YIELD_MS      (100)  // Yield to let UI task push display update

static struct {
  rtos_queue_t* queue;
} fwup_priv = {
  .queue = NULL,
};

void fwup_task_create(fwup_task_options_t options) {
  fwup_init((uint32_t*)fwup_target_slot_address(), (uint32_t*)fwup_current_slot_address(),
            (uint32_t*)fwup_target_slot_signature_address(), fwup_slot_size(), options.bl_upgrade,
            options.confirmation);

  fwup_priv.queue = rtos_queue_create(fwup_queue, ipc_ref_t, 4);
  ASSERT(fwup_priv.queue);
  ipc_register_port(fwup_port, fwup_priv.queue);

  rtos_thread_t* fwup_thread_handle =
    rtos_thread_create(fwup_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 4096);
  ASSERT(fwup_thread_handle);
}

static NO_OPTIMIZE void fwup_thread(void* UNUSED(args)) {
  SECURE_ASSERT(rtos_thread_is_privileged() == false);

  sysevent_wait_with_timeout(SYSEVENT_FILESYSTEM_READY, true, FWUP_FS_READY_WAIT_MS);
  if (sysevent_get(SYSEVENT_FILESYSTEM_READY)) {
    fwup_cleanup_stale_patch();
  } else {
    LOGE("FS not ready");
  }

  fwup_task_register_listeners();

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(fwup_port, &message);

    switch (message.tag) {
      case IPC_PROTO_FWUP_START_CMD: {
        (void)fwup_task_handle_start_cmd(&message);
      } break;
      case IPC_PROTO_FWUP_TRANSFER_CMD:
        fwup_task_handle_transfer_cmd(&message);
        break;
      case IPC_PROTO_FWUP_FINISH_CMD: {
        fwup_task_handle_finish_cmd(&message);
      } break;
      case IPC_FWUP_CONFIRMATION_RESULT: {
        fwup_handle_confirmation_result(&message);
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
      case IPC_FWUP_COMMIT_SIG_COPROC_RSP: {
        fwup_task_handle_commit_sig_rsp(&message);
      } break;
      case IPC_FWUP_COPROC_VERSION: {
        fwup_task_handle_coproc_version(&message);
      } break;
      default:
        LOGE("Unk msg %ld", message.tag);
    }
  }
}

static bool fwup_task_handle_start_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  const fwpb_mcu_role mcu_role = cmd->msg.fwup_start_cmd.mcu_role;
  MFLOGI("FWUP start role=%d", (int)mcu_role);

  if ((mcu_role != fwpb_mcu_role_MCU_ROLE_CORE && mcu_role != fwpb_mcu_role_MCU_ROLE_UXC) ||
      fwup_should_reject_cmd()) {
    LOGW("Start reject %d", mcu_role);

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
    proto_send_rsp(cmd, rsp);
    return false;
  }

  if (mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    return fwup_task_send_coproc_fwup_start_cmd(cmd);
  }

  return fwup_task_port_handle_start_cmd(cmd);
}

static void fwup_task_handle_transfer_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  const fwpb_mcu_role mcu_role = cmd->msg.fwup_transfer_cmd.mcu_role;

  if ((mcu_role != fwpb_mcu_role_MCU_ROLE_CORE && mcu_role != fwpb_mcu_role_MCU_ROLE_UXC) ||
      fwup_should_reject_cmd()) {
    LOGW("Xfer reject %d", mcu_role);

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_transfer_rsp_tag;
    rsp->msg.fwup_transfer_rsp.rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
    proto_send_rsp(cmd, rsp);
    return;
  }

  if (mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
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
  const fwpb_mcu_role mcu_role = cmd->msg.fwup_finish_cmd.mcu_role;
  MFLOGI("FWUP finish role=%d mode=%d bl=%d", (int)mcu_role, (int)cmd->msg.fwup_finish_cmd.mode,
         (int)cmd->msg.fwup_finish_cmd.bl_upgrade);

  if ((mcu_role != fwpb_mcu_role_MCU_ROLE_CORE && mcu_role != fwpb_mcu_role_MCU_ROLE_UXC) ||
      fwup_should_reject_cmd()) {
    LOGW("Fin reject %d", mcu_role);

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;
    rsp->msg.fwup_finish_rsp.rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
    proto_send_rsp(cmd, rsp);
    return;
  }

  if (mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    fwup_task_send_coproc_fwup_finish_cmd(cmd);
    return;
  }

  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;

  bool success = false;
  const bool bl_upgrade = cmd->msg.fwup_finish_cmd.bl_upgrade;
  if (cmd->msg.fwup_finish_cmd.mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    if (!fwup_pre_apply_check(&cmd->msg.fwup_finish_cmd, &rsp->msg.fwup_finish_rsp)) {
      // Version header check failed while NFC is still active; send the error
      // response so the host can surface it to the user. The patch is not applied
      // and the existing firmware in the active slot is untouched.
      proto_send_rsp(cmd, rsp);
    } else {
      // Applying the patch in one shot is quite slow, so we need to reply immediately
      // and do the patching outside of the NFC field.
      rsp->msg.fwup_finish_rsp.rsp_status =
        fwpb_fwup_finish_rsp_fwup_finish_rsp_status_WILL_APPLY_PATCH;
      proto_send_rsp_without_free(rsp);
      UI_SHOW_EVENT(UI_EVENT_FWUP_VERIFYING);
      rtos_thread_sleep(FWUP_UI_YIELD_MS);
      success = fwup_finish(&cmd->msg.fwup_finish_cmd, &rsp->msg.fwup_finish_rsp);
      proto_free_buffers(cmd, rsp);
    }
  } else {
    // In all other cases we handle finalizing the FWUP and reply with the actual status.
    UI_SHOW_EVENT(UI_EVENT_FWUP_VERIFYING);
    rtos_thread_sleep(FWUP_UI_YIELD_MS);
    success = fwup_finish(&cmd->msg.fwup_finish_cmd, &rsp->msg.fwup_finish_rsp);
    proto_send_rsp(cmd, rsp);
  }

  // If both UXC and Core were updated, attempt atomic commit — both chips
  // switch to new firmware together.  fwup_task_port_try_atomic_commit()
  // returns true when the async commit protocol has started; the rest of the
  // flow continues in fwup_task_handle_commit_sig_rsp().
  if (success && fwup_task_port_try_atomic_commit()) {
    return;
  }

  // If atomic commit was intended but failed to start (e.g. UC send failure),
  // do NOT fall through to direct commit — that would update Core alone.
  if (success && fwup_task_port_is_deferred_session()) {
    success = false;
  }

  // Non-atomic path: Core-only update (defer_commit not set).
  // Bootloader upgrades commit their signature inline in fwup_finish(),
  // so fwup_commit_signature() is only needed for app-slot updates.
  if (success && !bl_upgrade) {
    success = fwup_commit_signature();
  }

  UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
  MFLOGI("FWUP finish result success=%d", (int)success);
  if (success) {
    bool is_final = true;
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_COMPLETE, &is_final, sizeof(is_final));
  } else {
    SECURE_DO({ deauthenticate_without_animation(); });
    UI_SHOW_EVENT(UI_EVENT_FWUP_FAILED);
    rtos_thread_sleep(FWUP_UI_YIELD_MS);
  }

  // Wait a few seconds to give the host a chance to receive the proto, then reset.
  rtos_thread_sleep(FWUP_FINISH_RESET_MS);
  mcu_reset_with_reason(MCU_RESET_FWUP);
}
