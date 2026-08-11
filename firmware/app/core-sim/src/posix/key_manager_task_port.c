/**
 * @file key_manager_task_port.c
 * @brief Platform port for key_manager_task on POSIX.
 *
 * This file mirrors w1/key_manager_task_port.c, providing stub implementations
 * for platform-specific key_manager functions that don't apply to POSIX.
 *
 * Also provides real implementations for crypto functions used by core-sim.
 */

#include "attestation.h"
#include "ecc.h"
#include "hash.h"
#include "ipc.h"
#include "key_management.h"
#include "key_manager_task_impl.h"
#include "sim_provisioning.h"
#include "stdio_defs.h"

// curve25519 functions from src/stm32/curve25519.c (no header file)
extern void curve25519_get_public_key(uint8_t* pk, const uint8_t* sk);
extern void curve25519_get_shared_secret(uint8_t* shared, const uint8_t* my_sk,
                                         const uint8_t* their_pk);

#include <string.h>
#include <unistd.h>

/* key_manager_task_handle_uxc_session_response / _init and
 * key_manager_task_register_listeners are provided by the W1 hardware port
 * (app/tasks/key_manager/src/w1/key_manager_task_port.c), which is compiled
 * into core-sim. The simulator matches W1 semantics: no UXC secure channel. */

// Attestation label prefix for challenge signing
static const uint8_t ATTESTATION_LABEL[] = "ATV1";
#define ATTESTATION_LABEL_LEN 4

/**
 * Sign a digest using the simulator's device identity.
 * Requires provisioning - set CORE_SIM_PROVISION=1 to generate identity.
 */
static bool sign_digest_with_identity(const uint8_t* digest, size_t digest_len, uint8_t* signature,
                                      uint32_t signature_size) {
  if (!sim_is_provisioned()) {
    LOG("sign_digest: device not provisioned (set CORE_SIM_PROVISION=1)");
    return false;
  }

  size_t actual_sig_len = sim_sign_with_device_key(digest, digest_len, signature, signature_size);
  if (actual_sig_len > 0) {
    if (actual_sig_len < signature_size) {
      memset(signature + actual_sig_len, 0, signature_size - actual_sig_len);
    }
    return true;
  }

  return false;
}

bool crypto_sign_challenge(uint8_t* challenge, uint32_t challenge_size, uint8_t* signature,
                           uint32_t signature_size) {
  if (!signature || signature_size < ECC_SIG_SIZE) {
    return false;
  }

  // Hash: SHA256("ATV1" || challenge)
  uint8_t digest[32];
  uint8_t hash_input[ATTESTATION_LABEL_LEN + 256];
  if (challenge_size > sizeof(hash_input) - ATTESTATION_LABEL_LEN) {
    return false;
  }
  memcpy(hash_input, ATTESTATION_LABEL, ATTESTATION_LABEL_LEN);
  memcpy(hash_input + ATTESTATION_LABEL_LEN, challenge, challenge_size);
  if (!crypto_hash(hash_input, ATTESTATION_LABEL_LEN + challenge_size, digest, sizeof(digest),
                   ALG_SHA256)) {
    return false;
  }

  return sign_digest_with_identity(digest, sizeof(digest), signature, signature_size);
}

bool crypto_read_serial(uint8_t* serial_number) {
  if (!serial_number) {
    return false;
  }

  if (sim_is_provisioned()) {
    const uint8_t* sim_serial = sim_get_serial();
    if (sim_serial) {
      memset(serial_number, 0, CRYPTO_SERIAL_SIZE);
      memcpy(serial_number, sim_serial, 8);
      return true;
    }
  }

  // Legacy stub behavior - use process PID
  memset(serial_number, 0, CRYPTO_SERIAL_SIZE);
  uint32_t pid = (uint32_t)getpid();
  memcpy(serial_number, &pid, sizeof(pid));
  return true;
}

bool crypto_sign_with_device_identity(uint8_t* data, uint32_t data_size, uint8_t* signature,
                                      uint32_t signature_size) {
  if (!signature || signature_size < ECC_SIG_SIZE) {
    return false;
  }

  // Hash the data first
  uint8_t digest[32];
  if (!crypto_hash(data, data_size, digest, sizeof(digest), ALG_SHA256)) {
    return false;
  }

  return sign_digest_with_identity(digest, sizeof(digest), signature, signature_size);
}

bool export_pubkey(key_handle_t* key_in, key_handle_t* key_out) {
  if (!key_in || !key_out || !key_out->key.bytes) {
    return false;
  }

  if (key_in->alg == ALG_ECC_X25519) {
    // X25519: derive public key from private key
    if (key_out->key.size < 32 || key_in->key.size < 32) {
      return false;
    }
    curve25519_get_public_key(key_out->key.bytes, key_in->key.bytes);
    key_out->alg = ALG_ECC_X25519;
    key_out->storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT;
    return true;
  }

  // Other curves not yet implemented
  return false;
}

bool crypto_ecc_compute_shared_secret(key_handle_t* private_key, key_handle_t* public_key,
                                      key_handle_t* secret) {
  if (!private_key || !public_key || !secret || !secret->key.bytes) {
    return false;
  }

  // Only X25519 ECDH implemented
  if (private_key->alg != ALG_ECC_X25519 || public_key->alg != ALG_ECC_X25519) {
    return false;
  }

  if (private_key->key.size < 32 || public_key->key.size < 32 || secret->key.size < 32) {
    return false;
  }

  curve25519_get_shared_secret(secret->key.bytes, private_key->key.bytes, public_key->key.bytes);
  return true;
}

// crypto_hkdf now provided by lib/crypto/src/posix/hkdf.c

// Referenced from libgrant.a - must be a real function for linker
void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out) {
  static const uint8_t fake_chip_id[8] = {0x50, 0x4F, 0x53, 0x49, 0x58, 0x45, 0x4D, 0x55};
  if (chip_id_out) {
    memcpy(chip_id_out, fake_chip_id, sizeof(fake_chip_id));
  }
  if (length_out) {
    *length_out = sizeof(fake_chip_id);
  }
}

/* key_manager_task_port_handle_get_address and
 * key_manager_task_port_handle_verify_keys_and_build_descriptor are provided
 * by the W1 hardware port, which is compiled into core-sim. */
