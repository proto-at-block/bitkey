#pragma once

#include <secutils.h>
#include <stdbool.h>

// Returns SECURE_TRUE if there is at least one fingerprint enrolled or the wallet is initialized.
// Non-production builds also consider a provisioned unlock secret.
secure_bool_t onboarding_complete(void);

void onboarding_wipe_state(void);
