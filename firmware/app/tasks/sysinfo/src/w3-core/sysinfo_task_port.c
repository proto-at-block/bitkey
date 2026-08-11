#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "auth.h"
#include "bitlog.h"
#include "confirmation_manager.h"
#include "coproc_power.h"
#include "display.pb.h"
#include "exti.h"
#include "ipc.h"
#include "kv.h"
#include "log.h"
#include "mcu_devinfo.h"
#include "mcu_reset.h"
#include "metadata.h"
#include "onboarding.h"
#include "power.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "secure_channel.h"
#include "secure_channel_cert.h"
#include "secutils.h"
#include "sleep.h"
#include "sysevent.h"
#include "sysinfo.h"
#include "sysinfo_task_impl.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <string.h>

// Must match BRIGHTNESS_MIN/MAX in ui.h (UXC-side header, not available here)
#define BRIGHTNESS_SAVE_MIN 15
#define BRIGHTNESS_SAVE_MAX 100
#define BRIGHTNESS_DEFAULT  80

// Forward declarations
static NO_OPTIMIZE bool wipe_state_confirmation_result_handler(ipc_ref_t* message);

/** Pending host request for peer cert */
static SHARED_TASK_BSS bool peer_cert_request_pending = false;
static SHARED_TASK_BSS uint32_t peer_cert_request_seq = 0;
static SHARED_TASK_BSS bool host_peer_cert_requests_allowed = false;

/**
 * @brief Delay, after power off, to check for a touch event if USB is plugged
 * in, preventing power off.
 */
#define SYSINFO_POWER_OFF_TOUCH_DELAY_MS (50)

/**
 * @brief Polling frequency to check for a touch or USB un-plug event during
 * device power off.
 */
#define SYSINFO_POWER_OFF_POLL_MS (10)

/**
 * @brief Grace period after `power_set_retain(false)` for hardware to
 * actually cut power. If we're still executing after this, something
 * external is holding the rail up — reset rather than zombify.
 */
#define SYSINFO_POWER_OFF_GRACE_MS (100)

/**
 * @brief Delay after wipe success before rebooting.
 *
 * This gives the host time to receive the success response before the core
 * resets back into onboarding.
 */
#define WIPE_RESET_DELAY_MS (500u)

extern power_config_t power_config;

static SHARED_TASK_BSS device_info_t device_info_for_ui;
static SHARED_TASK_BSS fwpb_device_info_rsp_device_info_mcu uxc_mcu_info = {0};
static SHARED_TASK_BSS uint32_t coproc_metadata_seq = 0;

static void _sysinfo_task_handle_coproc_boot_message(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_metadata(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_coredump(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_events(void* proto, void* UNUSED(context));

static void send_initial_device_info(void) {
  memset(&device_info_for_ui, 0, sizeof(device_info_for_ui));

  // Load device info from metadata
  metadata_t metadata = {0};
  fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
  if (metadata_get_active_slot(&metadata, &active_slot) == METADATA_VALID) {
    snprintf(device_info_for_ui.firmware_version, sizeof(device_info_for_ui.firmware_version),
             "v%u.%u.%u", metadata.version.major, metadata.version.minor, metadata.version.patch);
    strncpy(device_info_for_ui.hardware_version, metadata.hardware_revision,
            sizeof(device_info_for_ui.hardware_version) - 1);
  } else {
    strncpy(device_info_for_ui.firmware_version, "Unknown",
            sizeof(device_info_for_ui.firmware_version) - 1);
    strncpy(device_info_for_ui.hardware_version, "Unknown",
            sizeof(device_info_for_ui.hardware_version) - 1);
  }

  // Get device serial number
  uint32_t serial_length = sizeof(device_info_for_ui.serial_number) - 1;
  if (!sysinfo_assy_serial_read(device_info_for_ui.serial_number, &serial_length) ||
      serial_length == 0) {
    strncpy(device_info_for_ui.serial_number, "Unknown",
            sizeof(device_info_for_ui.serial_number) - 1);
  }

  // Load brightness from KV
  device_info_for_ui.brightness_percent = BRIGHTNESS_DEFAULT;
  uint8_t brightness_len = sizeof(device_info_for_ui.brightness_percent);
  kv_result_t result = kv_get("disp_bri", &device_info_for_ui.brightness_percent, &brightness_len);
  if (result != KV_ERR_NONE && result != KV_ERR_NOT_FOUND) {
    LOGE("KV brightness load err=%d", result);
    device_info_for_ui.brightness_percent = BRIGHTNESS_DEFAULT;
  }
  // Clamp in case flash contains a corrupted value
  if (device_info_for_ui.brightness_percent < BRIGHTNESS_SAVE_MIN ||
      device_info_for_ui.brightness_percent > BRIGHTNESS_SAVE_MAX) {
    device_info_for_ui.brightness_percent = BRIGHTNESS_DEFAULT;
  }

  // Send device info with brightness to UI task
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_SET_DEVICE_INFO, &device_info_for_ui,
                          sizeof(device_info_for_ui));
}

NO_OPTIMIZE static void handle_set_brightness_internal(ipc_ref_t* message) {
  sysinfo_set_brightness_internal_t* req = (sysinfo_set_brightness_internal_t*)message->object;

  // Clamp to valid range to prevent persisting out-of-bounds brightness
  if (req->brightness_percent < BRIGHTNESS_SAVE_MIN) {
    req->brightness_percent = BRIGHTNESS_SAVE_MIN;
  } else if (req->brightness_percent > BRIGHTNESS_SAVE_MAX) {
    req->brightness_percent = BRIGHTNESS_SAVE_MAX;
  }

  kv_result_t result =
    kv_set("disp_bri", &req->brightness_percent, sizeof(req->brightness_percent));
  if (result != KV_ERR_NONE) {
    LOGE("KV brightness save err=%d", result);
  }
}

void sysinfo_task_port_send_device_info(void) {
  send_initial_device_info();
}

static void _sysinfo_task_handle_device_get_cert_cmd(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_UXC_GET_CERT_CMD);
}
static void _sysinfo_task_handle_device_get_cert_rsp(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_UXC_GET_CERT_RSP);
}

