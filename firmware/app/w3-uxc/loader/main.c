#include "attributes.h"
#include "bl_secureboot.h"
#include "bl_secureboot_picocert.h"
#include "bootload.h"
#include "canary.h"
#include "clock.h"
#include "mcu.h"
#include "mcu_flash.h"
#include "mcu_reset.h"
#include "secure_rng.h"
#include "secutils.h"

#include <stddef.h>
#include <stdint.h>

extern uintptr_t __application_a_boot_addr;
extern uintptr_t __application_a_boot_size;
extern uintptr_t __application_a_start_addr;
extern uintptr_t __application_a_signature_addr;
extern uintptr_t __application_a_signature_size;
extern uintptr_t __application_a_properties_addr;
extern uintptr_t __application_a_signing_size;

extern uintptr_t __application_b_boot_addr;
extern uintptr_t __application_b_boot_size;
extern uintptr_t __application_b_start_addr;
extern uintptr_t __application_b_signature_addr;
extern uintptr_t __application_b_signature_size;
extern uintptr_t __application_b_properties_addr;
extern uintptr_t __application_b_signing_size;

USED const app_certificate_t bl_certificate = {0};

// Reserve space for bootloader signature (will be overwritten by firmware_signer.py)
const uint8_t SECTION(".bl_codesigning_signature_section") bl_codesigning_signature[64] = {
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
};

/* Required for the section to be included in the compiled elf */
void* SECTION(".bl_metadata_section") bl_metadata;

USED const app_properties_t bl_app_properties = {
  .magic = PICO_CERT_APP_PROPERTIES_MAGIC,
  .structVersion = 1,
  .app =
    {
      .version = 0,
      .productId = {0},
    },
  .cert = (app_certificate_t*)&bl_certificate,
};

NO_OPTIMIZE static secure_bool_t app_verify_img(const bootload_img_t* img) {
  if (img == NULL) {
    return SECURE_FALSE;
  }

  return bl_verify_app_slot((app_certificate_t*)&bl_certificate, (app_properties_t*)img->props_addr,
                            (uint8_t*)img->base_addr, img->total_size, (uint8_t*)img->sig_addr);
}

static void app_detect_glitch(void) {
  mcu_reset_with_reason(MCU_RESET_FAULT);
}

NO_OPTIMIZE static void app_run_valid_fw(const bootload_img_t* img_a, const bootload_img_t* img_b) {
  // Verify both firmware slots.
  volatile secure_bool_t a_img_valid = SECURE_FALSE;
  volatile secure_bool_t b_img_valid = SECURE_FALSE;

  SECURE_DO_ONCE({ a_img_valid = app_verify_img(img_a); });
  SECURE_DO_ONCE({ b_img_valid = app_verify_img(img_b); });

  // Check if neither slot is valid.
  SECURE_DO_FAILIN(((a_img_valid != SECURE_TRUE) && (b_img_valid != SECURE_TRUE)),
                   { mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE); });

  // Select the boot slot.
  boot_slot_t slot_a = {
    .props = (app_properties_t*)img_a->props_addr,
    .boot_addr = img_a->boot_addr,
    .signature_verified = a_img_valid,
  };

  boot_slot_t slot_b = {
    .props = (app_properties_t*)img_b->props_addr,
    .boot_addr = img_b->boot_addr,
    .signature_verified = b_img_valid,
  };

  boot_slot_t* selected_slot;
  volatile secure_bool_t select_img_ok = SECURE_FALSE;
  select_img_ok = bl_select_slot(&slot_a, &slot_b, &selected_slot);
  SECURE_DO_FAILIN(((select_img_ok != SECURE_TRUE) || (selected_slot == NULL)),
                   { mcu_reset_with_reason(MCU_RESET_INVALID_PROPERTIES); });

  volatile uintptr_t addr = selected_slot->boot_addr;

  // Double check the selected slot.
  if (selected_slot == &slot_a) {
    SECURE_DO_FAILIN(a_img_valid != SECURE_TRUE,
                     { mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE); });
  } else if (selected_slot == &slot_b) {
    SECURE_DO_FAILIN(b_img_valid != SECURE_TRUE,
                     { mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE); });
  } else {
    mcu_reset_with_reason(MCU_RESET_FATAL);
  }

  SECURE_DO_FAILIN(selected_slot->boot_addr != addr,
                   { mcu_reset_with_reason(MCU_RESET_BAD_BOOT_ADDR); });

  bootload_trampoline((uint8_t*)addr);
}

NO_OPTIMIZE int main(void) {
  // Keep bl_app_properties from being stripped by the linker
  volatile const void* __keep_props __attribute__((unused)) = &bl_app_properties;

  const bootload_img_t app_a = {
    .base_addr = (uintptr_t)&__application_a_start_addr,
    .boot_addr = (uintptr_t)&__application_a_boot_addr,
    .sig_addr = (uintptr_t)&__application_a_signature_addr,
    .props_addr = (uintptr_t)&__application_a_properties_addr,
    .program_size = (size_t)&__application_a_boot_size,
    .sig_size = (size_t)&__application_a_signature_size,
    .total_size = (size_t)&__application_a_signing_size,
  };

  const bootload_img_t app_b = {
    .base_addr = (uintptr_t)&__application_b_start_addr,
    .boot_addr = (uintptr_t)&__application_b_boot_addr,
    .sig_addr = (uintptr_t)&__application_b_signature_addr,
    .props_addr = (uintptr_t)&__application_b_properties_addr,
    .program_size = (size_t)&__application_b_boot_size,
    .sig_size = (size_t)&__application_b_signature_size,
    .total_size = (size_t)&__application_b_signing_size,
  };

  // Disable all IRQs to prevent jumping into IRQ handlers during boot
  // validation.
  __disable_irq();

  mcu_init();
  mcu_flash_init();
  crypto_random_init();
  secutils_init((secutils_api_t){
    .detect_glitch = &app_detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });

  SECURE_DO_ONCE({ canary_init(); });

  app_run_valid_fw(&app_a, &app_b);

  while (1) {
    ;
  }

  return 0;
}
