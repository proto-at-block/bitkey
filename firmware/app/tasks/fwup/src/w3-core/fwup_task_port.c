#include "assert.h"
#include "auth.h"
#include "confirmation_manager.h"
#include "coproc_power.h"
#include "fwup.h"
#include "fwup_staged_sig.h"
#include "fwup_task_impl.h"
#include "fwup_utils.h"
#include "ipc.h"
#include "log.h"
#include "mcu_reset.h"
#include "metadata.h"
#include "nfc_control.h"
#include "onboarding.h"
#include "proto_helpers.h"
#include "rtos_thread.h"
#include "secutils.h"
#include "sysinfo_task.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define FWUP_SUCCESS_DISPLAY_MS  (2000)
#define FWUP_UI_YIELD_MS         (100)
#define FWUP_NFC_DISABLE_TIMEOUT (30000)

static void _handle_fwup_start(void* proto, void* UNUSED(context));
static void _handle_fwup_transfer(void* proto, void* UNUSED(context));
static void _handle_fwup_finish(void* proto, void* UNUSED(context));
static void _handle_commit_sig(void* proto, void* UNUSED(context));
static bool fwup_atomic_commit(const fwpb_semver* core_version, const fwpb_semver* uxc_version);

// Tracks whether we're awaiting an async UXC response to a get_confirmation_result command.
// Set to true when forwarding a confirmed UXC fwup_start to UXC over UART.
// Cleared when the UXC response arrives via _handle_fwup_start callback.
static SHARED_TASK_BSS bool awaiting_uxc_confirmation_response = false;

// Tracks the version the user most recently confirmed via the FWUP confirmation UI.
// Used to allow skipping a second confirmation when both UXC and Core are being
// updated to the same version in a single session (UXC FWUP then Core FWUP).
// Cleared on any failure. On success, Core resets after its own FWUP which clears BSS.
typedef struct {
  fwpb_semver version;
  bool valid;
} user_confirmed_version_t;

static user_confirmed_version_t SHARED_TASK_BSS user_confirmed_version = {0};

// Tracks whether we're awaiting the actual result of a UXC delta patch application.
// Set when we receive WILL_APPLY_PATCH from UXC (patch is being applied).
// Cleared when the second fwup_finish_rsp arrives with the real result, or as a
// fallback when UXC reboots and reports its version (in case the result message is lost).
static SHARED_TASK_BSS bool awaiting_coproc_delta_result = false;

// NFC disable token held while UXC is applying a delta patch.  Acquired when
// awaiting_coproc_delta_result is set, released when it is cleared.
static SHARED_TASK_DATA nfc_disable_token_t coproc_delta_nfc_token = NFC_CONTROL_INVALID_TOKEN;

// True when an atomic commit is in progress — Core has sent fwup_commit_sig_cmd
// to UXC and is waiting for the ACK before committing its own signature.
static SHARED_TASK_BSS bool awaiting_atomic_commit_rsp = false;

// Session flag: true when the app requested deferred commit via fwup_start_cmd.
// Set when UXC FWUP starts with defer_commit=true; cleared on failure or reset.
static SHARED_TASK_BSS bool session_defer_commit = false;

// True after UXC verification succeeded in deferred mode.  Core uses this to
// reject a defer_commit Core FWUP start if UXC isn't ready to commit.
static SHARED_TASK_BSS bool uxc_verified = false;

static fwup_confirmation_data_t fwup_build_confirmation_data(const fwpb_fwup_start_cmd* cmd) {
  fwup_confirmation_data_t data = {
    .cmd = *cmd,
  };
  if (cmd->has_version) {
    char version_string[32] = {0};
    if (fwup_format_version_string(&cmd->version, version_string, sizeof(version_string))) {
      strncpy(data.version_str, version_string, sizeof(data.version_str) - 1);
      data.version_str[sizeof(data.version_str) - 1] = '\0';
    }
  }
  return data;
}

