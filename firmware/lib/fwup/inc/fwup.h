/**
 * @file
 *
 * @brief Firmware Update
 *
 * @{
 */

#pragma once

#include "fwup_addr.h"
#include "secutils.h"
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
 * @param require_confirmation    `SECURE_TRUE` if on-device confirmation should be required
 *                                for firmware updates, `SECURE_FALSE` otherwise.
 *
 * @note Must be called ONCE before any other firmware update functions are called.
 */
void fwup_init(void* _target_slot_addr, void* _current_slot_addr, void* _target_slot_signature,
               uint32_t target_app_slot_size, bool support_bl_upgrade,
               secure_bool_t require_confirmation);

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
 * @brief Verifies a firmware update image.
 *
 * Applies any pending delta patch and verifies the signature, version, and
 * confirmation version of the new image.  On success the verified signature
 * remains in RAM but is NOT written to flash — call #fwup_commit_signature()
 * to make the image bootable.
 *
 * @param[in]  cmd      Pointer to the FWUP finish command received from the host.
 * @param[out] rsp_out  Output pointer to store the operation result.
 *
 * @return `true` if verification was successful, otherwise `false`.
 *
 * @note Should not be called unless #fwup_transfer() has been called at least once.
 */
bool fwup_finish(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out);

/**
 * @brief Commits the pending firmware signature to flash.
 *
 * Writes the signature that was verified by #fwup_finish() to the target
 * slot's signature region, making the image bootable by the bootloader.
 *
 * @return `true` if the signature was written successfully, otherwise `false`.
 *
 * @note Must only be called after a successful #fwup_finish().
 */
bool fwup_commit_signature(void);

/**
 * @brief Writes a signature to a specific flash address.
 *
 * Used by recovery to commit a staged signature that was persisted to the
 * filesystem across a power loss.
 *
 * @param target_addr  Flash address for the signature.
 * @param signature    Pointer to the signature data (ECC_SIG_SIZE bytes).
 *
 * @return `true` if the signature was written successfully, otherwise `false`.
 */
bool fwup_commit_signature_to(void* target_addr, const uint8_t* signature);

/**
 * @brief Removes stale patch state from an abandoned delta update.
 *
 * @note Should be called only after filesystem is ready.
 */
void fwup_cleanup_stale_patch(void);

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

/**
 * @brief Check if a coprocessor firmware update is pending.
 *
 * @return `true` if coproc FWUP is in progress, otherwise `false`.
 */
bool fwup_is_coproc_pending(void);

/**
 * @brief Mark that FWUP is finalizing and an MCU reset/reboot is pending.
 */
void fwup_mark_reset_pending(void);

/**
 * @brief Clear the firmware update reset pending state.
 */
void fwup_clear_reset_pending(void);

/**
 * @brief Check whether FWUP commands should be rejected for current FWUP state.
 *
 * @return `true` if FWUP commands should be rejected, otherwise `false`.
 *
 * @note This rejects FWUP commands while reset is pending.
 */
bool fwup_should_reject_cmd(void);

/**
 * @brief Get the firmware update confirmation requirement setting.
 *
 * @return `SECURE_TRUE` if confirmation is required, `SECURE_FALSE` otherwise.
 *
 * @note This function returns a secure_bool_t to maintain fault injection protection.
 *       Callers should use SECURE_IF_FAILOUT macro to check the value.
 */
NO_OPTIMIZE secure_bool_t fwup_get_require_confirmation(void);

/**
 * @brief Validates the delta patch version header before the patch is applied.
 *
 * For DELTA_ONESHOT updates, reads the version header from the stored patch
 * file and checks it against the confirmation version recorded in fwup_start().
 * On failure, sets rsp_out->rsp_status to the appropriate error status.
 *
 * Must be called after all patch data has been transferred and BEFORE sending
 * the WILL_APPLY_PATCH response, while NFC is still active. For non-delta
 * modes this is a no-op that returns true.
 *
 * @param[in]  cmd      Pointer to the FWUP finish command.
 * @param[out] rsp_out  Output pointer populated with the error status on failure.
 *
 * @return `true` if the check passed (or is not applicable), otherwise `false`.
 */
bool fwup_pre_apply_check(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out);

/**
 * @brief Returns a pointer to the pending signature buffer.
 *
 * @return Pointer to the signature bytes (ECC_SIG_SIZE bytes), or NULL
 *         if no signature is pending.
 */
const uint8_t* fwup_get_pending_signature(void);

/**
 * @brief Returns the target slot's signature address in flash.
 *
 * @return Flash address where the signature should be committed.
 */
void* fwup_get_target_slot_signature_addr(void);

/** @} */
