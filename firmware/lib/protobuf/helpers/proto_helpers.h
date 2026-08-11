#pragma once

#include "assert.h"
#include "ipc.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define PROTO_CMD_METADATA_ALIGNMENT (sizeof(uint32_t))
#define PROTO_CMD_METADATA_OFFSET                                                                  \
  (((sizeof(fwpb_wallet_cmd) + PROTO_CMD_METADATA_ALIGNMENT - 1) / PROTO_CMD_METADATA_ALIGNMENT) * \
   PROTO_CMD_METADATA_ALIGNMENT)

typedef struct {
  uint32_t seq;
} proto_cmd_metadata_t;

#define PROTO_DECODED_CMD_ALLOC_SIZE (PROTO_CMD_METADATA_OFFSET + sizeof(proto_cmd_metadata_t))
#define PROTO_ROUTED_CMD_ALLOC_SIZE  (sizeof(ipc_proto_routed_cmd_t) + fwpb_wallet_cmd_size)
#define PROTO_CMD_ALLOC_SIZE                                                                   \
  ((PROTO_DECODED_CMD_ALLOC_SIZE > PROTO_ROUTED_CMD_ALLOC_SIZE) ? PROTO_DECODED_CMD_ALLOC_SIZE \
                                                                : PROTO_ROUTED_CMD_ALLOC_SIZE)

fwpb_wallet_cmd* proto_get_cmd(uint8_t* serialized_cmd, uint32_t length);
fwpb_wallet_rsp* proto_get_rsp(void);
void proto_send_rsp(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp);

// Command sequence tracking for stale response detection.
// Bound by the NFC thread (wca.c) into routed IPC commands.
// Copied by proto_get_cmd; echoed by proto_send_rsp when cmd is non-NULL.
void proto_set_cmd_seq(uint32_t seq);
void proto_retire_cmd_seq(uint32_t seq);
void proto_set_rsp_seq(uint32_t seq);
uint32_t proto_get_last_rsp_seq(void);

// Retrieve the cmd_seq from a cmd allocation (stored by proto_get_cmd).
// Use this to stash the seq before freeing cmd in async forwarding paths.
uint32_t proto_get_cmd_seq(const fwpb_wallet_cmd* cmd);

// Send a response with an explicit sequence number. Use for async coprocessor
// callbacks where cmd was already freed and the seq was carried by the async
// responder. Returns false if the seq is stale and the response was dropped.
bool proto_send_rsp_with_seq(uint32_t seq, fwpb_wallet_rsp* rsp);
bool proto_secure_channel_message_has_valid_sizes(const fwpb_secure_channel_message* message,
                                                  size_t ciphertext_size);

// UXC NFC-forwarding contract:
// - Core forwarding paths must call proto_uxc_prepare_cmd() before uc_send().
// - UXC handlers that send wallet responses must use uc_send_rsp_for_cmd() or
//   uc_send_rsp_with_seq() so msg_host.seq is echoed into msg_device.seq.
// - Core response handlers must use proto_uxc_take_rsp_seq() before proto_send_rsp_with_seq().
// A missing (zero) response seq is uncorrelatable and is dropped. The only
// exception is the FWUP start/transfer/finish recovery path, which accepts old
// seqless UXC firmware just far enough to update UXC over NFC. Do not add
// generic sysinfo/mfgtest fallback for mixed-version devices; new Core + old
// UXC is a degraded recovery state, not a fully supported steady state.
void proto_uxc_prepare_cmd(fwpb_uxc_msg_host* msg, const fwpb_wallet_cmd* cmd);
bool proto_uxc_take_rsp_seq(const fwpb_uxc_msg_device* msg, uint32_t* seq, const char* name);

// This pair of functions should rarely be used. They're used a task
// needs to send a response immediately over NFC, but still use the data
// afterwards. Don't forget to free the buffers!
// cmd must be non-NULL so the response uses the seq bound to that command.
void proto_send_rsp_without_free(fwpb_wallet_cmd* cmd, fwpb_wallet_rsp* rsp);
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
