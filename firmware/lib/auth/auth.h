#pragma once

#include "secutils.h"

#include <stdbool.h>

// Return whether the device has been authenticated to, and is 'unlocked' (true)
// or 'locked' (false).
secure_bool_t is_authenticated(void);

// Returns if a grant has been presented that allows fingerprint enrollment.
secure_bool_t is_allowing_fingerprint_enrollment(void);

void present_grant_for_fingerprint_enrollment(void);

void deauthenticate(void);
void deauthenticate_without_animation(void);

// Refresh the auth timer if the device is already authenticated.
// Returns the auth status.
secure_bool_t refresh_auth(void);
