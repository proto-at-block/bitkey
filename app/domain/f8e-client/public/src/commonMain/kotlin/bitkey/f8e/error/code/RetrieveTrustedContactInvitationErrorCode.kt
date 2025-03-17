package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Error codes used in [RetrieveTrustedContactInvitationService].
 */
@Serializable
enum class RetrieveTrustedContactInvitationErrorCode : F8eClientErrorCode {
  /** Indicates the code entered does not match an invitation. **/
  NOT_FOUND,
}
