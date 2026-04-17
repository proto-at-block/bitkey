#include "secure_channel_cert_impl.h"

#include "assert.h"
#include "filesystem.h"
#include "hash.h"
#include "key_management.h"
#include "log.h"
#include "wstring.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

static bool secure_channel_cert_subject_char_is_valid(char c) {
  return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' ||
          c == '-');
}

bool secure_channel_cert_subject_is_valid(const char* subject) {
  ASSERT(subject != NULL);

  size_t subject_len = strnlen(subject, SC_CERT_ID_MAX_SIZE);
  if (subject_len == 0 || subject_len == SC_CERT_ID_MAX_SIZE) {
    LOGE("Bad cert subj");
    return false;
  }

  for (size_t i = 0; i < subject_len; i++) {
    if (!secure_channel_cert_subject_char_is_valid(subject[i])) {
      LOGE("Bad cert subj");
      return false;
    }
  }

  return true;
}

// Resolve cert path: check legacy /secure_channel/ directory first, fall back to new sc- flat path.
// Returns true if path_out contains a valid path (file may or may not exist at the new path).
static bool resolve_cert_path(const char* subject, char* path_out, size_t path_size) {
  if (!secure_channel_cert_subject_is_valid(subject)) {
    return false;
  }

  int rc = snprintf(path_out, path_size, SC_CERT_OLD_DIRECTORY "/%s.cert", subject);
  if (rc >= 0 && (size_t)rc < path_size && fs_file_exists(path_out)) {
    return true;
  }
  return secure_channel_cert_format_cert_path(subject, path_out, path_size);
}

// Resolve key path: check legacy /secure_channel/ directory first, fall back to new sc- flat path.
static bool resolve_key_path(const char* subject, char* path_out, size_t path_size) {
  if (!secure_channel_cert_subject_is_valid(subject)) {
    return false;
  }

  int rc = snprintf(path_out, path_size, SC_CERT_OLD_DIRECTORY "/%s.key", subject);
  if (rc >= 0 && (size_t)rc < path_size && fs_file_exists(path_out)) {
    return true;
  }
  return secure_channel_cert_format_key_path(subject, path_out, path_size);
}

bool secure_channel_cert_generate_certificate(const secure_channel_cert_desc_t* desc) {
  ASSERT(desc != NULL);

  // 1) Generate private key
  const uint32_t key_buf_size = (desc->key_storage_type == KEY_STORAGE_EXTERNAL_WRAPPED)
                                  ? SE_WRAPPED_ECC_P256_KEY_BUFFER_SIZE
                                  : ECC_PRIVKEY_SIZE;
  uint8_t key_buf[SE_WRAPPED_ECC_P256_KEY_BUFFER_SIZE] = {0};

  key_acl_t privkey_acl = SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY;
  if (desc->key_storage_type == KEY_STORAGE_EXTERNAL_WRAPPED) {
    privkey_acl |=
      SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY | SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY;
  } else {
    privkey_acl |= SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY;
  }
  key_handle_t key_handle = {
    .alg = desc->key_type,
    .storage_type = desc->key_storage_type,
    .key.bytes = key_buf,
    .key.size = key_buf_size,
    .acl = privkey_acl,
  };

  if (!generate_key(&key_handle)) {
    LOGE("Key gen fail: %s", desc->id);
    return false;
  }

  // 2) Derive public key from private key
  uint8_t pubkey_buf[ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED] = {0};
  key_handle_t pubkey_handle = {
    .alg = desc->key_type,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = pubkey_buf,
    .key.size = sizeof(pubkey_buf),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
  };

  if (!export_pubkey(&key_handle, &pubkey_handle)) {
    LOGE("Pubkey fail: %s", desc->id);
    return false;
  }

  // 3) Generate self-signed certificate directly into cert_data to avoid
  //    duplicating picocert_t on the stack (~216 bytes saved).
  secure_channel_cert_data_t cert_data;
  memset(&cert_data, 0, sizeof(cert_data));
  cert_data.type = CERT_TYPE_PICOCERT;
  if (!secure_channel_cert_format_picocert(&cert_data.data.picocert, &key_handle, &pubkey_handle,
                                           desc->id, strlen(desc->id), 0, UINT64_MAX)) {
    LOGE("Picocert fail: %s", desc->id);
    return false;
  }

  // 4) Store certificate to filesystem (by subject)
  if (!secure_channel_cert_write_cert(desc->id, &cert_data)) {
    LOGE("Cert store fail: %s", desc->id);
    return false;
  }

  // 5) Store key to filesystem
  if (!secure_channel_cert_write_key(desc->id, key_buf, key_buf_size)) {
    LOGE("Key store fail: %s", desc->id);
    return false;
  }

  // Zeroize key buffer - keys are reloaded from filesystem when needed for signing
  memzero(key_buf, sizeof(key_buf));
  return true;
}

