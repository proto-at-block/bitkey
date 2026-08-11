#pragma once

// POSIX W1 simulator platform configuration.
// Reuses the host platform config and layers on the
// W1-specific values that firmware task code requires (kept in sync with
// config/platform/w1/platform.h).

#ifdef __APPLE__
#include "../darwin/platform.h"
#else
#include "../linux/platform.h"
#endif

// Task Stack Sizes
#define PLATFORM_CFG_SYSINFO_TASK_STACK_SIZE 2048

// Auth Configuration
#define PLATFORM_CFG_AUTH_EXPIRY_MS (60000)
