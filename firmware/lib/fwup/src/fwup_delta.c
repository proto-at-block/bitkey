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
#include "platform.h"
#include "rtos_mpu.h"
#include "security_config.h"
#include "secutils.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <inttypes.h>
#include <stdalign.h>
#include <stdint.h>
#include <stdlib.h>

// Inline mode isn't supported for now, since we don't have a good way to
// deal with verifying the patch.
#define DELTA_INLINE_SUPPORTED          0
#define FWUP_DELTA_PATCH_SIGNATURE_SIZE 64
#define FWUP_DELTA_PATCH_IO_BUFFER_SIZE 512u
#define PATCH_PATH                      "patch.bin"
// Patch must include at least one byte of data plus the signature.
#define FWUP_DELTA_MIN_PATCH_SIZE (FWUP_DELTA_PATCH_SIGNATURE_SIZE + 1)
#ifndef PLATFORM_CFG_FWUP_DELTA_MAX_PATCH_SIZE
// Reject invalid patch sizes. Defaults to 128K unless overridden in platform config.
#define PLATFORM_CFG_FWUP_DELTA_MAX_PATCH_SIZE (128 * 1024)
#endif
// Per-platform max patch size.
#define FWUP_DELTA_MAX_PATCH_SIZE PLATFORM_CFG_FWUP_DELTA_MAX_PATCH_SIZE

enum {
  FWUP_DELTA_WRITE_PATCH_ERR_STATUS_PATCH_WRITE_FAIL = 0,
  FWUP_DELTA_WRITE_PATCH_ERR_STATUS_ADDRESS_OVERFLOW = 1,
};

#ifndef EMBEDDED_BUILD
#define portRAISE_PRIVILEGE()
#define portRESET_PRIVILEGE()
#define portIS_PRIVILEGED() (false)
#endif

static struct detools_apply_patch_t apply_patch;
static fs_file_t fwup_file_handle = {0};
extern security_config_t security_config;
static const uint8_t fwup_delta_header_magic[] = FWUP_DELTA_HEADER_MAGIC_BYTES;

static struct {
  size_t remaining_patch_size;  // Only used for inline mode.
  uintptr_t active_slot_base_addr;
  size_t slot_size;
  size_t active_slot_offset_pointer;
  uintptr_t target_slot_base_addr;
  size_t target_slot_offset_pointer;
  bool delta_initialized;
  bool file_opened;
  alignas(MCU_FLASH_WRITE_ALIGNMENT) uint8_t patch_io_buffer[FWUP_DELTA_PATCH_IO_BUFFER_SIZE];
  hash_stream_ctx_t sha256_ctx;
  alignas(MCU_FLASH_WRITE_ALIGNMENT) uint8_t write_buffer[MCU_FLASH_WRITE_ALIGNMENT];
  size_t write_buffer_fill;
  uint8_t header_size;  // Size of the version header at the start of the patch (0 = no header).
} delta_state = {0};

_Static_assert(sizeof(delta_state.write_buffer) == MCU_FLASH_WRITE_ALIGNMENT,
               "write_buffer must match flash write alignment");

static struct {
  perf_counter_t* process;
  perf_counter_t* flash_write;
  perf_counter_t* erase;
} perf;

bool fwup_delta_slot_range_valid(size_t slot_size, size_t offset, size_t size) {
  return (offset <= slot_size) && (size <= (slot_size - offset));
}

bool fwup_delta_slot_seek(size_t slot_size, size_t current_offset, int offset,
                          size_t* next_offset_out) {
  ASSERT(next_offset_out != NULL);

  if (current_offset > slot_size) {
    return false;
  }

  if (offset < 0) {
    size_t backward = (size_t)(-(int64_t)offset);
    if (backward > current_offset) {
      return false;
    }
    *next_offset_out = current_offset - backward;
    return true;
  }

  size_t forward = (size_t)offset;
  if (forward > (slot_size - current_offset)) {
    return false;
  }

  *next_offset_out = current_offset + forward;
  return true;
}

