#include "metadata.h"

#include "assert.h"
#include "log.h"
#include "msgpack.h"
#include "printf.h"

#include <inttypes.h>
#include <stddef.h>
#include <string.h>
#include <time.h>

#define MAX_KEY_LEN        64
#define TARGET_STR(target) ('A' + (target - 1))

typedef union {
  uint8_t bytes[6];
  struct {
    uint32_t crc;
    uint16_t size;
  } __attribute__((packed)) fields;
} metadata_header_t;

/* Metadata memory sections */
extern char bl_metadata_size[];
extern char bl_metadata_page[];
extern char app_a_metadata_size[];
extern char app_a_metadata_page[];
extern char app_b_metadata_size[];
extern char app_b_metadata_page[];

extern char active_slot[];

void* SECTION(".bl_metadata_section") bl_metadata;
void* SECTION(".app_a_metadata_section") app_a_metadata;
void* SECTION(".app_b_metadata_section") app_b_metadata;

static metadata_t _meta = {0};

static void print_meta(metadata_t* meta);
static inline void* memory_get_metadata_addr(metadata_target_t target);
static inline size_t memory_get_metadata_size(metadata_target_t target);
STATIC_VISIBLE_FOR_TESTING metadata_result_t metadata_read(metadata_t* meta, void* buffer,
                                                           size_t buffer_size);
static metadata_result_t metadata_read_from_serialized(metadata_t* meta, cmp_ctx_t* context);
static metadata_result_t validate_meta(void* base_addr, size_t len, uint32_t crc);
static uint32_t crc32b(unsigned char* message, size_t len);

metadata_result_t metadata_get(metadata_target_t target, metadata_t* meta) {
  ASSERT(target < META_TGT_MAX);
  ASSERT(meta != NULL);

  const metadata_result_t state = metadata_validity(target);
  if (state != METADATA_VALID) {
    return state;
  }

  memset(meta, 0, sizeof(metadata_t));
  meta->target = target;

  return metadata_read(meta, memory_get_metadata_addr(target), memory_get_metadata_size(target));
}

metadata_result_t metadata_get_active_slot(metadata_t* meta, fwpb_firmware_slot* slot) {
  switch ((uint32_t)active_slot) {
    case fwpb_firmware_slot_SLOT_A:
      *slot = fwpb_firmware_slot_SLOT_A;
      return metadata_get(META_TGT_APP_A, meta);
    case fwpb_firmware_slot_SLOT_B:
      *slot = fwpb_firmware_slot_SLOT_B;
      return metadata_get(META_TGT_APP_B, meta);
    default:
      return METADATA_MISSING;
  }
}

metadata_result_t metadata_validity(metadata_target_t target) {
  ASSERT(target < META_TGT_MAX);

  void* meta_addr = memory_get_metadata_addr(target);
  metadata_header_t* hdr = meta_addr;
  void* metadata_start = (void*)((uint32_t)meta_addr + sizeof(metadata_header_t));

  // Missing metadata has a length of 0xffff
  if (hdr->fields.size > memory_get_metadata_size(target)) {
    return METADATA_MISSING;
  }

  return validate_meta(metadata_start, hdr->fields.size, hdr->fields.crc);
}

void metadata_print(metadata_target_t target) {
  ASSERT(target < META_TGT_MAX);

  switch (metadata_get(target, &_meta)) {
    case METADATA_VALID:
      print_meta(&_meta);
      break;
    case METADATA_MISSING:
      printf("Application (%c) Metadata\n  Empty\n", TARGET_STR(target));
      break;
    case METADATA_INVALID: /* falls-through */
    case METADATA_ROOT_ERROR:
      printf("Application (%c) Metadata\n  Invalid\n", TARGET_STR(target));
      break;
    default:
      printf("Application (%c) Metadata\n  Unknown\n", TARGET_STR(target));
      break;
  }
}

static void print_meta(metadata_t* meta) {
  switch (meta->target) {
    case META_TGT_BL:
      printf("Bootloader Metadata\n");
      break;
    case META_TGT_APP_A:
      printf("Application (A) Metadata\n");
      break;
    case META_TGT_APP_B:
      printf("Application (B) Metadata\n");
      break;
    default:
      break;
  }
  printf("  Git ID:     %s\n", meta->git.id);
  printf("  Git Branch: %s\n", meta->git.branch);
  printf("  Version:    %u.%u.%u\n", meta->version.major, meta->version.minor, meta->version.patch);
  printf("  Build Time: %" PRIu64 "\n", meta->timestamp);
  printf("  Build Type: %s\n", meta->build);
  printf("  HW Rev:     %s\n", meta->hardware_revision);

  char hash[(METADATA_HASH_LENGTH * 2) + 1]; /* Each byte prints two chars */
  for (uint32_t i = 0; i < METADATA_HASH_LENGTH; i++) {
    sprintf(hash + (i * 2), "%02x", meta->sha1hash[i]);
  }
  hash[(METADATA_HASH_LENGTH * 2)] = '\0';
  printf("  Hash:       %s\n", hash);
}
static inline void* memory_get_metadata_addr(metadata_target_t target) {
  ASSERT(target < META_TGT_MAX);

  switch (target) {
    case META_TGT_BL:
      return (void*)bl_metadata_page;
    case META_TGT_APP_A:
      return (void*)app_a_metadata_page;
    case META_TGT_APP_B:
      return (void*)app_b_metadata_page;
    default:
      return (void*)bl_metadata_page;
  }
}

