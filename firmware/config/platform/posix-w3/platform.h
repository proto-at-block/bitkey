#pragma once

// POSIX W3 simulator platform configuration.
// Reuses the host platform config and layers on the
// W3-specific values that firmware task code requires (kept in sync with
// config/platform/w3-core/platform.h).

#ifdef __APPLE__
#include "../darwin/platform.h"
#else
#include "../linux/platform.h"
#endif

// Task Stack Sizes
#define PLATFORM_CFG_SYSINFO_TASK_STACK_SIZE 4096

// Auth Configuration
#define PLATFORM_CFG_AUTH_EXPIRY_MS (150000)