static bool target_slot_logical_offset(size_t* logical_offset_out) {
  ASSERT(logical_offset_out != NULL);

  if (!fwup_delta_slot_range_valid(delta_state.slot_size, delta_state.target_slot_offset_pointer,
                                   delta_state.write_buffer_fill)) {
    return false;
  }

  *logical_offset_out = delta_state.target_slot_offset_pointer + delta_state.write_buffer_fill;
  return true;
}

SYSCALL NO_OPTIMIZE static bool close(void) {
  bool success;
  RTOS_THREAD_WITH_PRIVILEGE({
    if (fs_close(&fwup_file_handle) != 0) {
      BITLOG_EVENT(fwup_delta_init_err, 1);
      success = false;
    } else {
      success = true;
    }
  });

  if (success) {
    delta_state.file_opened = false;
  }
  return success;
}

SYSCALL NO_OPTIMIZE static int from_read(void* UNUSED(arg_p), uint8_t* buf_p, size_t size) {
  if (!fwup_delta_slot_range_valid(delta_state.slot_size, delta_state.active_slot_offset_pointer,
                                   size)) {
    LOGE("Rd OOB %zu+%zu>%zu", delta_state.active_slot_offset_pointer, size, delta_state.slot_size);
    return -1;
  }

  RTOS_THREAD_WITH_PRIVILEGE({
    memcpy(buf_p,
           (void*)(delta_state.active_slot_base_addr + delta_state.active_slot_offset_pointer),
           size);
    delta_state.active_slot_offset_pointer += size;
  });

  return 0;
}

static int from_seek(void* UNUSED(arg_p), int offset) {
  size_t next_offset;

  if (!fwup_delta_slot_seek(delta_state.slot_size, delta_state.active_slot_offset_pointer, offset,
                            &next_offset)) {
    LOGE("Seek OOB %zu+%d>%zu", delta_state.active_slot_offset_pointer, offset,
         delta_state.slot_size);
    return -1;
  }

  delta_state.active_slot_offset_pointer = next_offset;
  return 0;
}

static int to_write(void* UNUSED(arg_p), const uint8_t* buf_p, size_t size) {
  const uint8_t* src = buf_p;
  size_t remaining = size;
  size_t logical_offset = 0;

  if (!target_slot_logical_offset(&logical_offset)) {
    LOGE("Tgt OOB %zu+%zu>%zu", delta_state.target_slot_offset_pointer,
         delta_state.write_buffer_fill, delta_state.slot_size);
    return -1;
  }

  if (!fwup_delta_slot_range_valid(delta_state.slot_size, logical_offset, size)) {
    LOGE("Wr OOB %zu+%zu+%zu>%zu", delta_state.target_slot_offset_pointer,
         delta_state.write_buffer_fill, size, delta_state.slot_size);
    return -1;
  }

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
        LOGE("Wr fail %08X", (unsigned int)flash_addr);
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
      LOGE("Wr fail %08X", (unsigned int)flash_addr);
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

  if (!fwup_delta_slot_range_valid(delta_state.slot_size, delta_state.target_slot_offset_pointer,
                                   MCU_FLASH_WRITE_ALIGNMENT)) {
    LOGE("Flush OOB %zu>%zu", delta_state.target_slot_offset_pointer, delta_state.slot_size);
    return false;
  }

  // Pad the remaining buffer with 0xFF (erased flash value)
  memset(delta_state.write_buffer + delta_state.write_buffer_fill, 0xFF,
         MCU_FLASH_WRITE_ALIGNMENT - delta_state.write_buffer_fill);

  const uintptr_t flash_addr =
    delta_state.target_slot_base_addr + delta_state.target_slot_offset_pointer;

  if (!fwup_flash_write(perf.flash_write, (void*)flash_addr, delta_state.write_buffer,
                        MCU_FLASH_WRITE_ALIGNMENT)) {
    LOGE("Flush %08X", (unsigned int)flash_addr);
    return false;
  }

  delta_state.target_slot_offset_pointer += MCU_FLASH_WRITE_ALIGNMENT;
  delta_state.write_buffer_fill = 0;

  return true;
}

