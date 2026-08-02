/**
 * @file sleep.h
 * @brief POSIX passthrough for sleep.h.
 *
 * The real lib/sleep/sleep.h is hardware-independent and lib/sleep is linked
 * into core-sim (see sleep_dep), so use the canonical header directly instead
 * of maintaining a drifting copy.
 */

#pragma once

#include "../../sleep/sleep.h"
