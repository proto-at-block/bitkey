#include "fwup.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "fwup_delta_impl.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "fwup_utils.h"
#include "fwup_verify_impl.h"
#include "log.h"
#include "perf.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

fwup_priv_t fwup_priv FWUP_TASK_DATA = {0};

static NO_OPTIMIZE bool confirmation_check_required(void) {
  if (fwup_priv.has_confirmation_version) {
    return true;
  }
  volatile secure_bool_t require = fwup_get_require_confirmation();
  bool result = false;
  SECURE_IF_FAILOUT(require == SECURE_TRUE) { result = true; }
  return result;
}

static bool fwup_pending FWUP_TASK_DATA = false;
static bool fwup_coproc_pending FWUP_TASK_DATA = false;
static bool fwup_reset_pending FWUP_TASK_DATA = false;

enum {
  FWUP_WRITE_ERR_STATUS_ADDRESS_OVERFLOW = 1,
  FWUP_WRITE_ERR_STATUS_END_TOO_BIG = 2,
  FWUP_WRITE_ERR_STATUS_FLASH_WRITE_FAIL = 3,
};

bool fwup_in_progress(void) {
  return fwup_pending || fwup_coproc_pending;
}

void fwup_cleanup_stale_patch(void) {
  fwup_delta_cleanup_stale_patch();
}

void fwup_mark_pending(bool pending) {
  fwup_pending = pending;
}

void fwup_mark_coproc_pending(bool pending) {
  fwup_coproc_pending = pending;
}

bool fwup_is_coproc_pending(void) {
  return fwup_coproc_pending;
}

void fwup_mark_reset_pending(void) {
  fwup_reset_pending = true;
}

void fwup_clear_reset_pending(void) {
  fwup_reset_pending = false;
}

bool fwup_should_reject_cmd(void) {
  return fwup_reset_pending;
}

NO_OPTIMIZE secure_bool_t fwup_get_require_confirmation(void) {
  return fwup_priv.require_confirmation;
}

static bool is_delta_update(fwpb_fwup_mode mode) {
  return (mode == fwpb_fwup_mode_FWUP_MODE_DELTA_INLINE ||
          mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT);
}

void fwup_init(void* _target_slot_addr, void* _current_slot_addr, void* _target_slot_signature,
               uint32_t target_app_slot_size, bool _support_bl_upgrade,
               secure_bool_t _require_confirmation) {
  fwup_priv.perf.erase = perf_create(PERF_ELAPSED, fwup_erase);
  fwup_priv.perf.write = perf_create(PERF_ELAPSED, fwup_write);
  fwup_priv.perf.transfer = perf_create(PERF_INTERVAL, fwup_transfer);
  fwup_priv.perf.transfer_cmd = perf_create(PERF_ELAPSED, fwup_transfer_cmd);

  fwup_priv.target_slot_addr = _target_slot_addr;
  fwup_priv.current_slot_addr = _current_slot_addr;
  fwup_priv.app_slot_size = target_app_slot_size;
  fwup_priv.target_slot_signature = _target_slot_signature;
  fwup_priv.support_bl_upgrade = _support_bl_upgrade;
  fwup_priv.require_confirmation = _require_confirmation;

  fwup_pending = false;
  fwup_coproc_pending = false;
  fwup_reset_pending = false;
}

