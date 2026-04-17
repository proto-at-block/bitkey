#include "assert.h"
#include "attributes.h"
#include "ecc.h"
#include "hash.h"
#include "key_management.h"
#include "log.h"
#include "picocert.h"
#include "rtos.h"
#include "secure_channel_cert.h"
#include "secure_channel_cert_impl.h"
#include "secutils.h"
#include "wstring.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

static secure_channel_cert_priv_t secure_channel_cert_priv SHARED_TASK_BSS;

// Migrate from old directory-based layout to flat files, freeing 2 blocks (16 KB).
// Uses copy-then-delete to preserve key material so pinned peer certs remain valid.
// If any copy fails, the old directory is left intact — path fallback in read/exists
// functions ensures files are still found, and migration retries on next boot.
SYSCALL static void secure_channel_cert_migrate_from_directory(void) {
  fs_filetype_t dir_type;

  RTOS_THREAD_WITH_PRIVILEGE({ dir_type = fs_get_filetype(SC_CERT_OLD_DIRECTORY); });

  if (dir_type != FS_FILE_TYPE_DIR) {
    return;
  }

  LOGI("SC migrate");
  static const struct {
    const char* old_path;
    const char* new_path;
  } sc_migrate[] = {
    {SC_CERT_OLD_DIRECTORY "/w3_core_id.cert", "sc-w3_core_id.cert"},
    {SC_CERT_OLD_DIRECTORY "/w3_core_id.key", "sc-w3_core_id.key"},
    {SC_CERT_OLD_DIRECTORY "/w3_uxc_id.cert", "sc-w3_uxc_id.cert"},
    {SC_CERT_OLD_DIRECTORY "/w3_uxc_id.key", "sc-w3_uxc_id.key"},
  };

  // Copy each file to the new flat path. Track success so we only delete
  // the old directory if everything copied successfully.
  bool all_ok = true;
  uint8_t buf[256];
  RTOS_THREAD_WITH_PRIVILEGE({
    for (size_t i = 0; i < sizeof(sc_migrate) / sizeof(sc_migrate[0]); i++) {
      if (!fs_file_exists(sc_migrate[i].old_path)) {
        continue;
      }

      const int filesize = fs_get_filesize(sc_migrate[i].old_path);
      if (filesize <= 0 || (size_t)filesize > sizeof(buf)) {
        LOGE("Mig skip %d: %s", filesize, sc_migrate[i].old_path);
        all_ok = false;
        continue;
      }

      if (!fs_util_read_global((char*)sc_migrate[i].old_path, buf, (uint32_t)filesize)) {
        LOGE("Migrate read fail: %s", sc_migrate[i].old_path);
        all_ok = false;
        continue;
      }

      if (!fs_util_write_global((char*)sc_migrate[i].new_path, buf, (uint32_t)filesize)) {
        LOGE("Migrate write fail: %s", sc_migrate[i].new_path);
        all_ok = false;
        continue;
      }
    }
  });

  memzero(buf, sizeof(buf));

  if (!all_ok) {
    LOGW("Mig incomplete");
    return;
  }

  // All copies succeeded — remove old files and directory
  for (size_t i = 0; i < sizeof(sc_migrate) / sizeof(sc_migrate[0]); i++) {
    RTOS_THREAD_WITH_PRIVILEGE({
      if (fs_file_exists(sc_migrate[i].old_path)) {
        fs_remove(sc_migrate[i].old_path);
      }
    });
  }

  RTOS_THREAD_WITH_PRIVILEGE({ fs_remove(SC_CERT_OLD_DIRECTORY); });
}

