#include "key_manager_task.h"

#include "attestation.h"
#include "attributes.h"
#include "auth.h"
#include "bip32.h"
#include "bitlog.h"
#include "ecc.h"
#include "filesystem.h"
#include "grant_protocol.h"
#include "hash.h"
#include "ipc.h"
#include "key_management.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "lost_app_recovery_impl.h"
#include "mcu_reset.h"
#include "memfault.h"
#include "mempool.h"
#include "onboarding.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "policy.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "secure_channel.h"
#include "secutils.h"
#include "seed.h"
#include "sysevent.h"
#include "ui_messaging.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wkek.h"
#include "wstring.h"

#include <inttypes.h>

#define ATTESTATION_DOMAIN_PREFIX      "HWV1"
#define ATTESTATION_DOMAIN_PREFIX_SIZE (sizeof(ATTESTATION_DOMAIN_PREFIX) - 1)

typedef enum {
  KEY_ATTEST_ERR_SEED_DERIVE = 1,
  KEY_ATTEST_ERR_PRIV_TO_PUB = 2,
  KEY_ATTEST_ERR_SIGN = 3,
} key_attest_err_t;

// Spending keys are derived under BIP84 (purpose=84'). Auth keys and other
// internal derivations use different purpose indexes, and we deliberately do
// not attest those: it would waste a signing op per derive and would couple the
// hard-abort-on-attestation-failure behavior into unrelated flows.
static bool is_bip84_spend_path(const derivation_path_t* path) {
  return path->num_indices >= 1 && path->indices[0] == (BIP84_PURPOSE | BIP32_HARDENED_BIT);
}

static bool populate_key_attestation(const extended_key_t* key_pub, fwpb_derive_rsp* rsp) {
  uint8_t attestation_payload[ATTESTATION_DOMAIN_PREFIX_SIZE + BIP32_SEC1_KEY_SIZE] = {0};
  memcpy(attestation_payload, ATTESTATION_DOMAIN_PREFIX, ATTESTATION_DOMAIN_PREFIX_SIZE);
  attestation_payload[ATTESTATION_DOMAIN_PREFIX_SIZE] = key_pub->prefix;
  memcpy(&attestation_payload[ATTESTATION_DOMAIN_PREFIX_SIZE + 1], key_pub->key, BIP32_KEY_SIZE);

  if (!crypto_sign_with_device_identity(attestation_payload, sizeof(attestation_payload),
                                        rsp->attestation_signature.bytes,
                                        sizeof(rsp->attestation_signature.bytes))) {
    BITLOG_EVENT(wallet_generate_key_attestation_err, KEY_ATTEST_ERR_SIGN);
    return false;
  }
  rsp->attestation_signature.size = ECC_SIG_SIZE;

  return true;
}

static void clear_key_attestation(fwpb_derive_rsp* rsp) {
  rsp->attestation_signature.size = 0;
  memzero(rsp->attestation_signature.bytes, sizeof(rsp->attestation_signature.bytes));
}

static struct {
  rtos_queue_t* queue;
  uint32_t send_timeout_ms;
  mempool_t* mempool;
  rtos_thread_t* crypto_thread;
} key_manager_priv = {
  .queue = NULL,
  .send_timeout_ms = 1000,
  .crypto_thread = NULL,
};

bool key_manager_derive_and_serialize_pubkey(derivation_path_t path, version_bytes_t version,
                                             uint8_t bare_key_out[BIP32_SERIALIZED_EXT_KEY_SIZE],
                                             fingerprint_t* master_fp_out,
                                             extended_key_t* derived_pubkey_out) {
  extended_key_t key_priv __attribute__((__cleanup__(bip32_zero_key)));
  fingerprint_t master_fp;
  fingerprint_t parent_fp;

  if (seed_derive_bip32(path, &key_priv, &master_fp, &parent_fp) != SEED_RES_OK) {
    LOGE("derive_pubkey: seed derive fail");
    return false;
  }

  extended_key_t key_pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&key_priv, &key_pub)) {
    LOGE("derive_pubkey: priv_to_pub fail");
    return false;
  }

  uint32_t child_number = path.num_indices > 0 ? path.indices[path.num_indices - 1] : 0;

  if (!bip32_serialize_ext_key(&key_pub, NULL, parent_fp.bytes, version, child_number,
                               path.num_indices, bare_key_out, BIP32_SERIALIZED_EXT_KEY_SIZE)) {
    LOGE("derive_pubkey: serialize fail");
    return false;
  }

  if (derived_pubkey_out) {
    memcpy(derived_pubkey_out, &key_pub, sizeof(extended_key_t));
  }

  if (master_fp_out) {
    memcpy(master_fp_out, &master_fp, sizeof(fingerprint_t));
  }

  return true;
}

