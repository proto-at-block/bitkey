/**
 * @file kv.h
 * @brief POSIX passthrough for kv.h.
 *
 * The real lib/kv is linked into core-sim (it only needs the filesystem),
 * so use the canonical header directly instead of maintaining a drifting
 * copy.
 */

#pragma once

#include "../../kv/kv.h"
