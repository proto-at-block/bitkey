#include "log.h"
#include "secure_channel.h"
#include "secure_channel_cert.h"
#include "secure_channel_common.h"
#include "security_config.h"
#include "secutils.h"
#include "sysevent.h"

#include <criterion/criterion.h>

#include <stdarg.h>
#include <stdint.h>
#include <string.h>

security_config_t security_config = {
  .is_production = SECURE_FALSE,
  .biometrics_mac_key = NULL,
  .fwup_delta_patch_pubkey = NULL,
};

static const secure_channel_cert_desc_t local_identity_cert = {
  .id = SC_CERT_CORE_ID,
  .key_type = ALG_ECC_P256,
  .key_storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
  .cert_type = CERT_TYPE_PICOCERT,
};

const secure_channel_cert_desc_t* const secure_channel_product_certs[] = {
  &local_identity_cert,
  NULL,
};

static bool glitch_detected = false;
static uint8_t next_key_seed = 1;
static uint32_t sysevent_mask = 0;
static bool force_establish_failure = false;
static secure_channel_ctx_t* active_secure_channel_ctx = NULL;

static void detect_glitch(void) {
  glitch_detected = true;
}

static uint16_t secure_random(void) {
  return 1;
}

static uint32_t cpu_freq(void) {
  return 1000000;
}

static void init_runtime(void) {
  secutils_init((secutils_api_t){
    .detect_glitch = &detect_glitch,
    .secure_random = &secure_random,
    .cpu_freq = &cpu_freq,
  });
}

static void reset_test_state(void) {
  glitch_detected = false;
  next_key_seed = 1;
  sysevent_mask = 0;
  force_establish_failure = false;
  active_secure_channel_ctx = NULL;
}

static void fill_with_pattern(uint8_t* buffer, uint32_t size, uint8_t seed) {
  for (uint32_t i = 0; i < size; i++) {
    buffer[i] = (uint8_t)(seed + i);
  }
}

static uint8_t fold_bytes(const uint8_t* buffer, uint32_t size) {
  uint8_t acc = 0;
  for (uint32_t i = 0; i < size; i++) {
    acc ^= (uint8_t)(buffer[i] + i);
  }
  return acc;
}

static uint8_t cipher_test_key_byte(const secure_channel_ctx_t* secure_channel_ctx,
                                    secure_channel_cipher_op_t op) {
  return op == SECURE_CHANNEL_ENCRYPT ? secure_channel_ctx->send_key_buf[0]
                                      : secure_channel_ctx->recv_key_buf[0];
}

static void fill_test_mac(uint8_t key_acc, const uint8_t* data, uint32_t data_len,
                          const uint8_t* aad, uint32_t aad_len, const uint8_t* nonce,
                          uint8_t* mac) {
  const uint8_t data_acc = fold_bytes(data, data_len);
  const uint8_t aad_acc = fold_bytes(aad, aad_len);
  const uint8_t nonce_acc = fold_bytes(nonce, AES_GCM_IV_LENGTH);

  for (uint32_t i = 0; i < AES_GCM_TAG_LENGTH; i++) {
    mac[i] = key_acc ^ data_acc ^ aad_acc ^ nonce_acc ^ (uint8_t)data_len ^ (uint8_t)(i * 13u);
  }
}

static void transform_with_test_key(uint8_t key_acc, const uint8_t* data_in, uint8_t* data_out,
                                    uint32_t data_len, const uint8_t* aad, uint32_t aad_len,
                                    const uint8_t* nonce) {
  uint8_t mask = key_acc ^ fold_bytes(aad, aad_len) ^ fold_bytes(nonce, AES_GCM_IV_LENGTH);
  for (uint32_t i = 0; i < data_len; i++) {
    data_out[i] = data_in[i] ^ mask ^ (uint8_t)i;
  }
}

static secure_bool_t encrypt_for_current_receive_key(const uint8_t* plaintext, uint8_t* ciphertext,
                                                     uint32_t data_len, const uint8_t* aad,
                                                     uint32_t aad_len, uint8_t* nonce,
                                                     uint8_t* mac) {
  if (active_secure_channel_ctx == NULL || plaintext == NULL || ciphertext == NULL ||
      nonce == NULL || mac == NULL) {
    return SECURE_FALSE;
  }

  const uint8_t key_acc = cipher_test_key_byte(active_secure_channel_ctx, SECURE_CHANNEL_DECRYPT);
  fill_with_pattern(nonce, AES_GCM_IV_LENGTH, (uint8_t)(key_acc + data_len));
  transform_with_test_key(key_acc, plaintext, ciphertext, data_len, aad, aad_len, nonce);
  fill_test_mac(key_acc, ciphertext, data_len, aad, aad_len, nonce, mac);
  return SECURE_TRUE;
}

