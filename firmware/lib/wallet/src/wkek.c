#include "aes.h"
#include "attributes.h"
#include "filesystem.h"
#include "rtos.h"
#include "secure_rng.h"
#include "wkek_impl.h"

STATIC_VISIBLE_FOR_TESTING uint8_t
  encrypted_wkek_buffer[AES_256_LENGTH_BYTES + SE_WRAPPED_KEY_OVERHEAD];
STATIC_VISIBLE_FOR_TESTING key_handle_t wkek = {
  .alg = ALG_AES_256,
  .storage_type = KEY_STORAGE_EXTERNAL_WRAPPED,
  .key.bytes = &encrypted_wkek_buffer[0],
  .key.size = sizeof(encrypted_wkek_buffer),
  .acl = SE_KEY_FLAG_NON_EXPORTABLE,
};
static rtos_mutex_t wkek_lock;

static bool wkek_generate_and_store(void) {
  if (!generate_key(&wkek)) {
    return false;
  }
  return fs_util_write_global(WKEK_PATH, wkek.key.bytes, wkek.key.size);
}

static bool wkek_load(void) {
  static bool wkek_loaded = false;

  rtos_mutex_lock(&wkek_lock);

  if (!wkek_loaded && fs_util_read_global(WKEK_PATH, wkek.key.bytes, wkek.key.size)) {
    wkek_loaded = true;
  }

  rtos_mutex_unlock(&wkek_lock);
  return wkek_loaded;
}

void wkek_init() {
  rtos_mutex_create(&wkek_lock);
}

bool wkek_exists(void) {
  return fs_file_exists(WKEK_PATH);
}

int wkek_remove_files() {
  return fs_remove(WKEK_PATH);
}

bool wkek_lazy_init() {
  if (!wkek_exists() && !wkek_generate_and_store()) {
    return false;
  }

  if (!wkek_load()) {
    return false;
  }

  return true;
}

bool wkek_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t size,
                  uint8_t iv_out[AES_GCM_IV_LENGTH], uint8_t tag_out[AES_GCM_TAG_LENGTH]) {
  if (!wkek_lazy_init()) {
    return false;
  }
  if (!crypto_random(iv_out, AES_GCM_IV_LENGTH)) {
    return false;
  }
  return aes_gcm_encrypt(plaintext, ciphertext, size, iv_out, tag_out, NULL, 0, &wkek);
}

bool wkek_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t size,
                  uint8_t iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH]) {
  if (!wkek_lazy_init()) {
    return false;
  }

  return aes_gcm_decrypt(ciphertext, plaintext, size, iv, tag, NULL, 0, &wkek);
}