static bool fwup_forward_coproc_start(fwpb_wallet_cmd* cmd) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_host_fwup_start_cmd_tag;
  msg->msg.fwup_start_cmd = cmd->msg.fwup_start_cmd;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
    proto_send_rsp(NULL, rsp);
    session_defer_commit = false;
  } else {
    fwup_mark_coproc_pending(true);
  }
  return sent;
}

static bool fwup_confirmation_result_forwarder(ipc_ref_t* message) {
  ipc_send(fwup_port, message->object, message->length, IPC_FWUP_CONFIRMATION_RESULT);
  return true;
}

void fwup_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_fwup_start_rsp_tag, _handle_fwup_start, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_transfer_rsp_tag, _handle_fwup_transfer, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_finish_rsp_tag, _handle_fwup_finish, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_commit_sig_rsp_tag, _handle_commit_sig, NULL);

  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_FWUP_START,
                                               fwup_confirmation_result_forwarder);
}

NO_OPTIMIZE bool fwup_task_send_coproc_fwup_start_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_start_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  // Record whether the app requested atomic (deferred) commit for this session.
  session_defer_commit = cmd->msg.fwup_start_cmd.defer_commit;

  // Deferred commit requires a version to track the UXC target for recovery.
  if (session_defer_commit && !cmd->msg.fwup_start_cmd.has_version) {
    LOGW("defer_commit requires version");
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
    proto_send_rsp(cmd, rsp);
    session_defer_commit = false;
    return false;
  }

  SECURE_IF_FAILOUT(fwup_get_require_confirmation() == SECURE_FALSE) {
    if (cmd->msg.fwup_start_cmd.has_version) {
      user_confirmed_version.version = cmd->msg.fwup_start_cmd.version;
      user_confirmed_version.valid = true;
    }
    return fwup_forward_coproc_start(cmd);
  }

  // Prod mode: Skip confirmation if device is not onboarded or the user already
  // confirmed this version in the current update session.
  {
    bool have_incoming_version = cmd->msg.fwup_start_cmd.has_version;
    bool skip_for_not_onboarded = (onboarding_complete() != SECURE_TRUE);
    bool skip_for_confirmed =
      (user_confirmed_version.valid && have_incoming_version &&
       fwup_semver_equals(&user_confirmed_version.version, &cmd->msg.fwup_start_cmd.version));

    if (skip_for_not_onboarded || skip_for_confirmed) {
      fwup_confirmation_data_t confirmation_data =
        fwup_build_confirmation_data(&cmd->msg.fwup_start_cmd);
      confirmation_data.skip_confirmation = true;

      // Record the version so the atomic commit protocol knows the UXC target.
      if (have_incoming_version) {
        user_confirmed_version.version = cmd->msg.fwup_start_cmd.version;
        user_confirmed_version.valid = true;
      }

      bool result = fwup_forward_coproc_start(cmd);
      if (result) {
        UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_START, &confirmation_data, sizeof(confirmation_data));
      } else {
        user_confirmed_version.valid = false;
        session_defer_commit = false;
      }
      return result;
    }
  }

  // Require on-device confirmation for UXC update
  {
    uint8_t response_handle[32];
    uint8_t confirmation_handle[32];

    fwup_confirmation_data_t confirmation_data =
      fwup_build_confirmation_data(&cmd->msg.fwup_start_cmd);

    confirmation_result_t result = confirmation_manager_create(
      CONFIRMATION_TYPE_FWUP_START, &confirmation_data, sizeof(confirmation_data), response_handle,
      sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

    if (result != CONFIRMATION_RESULT_SUCCESS) {
      LOGE("UXC conf: %d", result);
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
      rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
      proto_send_rsp(cmd, rsp);
      return false;
    }

    // Show FWUP confirmation UI for UXC with extended data
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_CONFIRMATION, &confirmation_data,
                            sizeof(confirmation_data));

    // Return CONFIRMATION_PENDING with handles
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
    rsp->response_handle.size = sizeof(response_handle);
    memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
    rsp->confirmation_handle.size = sizeof(confirmation_handle);

    proto_send_rsp(cmd, rsp);
    return true;
  }
}