bool key_manager_derive_key_descriptor(derivation_path_t path, version_bytes_t version,
                                       fwpb_key_descriptor* descriptor_out,
                                       extended_key_t* derived_pubkey_out) {
  if (!descriptor_out) {
    return false;
  }

  fingerprint_t master_fingerprint;
  if (!key_manager_derive_and_serialize_pubkey(path, version, descriptor_out->bare_bip32_key.bytes,
                                               &master_fingerprint, derived_pubkey_out)) {
    return false;
  }

  descriptor_out->bare_bip32_key.size = BIP32_SERIALIZED_EXT_KEY_SIZE;

  if (path.num_indices > 0) {
    descriptor_out->has_origin_path = true;
    memcpy(descriptor_out->origin_path.child, path.indices, path.num_indices * sizeof(uint32_t));
    descriptor_out->origin_path.child_count = path.num_indices;
    descriptor_out->origin_path.wildcard = false;
  } else {
    descriptor_out->has_origin_path = false;
    memzero(&descriptor_out->origin_path, sizeof(descriptor_out->origin_path));
  }

  descriptor_out->has_xpub_path = false;
  memzero(&descriptor_out->xpub_path, sizeof(descriptor_out->xpub_path));

  memcpy(descriptor_out->origin_fingerprint.bytes, master_fingerprint.bytes,
         BIP32_KEY_FINGERPRINT_SIZE);
  descriptor_out->origin_fingerprint.size = BIP32_KEY_FINGERPRINT_SIZE;
  descriptor_out->wildcard = fwpb_wildcard_UNHARDENED;

  return true;
}

static void handle_derive(ipc_ref_t* message) {
  fwpb_wallet_cmd* m_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* m_rsp = proto_get_rsp();

  m_rsp->which_msg = fwpb_wallet_rsp_derive_rsp_tag;

  fwpb_derive_key_descriptor_cmd* cmd = &m_cmd->msg.derive_key_descriptor_cmd;
  fwpb_derive_rsp* rsp = &m_rsp->msg.derive_rsp;
  extended_key_t derived_pubkey __attribute__((__cleanup__(bip32_zero_key))) = {0};

  if (!cmd->has_derivation_path) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
    LOGE("No deriv path");
    goto out;
  } else if (cmd->derivation_path.child_count > BIP32_MAX_DERIVATION_DEPTH) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
    LOGE("Deriv path too long");
    goto out;
  }

  derivation_path_t derivation_path = {
    .indices = cmd->derivation_path.child,
    .num_indices = cmd->derivation_path.child_count,
  };

  version_bytes_t version = (cmd->network == BITCOIN) ? MAINNET_PUB : TESTNET_PUB;
  if (!key_manager_derive_key_descriptor(derivation_path, version, &rsp->descriptor,
                                         &derived_pubkey)) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_DERIVATION_FAILED;
    goto out;
  }

  if (is_bip84_spend_path(&derivation_path)) {
    if (!populate_key_attestation(&derived_pubkey, rsp)) {
      rsp->has_descriptor = false;
      memzero(&rsp->descriptor, sizeof(rsp->descriptor));
      clear_key_attestation(rsp);
      rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
      goto out;
    }
  } else {
    clear_key_attestation(rsp);
  }

  rsp->has_descriptor = true;
  rsp->status = fwpb_derive_rsp_derive_rsp_status_SUCCESS;

out:
  proto_send_rsp(m_cmd, m_rsp);
}

