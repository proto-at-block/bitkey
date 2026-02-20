#include "secure_channel_cert.h"

static const secure_channel_cert_desc_t local_identity_cert = {
  .id = "w3_uxc_id",
  .key_type = ALG_ECC_P256,
  // The STM32U5 has an AES peripheral that contains a hardware key.
  // That hardware key can be used in a wrapping mode for AES operations.
  // It's possible we can use that to wrap this key in the future.
  .key_storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
  .cert_type = CERT_TYPE_PICOCERT,
};

const secure_channel_cert_desc_t* const secure_channel_product_certs[] = {
  &local_identity_cert,
  NULL  // Sentinel
};
