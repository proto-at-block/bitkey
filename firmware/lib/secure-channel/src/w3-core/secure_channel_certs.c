#include "secure_channel_cert.h"

static const secure_channel_cert_desc_t local_identity_cert = {
  .id = "w3_core_id",
  .key_type = ALG_ECC_P256,
  // Wrapped by secure engine
  .key_storage_type = KEY_STORAGE_EXTERNAL_WRAPPED,
  .cert_type = CERT_TYPE_PICOCERT,
};

const secure_channel_cert_desc_t* const secure_channel_product_certs[] = {
  &local_identity_cert,
  NULL  // Sentinel
};
