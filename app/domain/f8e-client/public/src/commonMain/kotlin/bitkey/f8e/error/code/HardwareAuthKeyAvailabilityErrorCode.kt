package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors returned when checking whether a hardware auth key is usable by an account.
 */
@Serializable
enum class HardwareAuthKeyAvailabilityErrorCode : F8eClientErrorCode {
  /** The hardware auth key is already linked to another account or pending recovery. */
  HW_AUTH_PUBKEY_IN_USE,
}
