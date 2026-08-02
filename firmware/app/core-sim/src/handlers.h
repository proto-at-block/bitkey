/**
 * @file handlers.h
 * @brief Shared protobuf encoding utilities for core-sim
 */

#ifndef HANDLERS_H
#define HANDLERS_H

#include "pb_encode.h"
#include "stdio_defs.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * Encode a wallet response into a buffer.
 *
 * @param rsp Output buffer for encoded response
 * @param rsp_size Output: number of bytes written
 * @param buf_size Size of the output buffer
 * @param response Response structure to encode
 * @return true on success, false on encoding failure
 */
static inline bool encode_wallet_response(uint8_t* rsp, uint32_t* rsp_size, size_t buf_size,
                                          const fwpb_wallet_rsp* response) {
  pb_ostream_t os = pb_ostream_from_buffer(rsp, buf_size);
  if (!pb_encode(&os, fwpb_wallet_rsp_fields, response)) {
    LOG("Failed to encode response: %s", PB_GET_ERROR(&os));
    return false;
  }
  *rsp_size = os.bytes_written;
  return true;
}

#endif /* HANDLERS_H */