SYSCALL NO_OPTIMIZE static bool fwup_delta_open(void) {
  bool opened = false;
  RTOS_THREAD_WITH_PRIVILEGE({
    if (delta_state.file_opened) {
      // Close any previously opened files. This is an issue if this function
      // is called twice.
      close();
    }

    if (fs_file_exists(PATCH_PATH) && fs_remove(PATCH_PATH) != 0) {
      BITLOG_EVENT(fwup_delta_init_err, 2);
    } else {
      if (fs_open(&fwup_file_handle, PATCH_PATH, FS_O_RDWR | FS_O_CREAT) < 0) {
        BITLOG_EVENT(fwup_delta_init_err, 2);
      } else {
        opened = true;
      }
    }
  });
  return opened;
}

SYSCALL NO_OPTIMIZE static bool fwup_delta_write_patch(fwpb_fwup_transfer_cmd* cmd) {
  ASSERT(cmd);

  fwpb_fwup_transfer_cmd_fwup_data_t data = cmd->fwup_data;

  const uint32_t max_chunk_size = fwup_flash_get_max_chunk_size();
  ASSERT(max_chunk_size != 0u);
  if (cmd->sequence_id > ((UINT32_MAX - cmd->offset) / max_chunk_size)) {
    BITLOG_EVENT(fwup_delta_write_patch_err, FWUP_DELTA_WRITE_PATCH_ERR_STATUS_ADDRESS_OVERFLOW);
    close();
    return false;
  }
  const uint32_t write_address_offset = (cmd->sequence_id * max_chunk_size) + cmd->offset;

  if (data.size > max_chunk_size) {
    LOGE("Bad data sz");
    close();
    return false;
  }

  // Seek to the write address to ensure that writing a patch is idempotent.
  // In the happy path, this seek does nothing. But if the host retries a chunk we've
  // already written (i.e. the same sequence_id), then we'll seek to the same spot that
  // chunk was written last time. Without this seek, we would continue growing the end
  // of the file, resulting in an invalid patch.
  //
  // NOTE: littlefs bounds checks the address, so we don't need a separate bounds
  // check for `write_address_offset`.
  bool success = false;
  RTOS_THREAD_WITH_PRIVILEGE({
    if (fs_file_seek(&fwup_file_handle, write_address_offset, FS_SEEK_SET) < 0) {
      LOGE("Seek fail");
      close();
    } else {
      if (fs_file_write(&fwup_file_handle, data.bytes, data.size) < 0) {
        BITLOG_EVENT(fwup_delta_write_patch_err,
                     FWUP_DELTA_WRITE_PATCH_ERR_STATUS_PATCH_WRITE_FAIL);
        close();
      } else {
        success = true;
      }
    }
  });

  return success;
}

SYSCALL NO_OPTIMIZE static void fwup_delta_cleanup_stale_signing_files(void) {
  RTOS_THREAD_WITH_PRIVILEGE({
    fs_remove("signing_payload");
    fs_remove("signing_sigs");
  });
}

