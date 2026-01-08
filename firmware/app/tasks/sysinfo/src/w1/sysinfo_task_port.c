#include "attributes.h"
#include "ipc.h"
#include "log.h"
#include "proto_helpers.h"
#include "sysinfo_task_impl.h"
#include "wallet.pb.h"

#include <stdbool.h>

void sysinfo_task_port_send_device_info(void) {
  /* No-op on W1 - no display. */
}

void sysinfo_task_register_listeners(void) {
  /* No-op on W1. */
}

void sysinfo_task_handle_coproc_boot(ipc_ref_t* message) {
  /* No-op on W1. */
  (void)message;
}

bool sysinfo_task_port_handle_message(ipc_ref_t* message) {
  /* No platform-specific messages on W1. */
  (void)message;
  return false;
}

void sysinfo_task_handle_coproc_metadata(ipc_ref_t* message) {
  /* Should never be called. */
  (void)message;
  LOGE("unexpected call to coproc metadata");
}

void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd) {
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
  rsp->msg.meta_rsp.rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
  proto_send_rsp(cmd, rsp);
}
