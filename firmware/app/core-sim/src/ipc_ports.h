/**
 * @file ipc_ports.h
 * @brief POSIX IPC port interface for core-sim
 */

#ifndef IPC_PORTS_H
#define IPC_PORTS_H

#include "rtos_queue.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * Initialize all IPC ports with POSIX queues.
 * Must be called before any IPC operations or task creation.
 */
void ipc_ports_init(void);

/**
 * Route a proto command via IPC and wait for response.
 *
 * @param pb_tag Protobuf message tag (fwpb_wallet_cmd_*_tag)
 * @param cmd Command buffer (encoded protobuf)
 * @param cmd_size Size of command buffer
 * @param rsp Response buffer (to fill with encoded protobuf)
 * @param rsp_size In/out: max size on input, actual size on output
 * @return true if command was handled, false otherwise
 */
bool ipc_ports_route_proto(uint16_t pb_tag, uint8_t* cmd, uint32_t cmd_size, uint8_t* rsp,
                           uint32_t* rsp_size);

/**
 * Get the queue handle for a given port index.
 *
 * @param port Port index (0-5)
 * @return Queue handle, or NULL if invalid port
 */
rtos_queue_t* ipc_ports_get_queue(uint32_t port);

#endif /* IPC_PORTS_H */
