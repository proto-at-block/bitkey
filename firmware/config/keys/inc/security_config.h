#pragma once

#include "secutils.h"

#include <stdint.h>

#define FWUP_DELTA_PATCH_PUBKEY_SIZE 64

typedef struct {
  secure_bool_t is_production;
  uint8_t* biometrics_mac_key;
  uint8_t* fwup_delta_patch_pubkey;
} security_config_t;

/**
 * @brief Returns a boolean indicating if the current device configuration is
 * a production configuration.
 *
 * @return `SECURE_TRUE` if device is running a production configuration,
 * otherwise `SECURE_FALSE`.
 */
secure_bool_t security_config_is_production(void);