void sysinfo_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_boot_status_msg_tag,
                    _sysinfo_task_handle_coproc_boot_message, NULL);
  uc_route_register(fwpb_uxc_msg_device_meta_rsp_tag, _sysinfo_task_handle_coproc_metadata, NULL);
  uc_route_register(fwpb_uxc_msg_device_coredump_get_rsp_tag, _sysinfo_task_handle_coproc_coredump,
                    NULL);
  uc_route_register(fwpb_uxc_msg_device_events_get_rsp_tag, _sysinfo_task_handle_coproc_events,
                    NULL);
  uc_route_register(fwpb_uxc_msg_device_cert_get_cmd_tag, _sysinfo_task_handle_device_get_cert_cmd,
                    NULL);
  uc_route_register(fwpb_uxc_msg_device_cert_get_rsp_tag, _sysinfo_task_handle_device_get_cert_rsp,
                    NULL);

  // Register wipe state confirmation result handler for two-tap wipe flow
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_WIPE_STATE,
                                               wipe_state_confirmation_result_handler);
}

static void handle_device_get_cert_rsp(ipc_ref_t* message) {
  fwpb_uxc_msg_device* msg_device = message->object;
  ASSERT(msg_device->which_msg == fwpb_uxc_msg_device_cert_get_rsp_tag);
  fwpb_cert_get_rsp* get_cert_rsp = &msg_device->msg.cert_get_rsp;

  if (peer_cert_request_pending) {
    uint32_t seq = 0;
    if (!proto_uxc_take_rsp_seq(msg_device, &seq, "sysinfo cert")) {
      // Peer cert is not part of the old-UXC FWUP recovery surface. Missing seq
      // cannot satisfy this strict host request; clear the latch so a retry can
      // send a fresh request rather than adding another cached/timeout path.
      // Keep mismatched nonzero seqs from consuming the active request below.
      peer_cert_request_pending = false;
      peer_cert_request_seq = 0;
      uc_free_recv_proto(msg_device);
      return;
    }
    if (seq != peer_cert_request_seq) {
      LOGW("Dropping stale sysinfo cert seq %lu expected %lu", (unsigned long)seq,
           (unsigned long)peer_cert_request_seq);
      uc_free_recv_proto(msg_device);
      return;
    }
    peer_cert_request_pending = false;
    peer_cert_request_seq = 0;

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_cert_get_rsp_tag;
    memcpy(&rsp->msg.cert_get_rsp, get_cert_rsp, sizeof(*get_cert_rsp));

    uc_free_recv_proto(msg_device);
    proto_send_rsp_with_seq(seq, rsp);
    return;
  }

  host_peer_cert_requests_allowed = true;

  if (get_cert_rsp->rsp_status != fwpb_cert_get_rsp_cert_get_rsp_status_SUCCESS) {
    LOGE("Cert rsp err: %d", get_cert_rsp->rsp_status);
    uc_free_recv_proto(msg_device);
    return;
  }

  secure_channel_cert_data_t* cert_data = (secure_channel_cert_data_t*)&get_cert_rsp->cert.bytes;
  if (cert_data->type != CERT_TYPE_PICOCERT) {
    LOGE("Bad cert type");
    uc_free_recv_proto(msg_device);
    return;
  }
  if (strncmp(cert_data->data.picocert.subject, SC_CERT_UXC_ID,
              sizeof(cert_data->data.picocert.subject)) != 0) {
    LOGE("Bad cert subj");
    uc_free_recv_proto(msg_device);
    return;
  }

  secure_channel_cert_err_t err = secure_channel_pin_cert(cert_data);
  if (err != SECURE_CHANNEL_CERT_OK) {
    LOGE("Cert pin err: %d", err);
    uc_free_recv_proto(msg_device);
    return;
  } else {
    LOGI("Pinned %s", cert_data->data.picocert.subject);
    // Try starting up secure channel now that we have a cert
    ipc_send(key_manager_port, NULL, 0, IPC_KEY_MANAGER_UXC_SESSION_INIT);
  }
  uc_free_recv_proto(msg_device);
}