key_manager_sign_result_t key_manager_derive_and_sign(derivation_path_t path,
                                                      const uint8_t hash[SHA256_DIGEST_SIZE],
                                                      uint8_t sig_out[ECC_SIG_SIZE]) {
  extended_key_t key_priv CLEANUP(bip32_zero_key);
  if (!wallet_derive_key_priv_using_cache(&key_priv, path)) {
    LOGE("d&s: deriv");
    return KEY_MANAGER_SIGN_DERIVATION_FAILED;
  }

  uint8_t hash_buf[SHA256_DIGEST_SIZE];
  memcpy(hash_buf, hash, SHA256_DIGEST_SIZE);
  policy_sign_result_t sign_result = bip32_sign_with_policy(&key_priv, path, hash_buf, sig_out);
  memzero(hash_buf, sizeof(hash_buf));
  switch (sign_result) {
    case POLICY_SIGN_SUCCESS:
      return KEY_MANAGER_SIGN_SUCCESS;
    case POLICY_SIGN_SIGNING_ERROR:
      LOGE("d&s: sign");
      return KEY_MANAGER_SIGN_SIGNING_FAILED;
    case POLICY_SIGN_POLICY_VIOLATION:
      LOGE("d&s: policy");
      return KEY_MANAGER_SIGN_POLICY_VIOLATION;
    default:
      LOGE("d&s: err");
      return KEY_MANAGER_SIGN_SIGNING_FAILED;
  }
}

static void do_sync_derive_and_sign(fwpb_wallet_cmd* m_cmd, fwpb_wallet_rsp* m_rsp) {
  fwpb_derive_key_descriptor_and_sign_cmd* cmd = &m_cmd->msg.derive_key_descriptor_and_sign_cmd;
  fwpb_derive_and_sign_rsp* rsp = &m_rsp->msg.derive_and_sign_rsp;

  derivation_path_t derivation_path = {
    .indices = cmd->derivation_path.child,
    .num_indices = cmd->derivation_path.child_count,
  };

  key_manager_sign_result_t result =
    key_manager_derive_and_sign(derivation_path, cmd->hash.bytes, rsp->signature.bytes);
  switch (result) {
    case KEY_MANAGER_SIGN_SUCCESS:
      rsp->signature.size = ECC_SIG_SIZE;
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_SUCCESS;
      break;
    case KEY_MANAGER_SIGN_DERIVATION_FAILED:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_DERIVATION_FAILED;
      break;
    case KEY_MANAGER_SIGN_SIGNING_FAILED:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_SIGNING_FAILED;
      break;
    case KEY_MANAGER_SIGN_POLICY_VIOLATION:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_POLICY_VIOLATION;
      break;
    default:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_SIGNING_FAILED;
      break;
  }
}

static void do_async_derive_and_sign(fwpb_wallet_cmd* m_cmd, fwpb_wallet_rsp* m_rsp) {
  fwpb_derive_key_descriptor_and_sign_cmd* cmd = &m_cmd->msg.derive_key_descriptor_and_sign_cmd;
  fwpb_derive_and_sign_rsp* rsp = &m_rsp->msg.derive_and_sign_rsp;

  switch (crypto_task_get_status()) {
    case CRYPTO_TASK_IN_PROGRESS:
      m_rsp->status = fwpb_status_IN_PROGRESS;
      return;
    case CRYPTO_TASK_SUCCESS:
      if (!crypto_task_get_and_clear_signature(cmd->hash.bytes, cmd->derivation_path.child,
                                               cmd->derivation_path.child_count,
                                               rsp->signature.bytes)) {
        m_rsp->status = fwpb_status_INVALID_ARGUMENT;
      } else {
        rsp->signature.size = ECC_SIG_SIZE;
        m_rsp->status = fwpb_status_SUCCESS;
      }
      return;
    case CRYPTO_TASK_ERROR:
      crypto_task_reset_status();
      m_rsp->status = fwpb_status_ERROR;
      return;
    default:
      break;
  }

  derivation_path_t derivation_path = {
    .indices = cmd->derivation_path.child,
    .num_indices = cmd->derivation_path.child_count,
  };
  crypto_task_set_parameters(&derivation_path, cmd->hash.bytes);

  crypto_task_signal();

  m_rsp->status = fwpb_status_IN_PROGRESS;
}

void handle_derive_and_sign(ipc_ref_t* message) {
  fwpb_wallet_cmd* m_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* m_rsp = proto_get_rsp();

  m_rsp->which_msg = fwpb_wallet_rsp_derive_and_sign_rsp_tag;

  fwpb_derive_key_descriptor_and_sign_cmd* cmd = &m_cmd->msg.derive_key_descriptor_and_sign_cmd;

  if (!cmd->has_derivation_path) {
    m_rsp->status = fwpb_status_ERROR;
    LOGE("No deriv path");
    goto out;
  } else if (cmd->derivation_path.child_count > BIP32_MAX_DERIVATION_DEPTH) {
    m_rsp->status = fwpb_status_ERROR;
    LOGE("Deriv path too long");
    goto out;
  }

  if (cmd->hash.size != SHA256_DIGEST_SIZE) {
    m_rsp->status = fwpb_status_ERROR;
    LOGE("Bad hash len");
    goto out;
  }

  if (cmd->async_sign) {
    do_async_derive_and_sign(m_cmd, m_rsp);
  } else {
    // This is a blocking call and exists for backwards compatibility.
    do_sync_derive_and_sign(m_cmd, m_rsp);
  };

out:
  proto_send_rsp(m_cmd, m_rsp);
}

