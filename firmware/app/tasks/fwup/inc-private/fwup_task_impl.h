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

/**
 * @brief Processes a FWUP commit signature response from a co-processor.
 *
 * @param message  Pointer to the received IPC message.
 */
void fwup_task_handle_commit_sig_rsp(ipc_ref_t* message);

/**
 * @brief Attempts to start the atomic commit protocol.
 *
 * Called after Core's own fwup_finish() succeeds.  If UXC was also updated in
 * this session, stages Core's signature and sends a commit command to UXC.
 * The response arrives asynchronously via #fwup_task_handle_commit_sig_rsp.
 *
 * @return `true` if the atomic commit was started (caller should return to the
 *         event loop), `false` if not applicable or failed (caller should
 *         fall through to the non-atomic path).
 */
bool fwup_task_port_try_atomic_commit(void);

/**
 * @brief Returns whether this session uses deferred commit.
 */
bool fwup_task_port_is_deferred_session(void);

/**
 * @brief Handles coprocessor version update from sysinfo.
 *
 * @param message  Pointer to the received IPC message containing version.
 */
void fwup_task_handle_coproc_version(ipc_ref_t* message);

/**
 * @brief Handles FWUP start command with port-specific behavior.
 *
 * @param cmd      Decoded FWUP start command.
 *
 * @return `true` if handled successfully, otherwise `false`.
 */
bool fwup_task_port_handle_start_cmd(fwpb_wallet_cmd* cmd);

/**
 * @brief Handles FWUP confirmation result forwarded from sysinfo task.
 *
 * @param message  Pointer to the get_confirmation_result IPC message.
 *
 * @return `true` if handled successfully, otherwise `false`.
 */
bool fwup_handle_confirmation_result(ipc_ref_t* message);

/** @} */
