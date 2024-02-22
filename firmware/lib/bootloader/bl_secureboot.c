#include "bl_secureboot.h"

#include "attributes.h"
#include "ecc.h"
#include "hash.h"
#include "secutils.h"

#include <stddef.h>

#ifdef EMBEDDED_BUILD

extern int __FLASH_segment_start__;
extern int __FLASH_segment_end__;

const uint32_t flash_start_addr = (uint32_t)&__FLASH_segment_start__;
const uint32_t flash_end_addr = (uint32_t)&__FLASH_segment_end__;

NO_OPTIMIZE secure_bool_t addr_in_flash(uintptr_t addr) {
  volatile uintptr_t volatile_addr = addr;
  SECURE_IF_FAILIN(volatile_addr < flash_start_addr) { return SECURE_FALSE; }
  SECURE_IF_FAILIN(volatile_addr > flash_end_addr) { return SECURE_FALSE; }
  return SECURE_TRUE;
}

#else

// Must be defined by unit tests
extern secure_bool_t addr_in_flash(uintptr_t addr);

#endif

NO_OPTIMIZE static secure_bool_t verify(ApplicationCertificate_t* cert, uint8_t sig[ECC_SIG_SIZE],
                                        uint8_t* data, size_t length) {
  if (!cert || !sig || !data || !length) {
    return SECURE_FALSE;
  }

  uint8_t hash[SHA256_DIGEST_SIZE] = {0};
  if (!crypto_hash(data, length, hash, sizeof(hash), ALG_SHA256)) {
    return SECURE_FALSE;
  }

  key_handle_t key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = cert->key,
    .key.size = sizeof(cert->key),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  volatile secure_bool_t verified = SECURE_FALSE;
  verified = crypto_ecc_verify_hash(&key, hash, SHA256_DIGEST_SIZE, sig);
  SECURE_IF_FAILIN(verified != SECURE_TRUE) { return SECURE_FALSE; }

  SECURE_DO_ONCE({
    verified = SECURE_FALSE;  // Reset to closed state
  });

  verified = crypto_ecc_verify_hash(&key, hash, SHA256_DIGEST_SIZE, sig);
  SECURE_IF_FAILIN(verified != SECURE_TRUE) { return SECURE_FALSE; }

  return verified;
}

NO_OPTIMIZE secure_bool_t bl_verify_app_slot(ApplicationCertificate_t* bl_cert,
                                             ApplicationProperties_t* app_properties, uint8_t* app,
                                             uint32_t app_size, uint8_t* app_signature) {
  if (addr_in_flash((uintptr_t)app_properties) != SECURE_TRUE ||
      addr_in_flash((uintptr_t)app_properties->cert) != SECURE_TRUE) {
    return SECURE_FALSE;
  }

  // First, verify that the application certificate has been signed with the
  // bootloader key.
  volatile secure_bool_t cert_ok = SECURE_FALSE;
  cert_ok = bl_verify_app_certificate(app_properties->cert, (ApplicationCertificate_t*)bl_cert);
  SECURE_IF_FAILIN(cert_ok != SECURE_TRUE) { return SECURE_FALSE; }

  // We can now trust the application certificate's public key.
  // Locate application and verify signature.
  volatile secure_bool_t app_ok = SECURE_FALSE;
  app_ok = bl_verify_application(app_properties->cert, app, (uint32_t)app_size, app_signature);
  SECURE_IF_FAILIN(app_ok != SECURE_TRUE) { return SECURE_FALSE; }

  return SECURE_TRUE;
}

NO_OPTIMIZE secure_bool_t bl_select_slot(boot_slot_t* slot_a, boot_slot_t* slot_b,
                                         boot_slot_t** selected_slot) {
  if (!slot_a || !slot_b) {
    return SECURE_FALSE;
  }

  volatile boot_slot_t* a = (volatile boot_slot_t*)slot_a;
  volatile boot_slot_t* b = (volatile boot_slot_t*)slot_b;

  // Neither slot is valid
  SECURE_DO_FAILIN(
    ((a->signature_verified != SECURE_TRUE) && (b->signature_verified != SECURE_TRUE)),
    { return SECURE_FALSE; });

  // If only one slot has a valid signature, boot from that slot.
  SECURE_DO_FAILOUT(
    (a->signature_verified == SECURE_TRUE) && (b->signature_verified != SECURE_TRUE), {
      *selected_slot = slot_a;
      return SECURE_TRUE;
    });

  SECURE_DO_FAILOUT(
    (a->signature_verified != SECURE_TRUE) && (b->signature_verified == SECURE_TRUE), {
      *selected_slot = slot_b;
      return SECURE_TRUE;
    });

  // If both slots are valid, choose the slot with the higher version to boot to.
  SECURE_DO_FAILOUT(a->props->app.version > b->props->app.version, {
    *selected_slot = slot_a;
    return SECURE_TRUE;
  });
  SECURE_DO_FAILOUT(a->props->app.version < b->props->app.version, {
    *selected_slot = slot_b;
    return SECURE_TRUE;
  });
  SECURE_DO_FAILOUT(a->props->app.version == b->props->app.version, {
    *selected_slot = slot_a;
    return SECURE_TRUE;
  });

  return SECURE_FALSE;
}

NO_OPTIMIZE secure_bool_t bl_verify_app_certificate(ApplicationCertificate_t* app_cert,
                                                    ApplicationCertificate_t* bl_cert) {
  if (!app_cert || !bl_cert) {
    return SECURE_FALSE;
  }

  SECURE_IF_FAILIN((volatile uint32_t)bl_cert->version > app_cert->version) { return SECURE_FALSE; }

  uint32_t hashable_cert_size = offsetof(ApplicationCertificate_t, signature);
  return verify(bl_cert, app_cert->signature, (uint8_t*)app_cert, hashable_cert_size);
}

NO_OPTIMIZE secure_bool_t bl_verify_application(ApplicationCertificate_t* app_cert, uint8_t* app,
                                                uint32_t app_size, uint8_t* signature) {
  if (!app_cert || !signature) {
    return SECURE_FALSE;
  }
  return verify(app_cert, signature, app, app_size);
}
