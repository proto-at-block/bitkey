#pragma once

#include <stdbool.h>

// Storage-only functions (no FPC dependencies)
bool bio_fingerprint_exists(void);
void bio_wipe_state(void);
