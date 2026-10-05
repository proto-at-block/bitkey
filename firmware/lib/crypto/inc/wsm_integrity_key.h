#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * WSM (Wallet Security Module) Integrity Key
 *
 * Used for verifying signatures from the WSM server. The same key is used by:
 * - grant_protocol: verify grants for sensitive actions (e.g., fingerprint reset)
 * - key_manager: verify key provisioning during onboarding
 *
 * The test key is used for development/testing environments.
 * The prod key is used for production environments.
 */

#define WSM_INTEGRITY_PUBKEY_SIZE (33u)

/** WSM integrity public key selected at build time by CONFIG_PROD. */
extern const uint8_t WSM_INTEGRITY_PUBKEY[WSM_INTEGRITY_PUBKEY_SIZE];

/**
 * Verify a WSM signature over a message
 *
 * Automatically selects the correct WSM integrity key (prod or test) based on CONFIG_PROD.
 *
 * @param message The message that was signed
 * @param message_len Length of the message
 * @param signature The signature to verify (must be 64 bytes)
 * @return true if signature is valid, false otherwise
 */
bool wsm_verify_signature(const uint8_t* message, size_t message_len, const uint8_t* signature);
