#pragma once

#include <stdbool.h>
#include <stdint.h>

bool crypto_sign_challenge(uint8_t* challenge, uint32_t challenge_size, uint8_t* signature,
                           uint32_t signature_size);
