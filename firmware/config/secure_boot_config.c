#include "application_properties.h"
#include "attributes.h"

const ApplicationCertificate_t app_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0U},
  .key = {0U},
  .version = 0,
  .signature = {0U},
};

// * This version number is embedded in the ApplicationProperties_t struct, and is
//   the 'source of truth' -- it's used in the bootloader and firmware update task
//   to protect against downgrades.
// * Should always match the one in metadata.
// * This variable is updated at signing time.
USED const uint32_t app_properties_version = 0;

#define APP_PROPERTIES_ID \
  { 0 }

// IMPORTANT: If changing this struct definition, you *must* update the structVersion field as well.
USED SECTION(PROPERTIES_SECTION) const ApplicationProperties_t sl_app_properties = {
  .magic = APPLICATION_PROPERTIES_MAGIC,
  .structVersion = APPLICATION_PROPERTIES_VERSION,
  .signatureType = APPLICATION_SIGNATURE_ECDSA_P256,
  .signatureLocation = 0,
  .app =
    {
      .type = APPLICATION_TYPE_MCU,
      .version = app_properties_version,
      .capabilities = 0,
      .productId = APP_PROPERTIES_ID,
    },
  .cert = (ApplicationCertificate_t*)&app_certificate,
};

USED const uint8_t SECTION(SIGNATURE_SECTION) app_codesigning_signature[64] = {
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
  0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe, 0xca, 0xfe,
};
