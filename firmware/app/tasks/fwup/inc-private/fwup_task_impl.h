/**
 * @file fwup_task_impl.h
 *
 * @brief Implementation specific APIs.
 *
 * @{
 */

#pragma once

#include "ipc.h"
#include "wallet.pb.h"

#include <stdbool.h>

/**
 * @brief Registers port-specific listeners.
 */
void fwup_task_register_listeners(void);

/**
 * @brief Sends a FWUP start command to an on-device co-processor.
 *
 * @param cmd        Pointer to the FWUP command.
 *
 * @return `true` if FWUP started successfully, otherwise `false`.
 */
bool fwup_task_send_coproc_fwup_start_cmd(fwpb_wallet_cmd* cmd);

/**
 * @brief Sends a FWUP transfer command to an on-device co-processor.
 *
 * @param cmd        Pointer to the FWUP command.
 */
void fwup_task_send_coproc_fwup_transfer_cmd(fwpb_wallet_cmd* cmd);

/**
 * @brief Sends a FWUP finish command to an on-device co-processor.
 *
 * @param cmd        Pointer to the FWUP command.
 */
void fwup_task_send_coproc_fwup_finish_cmd(fwpb_wallet_cmd* cmd);

/**
 * @brief Processes a FWUP start response from a co-processor.
 *
 * @param message  Pointer to the received IPC message.
 */
void fwup_task_handle_coproc_fwup_start(ipc_ref_t* message);

/**
 * @brief Processes a FWUP transfer response from a co-processor.
 *
 * @param message  Pointer to the received IPC message.
 */
void fwup_task_handle_coproc_fwup_transfer(ipc_ref_t* message);

/**
 * @brief Processes a FWUP finish response from a co-processor.
 *
 * @param message  Pointer to the received IPC message.
 */
void fwup_task_handle_coproc_fwup_finish(ipc_ref_t* message);

/** @} */
