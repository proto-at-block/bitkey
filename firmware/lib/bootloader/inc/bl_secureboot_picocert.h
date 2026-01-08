/**
 * @file
 *
 * @brief Picocert based secure boot.
 *
 * @{
 */

#pragma once

#include "ecc.h"
#include "picocert.h"

#include <stdint.h>

/**
 * @brief Pico-cert based application certificates.
 */
typedef picocert_t app_certificate_t;

#define PICO_CERT_APP_PROPERTIES_MAGIC \
  { 0x42, 0x49, 0x54, 0x4B, 0x45, 0x59, 0x2D, 0x55, 0x58, 0x43, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }

/**
 * @brief Stored in flash, the application properties provides identifying
 * information about the application image.
 */
typedef struct {
  /**
   * @brief Magic number for the application properties structure.
   */
  uint8_t magic[16];

  /**
   * @brief Application property structure version.
   */
  uint32_t structVersion;

  struct {
    /**
     * @brief Application image firmware version number.
     */
    uint32_t version;

    /**
     * @brief Unique ID for the production this application is built for.
     */
    uint8_t productId[16];
  } app;

  /**
   * @brief Application certificate for this image.
   */
  app_certificate_t* cert;
} app_properties_t;

/** @} */