static void handle_device_get_cert_cmd(ipc_ref_t* message) {
  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_cert_get_cmd* cmd = &msg_device->msg.cert_get_cmd;

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);
  msg->which_msg = fwpb_uxc_msg_host_cert_get_rsp_tag;
  fwpb_cert_get_rsp* rsp = &msg->msg.cert_get_rsp;

  secure_channel_cert_handle_cmd_get(cmd, rsp);

  uc_free_recv_proto(msg_device);
  (void)uc_send(msg);
}

bool sysinfo_task_port_handle_host_secure_channel_cert_get(fwpb_wallet_cmd* cmd,
                                                           fwpb_wallet_rsp* rsp) {
  ASSERT(cmd != NULL && rsp != NULL);

  const fwpb_cert_get_cmd* cert_cmd = &cmd->msg.cert_get_cmd;
  const char* cert_id = cert_cmd->cert_id;

  if (strcmp(cert_id, SC_CERT_CORE_ID) != 0 && strcmp(cert_id, SC_CERT_UXC_ID) != 0) {
    rsp->msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
    return true;
  }

  if (cert_cmd->cert_source != fwpb_cert_get_cmd_cert_origin_CERT_ORIGIN_PEER) {
    // Return local cert
    if (!secure_channel_cert_handle_cmd_get(&cmd->msg.cert_get_cmd, &rsp->msg.cert_get_rsp)) {
      rsp->msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
    }
    return true;
  }

  // Request cert from UXC

  // Reject request if we are already waiting on a response or if the device is not ready yet
  if (peer_cert_request_pending || !host_peer_cert_requests_allowed) {
    rsp->msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
    return true;
  }

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_host_cert_get_cmd_tag;
  msg->msg.cert_get_cmd.kind = fwpb_cert_get_cmd_cert_type_DEVICE_SECURE_CHANNEL_CERT;
  strncpy(msg->msg.cert_get_cmd.cert_id, cert_id, sizeof(msg->msg.cert_get_cmd.cert_id) - 1);
  msg->msg.cert_get_cmd.cert_id[sizeof(msg->msg.cert_get_cmd.cert_id) - 1] = '\0';

  proto_uxc_prepare_cmd(msg, cmd);
  peer_cert_request_seq = proto_get_cmd_seq(cmd);
  peer_cert_request_pending = true;

  if (!uc_send(msg)) {
    peer_cert_request_pending = false;
    peer_cert_request_seq = 0;
    rsp->msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
    return true;
  }

  return false;
}

bool sysinfo_task_port_handle_message(ipc_ref_t* message) {
  switch (message->tag) {
    case IPC_SYSINFO_SET_BRIGHTNESS_INTERNAL:
      handle_set_brightness_internal(message);
      return true;
    case IPC_SYSINFO_UXC_GET_CERT_CMD:
      handle_device_get_cert_cmd(message);
      return true;
    case IPC_SYSINFO_UXC_GET_CERT_RSP:
      handle_device_get_cert_rsp(message);
      return true;
    default:
      return false;
  }
}

