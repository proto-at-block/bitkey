#pragma once

#include "uxc.pb.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Maximum size of payload data for display send messages.
 */
#define DISPLAY_SEND_PAYLOAD_MAX_SIZE 16

/**
 * @brief Handler function type for encoding payload data into a protobuf.
 *
 * @param proto The protobuf message to populate
 * @param payload The payload data to encode
 */
typedef void (*display_send_handler_t)(fwpb_uxc_msg_device* proto, const void* payload);

/**
 * @brief Flags for display send messages.
 */
typedef enum {
  DISPLAY_SEND_FLAG_NONE = 0,
  DISPLAY_SEND_FLAG_IMMEDIATE = (1 << 0),  // Use uc_send_immediate() instead of uc_send()
} display_send_flags_t;

/**
 * @brief Generic message structure for display send queue.
 *
 * Contains a handler function pointer and payload data. The handler is responsible
 * for encoding the payload into the protobuf message.
 */
typedef struct {
  display_send_handler_t handler;  ///< Handler function to encode payload into protobuf
  uint8_t payload[DISPLAY_SEND_PAYLOAD_MAX_SIZE];  ///< Caller-defined payload data
  display_send_flags_t flags;  ///< Send flags (e.g. DISPLAY_SEND_FLAG_IMMEDIATE)
  volatile bool* sent;         ///< Optional: set to true after message is sent (caller-owned)
} display_send_msg_t;

/**
 * @brief Function type for sending display messages.
 *
 * @param msg The message to send
 * @return true if sent/queued successfully, false otherwise
 */
typedef bool (*display_send_fn_t)(const display_send_msg_t* msg);

/**
 * @brief Register the send function implementation.
 *
 * Called by app/tasks/display to register the queue-based send implementation.
 * This breaks the circular dependency between lib/display and app/tasks/display.
 *
 * @param send_fn The function to call when sending messages
 */
void display_send_register(display_send_fn_t send_fn);

/**
 * @brief Queue a message to be sent to Core.
 *
 * This is called by lib/display when it needs to send a message to Core.
 * The message is forwarded to the registered send function.
 *
 * @param msg The message to queue
 * @return true if queued successfully, false if no handler registered or queue full
 */
bool display_send_queue_msg(const display_send_msg_t* msg);