bool fwup_delta_init(fwup_delta_cfg_t cfg, perf_counter_t* perf_flash_write,
                     perf_counter_t* perf_erase) {
  perf.process = perf_create(PERF_ELAPSED, fwup_delta_process_inline);
  perf.flash_write = perf_flash_write;
  perf.erase = perf_erase;

  delta_state.remaining_patch_size = cfg.patch_size;
  delta_state.active_slot_base_addr = cfg.active_slot_base_addr;
  delta_state.slot_size = cfg.slot_size;
  delta_state.active_slot_offset_pointer = 0;
  delta_state.target_slot_base_addr = cfg.target_slot_base_addr;
  delta_state.target_slot_offset_pointer = 0;
  delta_state.delta_initialized = false;
  delta_state.write_buffer_fill = 0;

  if (cfg.slot_size == 0) {
    LOGE("Bad slot sz: %zu", cfg.slot_size);
    return false;
  }

  if (cfg.active_slot_base_addr > (UINTPTR_MAX - cfg.slot_size) ||
      cfg.target_slot_base_addr > (UINTPTR_MAX - cfg.slot_size)) {
    LOGE("Slot overflow");
    return false;
  }

  if ((cfg.target_slot_base_addr % MCU_FLASH_WRITE_ALIGNMENT) != 0) {
    LOGE("Unalign %p", (void*)cfg.target_slot_base_addr);
    return false;
  }

  memset(&apply_patch, 0, sizeof(apply_patch));

  if (cfg.patch_size < FWUP_DELTA_MIN_PATCH_SIZE || cfg.patch_size > FWUP_DELTA_MAX_PATCH_SIZE) {
    LOGE("Bad patch sz");
    return false;
  }

  // Clean up any stale streaming-signing files — FWUP and streaming signing
  // share transient flash space and are never concurrent.
  fwup_delta_cleanup_stale_signing_files();

  if (cfg.mode == fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT) {
    delta_state.file_opened = fwup_delta_open();
    if (!delta_state.file_opened) {
      return false;
    }
  }

  if (detools_apply_patch_init(&apply_patch, from_read, from_seek, cfg.patch_size, to_write,
                               NULL) != 0) {
    LOGE("Delta init");
    return false;
  }

  delta_state.header_size = 0;  // Reset; will be set by fwup_delta_check_header().
  delta_state.delta_initialized = true;
  return true;
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
  LOGE("Patch process fail: %s", detools_error_as_string(res));
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
      break;
#endif
      break;
    case fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT:
      // Write the patch to the filesystem and process it in one shot
      // after the NFC transfer is complete.
      result = fwup_delta_write_patch(cmd);
      break;
    default:
      LOGE("Bad delta mode");
      break;
  }

  if (!result) {
    rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
  } else {
    rsp_out->rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS;
  }

  return result;
}

static NO_OPTIMIZE secure_bool_t verify_patch(uint8_t* buf, uint32_t buf_size,
                                              uint32_t patch_size) {
  ASSERT(buf && buf_size >= (SHA256_DIGEST_SIZE + FWUP_DELTA_PATCH_SIGNATURE_SIZE));

  if (patch_size < FWUP_DELTA_MIN_PATCH_SIZE) {
    LOGE("Patch too small");
    return SECURE_FALSE;
  }

  memzero(&delta_state.sha256_ctx, sizeof(delta_state.sha256_ctx));
  if (!crypto_sha256_stream_init(&delta_state.sha256_ctx)) {
    LOGE("SHA256 init fail");
    return SECURE_FALSE;
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
      return SECURE_FALSE;
    }
    if (!crypto_sha256_stream_update(&delta_state.sha256_ctx, buf, read_size)) {
      BITLOG_EVENT(fwup_delta_verify_patch_err, 2);
      return SECURE_FALSE;
    }
    bytes_read += read_size;
  }

  memzero(buf, buf_size);
  if (!crypto_sha256_stream_final(&delta_state.sha256_ctx, buf)) {
    BITLOG_EVENT(fwup_delta_verify_patch_err, 3);
    return SECURE_FALSE;
  }

  key_handle_t key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = security_config.fwup_delta_patch_pubkey,
    .key.size = FWUP_DELTA_PATCH_PUBKEY_SIZE,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  uint8_t* sig = buf + SHA256_DIGEST_SIZE;
  if (fs_file_read(&fwup_file_handle, sig, FWUP_DELTA_PATCH_SIGNATURE_SIZE) !=
      FWUP_DELTA_PATCH_SIGNATURE_SIZE) {
    BITLOG_EVENT(fwup_delta_verify_patch_err, 4);
    memzero(buf, buf_size);
    return SECURE_FALSE;
  }

  volatile secure_bool_t verified = crypto_ecc_verify_hash(&key, buf, SHA256_DIGEST_SIZE, sig);
  SECURE_IF_FAILIN(verified != SECURE_TRUE) {
    BITLOG_EVENT(fwup_delta_verify_patch_err, 5);
    memzero(buf, buf_size);
    return SECURE_FALSE;
  }

  return SECURE_TRUE;
}