void secure_channel_cert_init(void) {
  const secure_channel_cert_desc_t* const* cert_descriptors = secure_channel_product_certs;
  ASSERT(cert_descriptors != NULL);

  secure_channel_cert_migrate_from_directory();

  // Initialize mutex only once (should not call init more than once)
  if (secure_channel_cert_priv.mutex.handle == NULL) {
    rtos_mutex_create(&secure_channel_cert_priv.mutex);
  }

  const secure_channel_cert_desc_t* const* desc_ptr = cert_descriptors;

  while (*desc_ptr != NULL) {
    const secure_channel_cert_desc_t* desc = *desc_ptr;
    // Generate new certificate and key material if it doesn't exist.

    // Note: If an error occurred during the first boot one cert/key may have been written, but not
    // the other. In this case, clear out the existing material and regenerate both cert and key.
    // This should only happen if an error occurred during the first boot.
    if (!secure_channel_cert_exists(desc->id) || !secure_channel_cert_key_exists(desc->id)) {
      secure_channel_cert_clear_cert_and_key_files(desc->id);
      ASSERT(secure_channel_cert_generate_certificate(desc));
    }
    desc_ptr++;
  }
}

bool secure_channel_read_cert(const char* cert_id, secure_channel_cert_data_t* cert_data_out) {
  ASSERT(cert_id != NULL);
  ASSERT(cert_data_out != NULL);

  rtos_mutex_lock(&secure_channel_cert_priv.mutex);

  // Load certificate from filesystem (implementation in secure_channel_cert_impl.c)
  if (!secure_channel_cert_load(cert_id, cert_data_out)) {
    LOGW("Cert load fail: %s", cert_id);
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return false;
  }

  // Verify self-signature
  if (!secure_channel_cert_verify_self_signed_picocert_signature(&cert_data_out->data.picocert)) {
    LOGE("SS cert fail");
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return false;
  }

  rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
  return true;
}

bool secure_channel_sign_digest(const secure_channel_cert_desc_t* cert_desc, const uint8_t* digest,
                                const uint32_t digest_size, uint8_t* signature,
                                uint32_t signature_size) {
  ASSERT(cert_desc != NULL);
  ASSERT(digest != NULL);
  ASSERT(signature != NULL);

  if (signature_size != ECC_SIG_SIZE) {
    LOGE("Sig sz %lu!=%d", signature_size, ECC_SIG_SIZE);
    return false;
  }

  if (digest_size != SHA256_DIGEST_SIZE) {
    LOGE("Dig sz %lu!=%d", digest_size, SHA256_DIGEST_SIZE);
    return false;
  }

  rtos_mutex_lock(&secure_channel_cert_priv.mutex);

  // 1) Load the private key from filesystem (plaintext or wrapped)
  const uint32_t key_buf_size = (cert_desc->key_storage_type == KEY_STORAGE_EXTERNAL_WRAPPED)
                                  ? SE_WRAPPED_ECC_P256_KEY_BUFFER_SIZE
                                  : ECC_PRIVKEY_SIZE;
  uint8_t key_buf[SE_WRAPPED_ECC_P256_KEY_BUFFER_SIZE] = {0};

  key_acl_t privkey_acl = SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY;
  if (cert_desc->key_storage_type == KEY_STORAGE_EXTERNAL_WRAPPED) {
    privkey_acl |=
      SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY | SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY;
  } else {
    privkey_acl |= SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY;
  }

  if (!secure_channel_cert_read_key(cert_desc->id, key_buf, key_buf_size)) {
    LOGE("Privkey load fail: %s", cert_desc->id);
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return false;
  }

  // 2) Create key handle for signing
  key_handle_t privkey = {
    .alg = cert_desc->key_type,
    .storage_type = cert_desc->key_storage_type,
    .key.bytes = key_buf,
    .key.size = key_buf_size,
    .acl = privkey_acl,
  };

  // 3) Sign the digest
  secure_bool_t result = crypto_ecc_sign_hash(&privkey, (uint8_t*)digest, digest_size, signature);

  // 4) Zeroize private key
  memzero(key_buf, sizeof(key_buf));

  if (result != SECURE_TRUE) {
    LOGE("Sign digest fail");
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return false;
  }

  rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
  return true;
}

