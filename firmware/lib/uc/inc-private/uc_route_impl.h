#pragma once

#include <stdint.h>

/**
 * @brief Maximum number of proto message tag callbacks that can be registered.
 */
#define UC_ROUTE_MAX_NUM_REGISTRATIONS 15u

/**
 * @brief Routes a proto to the task that is registered to listen for it.
 *
 * @param proto_tag  Tag identifying the ``which_msg`` of the UXC proto.
 * @param proto      Pointer to the allocated UXC proto.
 *
 * @note Caller is responsible for free'ing the memory by calling #uc_free().
 */
void uc_route(uint32_t proto_tag, void* proto);