fwup_verify_status_t fwup_delta_check_header_from_buf(const uint8_t* buf, size_t buf_size,
                                                      fwpb_semver* expected_version,
                                                      bool require_header) {
  if (buf_size < sizeof(fwup_delta_header_v1_t)) {
    return FWUP_VERIFY_ERROR;
  }

  const fwup_delta_header_v1_t* header = (const fwup_delta_header_v1_t*)buf;

  if (memcmp(header->magic, fwup_delta_header_magic, FWUP_DELTA_HEADER_MAGIC_SIZE) != 0) {
    if (require_header) {
      LOGE("No hdr");
      return FWUP_VERIFY_MISSING_HEADER;
    }
    return FWUP_VERIFY_SUCCESS;
  }

  // Reject corrupt headers. Future header versions are expected to be
  // binary-compatible with v1 (same initial fields, possibly extended),
  // so we only require header_version >= 1 and header_size >= V1_SIZE.
  if (header->header_version < FWUP_DELTA_HEADER_VERSION_1 ||
      header->header_size < FWUP_DELTA_HEADER_V1_SIZE) {
    LOGE("Bad hdr v%d s%d", header->header_version, header->header_size);
    return FWUP_VERIFY_ERROR;
  }

  if (expected_version != NULL &&
      (expected_version->major > UINT8_MAX || expected_version->minor > UINT8_MAX ||
       expected_version->patch > UINT8_MAX || header->fw_major != expected_version->major ||
       header->fw_minor != expected_version->minor ||
       header->fw_patch != expected_version->patch)) {
    LOGE("Hdr ver mismatch");
    return FWUP_VERIFY_CONFIRMATION_MISMATCH;
  }

  return FWUP_VERIFY_SUCCESS;
}

SYSCALL fwup_verify_status_t fwup_delta_check_header(fwpb_semver* expected_version,
                                                     bool require_header) {
  fwup_verify_status_t result = FWUP_VERIFY_ERROR;
  RTOS_THREAD_WITH_PRIVILEGE({
    do {
      fwup_delta_header_v1_t header = {0};

      if (!delta_state.file_opened) {
        LOGE("No patch file for header check");
        break;
      }

      if (fs_file_seek(&fwup_file_handle, 0, FS_SEEK_SET) < 0) {
        LOGE("Header check seek fail");
        break;
      }

      if (fs_file_read(&fwup_file_handle, &header, sizeof(header)) != (int32_t)sizeof(header)) {
        LOGE("Header read fail");
        break;
      }

      result = fwup_delta_check_header_from_buf((const uint8_t*)&header, sizeof(header),
                                                expected_version, require_header);

      // Store the header size in delta_state so apply_patch_from_file() knows how many
      // bytes to skip. Only set when the magic was present (MISSING_HEADER means no magic).
      if (memcmp(header.magic, fwup_delta_header_magic, FWUP_DELTA_HEADER_MAGIC_SIZE) == 0) {
        // Validate header_size against the actual patch file to catch corruption
        // before WILL_APPLY_PATCH is sent.
        int32_t file_size = fs_file_size(&fwup_file_handle);
        if (file_size <= (int32_t)FWUP_DELTA_PATCH_SIGNATURE_SIZE ||
            header.header_size >= (uint32_t)(file_size - FWUP_DELTA_PATCH_SIGNATURE_SIZE)) {
          LOGE("Hdr sz %d>%" PRId32, header.header_size, file_size);
          result = FWUP_VERIFY_ERROR;
          break;
        }
        delta_state.header_size = header.header_size;
      }
    } while (0);
  });
  return result;
}

