#pragma once

#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Unseal AES-256-GCM sealed data using the hardware sealing key.
 *
 * Validates size constraints, then calls wallet_csek_decrypt.
 * Used by EEK restoration, FACBR, lost app recovery, and recovery composites.
 *
 * @param sealed Sealed data (ciphertext + nonce + tag)
 * @param output Output buffer for the unsealed plaintext
 * @param output_size Size of the output buffer
 * @return true on success, false on size error or decryption failure
 */
bool sealed_data_unseal(const fwpb_sealed_data* sealed, uint8_t* output, size_t output_size);
