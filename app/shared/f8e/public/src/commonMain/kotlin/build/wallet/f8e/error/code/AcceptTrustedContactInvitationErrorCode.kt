package build.wallet.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Error codes used in [AcceptTrustedContactInvitationService].
 */
@Serializable
enum class AcceptTrustedContactInvitationErrorCode : F8eClientErrorCode {
  /** Indicates the invitation attempting to be accepted was already accepted. **/
  RELATIONSHIP_ALREADY_ESTABLISHED,

  /** Indicates the invitation attempting to be accepted has expired. **/
  INVITATION_EXPIRED,

  /** Indicates the invitation attempting to be accepted corresponds to an invalid code. **/
  INVITATION_CODE_MISMATCH,

  /** Indicates the account attempting to accept the invite is the same account that send the invite. **/
  CUSTOMER_IS_TRUSTED_CONTACT,

  /** Indicates the account attempting to accept the invite is already a Trusted Contact for the requesting account. **/
  ACCOUNT_ALREADY_TRUSTED_CONTACT,
}
