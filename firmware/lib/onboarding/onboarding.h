#pragma once

#include <secutils.h>
#include <stdbool.h>

// Returns SECURE_TRUE if there is at least one fingerprint enrolled, OR if
// an unlock secret is provisioned.
secure_bool_t onboarding_auth_is_setup(void);

void onboarding_wipe_state(void);
