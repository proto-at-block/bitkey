/**
 * @file
 *
 * @brief Firmware Update
 *
 * @{
 */

#pragma once

#include "fwup_addr.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Initializes the FWUP library.
 *
 * @param _target_slot_addr       Address in flash for the slot to write a new
 *                                application image to.
 * @param _current_slot_addr      Address in flash of the current application
 *                                image.
 * @param _target_slot_signature  Address in flash for the signature of the new
 *                                application image.
 * @param target_app_slot_size    Size of the @p _target_slot_addr in bytes.
 * @param support_bl_upgrade      `true` if bootloader update should be allowed,
 *                                otherwise `false`.
 *
 * @note Must be called ONCE before any other firmware update functions are called.
 */
void fwup_init(void* _target_slot_addr, void* _current_slot_addr, void* _target_slot_signature,
               uint32_t target_app_slot_size, bool support_bl_upgrade);

/**
 * @brief Starts a firmware update session.
 *
 * @param[in]  cmd      Pointer to the FWUP start command received from the host.
 * @param[out] rsp_out  Output pointer to store the operation result.
 *
 * @return `true` if operation was successful, otherwise `false`.
 */
bool fwup_start(fwpb_fwup_start_cmd* cmd, fwpb_fwup_start_rsp* rsp_out);

/**
 * @brief Applies FWUP data received from the host to the target firmware update slot.
 *
 * @param[in]  cmd      Pointer to the FWUP transfer command containing the FWUP data.
 * @param[out] rsp_out  Output pointer to store the operation result.
 *
 * @return `true` if operation was successful, otherwise `false`.
 *
 * @note Should not be called unless #fwup_start() has been called.
 */
bool fwup_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out);

/**
 * @brief Finalizes a firmware update.
 *
 * @param[in]  cmd      Pointer to the FWUP finish command received from the host.
 * @param[out] rsp_out  Output pointer to store the operation result.
 *
 * @return `true` if operation was successful, otherwise `false`.
 *
 * @note The caller should reset after calling this method.
 *
 * @note Should not be called unless #fwup_transfer() has been called at least once.
 */
bool fwup_finish(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out);

/**
 * @brief Check if a firmware update is in progress (core or coprocessor).
 *
 * @return `true` if any FWUP is in progress, otherwise `false`.
 */
bool fwup_in_progress(void);

/**
 * @brief Mark core firmware update as pending or completed.
 *
 * @param pending `true` to mark core FWUP as in progress, `false` otherwise.
 */
void fwup_mark_pending(bool pending);

/**
 * @brief Mark coprocessor firmware update as pending or completed.
 *
 * @param pending `true` to mark coproc FWUP as in progress, `false` otherwise.
 */
void fwup_mark_coproc_pending(bool pending);

/** @} */
