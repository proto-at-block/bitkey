/**
 * @file
 *
 * @{
 */

#pragma once

#include "bl_secureboot.h"
#include "ecc.h"
#include "secutils.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Validates that passed data is signed with the key in the given certificate.
 *
 * @param cert    Certificate to validate against.
 * @param sig     Signature for the @p data.
 * @param data    The data to verify.
 * @param length  Length of @p data in bytes.
 *
 * @return #SECURE_TRUE if verified, otherwise #SECURE_FALSE.
 */
secure_bool_t bl_secureboot_verify(app_certificate_t* cert, uint8_t sig[ECC_SIG_SIZE],
                                   uint8_t* data, size_t length);

/** @} */