static void handle_seal_csek(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_seal_csek_rsp_tag;
  rsp->msg.seal_csek_rsp.rsp_status = fwpb_seal_csek_rsp_seal_csek_rsp_status_ERROR;

  _Static_assert(sizeof(cmd->msg.seal_csek_cmd.unsealed_csek.bytes) ==
                   sizeof(rsp->msg.seal_csek_rsp.sealed_csek.data.bytes),
                 "mismatched CSEK sizes");
  _Static_assert(sizeof(cmd->msg.seal_csek_cmd.unsealed_csek.bytes) == CSEK_LENGTH,
                 "mismatched CSEK sizes");
  _Static_assert(sizeof(cmd->msg.seal_csek_cmd.csek.nonce.bytes) == AES_GCM_IV_LENGTH,
                 "mismatched CSEK IV sizes");
  _Static_assert(sizeof(cmd->msg.seal_csek_cmd.csek.mac.bytes) == AES_GCM_TAG_LENGTH,
                 "mismatched CSEK tag sizes");

  // csek is consumed and cleared after the branch below, so decrypted storage
  // must remain alive through the common cleanup path.
  uint8_t decrypted_csek[CSEK_LENGTH] = {0};
  uint8_t* csek = decrypted_csek;
  if (!cmd->msg.seal_csek_cmd.has_csek) {
    csek = cmd->msg.seal_csek_cmd.unsealed_csek.bytes;
    if (cmd->msg.seal_csek_cmd.unsealed_csek.size != CSEK_LENGTH) {
      LOGE("Bad CSEK len");
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
  } else {
    if (!proto_secure_channel_message_has_valid_sizes(&cmd->msg.seal_csek_cmd.csek, CSEK_LENGTH)) {
      LOGE("Bad CSEK sizes");
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }

    if (secure_nfc_channel_decrypt(cmd->msg.seal_csek_cmd.csek.ciphertext.bytes, csek,
                                   cmd->msg.seal_csek_cmd.csek.ciphertext.size,
                                   cmd->msg.seal_csek_cmd.csek.nonce.bytes,
                                   cmd->msg.seal_csek_cmd.csek.mac.bytes) != SECURE_CHANNEL_OK) {
      LOGE("CSEK decrypt fail");
      rsp->status = fwpb_status_SECURE_CHANNEL_ERROR;
      goto out;
    }
  }

  wallet_res_t result =
    wallet_csek_encrypt(csek,                                            // Raw CSEK
                        rsp->msg.seal_csek_rsp.sealed_csek.data.bytes,   // Wrapped CSEK out
                        CSEK_LENGTH,                                     // CSEK size
                        rsp->msg.seal_csek_rsp.sealed_csek.nonce.bytes,  // IV out
                        rsp->msg.seal_csek_rsp.sealed_csek.tag.bytes     // Tag out
    );
  switch (result) {
    case WALLET_RES_OK:
      rsp->msg.seal_csek_rsp.rsp_status = fwpb_seal_csek_rsp_seal_csek_rsp_status_SUCCESS;
      break;
    case WALLET_RES_SEALING_ERR:
      rsp->msg.seal_csek_rsp.rsp_status = fwpb_seal_csek_rsp_seal_csek_rsp_status_SEAL_ERROR;
      goto out;
    default:
      rsp->msg.seal_csek_rsp.rsp_status = fwpb_seal_csek_rsp_seal_csek_rsp_status_ERROR;
      goto out;
  }

  rsp->msg.seal_csek_rsp.has_sealed_csek = true;

  _Static_assert(CSEK_LENGTH == sizeof(rsp->msg.seal_csek_rsp.sealed_csek.data.bytes),
                 "mismatch CSEK sizes");
  rsp->msg.seal_csek_rsp.sealed_csek.data.size = CSEK_LENGTH;

  _Static_assert(AES_GCM_IV_LENGTH == sizeof(rsp->msg.seal_csek_rsp.sealed_csek.nonce.bytes),
                 "mismatch CSEK IV sizes");
  rsp->msg.seal_csek_rsp.sealed_csek.nonce.size = AES_GCM_IV_LENGTH;

  _Static_assert(AES_GCM_TAG_LENGTH == sizeof(rsp->msg.seal_csek_rsp.sealed_csek.tag.bytes),
                 "mismatch CSEK tag sizes");
  rsp->msg.seal_csek_rsp.sealed_csek.tag.size = AES_GCM_TAG_LENGTH;
  rsp->status = fwpb_status_SUCCESS;

out:
  memzero(csek, CSEK_LENGTH);
  proto_send_rsp(cmd, rsp);
}

void handle_remove_wallet_state(void) {
  onboarding_wipe_state();

  // Give sysinfo time to send the wipe response back to the host before we
  // reboot. The wipe itself is already complete at this point, so the delay
  // only risks the app not seeing the response (worst case it retries or
  // observes the reboot), never partial erasure.
#define WIPE_RESET_DELAY_MS (500u)
  rtos_thread_sleep(WIPE_RESET_DELAY_MS);

  mcu_reset_with_reason(MCU_RESET_WIPE);
}

static void handle_hardware_attestation(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_hardware_attestation_rsp_tag;

  if (crypto_sign_challenge(cmd->msg.hardware_attestation_cmd.nonce.bytes,
                            sizeof(cmd->msg.hardware_attestation_cmd.nonce.bytes),
                            rsp->msg.hardware_attestation_rsp.signature.bytes,
                            sizeof(rsp->msg.hardware_attestation_rsp.signature.bytes))) {
    rsp->msg.hardware_attestation_rsp.signature.size =
      sizeof(rsp->msg.hardware_attestation_rsp.signature.bytes);
  }

  proto_send_rsp(cmd, rsp);
}

NO_OPTIMIZE static void handle_secure_channel_establish(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  volatile uint32_t protocol_version = cmd->msg.secure_channel_establish_cmd.protocol_version;

  rsp->which_msg = fwpb_wallet_rsp_secure_channel_establish_rsp_tag;
  rsp->msg.secure_channel_establish_rsp.protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;

  uint32_t pk_device_len = sizeof(rsp->msg.secure_channel_establish_rsp.pk_device.bytes);

  _Static_assert(
    sizeof(cmd->msg.secure_channel_establish_cmd.pk_host.bytes) == SECURE_CHANNEL_PUBKEY_MAX_LEN,
    "wrong size for pk_host");
  _Static_assert(
    sizeof(rsp->msg.secure_channel_establish_rsp.pk_device.bytes) == SECURE_CHANNEL_PUBKEY_MAX_LEN,
    "wrong size for pk_device");
  _Static_assert(sizeof(rsp->msg.secure_channel_establish_rsp.exchange_sig.bytes) == ECC_SIG_SIZE,
                 "wrong size for exchange_sig");
  _Static_assert(
    sizeof(rsp->msg.secure_channel_establish_rsp.key_confirmation_tag.bytes) == AES_GCM_TAG_LENGTH,
    "wrong size for key_confirmation_tag");

  SECURE_IF_FAILIN(!secure_channel_protocol_version_supported(protocol_version)) {
    LOGE("Bad SC proto ver: %" PRIu32, protocol_version);
    rsp->status = fwpb_status_VERSION_MISMATCH;
    goto out;
  }

  uint32_t pk_host_len = (uint32_t)cmd->msg.secure_channel_establish_cmd.pk_host.size;
  if (pk_host_len != EC_PUBKEY_SIZE_X25519) {
    LOGE("Bad SC pk len");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  if (secure_nfc_channel_establish(
        cmd->msg.secure_channel_establish_cmd.pk_host.bytes, pk_host_len,
        rsp->msg.secure_channel_establish_rsp.pk_device.bytes, &pk_device_len,
        rsp->msg.secure_channel_establish_rsp.exchange_sig.bytes,
        sizeof(rsp->msg.secure_channel_establish_rsp.exchange_sig.bytes),
        rsp->msg.secure_channel_establish_rsp.key_confirmation_tag.bytes) != SECURE_CHANNEL_OK) {
    rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
    goto out;
  }

  rsp->msg.secure_channel_establish_rsp.pk_device.size = pk_device_len;
  rsp->msg.secure_channel_establish_rsp.exchange_sig.size =
    sizeof(rsp->msg.secure_channel_establish_rsp.exchange_sig.bytes);
  rsp->msg.secure_channel_establish_rsp.key_confirmation_tag.size =
    sizeof(rsp->msg.secure_channel_establish_rsp.key_confirmation_tag.bytes);

  rsp->status = fwpb_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

static fwpb_status handle_grant_request(grant_action_t action, grant_request_t* grant_request) {
  grant_protocol_result_t result = grant_protocol_create_request(action, grant_request);

  switch (result) {
    case GRANT_RESULT_OK:
      return fwpb_status_SUCCESS;
    case GRANT_RESULT_ERROR_SIGNING:
      return fwpb_status_SIGNING_FAILED;
    case GRANT_RESULT_ERROR_INVALID_ARGUMENT:
      return fwpb_status_INVALID_ARGUMENT;
    default:
      return fwpb_status_ERROR;
  }
}

static fwpb_status handle_grant_finalize(grant_t* grant) {
  grant_protocol_result_t result = grant_protocol_verify_grant(grant);
  switch (result) {
    case GRANT_RESULT_OK:
      return fwpb_status_SUCCESS;
    case GRANT_RESULT_ERROR_VERIFICATION:
      return fwpb_status_VERIFICATION_FAILED;
    case GRANT_RESULT_ERROR_INVALID_ARGUMENT:
      return fwpb_status_INVALID_ARGUMENT;
    case GRANT_RESULT_ERROR_REQUEST_MISMATCH:
      return fwpb_status_REQUEST_MISMATCH;
    case GRANT_RESULT_ERROR_VERSION_MISMATCH:
      return fwpb_status_VERSION_MISMATCH;
    case GRANT_RESULT_ERROR_STORAGE:
      return fwpb_status_FILE_NOT_FOUND;
    default:
      return fwpb_status_ERROR;
  }
}

void handle_fingerprint_reset_request(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_fingerprint_reset_request_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  grant_request_t grant_request = {0};
  rsp->status = handle_grant_request(ACTION_FINGERPRINT_RESET, &grant_request);
  if (rsp->status != fwpb_status_SUCCESS) {
    goto out;
  }

  rsp->msg.fingerprint_reset_request_rsp.grant_request.size = sizeof(grant_request);
  memcpy(rsp->msg.fingerprint_reset_request_rsp.grant_request.bytes, &grant_request,
         sizeof(grant_request));

out:
  proto_send_rsp(cmd, rsp);
}

void handle_fingerprint_reset_finalize(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_fingerprint_reset_finalize_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  grant_t* grant = (grant_t*)cmd->msg.fingerprint_reset_finalize_cmd.grant.bytes;
  if (cmd->msg.fingerprint_reset_finalize_cmd.grant.size != sizeof(grant_t)) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  rsp->status = handle_grant_finalize(grant);

out:
  proto_send_rsp(cmd, rsp);
}

void handle_provision_app_auth_pubkey(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_provision_app_auth_pubkey_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  // Check pubkey size (33 bytes for compressed secp256k1)
  if (cmd->msg.provision_app_auth_pubkey_cmd.pubkey.size != 33) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    LOGE("Bad app pk sz");
    goto out;
  }

  grant_protocol_result_t result =
    grant_protocol_provision_app_auth_pubkey(cmd->msg.provision_app_auth_pubkey_cmd.pubkey.bytes);

  switch (result) {
    case GRANT_RESULT_OK:
      rsp->status = fwpb_status_SUCCESS;
      break;
    case GRANT_RESULT_ERROR_INVALID_ARGUMENT:
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      break;
    case GRANT_RESULT_ERROR_APP_VERIFICATION:
      rsp->status = fwpb_status_VERIFICATION_FAILED;
      break;
    case GRANT_RESULT_ERROR_STORAGE:
      rsp->status = fwpb_status_STORAGE_ERR;
      break;
    default:
      rsp->status = fwpb_status_ERROR;
      break;
  }

out:
  proto_send_rsp(cmd, rsp);
}

void key_manager_thread(void* UNUSED(args)) {
  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);

  if (onboarding_complete() == SECURE_FALSE) {
    // TODO(W-3920) Yeah, yeah, yeah... I know.
    // Need this sleep to ensure this animation doesn't play just before the charging indicator.
    // The proper fix is an animation state machine; see ticket.
    rtos_thread_sleep(200);

    // TODO(W-4580)
    UI_SET_IDLE_STATE(UI_EVENT_IDLE);
  }

  key_manager_task_register_listeners();

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(key_manager_port, &message);

    // Handlers can derive seeds and private keys into memory and CPU registers. Suppress coredumps
    // until the handler returns and its cleanup callbacks have wiped those values.
    memfault_port_coredump_sensitive_operation_begin();

#if 0
    if (sysevent_get(SYSEVENT_BREAK_GLASS_READY)) {
      LOGW("Break glass: policies off");
      policy_disable();
    }
#endif

    switch (message.tag) {
      // Unauthenticated commands
      case IPC_PROTO_SEAL_CSEK_CMD:
        handle_seal_csek(&message);
        break;
      case IPC_PROTO_HARDWARE_ATTESTATION_CMD:
        handle_hardware_attestation(&message);
        break;
      // Authenticated commands
      case IPC_PROTO_UNSEAL_CSEK_CMD:
        key_manager_task_port_handle_unseal_csek(&message);
        break;
      case IPC_PROTO_DERIVE_KEY_DESCRIPTOR_CMD:
        handle_derive(&message);
        break;
      case IPC_PROTO_DERIVE_KEY_DESCRIPTOR_AND_SIGN_CMD:
        key_manager_task_port_handle_derive_and_sign(&message);
        break;
      case IPC_KEY_MANAGER_REMOVE_WALLET_STATE:
        handle_remove_wallet_state();
        break;
      case IPC_PROTO_SECURE_CHANNEL_ESTABLISH_CMD:
        handle_secure_channel_establish(&message);
        break;
      case IPC_KEY_MANAGER_CLEAR_DERIVED_KEY_CACHE:
        wallet_clear_derived_key_cache();
        break;
      case IPC_KEY_MANAGER_SIGN_DEFERRED:
        key_manager_task_try_deferred_sign();
        key_manager_task_try_deferred_stream_sign();
        key_manager_task_try_sap_deferred_sign();
        break;
      case IPC_PROTO_FINGERPRINT_RESET_REQUEST_CMD: {
        handle_fingerprint_reset_request(&message);
        break;
      }
      case IPC_PROTO_FINGERPRINT_RESET_FINALIZE_CMD: {
        key_manager_task_port_handle_fingerprint_reset_finalize(&message);
        break;
      }
      case IPC_PROTO_PROVISION_APP_AUTH_PUBKEY_CMD: {
        handle_provision_app_auth_pubkey(&message);
        break;
      }
      case IPC_PROTO_GET_ADDRESS_CMD: {
        key_manager_task_port_handle_get_address(&message);
        break;
      }
      case IPC_PROTO_VERIFY_KEYS_AND_BUILD_DESCRIPTOR_CMD: {
        key_manager_task_port_handle_verify_keys_and_build_descriptor(&message);
        break;
      }
      case IPC_KEY_MANAGER_UXC_SESSION_INIT: {
        key_manager_task_handle_uxc_session_init();
        break;
      }
      case IPC_PROTO_SIGN_TX_REQUEST_CMD: {
        key_manager_task_handle_sign_tx_request(&message);
        break;
      }
      case IPC_PROTO_SIGN_STREAM_START_CMD: {
        key_manager_task_handle_sign_stream_start(&message);
        break;
      }
      case IPC_PROTO_SIGN_STREAM_TRANSFER_CMD: {
        key_manager_task_handle_sign_stream_transfer(&message);
        break;
      }
      case IPC_PROTO_SIGN_STREAM_FINALIZE_CMD: {
        key_manager_task_handle_sign_stream_finalize(&message);
        break;
      }
      case IPC_PROTO_GET_TX_SIGNATURE_CMD: {
        key_manager_task_handle_get_tx_signature(&message);
        break;
      }
      case IPC_PROTO_GET_TX_SIGNATURES_BATCH_CMD: {
        key_manager_task_handle_get_tx_signatures_batch(&message);
        break;
      }
      case IPC_PROTO_SWEEP_SIGN_CMD: {
        key_manager_task_handle_sweep_sign(&message);
        break;
      }
      case IPC_PROTO_SWEEP_SIGN_STREAM_START_CMD: {
        key_manager_task_handle_sweep_sign_stream_start(&message);
        break;
      }
      case IPC_KEY_MANAGER_UXC_SESSION_RESPONSE: {
        key_manager_task_handle_uxc_session_response(&message);
        break;
      }
      case IPC_PROTO_SIGN_ACTION_PROOF_CMD: {
        key_manager_task_handle_sign_action_proof(&message);
        break;
      }
      case IPC_PROTO_LOST_APP_RECOVERY_CMD: {
        key_manager_task_handle_lost_app_recovery(&message);
        break;
      }
      case IPC_PROTO_LOST_APP_RECOVERY_CONTINUE_CMD: {
        key_manager_task_handle_lost_app_recovery_continue(&message);
        break;
      }
      case IPC_PROTO_LOST_APP_RECOVERY_SIGN_CHALLENGE_CMD: {
        key_manager_task_handle_lost_app_recovery_sign_challenge(&message);
        break;
      }
      case IPC_PROTO_ROTATE_APP_AUTH_KEYS_CMD: {
        key_manager_task_handle_rotate_app_auth_keys(&message);
        break;
      }
      case IPC_PROTO_UPGRADE_ROTATE_APP_AUTH_KEYS_CMD: {
        key_manager_task_handle_upgrade_rotate_app_auth_keys(&message);
        break;
      }
      case IPC_PROTO_SIGN_CHALLENGE_AND_SEAL_SEKS_CMD: {
        key_manager_task_handle_sign_challenge_and_seal_seks(&message);
        break;
      }
      case IPC_PROTO_RECOVERY_AUTHORIZE_LOST_APP_CMD: {
        key_manager_task_handle_recovery_authorize_lost_app(&message);
        break;
      }
      case IPC_PROTO_RECOVERY_AUTHORIZE_LOST_HW_CMD: {
        key_manager_task_handle_recovery_authorize_lost_hw(&message);
        break;
      }
      case IPC_PROTO_UPGRADE_AUTHORIZE_W3_CMD: {
        key_manager_task_handle_upgrade_authorize_w3(&message);
        break;
      }
      case IPC_PROTO_EEK_RESTORATION_UNSEAL_SYMMETRIC_KEY_CMD: {
        key_manager_task_handle_eek_restoration_unseal_symmetric_key(&message);
        break;
      }
      case IPC_PROTO_FULL_ACCOUNT_CLOUD_BACKUP_RESTORATION_CMD: {
        key_manager_task_handle_full_account_cloud_backup_restoration(&message);
        break;
      }
      case IPC_PROTO_FULL_ACCOUNT_CLOUD_BACKUP_RESTORATION_CONTINUE_CMD: {
        key_manager_task_handle_full_account_cloud_backup_restoration_continue(&message);
        break;
      }
      case IPC_PROTO_KEYSET_REPAIR_UNSEAL_SYMMETRIC_KEY_CMD: {
        key_manager_task_handle_keyset_repair_unseal_symmetric_key(&message);
        break;
      }
      case IPC_PROTO_KEYSET_REPAIR_ROTATE_HW_KEY_CMD: {
        key_manager_task_handle_keyset_repair_rotate_hw_key(&message);
        break;
      }
      default:
        LOGE("Unknown msg: %ld", message.tag);
    }

    memfault_port_coredump_sensitive_operation_end();
  }
}

void key_manager_task_create(void) {
  key_manager_priv.queue = rtos_queue_create(key_manager_queue, ipc_ref_t, 4);
  ASSERT(key_manager_priv.queue);
  ipc_register_port(key_manager_port, key_manager_priv.queue);

  rtos_thread_t* key_manager_thread_handle =
    rtos_thread_create(key_manager_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 8192);
  ASSERT(key_manager_thread_handle);

  key_manager_priv.crypto_thread = crypto_task_create();
  ASSERT(key_manager_priv.crypto_thread);

#define REGIONS(X)                                                           \
  X(wallet_cmd_pool, extended_keys, WALLET_POOL_R0_SIZE, WALLET_POOL_R0_NUM) \
  X(wallet_cmd_pool, r1, WALLET_POOL_R1_SIZE, WALLET_POOL_R1_NUM)            \
  X(wallet_cmd_pool, r2, WALLET_POOL_R2_SIZE, WALLET_POOL_R2_NUM)
  key_manager_priv.mempool = mempool_create(wallet_cmd_pool);
#undef REGIONS
  wallet_init(key_manager_priv.mempool);

  // Intentionally NOT enabled for this release.
  policy_init(&wallet_get_w1_auth_path, SECURE_FALSE);
}
