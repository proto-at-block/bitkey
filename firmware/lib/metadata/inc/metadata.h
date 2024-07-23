#pragma once

#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

#define METADATA_BUILD_STR_MAX_LEN  7
#define METADATA_GIT_STR_MAX_LEN    64
#define METADATA_HW_REV_STR_MAX_LEN 32
#define METADATA_HASH_LENGTH        20

typedef enum {
  META_TGT_BL,
  META_TGT_APP_A,
  META_TGT_APP_B,
  META_TGT_MAX,
} metadata_target_t;

typedef struct {
  metadata_target_t target;
  struct {
    char id[METADATA_GIT_STR_MAX_LEN + 1];
    char branch[METADATA_GIT_STR_MAX_LEN + 1];
  } git;
  struct {
    uint8_t major;
    uint8_t minor;
    uint8_t patch;
  } version;
  char build[METADATA_BUILD_STR_MAX_LEN + 1];
  uint64_t timestamp;
  uint8_t sha1hash[METADATA_HASH_LENGTH];
  char hardware_revision[METADATA_HW_REV_STR_MAX_LEN];
} metadata_t;

typedef enum {
  METADATA_VALID,
  METADATA_INVALID,
  METADATA_MISSING,
  METADATA_ROOT_ERROR,
} metadata_result_t;

metadata_result_t metadata_get(metadata_target_t target, metadata_t* meta);
metadata_result_t metadata_validity(metadata_target_t target);
metadata_result_t metadata_get_active_slot(metadata_t* meta, fwpb_firmware_slot* slot);
metadata_result_t metadata_get_firmware_version(uint32_t* version);
void metadata_print(metadata_target_t target);

#ifndef EMBEDDED_BUILD
metadata_result_t metadata_read(metadata_t* meta, void* buffer, size_t buffer_size);
#endif
