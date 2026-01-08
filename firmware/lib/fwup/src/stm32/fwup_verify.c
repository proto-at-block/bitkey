#include "assert.h"
#include "attributes.h"
#include "bl_secureboot.h"
#include "ecc.h"
#include "fwup_addr.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "fwup_verify_impl.h"
#include "log.h"

#include <inttypes.h>
#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

extern fwup_priv_t fwup_priv;

extern int __FLASH_segment_start__;
extern int __FLASH_segment_end__;

static const uintptr_t flash_start_addr = (uintptr_t)&__FLASH_segment_start__;
static const uintptr_t flash_end_addr = (uintptr_t)&__FLASH_segment_end__;

static bool _fwup_verify_is_address_in_flash(const uintptr_t addr, const size_t length) {
  if (addr == 0u) {
    // Invalid address.
    return false;
  }

  if (addr < flash_start_addr) {
    return false;
  }

  if ((addr > flash_end_addr) || (length > (flash_end_addr - addr))) {
    return false;
  }

  return true;
}

static bool _fwup_verify_is_valid_app_properties(const app_properties_t* props) {
  if (props == NULL) {
    return false;
  }

  if ((props->structVersion == 0u) || (props->structVersion == UINT32_MAX)) {
    return false;
  }

  const uint8_t magic[] = PICO_CERT_APP_PROPERTIES_MAGIC;
  if (memcmp(magic, props->magic, sizeof(magic)) != 0) {
    // Magic string does not match.
    return false;
  }

  const uintptr_t cert_addr = (uintptr_t)props->cert;
  if (!_fwup_verify_is_address_in_flash(cert_addr, sizeof(app_certificate_t))) {
    // Certificate is invalid.
    return false;
  }

  if (cert_addr <= (uintptr_t)props) {
    // Certificate address is in flash, but in the wrong place; must be after
    // the application properties.
    return false;
  }

  if ((cert_addr & (sizeof(uint32_t) - 1u)) != 0u) {
    // Address must be aligned on a word boundary.
    return false;
  }

  return true;
}

static bool _fwup_verify_locate_app_properties(const uint8_t* region_base, size_t region_size,
                                               app_properties_t** props_out) {
  if ((region_base == NULL) || (props_out == NULL) || (region_size < sizeof(app_properties_t))) {
    return false;
  }

  // Iterate through the flash region to find a candidate offset for the
  // application properties.
  for (uintptr_t offset = 0u; offset <= (region_size - sizeof(app_properties_t));
       offset += sizeof(uint32_t)) {
    const app_properties_t* candidate = (const app_properties_t*)(region_base + offset);
    if (_fwup_verify_is_valid_app_properties(candidate)) {
      *props_out = (app_properties_t*)candidate;
      return true;
    }
  }

  return false;
}

static NO_OPTIMIZE fwup_verify_status_t _fwup_verify_app_signature(app_certificate_t* bl_cert,
                                                                   app_properties_t* app_props,
                                                                   uint8_t* app_base,
                                                                   size_t app_size,
                                                                   uint8_t* signature) {
  if ((bl_cert == NULL) || (app_props == NULL) || (app_base == NULL) || (signature == NULL)) {
    // Invalid argument.
    return FWUP_VERIFY_ERROR;
  }

  volatile secure_bool_t verified =
    bl_verify_app_slot(bl_cert, app_props, app_base, app_size, signature);

  SECURE_IF_FAILIN(verified != SECURE_TRUE) { return FWUP_VERIFY_SIGNATURE_INVALID; }
  return FWUP_VERIFY_SUCCESS;
}

fwup_verify_status_t fwup_verify_new_app(uintptr_t app_properties_offset,
                                         uintptr_t signature_offset) {
  if ((fwup_priv.target_slot_addr == NULL) || (fwup_priv.current_slot_addr == NULL)) {
    LOGE("FWUP not initialised");
    return FWUP_VERIFY_ERROR;
  }

  if (app_properties_offset > (fwup_priv.app_slot_size - sizeof(app_properties_t)) ||
      signature_offset > (fwup_priv.app_slot_size - ECC_SIG_SIZE)) {
    LOGE("Bad offset (props=%zu, sig=%zu)", app_properties_offset, signature_offset);
    return FWUP_VERIFY_BAD_OFFSET;
  }

  uint8_t* target_slot = (uint8_t*)fwup_priv.target_slot_addr;
  const uint8_t* current_slot = (uint8_t*)fwup_priv.current_slot_addr;

  app_properties_t* new_props = (app_properties_t*)(target_slot + app_properties_offset);
  if (!_fwup_verify_is_valid_app_properties(new_props)) {
    LOGE("New app properties invalid");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  app_properties_t* current_props = NULL;
  if (!_fwup_verify_locate_app_properties(current_slot, fwup_priv.app_slot_size, &current_props)) {
    LOGE("Failed to locate current app properties");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  if (new_props->app.version <= current_props->app.version) {
    LOGE("App version downgrade detected (%" PRIu32 " <= %" PRIu32 ")", new_props->app.version,
         current_props->app.version);
    return FWUP_VERIFY_VERSION_INVALID;
  }

  app_properties_t* bl_props = NULL;
  const uint8_t* bl_base = (uint8_t*)fwup_bl_address();
  const size_t bl_size = fwup_bl_size();
  if (!_fwup_verify_locate_app_properties(bl_base, bl_size, &bl_props)) {
    LOGE("Failed to locate bootloader properties");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  // Validate the application signature against the bootloader certificate.
  uint8_t* signature = target_slot + signature_offset;
  fwup_verify_status_t status = _fwup_verify_app_signature(
    bl_props->cert, new_props, target_slot, fwup_priv.app_slot_size - ECC_SIG_SIZE, signature);
  if (status != FWUP_VERIFY_SUCCESS) {
    LOGE("App signature invalid");
    return status;
  }

  return FWUP_VERIFY_SUCCESS;
}

fwup_verify_status_t fwup_verify_new_bootloader(void) {
  LOGE("Bootloader FWUP not supported");
  return FWUP_VERIFY_ERROR;
}
