#include "bip32.h"

#include "assert.h"
#include "bitops.h"
#include "ecc.h"
#include "hash.h"
#include "hex.h"
#include "secp256k1.h"
#include "secutils.h"

#include <string.h>

#define KEY_IDENTIFIER_SIZE (HASH160_DIGEST_SIZE)

static bool all_zeroes(uint8_t* buf, uint32_t size) {
  for (size_t i = 0; i < size; i++) {
    if (buf[i] != 0)
      return false;
  }
  return true;
}

static bool compute_identifier(uint8_t* sec_encoded_pubkey, uint8_t* identifier,
                               uint32_t identifier_size) {
  return crypto_hash(sec_encoded_pubkey, BIP32_SEC1_KEY_SIZE, identifier, identifier_size,
                     ALG_HASH160);
}

static bool ckd_priv_unhardened(extended_key_t* ext_priv_key, uint32_t index) {
  // let I = HMAC-SHA512(Key = c_par, Data = ser_p(point(k_par)) || ser32(i))
  uint8_t data[BIP32_SEC1_KEY_SIZE + sizeof(uint32_t)] = {0};

  if (!crypto_ecc_secp256k1_priv_to_sec_encoded_pub(ext_priv_key->key, data)) {
    return false;
  }

  uint32_t be_index = htonl(index);
  memcpy(&data[BIP32_SEC1_KEY_SIZE], (uint8_t*)&be_index, sizeof(be_index));

  key_handle_t hmac_key = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = ext_priv_key->chaincode,
    .key.size = BIP32_CHAINCODE_SIZE,
  };

  uint8_t digest[SHA512_DIGEST_SIZE];
  if (!crypto_hmac(data, sizeof(data), &hmac_key, digest, sizeof(digest), ALG_SHA512)) {
    return false;
  }

  if (!crypto_ecc_secp256k1_priv_tweak_add(ext_priv_key->key, digest)) {
    return false;
  }

  memcpy(ext_priv_key->chaincode, &digest[BIP32_KEY_SIZE], BIP32_CHAINCODE_SIZE);

  return true;
}

static bool ckd_priv_hardened(extended_key_t* ext_priv_key, uint32_t index) {
  // let I = HMAC-SHA512(Key = c_par, Data = 0x00 || ser256(k_par) || ser32(i))
  ASSERT(index & BIP32_HARDENED_BIT);

  uint8_t data[BIP32_SEC1_KEY_SIZE + sizeof(uint32_t)];
  ASSERT(ext_priv_key->prefix == BIP32_PRIVKEY_PREFIX);
  memcpy(data, (uint8_t*)ext_priv_key, BIP32_SEC1_KEY_SIZE);
  uint32_t be_index = htonl(index);
  memcpy(&data[BIP32_SEC1_KEY_SIZE], (uint8_t*)&be_index, sizeof(be_index));

  key_handle_t hmac_key = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = ext_priv_key->chaincode,
    .key.size = BIP32_CHAINCODE_SIZE,
  };

  uint8_t digest[SHA512_DIGEST_SIZE];
  if (!crypto_hmac(data, sizeof(data), &hmac_key, digest, sizeof(digest), ALG_SHA512)) {
    return false;
  }

  if (!crypto_ecc_secp256k1_priv_tweak_add(ext_priv_key->key, digest)) {
    return false;
  }

  memcpy(ext_priv_key->chaincode, &digest[BIP32_KEY_SIZE], BIP32_CHAINCODE_SIZE);

  return true;
}

static bool ckd_pub(extended_key_t* ext_pub_key, uint32_t index) {
  // let I = HMAC-SHA512(Key = cpar, Data = serP(Kpar) || ser32(i))

  uint8_t data[BIP32_SEC1_KEY_SIZE + sizeof(uint32_t)];
  ASSERT(ext_pub_key->prefix == SEC1_COMPRESSED_PUBKEY_EVEN ||
         ext_pub_key->prefix == SEC1_COMPRESSED_PUBKEY_ODD);
  memcpy(data, (uint8_t*)ext_pub_key, BIP32_SEC1_KEY_SIZE);
  uint32_t be_index = htonl(index);
  memcpy(&data[BIP32_SEC1_KEY_SIZE], (uint8_t*)&be_index, sizeof(be_index));

  key_handle_t hmac_key = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = ext_pub_key->chaincode,
    .key.size = BIP32_CHAINCODE_SIZE,
  };

  uint8_t digest[SHA512_DIGEST_SIZE];
  if (!crypto_hmac(data, sizeof(data), &hmac_key, digest, sizeof(digest), ALG_SHA512)) {
    return false;
  }

  if (!crypto_ecc_secp256k1_pub_tweak_add((uint8_t*)ext_pub_key, digest)) {
    return false;
  }

  memcpy(ext_pub_key->chaincode, &digest[BIP32_KEY_SIZE], BIP32_CHAINCODE_SIZE);

  return true;
}