void sysinfo_task_handle_coproc_boot(ipc_ref_t* message) {
  sysevent_clear(SYSEVENT_COPROC_BOOT);

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  peer_cert_request_pending = false;
  peer_cert_request_seq = 0;
  coproc_metadata_seq = 0;
  host_peer_cert_requests_allowed = false;

  secure_channel_cert_data_t cert_data;
  bool has_uxc_cert = secure_channel_read_cert(SC_CERT_UXC_ID, &cert_data);

  msg->which_msg = fwpb_uxc_msg_host_boot_status_msg_tag;
  fwpb_uxc_boot_status_msg* rsp = &msg->msg.boot_status_msg;
  rsp->mcu_id = fwpb_uxc_boot_status_msg_uxc_mcu_id_CORE;

  if (has_uxc_cert) {
    rsp->auth_status = fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNLOCKED;
  } else {
    rsp->auth_status = fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNAUTHENTICATED;
  }

  // Push our loaded device serial to UXC so its Memfault events report the
  // same device identity as ours. UXC has no copy of its own. Skip the push
  // when sysinfo_load failed and we're holding the "XXXXXXXXXXXXXXXX"
  // placeholder — otherwise we'd just hand UXC our placeholder, which is
  // worse than letting it keep its own.
  const sysinfo_t* const local_sysinfo = sysinfo_get();
  if (local_sysinfo->serial[0] != 'X') {
    rsp->serial.size = SYSINFO_SERIAL_NUMBER_LENGTH;
    memcpy(rsp->serial.bytes, local_sysinfo->serial, SYSINFO_SERIAL_NUMBER_LENGTH);
  }

  // ACK immediately since we may want to send a followup cert request.
  (void)uc_send_immediate(msg);

  sysinfo_boot_status_t* boot_status = (sysinfo_boot_status_t*)message->object;

  if (has_uxc_cert) {
    host_peer_cert_requests_allowed = true;
    if (boot_status->auth_status != fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNAUTHENTICATED) {
      // We have a cert so we can start up key agreement
      ipc_send(key_manager_port, NULL, 0, IPC_KEY_MANAGER_UXC_SESSION_INIT);
    } else {
      LOGW("%s cert unauth", SC_CERT_UXC_ID);
    }
  } else if (!has_uxc_cert) {
    LOGW("No %s cert", SC_CERT_UXC_ID);
    msg = uc_alloc_send_proto();
    ASSERT(msg != NULL);
    msg->which_msg = fwpb_uxc_msg_host_cert_get_cmd_tag;
    msg->msg.cert_get_cmd.kind = fwpb_cert_get_cmd_cert_type_DEVICE_SECURE_CHANNEL_CERT;
    strncpy(msg->msg.cert_get_cmd.cert_id, SC_CERT_UXC_ID, sizeof(msg->msg.cert_get_cmd.cert_id));
    (void)uc_send(msg);
  }
}

void sysinfo_task_handle_coproc_metadata(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  uint32_t seq = 0;
  if (coproc_metadata_seq == 0) {
    LOGW("Dropping unexpected sysinfo metadata response");
    uc_free_recv_proto(msg_device);
    return;
  }

  if (msg_device->seq == 0) {
    // Old-UXC recovery exception: host bundle FWUP needs UXC metadata to choose
    // the target slot/image before it can update old UXC into the seq-capable
    // protocol. Accept exactly one seqless metadata response while a metadata
    // request is pending; other UXC-backed sysinfo/mfgtest responses remain
    // strict-seq because they are not required for recovery.
    LOGW("Accepting seqless sysinfo metadata response");
    seq = coproc_metadata_seq;
  } else if (!proto_uxc_take_rsp_seq(msg_device, &seq, "sysinfo metadata")) {
    uc_free_recv_proto(msg_device);
    return;
  } else if (seq != coproc_metadata_seq) {
    LOGW("Dropping stale sysinfo metadata seq %lu expected %lu", (unsigned long)seq,
         (unsigned long)coproc_metadata_seq);
    uc_free_recv_proto(msg_device);
    return;
  }

  coproc_metadata_seq = 0;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
  memcpy(&rsp->msg.meta_rsp, &msg_device->msg.meta_rsp, sizeof(msg_device->msg.meta_rsp));
  uc_free_recv_proto(msg_device);

  proto_send_rsp_with_seq(seq, rsp);
}

void sysinfo_task_handle_coproc_coredump(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  uint32_t seq = 0;
  if (!proto_uxc_take_rsp_seq(msg_device, &seq, "sysinfo coredump")) {
    uc_free_recv_proto(msg_device);
    return;
  }
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_coredump_get_rsp_tag;
  rsp->msg.coredump_get_rsp.rsp_status = msg_device->msg.coredump_get_rsp.rsp_status;
  rsp->msg.coredump_get_rsp.coredump_count = msg_device->msg.coredump_get_rsp.coredump_count;
  rsp->msg.coredump_get_rsp.mcu_role = msg_device->msg.coredump_get_rsp.mcu_role;
  rsp->msg.coredump_get_rsp.mcu_name = msg_device->msg.coredump_get_rsp.mcu_name;
  rsp->msg.coredump_get_rsp.has_coredump_fragment =
    msg_device->msg.coredump_get_rsp.has_coredump_fragment;

  // If there is a coredump fragment present, then copy it over.
  if (rsp->msg.coredump_get_rsp.has_coredump_fragment) {
    rsp->msg.coredump_get_rsp.coredump_fragment.offset =
      msg_device->msg.coredump_get_rsp.coredump_fragment.offset;
    rsp->msg.coredump_get_rsp.coredump_fragment.complete =
      msg_device->msg.coredump_get_rsp.coredump_fragment.complete;
    rsp->msg.coredump_get_rsp.coredump_fragment.coredumps_remaining =
      msg_device->msg.coredump_get_rsp.coredump_fragment.coredumps_remaining;

    const size_t coredump_size =
      BLK_MIN(msg_device->msg.coredump_get_rsp.coredump_fragment.data.size,
              sizeof(rsp->msg.coredump_get_rsp.coredump_fragment.data.bytes));
    memcpy(rsp->msg.coredump_get_rsp.coredump_fragment.data.bytes,
           msg_device->msg.coredump_get_rsp.coredump_fragment.data.bytes, coredump_size);
    rsp->msg.coredump_get_rsp.coredump_fragment.data.size = coredump_size;
  }
  uc_free_recv_proto(msg_device);

  proto_send_rsp_with_seq(seq, rsp);
}

