#include "arithmetic.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "proto_helpers.h"
#include "wallet.pb.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdlib.h>
#include <string.h>

Test(proto_helpers, fill_bytes) {
  fwpb_wallet_rsp response_proto = fwpb_wallet_rsp_init_default;
  response_proto.which_msg = fwpb_wallet_rsp_derive_rsp_tag;
  response_proto.msg.derive_rsp.has_descriptor = true;
  response_proto.msg.derive_rsp.descriptor.has_origin_path = true;

  uint8_t origin_fingerprint[] = {0x37, 0x24, 0x9c, 0xd2};

  PROTO_FILL_BYTES(&response_proto, msg.derive_rsp.descriptor.origin_fingerprint,
                   origin_fingerprint, sizeof(origin_fingerprint));
  cr_assert(response_proto.msg.derive_rsp.descriptor.origin_fingerprint.size ==
            sizeof(origin_fingerprint));
  cr_util_cmp_buffers(response_proto.msg.derive_rsp.descriptor.origin_fingerprint.bytes,
                      origin_fingerprint, sizeof(origin_fingerprint));
}

Test(proto_helpers, set_repeated) {
  fwpb_wallet_rsp response_proto = fwpb_wallet_rsp_init_default;
  response_proto.which_msg = fwpb_wallet_rsp_derive_rsp_tag;
  response_proto.msg.derive_rsp.has_descriptor = true;
  response_proto.msg.derive_rsp.descriptor.has_origin_path = true;

  uint32_t origin_derivation_path[] = {84, 1, 0};

  PROTO_FILL_REPEATED(&response_proto, msg.derive_rsp.descriptor.origin_path,
                      origin_derivation_path, ARRAY_SIZE(origin_derivation_path));

  cr_assert(response_proto.msg.derive_rsp.descriptor.origin_path.child_count == 3);
  cr_util_cmp_buffers(response_proto.msg.derive_rsp.descriptor.origin_path.child,
                      origin_derivation_path, 3);
}