bool secure_channel_cert_format_picocert(picocert_t* cert, key_handle_t* privkey,
                                         key_handle_t* pubkey, const char* subject,
                                         const size_t subject_size, uint64_t valid_from,
                                         uint64_t valid_to) {
  ASSERT(cert != NULL);
  ASSERT(privkey != NULL);
  ASSERT(pubkey != NULL);
  ASSERT(subject != NULL);

  // Zero out the certificate
  memset(cert, 0, sizeof(picocert_t));

  // Fill in basic fields
  cert->version = PICOCERT_CURRENT_VERSION;
  cert->curve = PICOCERT_P256;
  cert->hash = PICOCERT_SHA256;
  picocert_set_reserved(cert, 0);

  // Self-signed: issuer = subject
  size_t issuer_max = sizeof(cert->issuer) - 1;
  size_t issuer_len = (subject_size < issuer_max) ? subject_size : issuer_max;
  memcpy(cert->issuer, subject, issuer_len);
  cert->issuer[issuer_len] = '\0';

  size_t subject_max = sizeof(cert->subject) - 1;
  size_t subject_len = (subject_size < subject_max) ? subject_size : subject_max;
  memcpy(cert->subject, subject, subject_len);
  cert->subject[subject_len] = '\0';

  // Set validity period (stored as little-endian)
  picocert_set_valid_from(cert, valid_from);
  picocert_set_valid_to(cert, valid_to);

  // Embed public key in SEC1 uncompressed format (0x04 || X || Y)
  // Pubkey is 64 bytes (32-byte X, 32-byte Y), need to prepend prefix
  cert->public_key[0] = ECC_PUBKEY_SEC1_UNCOMPRESSED_PREFIX;

  if (pubkey->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT ||
      pubkey->storage_type == KEY_STORAGE_EXTERNAL_WRAPPED) {
    // Public key is in external buffer
    if (pubkey->key.size != ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED) {
      LOGE("Pubkey sz %lu!=%d", pubkey->key.size, ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED);
      return false;
    }
    memcpy(&cert->public_key[1], pubkey->key.bytes, ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED);
  } else {
    LOGE("Int key unsup");
    return false;
  }

  // Hash the signable bytes (everything before signature field)
  const uint32_t signable_size = offsetof(picocert_t, signature);
  uint8_t digest[SHA256_DIGEST_SIZE] = {0};

  bool hash_result =
    crypto_hash((const uint8_t*)cert, signable_size, digest, sizeof(digest), ALG_SHA256);
  if (!hash_result) {
    LOGE("Cert hash fail");
    return false;
  }

  // Sign the hash with private key
  secure_bool_t sign_result =
    crypto_ecc_sign_hash(privkey, digest, sizeof(digest), cert->signature);
  if (sign_result != SECURE_TRUE) {
    LOGE("Cert sign fail");
    return false;
  }

  return true;
}

bool secure_channel_cert_load(const char* subject, secure_channel_cert_data_t* cert_data_out) {
  ASSERT(subject != NULL);
  ASSERT(cert_data_out != NULL);

  char cert_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!resolve_cert_path(subject, cert_path, sizeof(cert_path))) {
    return false;
  }
  if (!fs_file_exists(cert_path)) {
    LOGW("Cert not found: %s", cert_path);
    return false;
  }

  // Load the full struct (type + union + padding) from filesystem
  if (!fs_util_read_global(cert_path, (uint8_t*)cert_data_out,
                           sizeof(secure_channel_cert_data_t))) {
    LOGE("Cert read fail: %s", cert_path);
    return false;
  }

  // Validate certificate type to detect corrupted or malicious files early
  if (cert_data_out->type == CERT_TYPE_UNSUPPORTED || cert_data_out->type >= CERT_TYPE_MAX_TYPES) {
    LOGE("Bad cert type %s: %d", cert_path, cert_data_out->type);
    return false;
  }
  return true;
}

bool secure_channel_cert_write_cert(const char* subject,
                                    const secure_channel_cert_data_t* cert_data) {
  ASSERT(subject != NULL);
  ASSERT(cert_data != NULL);

  char cert_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!secure_channel_cert_format_cert_path(subject, cert_path, sizeof(cert_path))) {
    return false;
  }
  if (!fs_util_write_global(cert_path, (uint8_t*)cert_data, sizeof(secure_channel_cert_data_t))) {
    LOGE("Cert write fail: %s", subject);
    return false;
  }
  return true;
}

