#include "application_properties.h"
#include "attributes.h"
#include "bl_secureboot.h"
#include "canary.h"
#include "clock.h"
#include "ecc.h"
#include "fatal.h"
#include "hash.h"
#include "mcu.h"
#include "mcu_flash.h"
#include "mcu_gpio.h"
#include "mcu_reset.h"
#include "secure_engine.h"
#include "secure_rng.h"
#include "secutils.h"

#include "em_chip.h"
#include "em_device.h"

extern int __application_a_boot_addr;
extern int __application_b_boot_addr;

extern int __application_a_signature_addr;
extern int __application_a_properties_addr;
extern int __application_a_start_addr;
extern int __application_a_signing_size;

extern int __application_b_signature_addr;
extern int __application_b_properties_addr;
extern int __application_b_signing_size;
extern int __application_b_start_addr;

static const mcu_gpio_config_t power_retain_gpio = {
  .port = 1,  // B
  .pin = 5,
  .mode = MCU_GPIO_MODE_PUSH_PULL,
};

typedef void (*app_entry_func_t)(void);
static void jump_to_app(uintptr_t addr) __attribute__((noreturn, naked));

static volatile uintptr_t saved_boot_addr = 0;

USED const ApplicationCertificate_t bl_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0U},
  .key = {0U},
  .version = 0,
  .signature = {0U},
};

#define BOOTLOADER_VERSION 1
#define BOOTLOADER_ID \
  { 0 }

const uint8_t SECTION(".bl_codesigning_signature_section") bl_codesigning_signature[64] = {
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
};

// This variable must be named `sl_app_properties`, since it referred to in `mcu_startup.c`;
// the 13th vector in the vector table is a pointer to this struct, used by SiLabs' first stage
// bootloader.
USED const ApplicationProperties_t sl_app_properties = {
  .magic = APPLICATION_PROPERTIES_MAGIC,
  .structVersion = APPLICATION_PROPERTIES_VERSION,
  .signatureType = APPLICATION_SIGNATURE_ECDSA_P256,
  .signatureLocation = (uint32_t)&bl_codesigning_signature,
  .app =
    {
      .type = APPLICATION_TYPE_BOOTLOADER,
      .version = BOOTLOADER_VERSION,
      .capabilities = 0,
      .productId = BOOTLOADER_ID,
    },
  .cert = (ApplicationCertificate_t*)&bl_certificate,
};

/* Required for the section to be included in the compiled elf */
void* SECTION(".bl_metadata_section") bl_metadata;

static ApplicationProperties_t* app_properties_a =
  (ApplicationProperties_t*)&__application_a_properties_addr;
static ApplicationProperties_t* app_properties_b =
  (ApplicationProperties_t*)&__application_b_properties_addr;

static secure_bool_t verify_slot_a(void) {
  uint8_t* signature_a = (uint8_t*)&__application_a_signature_addr;
  uint8_t* app_a = (uint8_t*)&__application_a_start_addr;
  uint32_t app_size_a = (uint32_t)&__application_a_signing_size;

  return bl_verify_app_slot((ApplicationCertificate_t*)&bl_certificate, app_properties_a, app_a,
                            app_size_a, signature_a);
}

static secure_bool_t verify_slot_b(void) {
  uint8_t* signature_b = (uint8_t*)&__application_b_signature_addr;
  uint8_t* app_b = (uint8_t*)&__application_b_start_addr;
  uint32_t app_size_b = (uint32_t)&__application_b_signing_size;

  return bl_verify_app_slot((ApplicationCertificate_t*)&bl_certificate, app_properties_b, app_b,
                            app_size_b, signature_b);
}

void detect_glitch(void) {
  mcu_reset_with_reason(MCU_RESET_FAULT);
}

