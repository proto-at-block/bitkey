/**
 * @file uxc_socket_server.h
 * @brief TCP socket server for UXC communication with ui-simulate
 *
 * This provides a socket interface for core-sim to communicate with
 * ui-simulate, mirroring the UART communication between EFR32 and UXC
 * on real hardware.
 */

#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * Initialize the UXC socket server on the specified port.
 * @param port TCP port to listen on
 * @return true if server started successfully
 */
bool uxc_socket_init(int port);

/**
 * Cleanup and close the socket server.
 */
void uxc_socket_cleanup(void);

/**
 * Get the client file descriptor for use with select().
 * @return Client fd if connected, -1 otherwise
 */
int uxc_socket_get_client_fd(void);

/**
 * Get the server file descriptor for use with select().
 * @return Server fd if initialized, -1 otherwise
 */
int uxc_socket_get_server_fd(void);

/**
 * Check if a client (ui-simulate) is connected.
 * @return true if connected
 */
bool uxc_socket_is_connected(void);

/**
 * Close the currently connected client without shutting down the server.
 */
void uxc_socket_close_client(void);

/**
 * Accept pending connections (non-blocking).
 * Call this periodically to accept new connections.
 */
void uxc_socket_accept(void);

/**
 * Send data to the connected ui-simulate client.
 * @param data Data to send
 * @param len Length of data
 * @return true if sent successfully
 */
bool uxc_socket_send(const uint8_t* data, uint32_t len);

/**
 * Receive data from the connected ui-simulate client (non-blocking).
 * @param buf Buffer to receive into
 * @param max_len Maximum bytes to receive
 * @return Number of bytes received, 0 if no data, -1 on error/disconnect
 */
int uxc_socket_recv(uint8_t* buf, uint32_t max_len);
