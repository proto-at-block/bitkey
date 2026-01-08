/**
 * @file
 *
 * @brief UC Routing
 */

#pragma once

#include "rtos.h"

#include <stdint.h>

/**
 * @brief Type definition for a callback invoked when a proto is received in the
 * UC routing system matching the indicated proto tag during registration.
 *
 * @details This callback is responsible for ensuring the memory allocated for
 * the @p proto is free'd by calling `uc_free_recv_proto()`.
 *
 * @param proto    Pointer to the received proto message.
 * @param context  User-supplied context pointer.
 *
 * @note This callback should not block.
 */
typedef void (*uc_route_callback_t)(void* proto, void* context);

/**
 * @brief Registers a callback to be invoked on receipt of a proto matching the
 * given @p proto_tag.
 *
 * @param proto_tag  The proto tag to register for.
 * @param cb         The callback to invoke.
 * @param context    User-supplied context pointer to pass to the callback.
 *
 * @note Only one callback can be registered per proto tag.
 */
void uc_route_register(uint32_t proto_tag, uc_route_callback_t cb, void* context);

/**
 * @brief Registers a queue to be posted to on receipt of a proto matching the
 * given @p proto_tag.
 *
 * @param proto_tag  The proto tag to register for.
 * @param queue      The RTOS queue to post to.
 */
void uc_route_register_queue(uint32_t proto_tag, rtos_queue_t* queue);

/**
 * @brief Pends for a UXC proto on the specified queue.
 *
 * @param queue  Pointer to the RTOS queue to pend on.
 *
 * @return Pointer to the received proto.
 *
 * @note Caller is responsible for free'ing the proto by calling `uc_free_recv_proto()`.
 */
void* uc_route_pend_queue(rtos_queue_t* queue);
