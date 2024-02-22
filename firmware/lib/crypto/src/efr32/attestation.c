#include "attestation.h"

#include "log.h"
#include "secure_engine.h"

bool crypto_sign_challenge(uint8_t* challenge, uint32_t challenge_size, uint8_t* signature,
                           uint32_t signature_size) {
  sl_status_t result = se_sign_challenge(challenge, challenge_size, signature, signature_size);
  if (result != SL_STATUS_OK) {
    LOGE("Failed to sign challenge: %lx", result);
    return false;
  }
  return true;
}