bool bip32_derive_master_key(const uint8_t* seed, uint32_t seed_length,
                             extended_key_t* master_key_out) {
  ASSERT(seed && master_key_out);
  ASSERT(seed_length >= (128 / 8) &&
         seed_length <= (512 / 8));  // "between 128 and 512 bits; 256 bits is advised"

  const char hmac_key_bytes[12] = "Bitcoin seed";
  key_handle_t hmac_key = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)hmac_key_bytes,
    .key.size = sizeof(hmac_key_bytes),
  };

  _Static_assert(
    sizeof(master_key_out->key) + sizeof(master_key_out->chaincode) == SHA512_DIGEST_SIZE,
    "key + chaincode must be 512 bits");

  if (!crypto_hmac(seed, seed_length, &hmac_key, master_key_out->key, SHA512_DIGEST_SIZE,
                   ALG_SHA512)) {
    return false;
  }

  _Static_assert(sizeof(master_key_out->key) == SECP256K1_KEY_SIZE,
                 "master_key_out must be 256 bits");

  if (!crypto_ecc_secp256k1_priv_verify(master_key_out->key)) {
    return false;
  }

  master_key_out->prefix = BIP32_PRIVKEY_PREFIX;

  return true;
}

bool bip32_derive_path_priv(extended_key_t* priv_parent, extended_key_t* priv_child_out,
                            uint8_t childs_parent_fingerprint_out[BIP32_KEY_FINGERPRINT_SIZE],
                            const derivation_path_t* path) {
  ASSERT(priv_parent && priv_child_out && childs_parent_fingerprint_out && path);
  ASSERT(path->num_indices <= BIP32_MAX_DERIVATION_DEPTH);
  ASSERT(path->indices);

  bool status = false;

  memcpy(priv_child_out, priv_parent, sizeof(extended_key_t));
  memzero(childs_parent_fingerprint_out, BIP32_KEY_FINGERPRINT_SIZE);

  for (uint32_t i = 0; i < path->num_indices; i++) {
    uint32_t index = path->indices[i];

    // Convert current child, who will be the NEXT child's parent, to
    // public key to compute the fingerprint.
    if (!bip32_compute_fingerprint(priv_child_out, childs_parent_fingerprint_out)) {
      goto out;
    }

    // CKDpriv((k_par, c_par), i) → (k_i, c_i)
    if (index & BIP32_HARDENED_BIT) {
      if (!ckd_priv_hardened(priv_child_out, index)) {
        goto out;
      }
    } else {
      if (!ckd_priv_unhardened(priv_child_out, index)) {
        goto out;
      }
    }
  }

  status = true;

out:
  return status;
}

bool bip32_derive_path_pub(extended_key_t* pub_parent, extended_key_t* pub_child_out,
                           derivation_path_t* path) {
  ASSERT(pub_parent && pub_child_out && path);
  ASSERT(path->num_indices <= BIP32_MAX_DERIVATION_DEPTH);
  ASSERT(path->indices);

  bool status = false;

  memcpy(pub_child_out, pub_parent, sizeof(extended_key_t));

  for (uint32_t i = 0; i < path->num_indices; i++) {
    uint32_t index = path->indices[i];
    if (index & BIP32_HARDENED_BIT) {
      goto out;
    }
    if (!ckd_pub(pub_child_out, index)) {
      goto out;
    }
  }

  status = true;

out:
  return status;
}

bool bip32_priv_to_pub(extended_key_t* priv_in, extended_key_t* pub_out) {
  ASSERT(priv_in && pub_out);
  ASSERT(priv_in->prefix == BIP32_PRIVKEY_PREFIX);

  if (!crypto_ecc_secp256k1_priv_to_sec_encoded_pub(priv_in->key, (uint8_t*)pub_out)) {
    return false;
  }

  memcpy(pub_out->chaincode, priv_in->chaincode, BIP32_CHAINCODE_SIZE);

  return true;
}

bool bip32_compute_fingerprint(extended_key_t* key,
                               uint8_t fingerprint[BIP32_KEY_FINGERPRINT_SIZE]) {
  ASSERT(key && fingerprint);

  const extended_key_t* pub_key;
  extended_key_t pub_key_from_priv CLEANUP(bip32_zero_key) = {0};

  switch (key->prefix) {
    case BIP32_PRIVKEY_PREFIX:
      if (!bip32_priv_to_pub(key, &pub_key_from_priv)) {
        return false;
      }
      pub_key = &pub_key_from_priv;
      break;
    case SEC1_COMPRESSED_PUBKEY_EVEN:
    case SEC1_COMPRESSED_PUBKEY_ODD:
      pub_key = key;
      break;
    default:
      return false;
  }

  uint8_t identifier[KEY_IDENTIFIER_SIZE];
  if (!compute_identifier((uint8_t*)pub_key, identifier, sizeof(identifier))) {
    return false;
  }

  memcpy(fingerprint, identifier, BIP32_KEY_FINGERPRINT_SIZE);
  return true;
}