static secure_channel_err_t establish_and_confirm_session(uint8_t* peer_pubkey,
                                                          uint32_t peer_pubkey_len) {
  uint8_t exchange_sig[ECC_SIG_SIZE] = {0};
  uint8_t confirmation_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN] = {0};

  secure_channel_err_t ret = secure_uart_channel_establish(
    peer_pubkey, peer_pubkey_len, NULL, NULL, exchange_sig, sizeof(exchange_sig), confirmation_tag);
  if (ret != SECURE_CHANNEL_OK) {
    return ret;
  }

  return secure_uart_channel_confirm_session(confirmation_tag, exchange_sig, sizeof(exchange_sig));
}

secure_bool_t security_config_is_production(void) {
  return security_config.is_production;
}

void _log(log_level_t UNUSED(level), const char* UNUSED(colour), const char* UNUSED(file),
          int UNUSED(line), const char* UNUSED(format), ...) {}

void rtos_mutex_create(rtos_mutex_t* mutex) {
  if (mutex != NULL) {
    mutex->handle = (SemaphoreHandle_t)mutex;
  }
}

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(mutex)) {
  return true;
}

bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(mutex)) {
  return true;
}

bool generate_key(key_handle_t* key) {
  if (key == NULL || key->storage_type != KEY_STORAGE_EXTERNAL_PLAINTEXT ||
      key->key.bytes == NULL) {
    return false;
  }

  memset(key->key.bytes, next_key_seed, key->key.size);
  next_key_seed++;
  return true;
}

bool export_pubkey(key_handle_t* key_in, key_handle_t* key_out) {
  if (key_in == NULL || key_out == NULL || key_in->key.bytes == NULL ||
      key_out->key.bytes == NULL) {
    return false;
  }

  for (uint32_t i = 0; i < key_out->key.size; i++) {
    key_out->key.bytes[i] = (uint8_t)(key_in->key.bytes[i % key_in->key.size] ^ 0x5a);
  }
  return true;
}

bool crypto_hash(const uint8_t* message, uint32_t message_size, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t UNUSED(alg)) {
  if (digest == NULL) {
    return false;
  }

  uint8_t seed = (message != NULL && message_size > 0) ? message[0] : 0;
  fill_with_pattern(digest, digest_size, seed);
  return true;
}

