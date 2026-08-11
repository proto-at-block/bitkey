/**
 * @file bitlog.h
 * @brief POSIX passthrough for bitlog.h.
 *
 * The real lib/bitlog is linked into core-sim and its platform header
 * (lib/bitlog/inc/bitlog_platform.h) already supports x86_64/arm64, so use
 * the canonical header directly instead of maintaining a drifting copy.
 * Consumers need the Memfault SDK + event-def include paths (see
 * core-sim's meson.build).
 */

#pragma once

#include "../../bitlog/inc/bitlog.h"
