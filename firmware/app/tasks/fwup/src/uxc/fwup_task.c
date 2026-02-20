#include "fwup_task.h"

#include "assert.h"
#include "fwup.h"
#include "mcu_reset.h"
#include "rtos_queue.h"
#include "rtos_thread.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define FWUP_TASK_PRIORITY   (RTOS_THREAD_PRIORITY_NORMAL)
#define FWUP_TASK_STACK_SIZE (2048u)
#define FWUP_TASK_QUEUE_SIZE (2u)
#define FWUP_FINISH_RESET_MS (500u)

static void fwup_thread(void* args);
static void _fwup_task_handle_fwup_start(fwpb_uxc_msg_host* msg);
static void _fwup_task_handle_fwup_transfer(fwpb_uxc_msg_host* msg);
static void _fwup_task_handle_fwup_finish(fwpb_uxc_msg_host* msg);

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

      default:
        uc_free_recv_proto(proto);
        break;
    }
  }
}

static void _fwup_task_handle_fwup_start(fwpb_uxc_msg_host* msg) {
  fwpb_uxc_msg_device* rsp = uc_alloc_send_proto();
  ASSERT(rsp != NULL);
  rsp->which_msg = fwpb_uxc_msg_device_fwup_start_rsp_tag;

  // Intentionally ignoring the return value here as UI is driven by the
  // core.
  (void)fwup_start(&msg->msg.fwup_start_cmd, &rsp->msg.fwup_start_rsp);

  uc_free_recv_proto(msg);
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
  fwpb_fwup_finish_cmd fwup_finish_cmd;
  fwpb_uxc_msg_device* rsp = uc_alloc_send_proto();
  ASSERT(rsp != NULL);
  rsp->which_msg = fwpb_uxc_msg_device_fwup_finish_rsp_tag;

  // Copy the command as the FWUP application may take some time.
  memcpy(&fwup_finish_cmd, &msg->msg.fwup_finish_cmd, sizeof(fwup_finish_cmd));
  uc_free_recv_proto(msg);

  if (fwup_finish_cmd.mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    // Respond immediately, then apply.
    rsp->msg.fwup_finish_rsp.rsp_status =
      fwpb_fwup_finish_rsp_fwup_finish_rsp_status_WILL_APPLY_PATCH;

    (void)uc_send(rsp);

    // Give the Core a chance to receive the message and render any new
    // displays. We do this before we apply the FWUP, as applying the patch
    // will take some time.
    rtos_thread_sleep(FWUP_FINISH_RESET_MS);

    fwpb_fwup_finish_rsp fwup_finish_rsp;

    // We intentionally ignore the contents of fwup_finish_rsp here. The UXC has
    // already responded with WILL_APPLY_PATCH and will reset unconditionally
    // after fwup_finish completes; any detailed result handling is done on Core.
    (void)fwup_finish(&fwup_finish_cmd, &fwup_finish_rsp);
    mcu_reset_with_reason(MCU_RESET_FWUP);
  } else {
    // We do not care about the result on the UXC, as the Core will handle
    // the failure in the response.
    (void)fwup_finish(&fwup_finish_cmd, &rsp->msg.fwup_finish_rsp);

    (void)uc_send(rsp);
    rtos_thread_sleep(FWUP_FINISH_RESET_MS);
    mcu_reset_with_reason(MCU_RESET_FWUP);
  }
}