NO_OPTIMIZE int main(void) {
  CHIP_Init();
  mcu_init();

  // Set power retain as early as possible to support wake-from-NFC: the NFC field is only
  // active for ~40ms.
  mcu_gpio_configure(&power_retain_gpio, true);

  sl_se_init();
  mcu_flash_init();
  secutils_init((secutils_api_t){
    .detect_glitch = &detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });

  SECURE_DO_ONCE({ se_configure_active_mode(SECURE_TRUE); });
  SECURE_DO_ONCE({ canary_init(); });

  // Verify both firmware slots.
  volatile secure_bool_t a_slot_valid = SECURE_FALSE;
  volatile secure_bool_t b_slot_valid = SECURE_FALSE;

  SECURE_DO_ONCE({ a_slot_valid = verify_slot_a(); });
  SECURE_DO_ONCE({ b_slot_valid = verify_slot_b(); });

  // Check if neither slot is valid.
  SECURE_DO_FAILIN(((a_slot_valid != SECURE_TRUE) && (b_slot_valid != SECURE_TRUE)),
                   { mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE); });

  // Select boot slot.
  boot_slot_t slot_a = {
    .props = app_properties_a,
    .boot_addr = (uintptr_t)&__application_a_boot_addr,
    .signature_verified = a_slot_valid,
  };
  boot_slot_t slot_b = {
    .props = app_properties_b,
    .boot_addr = (uintptr_t)&__application_b_boot_addr,
    .signature_verified = b_slot_valid,
  };
  boot_slot_t* selected_slot = NULL;

  volatile secure_bool_t select_slot_ok = SECURE_FALSE;
  select_slot_ok = bl_select_slot(&slot_a, &slot_b, &selected_slot);
  SECURE_DO_FAILIN(select_slot_ok != SECURE_TRUE,
                   { mcu_reset_with_reason(MCU_RESET_INVALID_PROPERTIES); });

  saved_boot_addr = selected_slot->boot_addr;

  // Double check the selected slot.
  if (selected_slot == &slot_a) {
    SECURE_DO_FAILIN(a_slot_valid != SECURE_TRUE,
                     { mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE); });
  } else if (selected_slot == &slot_b) {
    SECURE_DO_FAILIN(b_slot_valid != SECURE_TRUE,
                     { mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE); });
  } else {
    mcu_reset_with_reason(MCU_RESET_FATAL);
  }

  SECURE_DO_FAILIN(selected_slot->boot_addr != saved_boot_addr,
                   { mcu_reset_with_reason(MCU_RESET_BAD_BOOT_ADDR); });

  jump_to_app(selected_slot->boot_addr);

  for (;;) {
  }
}

NO_OPTIMIZE NO_RETURN static void jump_to_app(uintptr_t addr) {
  SECURE_DO_FAILIN(addr != saved_boot_addr, { mcu_reset_with_reason(MCU_RESET_BAD_BOOT_ADDR); });

  volatile uint32_t app_stack = (uint32_t) * (volatile unsigned int*)(addr);
  SECURE_DO({ app_stack = (uint32_t) * (volatile unsigned int*)(addr); });

  volatile app_entry_func_t app_entry = (app_entry_func_t) * (volatile unsigned int*)(addr + 4);
  SECURE_DO({ app_entry = (app_entry_func_t) * (volatile unsigned int*)(addr + 4); });

  volatile app_entry_func_t app_entry_redundant =
    (app_entry_func_t) * (volatile unsigned int*)(addr + 4);
  SECURE_DO({ app_entry_redundant = (app_entry_func_t) * (volatile unsigned int*)(addr + 4); });

  volatile uint32_t app_stack_redundant = (uint32_t) * (volatile unsigned int*)(addr);
  SECURE_DO({ app_stack_redundant = (uint32_t) * (volatile unsigned int*)(addr); });

  SECURE_IF_FAILIN(app_entry != app_entry_redundant) {
    mcu_reset_with_reason(MCU_RESET_BAD_BOOT_ADDR);
  }

  SECURE_IF_FAILIN(app_stack != app_stack_redundant) {
    mcu_reset_with_reason(MCU_RESET_BAD_BOOT_ADDR);
  }

  SECURE_DO_FAILIN(addr != saved_boot_addr, { mcu_reset_with_reason(MCU_RESET_BAD_BOOT_ADDR); });

  // Must be after all secure engine calls (such as or any of the SECURE_DO* macros)
  SECURE_DO_ONCE({ se_configure_active_mode(SECURE_FALSE); });
  sl_se_deinit();

  // Disable global IRQ before jumping
  // Note: IRQs _must_ be re-enabled by the app init code
  __disable_irq();

  // Set vector table offset
  SCB->VTOR = (uint32_t)addr;

  // Set valid stack pointer for app
  __set_MSP(app_stack);

  // Start app
  app_entry();
}
