package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Error codes used when creating a Trusted Contact invitation.
 */
@Serializable
enum class CreateTrustedContactInvitationErrorCode : F8eClientErrorCode {
  /**
   * Indicates the account has reached the maximum number of trusted contacts across
   * inheritance and social recovery.
   */
  MAX_TRUSTED_CONTACTS_REACHED,
}