secure_channel_err_t secure_channel_establish_impl(secure_channel_ctx_t* secure_channel_ctx,
                                                   uint8_t* pk_host, uint32_t pk_host_len,
                                                   key_handle_t* sk_device, key_handle_t* pk_device,
                                                   uint8_t* UNUSED(exchange_sig),
                                                   uint32_t UNUSED(exchange_sig_len)) {
  if (secure_channel_ctx == NULL || pk_host == NULL || sk_device == NULL || pk_device == NULL ||
      pk_host_len != EC_PUBKEY_SIZE_X25519) {
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  if (force_establish_failure) {
    secure_channel_ctx->established = false;
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  active_secure_channel_ctx = secure_channel_ctx;

  const uint8_t send_key_seed = (uint8_t)(sk_device->key.bytes[0] ^ (uint8_t)(pk_host[0] + 0x11u));
  const uint8_t recv_key_seed =
    (uint8_t)((uint8_t)(sk_device->key.bytes[0] * 3u) ^ (uint8_t)(pk_host[0] + 0x22u));
  const uint8_t conf_key_seed = (uint8_t)(send_key_seed ^ recv_key_seed ^ 0x5cu);

  memset(secure_channel_ctx->send_key_buf, send_key_seed, sizeof(secure_channel_ctx->send_key_buf));
  memset(secure_channel_ctx->recv_key_buf, recv_key_seed, sizeof(secure_channel_ctx->recv_key_buf));
  memset(secure_channel_ctx->conf_key_buf, conf_key_seed, sizeof(secure_channel_ctx->conf_key_buf));
  secure_channel_ctx->established = true;
  return SECURE_CHANNEL_OK;
}

secure_channel_err_t secure_channel_compute_confirmation(secure_channel_type_t UNUSED(channel_type),
                                                         key_handle_t* conf_key,
                                                         uint8_t* confirmation_tag) {
  if (conf_key == NULL || confirmation_tag == NULL) {
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  fill_with_pattern(confirmation_tag, SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN,
                    conf_key->key.bytes[0]);
  return SECURE_CHANNEL_OK;
}

secure_channel_err_t secure_channel_cipher(secure_channel_ctx_t* secure_channel_ctx,
                                           secure_channel_cipher_op_t op, const uint8_t* data_in,
                                           uint8_t* data_out, uint32_t data_len, const uint8_t* aad,
                                           uint32_t aad_len, uint8_t* nonce, uint8_t* mac) {
  if (secure_channel_ctx == NULL || data_in == NULL || data_out == NULL || nonce == NULL ||
      mac == NULL) {
    return SECURE_CHANNEL_CIPHER_FAILED;
  }

  const uint8_t key_acc = cipher_test_key_byte(secure_channel_ctx, op);
  switch (op) {
    case SECURE_CHANNEL_ENCRYPT:
      fill_with_pattern(nonce, AES_GCM_IV_LENGTH, (uint8_t)(key_acc + data_len));
      transform_with_test_key(key_acc, data_in, data_out, data_len, aad, aad_len, nonce);
      fill_test_mac(key_acc, data_out, data_len, aad, aad_len, nonce, mac);
      return SECURE_CHANNEL_OK;
    case SECURE_CHANNEL_DECRYPT: {
      uint8_t expected_mac[AES_GCM_TAG_LENGTH] = {0};
      fill_test_mac(key_acc, data_in, data_len, aad, aad_len, nonce, expected_mac);
      if (memcmp(expected_mac, mac, sizeof(expected_mac)) != 0) {
        return SECURE_CHANNEL_CIPHER_FAILED;
      }

      transform_with_test_key(key_acc, data_in, data_out, data_len, aad, aad_len, nonce);
      return SECURE_CHANNEL_OK;
    }
    default:
      return SECURE_CHANNEL_CIPHER_FAILED;
  }
}

bool secure_channel_sign_digest(const secure_channel_cert_desc_t* UNUSED(cert_desc),
                                const uint8_t* digest, const uint32_t digest_size,
                                uint8_t* signature, uint32_t signature_size) {
  if (digest == NULL || digest_size == 0 || signature == NULL || signature_size == 0) {
    return false;
  }

  fill_with_pattern(signature, signature_size, digest[0]);
  return true;
}

bool secure_channel_read_cert(const char* UNUSED(cert_id),
                              secure_channel_cert_data_t* UNUSED(cert_data_out)) {
  return false;
}

bool secure_channel_verify_digest(const secure_channel_cert_data_t* UNUSED(cert_data),
                                  const uint8_t* UNUSED(digest), uint32_t UNUSED(digest_size),
                                  const uint8_t* UNUSED(signature),
                                  uint32_t UNUSED(signature_size)) {
  return true;
}

void sysevent_set(const sysevent_t events) {
  sysevent_mask |= events;
}

Test(secure_channel_uart_test, accepts_only_supported_protocol_versions) {
  cr_assert_eq(secure_channel_protocol_version_supported(SECURE_CHANNEL_PROTOCOL_MIN_VERSION),
               true);
  cr_assert_eq(secure_channel_protocol_version_supported(SECURE_CHANNEL_PROTOCOL_VERSION), true);
  cr_assert_eq(secure_channel_protocol_version_supported(UINT32_MAX), true);

  if (SECURE_CHANNEL_PROTOCOL_MIN_VERSION > 0) {
    cr_assert_eq(secure_channel_protocol_version_supported(SECURE_CHANNEL_PROTOCOL_MIN_VERSION - 1),
                 false);
  }
}

Test(secure_channel_uart_test, rotates_keys_across_success_and_failure_paths) {
  reset_test_state();
  init_runtime();
  secure_uart_channel_init(SECURE_UART_CHANNEL_CORE);

  uint8_t peer_pubkey[EC_PUBKEY_SIZE_X25519];
  memset(peer_pubkey, 0xa5, sizeof(peer_pubkey));

  uint8_t split_handshake_pubkey[EC_PUBKEY_SIZE_X25519] = {0};
  uint32_t split_handshake_pubkey_len = sizeof(split_handshake_pubkey);
  cr_assert_eq(
    secure_uart_channel_public_key_init(split_handshake_pubkey, &split_handshake_pubkey_len),
    SECURE_CHANNEL_OK);
  cr_assert_eq(split_handshake_pubkey_len, EC_PUBKEY_SIZE_X25519);

  uint8_t first_session_pubkey[EC_PUBKEY_SIZE_X25519] = {0};
  uint32_t first_session_pubkey_len = sizeof(first_session_pubkey);
  uint8_t first_exchange_sig[ECC_SIG_SIZE] = {0};
  uint8_t first_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN] = {0};
  cr_assert_eq(secure_uart_channel_establish(peer_pubkey, sizeof(peer_pubkey), first_session_pubkey,
                                             &first_session_pubkey_len, first_exchange_sig,
                                             sizeof(first_exchange_sig), first_tag),
               SECURE_CHANNEL_OK);
  cr_assert_eq(first_session_pubkey_len, EC_PUBKEY_SIZE_X25519);
  cr_assert_eq(memcmp(split_handshake_pubkey, first_session_pubkey, sizeof(split_handshake_pubkey)),
               0, "public_key_init() and the matching establish() must use the same keypair");

  uint8_t second_session_pubkey[EC_PUBKEY_SIZE_X25519] = {0};
  uint32_t second_session_pubkey_len = sizeof(second_session_pubkey);
  uint8_t second_exchange_sig[ECC_SIG_SIZE] = {0};
  uint8_t second_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN] = {0};
  cr_assert_eq(
    secure_uart_channel_establish(peer_pubkey, sizeof(peer_pubkey), second_session_pubkey,
                                  &second_session_pubkey_len, second_exchange_sig,
                                  sizeof(second_exchange_sig), second_tag),
    SECURE_CHANNEL_OK);
  cr_assert_eq(second_session_pubkey_len, EC_PUBKEY_SIZE_X25519);
  cr_assert_neq(memcmp(first_session_pubkey, second_session_pubkey, sizeof(first_session_pubkey)),
                0, "a second establish() after success must generate a fresh keypair");

  uint8_t failing_session_pubkey[EC_PUBKEY_SIZE_X25519] = {0};
  uint32_t failing_session_pubkey_len = sizeof(failing_session_pubkey);
  cr_assert_eq(
    secure_uart_channel_public_key_init(failing_session_pubkey, &failing_session_pubkey_len),
    SECURE_CHANNEL_OK);
  cr_assert_eq(failing_session_pubkey_len, EC_PUBKEY_SIZE_X25519);

  force_establish_failure = true;
  uint8_t failing_exchange_sig[ECC_SIG_SIZE] = {0};
  uint8_t failing_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN] = {0};
  cr_assert_eq(
    secure_uart_channel_establish(peer_pubkey, sizeof(peer_pubkey), NULL, NULL,
                                  failing_exchange_sig, sizeof(failing_exchange_sig), failing_tag),
    SECURE_CHANNEL_FAILED_TO_DERIVE_KEY);
  force_establish_failure = false;

  uint8_t recovered_pubkey[EC_PUBKEY_SIZE_X25519] = {0};
  uint32_t recovered_pubkey_len = sizeof(recovered_pubkey);
  cr_assert_eq(secure_uart_channel_public_key_init(recovered_pubkey, &recovered_pubkey_len),
               SECURE_CHANNEL_OK);
  cr_assert_eq(recovered_pubkey_len, EC_PUBKEY_SIZE_X25519);
  cr_assert_neq(memcmp(failing_session_pubkey, recovered_pubkey, sizeof(failing_session_pubkey)), 0,
                "a failed establish() must clear cached keys before the next attempt");

  cr_assert(glitch_detected == false);
  cr_assert_eq(sysevent_mask, 0);
}