bool secure_channel_cert_write_key(const char* subject, const uint8_t* key_buf,
                                   uint32_t key_buf_size) {
  ASSERT(subject != NULL);
  ASSERT(key_buf != NULL);
  ASSERT(key_buf_size > 0);

  char key_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!secure_channel_cert_format_key_path(subject, key_path, sizeof(key_path))) {
    return false;
  }
  if (!fs_util_write_global(key_path, (uint8_t*)key_buf, key_buf_size)) {
    LOGE("Key write fail: %s", subject);
    return false;
  }
  return true;
}

bool secure_channel_cert_format_cert_path(const char* subject, char* path_out, size_t path_size) {
  ASSERT(subject != NULL);
  ASSERT(path_out != NULL);

  if (!secure_channel_cert_subject_is_valid(subject)) {
    return false;
  }

  memset(path_out, 0, path_size);
  int rc = snprintf(path_out, path_size, "%s%s.cert", SC_CERT_FILE_PREFIX, subject);
  if (rc < 0 || (size_t)rc >= path_size) {
    LOGE("Cert path ovf: %s", subject);
    return false;
  }
  return true;
}

bool secure_channel_cert_format_key_path(const char* subject, char* path_out, size_t path_size) {
  ASSERT(subject != NULL);
  ASSERT(path_out != NULL);

  if (!secure_channel_cert_subject_is_valid(subject)) {
    return false;
  }

  memset(path_out, 0, path_size);
  int rc = snprintf(path_out, path_size, "%s%s.key", SC_CERT_FILE_PREFIX, subject);
  if (rc < 0 || (size_t)rc >= path_size) {
    LOGE("Key path ovf: %s", subject);
    return false;
  }
  return true;
}

bool secure_channel_cert_read_key(const char* subject, uint8_t* key_buf_out,
                                  uint32_t key_buf_size) {
  ASSERT(subject != NULL);
  ASSERT(key_buf_out != NULL);
  ASSERT(key_buf_size > 0);

  memset(key_buf_out, 0, key_buf_size);

  char key_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!resolve_key_path(subject, key_path, sizeof(key_path))) {
    return false;
  }
  if (!fs_file_exists(key_path)) {
    LOGE("Key missing: %s", key_path);
    return false;
  }

  if (!fs_util_read_global(key_path, key_buf_out, key_buf_size)) {
    LOGE("Key read fail: %s", key_path);
    return false;
  }
  return true;
}

bool secure_channel_cert_exists(const char* subject) {
  ASSERT(subject != NULL);

  char cert_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!resolve_cert_path(subject, cert_path, sizeof(cert_path))) {
    return false;
  }
  return fs_file_exists(cert_path);
}

bool secure_channel_cert_key_exists(const char* subject) {
  ASSERT(subject != NULL);

  char key_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!resolve_key_path(subject, key_path, sizeof(key_path))) {
    return false;
  }
  return fs_file_exists(key_path);
}

bool secure_channel_cert_clear_cert_and_key_files(const char* subject) {
  ASSERT(subject != NULL);

  char cert_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!secure_channel_cert_format_cert_path(subject, cert_path, sizeof(cert_path))) {
    return false;
  }
  if (fs_file_exists(cert_path)) {
    if (fs_remove(cert_path) < 0) {
      LOGE("Cert rm fail: %s", cert_path);
      return false;
    }
  }

  char key_path[FS_FILE_NAME_MAX_LEN] = {0};
  if (!secure_channel_cert_format_key_path(subject, key_path, sizeof(key_path))) {
    return false;
  }
  if (fs_file_exists(key_path)) {
    if (fs_remove(key_path) < 0) {
      LOGE("Key rm fail: %s", key_path);
      return false;
    }
  }
  return true;
}

bool secure_channel_cert_verify_self_signed_picocert_signature(const picocert_t* cert) {
  ASSERT(cert != NULL);

  const uint32_t signable_size = offsetof(picocert_t, signature);
  uint8_t digest[SHA256_DIGEST_SIZE] = {0};

  bool hash_ok =
    crypto_hash((const uint8_t*)cert, signable_size, digest, sizeof(digest), ALG_SHA256);
  if (!hash_ok) {
    LOGE("Cert verify hash fail");
    return false;
  }
  if (cert->public_key[0] != ECC_PUBKEY_SEC1_UNCOMPRESSED_PREFIX) {
    LOGE("Bad pk pfx: %02x", cert->public_key[0]);
    return false;
  }
  key_handle_t pubkey_handle = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)&cert->public_key[1],
    .key.size = ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };
  secure_bool_t verified =
    crypto_ecc_verify_hash(&pubkey_handle, digest, sizeof(digest), cert->signature);
  return (verified == SECURE_TRUE);
}