void fwup_task_send_coproc_fwup_transfer_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_transfer_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  // Copy over the transfer command message.
  msg->which_msg = fwpb_uxc_msg_host_fwup_transfer_cmd_tag;
  msg->msg.fwup_transfer_cmd.sequence_id = cmd->msg.fwup_transfer_cmd.sequence_id;
  msg->msg.fwup_transfer_cmd.offset = cmd->msg.fwup_transfer_cmd.offset;
  msg->msg.fwup_transfer_cmd.mode = cmd->msg.fwup_transfer_cmd.mode;
  msg->msg.fwup_transfer_cmd.mcu_role = cmd->msg.fwup_transfer_cmd.mcu_role;

  const size_t num_bytes = BLK_MIN(sizeof(msg->msg.fwup_transfer_cmd.fwup_data.bytes),
                                   cmd->msg.fwup_transfer_cmd.fwup_data.size);
  memcpy(msg->msg.fwup_transfer_cmd.fwup_data.bytes, cmd->msg.fwup_transfer_cmd.fwup_data.bytes,
         num_bytes);
  msg->msg.fwup_transfer_cmd.fwup_data.size = num_bytes;

  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response, as we were unable to send the FWUP transfer
    // command to the co-processor.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_transfer_rsp_tag;
    rsp->msg.fwup_transfer_rsp.rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
    proto_send_rsp(NULL, rsp);

    user_confirmed_version.valid = false;
    fwup_mark_coproc_pending(false);
  }
}

void fwup_task_send_coproc_fwup_finish_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_finish_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  // Copy over the FWUP finish command message.
  msg->which_msg = fwpb_uxc_msg_host_fwup_finish_cmd_tag;
  msg->msg.fwup_finish_cmd.app_properties_offset = cmd->msg.fwup_finish_cmd.app_properties_offset;
  msg->msg.fwup_finish_cmd.signature_offset = cmd->msg.fwup_finish_cmd.signature_offset;
  msg->msg.fwup_finish_cmd.bl_upgrade = cmd->msg.fwup_finish_cmd.bl_upgrade;
  msg->msg.fwup_finish_cmd.mode = cmd->msg.fwup_finish_cmd.mode;
  msg->msg.fwup_finish_cmd.mcu_role = cmd->msg.fwup_finish_cmd.mcu_role;
  ipc_proto_free((uint8_t*)cmd);

  // Block new FWUP commands before sending finish to UXC
  fwup_mark_reset_pending();

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response, as we were unable to send the FWUP finish
    // command to the co-processor.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;
    rsp->msg.fwup_finish_rsp.rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
    proto_send_rsp(NULL, rsp);

    user_confirmed_version.valid = false;
    fwup_mark_coproc_pending(false);
    fwup_clear_reset_pending();
  }
}

void fwup_task_handle_coproc_fwup_start(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  const fwpb_fwup_start_rsp_fwup_start_rsp_status rsp_status =
    msg_device->msg.fwup_start_rsp.rsp_status;
  const bool success = (rsp_status == fwpb_fwup_start_rsp_fwup_start_rsp_status_SUCCESS);

  if (awaiting_uxc_confirmation_response) {
    // This is a response to get_confirmation_result for UXC
    awaiting_uxc_confirmation_response = false;

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_fwup_start_result_tag;
    rsp->msg.get_confirmation_result_rsp.result.fwup_start_result.rsp_status = rsp_status;
    rsp->msg.get_confirmation_result_rsp.result.fwup_start_result.max_chunk_size =
      msg_device->msg.fwup_start_rsp.max_chunk_size;

    uc_free_recv_proto(msg_device);
    proto_send_rsp(NULL, rsp);
  } else {
    // Normal UXC FWUP start response (direct, not confirmation)
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = rsp_status;
    rsp->msg.fwup_start_rsp.max_chunk_size = msg_device->msg.fwup_start_rsp.max_chunk_size;
    uc_free_recv_proto(msg_device);

    // Send the IPC message.
    proto_send_rsp(NULL, rsp);
  }

  if (!success) {
    user_confirmed_version.valid = false;
    fwup_mark_coproc_pending(false);
  }
}

