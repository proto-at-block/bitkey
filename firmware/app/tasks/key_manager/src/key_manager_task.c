#include "key_manager_task.h"

#include "animation.h"
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
#include "key_manager_task_impl.h"
#include "log.h"
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
#include "wallet.h"
#include "wallet.pb.h"
#include "wkek.h"

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

static void handle_derive(ipc_ref_t* message) {
  fwpb_wallet_cmd* m_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* m_rsp = proto_get_rsp();

  m_rsp->which_msg = fwpb_wallet_rsp_derive_rsp_tag;

  fwpb_derive_key_descriptor_cmd* cmd = &m_cmd->msg.derive_key_descriptor_cmd;
  fwpb_derive_rsp* rsp = &m_rsp->msg.derive_rsp;

  if (!cmd->has_derivation_path) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
    LOGE("derivation path not provided");
    goto out;
  } else if (cmd->derivation_path.child_count > BIP32_MAX_DERIVATION_DEPTH) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
    LOGE("derivation path too long");
    goto out;
  }

  derivation_path_t derivation_path = {
    .indices = cmd->derivation_path.child,
    .num_indices = cmd->derivation_path.child_count,
  };
  extended_key_t key_priv __attribute__((__cleanup__(bip32_zero_key)));
  fingerprint_t key_priv_master_fingerprint;
  fingerprint_t key_priv_childs_parent_fingerprint;
  if (seed_derive_bip32(derivation_path, &key_priv, &key_priv_master_fingerprint,
                        &key_priv_childs_parent_fingerprint) != SEED_RES_OK) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_DERIVATION_FAILED;
    LOGE("seed_derive failed");
    goto out;
  }

  extended_key_t key_pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&key_priv, &key_pub)) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
    LOGE("bip32_priv_to_pub failed");
    goto out;
  }

  uint32_t child_number =
    derivation_path.num_indices > 0 ? derivation_path.indices[derivation_path.num_indices - 1] : 0;

  if (!bip32_serialize_ext_key(&key_pub, NULL, key_priv_childs_parent_fingerprint.bytes,
                               (cmd->network == BITCOIN) ? MAINNET_PUB : TESTNET_PUB, child_number,
                               derivation_path.num_indices, rsp->descriptor.bare_bip32_key.bytes,
                               BIP32_SERIALIZED_EXT_KEY_SIZE)) {
    rsp->status = fwpb_derive_rsp_derive_rsp_status_ERROR;
    LOGE("bip32_serialize_ext_key failed");
    goto out;
  }

  rsp->has_descriptor = true;

  // bare_bip32_key set by bip32_serialize_ext_key
  rsp->descriptor.bare_bip32_key.size = BIP32_SERIALIZED_EXT_KEY_SIZE;

  if (derivation_path.num_indices > 0) {
    rsp->descriptor.has_origin_path = true;
    memcpy(rsp->descriptor.origin_path.child, derivation_path.indices,
           derivation_path.num_indices * sizeof(uint32_t));
    rsp->descriptor.origin_path.child_count = derivation_path.num_indices;
    rsp->descriptor.origin_path.wildcard = false;
  } else {
    rsp->descriptor.has_origin_path = false;
    memzero(&rsp->descriptor.origin_path, sizeof(rsp->descriptor.origin_path));
  }

  rsp->descriptor.has_xpub_path = false;
  memzero(&rsp->descriptor.xpub_path, sizeof(rsp->descriptor.xpub_path));

  memcpy(rsp->descriptor.origin_fingerprint.bytes, key_priv_master_fingerprint.bytes,
         BIP32_KEY_FINGERPRINT_SIZE);
  rsp->descriptor.origin_fingerprint.size = BIP32_KEY_FINGERPRINT_SIZE;
  rsp->descriptor.wildcard = fwpb_wildcard_UNHARDENED;

  rsp->status = fwpb_derive_rsp_derive_rsp_status_SUCCESS;

out:
  proto_send_rsp(m_cmd, m_rsp);
}

