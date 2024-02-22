#pragma once

#include "bip32.h"
#include "wallet.h"

// All keys stored in flash are wrapped by the secure engine (or by another SE-backed key).
#define WALLET_ACTIVE_AUTH_KEY_PATH     ("encrypted-auth-key-active.bin")
#define WALLET_ACTIVE_CONFIG_KEY_PATH   ("encrypted-config-key-active.bin")
#define WALLET_ACTIVE_SPEND_KEY_PATH    ("encrypted-spend-key-active.bin")
#define WALLET_RECOVERY_AUTH_KEY_PATH   ("encrypted-auth-key-recovery.bin")
#define WALLET_RECOVERY_CONFIG_KEY_PATH ("encrypted-config-key-recovery.bin")
#define WALLET_RECOVERY_SPEND_KEY_PATH  ("encrypted-spend-key-recovery.bin")
#define WALLET_INACTIVE_AUTH_KEY_PATH   ("encrypted-auth-key-inactive.bin")
#define WALLET_INACTIVE_CONFIG_KEY_PATH ("encrypted-config-key-inactive.bin")
#define WALLET_INACTIVE_SPEND_KEY_PATH  ("encrypted-spend-key-inactive.bin")

// Key bundle keys
bool wallet_keys_exist(const wallet_key_bundle_type_t type);
bool wallet_key_encrypt_and_store(const wallet_key_bundle_type_t type,
                                  const wallet_key_domain_t domain,
                                  extended_key_t* plaintext_master_key);
bool wallet_key_load(const wallet_key_bundle_type_t type, const wallet_key_domain_t domain,
                     extended_key_t* plaintext_key_out);
