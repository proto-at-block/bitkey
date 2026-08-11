/**
 * @file handler_device.h
 * @brief Authentication handlers for core-sim
 *
 * Device info commands (meta_cmd, device_id_cmd, device_info_cmd) are handled
 * by the real sysinfo_task via IPC routing. Only authentication handlers
 * remain here since the auth_task is not fully implemented for POSIX.
 */

#ifndef HANDLER_DEVICE_H
#define HANDLER_DEVICE_H

#include <stdbool.h>
#include <stdint.h>

/**
 * Set the POSIX authentication state.
 * This allows tests to control the authentication state for core-sim.
 */
void core_sim_set_authenticated(bool authenticated);

/**
 * Handle query_authentication command.
 * Returns the current authentication state for the POSIX build.
 */
bool stdio_query_authentication_handler(uint8_t* rsp, uint32_t* rsp_size);

#endif /* HANDLER_DEVICE_H */