Test(secure_channel_uart_test, rejects_ciphertext_replayed_after_session_reestablish) {
  reset_test_state();
  init_runtime();
  secure_uart_channel_init(SECURE_UART_CHANNEL_CORE);

  uint8_t peer_pubkey[EC_PUBKEY_SIZE_X25519];
  memset(peer_pubkey, 0xa5, sizeof(peer_pubkey));

  cr_assert_eq(establish_and_confirm_session(peer_pubkey, sizeof(peer_pubkey)), SECURE_CHANNEL_OK);
  cr_assert_eq(secure_uart_channel_confirmed(), true);

  uint8_t plaintext[] = {0x10, 0x21, 0x32, 0x43, 0x54};
  uint8_t ciphertext[sizeof(plaintext)] = {0};
  uint8_t decrypted[sizeof(plaintext)] = {0};
  uint8_t replay_plaintext[sizeof(plaintext)] = {0};
  uint8_t aad[] = {0x44, 0x55, 0x66, 0x77, 0x01, 0x00, 0x00, 0x00};
  uint8_t nonce[AES_GCM_IV_LENGTH] = {0};
  uint8_t mac[AES_GCM_TAG_LENGTH] = {0};

  cr_assert_eq(encrypt_for_current_receive_key(plaintext, ciphertext, sizeof(plaintext), aad,
                                               sizeof(aad), nonce, mac),
               SECURE_TRUE);
  cr_assert_eq(secure_uart_channel_decrypt(ciphertext, decrypted, sizeof(ciphertext), aad,
                                           sizeof(aad), nonce, mac),
               SECURE_TRUE);
  cr_assert_eq(memcmp(plaintext, decrypted, sizeof(plaintext)), 0,
               "ciphertext must authenticate under the session that created it");

  cr_assert_eq(establish_and_confirm_session(peer_pubkey, sizeof(peer_pubkey)), SECURE_CHANNEL_OK);
  cr_assert_eq(secure_uart_channel_confirmed(), true);

  cr_assert_eq(secure_uart_channel_decrypt(ciphertext, replay_plaintext, sizeof(ciphertext), aad,
                                           sizeof(aad), nonce, mac),
               SECURE_FALSE,
               "ciphertext from a prior session must fail authentication after re-establish");
}