void fwup_task_handle_coproc_fwup_transfer(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  const fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status rsp_status =
    msg_device->msg.fwup_transfer_rsp.rsp_status;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_transfer_rsp_tag;
  rsp->msg.fwup_transfer_rsp.rsp_status = rsp_status;
  uc_free_recv_proto(msg_device);

  // Send the IPC message.
  proto_send_rsp(NULL, rsp);

  if (rsp_status != fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS) {
    user_confirmed_version.valid = false;
    fwup_mark_coproc_pending(false);
  }
}

// Show FWUP completion or failure UI and clean up state on failure.
static NO_OPTIMIZE void fwup_coproc_finish_ui(bool success) {
  UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
  if (success && session_defer_commit) {
    // Atomic mode: UXC verification succeeded but signature is not yet committed.
    // Update the cached version so getDeviceInfo() reports the target version to
    // the app, then clear reset_pending so Core FWUP commands are accepted.
    uxc_verified = true;
    if (user_confirmed_version.valid) {
      sysinfo_task_port_set_uxc_pending_version(&user_confirmed_version.version);
    }
    fwup_clear_reset_pending();
    bool is_final = false;
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_COMPLETE, &is_final, sizeof(is_final));
  } else if (success) {
    // Legacy mode: UXC committed and will reset on its own.
    UI_SHOW_EVENT(UI_EVENT_FWUP_COMPLETE);
  } else if (session_defer_commit) {
    // Atomic mode failure: show failure screen while FWUP flow is still
    // active, then deauthenticate and reset.  Deauthenticating first would
    // trigger a lock event that exits the FWUP display flow.
    SECURE_DO({ deauthenticate_without_animation(); });
    UI_SHOW_EVENT(UI_EVENT_FWUP_FAILED);
    rtos_thread_sleep(FWUP_UI_YIELD_MS);
    rtos_thread_sleep(FWUP_SUCCESS_DISPLAY_MS);
    mcu_reset_with_reason(MCU_RESET_FWUP);
  } else {
    // Legacy mode failure: clean up all state.
    SECURE_DO({ deauthenticate(); });
    UI_SHOW_EVENT(UI_EVENT_FWUP_FAILED);
    user_confirmed_version.valid = false;
    fwup_mark_coproc_pending(false);
    fwup_clear_reset_pending();
  }
}

NO_OPTIMIZE void fwup_task_handle_coproc_fwup_finish(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  const fwpb_fwup_finish_rsp_fwup_finish_rsp_status rsp_status =
    msg_device->msg.fwup_finish_rsp.rsp_status;
  uc_free_recv_proto(msg_device);

  if (awaiting_coproc_delta_result) {
    // This is the second fwup_finish_rsp after UXC applied the delta patch.
    // Don't send to host (they already received WILL_APPLY_PATCH).
    awaiting_coproc_delta_result = false;
    nfc_enable(coproc_delta_nfc_token);
    coproc_delta_nfc_token = NFC_CONTROL_INVALID_TOKEN;
    bool success = (rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS);
    LOGI("Delta rsp %d", rsp_status);
    fwup_coproc_finish_ui(success);
    return;
  }

  // First fwup_finish_rsp from UXC (only response for non-delta, first of two for delta).
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;
  rsp->msg.fwup_finish_rsp.rsp_status = rsp_status;
  proto_send_rsp(NULL, rsp);

  if (rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_WILL_APPLY_PATCH) {
    // UXC is applying a delta patch — switch display to "Verifying..." and
    // wait for the second fwup_finish_rsp with the actual result.
    UI_SHOW_EVENT(UI_EVENT_FWUP_VERIFYING);
    // Yield to let the UI task push the display update and the NFC task
    // start transmitting the response before the call to disable NFC.
    rtos_thread_sleep(FWUP_UI_YIELD_MS);

    // Disable NFC on Core while UXC is patching to prevent a new NFC session
    // from interfering.  Re-enabled when the result (or version fallback) arrives.
    coproc_delta_nfc_token = nfc_disable(FWUP_NFC_DISABLE_TIMEOUT);
    awaiting_coproc_delta_result = true;
    return;
  }

  fwup_coproc_finish_ui(rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS);
}

