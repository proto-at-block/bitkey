/**
 * @file
 *
 * @brief Firmware Update Delta
 *
 * @details A delta firmware update uses the existing firmware image as a base
 * and applies patches to it to the target firmware application slot allowing
 * for faster updates by only transferring flash data that has changed between
 * the current image and the next image.
 *
 * @{
 */

#pragma once

#include "fwup_verify_impl.h"
#include "perf.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Magic bytes identifying the start of a BKFW delta patch header.
 */
#define FWUP_DELTA_HEADER_MAGIC_BYTES \
  { 'B', 'K', 'F', 'W' }
#define FWUP_DELTA_HEADER_MAGIC_SIZE 4

#define FWUP_DELTA_HEADER_VERSION_1 1
#define FWUP_DELTA_HEADER_V1_SIZE   9

/**
 * @brief Version 1 header prepended to delta patch files before the detools
 * patch data. The entire header is covered by the patch signature.
 *
 * Layout: [magic(4)][header_version(1)][header_size(1)][fw_major(1)][fw_minor(1)][fw_patch(1)]
 */
typedef struct __attribute__((packed)) {
  uint8_t magic[FWUP_DELTA_HEADER_MAGIC_SIZE];  //<! FWUP_DELTA_HEADER_MAGIC_BYTES
  uint8_t header_version;  //<! Header struct version (FWUP_DELTA_HEADER_VERSION_1)
  uint8_t header_size;     //<! Total header size in bytes (skip-forward field)
  uint8_t fw_major;        //<! Firmware major version
  uint8_t fw_minor;        //<! Firmware minor version
  uint8_t fw_patch;        //<! Firmware patch version
} fwup_delta_header_v1_t;

_Static_assert(sizeof(fwup_delta_header_v1_t) == FWUP_DELTA_HEADER_V1_SIZE,
               "fwup_delta_header_v1_t size mismatch");

/**
 * @brief Configuration state for applying a delta (patch) firmware update.
 */
typedef struct {
  fwpb_fwup_mode mode;              //<! Firmware update mode.
  size_t patch_size;                //<! Size of the patch, in bytes.
  uintptr_t active_slot_base_addr;  //<! Address in flash of the active firmware application slot.
  size_t slot_size;                 //<! Size in bytes of the application slots.
  uintptr_t target_slot_base_addr;  //<! Address in flash of the target firmware application slot.
} fwup_delta_cfg_t;

/**
 * @brief Initializes the FWUP delta module.
 *
 * @param cfg               Delta configuration.
 * @param perf_flash_write  Performance counter for flash writes.
 * @param perf_erase        Performance counter for flash erases.
 *
 * @return `true` if initialization was successful, otherwise `false`.
 */
bool fwup_delta_init(fwup_delta_cfg_t cfg, perf_counter_t* perf_flash_write,
                     perf_counter_t* perf_erase);

/**
 * @brief Returns true if [offset, offset + size) stays within the slot.
 */
bool fwup_delta_slot_range_valid(size_t slot_size, size_t offset, size_t size);

/**
 * @brief Applies a signed seek to an in-slot offset.
 *
 * @param slot_size        Size in bytes of the slot.
 * @param current_offset   Current in-slot offset.
 * @param offset           Signed delta to apply.
 * @param next_offset_out  Output for the updated offset on success.
 *
 * @return `true` if the seek stays within [0, slot_size], otherwise `false`.
 */
bool fwup_delta_slot_seek(size_t slot_size, size_t current_offset, int offset,
                          size_t* next_offset_out);

/**
 * @brief Transfers patch data.
 *
 * @param[in]  cmd      The received transfer command containing patch data.
 * @param[out] rsp_out  Pointer to the output status pointer.
 *
 * @return `true` if patch was applied successfully, otherwise `false`.
 */
bool fwup_delta_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out);

/**
 * @brief Finalizes a patch update.
 *
 * @param[in] cmd  Pointer to the FWUP finish command received from the host.
 *
 * @return `true` if operation was successful, otherwise `false`.
 */
bool fwup_delta_finish(fwpb_fwup_finish_cmd* cmd);

/**
 * @brief Removes stale patch state from a previously abandoned delta update.
 */
void fwup_delta_cleanup_stale_patch(void);

/**
 * @brief Validates a version header from a pre-read buffer.
 *
 * Contains the pure header-parsing logic used by fwup_delta_check_header().
 * Exposed separately to allow unit testing without filesystem setup.
 *
 * @param buf              Buffer containing at least sizeof(fwup_delta_header_v1_t) bytes.
 * @param buf_size         Size of @p buf.
 * @param expected_version Semver to compare against the header, or NULL to skip comparison.
 * @param require_header   If true and no header magic is found, returns FWUP_VERIFY_MISSING_HEADER.
 *
 * @return FWUP_VERIFY_SUCCESS on success, otherwise an error code.
 */
fwup_verify_status_t fwup_delta_check_header_from_buf(const uint8_t* buf, size_t buf_size,
                                                      fwpb_semver* expected_version,
                                                      bool require_header);

/**
 * @brief Reads and validates the version header from the stored patch file.
 *
 * Must be called after all patch data has been transferred (i.e. after
 * fwup_delta_transfer() has been called for all chunks). Records the header
 * size in internal state so that fwup_delta_finish() can skip it when feeding
 * data to detools.
 *
 * @param expected_version  Semver to compare against the header, or NULL to
 *                          skip the version comparison.
 * @param require_header    If true and no header magic is found, returns
 *                          FWUP_VERIFY_MISSING_HEADER. If false and no magic
 *                          is found, the check is skipped (old-format patch).
 *
 * @return FWUP_VERIFY_SUCCESS on success, otherwise an error code.
 */
fwup_verify_status_t fwup_delta_check_header(fwpb_semver* expected_version, bool require_header);

/** @} */
