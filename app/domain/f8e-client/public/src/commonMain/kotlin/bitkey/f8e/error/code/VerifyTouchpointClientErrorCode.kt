package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors encountered when verifying a notification touchpoint
 */
@Serializable
enum class VerifyTouchpointClientErrorCode : F8eClientErrorCode {
  /** Indicates a notification verification code sent to F8e was incorrect. */
  CODE_MISMATCH,

  /** Indicates a notification verification code sent to F8e expired. */
  CODE_EXPIRED,
}
