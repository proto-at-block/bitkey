
#include "FuzzedDataProvider.h"
extern "C" {
#include "fff.h"
#include "metadata.h"
size_t active_slot;
size_t bl_metadata_size;
size_t bl_metadata_page;
size_t app_a_metadata_size;
size_t app_a_metadata_page;
size_t app_b_metadata_size;
size_t app_b_metadata_page;

FAKE_VALUE_FUNC(int, _putchar, int);
}

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

/* while this is in the metadata section, this is fuzzer also hammers lib/msgpack and the associated third-party library
  this fuzzer should be ran with the following parameters: ./metadata-fuzz -dict=metadata.dict corpus/ -max_len=1024
  where corpus contains a cleaned copy of the metadata section from the firmware image
*/

DEFINE_FFF_GLOBALS;
#define METADATA_SECTION_SIZE 1024

void copy_metadata_to_proto(metadata_t* metadata, fwpb_firmware_metadata* proto) {
  strncpy(proto->git_id, metadata->git.id, METADATA_GIT_STR_MAX_LEN);
  strncpy(proto->git_branch, metadata->git.branch, METADATA_GIT_STR_MAX_LEN);

  proto->has_version = true;
  proto->version.major = metadata->version.major;
  proto->version.minor = metadata->version.minor;
  proto->version.patch = metadata->version.patch;

  strncpy(proto->build, metadata->build, METADATA_BUILD_STR_MAX_LEN);

  proto->timestamp = metadata->timestamp;
  memcpy(proto->hash.bytes, metadata->sha1hash, METADATA_HASH_LENGTH);
  proto->hash.size = METADATA_HASH_LENGTH;

  strncpy(proto->hw_revision, metadata->hardware_revision, METADATA_HW_REV_STR_MAX_LEN);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {

  if(size < sizeof(metadata_t)) {
    return 0;
  }

  metadata_result_t res;
  metadata_t metadata;
  memset(&metadata, 0, sizeof(metadata_t));

  fwpb_wallet_rsp rsp;
  memset(&rsp, 0, sizeof(fwpb_wallet_rsp));

  rsp.which_msg = fwpb_wallet_rsp_meta_rsp_tag;

  res = metadata_read(&metadata, (void*)data, size);
  rsp.msg.meta_rsp.meta_bl.valid = (res == METADATA_VALID);
  copy_metadata_to_proto(&metadata, &(rsp.msg.meta_rsp.meta_bl));

  return 0;
}
