#pragma once

#include "attributes.h"
#include "bip32.h"
#include "hash.h"
#include "ipc.h"
#include "rtos.h"
#include "wallet.pb.h"

#include <stdbool.h>

typedef enum {
  CRYPTO_TASK_WAITING = 0,
  CRYPTO_TASK_IN_PROGRESS = 1,
  CRYPTO_TASK_SUCCESS = 2,
  CRYPTO_TASK_ERROR = 3,
  CRYPTO_TASK_DERIVATION_FAILED = 5,
  CRYPTO_TASK_SIGNING_FAILED = 6,
  CRYPTO_TASK_POLICY_VIOLATION = 7,
} crypto_task_status_t;

// BIP32 derivation path constants
#define BIP84_PURPOSE            (84)
#define BIP32_COIN_BTC           (0)
#define BIP32_COIN_TESTNET       (1)
#define BIP32_PATH_DEPTH_ACCOUNT (3)

typedef enum {
  KEY_MANAGER_SIGN_SUCCESS = 0,
  KEY_MANAGER_SIGN_DERIVATION_FAILED,
  KEY_MANAGER_SIGN_SIGNING_FAILED,
  KEY_MANAGER_SIGN_POLICY_VIOLATION,
} key_manager_sign_result_t;

rtos_thread_t* crypto_task_create(void);
crypto_task_status_t crypto_task_get_status(void);
void crypto_task_reset_status(void);
bool crypto_task_get_and_clear_signature(uint8_t expected_hash[SHA256_DIGEST_SIZE],
                                         uint32_t* expected_indices, uint32_t num_indices,
                                         uint8_t signature[ECC_SIG_SIZE]);
void crypto_task_set_parameters(derivation_path_t* derivation_path,
                                uint8_t hash[SHA256_DIGEST_SIZE]);
void crypto_task_signal(void);

void key_manager_task_register_listeners(void);
void key_manager_task_handle_uxc_session_init(void);
NO_OPTIMIZE void key_manager_task_handle_uxc_session_response(ipc_ref_t* message);
void key_manager_task_port_handle_get_address(ipc_ref_t* message);
void key_manager_task_port_handle_verify_keys_and_build_descriptor(ipc_ref_t* message);
void key_manager_task_port_handle_unseal_csek(ipc_ref_t* message);
void key_manager_task_port_handle_derive_and_sign(ipc_ref_t* message);
void key_manager_task_port_handle_fingerprint_reset_finalize(ipc_ref_t* message);

// Core implementation of FingerprintResetFinalizeCmd (provide grant), used by W1 port.
void handle_fingerprint_reset_finalize(ipc_ref_t* message);

// Core implementation of DeriveKeyDescriptorAndSignCmd, used by W1 port.
void handle_derive_and_sign(ipc_ref_t* message);

void key_manager_task_try_deferred_sign(void);
void key_manager_task_try_deferred_stream_sign(void);
void key_manager_task_try_sap_deferred_sign(void);
void key_manager_task_handle_sign_action_proof(ipc_ref_t* message);
void key_manager_task_handle_lost_app_recovery(ipc_ref_t* message);
void key_manager_task_handle_lost_app_recovery_continue(ipc_ref_t* message);
void key_manager_task_handle_lost_app_recovery_sign_challenge(ipc_ref_t* message);
void key_manager_task_handle_rotate_app_auth_keys(ipc_ref_t* message);
void key_manager_task_handle_upgrade_rotate_app_auth_keys(ipc_ref_t* message);
void key_manager_task_handle_sign_challenge_and_seal_seks(ipc_ref_t* message);
void key_manager_task_handle_recovery_authorize_lost_app(ipc_ref_t* message);
void key_manager_task_handle_recovery_authorize_lost_hw(ipc_ref_t* message);
void key_manager_task_handle_upgrade_authorize_w3(ipc_ref_t* message);
void key_manager_task_handle_eek_restoration_unseal_symmetric_key(ipc_ref_t* message);
void key_manager_task_handle_full_account_cloud_backup_restoration(ipc_ref_t* message);
void key_manager_task_handle_full_account_cloud_backup_restoration_continue(ipc_ref_t* message);
void key_manager_task_handle_keyset_repair_unseal_symmetric_key(ipc_ref_t* message);
void key_manager_task_handle_keyset_repair_rotate_hw_key(ipc_ref_t* message);

// Derive a key at the given path and serialize as a 78-byte bare extended public key.
// Optionally outputs the master fingerprint and/or derived extended public key
// (pass NULL for any out-parameter that is not needed).
// Used by handle_derive (for DeriveKeyDescriptorCmd) and lost app recovery.
bool key_manager_derive_and_serialize_pubkey(derivation_path_t path, version_bytes_t version,
                                             uint8_t bare_key_out[BIP32_SERIALIZED_EXT_KEY_SIZE],
                                             fingerprint_t* master_fp_out,
                                             extended_key_t* derived_pubkey_out);

// Derive a key at the given path and populate a full key_descriptor response payload.
// Uses the same descriptor serialization contract as DeriveKeyDescriptorCmd.
// Optionally outputs the derived extended public key (pass NULL if not needed).
bool key_manager_derive_key_descriptor(derivation_path_t path, version_bytes_t version,
                                       fwpb_key_descriptor* descriptor_out,
                                       extended_key_t* derived_pubkey_out);

// Derive a key at the given path and sign a hash with policy enforcement.
// Used by do_sync_derive_and_sign (for DeriveKeyDescriptorAndSignCmd) and lost app recovery.
key_manager_sign_result_t key_manager_derive_and_sign(derivation_path_t path,
                                                      const uint8_t hash[SHA256_DIGEST_SIZE],
                                                      uint8_t sig_out[ECC_SIG_SIZE]);

void key_manager_task_handle_sign_tx_request(ipc_ref_t* message);

// Streaming signing protocol handlers (>5 inputs)
void key_manager_task_handle_sign_stream_start(ipc_ref_t* message);
void key_manager_task_handle_sign_stream_transfer(ipc_ref_t* message);
void key_manager_task_handle_sign_stream_finalize(ipc_ref_t* message);
void key_manager_task_handle_get_tx_signature(ipc_ref_t* message);
void key_manager_task_handle_get_tx_signatures_batch(ipc_ref_t* message);

// Sweep signing handlers (W3 only). See sweep_sign_cmd in wallet.proto.
void key_manager_task_handle_sweep_sign(ipc_ref_t* message);
void key_manager_task_handle_sweep_sign_stream_start(ipc_ref_t* message);
