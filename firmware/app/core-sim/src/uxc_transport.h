/**
 * @file uxc_transport.h
 * @brief UXC message transport layer for core-sim
 *
 * This provides a transport layer that mirrors the real firmware's UC protocol,
 * allowing core-sim to send/receive uxc_msg_host/device messages to/from
 * ui-simulate over a socket connection.
 *
 * The interface mirrors uc_route_register() for consistent patterns.
 */

#pragma once

#include "uxc.pb.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * Initialize the UXC transport layer.
 * Must be called after uxc_socket_init().
 */
void uxc_transport_init(void);

/**
 * Send a uxc_msg_host message to ui-simulate.
 * Used by ui_execute_command() to forward display commands.
 *
 * @param msg The host message to send
 * @return true if sent successfully
 */
bool uxc_transport_send_host_msg(const fwpb_uxc_msg_host* msg);

/**
 * Poll for incoming uxc_msg_device messages from ui-simulate.
 * Non-blocking - returns false immediately if no data available.
 * If a message is received, it is dispatched to registered handlers.
 *
 * @return true if a message was received and processed
 */
bool uxc_transport_poll(void);

/**
 * Handler function type for incoming device messages.
 * Mirrors uc_route callback signature.
 */
typedef void (*uxc_route_handler_t)(const fwpb_uxc_msg_device* msg, void* context);

/**
 * Register a handler for a specific message tag.
 * Mirrors uc_route_register() pattern from real firmware.
 *
 * @param tag The which_msg tag to handle (e.g., fwpb_uxc_msg_device_display_action_tag)
 * @param handler Function to call when message with this tag is received
 * @param context User context passed to handler
 */
void uxc_transport_register_handler(uint32_t tag, uxc_route_handler_t handler, void* context);

/**
 * Send a UI event to ui-simulate.
 * Used to forward UI events that should be handled by ui-simulate's display_controller.
 *
 * @param event_type The ui_event_type_t value
 * @param data Event-specific data (can be NULL)
 * @param data_len Length of data in bytes
 * @return true if sent successfully
 */
bool uxc_transport_send_ui_event(uint32_t event_type, const void* data, uint32_t data_len);

/**
 * Send a reset onboarding message to ui-simulate.
 * Used after device wipe to tell ui-simulate to reset to onboarding state.
 *
 * @return true if sent successfully
 */
bool uxc_transport_send_reset_onboarding(void);