static void _handle_fwup_start(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(fwup_port, proto, sizeof(proto), IPC_FWUP_START_COPROC_RSP);
}

static void _handle_fwup_transfer(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(fwup_port, proto, sizeof(proto), IPC_FWUP_TRANSFER_COPROC_RSP);
}

static void _handle_fwup_finish(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(fwup_port, proto, sizeof(proto), IPC_FWUP_FINISH_COPROC_RSP);
}

static void _handle_commit_sig(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(fwup_port, proto, sizeof(proto), IPC_FWUP_COMMIT_SIG_COPROC_RSP);
}

NO_OPTIMIZE bool fwup_task_port_handle_start_cmd(fwpb_wallet_cmd* cmd) {
  // Pick up defer_commit from Core's start command.
  if (cmd->msg.fwup_start_cmd.defer_commit) {
    session_defer_commit = true;
  }

  // Reject Core FWUP with defer_commit if UXC hasn't successfully verified
  // in this session.  Without a pending UXC signature, the atomic commit
  // would fail at commit time — better to fail fast here.
  if (session_defer_commit && !uxc_verified) {
    LOGW("Core defer_commit rejected: UXC not verified");
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
    proto_send_rsp(cmd, rsp);
    return false;
  }

  SECURE_IF_FAILOUT(fwup_get_require_confirmation() == SECURE_FALSE) {
    // Mfgtest mode: Skip 2-tap confirmation, execute directly
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;

    bool result = fwup_start(&cmd->msg.fwup_start_cmd, &rsp->msg.fwup_start_rsp);
    fwup_mark_pending(result);
    proto_send_rsp(cmd, rsp);

    return result;
  }

  // Prod mode: Skip confirmation if the user already confirmed this version
  // in the current update session (e.g. UXC was confirmed and updated first).
  {
    bool have_incoming_version = cmd->msg.fwup_start_cmd.has_version;

    fwup_confirmation_data_t confirmation_data =
      fwup_build_confirmation_data(&cmd->msg.fwup_start_cmd);

    // Skip confirmation if device not onboarded or if the user already confirmed
    // this version via the FWUP confirmation UI in this session.
    bool skip_confirmation = false;
    if (onboarding_complete() != SECURE_TRUE) {
      skip_confirmation = true;
    } else if (user_confirmed_version.valid && have_incoming_version &&
               fwup_semver_equals(&user_confirmed_version.version,
                                  &cmd->msg.fwup_start_cmd.version)) {
      LOGI("Skip confirmed %lu.%lu.%lu", user_confirmed_version.version.major,
           user_confirmed_version.version.minor, user_confirmed_version.version.patch);
      skip_confirmation = true;
    }

    if (skip_confirmation) {
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;

      bool result = fwup_start(&cmd->msg.fwup_start_cmd, &rsp->msg.fwup_start_rsp);
      fwup_mark_pending(result);

      if (result) {
        // Go straight to in-progress screen (confirmation not needed, e.g.,
        // device not onboarded or version already confirmed earlier in session).
        confirmation_data.skip_confirmation = true;
        UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_START, &confirmation_data, sizeof(confirmation_data));
      }

      proto_send_rsp(cmd, rsp);

      return result;
    }

    // Require user confirmation via standard flow.
    uint8_t response_handle[32];
    uint8_t confirmation_handle[32];

    // Create confirmation
    confirmation_result_t result = confirmation_manager_create(
      CONFIRMATION_TYPE_FWUP_START, &confirmation_data, sizeof(confirmation_data), response_handle,
      sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

    if (result != CONFIRMATION_RESULT_SUCCESS) {
      LOGE("Conf create: %d", result);
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_ERROR;
      proto_send_rsp(cmd, rsp);
      return false;
    }

    // Show FWUP confirmation UI with extended command data including version
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_CONFIRMATION, &confirmation_data,
                            sizeof(confirmation_data));

    // Return CONFIRMATION_PENDING with handles
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
    rsp->response_handle.size = sizeof(response_handle);
    memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
    rsp->confirmation_handle.size = sizeof(confirmation_handle);

    proto_send_rsp(cmd, rsp);
    return true;
  }
}