static secure_channel_cert_err_t secure_channel_pin_picocert(
  const secure_channel_cert_data_t* cert_data) {
  ASSERT(cert_data != NULL);
  ASSERT(cert_data->type == CERT_TYPE_PICOCERT);

  const picocert_t* cert = &cert_data->data.picocert;

  // 1) Validate certificate subject is null-terminated
  size_t subject_len = strnlen(cert->subject, sizeof(cert->subject));
  if (subject_len == sizeof(cert->subject)) {
    LOGE("Cert subj NUL");
    return SECURE_CHANNEL_CERT_ERROR;
  }

  if (!secure_channel_cert_subject_is_valid(cert->subject)) {
    return SECURE_CHANNEL_CERT_ERROR;
  }

  // 2) Check if certificate already pinned
  if (secure_channel_cert_exists(cert->subject)) {
    LOGE("Cert already pinned: %s", cert->subject);
    return SECURE_CHANNEL_CERT_PINNED_ALREADY_EXISTS;
  }

  // 3) Verify self-signed certificate
  if (!secure_channel_cert_verify_self_signed_picocert_signature(&cert_data->data.picocert)) {
    LOGE("SS cert fail");
    return SECURE_CHANNEL_CERT_ERROR;
  }

  // 4) Store (pin) the certificate
  // Storing the full certificate can enable us to store full certificate chains in the future
  // for example, the SiLabs EFR32 identity certificate chain.
  // Additionally, storing the full certificate means we don't need to request/send them on every
  // boot when establishing a secure channel.
  if (!secure_channel_cert_write_cert(cert->subject, cert_data)) {
    LOGE("Cert store fail: %s", cert->subject);
    return SECURE_CHANNEL_CERT_ERROR;
  }

  return SECURE_CHANNEL_CERT_OK;
}

secure_channel_cert_err_t secure_channel_pin_cert(const secure_channel_cert_data_t* cert_data) {
  if (cert_data == NULL) {
    return SECURE_CHANNEL_CERT_ERROR;
  }

  if (cert_data->type == CERT_TYPE_UNSUPPORTED || cert_data->type >= CERT_TYPE_MAX_TYPES) {
    LOGE("Bad cert type: %d", cert_data->type);
    return SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE;
  }

  rtos_mutex_lock(&secure_channel_cert_priv.mutex);

  secure_channel_cert_err_t result;
  switch (cert_data->type) {
    case CERT_TYPE_PICOCERT:
      result = secure_channel_pin_picocert(cert_data);
      break;

    default:
      result = SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE;
      break;
  }

  rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
  return result;
}

secure_channel_cert_err_t secure_channel_matches_pinned_cert(
  const secure_channel_cert_data_t* cert_data) {
  ASSERT(cert_data != NULL);

  // Check certificate type
  if (cert_data->type == CERT_TYPE_UNSUPPORTED || cert_data->type >= CERT_TYPE_MAX_TYPES) {
    LOGE("Bad cert type: %d", cert_data->type);
    return SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE;
  }

  rtos_mutex_lock(&secure_channel_cert_priv.mutex);

  const picocert_t* cert = &cert_data->data.picocert;

  // Ensure cert->subject is null-terminated before using it as a string
  size_t subject_len = strnlen(cert->subject, sizeof(cert->subject));
  if (subject_len == sizeof(cert->subject)) {
    LOGE("Cert subj NUL");
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return SECURE_CHANNEL_CERT_ERROR;
  }

  if (!secure_channel_cert_subject_is_valid(cert->subject)) {
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return SECURE_CHANNEL_CERT_ERROR;
  }

  char subject_buf[sizeof(cert->subject)];
  memcpy(subject_buf, cert->subject, subject_len + 1);

  // 1) Extract subject to find pinned cert
  if (!secure_channel_cert_exists(subject_buf)) {
    LOGW("No pinned cert: %s", subject_buf);
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return SECURE_CHANNEL_CERT_PINNED_NOT_FOUND;
  }

  // 2) Load the pinned secure channel certificate
  secure_channel_cert_data_t pinned_cert = {0};
  if (!secure_channel_cert_load(subject_buf, &pinned_cert)) {
    LOGE("Pinned cert load fail: %s", subject_buf);
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return SECURE_CHANNEL_CERT_ERROR;
  }

  // 3) Compare the certificates (only active fields to avoid padding issues)
  if (pinned_cert.type != cert_data->type ||
      memcmp_s(&cert_data->data.picocert, &pinned_cert.data.picocert, sizeof(picocert_t)) != 0) {
    LOGE("SC cert mismatch");
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return SECURE_CHANNEL_CERT_PINNED_MISMATCH;
  }

  rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
  return SECURE_CHANNEL_CERT_OK;
}

