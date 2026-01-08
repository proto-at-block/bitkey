/**
 * @file mfgtest_task_port.h
 *
 * @brief Manufacturing Test Task Port
 *
 * @details Port-specific handlers for manufacturing test commands.
 *
 * @{
 */

#pragma once

#include "ipc.h"
#include "wallet.pb.h"

/**
 * @brief Initializes port-specific handlers.
 */
void mfgtest_task_port_init(void);

/**
 * @brief Handles a button command from the host.
 *
 * @param message IPC message containing the host message proto.
 */
void mfgtest_task_port_handle_button_cmd(ipc_ref_t* message);

/**
 * @brief Handles a show screen command from the host.
 *
 * @param message IPC message containing the host message proto.
 */
void mfgtest_task_port_handle_show_screen_cmd(ipc_ref_t* message);

/**
 * @brief Pass through for a GPIO command from the host to the co-processor.
 *
 * @param wallet_cmd Pointer to the wallet command proto.
 */
void mfgtest_task_port_handle_coproc_gpio_command(fwpb_wallet_cmd* wallet_cmd);

/**
 * @brief Handles a touch related command from the host.
 *
 * @param message IPC message containing the host message proto.
 */
void mfgtest_task_port_handle_touch_cmd(ipc_ref_t* message);

/**
 * @brief Handles a GPIO response from the co-processor.
 *
 * @param message IPC message containing the proto from the co-processor.
 */
void mfgtest_task_port_handle_coproc_gpio_response(ipc_ref_t* message);

/** @} */
