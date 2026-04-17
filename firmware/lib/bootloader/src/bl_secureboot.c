#include "bl_secureboot.h"

#include "attributes.h"
#include "bl_secureboot_impl.h"
#include "ecc.h"
#include "hash.h"
#include "mcu_devinfo.h"
#include "secutils.h"
#include "wstring.h"

#include <stddef.h>

_Static_assert(CHIPID_LENGTH <= sizeof(((app_properties_t*)0)->app.productId),
               "CHIPID_LENGTH must be <= productId size");

static const uint8_t default_product_id[sizeof(((app_properties_t*)0)->app.productId)] = {0};

static secure_bool_t bl_product_id_valid_for_chipid(const app_properties_t* app_properties) {
  if (app_properties == NULL) {
    return SECURE_FALSE;
  }

  // `productId` is unused for app-slot properties on EFR32/UXC, so the default
  // value indicates "not per-device signed" and bypasses chip-ID enforcement.
  if (memcmp_s(app_properties->app.productId, default_product_id, sizeof(default_product_id)) ==
      0) {
    return SECURE_TRUE;
  }

  uint8_t chipid[CHIPID_LENGTH] = {0};
  mcu_devinfo_chipid(chipid);

  if (memcmp_s(app_properties->app.productId, chipid, CHIPID_LENGTH) != 0) {
    return SECURE_FALSE;
  }

  // If productId is longer than CHIPID_LENGTH, ensure the remaining bytes are all 0.
  for (size_t i = CHIPID_LENGTH; i < sizeof(app_properties->app.productId); i++) {
    if (app_properties->app.productId[i] != 0u) {
      return SECURE_FALSE;
    }
  }

  return SECURE_TRUE;
}

#ifdef EMBEDDED_BUILD
extern int __application_a_start_addr;
extern int __application_a_signing_size;
extern int __application_b_start_addr;
extern int __application_b_signing_size;
extern char bl_base_addr[];
extern char bl_slot_size[];

static secure_bool_t _addr_in_region(uintptr_t addr, uintptr_t region_start,
                                     uintptr_t region_size) {
  if ((addr >= region_start) && (addr < (region_start + region_size))) {
    return SECURE_TRUE;
  }
  return SECURE_FALSE;
}

NO_OPTIMIZE secure_bool_t addrs_in_same_slot(uintptr_t addr_a, uintptr_t addr_b) {
  volatile uintptr_t va = addr_a;
  volatile uintptr_t vb = addr_b;

  // Check if both are in app slot A
  if (_addr_in_region(va, (uintptr_t)&__application_a_start_addr,
                      (uintptr_t)&__application_a_signing_size) == SECURE_TRUE &&
      _addr_in_region(vb, (uintptr_t)&__application_a_start_addr,
                      (uintptr_t)&__application_a_signing_size) == SECURE_TRUE) {
    return SECURE_TRUE;
  }

  // Check if both are in app slot B
  if (_addr_in_region(va, (uintptr_t)&__application_b_start_addr,
                      (uintptr_t)&__application_b_signing_size) == SECURE_TRUE &&
      _addr_in_region(vb, (uintptr_t)&__application_b_start_addr,
                      (uintptr_t)&__application_b_signing_size) == SECURE_TRUE) {
    return SECURE_TRUE;
  }

  // Check if both are in bootloader slot
  if (_addr_in_region(va, (uintptr_t)bl_base_addr, (uintptr_t)bl_slot_size) == SECURE_TRUE &&
      _addr_in_region(vb, (uintptr_t)bl_base_addr, (uintptr_t)bl_slot_size) == SECURE_TRUE) {
    return SECURE_TRUE;
  }

  return SECURE_FALSE;
}

#else

// Must be defined by unit tests
extern secure_bool_t addrs_in_same_slot(uintptr_t addr_a, uintptr_t addr_b);

#endif

NO_OPTIMIZE secure_bool_t bl_verify_app_slot(app_certificate_t* bl_cert,
                                             app_properties_t* app_properties, uint8_t* app,
                                             uint32_t app_size, uint8_t* app_signature) {
  // app_properties must be in the same slot as app
  SECURE_IF_FAILIN(addrs_in_same_slot((uintptr_t)app_properties, (uintptr_t)app) != SECURE_TRUE) {
    return SECURE_FALSE;
  }

  // app_properties->cert must be in the same slot as app_properties
  SECURE_IF_FAILIN(addrs_in_same_slot((uintptr_t)app_properties->cert, (uintptr_t)app_properties) !=
                   SECURE_TRUE) {
    return SECURE_FALSE;
  }

  // cert must be after properties
  SECURE_IF_FAILIN((uintptr_t)app_properties->cert <= (uintptr_t)app_properties) {
    return SECURE_FALSE;
  }

  // First, verify that the application certificate has been signed with the
  // bootloader key.
  volatile secure_bool_t cert_ok = SECURE_FALSE;
  cert_ok = bl_verify_app_certificate(app_properties->cert, (app_certificate_t*)bl_cert);
  SECURE_IF_FAILIN(cert_ok != SECURE_TRUE) { return SECURE_FALSE; }

  // We can now trust the application certificate's public key.
  // Locate application and verify signature.
  volatile secure_bool_t app_ok = SECURE_FALSE;
  app_ok = bl_verify_application(app_properties->cert, app, (uint32_t)app_size, app_signature);
  SECURE_IF_FAILIN(app_ok != SECURE_TRUE) { return SECURE_FALSE; }

  SECURE_IF_FAILIN(bl_product_id_valid_for_chipid(app_properties) != SECURE_TRUE) {
    return SECURE_FALSE;
  }

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

  // Safety check: if we got here, both slots have valid signatures
  SECURE_DO_FAILIN((a->signature_verified != SECURE_TRUE) || (b->signature_verified != SECURE_TRUE),
                   { return SECURE_FALSE; });

  // Both slots appear valid, choose the slot with the higher version to boot to.
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

NO_OPTIMIZE secure_bool_t bl_verify_app_certificate(app_certificate_t* app_cert,
                                                    app_certificate_t* bl_cert) {
  if (!app_cert || !bl_cert) {
    return SECURE_FALSE;
  }

  SECURE_IF_FAILIN((volatile uint32_t)bl_cert->version > app_cert->version) { return SECURE_FALSE; }

  uint32_t hashable_cert_size = offsetof(app_certificate_t, signature);
  return bl_secureboot_verify(bl_cert, app_cert->signature, (uint8_t*)app_cert, hashable_cert_size);
}

NO_OPTIMIZE secure_bool_t bl_verify_application(app_certificate_t* app_cert, uint8_t* app,
                                                uint32_t app_size, uint8_t* signature) {
  if (!app_cert || !signature) {
    return SECURE_FALSE;
  }
  return bl_secureboot_verify(app_cert, signature, app, app_size);
}
