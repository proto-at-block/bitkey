#pragma once

// TODO(W-16140): Currently only tested with a single input / single output. We need to support
// up to 5 inputs, but must first identify the correct limits for both the libew mempool
// (WALLY_MEMPOOL_REGIONS in lib/psbt/src/psbt.c) and the signing manager buffers below.
#define KEY_MANAGER_MAX_INPUTS 1u

// Maximum unsigned PSBT size (supports KEY_MANAGER_MAX_INPUTS inputs)
#define MAX_PSBT_SIZE 1536
// Signature overhead budget
#define MAX_SIGNATURE_OVERHEAD 512

#define MAX_SIGNED_PSBT_SIZE (MAX_PSBT_SIZE + MAX_SIGNATURE_OVERHEAD)

#define SIGN_CHUNK_SIZE sizeof(((fwpb_sign_transfer_cmd*)0)->chunk_data.bytes)
