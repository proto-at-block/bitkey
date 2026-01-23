/**
 * @file
 *
 * @{
 */

#pragma once

#include "ipc.h"
#include "wallet.pb.h"

/**
 * @brief Sends initial device info to UI (w3-core only).
 *
 * Called after filesystem and KV are initialized, before entering main message loop.
 * Loads and sends device metadata and brightness setting to display controller.
 * No-op on w1 (no display).
 */
void sysinfo_task_port_send_device_info(void);

/**
 * @brief Registers product-specific event listeners.
 */
void sysinfo_task_register_listeners(void);

/**
 * @brief Handles platform-specific IPC messages.
 *
 * @return true if message was handled, false otherwise
 */
bool sysinfo_task_port_handle_message(ipc_ref_t* message);

/**
 * @brief Handles a co-processor boot message.
 *
 * @param message  Received IPC message.
 */
void sysinfo_task_handle_coproc_boot(ipc_ref_t* message);

/**
 * @brief Handles firmware metadata received from a co-processor.
 *
 * @param message  Received IPC message.
 */
void sysinfo_task_handle_coproc_metadata(ipc_ref_t* message);

/**
 * @brief Requests metadata from the co-processor.
 *
 * @param cmd  Pointer to the received wallet command.
 */
void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd);

/**
 * @brief Platform-specific sleep preparation and power down.
 *
 * On W3: Coordinates with UXC to enter touch monitor mode before powering down.
 * On W1: Powers down immediately (no coprocessor coordination needed).
 */
void sysinfo_task_port_prepare_sleep_and_power_down(void);

/** @} */
