#pragma once

typedef enum {
  HWREV_UNKNOWN,
  HWREV_PROTO,
  HWREV_EVT,
  HWREV_DVT,
} platform_hwrev_t;

// This must come after the definition of platform_hwrev_t to avoid pre-compiler errors
#ifdef CFG_PROTO
#define PLATFORM_HW_REV HWREV_PROTO
#elif defined CFG_EVT
#define PLATFORM_HW_REV HWREV_EVT
#elif defined CFG_DVT
#define PLATFORM_HW_REV HWREV_DVT
#else
#define PLATFORM_HW_REV HWREV_UNKNOWN
#endif

// MCU Configuration
#define PLATFORM_CFG_MCU_UART_CNT 1u

// NFC Configuration
#define PLATFORM_CFG_NFC_LISTENER_MODE  1u
#define PLATFORM_CFG_NFC_TYPE_A_SUPPORT 1u

// Task Stack Sizes
#define PLATFORM_CFG_SHELL_TASK_STACK_SIZE   8192
#define PLATFORM_CFG_SYSINFO_TASK_STACK_SIZE 2048

// LittleFS Configuration
#define PLATFORM_CFG_LFS_READ_SIZE  8192
#define PLATFORM_CFG_LFS_PROG_SIZE  8192
#define PLATFORM_CFG_LFS_CACHE_SIZE 8192

// FWUP delta patch configuration. EFR32 mfgtest images have additional
// headroom for larger patches.
#ifdef MFGTEST
#define PLATFORM_CFG_FWUP_DELTA_MAX_PATCH_SIZE (168 * 1024)
#else
#define PLATFORM_CFG_FWUP_DELTA_MAX_PATCH_SIZE (120 * 1024)
#endif

// Auth Configuration
#define PLATFORM_CFG_AUTH_EXPIRY_MS (60000)

// Relock the device on a failed fingerprint match, even if already unlocked.
// W1 has no screen-based lock mechanism, so this is the only way to relock.
#define PLATFORM_CFG_RELOCK_ON_FP_MISMATCH 1u
