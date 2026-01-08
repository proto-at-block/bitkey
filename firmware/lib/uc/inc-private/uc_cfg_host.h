/**
 * @file
 *
 * @brief Configuration for the UC Host.
 *
 * @{
 */

#pragma once

#include "uxc.pb.h"

/**
 * @brief Type alias for the proto type that messages are encoded in for
 * sending to the companion MCU.
 */
#define UC_CFG_ENC_PROTO_TYPE fwpb_uxc_msg_host

/**
 * @brief Alias for the field structure for the sent protos.
 */
#define UC_CFG_ENC_PROTO_FIELDS fwpb_uxc_msg_host_fields

/**
 * @brief Type alias for the proto type that messages are decoded into on
 * receipt from the companion MCU.
 */
#define UC_CFG_DEC_PROTO_TYPE fwpb_uxc_msg_device

/**
 * @brief Alias for the field structure for the received protos.
 */
#define UC_CFG_DEC_PROTO_FIELDS fwpb_uxc_msg_device_fields

/**
 * @brief Size of the buffer to use for reading a serialized proto.
 *
 * @note This value is padded to account for future proto additions.
 * @note For backwards-compatibility, this value should not be changed.
 */
#define UC_CFG_RD_BUFFER_SIZE 888u

/**
 * @brief Size of the buffer to use for encoding a serialized proto.
 *
 * @note This value is padded to account for future proto additions.
 * @note For backwards-compatibility, this value should not be changed.
 */
#define UC_CFG_WR_BUFFER_SIZE 704u

/** @} */
