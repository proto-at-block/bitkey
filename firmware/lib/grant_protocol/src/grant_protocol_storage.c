#include "assert.h"
#include "ecc.h"
#include "filesystem.h"
#include "grant_protocol.h"
#include "grant_protocol_storage_impl.h"
#include "log.h"

#include <string.h>

static grant_protocol_result_t write_to_filesystem(const char* path, const uint8_t* data,
                                                   uint32_t size) {
  ASSERT(path && data && size > 0);

  grant_protocol_result_t result = GRANT_RESULT_ERROR_STORAGE;
  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, path, FS_O_RDWR | FS_O_CREAT | FS_O_TRUNC);
  if (ret != 0) {
    LOGE("Grant open W: %d", ret);
    return result;
  }

  ret = fs_file_write(file, data, size);
  if (ret != (int32_t)size) {
    LOGE("Grant wr: %d", ret);
    goto out;
  }

  result = GRANT_RESULT_OK;

out:
  if (file) {
    (void)fs_close_global(file);
  }
  return result;
}

static grant_protocol_result_t read_from_filesystem(const char* path, uint8_t* data,
                                                    uint32_t size) {
  ASSERT(path && data && size > 0);

  grant_protocol_result_t result = GRANT_RESULT_ERROR_STORAGE;

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, path, FS_O_RDONLY);
  if (ret != 0) {
    LOGE("Grant open R: %d", ret);
    return result;
  }

  ret = fs_file_read(file, data, size);
  if (ret != (int32_t)size) {
    LOGE("Grant rd: %d", ret);
    goto out;
  }

  result = GRANT_RESULT_OK;

out:
  if (file) {
    (void)fs_close_global(file);
  }
  return result;
}

grant_protocol_result_t grant_storage_read_request(grant_request_t* request) {
  ASSERT(request);
  return read_from_filesystem(GRANT_REQUEST_PATH, (uint8_t*)request, sizeof(grant_request_t));
}

grant_protocol_result_t grant_storage_write_request(const grant_request_t* request) {
  ASSERT(request);
  return write_to_filesystem(GRANT_REQUEST_PATH, (const uint8_t*)request, sizeof(grant_request_t));
}

grant_protocol_result_t grant_storage_delete_request(void) {
  if (fs_file_exists(GRANT_REQUEST_PATH)) {
    if (fs_remove(GRANT_REQUEST_PATH) != 0) {
      LOGE("Grant req delete fail");
      return GRANT_RESULT_ERROR_STORAGE;
    }
  }
  return GRANT_RESULT_OK;
}

// App auth pubkey storage functions
bool grant_storage_read_app_auth_pubkey(uint8_t* pubkey) {
  ASSERT(pubkey);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, APP_AUTH_PUBKEY_PATH, FS_O_RDONLY);
  if (ret != 0) {
    return false;
  }

  ret = fs_file_read(file, pubkey, 33);  // Compressed secp256k1 pubkey is 33 bytes
  fs_close_global(file);

  if (ret != 33) {
    LOGE("App pk rd: %d", ret);
    return false;
  }

  return true;
}

bool grant_storage_write_app_auth_pubkey(const uint8_t* pubkey) {
  ASSERT(pubkey);

  fs_file_t* file = NULL;
  int ret = fs_open_global(&file, APP_AUTH_PUBKEY_PATH, FS_O_RDWR | FS_O_CREAT | FS_O_TRUNC);
  if (ret != 0) {
    LOGE("App pk open W: %d", ret);
    return false;
  }

  ret = fs_file_write(file, pubkey, 33);  // Compressed secp256k1 pubkey is 33 bytes
  fs_close_global(file);

  if (ret != 33) {
    LOGE("App pk wr: %d", ret);
    return false;
  }

  return true;
}

bool grant_storage_app_auth_pubkey_exists(void) {
  return fs_file_exists(APP_AUTH_PUBKEY_PATH);
}

bool grant_storage_delete_app_auth_pubkey(void) {
  if (fs_file_exists(APP_AUTH_PUBKEY_PATH)) {
    if (fs_remove(APP_AUTH_PUBKEY_PATH) != 0) {
      LOGE("App pubkey delete fail");
      return false;
    }
  }
  return true;
}

grant_protocol_result_t grant_protocol_provision_app_auth_pubkey(const uint8_t* new_pubkey) {
  ASSERT(new_pubkey);

  // Validate that the pubkey is a valid compressed secp256k1 public key on the curve
  if (!crypto_ecc_secp256k1_pubkey_verify(new_pubkey)) {
    LOGE("Bad secp256k1 pubkey");
    return GRANT_RESULT_ERROR_INVALID_ARGUMENT;
  }

  // Write the new app auth pubkey (no signature validation required)
  if (!grant_storage_write_app_auth_pubkey(new_pubkey)) {
    LOGE("App pk store err");
    return GRANT_RESULT_ERROR_STORAGE;
  }

  return GRANT_RESULT_OK;
}