void sysinfo_task_handle_coproc_events(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  uint32_t seq = 0;
  if (!proto_uxc_take_rsp_seq(msg_device, &seq, "sysinfo events")) {
    uc_free_recv_proto(msg_device);
    return;
  }
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_events_get_rsp_tag;
  rsp->msg.events_get_rsp.rsp_status = msg_device->msg.events_get_rsp.rsp_status;
  rsp->msg.events_get_rsp.version = msg_device->msg.events_get_rsp.version;
  rsp->msg.events_get_rsp.mcu_role = msg_device->msg.events_get_rsp.mcu_role;
  rsp->msg.events_get_rsp.has_fragment = msg_device->msg.events_get_rsp.has_fragment;

  // If there is a fragment present, then copy it over.
  if (rsp->msg.events_get_rsp.has_fragment) {
    rsp->msg.events_get_rsp.fragment.remaining_size =
      msg_device->msg.events_get_rsp.fragment.remaining_size;

    const size_t fragment_size = BLK_MIN(msg_device->msg.events_get_rsp.fragment.data.size,
                                         sizeof(rsp->msg.events_get_rsp.fragment.data.bytes));
    memcpy(rsp->msg.events_get_rsp.fragment.data.bytes,
           msg_device->msg.events_get_rsp.fragment.data.bytes, fragment_size);
    rsp->msg.events_get_rsp.fragment.data.size = fragment_size;
  }
  uc_free_recv_proto(msg_device);

  proto_send_rsp_with_seq(seq, rsp);
}

void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd) {
  const uint8_t mcu_role = cmd->msg.meta_cmd.mcu_role;
  if (coproc_metadata_seq != 0) {
    // Do not replace a pending metadata request on retry. Old UXC metadata
    // responses can be seqless, so replacing the pending seq would allow a
    // stale response from the abandoned request to satisfy the retry. UXC
    // boot/reset clears the latch if the response is truly lost.
    LOGW("UXC metadata response pending; wait for response/reset");
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
    rsp->msg.meta_rsp.rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
    rsp->msg.meta_rsp.mcu_role = mcu_role;
    proto_send_rsp(cmd, rsp);
    return;
  }

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_meta_cmd_tag;
  msg->msg.meta_cmd.mcu_role = mcu_role;
  const uint32_t cmd_seq = proto_get_cmd_seq(cmd);
  proto_uxc_prepare_cmd(msg, cmd);
  coproc_metadata_seq = cmd_seq;
  proto_free_buffers(cmd, NULL);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
    rsp->msg.meta_rsp.rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
    rsp->msg.meta_rsp.mcu_role = mcu_role;
    proto_send_rsp_with_seq(cmd_seq, rsp);
    coproc_metadata_seq = 0;
  }
}

void sysinfo_task_request_coproc_coredump(fwpb_wallet_cmd* cmd) {
  const uint8_t mcu_role = cmd->msg.coredump_get_cmd.mcu_role;
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_coredump_get_cmd_tag;
  msg->msg.coredump_get_cmd.type = cmd->msg.coredump_get_cmd.type;
  msg->msg.coredump_get_cmd.offset = cmd->msg.coredump_get_cmd.offset;
  msg->msg.coredump_get_cmd.mcu_role = mcu_role;
  const uint32_t cmd_seq = proto_get_cmd_seq(cmd);
  proto_uxc_prepare_cmd(msg, cmd);
  proto_free_buffers(cmd, NULL);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_coredump_get_rsp_tag;
    rsp->msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
    rsp->msg.coredump_get_rsp.mcu_role = mcu_role;
    proto_send_rsp_with_seq(cmd_seq, rsp);
  }
}

