/**
 * @file key_management.c
 * @brief POSIX key management using SE API.
 *
 * This implementation routes key generation through the SE emulation layer
 * to support wrapped keys transparently.
 */

#include "key_management.h"

#include "assert.h"
#include "attributes.h"
#include "secure_engine.h"

bool generate_key(key_handle_t* key) {
  ASSERT(key);

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  status = sl_se_generate_key(&cmd_ctx, &key_desc);
  return status == SL_STATUS_OK;
}

uint32_t key_management_custom_domain_prepare(key_algorithm_t UNUSED(alg), uint8_t* UNUSED(buffer),
                                              uint32_t UNUSED(size)) {
  return 0;
}
