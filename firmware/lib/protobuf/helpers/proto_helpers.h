#pragma once

#include "assert.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

fwpb_wallet_cmd* proto_get_cmd(uint8_t* serialized_cmd, uint32_t length);
fwpb_wallet_rsp* proto_get_rsp(void);
void proto_send_rsp(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp);

// This pair of functions should rarely be used. They're used a task
// needs to send a response immediately over NFC, but still use the data
// afterwards. Don't forget to free the buffers!
void proto_send_rsp_without_free(fwpb_wallet_rsp* rsp);
void proto_free_buffers(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp);

// Does not set 'has_*' = true, if present.
#define PROTO_FILL_BYTES(_proto, _field, _data, _size)   \
  ({                                                     \
    ASSERT(_size <= sizeof((_proto)->_field.bytes));     \
    ASSERT(_data != NULL);                               \
    memcpy((_proto)->_field.bytes, (void*)_data, _size); \
    (_proto)->_field.size = (pb_size_t)_size;            \
  })

// Fill a bounded repeated field
// Does not set 'has_*' = true, if present.
#define PROTO_FILL_REPEATED(_proto, _field, _data, _count)            \
  ({                                                                  \
    ASSERT(_count <= ARRAY_SIZE((_proto)->_field.child));             \
    ASSERT(_data != NULL);                                            \
    (_proto)->_field.child_count = _count;                            \
    ASSERT((uint32_t)_count < (UINT32_MAX / sizeof(_data[0])));       \
    memcpy((_proto)->_field.child, _data, _count * sizeof(_data[0])); \
  })
