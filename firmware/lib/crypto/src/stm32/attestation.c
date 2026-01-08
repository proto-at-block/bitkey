#include "attestation.h"

#include <string.h>

// This is needed since it is linked in to key_exchange.c but it is currently not called.
bool crypto_read_serial(uint8_t* serial_number) {
  (void)serial_number;
  return false;
}
