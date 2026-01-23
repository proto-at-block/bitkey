#include "key_management.h"

#include "assert.h"
#include "wstring.h"

void zeroize_key(key_handle_t* const key) {
  ASSERT(key);
  if (key->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT ||
      key->storage_type == KEY_STORAGE_EXTERNAL_WRAPPED) {
    memzero(key->key.bytes, key->key.size);
  }
}
