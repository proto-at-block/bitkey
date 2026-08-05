#include "fwup_task.h"

#include "assert.h"
#include "fwup.h"
#include "log.h"
#include "mcu_reset.h"
#include "rtos_queue.h"
#include "rtos_thread.h"
#include "sysevent.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define FWUP_TASK_PRIORITY    (RTOS_THREAD_PRIORITY_NORMAL)
#define FWUP_TASK_STACK_SIZE  (2048u)
#define FWUP_TASK_QUEUE_SIZE  (2u)
#define FWUP_FINISH_RESET_MS  (2000u)
#define FWUP_FS_READY_WAIT_MS (1000)

static void fwup_thread(void* args);
static void _fwup_task_handle_fwup_start(fwpb_uxc_msg_host* msg);
static void _fwup_task_handle_fwup_transfer(fwpb_uxc_msg_host* msg);
static void _fwup_task_handle_fwup_finish(fwpb_uxc_msg_host* msg);
static void _fwup_task_handle_commit_sig(fwpb_uxc_msg_host* msg);

// Session flag: true when the app requested deferred commit via fwup_start_cmd.
static bool uxc_defer_commit FWUP_TASK_DATA = false;

void fwup_task_create(fwup_task_options_t options) {
  fwup_init((uint32_t*)fwup_target_slot_address(), (uint32_t*)fwup_current_slot_address(),
            (uint32_t*)fwup_target_slot_signature_address(), fwup_slot_size(), options.bl_upgrade,
            options.confirmation);

  rtos_queue_t* queue =
    rtos_queue_create(fwup_task_queue, fwpb_uxc_msg_host*, FWUP_TASK_QUEUE_SIZE);
  rtos_thread_t* thread =
    rtos_thread_create(fwup_thread, queue, FWUP_TASK_PRIORITY, FWUP_TASK_STACK_SIZE);
  ASSERT(thread != NULL);
}

static void fwup_thread(void* args) {
  rtos_queue_t* queue = args;
  ASSERT(queue != NULL);

  uc_route_register_queue(fwpb_uxc_msg_host_fwup_start_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_fwup_transfer_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_fwup_finish_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_fwup_commit_sig_cmd_tag, queue);

  sysevent_wait_with_timeout(SYSEVENT_FILESYSTEM_READY, true, FWUP_FS_READY_WAIT_MS);
  if (sysevent_get(SYSEVENT_FILESYSTEM_READY)) {
    fwup_cleanup_stale_patch();
  } else {
    LOGE("FS not ready; stale patch cleanup skipped");
  }

  while (true) {
    fwpb_uxc_msg_host* proto = uc_route_pend_queue(queue);
    ASSERT(proto != NULL);

    switch (proto->which_msg) {
      case fwpb_uxc_msg_host_fwup_start_cmd_tag:
        _fwup_task_handle_fwup_start(proto);
        break;

      case fwpb_uxc_msg_host_fwup_transfer_cmd_tag:
        _fwup_task_handle_fwup_transfer(proto);
        break;

      case fwpb_uxc_msg_host_fwup_finish_cmd_tag:
        _fwup_task_handle_fwup_finish(proto);
        break;

      case fwpb_uxc_msg_host_fwup_commit_sig_cmd_tag:
        _fwup_task_handle_commit_sig(proto);
        break;

      default:
        uc_free_recv_proto(proto);
        break;
    }
  }
}

static void _fwup_task_handle_fwup_start(fwpb_uxc_msg_host* msg) {
  MFLOGI("UXC FWUP start");
  // Copy command to stack and free recv buffer immediately to avoid
  // holding a shared UC recv buffer during flash erase.
  fwpb_fwup_start_cmd cmd_local = msg->msg.fwup_start_cmd;
  uc_free_recv_proto(msg);

  uxc_defer_commit = cmd_local.defer_commit;

  fwpb_uxc_msg_device* rsp = uc_alloc_send_proto();
  ASSERT(rsp != NULL);
  rsp->which_msg = fwpb_uxc_msg_device_fwup_start_rsp_tag;

  // Intentionally ignoring the return value here as UI is driven by the
  // core.
  (void)fwup_start(&cmd_local, &rsp->msg.fwup_start_rsp);

  (void)uc_send(rsp);
}

static void _fwup_task_handle_fwup_transfer(fwpb_uxc_msg_host* msg) {
  fwpb_uxc_msg_device* rsp = uc_alloc_send_proto();
  ASSERT(rsp != NULL);
  rsp->which_msg = fwpb_uxc_msg_device_fwup_transfer_rsp_tag;

  fwup_transfer(&msg->msg.fwup_transfer_cmd, &rsp->msg.fwup_transfer_rsp);

  uc_free_recv_proto(msg);
  (void)uc_send(rsp);
}

