#pragma once

#include <secutils.h>
#include <stdbool.h>

// Returns SECURE_TRUE if there is at least one fingerprint enrolled, OR if
// an unlock secret is provisioned, OR if the wallet is initialized.
secure_bool_t onboarding_complete(void);

void onboarding_wipe_state(void);
