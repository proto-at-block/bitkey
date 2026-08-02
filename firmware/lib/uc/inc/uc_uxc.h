#pragma once

#include "assert.h"
#include "uc.h"
#include "uxc.pb.h"

#include <stdbool.h>
#include <stdint.h>

// Use these helpers for UXC responses that may be forwarded into a wallet_rsp.
// They make the Core/UXC seq echo explicit at the send site while leaving
// uc_send() available for uncorrelated messages like boot status, display
// events, and secure-channel internals.
static inline bool uc_send_rsp_with_seq(uint32_t seq, fwpb_uxc_msg_device* rsp) {
  ASSERT(rsp != NULL);
  rsp->seq = seq;
  return uc_send(rsp);
}