static void _fwup_task_handle_fwup_finish(fwpb_uxc_msg_host* msg) {
  MFLOGI("UXC FWUP finish mode=%d", (int)msg->msg.fwup_finish_cmd.mode);
  fwpb_fwup_finish_cmd fwup_finish_cmd;
  fwpb_uxc_msg_device* rsp = uc_alloc_send_proto();
  ASSERT(rsp != NULL);
  rsp->which_msg = fwpb_uxc_msg_device_fwup_finish_rsp_tag;

  // Copy the command as the FWUP application may take some time.
  memcpy(&fwup_finish_cmd, &msg->msg.fwup_finish_cmd, sizeof(fwup_finish_cmd));
  uc_free_recv_proto(msg);

  if (fwup_finish_cmd.mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    if (!fwup_pre_apply_check(&fwup_finish_cmd, &rsp->msg.fwup_finish_rsp)) {
      // Version header check failed while still connected; send the error so
      // Core can surface it to the host. No patch is applied.
      (void)uc_send(rsp);
      rtos_thread_sleep(FWUP_FINISH_RESET_MS);
      mcu_reset_with_reason(MCU_RESET_FWUP);
    } else {
      // Respond immediately, then apply.
      rsp->msg.fwup_finish_rsp.rsp_status =
        fwpb_fwup_finish_rsp_fwup_finish_rsp_status_WILL_APPLY_PATCH;

      (void)uc_send(rsp);

      // Give the Core a chance to receive the message before the slow patch.
      rtos_thread_sleep(FWUP_FINISH_RESET_MS);

      fwpb_fwup_finish_rsp fwup_finish_rsp;
      bool patch_success = fwup_finish(&fwup_finish_cmd, &fwup_finish_rsp);

      // Send a second fwup_finish_rsp with the actual result so Core can
      // show the correct completion/failure UI after patching.
      fwpb_uxc_msg_device* result_rsp = uc_alloc_send_proto();
      ASSERT(result_rsp != NULL);
      result_rsp->which_msg = fwpb_uxc_msg_device_fwup_finish_rsp_tag;
      result_rsp->msg.fwup_finish_rsp.rsp_status =
        patch_success ? fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS
                      : fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
      (void)uc_send(result_rsp);

      if (!uxc_defer_commit) {
        // Legacy mode: commit if successful, then reset.
        if (patch_success) {
          (void)fwup_commit_signature();
        }
        rtos_thread_sleep(FWUP_FINISH_RESET_MS);
        mcu_reset_with_reason(MCU_RESET_FWUP);
      }
      // Deferred mode (success or failure): return to the event loop.
      // Core drives the display and reset for both chips.
    }
  } else {
    (void)fwup_finish(&fwup_finish_cmd, &rsp->msg.fwup_finish_rsp);
    bool finish_ok =
      (rsp->msg.fwup_finish_rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS);
    if (finish_ok && !uxc_defer_commit) {
      // Legacy mode: commit signature and reset.
      (void)fwup_commit_signature();
    }
    (void)uc_send(rsp);

    if (!uxc_defer_commit) {
      rtos_thread_sleep(FWUP_FINISH_RESET_MS);
      mcu_reset_with_reason(MCU_RESET_FWUP);
    }
    // Atomic mode success: signature is held in RAM. Return to the event
    // loop and wait for the fwup_commit_sig_cmd from Core before committing.
  }
}

static void _fwup_task_handle_commit_sig(fwpb_uxc_msg_host* msg) {
  uc_free_recv_proto(msg);

  fwpb_uxc_msg_device* rsp = uc_alloc_send_proto();
  ASSERT(rsp != NULL);
  rsp->which_msg = fwpb_uxc_msg_device_fwup_commit_sig_rsp_tag;

  if (fwup_commit_signature()) {
    rsp->msg.fwup_commit_sig_rsp.rsp_status =
      fwpb_fwup_commit_sig_rsp_fwup_commit_sig_rsp_status_SUCCESS;
  } else {
    rsp->msg.fwup_commit_sig_rsp.rsp_status =
      fwpb_fwup_commit_sig_rsp_fwup_commit_sig_rsp_status_ERROR;
  }

  (void)uc_send(rsp);
  // Do not reset — Core will hold UXC in reset via GPIO and then
  // reset itself, bringing both chips up on the new firmware.
}