bool bip32_fingerprint_for_path(extended_key_t* priv_key, derivation_path_t* path,
                                uint8_t fingerprint[BIP32_KEY_FINGERPRINT_SIZE]) {
  ASSERT(priv_key && path && fingerprint);
  extended_key_t priv_child;
  if (!bip32_derive_path_priv(priv_key, &priv_child, fingerprint, path)) {
    memzero(fingerprint, BIP32_KEY_FINGERPRINT_SIZE);
    return false;
  }
  return bip32_compute_fingerprint(&priv_child, fingerprint);
}

bool bip32_serialize_ext_key(extended_key_t* ext_key, extended_key_t* parent_pub_key,
                             uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE],
                             version_bytes_t version, uint32_t child_num, uint8_t depth,
                             uint8_t* serialized, uint32_t serialized_size) {
  ASSERT(ext_key && serialized);
  ASSERT(serialized_size == BIP32_SERIALIZED_EXT_KEY_SIZE);

  memzero(serialized, serialized_size);

  // Check version bytes.
  uint32_t be_version;
  bool is_pub = false;
  switch (version) {
    case MAINNET_PUB:
      be_version = htonl(MAINNET_PUB);
      is_pub = true;
      break;
    case MAINNET_PRIV:
      be_version = htonl(MAINNET_PRIV);
      break;
    case TESTNET_PUB:
      be_version = htonl(TESTNET_PUB);
      is_pub = true;
      break;
    case TESTNET_PRIV:
      be_version = htonl(TESTNET_PRIV);
      break;
    default:
      ASSERT(false);  // Crash loudly for invalid version.
  }

  // Ensure consistency between version bytes and extended key prefix byte.
  if (is_pub) {
    ASSERT(ext_key->prefix == SEC1_COMPRESSED_PUBKEY_EVEN ||
           ext_key->prefix == SEC1_COMPRESSED_PUBKEY_ODD);
  } else {
    ASSERT(ext_key->prefix == BIP32_PRIVKEY_PREFIX);
  }

  // Chain code and key should not be zero.
  if (all_zeroes(ext_key->chaincode, BIP32_CHAINCODE_SIZE) ||
      all_zeroes(ext_key->key, BIP32_KEY_SIZE)) {
    return false;
  }

  uint32_t offset = 0;
  memcpy(&serialized[offset], (uint8_t*)&be_version, sizeof(be_version));
  offset += sizeof(be_version);

  serialized[offset] = depth;
  offset += sizeof(depth);

  if (depth != 0) {
    ASSERT(parent_pub_key || parent_fingerprint);
    if (parent_fingerprint) {
      memcpy(&serialized[offset], parent_fingerprint, BIP32_KEY_FINGERPRINT_SIZE);
    }
    if (parent_pub_key) {
      if (!bip32_compute_fingerprint(parent_pub_key, &serialized[offset])) {
        return false;
      }
      if (parent_fingerprint) {
        // If both are provided, they must match.
        ASSERT(memcmp(&serialized[offset], parent_fingerprint, BIP32_KEY_FINGERPRINT_SIZE) == 0);
      }
    }
  }
  offset += BIP32_KEY_FINGERPRINT_SIZE;

  uint32_t be_child_num = htonl(child_num);
  memcpy(&serialized[offset], (uint8_t*)&be_child_num, sizeof(be_child_num));
  offset += sizeof(be_child_num);

  memcpy(&serialized[offset], ext_key->chaincode, BIP32_CHAINCODE_SIZE);
  offset += BIP32_CHAINCODE_SIZE;

  serialized[offset++] = ext_key->prefix;
  memcpy(&serialized[offset], ext_key->key, BIP32_KEY_SIZE);
  offset += BIP32_KEY_SIZE;

  ASSERT(offset == serialized_size);
  return true;
}

bool bip32_sign(extended_key_t* priv_key, uint8_t digest[SHA256_DIGEST_SIZE],
                uint8_t signature_out[ECC_SIG_SIZE]) {
  uint8_t keypair_bytes[SECP256K1_KEYPAIR_SIZE] = {0};

  key_buffer_t key_buffer = {
    .bytes = keypair_bytes,
    .size = SECP256K1_KEYPAIR_SIZE,
  };

  key_handle_t key_handle CLEANUP(zeroize_key) = {
    .alg = ALG_ECC_SECP256K1,  // Not actually used, since this key is software only.
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key = key_buffer,
  };

  if (!crypto_ecc_secp256k1_load_keypair(priv_key->key, &key_handle)) {
    return false;
  }

  if (!crypto_ecc_secp256k1_ecdsa_sign_hash32(&key_handle, digest, signature_out, ECC_SIG_SIZE)) {
    memzero(signature_out, ECC_SIG_SIZE);  // Clear signature if signing failed.
    return false;
  }

  return true;
}

void bip32_zero_key(extended_key_t* const key) {
  memzero(key, sizeof(extended_key_t));
}
