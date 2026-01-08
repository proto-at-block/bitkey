#include "application_properties.h"
#include "attributes.h"
#include "bl_secureboot.h"
#include "ecc.h"
#include "fwup.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "fwup_verify_impl.h"
#include "hash.h"
#include "hex.h"
#include "log.h"
#include "perf.h"

#include <stddef.h>
#include <stdint.h>

#ifdef EMBEDDED_BUILD
extern char bl_slot_size[];
extern char bl_base_addr[];
extern char bl_signature_addr[];
#else
const size_t bl_slot_size = 48 * 1024;
const uintptr_t bl_base_addr = 0x08000000;
const uintptr_t bl_signature_addr = bl_base_addr + bl_slot_size - 64;
#endif

extern fwup_priv_t fwup_priv;

// App properties for the active slot.
extern ApplicationProperties_t sl_app_properties;

static bool locate_bl_sl_app_properties(uintptr_t bl_base_addr, size_t bl_size,
                                        uintptr_t* addr_out);
static fwup_verify_status_t verify_new_bootloader(ApplicationCertificate_t* cert, void* data,
                                                  size_t length, uint8_t* signature);

static NO_OPTIMIZE fwup_verify_status_t verify_new_bootloader(ApplicationCertificate_t* cert,
                                                              void* data, size_t length,
                                                              uint8_t* signature) {
  uint8_t hash[SHA256_DIGEST_SIZE] = {0};

  // Verify the cert is signed with secure boot OTP pubkey
  key_handle_t secure_boot_public_key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_INTERNAL_IMMUTABLE,
    .slot = SE_SECURE_BOOT_PUBKEY_SLOT,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  size_t hashable_cert_size = offsetof(ApplicationCertificate_t, signature);
  if (!crypto_hash((uint8_t*)cert, hashable_cert_size, hash, sizeof(hash), ALG_SHA256)) {
    return FWUP_VERIFY_SIGNATURE_INVALID;
  }

  secure_bool_t result =
    crypto_ecc_verify_hash(&secure_boot_public_key, hash, sizeof(hash), cert->signature);
  SECURE_IF_FAILIN(result != SECURE_TRUE) { return FWUP_VERIFY_SIGNATURE_INVALID; }

  // Verify the actual bootloader
  memset(hash, 0, sizeof(hash));
  if (!crypto_hash((uint8_t*)data, length, hash, sizeof(hash), ALG_SHA256)) {
    return FWUP_VERIFY_SIGNATURE_INVALID;
  }

  key_handle_t bl_key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)cert->key,
    .key.size = sizeof(cert->key),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  return (crypto_ecc_verify_hash(&bl_key, hash, SHA256_DIGEST_SIZE, (uint8_t*)signature) ==
          SECURE_TRUE)
           ? FWUP_VERIFY_SUCCESS
           : FWUP_VERIFY_SIGNATURE_INVALID;
}

