/**
 * @file
 *
 * @{
 */

#pragma once

#include "ipc.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Starts the shutdown timer for power off fallback.
 *
 * @param timeout_ms Milliseconds to wait before forcing shutdown.
 */
void sysinfo_task_start_shutdown_timer(uint32_t timeout_ms);

/**
 * @brief Returns `true` if the wallet device is in ship state.
 *
 * @details Ship state is a low power state designed to reduce as much current
 * draw from the device. Devices enter this state before packing in the
 * factory. This low power mode still allows the wallet to power up during
 * un-boxing.
 *
 * @return `true` if in ship state, otherwise `false`.
 */
bool sysinfo_task_in_ship_state(void);

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
 * @brief Handles a coredump received from the co-processor.
 *
 * @param message  Received IPC message.
 */
void sysinfo_task_handle_coproc_coredump(ipc_ref_t* message);

/**
 * @brief Handles bitlog events received from the co-processor.
 *
 * @param message  Received IPC message.
 */
void sysinfo_task_handle_coproc_events(ipc_ref_t* message);

/**
 * @brief Requests metadata from the co-processor.
 *
 * @param cmd  Pointer to the received wallet command.
 */
void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd);

/**
 * @brief Platform-specific pre-power down preparation.
 *
 * On W3: Coordinates with UXC to enter touch monitor mode before powering down.
 * On W1: No-op.
 */
void sysinfo_task_port_prepare_power_down(void);

/**
 * @brief Platform-specific power down.
 */
void sysinfo_task_port_power_down(void);

/**
 * @brief Dispatch a confirmation result to the registered handler.
 *
 * On W3: Calls confirmation_manager_dispatch_result().
 * On W1: Returns false (confirmation not supported).
 *
 * @param message IPC message containing get_confirmation_result_cmd
 * @return true if a handler was found and dispatched, false if no handler registered
 */
bool sysinfo_task_port_dispatch_confirmation_result(ipc_ref_t* message);

/**
 * @brief Requests a coredump from a co-processor.
 *
 * @param cmd  Pointer to the received wallet command.
 */
void sysinfo_task_request_coproc_coredump(fwpb_wallet_cmd* cmd);

/**
 * @brief Requests bitlog events from the co-processor.
 *
 * @param cmd  Pointer to the received wallet command.
 */
void sysinfo_task_request_coproc_events(fwpb_wallet_cmd* cmd);

/**
 * @brief Populates MCU info for the device info response.
 *
 * @param[out] rsp  Device info response to populate with MCU information.
 */
void sysinfo_task_port_populate_mcu_info(fwpb_device_info_rsp* rsp);

/** @} */
