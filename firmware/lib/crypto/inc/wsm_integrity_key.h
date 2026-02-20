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

// WSM Integrity Key for test/dev environment
extern const uint8_t WSM_INTEGRITY_TEST_PUBKEY[33];

// WSM Integrity Key for production environment
extern const uint8_t WSM_INTEGRITY_PROD_PUBKEY[33];

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
