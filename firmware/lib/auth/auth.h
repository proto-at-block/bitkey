#pragma once

#include "secutils.h"

#include <stdbool.h>

// Return whether the device has been authenticated to, and is 'unlocked' (true)
// or 'locked' (false).
secure_bool_t is_authenticated(void);

void deauthenticate(void);
void deauthenticate_without_animation(void);

// Refresh the auth timer if the device is already authenticated.
// Returns the auth status.
secure_bool_t refresh_auth(void);