void sysinfo_task_request_coproc_events(fwpb_wallet_cmd* cmd) {
  const uint8_t mcu_role = cmd->msg.events_get_cmd.mcu_role;
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_events_get_cmd_tag;
  msg->msg.events_get_cmd.mcu_role = mcu_role;
  const uint32_t cmd_seq = proto_get_cmd_seq(cmd);
  proto_uxc_prepare_cmd(msg, cmd);
  proto_free_buffers(cmd, NULL);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_events_get_rsp_tag;
    rsp->msg.events_get_rsp.rsp_status = fwpb_events_get_rsp_events_get_rsp_status_ERROR;
    rsp->msg.events_get_rsp.mcu_role = mcu_role;
    proto_send_rsp_with_seq(cmd_seq, rsp);
  }
}

static void _sysinfo_task_handle_coproc_boot_message(void* proto, void* UNUSED(context)) {
  static sysinfo_boot_status_t sysinfo_boot_status SHARED_TASK_BSS;

  fwpb_uxc_boot_status_msg* msg = &((fwpb_uxc_msg_device*)proto)->msg.boot_status_msg;
  uxc_mcu_info.mcu_role = fwpb_mcu_role_MCU_ROLE_UXC;
  uxc_mcu_info.mcu_name = fwpb_mcu_name_MCU_NAME_STM32U5;
  uxc_mcu_info.version.major = msg->version.major;
  uxc_mcu_info.version.minor = msg->version.minor;
  uxc_mcu_info.version.patch = msg->version.patch;
  uxc_mcu_info.has_version = true;
  uxc_mcu_info.active_slot = msg->active_slot;
  if (msg->chip_id.size > 0) {
    PROTO_FILL_BYTES(&uxc_mcu_info, chip_id, msg->chip_id.bytes, msg->chip_id.size);
  } else {
    uxc_mcu_info.chip_id.size = 0;
  }

  sysinfo_boot_status.auth_status = msg->auth_status;

  secure_uart_channel_reset_session();
  sysevent_set(SYSEVENT_COPROC_BOOT);
  ipc_send(sysinfo_port, &sysinfo_boot_status, sizeof(sysinfo_boot_status),
           IPC_SYSINFO_BOOT_STATUS);
  // Notify fwup task of UXC version.
  static fwup_coproc_version_t SHARED_TASK_BSS fwup_version;
  fwup_version.version = msg->version;
  ipc_send(fwup_port, &fwup_version, sizeof(fwup_version), IPC_FWUP_COPROC_VERSION);

  uc_free_recv_proto(proto);
}

static void _sysinfo_task_handle_coproc_metadata(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_COPROC_METADATA);
}

void sysinfo_task_port_prepare_power_down(void) {
  // Latch the sleep subsystem so no event (NFC, captouch, UXC touch, charger
  // transition, etc.) can re-arm the countdown timer once the shutdown
  // sequence has begun. Idempotent — `power_system_down_callback` typically
  // latched first on the timer-task side, so the latch is the steady-state
  // signal on entry from the timer path. We deliberately do not bail on
  // already-latched here: that would short-circuit the timer-initiated
  // shutdown entirely (the callback latches before posting the IPC).
  // Duplicate execution is benign — sequential dispatch + first-call resets
  // the MCU; for the COPROC_BOOT bail path, re-entry via a follow-up
  // `IPC_SYSINFO_POWER_OFF_REQUESTED` after `sleep_cancel_shutdown()` is
  // the intended retry path.
  sleep_begin_shutdown();

  UI_SHOW_EVENT(UI_EVENT_POWER_OFF);

  // If USB is plugged in, we cannot power off, so instead we turn off the
  // screen and poll until USB is un-plugged.
  if (power_is_plugged_in()) {
    MFLOGI("prepare_power_down: USB plugged, polling");
    rtos_thread_sleep(SYSINFO_POWER_OFF_TOUCH_DELAY_MS);
    const sysevent_t touch_events = SYSEVENT_TOUCH | SYSEVENT_CAPTOUCH;
    sysevent_clear(touch_events);

    // If UXC reboots while sysinfo is parked here, the boot-status IPC cannot
    // be handled to rekey UC. Return to the sysinfo loop so it can process the
    // queued boot status and start IPC_KEY_MANAGER_UXC_SESSION_INIT.
    const sysevent_t wake_events = touch_events | SYSEVENT_COPROC_BOOT;

    // If there is a touch event (captouch or screen) while USB is plugged
    // in, we exit to reset under the assumption that the user wants to
    // use their device.
    uint32_t poll_iters = 0;
    while (power_is_plugged_in() && !sysevent_get(wake_events)) {
      rtos_thread_sleep(SYSINFO_POWER_OFF_POLL_MS);
      // Heartbeat so a stuck poll loop is visible in Memfault.
      poll_iters++;
      if (poll_iters % 100 == 0) {
        MFLOGW("prepare_power_down: poll stuck iters=%lu", (unsigned long)poll_iters);
      }
    }
    MFLOGI("prepare_power_down: poll exit iters=%lu", (unsigned long)poll_iters);

    if (power_is_plugged_in() && sysevent_get(SYSEVENT_COPROC_BOOT)) {
      LOGI("UXC booted during USB power-off wait");
      // This isn't actually a shutdown — sysinfo bails so it can rekey UC.
      // Clear the shutdown latch so the device can resume normal sleep/timer
      // behavior; otherwise every future shutdown attempt would no-op via the
      // latch and the device would never auto-sleep again.
      sleep_cancel_shutdown();
      return;
    }
  }

  // Call power_down directly rather than re-enqueuing onto sysinfo_port.
  // Self-enqueueing would deadlock if the queue filled during the polling
  // loop above (the only consumer is this same thread), and bounding the
  // send only converts that into a deterministic reset — not a recovery —
  // turning a routine USB-plugged shutdown into a reboot whenever NFC or
  // coproc traffic accumulated while we were polling.
  LOGI("Powering off");
  sysinfo_task_port_power_down();
}

