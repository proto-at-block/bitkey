#pragma once

#include "ipc_port_gen.h"
#include "mempool.h"
#include "rtos.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define IPC_TIMEOUT_MAX RTOS_QUEUE_TIMEOUT_MAX

typedef uint32_t ipc_port_t;

typedef struct {
  uint32_t tag;
  size_t length;
  void* object;
} ipc_ref_t;

typedef struct {
  uint32_t timeout_ms;  // The time to wait for the receiving queue to have a slot available to
                        // receive the message.
  bool take_ownership;  // Take ownership of the memory, copying it into the IPC mempool.
} ipc_options_t;

extern const ipc_options_t ipc_default_send_opts;
extern const ipc_options_t ipc_default_recv_opts;

typedef void (*ipc_proto_ready_cb_t)(uint8_t*, uint32_t);

/**
 * ipc_register_port() - Associate an IPC port with an RTOS queue.
 * @port: The `ipc_port_t` to register.
 * @queue: The `rtos_queue_t*` to associate with the port.
 *
 * Context: This must be called before a port can be used!
 */
#define ipc_register_port(port, queue) ({ _ipc_register_port(port, #port, queue); })

/**
 * ipc_send_opt() - Send a message to a port with options.
 * See ipc_send().
 */
#define ipc_send_opt(port, msg, len, tag_enum, options) \
  ({                                                    \
    ipc_ref_t ref = {                                   \
      .tag = tag_enum,                                  \
      .length = len,                                    \
      .object = msg,                                    \
    };                                                  \
    _ipc_send(port, &ref, options);                     \
  })

/**
 * ipc_send() - Send a message to a port.
 * @port: The destination `ipc_port_t`.
 * @msg: A pointer to the message to send.
 * @len: Length of the message.
 * @tag_enum: The tag enum associated with the message struct.
 *
 * IMPORTANT: The message is passed BY REFERENCE, not by value.
 * Internally, an `ipc_ref_t` which points to the message is created and placed in the queue.
 * The message pointer MUST remain valid until the receiving task is done with it.
 *
 * Context: The port must have been registered.
 */
#define ipc_send(port, msg, len, tag_enum) \
  ipc_send_opt(port, msg, len, tag_enum, ipc_default_send_opts)

/**
 * ipc_send_cp() - Send a message to a port, giving ownership to the IPC mempool.
 *
 * This is a convenience macro for sending a message with `take_ownership` set to true.
 */
#define ipc_send_cp(port, msg, len, tag_enum)        \
  ({                                                 \
    ipc_options_t options = ipc_default_send_opts;   \
    options.take_ownership = true;                   \
    ipc_send_opt(port, msg, len, tag_enum, options); \
  })

/**
 * ipc_send_empty() - Send an empty message to a port.
 * @port: The destination `ipc_port_t`.
 * @tag_enum: The tag enum associated with the message struct.
 *
 * IMPORTANT: The message is passed BY REFERENCE, not by value.
 * Internally, an `ipc_ref_t` which points to the message is created and placed in the queue.
 * The message pointer MUST remain valid until the receiving task is done with it.
 *
 * Context: The port must have been registered.
 */
#define ipc_send_empty(port, tag_enum) ipc_send_opt(port, NULL, 0, tag_enum, ipc_default_send_opts)

/**
 * ipc_recv() - Receive a message on a port.
 * @port: The receiving `ipc_port_t`.
 * @msg: A pointer to the message to send.
 *
 * IMPORTANT: The message is received BY REFERENCE, not by value. See note in `ipc_send()` for
 * details.
 *
 * Context: The port must have been registered.
 */
#define ipc_recv(port, msg) ipc_recv_opt(port, msg, ipc_default_recv_opts)

/**
 * ipc_release() - Free memory owned by this library within `ipc_ref_t`.
 *
 * IMPORTANT: Must be called after receiving a message with `take_ownership` set to true.
 */
void ipc_release(ipc_ref_t* ref);

/**
 * ipc_recv_opt() - Receive a message on a port with options.
 * See ipc_recv().
 */
bool ipc_recv_opt(ipc_port_t port, ipc_ref_t* ref, ipc_options_t options);

/**
 * ipc_proto_get_response_buffer() - Get a scratch buffer to fill in an encoded protobuf.
 *
 * Context: Must be freed by ipc_proto_send_response_buffer().
 * Return: The buffer.
 */
uint8_t* ipc_proto_get_response_buffer(void);

/**
 * ipc_proto_send_response_buffer() - Release the currently held scratch buffer and send it.
 * @encoded_proto: Encoded protobuf to copy into the buffer.
 * @size: Size of the encoded proto.
 */
void ipc_proto_send_response_buffer(uint8_t* encoded_proto, uint32_t size);

/**
 * ipc_proto_notify_done() - Release the currently held scratch buffer.
 */
void ipc_proto_notify_done(void);

// Regular users don't need to call the below functions.

/**
 * ipc_register_proto_memory() - Set the mempool used for storing protos
 * @pool: The mempool.
 */
void ipc_proto_register_api(mempool_t* pool, uint8_t* response_buffer,
                            ipc_proto_ready_cb_t callback);

/**
 * ipc_proto_alloc() - Allocate from the shared proto mempool.
 * @size: Protobuf size.
 */
uint8_t* ipc_proto_alloc(uint32_t size);

/**
 * ipc_proto_free() - Free from the shared proto mempool.
 * @buffer: The buffer.
 */
void ipc_proto_free(uint8_t* buffer);

/**
 * ipc_proto_route() - Send a proto to the associated port.
 * You probably don't need to call this! There should only be one callsite.
 */
bool ipc_proto_route(uint16_t pb_tag, uint8_t* buffer, uint32_t size);

// Private functions, but are exposed because they are used in public macros.
// Do not call!
void _ipc_register_port(ipc_port_t port, char* name, rtos_queue_t* queue);
bool _ipc_send(ipc_port_t port, ipc_ref_t* ref, ipc_options_t options);