bool secure_channel_verify_digest(const secure_channel_cert_data_t* cert_data,
                                  const uint8_t* digest, uint32_t digest_size,
                                  const uint8_t* signature, uint32_t signature_size) {
  ASSERT(cert_data != NULL);
  ASSERT(digest != NULL);
  ASSERT(signature != NULL);

  // 1) Check certificate type
  if (cert_data->type == CERT_TYPE_UNSUPPORTED || cert_data->type >= CERT_TYPE_MAX_TYPES) {
    LOGE("Bad cert type: %d", cert_data->type);
    return false;
  }

  if (digest_size != SHA256_DIGEST_SIZE) {
    LOGE("Dig sz %lu!=%d", digest_size, SHA256_DIGEST_SIZE);
    return false;
  }

  if (signature_size != ECC_SIG_SIZE) {
    LOGE("Sig sz %lu!=%d", signature_size, ECC_SIG_SIZE);
    return false;
  }

  // 2) Verify the signature using the certificate's public key
  // Picocert stores public key in SEC1 format: 0x04 || X || Y (65 bytes)
  // crypto_ecc_verify_hash expects just X || Y (64 bytes), so skip the 0x04 prefix
  if (cert_data->data.picocert.public_key[0] != ECC_PUBKEY_SEC1_UNCOMPRESSED_PREFIX) {
    LOGE("Bad pk pfx: %02x", cert_data->data.picocert.public_key[0]);
    return false;
  }
  key_handle_t pubkey_handle = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)&cert_data->data.picocert.public_key[1],  // Skip 0x04 prefix
    .key.size = ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED,                  // 64 bytes (X || Y)
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  secure_bool_t verified = crypto_ecc_verify_hash(&pubkey_handle, digest, digest_size, signature);

  if (verified != SECURE_TRUE) {
    LOGE("Sig verify fail");
    return false;
  }

  return true;
}

bool secure_channel_cert_handle_cmd_get(fwpb_cert_get_cmd* cmd, fwpb_cert_get_rsp* rsp) {
  ASSERT(cmd != NULL);
  ASSERT(rsp != NULL);

  if (cmd->kind != fwpb_cert_get_cmd_cert_type_DEVICE_SECURE_CHANNEL_CERT) {
    LOGE("Bad cert type: %d", cmd->kind);
    return false;
  }

  // The cert_data is a tagged union.
  // The size of this structure is based on the largest union member.
  secure_channel_cert_data_t cert_data = {0};
  size_t cert_size = sizeof(secure_channel_cert_data_t);
  if (cert_size > sizeof(rsp->cert.bytes)) {
    LOGE("Cert too big: %lu>%lu", (uint32_t)cert_size, (uint32_t)sizeof(rsp->cert.bytes));
    rsp->rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
    return false;
  }

  rtos_mutex_lock(&secure_channel_cert_priv.mutex);

  if (secure_channel_cert_load(cmd->cert_id, &cert_data)) {
    // Serialize the entire cert_data structure (includes type + cert)
    memcpy(rsp->cert.bytes, &cert_data, cert_size);
    rsp->cert.size = cert_size;
    rsp->rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_SUCCESS;
    rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
    return true;
  }

  LOGE("SC cert get fail: %s", cmd->cert_id);
  rsp->rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
  rtos_mutex_unlock(&secure_channel_cert_priv.mutex);
  return false;
}
