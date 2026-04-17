#include "sealed_data_utils.h"

#include "aes.h"
#include "log.h"
#include "wallet.h"

bool sealed_data_unseal(const fwpb_sealed_data* sealed, uint8_t* output, size_t output_size) {
  if (sealed->data.size == 0 || sealed->data.size > output_size) {
    return false;
  }
  if (sealed->nonce.size != AES_GCM_IV_LENGTH) {
    return false;
  }
  if (sealed->tag.size != AES_GCM_TAG_LENGTH) {
    return false;
  }

  wallet_res_t result =
    wallet_csek_decrypt((uint8_t*)sealed->data.bytes, output, sealed->data.size,
                        (uint8_t*)sealed->nonce.bytes, (uint8_t*)sealed->tag.bytes);
  return (result == WALLET_RES_OK);
}