bool fwup_start(fwpb_fwup_start_cmd* cmd, fwpb_fwup_start_rsp* rsp_out) {
  // Record the version the user confirmed so fwup_finish() can validate it
  // against the version actually present in the flashed binary.
  fwup_priv.has_confirmation_version = false;
  if (cmd->has_version) {
    fwup_priv.confirmation_version = cmd->version;
    fwup_priv.has_confirmation_version = true;
  }

  // Clear any signature from a previous update attempt.
  memset(fwup_priv.pending_signature, 0, sizeof(fwup_priv.pending_signature));
  fwup_priv.has_pending_signature = false;

  if (is_delta_update(cmd->mode)) {
    if (!fwup_delta_init(
          (fwup_delta_cfg_t){
            .mode = cmd->mode,
            .patch_size = cmd->patch_size,
            .active_slot_base_addr = (uintptr_t)fwup_priv.current_slot_addr,
            .slot_size = fwup_priv.app_slot_size,
            .target_slot_base_addr = (uintptr_t)fwup_priv.target_slot_addr,
          },
          fwup_priv.perf.write, fwup_priv.perf.erase)) {
      goto error;
    }
    // Delta updates defer erasing flash until patch application.  The signature
    // is held in RAM (pending_signature) during transfer, so there is no
    // signature page to erase here.
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
  ASSERT(max_chunk_size != 0u);
  if (cmd->sequence_id > ((UINT32_MAX - cmd->offset) / max_chunk_size)) {
    LOGE("Offset wrapped");
    BITLOG_EVENT(fwup_write_err, FWUP_WRITE_ERR_STATUS_ADDRESS_OVERFLOW);
    goto error;
  }
  const uint32_t write_address_offset = (cmd->sequence_id * max_chunk_size) + cmd->offset;

  const uint32_t end = write_address_offset + data.size;
  if (end < write_address_offset) {
    LOGE("Wrapped");
    BITLOG_EVENT(fwup_write_err, FWUP_WRITE_ERR_STATUS_ADDRESS_OVERFLOW);
    goto error;  // Wrapped
  }

  if (end > fwup_priv.app_slot_size) {
    LOGE("End too big");
    BITLOG_EVENT(fwup_write_err, FWUP_WRITE_ERR_STATUS_END_TOO_BIG);
    goto error;
  }

  // The signature occupies the last FWUP_SIGNATURE_SIZE bytes of the slot.
  // Redirect any bytes that fall in that region to the RAM buffer so the
  // image cannot appear valid to the bootloader before verification.
  if (fwup_priv.app_slot_size < FWUP_SIGNATURE_SIZE) {
    LOGE("Slot small");
    goto error;
  }
  const uint32_t sig_boundary = fwup_priv.app_slot_size - FWUP_SIGNATURE_SIZE;

  // Determine how much of this chunk falls before the signature region.
  const uint32_t flash_end = BLK_MIN(end, sig_boundary);
  uint32_t flash_len = (write_address_offset < flash_end) ? (flash_end - write_address_offset) : 0;

  // Write the firmware portion (before signature region) to flash.
  if (flash_len > 0) {
    void* write_addr = (uint8_t*)fwup_priv.target_slot_addr + write_address_offset;
    if (!fwup_flash_write(fwup_priv.perf.write, write_addr, data.bytes, flash_len)) {
      LOGE("Wr fail %p", write_addr);
      BITLOG_EVENT(fwup_write_err, FWUP_WRITE_ERR_STATUS_FLASH_WRITE_FAIL);
      goto error;
    }
  }

  // Copy any bytes that fall in the signature region to the RAM buffer.
  if (end > sig_boundary && write_address_offset < fwup_priv.app_slot_size) {
    const uint32_t sig_start = BLK_MAX(write_address_offset, sig_boundary);
    const uint32_t sig_data_offset = sig_start - write_address_offset;  // offset into data.bytes
    const uint32_t buf_offset = sig_start - sig_boundary;  // offset into pending_signature
    const uint32_t sig_len = end - sig_start;

    if (buf_offset + sig_len > FWUP_SIGNATURE_SIZE) {
      LOGE("Sig overflow");
      BITLOG_EVENT(fwup_write_err, FWUP_WRITE_ERR_STATUS_END_TOO_BIG);
      goto error;
    }

    memcpy(fwup_priv.pending_signature + buf_offset, data.bytes + sig_data_offset, sig_len);
    fwup_priv.has_pending_signature = true;
  }

  rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS;
  perf_end(fwup_priv.perf.transfer_cmd);
  return true;

error:
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

bool fwup_pre_apply_check(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out) {
  if (cmd->mode != fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    return true;
  }

  bool require = confirmation_check_required();

  // If confirmation is required but the app didn't send a version, fail early
  // rather than applying the patch and then failing in fwup_finish().
  if (require && !fwup_priv.has_confirmation_version) {
    LOGE("No conf ver");
    rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_CONFIRMATION_MISMATCH;
    return false;
  }

  fwpb_semver* expected =
    fwup_priv.has_confirmation_version ? &fwup_priv.confirmation_version : NULL;

  fwup_verify_status_t status = fwup_delta_check_header(expected, require);
  if (status == FWUP_VERIFY_SUCCESS) {
    return true;
  }

  BITLOG_EVENT(fwup_verify_err, status);
  switch (status) {
    case FWUP_VERIFY_CONFIRMATION_MISMATCH:
    case FWUP_VERIFY_MISSING_HEADER:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_CONFIRMATION_MISMATCH;
      break;
    default:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
      break;
  }
  return false;
}

bool fwup_finish(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out) {
  bool result = false;

  if (cmd->bl_upgrade && !fwup_priv.support_bl_upgrade) {
    LOGE("BL upgrade not supported");
    rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
    return false;
  }

  if (is_delta_update(cmd->mode)) {
    if (!fwup_delta_finish(cmd)) {
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
      return false;
    }
  }

  fwup_verify_status_t status = FWUP_VERIFY_ERROR;
  if (cmd->bl_upgrade) {
    // BL images are smaller than the app slot, so their data (including the
    // embedded BL signature) falls below the signature intercept boundary.
    // fwup_verify_new_bootloader() validates this before proceeding.
    status = fwup_verify_new_bootloader();
  } else {
    // Validate that the caller's signature_offset matches where we expect the
    // signature to live, as a sanity check.
    if (fwup_priv.app_slot_size < FWUP_SIGNATURE_SIZE) {
      LOGE("Slot small");
      status = FWUP_VERIFY_ERROR;
    } else if (cmd->signature_offset != (fwup_priv.app_slot_size - FWUP_SIGNATURE_SIZE)) {
      LOGE("Sig off %zu!=%zu", (size_t)cmd->signature_offset,
           (size_t)(fwup_priv.app_slot_size - FWUP_SIGNATURE_SIZE));
      status = FWUP_VERIFY_BAD_OFFSET;
    } else if (!fwup_priv.has_pending_signature) {
      LOGE("No signature received");
      status = FWUP_VERIFY_SIGNATURE_INVALID;
    } else {
      // Verify using the signature held in RAM — it has not been written to flash.
      status = fwup_verify_new_app(cmd->app_properties_offset, fwup_priv.pending_signature);
    }

    if (status == FWUP_VERIFY_SUCCESS) {
      bool needs_check = confirmation_check_required();
      if (needs_check) {
        fwpb_semver target_version = {0};
        if (!fwup_priv.has_confirmation_version || !fwup_get_target_version(&target_version) ||
            !fwup_semver_equals(&fwup_priv.confirmation_version, &target_version)) {
          LOGE("Confirmation mismatch");
          status = FWUP_VERIFY_CONFIRMATION_MISMATCH;
        }
      }
    }

    // On failure the signature is not committed to flash, so the bootloader
    // will not consider this slot valid.  The firmware content remains in the
    // target slot but is inert and will be erased before the next update
    // (by fwup_start() for normal mode, or apply_patch_from_file() for delta).
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
    case FWUP_VERIFY_CONFIRMATION_MISMATCH:
      rsp_out->rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_CONFIRMATION_MISMATCH;
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

const uint8_t* fwup_get_pending_signature(void) {
  return fwup_priv.has_pending_signature ? fwup_priv.pending_signature : NULL;
}

void* fwup_get_target_slot_signature_addr(void) {
  return fwup_priv.target_slot_signature;
}

bool fwup_commit_signature_to(void* target_addr, const uint8_t* signature) {
  if (!fwup_flash_write(fwup_priv.perf.write, target_addr, signature, FWUP_SIGNATURE_SIZE)) {
    LOGE("Sig write fail");
    fwup_flash_erase_app_signature(fwup_priv.perf.erase, target_addr);
    return false;
  }
  return true;
}

bool fwup_commit_signature(void) {
  if (!fwup_priv.has_pending_signature) {
    LOGE("No pending sig");
    return false;
  }
  return fwup_commit_signature_to(fwup_priv.target_slot_signature, fwup_priv.pending_signature);
}
