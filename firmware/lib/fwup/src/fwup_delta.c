#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "detools.h"
#include "ecc.h"
#include "filesystem.h"
#include "fwup_delta_impl.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "hash.h"
#include "hex.h"
#include "log.h"
#include "mcu.h"
#include "perf.h"
#include "rtos_mpu.h"
#include "security_config.h"
#include "secutils.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <stdalign.h>
#include <stdlib.h>

// Inline mode isn't supported for now, since we don't have a good way to
// deal with verifying the patch.
#define DELTA_INLINE_SUPPORTED          0
#define FWUP_DELTA_PATCH_SIGNATURE_SIZE 64
#define PATCH_PATH                      "patch.bin"
// Patch must include at least one byte of data plus the signature.
#define FWUP_DELTA_MIN_PATCH_SIZE (FWUP_DELTA_PATCH_SIGNATURE_SIZE + 1)
// Reject invalid patch sizes. 128K is a reasonable upper bound, but we can
// change it later.
#define FWUP_DELTA_MAX_PATCH_SIZE (128 * 1024)

#ifndef EMBEDDED_BUILD
#define portRAISE_PRIVILEGE()
#define portRESET_PRIVILEGE()
#define portIS_PRIVILEGED() (false)
#endif

static struct detools_apply_patch_t apply_patch;
static fs_file_t fwup_file_handle = {0};
extern security_config_t security_config;

static struct {
  size_t remaining_patch_size;  // Only used for inline mode.
  uintptr_t active_slot_base_addr;
  uintptr_t active_slot_offset_pointer;
  uintptr_t target_slot_base_addr;
  uintptr_t target_slot_offset_pointer;
  bool file_opened;
  alignas(MCU_FLASH_WRITE_ALIGNMENT) uint8_t write_buffer[MCU_FLASH_WRITE_ALIGNMENT];
  size_t write_buffer_fill;
} delta_state = {0};

_Static_assert(sizeof(delta_state.write_buffer) == MCU_FLASH_WRITE_ALIGNMENT,
               "write_buffer must match flash write alignment");

static struct {
  perf_counter_t* process;
  perf_counter_t* flash_write;
  perf_counter_t* erase;
} perf;

static bool close(void) {
  if (fs_close(&fwup_file_handle) != 0) {
    BITLOG_EVENT(fwup_delta_init_err, 1);
    LOGE("Failed to close file handle");
    return false;
  }
  delta_state.file_opened = false;
  return true;
}

SYSCALL static int from_read(void* UNUSED(arg_p), uint8_t* buf_p, size_t size) {
  int was_priv = rtos_thread_is_privileged();
  if (!was_priv) {
    rtos_thread_raise_privilege();
  }

  memcpy(buf_p, (void*)(delta_state.active_slot_base_addr + delta_state.active_slot_offset_pointer),
         size);
  delta_state.active_slot_offset_pointer += size;

  if (!was_priv) {
    rtos_thread_reset_privilege();
  }

  return 0;
}

static int from_seek(void* UNUSED(arg_p), int offset) {
  delta_state.active_slot_offset_pointer += offset;
  return 0;
}

