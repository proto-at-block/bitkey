/**
 * @file feature_flags.h
 * @brief POSIX passthrough for feature_flags.h.
 *
 * The canonical header is hardware-independent. POSIX function
 * implementations (RAM-backed, no persistence) live in
 * app/core-sim/src/posix/task_stubs.c.
 */

#pragma once

#include "../../feature-flags/feature_flags.h"
