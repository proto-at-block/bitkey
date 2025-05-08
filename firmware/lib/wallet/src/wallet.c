#include "wallet.h"

#include "aes.h"
#include "assert.h"
#include "wkek_impl.h"

mempool_t* wallet_pool;

void wallet_init(mempool_t* mempool) {
  ASSERT_EMBEDDED_ONLY(mempool && !wallet_pool);
  wallet_pool = mempool;
  crypto_ecc_secp256k1_init();
  wkek_init();
}

wallet_res_t wallet_csek_encrypt(uint8_t* unwrapped_csek, uint8_t* wrapped_csek_out,
                                 uint32_t length, uint8_t iv_out[AES_GCM_IV_LENGTH],
                                 uint8_t tag_out[AES_GCM_TAG_LENGTH]) {
  ASSERT(unwrapped_csek && wrapped_csek_out && iv_out && tag_out);
  ASSERT(length == CSEK_LENGTH);

  if (!wkek_lazy_init()) {
    return WALLET_RES_WKEK_ERR;
  }

  if (!wkek_encrypt(unwrapped_csek, wrapped_csek_out, length, iv_out, tag_out)) {
    return WALLET_RES_SEALING_ERR;
  }

  return WALLET_RES_OK;
}

wallet_res_t wallet_csek_decrypt(uint8_t* wrapped_csek, uint8_t* unwrapped_csek_out,
                                 uint32_t length, uint8_t iv[AES_GCM_IV_LENGTH],
                                 uint8_t tag[AES_GCM_TAG_LENGTH]) {
  ASSERT(wrapped_csek && unwrapped_csek_out && iv && tag);

  if (!wkek_lazy_init()) {
    return WALLET_RES_WKEK_ERR;
  }

  if (!wkek_decrypt(wrapped_csek, unwrapped_csek_out, length, iv, tag)) {
    return WALLET_RES_UNSEALING_ERR;
  }

  return WALLET_RES_OK;
}
