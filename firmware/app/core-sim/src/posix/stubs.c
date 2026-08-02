/**
 * @file stubs.c
 * @brief Core POSIX stubs for firmware dependencies
 *
 * Provides stub implementations for:
 * - Block device operations (FFF fakes)
 * - Security configuration and application properties
 * - Secutils initialization (secure random, glitch detection)
 * - Bitlog initialization
 * - Serial number generation
 */

#include "application_properties.h"
#include "bitlog.h"
#include "fff.h"
#include "security_config.h"
#include "secutils.h"
#include "sysinfo.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// Required by the embedded printf library (third-party/printf).
// On macOS the symbol is satisfied at runtime via -undefined,dynamic_lookup;
// on Linux the linker requires a concrete definition.
// Redirect to stderr so stdout stays clean for the binary protocol.
void _putchar(char c) {
  fputc(c, stderr);
}

DEFINE_FFF_GLOBALS;

// Forward declaration for LittleFS type (we don't need the full definition for stubs)
struct lfs;
typedef struct lfs lfs_t;

// Block device stubs
FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);

// RTOS primitives implemented with real pthread semantics in rtos_posix.c

security_config_t security_config = {0};
// NOTE: _fs_mount_task_regions is defined in lib/filesystem/filesystem_mpu.c
// Do not define it here to avoid duplicate/conflicting symbol definitions

const ApplicationCertificate_t app_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0U},
  .key = {0U},
  .version = 0,
  .signature = {0U},
};

const uint32_t app_properties_version = 0;

#define STDIO_SERVER_APP_PROPERTIES_ID \
  { 0 }

__attribute__((used)) ApplicationProperties_t sl_app_properties = {
  .magic = APPLICATION_PROPERTIES_MAGIC,
  .structVersion = APPLICATION_PROPERTIES_VERSION,
  .signatureType = APPLICATION_SIGNATURE_ECDSA_P256,
  .signatureLocation = 0,
  .app =
    {
      .type = APPLICATION_TYPE_MCU,
      .version = 0,
      .capabilities = 0,
      .productId = STDIO_SERVER_APP_PROPERTIES_ID,
    },
  .cert = (ApplicationCertificate_t*)&app_certificate,
};

__attribute__((used)) uint8_t app_codesigning_signature[64] = {0};

static void posix_detect_glitch(void) {}

static uint16_t posix_secure_random(void) {
  uint16_t val;
  FILE* f = fopen("/dev/urandom", "rb");
  if (f) {
    const bool read_ok = fread(&val, sizeof(val), 1, f) == 1;
    fclose(f);
    if (read_ok) {
      return val;
    }
  }
  // Fallback: not cryptographically secure, but works for testing
  return (uint16_t)(rand() & 0xFFFF);
}

static uint32_t posix_cpu_freq(void) {
  return 1000000000;
}

static bool secutils_initialized = false;

void init_secutils_if_needed(void) {
  if (!secutils_initialized) {
    secutils_api_t api = {
      .detect_glitch = posix_detect_glitch,
      .secure_random = posix_secure_random,
      .cpu_freq = posix_cpu_freq,
    };
    secutils_init(api);
    secutils_initialized = true;
  }
}

static uint32_t posix_bitlog_timestamp(void) {
  static uint32_t counter = 0;
  return counter++;
}

static bool bitlog_initialized = false;

void init_bitlog_if_needed(void) {
  if (!bitlog_initialized) {
    bitlog_api_t api = {
      .timestamp_cb = posix_bitlog_timestamp,
    };
    bitlog_init(api);
    bitlog_initialized = true;
  }
}

bool sysinfo_mlb_serial_read(char* serial_out, uint32_t* length_out) {
  snprintf(serial_out, SYSINFO_SERIAL_NUMBER_LENGTH + 1, "POSIX-MLB-%06d", getpid() % 1000000);
  *length_out = SYSINFO_SERIAL_NUMBER_LENGTH;
  return true;
}

bool sysinfo_assy_serial_read(char* serial_out, uint32_t* length_out) {
  snprintf(serial_out, SYSINFO_SERIAL_NUMBER_LENGTH + 1, "POSIXASSY-%06d", getpid() % 1000000);
  *length_out = SYSINFO_SERIAL_NUMBER_LENGTH;
  return true;
}

/* Fixed placeholder system info for the simulator. */
static char _sysinfo_sw_type[SYSINFO_SOFTWARE_TYPE_MAX_LENGTH] = "app-a-dev";
static char _sysinfo_version[SYSINFO_VERSION_MAX_LENGTH] = "1.0.0";
#ifdef CORE_SIM_HW_REVISION
static char _sysinfo_hwrev[SYSINFO_HARDWARE_REVISION_MAX_LENGTH] = CORE_SIM_HW_REVISION;
#else
static char _sysinfo_hwrev[SYSINFO_HARDWARE_REVISION_MAX_LENGTH] = "evt";
#endif

static sysinfo_t posix_sysinfo = {
  .serial = "POSIX-EMULATOR0",
  .software_type = _sysinfo_sw_type,
  .version_string = _sysinfo_version,
  .hardware_revision = _sysinfo_hwrev,
};

bool sysinfo_load(void) {
  return true;
}

sysinfo_t* sysinfo_get(void) {
  return &posix_sysinfo;
}

void sysinfo_set_serial(const char* serial, uint32_t length) {
  if (serial == NULL || length != SYSINFO_SERIAL_NUMBER_LENGTH) {
    return;
  }
  memcpy(posix_sysinfo.serial, serial, SYSINFO_SERIAL_NUMBER_LENGTH);
  posix_sysinfo.serial[SYSINFO_SERIAL_NUMBER_LENGTH] = '\0';
}

void sysinfo_chip_info_read(uint8_t* buffer, uint32_t size) {
  if (buffer && size > 0) {
    memset(buffer, 0, size);
  }
}
