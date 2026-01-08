#include "attributes.h"
#include "bl_secureboot_picocert.h"

#include <stdint.h>

// Placeholder for picocert certificate (will be updated by firmware_signer.py)
// This is placed in .rodata and pointed to by app_properties
USED const app_certificate_t app_certificate = {0};

// Application properties structure containing metadata and certificate reference
// The bootloader reads this to get the certificate and verify the firmware signature
// IMPORTANT: This structure and the certificate are part of the SIGNED data,
// ensuring the certificate itself is authenticated
//
// The firmware_signer.py will update:
// - app.version field (offset 20) with the actual version number
// - app_certificate with the real picocert
USED SECTION(PROPERTIES_SECTION) const app_properties_t app_properties = {
  .magic = PICO_CERT_APP_PROPERTIES_MAGIC,
  .structVersion = 1,
  .app =
    {
      .version = 0,      // Updated at signing time
      .productId = {0},  // Product ID placeholder
    },
  .cert = (app_certificate_t*)&app_certificate,
};

// Reserve space for application signature (will be overwritten by firmware_signer.py)
// This creates a 64-byte symbol in the signature section, ensuring:
// - Section has non-zero size from compilation
// - SHF_ALLOC flag is set automatically by linker
// - Dedicated PT_LOAD segment is created
// - GDB will load the signature to flash
USED const uint8_t SECTION(SIGNATURE_SECTION) app_codesigning_signature[64] = {
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
};

// Reserve space for metadata section (will be filled by objcopy --update-section during build)
// This ensures the section exists in the ELF so objcopy can update it with the generated metadata
// Using void* to match the metadata.c pattern from EFR32
void* SECTION(METADATA_SECTION) app_metadata;
