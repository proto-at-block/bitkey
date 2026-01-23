#pragma once

#include "secutils.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Unlock method used for authentication.
 * @note Values match wallet.proto unlock_method enum.
 */
typedef enum {
  AUTH_METHOD_UNSPECIFIED = 0,
  AUTH_METHOD_BIOMETRICS = 1,
  AUTH_METHOD_UNLOCK_SECRET = 2,
} auth_unlock_method_t;

/** @brief Invalid fingerprint index (no fingerprint used for auth). */
#define AUTH_INVALID_FINGERPRINT_INDEX (0xFF)

/**
 * @brief Callbacks for auth state changes.
 */
typedef struct {
  void (*on_lock)(bool show_animation);
} auth_callbacks_t;

/**
 * @brief Configuration for auth library.
 */
typedef struct {
  uint32_t expiry_ms; /**< Auth timeout in milliseconds (e.g., 60000 for 1 minute). */
} auth_config_t;

/**
 * @brief Initialize the auth library.
 * @param config Configuration settings.
 * @param callbacks Callback functions for auth events.
 * @note Must be called before any other auth functions.
 */
void auth_init(auth_config_t config, auth_callbacks_t callbacks);

/**
 * @brief Check if the device is currently authenticated.
 * @return SECURE_TRUE if authenticated, SECURE_FALSE otherwise.
 */
secure_bool_t is_authenticated(void);

/**
 * @brief Check if fingerprint enrollment is currently allowed.
 * @return SECURE_TRUE if enrollment is allowed, SECURE_FALSE otherwise.
 */
secure_bool_t is_allowing_fingerprint_enrollment(void);

/**
 * @brief Authenticate via biometrics (fingerprint match).
 * @param fingerprint_index Index of the matched fingerprint template.
 */
void auth_authenticate_biometrics(uint8_t fingerprint_index);

/**
 * @brief Authenticate via unlock secret (PIN/password).
 */
void auth_authenticate_unlock_secret(void);

/**
 * @brief Deauthenticate (lock) the device with lock animation.
 */
void deauthenticate(void);

/**
 * @brief Deauthenticate (lock) the device without showing animation.
 */
void deauthenticate_without_animation(void);

/**
 * @brief Refresh the auth timer if the device is already authenticated.
 * @return Current auth status (SECURE_TRUE if authenticated).
 */
secure_bool_t refresh_auth(void);

/**
 * @brief Grant temporary permission for fingerprint enrollment.
 */
void present_grant_for_fingerprint_enrollment(void);

/**
 * @brief Get the method used for the current authentication.
 * @return The unlock method (biometrics, unlock secret, or unspecified).
 */
auth_unlock_method_t auth_get_unlock_method(void);

/**
 * @brief Get the fingerprint index used for authentication.
 * @return Fingerprint template index, or AUTH_INVALID_FINGERPRINT_INDEX if not applicable.
 */
uint8_t auth_get_fingerprint_index(void);