static void do_sync_derive_and_sign(fwpb_wallet_cmd* m_cmd, fwpb_wallet_rsp* m_rsp) {
  fwpb_derive_key_descriptor_and_sign_cmd* cmd = &m_cmd->msg.derive_key_descriptor_and_sign_cmd;
  fwpb_derive_and_sign_rsp* rsp = &m_rsp->msg.derive_and_sign_rsp;

  LOGD("Sync derive and sign");

  derivation_path_t derivation_path = {
    .indices = cmd->derivation_path.child,
    .num_indices = cmd->derivation_path.child_count,
  };

  extended_key_t key_priv CLEANUP(bip32_zero_key);
  if (!wallet_derive_key_priv_using_cache(&key_priv, derivation_path)) {
    rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_DERIVATION_FAILED;
    return;
  }

  policy_sign_result_t sign_result =
    bip32_sign_with_policy(&key_priv, derivation_path, cmd->hash.bytes, rsp->signature.bytes);
  switch (sign_result) {
    case POLICY_SIGN_SUCCESS:
      break;
    case POLICY_SIGN_SIGNING_ERROR:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_SIGNING_FAILED;
      LOGE("bip32_sign: signing failed");
      return;
    case POLICY_SIGN_POLICY_VIOLATION:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_POLICY_VIOLATION;
      LOGE("bip32_sign: policy enforced");
      return;
    default:
      rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_ERROR;
      LOGE("bip32_sign: failed");
      return;
  }

  rsp->signature.size = ECC_SIG_SIZE;
  rsp->status = fwpb_derive_and_sign_rsp_derive_and_sign_rsp_status_SUCCESS;
}