static int to_write(void* UNUSED(arg_p), const uint8_t* buf_p, size_t size) {
  const uint8_t* src = buf_p;
  size_t remaining = size;

  // detools may emit sub-alignment writes. On some MCUs (e.g., STM32U5), each
  // aligned flash block can only be programmed once after erase.
  // We therefore buffer tails and only program full MCU_FLASH_WRITE_ALIGNMENT
  // blocks, padding the final block on flush. Note: MCU_FLASH_WRITE_ALIGNMENT
  // is target-specific (e.g., 4 bytes on EFR32, 16 bytes on STM32U5).

  // If we have pending data, fill the block and write it once.
  if (delta_state.write_buffer_fill > 0) {
    const size_t space_in_buffer = MCU_FLASH_WRITE_ALIGNMENT - delta_state.write_buffer_fill;
    const size_t to_copy = (remaining < space_in_buffer) ? remaining : space_in_buffer;

    memcpy(delta_state.write_buffer + delta_state.write_buffer_fill, src, to_copy);
    delta_state.write_buffer_fill += to_copy;
    src += to_copy;
    remaining -= to_copy;

    if (delta_state.write_buffer_fill == MCU_FLASH_WRITE_ALIGNMENT) {
      const uintptr_t flash_addr =
        delta_state.target_slot_base_addr + delta_state.target_slot_offset_pointer;

      if (!fwup_flash_write(perf.flash_write, (void*)flash_addr, delta_state.write_buffer,
                            MCU_FLASH_WRITE_ALIGNMENT)) {
        LOGI("to_write failed: dst=%08X, src=%p, size=%d", (unsigned int)flash_addr,
             delta_state.write_buffer, MCU_FLASH_WRITE_ALIGNMENT);
        return -1;
      }

      delta_state.target_slot_offset_pointer += MCU_FLASH_WRITE_ALIGNMENT;
      delta_state.write_buffer_fill = 0;
    } else {
      return 0;
    }
  }

  // Fast path: write full aligned blocks directly from the input buffer.
  if (remaining >= MCU_FLASH_WRITE_ALIGNMENT) {
    const size_t aligned_len = remaining - (remaining % MCU_FLASH_WRITE_ALIGNMENT);
    const uintptr_t flash_addr =
      delta_state.target_slot_base_addr + delta_state.target_slot_offset_pointer;

    if (!fwup_flash_write(perf.flash_write, (void*)flash_addr, src, aligned_len)) {
      LOGI("to_write failed: dst=%08X, src=%p, size=%d", (unsigned int)flash_addr, src,
           aligned_len);
      return -1;
    }

    delta_state.target_slot_offset_pointer += aligned_len;
    src += aligned_len;
    remaining -= aligned_len;
  }

  // Buffer any trailing bytes (< alignment) to avoid reprogramming a quad-word.
  if (remaining > 0) {
    memcpy(delta_state.write_buffer, src, remaining);
    delta_state.write_buffer_fill = remaining;
  }

  return 0;
}

static bool flush_write_buffer(void) {
  if (delta_state.write_buffer_fill == 0) {
    return true;
  }

  // Pad the remaining buffer with 0xFF (erased flash value)
  memset(delta_state.write_buffer + delta_state.write_buffer_fill, 0xFF,
         MCU_FLASH_WRITE_ALIGNMENT - delta_state.write_buffer_fill);

  const uintptr_t flash_addr =
    delta_state.target_slot_base_addr + delta_state.target_slot_offset_pointer;

  if (!fwup_flash_write(perf.flash_write, (void*)flash_addr, delta_state.write_buffer,
                        MCU_FLASH_WRITE_ALIGNMENT)) {
    LOGE("flush_write_buffer failed: dst=%08X", (unsigned int)flash_addr);
    return false;
  }

  delta_state.target_slot_offset_pointer += MCU_FLASH_WRITE_ALIGNMENT;
  delta_state.write_buffer_fill = 0;

  return true;
}

static bool fwup_delta_write_patch(fwpb_fwup_transfer_cmd* cmd) {
  ASSERT(cmd);

  fwpb_fwup_transfer_cmd_fwup_data_t data = cmd->fwup_data;

  const uint32_t max_chunk_size = fwup_flash_get_max_chunk_size();
  const uint32_t write_address_offset = (cmd->sequence_id * max_chunk_size) + cmd->offset;

  if (data.size > max_chunk_size) {
    LOGE("Invalid data size");
    close();
    return false;
  }

  // Seek to the write address to ensure that writing a patch is idempotent.
  // In the happy path, this seek does nothing. But if the host retries a chunk we've
  // already written (i.e. the same sequence_id), then we'll seek to the same spot that
  // chunk was written last time. Without this seek, we would continue growing the end
  // of the file, resulting in an invalid patch.
  //
  // NOTE: littlefs bounds checks the address, so we don't need to validate `write_address_offset`.
  if (fs_file_seek(&fwup_file_handle, write_address_offset, FS_SEEK_SET) < 0) {
    LOGE("Failed to seek");
    close();
    return false;
  }

  if (fs_file_write(&fwup_file_handle, data.bytes, data.size) < 0) {
    BITLOG_EVENT(fwup_delta_write_patch_err, 0);
    LOGE("Failed to write patch piece");
    close();
    return false;
  }

  return true;
}

