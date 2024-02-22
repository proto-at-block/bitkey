#pragma once

#include "aes.h"
#include "key_management.h"

#define KEY_CONFIRMATION_TAG_LEN        (16)
#define CRYPTO_KEY_EXCHANGE_PUBKEY_SIZE (32)

typedef struct {
  // Peer's public key.
  uint8_t* pk_peer;      // [in]
  uint32_t pk_peer_len;  // [in]

  // Our public key.
  uint8_t* pk_us;      // [out]
  uint32_t pk_us_len;  // [in]

  // Signature over a label, our public key, and the peer's public key.
  uint8_t* exchange_sig;      // [out]
  uint32_t exchange_sig_len;  // [in]

  // Key confirmation tag.
  uint8_t* key_confirmation_tag;      // [out]
  uint32_t key_confirmation_tag_len;  // [in]
} crypto_key_exchange_ctx_t;

// Both keys must point to a 32-byte buffer.
typedef struct {
  // Key for sending to the peer; i.e. encrypting.
  uint8_t* send_key;
  // Key for receiving from the peer; i.e. decrypting.
  uint8_t* recv_key;
  // Key for key confirmation, using HMAC (truncated to 16 bytes).
  uint8_t* conf_key;
} crypto_key_exchange_derived_key_material_t;

// One-way authenticated ECDH using X25519.
//   * Derived keys is HKDF'd with a fixed label.
//   * Key confirmation using HMAC with a fixed label.
//   * Derives separate keys for sending and receiving, to protect against reflection.
//
// This is essentially SIGMA (https://www.iacr.org/cryptodb/archive/2003/CRYPTO/1495/1495.pdf)
// See section 5.1. However, the other party can't authenticate to us.
bool crypto_key_exchange(crypto_key_exchange_ctx_t* ctx,
                         crypto_key_exchange_derived_key_material_t* keys);
