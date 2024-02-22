#pragma once

#include "application_properties.h"
#include "secutils.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct {
  ApplicationProperties_t* props;
  uintptr_t boot_addr;
  secure_bool_t signature_verified;
} boot_slot_t;

secure_bool_t bl_verify_app_slot(ApplicationCertificate_t* bl_cert,
                                 ApplicationProperties_t* app_properties, uint8_t* app,
                                 uint32_t app_size, uint8_t* app_signature);
secure_bool_t bl_select_slot(boot_slot_t* slot_a, boot_slot_t* slot_b, boot_slot_t** selected_slot);

// Visible for unit tests.
secure_bool_t bl_verify_app_certificate(ApplicationCertificate_t* app_cert,
                                        ApplicationCertificate_t* bl_cert);
secure_bool_t bl_verify_application(ApplicationCertificate_t* app_cert, uint8_t* app,
                                    uint32_t app_size, uint8_t* signature);
