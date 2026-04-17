/**
 * @file
 *
 * @brief Staged Signature for Atomic FWUP Reset
 *
 * Provides APIs to persist and recover a Core firmware signature across
 * power loss during the atomic UXC + Core firmware update protocol.
 *
 * @{
 */

#pragma once

#include "ecc.h"
#include "wallet.pb.h"

#include <stdbool.h>

#define FWUP_STAGED_SIG_PATH "staged_sig"

/**
 * @brief On-disk layout of the staged signature file.
 */
typedef struct {
  uint8_t signature[ECC_SIG_SIZE];
  fwpb_firmware_slot target_slot;
  fwpb_semver core_target_version;
  fwpb_semver uxc_target_version;
} fwup_staged_sig_t;

/**
 * @brief Writes a staged signature file to the filesystem.
 *
 * @param staged  Pointer to the staged signature data.
 *
 * @return `true` if the file was written successfully, otherwise `false`.
 */
bool fwup_staged_sig_write(const fwup_staged_sig_t* staged);

/**
 * @brief Reads a staged signature file from the filesystem.
 *
 * @param staged  Pointer to receive the staged signature data.
 *
 * @return `true` if the file was read successfully, otherwise `false`.
 */
bool fwup_staged_sig_read(fwup_staged_sig_t* staged);

/**
 * @brief Removes the staged signature file from the filesystem.
 */
void fwup_staged_sig_remove(void);

/**
 * @brief Checks whether a staged signature file exists.
 *
 * @return `true` if the file exists, otherwise `false`.
 */
bool fwup_staged_sig_exists(void);

/**
 * @brief Writes the staged signature to its target flash location.
 *
 * @param staged  Pointer to the staged signature data.
 *
 * @return `true` if the signature was written successfully, otherwise `false`.
 */
bool fwup_staged_sig_commit_to_flash(const fwup_staged_sig_t* staged);

/** @} */
