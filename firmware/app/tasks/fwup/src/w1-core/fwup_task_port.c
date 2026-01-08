#include "assert.h"
#include "fwup_task_impl.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

void fwup_task_register_listeners(void) {
  // No-op.
}

bool fwup_task_send_coproc_fwup_start_cmd(fwpb_wallet_cmd* UNUSED(cmd)) {
  // Should never be called on W1.
  ASSERT(false);
  return false;
}

void fwup_task_send_coproc_fwup_transfer_cmd(fwpb_wallet_cmd* UNUSED(cmd)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_send_coproc_fwup_finish_cmd(fwpb_wallet_cmd* UNUSED(cmd)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_handle_coproc_fwup_start(ipc_ref_t* UNUSED(message)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_handle_coproc_fwup_transfer(ipc_ref_t* UNUSED(message)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_handle_coproc_fwup_finish(ipc_ref_t* UNUSED(message)) {
  // Should never be called on W1.
  ASSERT(false);
}
