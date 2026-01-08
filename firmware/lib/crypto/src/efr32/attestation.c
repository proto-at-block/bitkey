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

bool crypto_read_serial(uint8_t* serial_number) {
  uint8_t zero_padded_serial[SE_SERIAL_SIZE] = {0};
  if (se_read_serial(zero_padded_serial) != SL_STATUS_OK) {
    return false;
  }

  _Static_assert(SE_ACTUAL_SERIAL_SIZE == CRYPTO_SERIAL_SIZE,
                 "This function will write a buffer of length CRYPTO_SERIAL_SIZE");
  memcpy(serial_number, &zero_padded_serial[SE_ACTUAL_SERIAL_START], SE_ACTUAL_SERIAL_SIZE);
  return true;
}