static NO_OPTIMIZE bool apply_patch_from_file(void) {
  bool result = false;
  uint8_t* buf = delta_state.patch_io_buffer;
  const size_t buf_size = sizeof(delta_state.patch_io_buffer);

  if (!delta_state.file_opened) {
    if (fs_open(&fwup_file_handle, PATCH_PATH, FS_O_RDWR) < 0) {
      LOGE("Open patch.bin fail");
      goto out;
    }
    delta_state.file_opened = true;
  }

  if (fs_file_seek(&fwup_file_handle, 0, FS_SEEK_SET) < 0) {
    LOGE("Patch seek fail");
    goto out;
  }

  int size_or_err = fs_file_size(&fwup_file_handle);
  if (size_or_err < 0) {
    LOGE("Patch size fail");
    goto out;
  }
  uint32_t patch_size = size_or_err;
  int finalize_status;

  // First pass: verify the patch.
  volatile secure_bool_t patch_verified = verify_patch(buf, buf_size, patch_size);
  SECURE_IF_FAILIN(patch_verified != SECURE_TRUE) { goto out; }

  // Erase the entire target slot.  The signature is held in RAM and will be
  // written to flash only after verification succeeds in fwup_finish().
  if (!fwup_flash_erase_app(perf.erase, (void*)delta_state.target_slot_base_addr)) {
    goto out;
  }

  // Second pass: apply the patch, seeking past the header first.
  if (patch_size <= (uint32_t)(FWUP_DELTA_PATCH_SIGNATURE_SIZE + delta_state.header_size)) {
    LOGE("Patch too small for header + signature");
    goto out;
  }

  if (fs_file_seek(&fwup_file_handle, delta_state.header_size, FS_SEEK_SET) < 0) {
    LOGE("Seek past header fail");
    goto out;
  }

  uint32_t detools_patch_size =
    patch_size - FWUP_DELTA_PATCH_SIGNATURE_SIZE - delta_state.header_size;
  if (detools_apply_patch_init(&apply_patch, from_read, from_seek, detools_patch_size, to_write,
                               NULL) != 0) {
    LOGE("Detools init fail");
    goto out;
  }

  uint32_t bytes_read = 0;
  uint32_t total_bytes = detools_patch_size;
  while (bytes_read < total_bytes) {
    size_t to_read = BLK_MIN(buf_size, total_bytes - bytes_read);
    size_t read_size = fs_file_read(&fwup_file_handle, buf, to_read);
    if (read_size != to_read) {
      LOGE("Patch read fail");
      goto out;
    }
    if (detools_apply_patch_process(&apply_patch, buf, read_size) != 0) {
      LOGE("Patch process fail");
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
      LOGE("Flush fail");
    }
  }
  LOGD("Patch %s", result ? "ok" : "fail");
out:
  if (delta_state.file_opened) {
    close();
  }
  fwup_delta_cleanup_stale_patch();
  return result;
}

SYSCALL NO_OPTIMIZE bool fwup_delta_finish(fwpb_fwup_finish_cmd* cmd) {
  // Note: The caller will handle verification.
  if (!delta_state.delta_initialized) {
    LOGE("Delta not initialized");
    return false;
  }

  bool success;

  switch (cmd->mode) {
    case fwpb_fwup_mode_FWUP_MODE_DELTA_INLINE:
#if DELTA_INLINE_SUPPORTED
      RTOS_THREAD_WITH_PRIVILEGE({
        if (detools_apply_patch_finalize(&apply_patch) < 0) {
          success = false;
        } else {
          success = flush_write_buffer();
        }
      });
      break;
#else
      success = false;
      break;
#endif
    case fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT:
      LOGI("Patching");
      RTOS_THREAD_WITH_PRIVILEGE({ success = apply_patch_from_file(); });
      break;
    default:
      LOGE("Bad delta mode");
      success = false;
  }
  return success;
}

SYSCALL NO_OPTIMIZE void fwup_delta_cleanup_stale_patch(void) {
  RTOS_THREAD_WITH_PRIVILEGE({
    if (fs_file_exists(PATCH_PATH)) {
      if (fs_remove(PATCH_PATH) != 0) {
        LOGE("Del patch.bin fail");
      }
    }
  });
}