void sysinfo_task_port_power_down(void) {
  if (power_is_plugged_in()) {
    MFLOGW("power_down: USB plugged, resetting");
    // If USB is plugged in, we cannot power down, so instead we just reset.
    const mcu_reset_reason_t reason = sysevent_get(SYSEVENT_FORCE_POWER_OFF_RESET)
                                        ? MCU_RESET_DISPLAY_WEDGE
                                        : MCU_RESET_POWER_DOWN_USB_PLUGGED;
    coproc_power_assert_reset();
    mcu_reset_with_reason(reason);
  } else {
    if (sysinfo_task_in_ship_state()) {
      MFLOGI("power_down: ship state, disable LDO");
      // Disable LDO completely for ship state (packout mode).
      power_disable_ldo();
    } else {
      MFLOGI("power_down: LDO low-power mode");
      // Reduce LDO quiescent current before sleep.
      power_set_ldo_low_power_mode();
    }
    power_set_retain(false);

    // If control reaches here, the power-hold GPIO has been dropped but the
    // MCU is still alive — some external source is keeping the rail up.
    // Prefer a reset (with a recorded reason) over a zombie device with a
    // dead UI.
    rtos_thread_sleep(SYSINFO_POWER_OFF_GRACE_MS);
    BITLOG_EVENT(power_off_failed, 0);
    mcu_reset_with_reason(MCU_RESET_POWER_OFF_FAILED);
  }
}

void sysinfo_task_port_populate_mcu_info(fwpb_device_info_rsp* rsp) {
  ASSERT(rsp != NULL);

  uint8_t index = 0;
  metadata_t metadata = {0};
  fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
  if (metadata_get_active_slot(&metadata, &active_slot) != METADATA_VALID) {
    rsp->device_info_mcus_count = 0;
    return;
  }

  rsp->device_info_mcus_count = index + 1;
  rsp->device_info_mcus[index].mcu_role = fwpb_mcu_role_MCU_ROLE_CORE;
  rsp->device_info_mcus[index].mcu_name = fwpb_mcu_name_MCU_NAME_EFR32;
  rsp->device_info_mcus[index].has_version = true;
  rsp->device_info_mcus[index].version.major = metadata.version.major;
  rsp->device_info_mcus[index].version.minor = metadata.version.minor;
  rsp->device_info_mcus[index].version.patch = metadata.version.patch;
  rsp->device_info_mcus[index].active_slot = active_slot;
  {
    uint8_t chip_id[CHIPID_LENGTH] = {0};
    uint32_t chip_id_length = 0;
    sysinfo_chip_id_read(chip_id, &chip_id_length);
    PROTO_FILL_BYTES(&rsp->device_info_mcus[index], chip_id, chip_id, chip_id_length);
  }
  index++;

  if (uxc_mcu_info.has_version) {
    rsp->device_info_mcus_count = index + 1;
    rsp->device_info_mcus[index].mcu_role = uxc_mcu_info.mcu_role;
    rsp->device_info_mcus[index].mcu_name = uxc_mcu_info.mcu_name;
    rsp->device_info_mcus[index].has_version = true;
    rsp->device_info_mcus[index].version.major = uxc_mcu_info.version.major;
    rsp->device_info_mcus[index].version.minor = uxc_mcu_info.version.minor;
    rsp->device_info_mcus[index].version.patch = uxc_mcu_info.version.patch;
    rsp->device_info_mcus[index].active_slot = uxc_mcu_info.active_slot;
    if (uxc_mcu_info.chip_id.size > 0) {
      PROTO_FILL_BYTES(&rsp->device_info_mcus[index], chip_id, uxc_mcu_info.chip_id.bytes,
                       uxc_mcu_info.chip_id.size);
    }
    index++;
  }
}