static void do_async_derive_and_sign(fwpb_wallet_cmd* m_cmd, fwpb_wallet_rsp* m_rsp) {
  fwpb_derive_key_descriptor_and_sign_cmd* cmd = &m_cmd->msg.derive_key_descriptor_and_sign_cmd;
  fwpb_derive_and_sign_rsp* rsp = &m_rsp->msg.derive_and_sign_rsp;

  LOGD("Status: %d", crypto_task_get_status());

  switch (crypto_task_get_status()) {
    case CRYPTO_TASK_IN_PROGRESS:
      LOGD("in progress");
      m_rsp->status = fwpb_status_IN_PROGRESS;
      return;
    case CRYPTO_TASK_SUCCESS:
      LOGD("success");
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
      LOGD("error");
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

  rtos_notification_signal(key_manager_priv.crypto_thread);

  m_rsp->status = fwpb_status_IN_PROGRESS;
}

static void handle_derive_and_sign(ipc_ref_t* message) {
  fwpb_wallet_cmd* m_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* m_rsp = proto_get_rsp();

  m_rsp->which_msg = fwpb_wallet_rsp_derive_and_sign_rsp_tag;

  LOGD("Received derive_and_sign command");

  fwpb_derive_key_descriptor_and_sign_cmd* cmd = &m_cmd->msg.derive_key_descriptor_and_sign_cmd;

  if (!cmd->has_derivation_path) {
    m_rsp->status = fwpb_status_ERROR;
    LOGE("derivation path not provided");
    goto out;
  } else if (cmd->derivation_path.child_count > BIP32_MAX_DERIVATION_DEPTH) {
    m_rsp->status = fwpb_status_ERROR;
    LOGE("derivation path too long");
    goto out;
  }

  if (cmd->hash.size != SHA256_DIGEST_SIZE) {
    m_rsp->status = fwpb_status_ERROR;
    LOGE("invalid hash length");
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

  uint8_t* csek = NULL;
  if (!cmd->msg.seal_csek_cmd.has_csek) {
    LOGD("Received plaintext CSEK");
    ASSERT(sizeof(cmd->msg.seal_csek_cmd.unsealed_csek.bytes) ==
           cmd->msg.seal_csek_cmd.unsealed_csek.size);
    csek = cmd->msg.seal_csek_cmd.unsealed_csek.bytes;
  } else {
    LOGD("Received CSEK wrapped in secure channel message");

    if (cmd->msg.seal_csek_cmd.csek.ciphertext.size != CSEK_LENGTH) {
      LOGE("Wrong CSEK length");
      goto out;
    }

    uint8_t decrypted_csek[CSEK_LENGTH] = {0};
    if (secure_channel_decrypt(cmd->msg.seal_csek_cmd.csek.ciphertext.bytes, decrypted_csek,
                               cmd->msg.seal_csek_cmd.csek.ciphertext.size,
                               cmd->msg.seal_csek_cmd.csek.nonce.bytes,
                               cmd->msg.seal_csek_cmd.csek.mac.bytes) != SECURE_CHANNEL_OK) {
      LOGE("Failed to decrypt CSEK");
      rsp->status = fwpb_status_SECURE_CHANNEL_ERROR;
      goto out;
    }

    csek = decrypted_csek;
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

out:
  memzero(csek, CSEK_LENGTH);
  proto_send_rsp(cmd, rsp);
}

static void handle_unseal_csek(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_unseal_csek_rsp_tag;

  ASSERT(sizeof(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes) ==
         cmd->msg.unseal_csek_cmd.sealed_csek.data.size);
  _Static_assert(sizeof(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes) ==
                   sizeof(rsp->msg.unseal_csek_rsp.unsealed_csek.bytes),
                 "mismatch CSEK sizes");
  wallet_res_t result =
    wallet_csek_decrypt(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes,          // Wrapped CSEK
                        rsp->msg.unseal_csek_rsp.unsealed_csek.bytes,             // Raw CSEK out
                        sizeof(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes),  // CSEK size
                        cmd->msg.unseal_csek_cmd.sealed_csek.nonce.bytes,         // IV
                        cmd->msg.unseal_csek_cmd.sealed_csek.tag.bytes            // Tag
    );

  switch (result) {
    case WALLET_RES_OK:
      rsp->msg.unseal_csek_rsp.rsp_status = fwpb_unseal_csek_rsp_unseal_csek_rsp_status_SUCCESS;
      break;
    case WALLET_RES_UNSEALING_ERR:
      rsp->msg.unseal_csek_rsp.rsp_status =
        fwpb_unseal_csek_rsp_unseal_csek_rsp_status_UNSEAL_ERROR;
      goto out;
    default:
      rsp->msg.unseal_csek_rsp.rsp_status = fwpb_unseal_csek_rsp_unseal_csek_rsp_status_ERROR;
      goto out;
  }

  _Static_assert(CSEK_LENGTH == sizeof(rsp->msg.unseal_csek_rsp.unsealed_csek.bytes),
                 "mismatch CSEK sizes");
  rsp->msg.unseal_csek_rsp.unsealed_csek.size = CSEK_LENGTH;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_remove_wallet_state(void) {
  onboarding_wipe_state();
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

static void handle_secure_channel_establish(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

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

  if (secure_channel_establish(
        cmd->msg.secure_channel_establish_cmd.pk_host.bytes,
        (uint32_t)cmd->msg.secure_channel_establish_cmd.pk_host.size,
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
    case GRANT_RESULT_ERROR_REQUEST_MISMATCH:
      return fwpb_status_REQUEST_MISMATCH;
    case GRANT_RESULT_ERROR_VERSION_MISMATCH:
      return fwpb_status_VERSION_MISMATCH;
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

  grant_t* grant = (grant_t*)cmd->msg.fingerprint_reset_finalize_cmd.grant.bytes;
  rsp->status = handle_grant_finalize(grant);

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
    static led_set_rest_animation_t LED_TASK_DATA rest_msg = {.animation = (uint32_t)ANI_REST};
    ipc_send(led_port, &rest_msg, sizeof(rest_msg), IPC_LED_SET_REST_ANIMATION);
  }

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(key_manager_port, &message);

#if 0
    if (sysevent_get(SYSEVENT_BREAK_GLASS_READY)) {
      LOGW("Entering break glass mode - disabling signing policies!");
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
        handle_unseal_csek(&message);
        break;
      case IPC_PROTO_DERIVE_KEY_DESCRIPTOR_CMD:
        handle_derive(&message);
        break;
      case IPC_PROTO_DERIVE_KEY_DESCRIPTOR_AND_SIGN_CMD:
        handle_derive_and_sign(&message);
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
      case IPC_PROTO_FINGERPRINT_RESET_REQUEST_CMD: {
        handle_fingerprint_reset_request(&message);
        break;
      }
      case IPC_PROTO_FINGERPRINT_RESET_FINALIZE_CMD: {
        handle_fingerprint_reset_finalize(&message);
        break;
      }
      default:
        LOGE("unknown message %ld", message.tag);
    }
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
  policy_init(&wallet_get_w1_auth_path, false);
}
