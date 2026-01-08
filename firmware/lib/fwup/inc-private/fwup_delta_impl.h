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

#include "perf.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Configuration state for applying a delta (patch) firmware update.
 */
typedef struct {
  fwpb_fwup_mode mode;              //<! Firmware update mode.
  size_t patch_size;                //<! Size of the patch, in bytes.
  uintptr_t active_slot_base_addr;  //<! Address in flash of the active firmware application slot.
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

/** @} */