void sysinfo_task_port_set_uxc_pending_version(const fwpb_semver* version) {
  // Update the cached version so that getDeviceInfo() reports the target
  // version to the app.  UXC has not committed or reset yet — this is the
  // version it will run once the atomic commit completes.  On any reset,
  // UXC sends a fresh uxc_boot_status_msg that overwrites this value with
  // its actual running version.
  uxc_mcu_info.version = *version;
}

static void _sysinfo_task_handle_coproc_coredump(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_COPROC_COREDUMP);
}

static void _sysinfo_task_handle_coproc_events(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_COPROC_EVENTS);
}

bool sysinfo_task_port_dispatch_confirmation_result(ipc_ref_t* message) {
  return confirmation_manager_dispatch_result(message);
}

/**
 * @brief Confirmation result handler for wipe state.
 *
 * Called on the second NFC tap after user confirms the wipe on the device screen.
 * Validates the confirmation handles, performs the actual wipe, and returns the
 * wipe_state_result in the get_confirmation_result_rsp.
 */
static NO_OPTIMIZE bool wipe_state_confirmation_result_handler(ipc_ref_t* message) {
  bool ok = false;
  bool wipe_complete = false;
  confirmation_result_t result = CONFIRMATION_RESULT_ERROR;
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  // Validate handles
  result =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (result != CONFIRMATION_RESULT_SUCCESS) {
    if (result == CONFIRMATION_RESULT_NOT_APPROVED && confirmation_manager_is_pending()) {
      // User hasn't approved/denied yet on the device screen.
      rsp->status = fwpb_status_CONFIRMATION_PENDING;
      ok = true;
      goto out;
    }

    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    goto out;
  }

  // Confirmation validated and approved — perform the actual wipe synchronously
  // before replying so the app cannot move forward while the device is still
  // clearing flash-backed state.
  onboarding_wipe_state();
  SECURE_DO({ deauthenticate(); });
  UI_SET_IDLE_STATE(UI_EVENT_IDLE);
  confirmation_manager_clear();

  rsp->status = fwpb_status_SUCCESS;
  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_wipe_state_result_tag;
  rsp->msg.get_confirmation_result_rsp.result.wipe_state_result.rsp_status =
    fwpb_wipe_state_rsp_wipe_state_rsp_status_SUCCESS;
  wipe_complete = true;
  ok = true;

out:
  if (!ok) {
    LOGE("Wipe confirm result fail: %d", result);
  }
  proto_send_rsp(cmd, rsp);
  if (wipe_complete) {
    rtos_thread_sleep(WIPE_RESET_DELAY_MS);
    mcu_reset_with_reason(MCU_RESET_WIPE);
  }
  return ok;
}

NO_OPTIMIZE bool sysinfo_task_port_handle_wipe_state(ipc_ref_t* message) {
  // Only require on-device confirmation if the device is onboarded.
  if (onboarding_complete() != SECURE_TRUE) {
    return false;
  }

  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t wipe_confirmation_context = 0;

  confirmation_result_t result = confirmation_manager_create(
    CONFIRMATION_TYPE_WIPE_STATE, &wipe_confirmation_context, sizeof(wipe_confirmation_context),
    response_handle, sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

  if (result != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("Wipe confirm create fail: %d", result);
    rsp->status = fwpb_status_ERROR;
    rsp->which_msg = fwpb_wallet_rsp_wipe_state_rsp_tag;
    rsp->msg.wipe_state_rsp.rsp_status = fwpb_wipe_state_rsp_wipe_state_rsp_status_ERROR;
    goto out;
  }

  // Show the privileged action confirmation screen.
  {
    fwpb_display_params_privileged_action action_params = {0};
    strncpy(action_params.title, "WIPE DEVICE", sizeof(action_params.title) - 1);
    action_params.which_action = fwpb_display_params_privileged_action_confirm_action_tag;
    action_params.action.confirm_action.action_type =
      fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_WIPE_DEVICE;

    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_PRIVILEGED_ACTION, &action_params,
                            sizeof(action_params));
  }

  // Return CONFIRMATION_PENDING with handles for the two-tap flow.
  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
  rsp->response_handle.size = sizeof(response_handle);
  memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
  rsp->confirmation_handle.size = sizeof(confirmation_handle);

out:
  proto_send_rsp(cmd, rsp);
  return true;
}