bool fwup_handle_confirmation_result(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);

  // Validate handles
  confirmation_result_t result =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (result != CONFIRMATION_RESULT_SUCCESS) {
    // Check if this is "not approved yet" vs "invalid/expired/rejected"
    if (result == CONFIRMATION_RESULT_NOT_APPROVED && confirmation_manager_is_pending()) {
      // User hasn't approved yet (or rejected)
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_CONFIRMATION_PENDING;
      proto_send_rsp(cmd, rsp);
      return true;  // Not an error, just waiting for user
    } else {
      LOGE("Conf validate: %d", result);
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
      proto_send_rsp(cmd, rsp);
      return false;
    }
  }

  // Retrieve saved FWUP command from confirmation manager
  fwup_confirmation_data_t saved_data;
  size_t data_size;
  if (!confirmation_manager_get_operation_data(CONFIRMATION_TYPE_FWUP_START, &saved_data,
                                               &data_size)) {
    LOGE("FWUP cmd retrieve fail");
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->status = fwpb_status_ERROR;
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Extract the command from confirmation data
  fwpb_fwup_start_cmd saved_cmd = saved_data.cmd;

  if ((saved_cmd.mcu_role != fwpb_mcu_role_MCU_ROLE_CORE &&
       saved_cmd.mcu_role != fwpb_mcu_role_MCU_ROLE_UXC) ||
      fwup_should_reject_cmd()) {
    LOGW("Conf reject %d", saved_cmd.mcu_role);

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_fwup_start_result_tag;
    rsp->msg.get_confirmation_result_rsp.result.fwup_start_result.rsp_status =
      fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
    rsp->msg.get_confirmation_result_rsp.result.fwup_start_result.max_chunk_size = 0;

    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Record the confirmed version so the next FWUP start (for the other chip)
  // can skip confirmation if it targets the same version.
  if (saved_cmd.has_version) {
    user_confirmed_version.version = saved_cmd.version;
    user_confirmed_version.valid = true;
  } else {
    user_confirmed_version.valid = false;
  }

  // Check if this is a UXC update confirmation
  if (saved_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC) {
    // Forward to UXC over UART
    fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
    if (msg == NULL) {
      LOGE("UXC msg alloc fail");
      user_confirmed_version.valid = false;
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_ERROR;
      proto_send_rsp(cmd, rsp);
      confirmation_manager_clear();
      return false;
    }

    msg->which_msg = fwpb_uxc_msg_host_fwup_start_cmd_tag;
    msg->msg.fwup_start_cmd = saved_cmd;

    // Set flag to handle async UXC response
    awaiting_uxc_confirmation_response = true;

    const bool sent = uc_send(msg);
    if (!sent) {
      LOGE("UXC FWUP send fail");
      awaiting_uxc_confirmation_response = false;
      user_confirmed_version.valid = false;
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_ERROR;
      proto_send_rsp(cmd, rsp);
      confirmation_manager_clear();
      return false;
    }

    fwup_mark_coproc_pending(true);

    // Show UI transition to in-progress
    UI_SHOW_EVENT(UI_EVENT_FWUP_START);

    // Clean up confirmation state
    confirmation_manager_clear();

    // Response will come asynchronously via _handle_fwup_start callback
    // Don't send response here - it will be sent from fwup_task_handle_coproc_fwup_start
    ipc_proto_free((uint8_t*)cmd);
    return true;
  } else {
    // Execute the saved FWUP start command (CORE MCU path)
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_fwup_start_result_tag;

    bool fwup_success =
      fwup_start(&saved_cmd, &rsp->msg.get_confirmation_result_rsp.result.fwup_start_result);

    // Mark FWUP as pending if successful
    fwup_mark_pending(fwup_success);

    if (!fwup_success) {
      user_confirmed_version.valid = false;
    }

    if (fwup_success) {
      // Transition UI from scanning to in-progress screen
      UI_SHOW_EVENT(UI_EVENT_FWUP_START);
    }

    // Clean up confirmation state
    confirmation_manager_clear();

    proto_send_rsp(cmd, rsp);
    return true;
  }
}

bool fwup_task_port_is_deferred_session(void) {
  return session_defer_commit;
}

bool fwup_task_port_try_atomic_commit(void) {
  if (!session_defer_commit || !fwup_is_coproc_pending() || !user_confirmed_version.valid) {
    return false;
  }

  fwpb_semver core_version = {0};
  if (!fwup_get_target_version(&core_version)) {
    LOGE("Core ver unavail");
    return false;
  }

  return fwup_atomic_commit(&core_version, &user_confirmed_version.version);
}

static bool fwup_atomic_commit(const fwpb_semver* core_version, const fwpb_semver* uxc_version) {
  // Phase 2 step 3: Stage Core's signature to filesystem.
  const uint8_t* pending_sig = fwup_get_pending_signature();
  if (pending_sig == NULL) {
    LOGE("No pending sig for atomic commit");
    return false;
  }

  fwup_staged_sig_t staged = {0};
  memcpy(staged.signature, pending_sig, ECC_SIG_SIZE);
  staged.target_slot = fwup_target_slot();
  staged.core_target_version = *core_version;
  staged.uxc_target_version = *uxc_version;

  if (!fwup_staged_sig_write(&staged)) {
    LOGE("Staged sig write fail");
    return false;
  }
  // Phase 2 step 4: Send commit command to UXC.
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  if (msg == NULL) {
    LOGE("Commit sig alloc fail");
    fwup_staged_sig_remove();
    return false;
  }

  msg->which_msg = fwpb_uxc_msg_host_fwup_commit_sig_cmd_tag;
  if (!uc_send(msg)) {
    // Don't delete staged_sig here — UXC may have received the message
    // and committed before the ACK was lost.  Recovery will check UXC's
    // booted version and either complete or abort.
    LOGE("Commit sig send fail");
    return false;
  }

  awaiting_atomic_commit_rsp = true;
  // Block new FWUP commands while waiting for the commit ACK — a new
  // fwup_start would clear the pending signature buffer that we need
  // to commit when the ACK arrives.
  fwup_mark_reset_pending();
  return true;
}

NO_OPTIMIZE void fwup_task_handle_commit_sig_rsp(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  const fwpb_fwup_commit_sig_rsp_fwup_commit_sig_rsp_status rsp_status =
    msg_device->msg.fwup_commit_sig_rsp.rsp_status;
  uc_free_recv_proto(msg_device);

  awaiting_atomic_commit_rsp = false;

  if (rsp_status != fwpb_fwup_commit_sig_rsp_fwup_commit_sig_rsp_status_SUCCESS) {
    LOGE("UXC commit sig fail %d", rsp_status);
    fwup_staged_sig_remove();
    fwup_coproc_finish_ui(false);
    return;
  }

  // Phase 2 steps 5-9: UXC committed. Now commit Core.
  LOGI("UXC sig committed");

  // Commit Core's own signature.
  if (!fwup_commit_signature()) {
    // UXC already committed but Core's flash write failed.  Delete the
    // staged file rather than resetting into recovery — a persistent
    // flash fault would cause an infinite reset loop since recovery
    // attempts the same write.  The resulting mismatch (UXC new, Core
    // old) is recoverable: the app detects it via getDeviceInfo() and
    // can re-send Core's FWUP.
    LOGE("Core sig commit fail");
    fwup_staged_sig_remove();
    fwup_coproc_finish_ui(false);
    return;
  }

  fwup_staged_sig_remove();

  // Show success while UXC display is still alive, then hold UXC in reset
  // before resetting Core so both boot into new firmware together.
  UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
  bool is_final = true;
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_COMPLETE, &is_final, sizeof(is_final));

  rtos_thread_sleep(FWUP_SUCCESS_DISPLAY_MS);

  // Now hold UXC in reset — display goes dark, then Core resets.
  coproc_power_assert_reset();
  // On reboot, Core will release UXC and both boot into new firmware.
  mcu_reset_with_reason(MCU_RESET_FWUP);
}