bool fwup_delta_init(fwup_delta_cfg_t cfg, perf_counter_t* perf_flash_write,
                     perf_counter_t* perf_erase) {
  perf.process = perf_create(PERF_ELAPSED, fwup_delta_process_inline);
  perf.flash_write = perf_flash_write;
  perf.erase = perf_erase;

  delta_state.remaining_patch_size = cfg.patch_size;
  delta_state.active_slot_base_addr = cfg.active_slot_base_addr;
  delta_state.active_slot_offset_pointer = 0;
  delta_state.target_slot_base_addr = cfg.target_slot_base_addr;
  delta_state.target_slot_offset_pointer = 0;
  delta_state.write_buffer_fill = 0;

  if ((cfg.target_slot_base_addr % MCU_FLASH_WRITE_ALIGNMENT) != 0) {
    LOGE("Target slot base addr not aligned: %p", (void*)cfg.target_slot_base_addr);
    return false;
  }

  memset(&apply_patch, 0, sizeof(apply_patch));

  if (cfg.patch_size < FWUP_DELTA_MIN_PATCH_SIZE || cfg.patch_size > FWUP_DELTA_MAX_PATCH_SIZE) {
    LOGE("Got an invalid patch size");
    return false;
  }

  if (cfg.mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    if (delta_state.file_opened) {
      // Close any previously opened files. This is an issue if this function
      // is called twice.
      close();
    }

    if (fs_file_exists(PATCH_PATH) && fs_remove(PATCH_PATH) != 0) {
      BITLOG_EVENT(fwup_delta_init_err, 2);
      LOGE("Failed to delete existing patch.bin");
      return false;
    }

    if (fs_open(&fwup_file_handle, PATCH_PATH, FS_O_RDWR | FS_O_CREAT) < 0) {
      BITLOG_EVENT(fwup_delta_init_err, 2);
      LOGE("Failed to open patch.bin");
      return false;
    }

    delta_state.file_opened = true;  // Don't try to open the file twice.
  }

  LOGI("Preparing for delta update, patch size: %d", cfg.patch_size);

  return (detools_apply_patch_init(&apply_patch, from_read, from_seek, cfg.patch_size, to_write,
                                   NULL) == 0);
}

#if DELTA_INLINE_SUPPORTED
static bool fwup_delta_process_inline(uint8_t* piece, size_t size) {
  perf_begin(perf.process);
  int res = detools_apply_patch_process(&apply_patch, piece, size);
  perf_end(perf.process);
  if (res != 0) {
    goto error;
  }

  delta_state.remaining_patch_size -= size;
  return true;

error:
  LOGE("Failed to process patch piece: %s", detools_error_as_string(res));
  return false;
}
#endif

bool fwup_delta_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out) {
  bool result = false;

  switch (cmd->mode) {
    case fwpb_fwup_mode_FWUP_MODE_DELTA_INLINE:
#if DELTA_INLINE_SUPPORTED
      // Process the patch piece-by-piece during the NFC transfer.
      result = fwup_delta_process_inline(data.bytes, data.size);
      break;
#else
      LOGE("Delta inline not supported");
      break;
#endif
      break;
    case fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT:
      // Write the patch to the filesystem and process it in one shot
      // after the NFC transfer is complete.
      result = fwup_delta_write_patch(cmd);
      break;
    default:
      LOGE("Invalid delta mode");
      break;
  }

  if (!result) {
    LOGE("Delta update transfer failed");
    rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
  } else {
    rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS;
  }

  return result;
}

