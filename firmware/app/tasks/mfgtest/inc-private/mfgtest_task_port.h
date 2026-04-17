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

/**
 * @brief Pass through for a set device production lock command from the host to the co-processor.
 *
 * @param wallet_cmd Pointer to the wallet command proto.
 */
void mfgtest_task_port_handle_coproc_device_set_production_lock_command(
  fwpb_wallet_cmd* wallet_cmd);

/**
 * @brief Pass through for a get device production lock command from the host to the co-processor.
 *
 * @param wallet_cmd Pointer to the wallet command proto.
 */
void mfgtest_task_port_handle_coproc_device_get_production_lock_command(
  fwpb_wallet_cmd* wallet_cmd);

/**
 * @brief Handles a set device production lock response from the co-processor.
 *
 * @param message IPC message containing the proto from the co-processor.
 */
void mfgtest_task_port_handle_coproc_device_set_production_lock_response(ipc_ref_t* message);

/**
 * @brief Handles a get device production lock response from the co-processor.
 *
 * @param message IPC message containing the proto from the co-processor.
 */
void mfgtest_task_port_handle_coproc_device_get_production_lock_response(ipc_ref_t* message);

/**
 * @brief Handles a touch data collection command from the host.
 *
 * @param message IPC message containing the host message proto.
 */
void mfgtest_task_port_handle_touch_data_cmd(ipc_ref_t* message);

/**
 * @brief Handles a touch point IPC message from the UI task.
 *
 * @param message IPC message containing touch point data.
 */
void mfgtest_task_port_handle_touch_point(ipc_ref_t* message);

/** @} */