void fwup_task_handle_coproc_version(ipc_ref_t* message) {
  ASSERT(message != NULL);
  ASSERT(message->object != NULL);

  // If we were waiting for a delta patch result that never arrived, UXC rebooted
  // without sending it (e.g. UART failure). Check whether UXC booted into the
  // confirmed version to determine success or failure. If no confirmed version is
  // available (e.g. confirmation was skipped), assume success — this is best-effort.
  // TODO(W-16671): Improve by gating UXC boot on verified image report to Core.
  if (awaiting_coproc_delta_result) {
    awaiting_coproc_delta_result = false;
    nfc_enable(coproc_delta_nfc_token);
    coproc_delta_nfc_token = NFC_CONTROL_INVALID_TOKEN;

    bool success = true;
    fwup_coproc_version_t* version_msg = (fwup_coproc_version_t*)message->object;
    if (user_confirmed_version.valid) {
      success = fwup_semver_equals(&user_confirmed_version.version, &version_msg->version);
      LOGW("Delta rsp lost, ver %s", success ? "ok" : "mismatch");
    } else {
      LOGW("Delta rsp lost, no confirmed ver");
    }
    fwup_coproc_finish_ui(success);
  }

  // Check for a staged signature from an interrupted atomic commit.
  // This handles both power-loss recovery (on fresh boot) and UXC crash/timeout
  // during the commit protocol (UXC reboots mid-session and reports its version).
  // In either case the staged_sig file is the durable intent record, and UXC's
  // reported version is the ground truth for whether UXC committed.
  {
    fwup_coproc_version_t* version_msg = (fwup_coproc_version_t*)message->object;
    fwup_staged_sig_t staged;

    if (fwup_staged_sig_read(&staged)) {
      if (fwup_semver_equals(&staged.uxc_target_version, &version_msg->version)) {
        LOGI("Recovering staged sig");
        if (fwup_staged_sig_commit_to_flash(&staged)) {
          fwup_staged_sig_remove();
          LOGI("Recovery complete, resetting");
          mcu_reset_with_reason(MCU_RESET_FWUP);
        } else {
          LOGE("Recovery sig write fail");
        }
      } else {
        LOGW("Recovery abort: UXC ver mismatch");
      }
      // Clean up staged file on all non-reset paths.
      fwup_staged_sig_remove();
    }
  }

  // UXC has rebooted and reported version; clear atomic commit state.
  // Preserve user_confirmed_version so legacy (non-deferred) Core FWUP
  // can skip re-confirmation after UXC resets.
  awaiting_atomic_commit_rsp = false;
  uxc_verified = false;
  fwup_mark_coproc_pending(false);
  // If Core's own FWUP is still in progress, preserve session_defer_commit
  // so the is_deferred_session guard prevents Core from committing alone.
  // The atomic commit will fail naturally (UXC has no pending sig).
  if (!fwup_in_progress()) {
    session_defer_commit = false;
  }
  fwup_clear_reset_pending();
}
