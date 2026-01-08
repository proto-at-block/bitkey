#include "fwup.h"

#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "fwup_delta_impl.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "fwup_verify_impl.h"
#include "log.h"
#include "perf.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

fwup_priv_t fwup_priv FWUP_TASK_DATA = {0};

static bool fwup_pending FWUP_TASK_DATA = false;
static bool fwup_coproc_pending FWUP_TASK_DATA = false;

bool fwup_in_progress(void) {
  return fwup_pending || fwup_coproc_pending;
}

void fwup_mark_pending(bool pending) {
  fwup_pending = pending;
}

void fwup_mark_coproc_pending(bool pending) {
  fwup_coproc_pending = pending;
}

static bool is_delta_update(fwpb_fwup_mode mode) {
  return (mode == fwpb_fwup_mode_FWUP_MODE_DELTA_INLINE ||
          mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT);
}

void fwup_init(void* _target_slot_addr, void* _current_slot_addr, void* _target_slot_signature,
               uint32_t target_app_slot_size, bool _support_bl_upgrade) {
  fwup_priv.perf.erase = perf_create(PERF_ELAPSED, fwup_erase);
  fwup_priv.perf.write = perf_create(PERF_ELAPSED, fwup_write);
  fwup_priv.perf.transfer = perf_create(PERF_INTERVAL, fwup_transfer);
  fwup_priv.perf.transfer_cmd = perf_create(PERF_ELAPSED, fwup_transfer_cmd);

  fwup_priv.target_slot_addr = _target_slot_addr;
  fwup_priv.current_slot_addr = _current_slot_addr;
  fwup_priv.app_slot_size = target_app_slot_size;
  fwup_priv.target_slot_signature = _target_slot_signature;
  fwup_priv.support_bl_upgrade = _support_bl_upgrade;
}

bool fwup_start(fwpb_fwup_start_cmd* cmd, fwpb_fwup_start_rsp* rsp_out) {
  if (is_delta_update(cmd->mode)) {
    if (!fwup_delta_init(
          (fwup_delta_cfg_t){
            .mode = cmd->mode,
            .patch_size = cmd->patch_size,
            .active_slot_base_addr = (uintptr_t)fwup_priv.current_slot_addr,
            .target_slot_base_addr = (uintptr_t)fwup_priv.target_slot_addr,
          },
          fwup_priv.perf.write, fwup_priv.perf.erase)) {
      LOGE("Delta update initialization failed");
      goto error;
    }
    // Delta updates defer erasing most of flash until patch application. However, we must erase
    // the signature's page now, because we transfer the signature using fwup_transfer() in normal
    // mode.
    if (!fwup_flash_erase_app_signature(fwup_priv.perf.erase, fwup_priv.target_slot_signature)) {
      goto error;
    }
  } else {
    /* Erase the correct flash slot */
    if (!fwup_flash_erase_app(fwup_priv.perf.erase, fwup_priv.target_slot_addr)) {
      goto error;
    }
  }

  /* Reset the transfer interval perf counter */
  perf_reset(fwup_priv.perf.transfer);

  rsp_out->rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_SUCCESS;
  rsp_out->max_chunk_size = fwup_flash_get_max_chunk_size();
  return true;

error:
  rsp_out->rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
  return false;
}

static bool fwup_handle_write(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out) {
  fwpb_fwup_transfer_cmd_fwup_data_t data = cmd->fwup_data;

  const uint32_t max_chunk_size = fwup_flash_get_max_chunk_size();
  const uint32_t write_address_offset = (cmd->sequence_id * max_chunk_size) + cmd->offset;

  const uint32_t end = write_address_offset + data.size;
  if (end < write_address_offset) {
    LOGE("Wrapped");
    BITLOG_EVENT(fwup_write_err, 1);
    goto error;  // Wrapped
  }

  if (end > fwup_priv.app_slot_size) {
    LOGE("End too big");
    BITLOG_EVENT(fwup_write_err, 2);
    goto error;
  }

  /* Write the data */
  void* write_addr = (uint8_t*)fwup_priv.target_slot_addr + write_address_offset;
  if (!fwup_flash_write(fwup_priv.perf.write, write_addr, data.bytes, data.size)) {
    LOGE("Failed to write flash: %p, %d", write_addr, data.size);
    BITLOG_EVENT(fwup_write_err, 3);
    goto error;
  }

  rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS;
  perf_end(fwup_priv.perf.transfer_cmd);
  return true;

error:
  LOGE("fwup_transfer failed");
  rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
  perf_end(fwup_priv.perf.transfer_cmd);
  return false;
}

bool fwup_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out) {
  perf_count(fwup_priv.perf.transfer);
  perf_begin(fwup_priv.perf.transfer_cmd);

  if (is_delta_update(cmd->mode)) {
    return fwup_delta_transfer(cmd, rsp_out);
  } else {
    // For non-delta updates, we write the data to the inactive slot.
    return fwup_handle_write(cmd, rsp_out);
  }
}

bool fwup_finish(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out) {
  bool result = false;

  if (cmd->bl_upgrade && !fwup_priv.support_bl_upgrade) {
    LOGE("BL upgrade not supported");
    rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
    return false;
  }

  if (is_delta_update(cmd->mode)) {
    LOGI("Finalizing delta update...");
    fwup_delta_finish(cmd);
  }

  fwup_verify_status_t status = FWUP_VERIFY_ERROR;
  if (cmd->bl_upgrade) {
    status = fwup_verify_new_bootloader();
  } else {
    status = fwup_verify_new_app(cmd->app_properties_offset, cmd->signature_offset);
    if (status != FWUP_VERIFY_SUCCESS) {
      BITLOG_EVENT(fwup_verify_err, status);
    }
  }

  BITLOG_EVENT(fwup_finish, status);
  switch (status) {
    case FWUP_VERIFY_VERSION_INVALID:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_VERSION_INVALID;
      break;
    case FWUP_VERIFY_SIGNATURE_INVALID:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SIGNATURE_INVALID;
      break;
    case FWUP_VERIFY_SUCCESS:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS;
      result = true;
      break;
    default:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
      break;
  }
  return result;
}
