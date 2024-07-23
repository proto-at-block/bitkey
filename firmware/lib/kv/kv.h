#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Simple key-value store, backed by TLV records in a file. The TLV records have a fixed-size tag
// and maximum length.
// The intended use is to store small amounts of data that are read and written infrequently, and
// that otherwise would clutter up the filesystem.

typedef enum {
  KV_ERR_NONE = 0,
  KV_ERR_NOT_FOUND = 1,
  KV_ERR_INVALID = 2,
  KV_ERR_IO = 3,
  KV_ERR_APPEND = 4,
  KV_ERR_TRUNCATED = 5,
} kv_result_t;

// 11 bytes for the key, 1 byte for the length, and 116 bytes for the value.
// Key IS null-terminated, so the maximum key length is 10 characters.
#define KV_MAX_KEY_LEN   (11)
#define KV_LEN_LEN       (1)
#define KV_MAX_VALUE_LEN (52)

#define KV_MAX_FILE_SIZE (4096)
#define KV_MAX_ENTRIES   (KV_MAX_FILE_SIZE / (KV_MAX_KEY_LEN + KV_LEN_LEN + KV_MAX_VALUE_LEN))

_Static_assert(KV_MAX_ENTRIES == 64, "KV_MAX_ENTRIES must be 64");

typedef bool (*kv_lock_t)(void);
typedef bool (*kv_unlock_t)(void);
typedef struct {
  kv_lock_t lock;
  kv_unlock_t unlock;
} kv_api_t;

// Load the key-value store from the filesystem.
kv_result_t kv_init(kv_api_t api);

// Set the value of a key. If the key already exists, it will be overwritten.
// The key must be null-terminated and have a length less than or equal to KV_MAX_KEY_LEN.
kv_result_t kv_set(const char* key, const void* value, uint8_t value_len);

// Get the value of a key. If the key does not exist, KV_ERR_NOT_FOUND will be returned.
// The key must be null-terminated and have a length less than or equal to KV_MAX_KEY_LEN.
// The size of the value is written to value_len.
// If the buffer pointed to by value is too small, the value will be truncated and KV_ERR_TRUNCATED
// will be returned.
kv_result_t kv_get(const char* key, void* value, uint8_t* value_len);