static bool verify_patch(uint8_t* buf, uint32_t buf_size, uint32_t patch_size) {
  ASSERT(buf && buf_size >= SHA256_DIGEST_SIZE);

  if (patch_size < FWUP_DELTA_MIN_PATCH_SIZE) {
    LOGE("Patch too small");
    return false;
  }

  hash_stream_ctx_t sha256 = {0};
  if (!crypto_sha256_stream_init(&sha256)) {
    LOGE("Failed to initialize SHA256");
    return false;
  }

  // The patch file includes a 64 byte signature at the end.
  // Read the patch file up to, but excluding, the signature and compute the SHA256.
  uint32_t bytes_read = 0;
  uint32_t total_bytes = patch_size - FWUP_DELTA_PATCH_SIGNATURE_SIZE;
  while (bytes_read < total_bytes) {
    size_t to_read = BLK_MIN(buf_size, total_bytes - bytes_read);
    size_t read_size = fs_file_read(&fwup_file_handle, buf, to_read);
    if (read_size != to_read) {
      BITLOG_EVENT(fwup_delta_verify_patch_err, 1);
      LOGE("Failed to read patch");
      return false;
    }
    if (!crypto_sha256_stream_update(&sha256, buf, read_size)) {
      BITLOG_EVENT(fwup_delta_verify_patch_err, 2);
      LOGE("Failed to update SHA256");
      return false;
    }
    bytes_read += read_size;
  }

  memzero(buf, buf_size);
  if (!crypto_sha256_stream_final(&sha256, buf)) {
    BITLOG_EVENT(fwup_delta_verify_patch_err, 3);
    LOGE("Failed to finalize SHA256");
    return false;
  }

  key_handle_t key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = security_config.fwup_delta_patch_pubkey,
    .key.size = FWUP_DELTA_PATCH_PUBKEY_SIZE,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  uint8_t sig[FWUP_DELTA_PATCH_SIGNATURE_SIZE] = {0};
  if (fs_file_read(&fwup_file_handle, sig, sizeof(sig)) != sizeof(sig)) {
    BITLOG_EVENT(fwup_delta_verify_patch_err, 4);
    LOGE("Failed to read signature");
    memzero(sig, sizeof(sig));
    memzero(buf, buf_size);
    return false;
  }

  volatile secure_bool_t verified = crypto_ecc_verify_hash(&key, buf, SHA256_DIGEST_SIZE, sig);
  if (verified != SECURE_TRUE) {
    BITLOG_EVENT(fwup_delta_verify_patch_err, 5);
    LOGE("Failed to verify patch");
    memzero(sig, sizeof(sig));
    memzero(buf, buf_size);
    return false;
  }

  return true;
}

static bool apply_patch_from_file(void) {
  bool result = false;

  if (!fwup_flash_erase_app_excluding_signature(perf.erase,
                                                (void*)delta_state.target_slot_base_addr)) {
    goto out;
  }

  if (fs_file_seek(&fwup_file_handle, 0, FS_SEEK_SET) < 0) {
    LOGE("Failed to seek to beginning of patch file");
    goto out;
  }

  uint8_t buf[512] = {0};
  int size_or_err = fs_file_size(&fwup_file_handle);
  if (size_or_err < 0) {
    LOGE("Failed to get patch file size");
    goto out;
  }
  uint32_t patch_size = size_or_err;
  int finalize_status;

  // First pass: verify the patch
  if (!verify_patch(buf, sizeof(buf), patch_size)) {
    LOGE("Failed to verify patch");
    goto out;
  }

  if (fs_file_seek(&fwup_file_handle, 0, FS_SEEK_SET) < 0) {
    LOGE("Failed to seek to beginning of patch file");
    goto out;
  }

  // Second pass: apply the patch

  uint32_t bytes_read = 0;
  uint32_t total_bytes = patch_size - FWUP_DELTA_PATCH_SIGNATURE_SIZE;
  while (bytes_read < total_bytes) {
    size_t to_read = BLK_MIN(sizeof(buf), total_bytes - bytes_read);
    size_t read_size = fs_file_read(&fwup_file_handle, buf, to_read);
    if (read_size != to_read) {
      LOGE("Failed to read patch");
      goto out;
    }
    if (detools_apply_patch_process(&apply_patch, buf, read_size) != 0) {
      LOGE("Failed to process patch piece");
      goto finalize;
    }
    bytes_read += read_size;
  }

finalize:
  finalize_status = detools_apply_patch_finalize(&apply_patch);
  if (finalize_status < 0) {
    result = false;
    BITLOG_EVENT(fwup_delta_apply_patch_err, finalize_status);
  } else {
    // Flush any remaining buffered data with 0xFF padding
    result = flush_write_buffer();
    if (!result) {
      LOGE("Failed to flush write buffer");
    }
  }
  LOGD("Patch %s", result ? "applied" : "failed");
out:
  close();
  return result;
}

bool fwup_delta_finish(fwpb_fwup_finish_cmd* cmd) {
  // Note: The caller will handle verification.

  switch (cmd->mode) {
    case fwpb_fwup_mode_FWUP_MODE_DELTA_INLINE:
#if DELTA_INLINE_SUPPORTED
      if (detools_apply_patch_finalize(&apply_patch) < 0) {
        return false;
      }
      return flush_write_buffer();
#else
      LOGE("Delta inline not supported");
      return false;
#endif
    case fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT:
      LOGI("Applying patch from file...");
      return apply_patch_from_file();
    default:
      LOGE("Invalid delta mode");
      return false;
  }
}
