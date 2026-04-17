/**
 * @file
 *
 * @brief Firmware Update Utilities
 *
 * Common utilities for firmware version comparison and metadata access.
 *
 */

#pragma once

#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>

/**
 * @brief Compare two semantic versions for equality
 *
 * @param v1 First version to compare
 * @param v2 Second version to compare
 * @return true if versions are equal, false otherwise (including NULL inputs)
 */
bool fwup_semver_equals(const fwpb_semver* v1, const fwpb_semver* v2);

/**
 * @brief Get current firmware version from metadata
 *
 * Retrieves the semantic version of the currently running firmware from
 * the active slot metadata. This is a cross-platform utility used by
 * firmware update logic to determine version compatibility.
 *
 * @param[out] version_out Output buffer for version information
 * @return true if successful and version is valid (non-zero), false otherwise
 *
 * @note Returns false if:
 *       - version_out is NULL
 *       - metadata_get_active_slot() fails
 *       - metadata contains all-zero version (0.0.0)
 */
bool fwup_get_self_version(fwpb_semver* version_out);

/**
 * @brief Get firmware version from the inactive (target) slot metadata
 *
 * Reads the semantic version of the firmware written to the inactive slot.
 * Used after fwup_transfer() completes to verify the flashed version matches
 * what was shown to the user for confirmation.
 *
 * @param[out] version_out Output buffer for version information
 * @return true if successful, false otherwise
 *
 * @note Returns false if:
 *       - version_out is NULL
 *       - the active slot cannot be determined
 *       - the inactive slot metadata is invalid or missing
 */
bool fwup_get_target_version(fwpb_semver* version_out);

/**
 * @brief Format semantic version as a string
 *
 * Converts a semantic version structure to a human-readable string
 * in the format "vX.Y.Z" (e.g., "v1.2.3").
 *
 * @param[in]  version     Pointer to semantic version to format
 * @param[out] buffer      Output buffer for formatted string
 * @param[in]  buffer_size Size of output buffer in bytes
 * @return true if successful, false otherwise
 *
 * @note Returns false if:
 *       - version is NULL
 *       - buffer is NULL
 *       - buffer_size < 16 (minimum size needed for version string)
 *       - snprintf fails or would truncate
 */
bool fwup_format_version_string(const fwpb_semver* version, char* buffer, size_t buffer_size);
