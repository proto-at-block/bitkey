#include "kv.h"

#include "attributes.h"
#include "filesystem.h"
#include "log.h"

#include <string.h>

#define KVSTORE_FILE_NAME "kvstore.bin"

typedef struct {
  char tag[KV_MAX_KEY_LEN];  // Tag == key. We'll refer to it as 'tag' in this, since TLV is
                             // pertinent to the implementation.
  uint8_t len;
  uint8_t value[KV_MAX_VALUE_LEN];
} PACKED tlv_t;

static struct {
  uint8_t buf[KV_MAX_FILE_SIZE];
  uint32_t len;
  kv_lock_t lock;
  kv_unlock_t unlock;
} ctx = {
  .buf = {0},
  .len = 0,
};

static bool create_if_not_exist(void) {
  if (!fs_file_exists(KVSTORE_FILE_NAME)) {
    if (fs_touch(KVSTORE_FILE_NAME) != 0) {
      LOGE("Failed to create %s", KVSTORE_FILE_NAME);
      return false;
    }
  }
  return true;
}

static bool read_from_fs(void) {
  create_if_not_exist();
  uint32_t size_out = 0;
  const bool ret =
    fs_util_read_all_global(KVSTORE_FILE_NAME, (uint8_t*)ctx.buf, sizeof(ctx.buf), &size_out);
  if (!ret || (size_out > KV_MAX_FILE_SIZE)) {
    LOGE("Failed to read %s (%ld)", KVSTORE_FILE_NAME, size_out);
    return false;
  }
  ctx.len = size_out;
  return true;
}

static bool write_to_fs(void) {
  create_if_not_exist();
  return fs_util_write_global(KVSTORE_FILE_NAME, (uint8_t*)ctx.buf, ctx.len);
}

static tlv_t* locate(const char* key) {
  for (size_t off = 0; off < ctx.len; off += sizeof(tlv_t)) {
    tlv_t* tlv = (tlv_t*)(ctx.buf + off);
    if (strncmp(tlv->tag, key, KV_MAX_KEY_LEN) == 0) {
      return tlv;
    }
  }
  return NULL;
}

static bool append(const char* key, const void* value, uint8_t value_len) {
  if (ctx.len + sizeof(tlv_t) > KV_MAX_FILE_SIZE) {
    LOGE("KV store is full (%ld)", ctx.len);
    return false;
  }

  if (value_len > KV_MAX_VALUE_LEN) {
    LOGE("Value too long (%d)", value_len);
    return false;
  }

  tlv_t* tlv = (tlv_t*)(ctx.buf + ctx.len);  // Seek to the end of the buffer.

  strncpy(tlv->tag, key, KV_MAX_KEY_LEN);

  tlv->len = value_len;
  memcpy(tlv->value, value, value_len);

  ctx.len += sizeof(tlv_t);
  return true;
}

#ifndef EMBEDDED_BUILD
STATIC_VISIBLE_FOR_TESTING void kv_print(void) {
  for (size_t off = 0; off < ctx.len; off += sizeof(tlv_t)) {
    tlv_t* tlv = (tlv_t*)(ctx.buf + off);
    LOGD("tag: %s, len: %d, value: %s", tlv->tag, tlv->len, tlv->value);
  }
}
#endif

kv_result_t kv_init(kv_api_t api) {
  if (!api.lock || !api.unlock) {
    return KV_ERR_INVALID;
  }

  ctx.lock = api.lock;
  ctx.unlock = api.unlock;

  if (!read_from_fs()) {
    return KV_ERR_IO;
  }

  return KV_ERR_NONE;
}

kv_result_t kv_set(const char* key, const void* value, uint8_t value_len) {
  kv_result_t result = KV_ERR_NONE;

  if (!key || !value || value_len == 0 || value_len > KV_MAX_VALUE_LEN) {
    return KV_ERR_INVALID;
  }

  ctx.lock();

  // Ensure the key is null-terminated and fits within the maximum key length.
  if (strnlen(key, KV_MAX_KEY_LEN) == KV_MAX_KEY_LEN) {
    result = KV_ERR_INVALID;
    goto out;
  }

  // Try to locate the TLV record. If it exists, update it.
  tlv_t* tlv = locate(key);
  if (tlv) {
    tlv->len = value_len;
    memcpy(tlv->value, value, value_len);
  } else {
    // Key not found. Create a new TLV record.
    if (!append(key, value, value_len)) {
      result = KV_ERR_APPEND;
      goto out;
    }
  }

  if (!write_to_fs()) {
    result = KV_ERR_IO;
    goto out;
  }

out:
  ctx.unlock();
  return result;
}

kv_result_t kv_get(const char* key, void* value, uint8_t* value_len) {
  kv_result_t result = KV_ERR_NONE;

  if (!key || !value || !value_len || *value_len > KV_MAX_VALUE_LEN) {
    return KV_ERR_INVALID;
  }

  ctx.lock();

  // Ensure the key is null-terminated and fits within the maximum key length.
  if (strnlen(key, KV_MAX_KEY_LEN) == KV_MAX_KEY_LEN) {
    result = KV_ERR_INVALID;
    goto out;
  }

  tlv_t* tlv = locate(key);
  if (!tlv) {
    result = KV_ERR_NOT_FOUND;
    goto out;
  }

  // Copy the value to the output buffer. If the output buffer is too small,
  // copy as much as possible and set the length to the actual value length.
  if (*value_len < tlv->len) {
    LOGW("Truncating value for key %s (%d < %d)", key, *value_len, tlv->len);
    // Provided buffer is too small, copy as much as possible
    memcpy(value, tlv->value, *value_len);
    result = KV_ERR_TRUNCATED;
    goto out;
  } else {
    memcpy(value, tlv->value, tlv->len);
    *value_len = tlv->len;
  }

out:
  ctx.unlock();
  return result;
}

kv_result_t kv_wipe_state(void) {
  if (fs_remove(KVSTORE_FILE_NAME) != 0) {
    return KV_ERR_IO;
  }
  memset(ctx.buf, 0, sizeof(ctx.buf));
  ctx.len = 0;
  return KV_ERR_NONE;
}