NO_OPTIMIZE fwup_verify_status_t fwup_verify_new_app(uintptr_t app_properties_offset,
                                                     uintptr_t signature_offset) {
  if (app_properties_offset > (fwup_priv.app_slot_size - sizeof(ApplicationProperties_t)) ||
      signature_offset > (fwup_priv.app_slot_size - ECC_SIG_SIZE)) {
    LOGE("Bad offset (%zu, %zu)", app_properties_offset, signature_offset);
    return FWUP_VERIFY_BAD_OFFSET;
  }

#ifndef EMBEDDED_BUILD
  // The below code is not suitable for fuzzing (it's going to read from invalid memory).
  return FWUP_VERIFY_SUCCESS;
#endif

  const ApplicationProperties_t* current_slot_app_props = &sl_app_properties;
  const ApplicationProperties_t* new_app_props =
    (ApplicationProperties_t*)((uint8_t*)fwup_priv.target_slot_addr + app_properties_offset);

  LOGI("Verifying firmware update from version %ld to %ld", current_slot_app_props->app.version,
       new_app_props->app.version);

  // First, check the version to ensure no downgrades.
  SECURE_DO_FAILIN((new_app_props->app.version <= current_slot_app_props->app.version), {
    LOGE("Version invalid");
    return FWUP_VERIFY_VERSION_INVALID;
  });

  uintptr_t bl_sl_app_properties_addr = 0;
  if (!locate_bl_sl_app_properties((uintptr_t)bl_base_addr, (size_t)bl_slot_size,
                                   &bl_sl_app_properties_addr)) {
    LOGE("Failed to locate sl_app_properties in current bootloader");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  // Second, check the signature. While secure boot does protect us from booting into
  // invalid slots, checking the signature here allows us to inform that the FWUP failed.
  //
  // NOTE: This function is called `bl_verify_app_slot` because it's the same code we use
  // in the bootloader (hence the prefix). This function checks the application certificate
  // and verifies the image itself.
  ApplicationProperties_t* bl_props = (ApplicationProperties_t*)bl_sl_app_properties_addr;
  SECURE_DO_FAILOUT(
    bl_verify_app_slot(bl_props->cert, (ApplicationProperties_t*)new_app_props,
                       fwup_priv.target_slot_addr, fwup_priv.app_slot_size - ECC_SIG_SIZE,
                       (uint8_t*)fwup_priv.target_slot_addr + signature_offset) == SECURE_TRUE,
    { return FWUP_VERIFY_SUCCESS; });

  LOGE("Signature invalid");
  return FWUP_VERIFY_SIGNATURE_INVALID;
}

static bool locate_bl_sl_app_properties(uintptr_t bl_base_addr, size_t bl_size,
                                        uintptr_t* addr_out) {
  // The sl_app_properties in the *current* bootloader is not known to the app, and
  // must be located by searching for the magic value.
  for (uintptr_t addr = bl_base_addr; addr < (bl_base_addr + bl_size); addr += sizeof(uint32_t)) {
    // Grab the magic from *our own* sl_app_properties -- this is consistent across the BL and app
    // and should never change.
    if (memcmp((void*)addr, sl_app_properties.magic, sizeof(sl_app_properties.magic)) == 0) {
      *addr_out = addr;
      return true;
    }
  }

  LOGE("Failed to locate sl_app_properties in bootloader");
  return false;
}

NO_OPTIMIZE fwup_verify_status_t fwup_verify_new_bootloader(void) {
  const uintptr_t base_addr = (uintptr_t)bl_base_addr;
  const size_t slot_size = (size_t)bl_slot_size;

  // This is the address of the signature in the *inactive* slot.
  const uintptr_t sig_addr = (uintptr_t)fwup_priv.target_slot_addr + slot_size - ECC_SIG_SIZE;

  LOGD("Parameters: base_addr=%p, slot_size=%zu, sig_addr=%p", (void*)base_addr, slot_size,
       (void*)sig_addr);

  // Get current and new bootloader sl_app_properties, which is not at a fixed location.
  uintptr_t current_sl_app_properties_addr = 0;
  if (!locate_bl_sl_app_properties(base_addr, slot_size, &current_sl_app_properties_addr)) {
    LOGE("Failed to locate sl_app_properties in current bootloader");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }

  const ApplicationProperties_t* current_bl_app_props =
    (ApplicationProperties_t*)current_sl_app_properties_addr;

  uintptr_t new_sl_app_properties_addr = 0;
  if (!locate_bl_sl_app_properties((uintptr_t)fwup_priv.target_slot_addr, slot_size,
                                   &new_sl_app_properties_addr)) {
    LOGE("Failed to locate sl_app_properties in new bootloader");
    return FWUP_VERIFY_CANT_FIND_PROPERTIES;
  }
  const ApplicationProperties_t* new_bl_app_props =
    (ApplicationProperties_t*)new_sl_app_properties_addr;

  // Check new bootloader version against the old one
  LOGD("Upgrade from version %ld to %ld", current_bl_app_props->app.version,
       new_bl_app_props->app.version);
  SECURE_DO_FAILIN((new_bl_app_props->app.version <= current_bl_app_props->app.version), {
    LOGE("New bootloader version is not newer than the current bootloader version (%ld <= %ld)",
         new_bl_app_props->app.version, current_bl_app_props->app.version);
    return FWUP_VERIFY_VERSION_INVALID;
  });

  SECURE_DO_FAILIN(
    verify_new_bootloader(current_bl_app_props->cert, (uint8_t*)fwup_priv.target_slot_addr,
                          slot_size - ECC_SIG_SIZE,  // Signature must be excluded from hashing
                          (uint8_t*)sig_addr) != FWUP_VERIFY_SUCCESS,
    { return FWUP_VERIFY_SIGNATURE_INVALID; });

  // Erase the BL slot
  if (!fwup_flash_erase_bl(fwup_priv.perf.erase, (void*)bl_base_addr, slot_size)) {
    LOGE("Flash erase of bootloader failed");
    return FWUP_VERIFY_FLASH_ERROR;
  }

  LOGD("Done with erase...");

  // Copy the BL from the inactive slot to the BL slot.
  if (!fwup_flash_write(fwup_priv.perf.write, (void*)bl_base_addr, fwup_priv.target_slot_addr,
                        slot_size)) {
    LOGE("Flash write of bootloader update failed");
    return FWUP_VERIFY_FLASH_ERROR;
  }

  LOGD("Done with write...");

  // Re-verify the BL in the BL slot; note the difference in the parameters.
  SECURE_DO_FAILIN(
    verify_new_bootloader(new_bl_app_props->cert, (uint8_t*)bl_base_addr, slot_size - ECC_SIG_SIZE,
                          (uint8_t*)bl_signature_addr) != FWUP_VERIFY_SUCCESS,
    { return FWUP_VERIFY_SIGNATURE_INVALID; });

  return FWUP_VERIFY_SUCCESS;
}