static inline size_t memory_get_metadata_size(metadata_target_t target) {
  ASSERT(target < META_TGT_MAX);

  switch (target) {
    case META_TGT_BL:
      return (size_t)bl_metadata_size;
    case META_TGT_APP_A:
      return (size_t)app_a_metadata_size;
    case META_TGT_APP_B:
      return (size_t)app_b_metadata_size;
    default:
      return 0;
  }
}

STATIC_VISIBLE_FOR_TESTING metadata_result_t metadata_read(metadata_t* meta, void* buffer,
                                                           size_t buffer_size) {
  cmp_ctx_t context;
  msgpack_mem_access_t cma;

  if (buffer_size < sizeof(metadata_header_t)) {
    return METADATA_MISSING;
  }

  uintptr_t buffer_start = (uintptr_t)buffer + (uintptr_t)sizeof(metadata_header_t);
  msgpack_mem_access_ro_init(&context, &cma, (void*)buffer_start,
                             buffer_size - sizeof(metadata_header_t));

  return metadata_read_from_serialized(meta, &context);
}

static metadata_result_t metadata_read_from_serialized(metadata_t* meta, cmp_ctx_t* context) {
  uint32_t key_count = 0;
  bool error = false;

  if (!cmp_read_map(context, &key_count)) {
    LOGE("Failed to parse root, error: %s", cmp_strerror(context));
    return METADATA_ROOT_ERROR;
  }

  while (!error && key_count--) {
    char key[MAX_KEY_LEN];
    uint32_t key_len;

    key_len = sizeof(key);

    if (!cmp_read_str(context, key, &key_len)) {
      LOGE("Failed to parse key, error: %s\n", cmp_strerror(context));
      key[0] = '\0';
    }

    /* Consider error if not parsed */
    bool success = false;

    if (!strncmp("git_id", key, key_len)) {
      uint32_t len = METADATA_GIT_STR_MAX_LEN;
      success = cmp_read_str(context, &meta->git.id[0], &len);
    } else if (!strncmp("git_branch", key, key_len)) {
      uint32_t len = METADATA_GIT_STR_MAX_LEN;
      success = cmp_read_str(context, &meta->git.branch[0], &len);
    } else if (!strncmp("ver_major", key, key_len)) {
      success = cmp_read_uchar(context, &meta->version.major);
    } else if (!strncmp("ver_minor", key, key_len)) {
      success = cmp_read_uchar(context, &meta->version.minor);
    } else if (!strncmp("ver_patch", key, key_len)) {
      success = cmp_read_uchar(context, &meta->version.patch);
    } else if (!strncmp("timestamp", key, key_len)) {
      success = cmp_read_ulong(context, &meta->timestamp);
    } else if (!strncmp("build", key, key_len) || !strncmp("build_type", key, key_len)) {
      uint32_t len = METADATA_GIT_STR_MAX_LEN;
      success = cmp_read_str(context, &meta->build[0], &len);
    } else if (!strncmp("hash", key, key_len)) {
      uint32_t len = METADATA_HASH_LENGTH;
      success = cmp_read_bin(context, &meta->sha1hash[0], &len);
    } else if (!strncmp("hw_rev", key, key_len)) {
      uint32_t len = METADATA_HW_REV_STR_MAX_LEN;
      success = cmp_read_str(context, &meta->hardware_revision[0], &len);
    } else {
      LOGW("Skipping unknown msgpack key: %s", key);
      cmp_object_t obj;
      success = cmp_skip_object(context, &obj);
    }

    if (!success) {
      LOGE("Failed to parse value of key %s, error: %s", key, cmp_strerror(context));
      error = true;
    }
  }

  return !error ? METADATA_VALID : METADATA_INVALID;
}

static metadata_result_t validate_meta(void* base_addr, size_t len, uint32_t crc) {
  const uint32_t crc_calc = crc32b(base_addr, len);

  // LOGD("CRC32: 0x%04lX (stored = 0x%04lX, len = %i)", crc_calc, crc, len);
  return crc == crc_calc ? METADATA_VALID : METADATA_INVALID;
}

/* Source:
 * https://stackoverflow.com/questions/15030011/same-crc32-for-python-and-c
 */
static uint32_t crc32b(unsigned char* message, size_t len) {
  int k = 0;
  uint32_t crc = 0xFFFFFFFF;
  while (len--) {
    crc ^= *message++;
    for (k = 0; k < 8; k++) crc = crc & 1 ? (crc >> 1) ^ 0xedb88320 : crc >> 1;
  }
  return ~crc;
}
